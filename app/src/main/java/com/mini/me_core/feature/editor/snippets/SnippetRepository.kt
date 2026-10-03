package com.mini.me_core.feature.editor.snippets

import android.content.Context
import com.mini.me_core.feature.editor.snippets.model.Snippet
import org.json.JSONArray
import org.json.JSONObject

/**
 * 片段仓库（单例）：聚合内置片段（assets）与自定义片段（[CustomSnippetStore]）。
 *
 * 职责：
 *  - 启动时从 assets 的 editor/snippets 目录加载内置片段
 *  - 维护 scopeName → 片段语言 id 的映射（编辑器用 TextMate scope，片段用短语言名）
 *  - 按「当前语言 + 已输入前缀」过滤出可参与补全的片段
 *
 * 内置片段加载为一次性（低内存：150+ 条轻量对象，常驻可忽略）。
 */
object SnippetRepository {

    private const val ASSET_DIR = "editor/snippets"

    @Volatile
    private var initialized = false

    /** 全部内置片段。 */
    private val builtin = mutableListOf<Snippet>()

    /** scopeName → 片段语言 id。TextMate scope 可能带后缀，前缀匹配。 */
    private val scopeToLanguage = mapOf(
        "source.python" to "python",
        "source.js" to "javascript",
        "source.js.jsx" to "javascript",
        "source.ts" to "typescript",
        "source.tsx" to "typescript",
        "source.java" to "java",
        "source.go" to "go",
        "source.rust" to "rust",
        "source.c" to "c",
        "source.cpp" to "cpp",
        "text.html.basic" to "html",
        "text.html.derivative" to "html",
        "source.css" to "css",
        "source.json" to "json",
        "text.html.markdown" to "markdown",
        "source.sql" to "sql",
        "source.shell" to "shell",
        "source.dockerfile" to "docker",
        "source.php" to "php",
        "source.ruby" to "ruby",
        "source.kotlin" to "kotlin",
        "source.swift" to "swift",
        // Git 提交信息 / ignore 文件归到 git 片段
        "source.git-commit-message" to "git",
        "source.ignore" to "git",
        "source.gitignore" to "git",
    )

    /**
     * 初始化：加载内置片段。幂等，应在后台线程调用。
     */
    fun initialize(appContext: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            runCatching {
                val files = appContext.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".json") }
                for (file in files) {
                    loadBuiltinFile(appContext, "$ASSET_DIR/$file")
                }
            }
            initialized = true
        }
    }

    private fun loadBuiltinFile(appContext: Context, path: String) {
        runCatching {
            val text = appContext.assets.open(path).bufferedReader().use { it.readText() }
            val arr = JSONObject(text).getJSONArray("snippets")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val prefix = obj.optString("prefix")
                if (prefix.isEmpty()) continue
                val lang = obj.optString("language")
                val bodyJson = obj.optJSONArray("body") ?: continue
                val body = ArrayList<String>(bodyJson.length())
                for (j in 0 until bodyJson.length()) body.add(bodyJson.getString(j))
                builtin.add(
                    Snippet(
                        id = "builtin-$lang-$prefix-$i",
                        name = obj.optString("name", prefix),
                        prefix = prefix,
                        language = lang,
                        description = obj.optString("description", ""),
                        body = body,
                        builtin = true,
                        enabled = true,
                    ),
                )
            }
        }
    }

    /** scopeName → 片段语言 id；无法识别返回 null。 */
    fun languageForScope(scopeName: String): String? {
        // 精确匹配
        scopeToLanguage[scopeName]?.let { return it }
        // 前缀匹配（如 text.html.markdown 已精确；source.css.scss 归 css 扩展）
        for ((scope, lang) in scopeToLanguage) {
            if (scopeName == scope || scopeName.startsWith("$scope.")) return lang
        }
        return null
    }

    /** 全部内置片段（不可变快照）。 */
    fun builtinSnippets(): List<Snippet> = builtin.toList()

    /** 内置片段按语言分组。 */
    fun builtinByLanguage(): Map<String, List<Snippet>> =
        builtin.groupBy { it.language }.toSortedMap()

    /**
     * 匹配可参与补全的片段：当前语言 + 前缀匹配（前缀大小写不敏感，支持前缀子串）。
     * 合并内置与启用的自定义片段。
     */
    fun match(scopeName: String, prefix: String): List<Snippet> {
        val lang = languageForScope(scopeName) ?: return emptyList()
        val p = prefix.trim()
        if (p.isEmpty()) return emptyList()
        val result = ArrayList<Snippet>()
        result += builtin.filter { it.enabled && it.language == lang && it.prefix.startsWith(p, ignoreCase = true) }
        result += CustomSnippetStore.all().filter {
            it.enabled && it.language == lang && it.prefix.startsWith(p, ignoreCase = true)
        }
        // 完全等于前缀的排前面，其次按 prefix 长度排序
        result.sortWith(
            compareByDescending<Snippet> { it.prefix.equals(p, ignoreCase = true) }
                .thenBy { it.prefix.length },
        )
        return result
    }
}
