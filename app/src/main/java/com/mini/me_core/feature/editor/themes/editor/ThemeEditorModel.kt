package com.mini.me_core.feature.editor.themes.editor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 可视化主题编辑器的纯逻辑模型：
 *  - 从基础主题 JSON 提取各 [EditorColorField] 当前色值
 *  - 应用用户覆盖色 → 产出可保存的新主题 JSON
 *
 * 不依赖 Android，可直接单测。
 */
object ThemeEditorModel {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** 字段 key → 当前 hex 颜色（#RRGGBB）。 */
    data class Snapshot(
        val baseThemeJson: String,
        val type: String,
        val values: Map<String, String>,
    )

    /** 从基础主题 JSON 提取所有字段的当前色值。 */
    fun loadSnapshot(baseThemeJson: String): Snapshot {
        val root = runCatching { json.parseToJsonElement(baseThemeJson).jsonObject }.getOrElse {
            return Snapshot(baseThemeJson, "dark", emptyMap())
        }
        val type = root["type"]?.jsonPrimitive?.contentOrNull ?: "dark"
        val colors = root["colors"]?.jsonObject
        val tokenColors = root["tokenColors"]?.jsonArray

        val values = HashMap<String, String>()
        EditorColorField.ALL.forEach { field ->
            val hex = when {
                field.colorKey != null -> colors?.get(field.colorKey)?.jsonPrimitive?.contentOrNull
                field.scope != null -> findTokenColor(tokenColors, field.scope)
                else -> null
            }
            values[field.key] = normalizeHex(hex) ?: fallbackFor(field)
        }
        return Snapshot(baseThemeJson, type, values)
    }

    /** 在 tokenColors 中查找 scope 对应的 foreground。 */
    private fun findTokenColor(tokenColors: JsonArray?, primaryScope: String): String? {
        tokenColors ?: return null
        tokenColors.forEach { entry ->
            val obj = entry.jsonObject
            val settings = obj["settings"]?.jsonObject
            val fg = settings?.get("foreground")?.jsonPrimitive?.contentOrNull
                ?: return@forEach
            val scope = obj["scope"] ?: return@forEach
            val scopes = when (scope) {
                is JsonPrimitive -> listOf(scope.contentOrNull ?: "")
                is JsonArray -> scope.map { it.jsonPrimitive.contentOrNull ?: "" }
                else -> emptyList()
            }
            // 优先精确匹配，其次前缀匹配
            if (scopes.any { it == primaryScope || it.startsWith("$primaryScope.") }) return fg
        }
        return null
    }

    /**
     * 基于基础主题 JSON + 覆盖色，生成新主题 JSON 字符串。
     * @param overrides 字段 key → 新 hex 颜色；未覆盖的字段保留基础值
     * @param newName 新主题名
     * @param displayName 新主题展示名
     */
    fun buildThemeJson(
        snapshot: Snapshot,
        overrides: Map<String, String>,
        newName: String,
        displayName: String,
    ): String {
        val root = runCatching { json.parseToJsonElement(snapshot.baseThemeJson).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

        // 合并最终颜色值
        val finalValues = snapshot.values.toMutableMap()
        overrides.forEach { (k, v) -> finalValues[k] = v }

        // 1. 写回 colors.*
        val colors = root["colors"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        EditorColorField.EDITOR_FIELDS.forEach { field ->
            val ck = field.colorKey ?: return@forEach
            finalValues[field.key]?.let { colors[ck] = JsonPrimitive(it) }
        }

        // 2. 写回 tokenColors（按 scope 覆盖/新增 foreground）
        val tokenColors = buildJsonArray {
            val usedScopes = HashSet<String>()
            snapshot.baseThemeJson.let {
                runCatching { json.parseToJsonElement(it).jsonObject["tokenColors"]?.jsonArray }
                    .getOrNull()?.forEach { entry ->
                        val obj = entry.jsonObject
                        val scope = obj["scope"]
                        val settings = obj["settings"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                        // 检查是否需要覆盖
                        EditorColorField.SYNTAX_FIELDS.forEach { field ->
                            val sc = field.scope ?: return@forEach
                            val matches = when (scope) {
                                is JsonPrimitive -> scope.contentOrNull == sc || scope.contentOrNull?.startsWith("$sc.") == true
                                is JsonArray -> scope.any { it.jsonPrimitive.contentOrNull == sc || it.jsonPrimitive.contentOrNull?.startsWith("$sc.") == true }
                                else -> false
                            }
                            if (matches) {
                                finalValues[field.key]?.let { settings["foreground"] = JsonPrimitive(it) }
                                usedScopes += sc
                            }
                        }
                        add(JsonObject(obj.toMutableMap().apply {
                            put("settings", JsonObject(settings))
                        }))
                    }
            }
            // 为未在基础主题中出现的语法字段追加新规则
            EditorColorField.SYNTAX_FIELDS.forEach { field ->
                val sc = field.scope ?: return@forEach
                if (sc !in usedScopes) {
                    add(buildJsonObject {
                        put("scope", sc)
                        put("settings", buildJsonObject {
                            finalValues[field.key]?.let { put("foreground", it) }
                        })
                    })
                }
            }
        }

        val result = buildJsonObject {
            put("name", newName)
            put("displayName", displayName)
            put("type", snapshot.type)
            put("author", "custom")
            put("colors", JsonObject(colors))
            put("tokenColors", tokenColors)
        }
        return json.encodeToString(JsonObject.serializer(), result)
    }

    private fun fallbackFor(field: EditorColorField): String = when (field.group) {
        EditorColorField.Group.EDITOR -> if (field.colorKey?.contains("foreground") == true) "#D4D4D4" else "#1E1E1E"
        EditorColorField.Group.SYNTAX -> "#D4D4D4"
    }

    private fun normalizeHex(value: String?): String? {
        if (value.isNullOrBlank()) return null
        var v = value.trim().removePrefix("#")
        if (v.length == 3) v = v.map { "$it$it" }.joinToString("")
        if (v.length == 8) v = v.substring(0, 6)
        return if (v.length == 6) "#$v" else null
    }
}
