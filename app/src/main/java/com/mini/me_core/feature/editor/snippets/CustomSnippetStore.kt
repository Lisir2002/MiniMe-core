package com.mini.me_core.feature.editor.snippets

import android.content.Context
import com.mini.me_core.feature.editor.snippets.model.Snippet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 自定义片段存储（单例）。
 *
 * 以 JSON 文件持久化到应用私有目录（`filesDir/snippets/custom_snippets.json`），
 * 自包含、不依赖 Hilt/网络。支持：
 *  - CRUD：添加 / 编辑 / 删除 / 查询
 *  - 启用/禁用单个片段
 *  - 导入/导出：JSON 批量格式
 *  - 导入时格式校验，错误给出具体位置提示
 *
 * 线程安全：所有公开方法加锁。
 */
object CustomSnippetStore {

    private const val DIR = "snippets"
    private const val FILE = "custom_snippets.json"

    private val lock = Any()

    @Volatile
    private var initialized = false

    private lateinit var storeFile: File

    private val items = mutableListOf<Snippet>()

    /** 校验错误：面向用户的可读消息。 */
    class InvalidSnippetException(message: String) : Exception(message)

    fun initialize(appContext: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            val dir = File(appContext.filesDir, DIR).apply { mkdirs() }
            storeFile = File(dir, FILE)
            loadLocked()
            initialized = true
        }
    }

    private fun loadLocked() {
        if (!storeFile.exists()) return
        runCatching {
            val arr = JSONArray(storeFile.readText())
            items.clear()
            for (i in 0 until arr.length()) {
                items.add(fromJson(arr.getJSONObject(i)))
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            val arr = JSONArray()
            for (s in items) arr.put(toJson(s))
            storeFile.writeText(arr.toString(2))
        }
    }

    /** 全部自定义片段（不可变快照）。 */
    fun all(): List<Snippet> = synchronized(lock) { items.toList() }

    fun get(id: String): Snippet? = synchronized(lock) { items.firstOrNull { it.id == id } }

    /** 新增。返回创建后的片段。校验失败抛 [InvalidSnippetException]。 */
    fun add(name: String, prefix: String, language: String, description: String, body: List<String>): Snippet {
        validateFields(prefix, language, body)
        val snippet = Snippet(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { prefix },
            prefix = prefix.trim(),
            language = language.trim(),
            description = description.trim(),
            body = body,
            builtin = false,
            enabled = true,
        )
        synchronized(lock) {
            items.add(snippet)
            persistLocked()
        }
        return snippet
    }

    /** 按 id 更新。 */
    fun update(id: String, name: String, prefix: String, language: String, description: String, body: List<String>) {
        validateFields(prefix, language, body)
        synchronized(lock) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx < 0) return
            items[idx] = items[idx].copy(
                name = name.ifBlank { prefix },
                prefix = prefix.trim(),
                language = language.trim(),
                description = description.trim(),
                body = body,
            )
            persistLocked()
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            items.removeAll { it.id == id }
            persistLocked()
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        synchronized(lock) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx < 0) return
            items[idx] = items[idx].withEnabled(enabled)
            persistLocked()
        }
    }

    /** 导出全部自定义片段为 JSON 字符串（数组）。 */
    fun exportToJson(): String {
        val arr = JSONArray()
        synchronized(lock) {
            for (s in items) arr.put(toJson(s))
        }
        return arr.toString(2)
    }

    /**
     * 从 JSON 字符串批量导入。支持两种格式：
     *  - 单个片段对象 `{...}`
     *  - 片段数组 `[{...}, {...}]`
     *  - VS Code 风格 `{ "snippets": { "name": { "prefix":..., "body":... } } }`
     *
     * @return 成功导入的数量
     * @throws InvalidSnippetException 任一条目非法时抛出，message 含行号/位置提示
     */
    fun importFromJson(json: String): Int {
        val trimmed = json.trim()
        val parsed: List<Snippet> = when {
            trimmed.startsWith("[") -> {
                val arr = JSONArray(trimmed)
                (0 until arr.length()).map { i ->
                    requireValid(arr.getJSONObject(i), entryLabel = "第 ${i + 1} 项")
                }
            }
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                if (obj.has("snippets")) {
                    // VS Code 风格：snippets 为 map
                    val map = obj.getJSONObject("snippets")
                    val result = ArrayList<Snippet>()
                    val keys = map.keys()
                    var idx = 0
                    while (keys.hasNext()) {
                        idx++
                        val key = keys.next()
                        val entry = map.getJSONObject(key)
                        result.add(requireValid(entry, entryLabel = "「$key」", fallbackName = key))
                    }
                    result
                } else {
                    listOf(requireValid(obj, entryLabel = "片段"))
                }
            }
            else -> throw InvalidSnippetException("无法识别的 JSON 格式：应以 { 或 [ 开头")
        }
        synchronized(lock) {
            for (s in parsed) {
                // 去重：同语言同前缀则覆盖
                val existing = items.indexOfFirst { it.language == s.language && it.prefix == s.prefix }
                if (existing >= 0) items[existing] = s.copy(id = items[existing].id) else items.add(s)
            }
            persistLocked()
        }
        return parsed.size
    }

    private fun requireValid(obj: JSONObject, entryLabel: String, fallbackName: String? = null): Snippet {
        val prefix = obj.optString("prefix", "")
        val language = obj.optString("language", "")
        val bodyJson = obj.optJSONArray("body")
        val body = if (bodyJson != null) {
            (0 until bodyJson.length()).map { bodyJson.getString(it) }
        } else {
            // VS Code 风格 body 可能是字符串
            val single = obj.optString("body", "")
            if (single.isNotEmpty()) listOf(single) else emptyList()
        }
        try {
            validateFields(prefix, language, body)
        } catch (e: InvalidSnippetException) {
            throw InvalidSnippetException("$entryLabel：${e.message}")
        }
        return Snippet(
            id = UUID.randomUUID().toString(),
            name = obj.optString("name", fallbackName ?: prefix),
            prefix = prefix.trim(),
            language = language.trim(),
            description = obj.optString("description", ""),
            body = body,
            builtin = false,
            enabled = obj.optBoolean("enabled", true),
        )
    }

    private fun validateFields(prefix: String, language: String, body: List<String>) {
        if (prefix.isBlank()) throw InvalidSnippetException("触发词 prefix 不能为空")
        if (language.isBlank()) throw InvalidSnippetException("语言 language 不能为空")
        if (body.isEmpty()) throw InvalidSnippetException("正文 body 不能为空")
        // 复用解析器校验占位符语法
        runCatching { SnippetParser.parseBody(body) }
            .getOrElse { throw InvalidSnippetException("占位符语法错误：${it.message}") }
    }

    private fun toJson(s: Snippet): JSONObject = JSONObject().apply {
        put("name", s.name)
        put("prefix", s.prefix)
        put("language", s.language)
        put("description", s.description)
        put("enabled", s.enabled)
        val arr = JSONArray()
        for (line in s.body) arr.put(line)
        put("body", arr)
    }

    private fun fromJson(obj: JSONObject): Snippet = Snippet(
        id = obj.optString("id", UUID.randomUUID().toString()),
        name = obj.optString("name", ""),
        prefix = obj.optString("prefix", ""),
        language = obj.optString("language", ""),
        description = obj.optString("description", ""),
        body = obj.optJSONArray("body")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        builtin = false,
        enabled = obj.optBoolean("enabled", true),
    )
}
