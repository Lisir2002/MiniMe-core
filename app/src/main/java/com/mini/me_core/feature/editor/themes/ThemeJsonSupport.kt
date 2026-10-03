package com.mini.me_core.feature.editor.themes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 主题 JSON 解析/校验/预览色提取的纯逻辑集合（不依赖 Android，可在 JVM 单测中直接调用）。
 *
 * 内置主题清单 `editor/themes/index.json` 与主题文件均遵循 VS Code / TextMate color theme JSON 格式。
 */
object ThemeJsonSupport {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析内置主题清单，返回元数据列表。格式错误时抛出 [ThemeParseException]。 */
    fun parseIndex(raw: String): List<ThemeMetadata> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            throw ThemeParseException("主题清单不是合法 JSON：${it.message}", it)
        }
        val arr = root["themes"]?.jsonArray
            ?: throw ThemeParseException("主题清单缺少 \"themes\" 数组")
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: name
            val category = ThemeCategory.fromRaw(obj["category"]?.jsonPrimitive?.contentOrNull)
            val isDark = obj["isDark"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: (category == ThemeCategory.DARK)
            val author = obj["author"]?.jsonPrimitive?.contentOrNull ?: ""
            ThemeMetadata(
                name = name,
                displayName = displayName,
                category = category,
                isDark = isDark,
                author = author,
                assetPath = path,
                isCustom = false,
            )
        }
    }

    /** 从主题 JSON 文本中提取缩略图预览色板。 */
    fun extractPreviewColors(raw: String): ThemePreviewColors {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            return ThemePreviewColors.EMPTY
        }
        val colors = root["colors"]?.jsonObject
        fun color(key: String, fallback: String): String {
            val v = colors?.get(key)?.jsonPrimitive?.contentOrNull
            return normalizeHex(v) ?: fallback
        }

        var keyword = "#569CD6"; var string = "#CE9178"; var comment = "#6A9955"
        var number = "#B5CEA8"; var function = "#DCDCAA"

        root["tokenColors"]?.jsonArray?.forEach { entry ->
            val obj = entry.jsonObject
            val fg = obj["settings"]?.jsonObject?.get("foreground")?.jsonPrimitive?.contentOrNull
                ?.let { normalizeHex(it) } ?: return@forEach
            when (val scope = obj["scope"]) {
                is JsonPrimitive -> matchScope(scope.contentOrNull, fg)?.let {
                    when (it) { "keyword" -> keyword = fg; "string" -> string = fg
                        "comment" -> comment = fg; "number" -> number = fg; "function" -> function = fg }
                }
                is JsonArray -> scope.forEach { s -> matchScope(s.jsonPrimitive.contentOrNull, fg)?.let {
                    when (it) { "keyword" -> keyword = fg; "string" -> string = fg
                        "comment" -> comment = fg; "number" -> number = fg; "function" -> function = fg }
                } }
                else -> Unit
            }
        }

        return ThemePreviewColors(
            background = color("editor.background", "#1E1E1E"),
            foreground = color("editor.foreground", "#D4D4D4"),
            keyword = keyword,
            string = string,
            comment = comment,
            number = number,
            function = function,
        )
    }

    private fun matchScope(scope: String?, fg: String): String? = when {
        scope == null -> null
        scope.startsWith("comment") -> "comment"
        scope.startsWith("string") -> "string"
        scope.startsWith("constant.numeric") || scope == "number" -> "number"
        scope.startsWith("entity.name.function") || scope.startsWith("support.function") -> "function"
        scope.startsWith("keyword") || scope.startsWith("storage.type") -> "keyword"
        else -> null
    }

    /** 归一化 #RGB / #RRGGBB / #RRGGBBAA 为 #RRGGBB；无法识别返回 null。 */
    fun normalizeHex(value: String?): String? {
        if (value.isNullOrBlank()) return null
        var v = value.trim().removePrefix("#")
        if (v.startsWith("0x", ignoreCase = true)) v = v.substring(2)
        return when (v.length) {
            3 -> "#" + v.map { "$it$it" }.joinToString("")
            6 -> "#$v"
            8 -> "#" + v.substring(0, 6)
            else -> null
        }?.uppercase()
    }

    /**
     * 校验主题 JSON 是否可被 TextMate 引擎加载。
     * @return 错误信息列表；空列表表示校验通过。
     */
    fun validateThemeJson(raw: String): List<String> {
        val errors = mutableListOf<String>()
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            return listOf("不是合法的 JSON：${it.message}")
        }
        if (root["colors"]?.jsonObject == null) {
            errors += "缺少 \"colors\" 字段（编辑器背景/前景等颜色）"
        }
        root["colors"]?.jsonObject?.let { colors ->
            if (colors["editor.background"] == null) {
                errors += "colors 中缺少 editor.background"
            }
        }
        if (root["tokenColors"]?.jsonArray == null) {
            errors += "缺少 \"tokenColors\" 数组（语法高亮着色规则）"
        }
        return errors
    }
}

/** 主题解析异常。 */
class ThemeParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
