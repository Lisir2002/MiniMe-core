package com.mini.me_core.feature.editor.themes.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 将 TextMate `.tmTheme`（XML plist）转换为 VS Code / TextMate color theme JSON。
 *
 * plist 结构：
 * ```xml
 * <plist><dict>
 *   <key>name</key><string>Theme Name</string>
 *   <key>settings</key><array>
 *     <dict><key>settings</key><dict>
 *       <key>background</key><string>#272822</string>
 *       <key>foreground</key><string>#F8F8F2</string>
 *     </dict></dict>
 *     <dict><key>name</key><string>Comment</string>
 *       <key>scope</key><string>comment</string>
 *       <key>settings</key><dict><key>foreground</key><string>#75715E</string></dict></dict>
 *   </array>
 * </dict></plist>
 * ```
 *
 * 纯 JVM 逻辑，不依赖 Android 框架，可直接单测。
 */
object TmThemePlistConverter {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * 解析 plist XML 文本并输出 TextMate color theme JSON 字符串。
     * @throws IllegalArgumentException XML 非法或缺少必要结构时
     */
    fun convert(plistXml: String): String {
        val doc = parseXml(plistXml)
        val plistDict = doc.documentElement.childNodes.toList()
            .firstOrNull { it is Element && it.tagName == "dict" }
            ?: throw IllegalArgumentException("plist 缺少根 dict")

        val rootMap = readDict(plistDict as Element)

        val name = rootMap["name"] as? String ?: "Imported Theme"
        val settingsArr = rootMap["settings"] as? List<*>
            ?: throw IllegalArgumentException("tmTheme 缺少 settings 数组")

        val editorColors = mutableMapOf<String, String>()
        val tokenColors = buildJsonArray {
            settingsArr.forEach { entry ->
                val dict = entry as? Map<*, *> ?: return@forEach
                val scopes = dict["scope"]
                val settings = dict["settings"] as? Map<*, *>
                if (scopes == null && settings != null) {
                    // 全局配色块：background/foreground/caret → editor colors
                    settings["background"]?.let { editorColors["editor.background"] = it.toString() }
                    settings["foreground"]?.let { editorColors["editor.foreground"] = it.toString() }
                    settings["caret"]?.let { editorColors["editorCursor.foreground"] = it.toString() }
                    settings["selection"]?.let { editorColors["editor.selectionBackground"] = it.toString() }
                } else if (scopes != null && settings != null) {
                    val scopeVal = when (scopes) {
                        is List<*> -> scopes.joinToString(", ") { it.toString() }
                        else -> scopes.toString()
                    }
                    val settingsObj = buildJsonObject {
                        settings.forEach { (k, v) ->
                            if (v is String) put(k.toString(), v)
                        }
                    }
                    add(JsonObject(mapOf("scope" to JsonPrimitive(scopeVal), "settings" to settingsObj)))
                }
            }
        }

        // 推断 dark/light
        val bg = editorColors["editor.background"] ?: "#1E1E1E"
        val isDark = !isLightColor(bg)
        // 补全缺失的常用 editor 键
        if ("editorLineNumber.foreground" !in editorColors) {
            editorColors["editorLineNumber.foreground"] = if (isDark) "#858585" else "#6e7681"
        }
        if ("editor.lineHighlightBackground" !in editorColors) {
            editorColors["editor.lineHighlightBackground"] = if (isDark) "#2a2d2e" else "#f0f0f0"
        }
        val colorsObj = buildJsonObject {
            editorColors.forEach { (k, v) -> put(k, v) }
        }

        val result = buildJsonObject {
            put("name", slugify(name))
            put("displayName", name)
            put("type", if (isDark) "dark" else "light")
            put("colors", colorsObj)
            put("tokenColors", tokenColors)
        }
        return json.encodeToString(JsonObject.serializer(), result)
    }

    // ── plist 解析 ──

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setXIncludeAware(false)
        factory.isExpandEntityReferences = false
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    /** 读取一个 <dict> 元素为 LinkedMap<String, Any>。 */
    private fun readDict(el: Element): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        val children = el.childNodes.toList().filterIsInstance<Element>()
        var i = 0
        while (i < children.size) {
            val keyEl = children[i]
            if (keyEl.tagName != "key") { i++; continue }
            val key = keyEl.textContent.trim()
            val valueEl = children.getOrNull(i + 1)
            if (valueEl != null) {
                result[key] = readValue(valueEl)
                i += 2
            } else {
                i += 1
            }
        }
        return result
    }

    private fun readValue(el: Element): Any = when (el.tagName) {
        "string" -> el.textContent.trim()
        "integer", "real" -> el.textContent.trim()
        "true" -> true
        "false" -> false
        "array" -> el.childNodes.toList().filterIsInstance<Element>().map { readValue(it) }
        "dict" -> readDict(el)
        "data" -> el.textContent.trim()
        else -> el.textContent.trim()
    }

    private fun NodeList.toList(): List<Node> = (0 until length).map { item(it) }

    // ── 工具 ──

    /** 粗略判断颜色是否为浅色（用于推断 type=dark/light）。 */
    internal fun isLightColor(hex: String): Boolean {
        var v = hex.trim().removePrefix("#")
        if (v.length == 3) v = v.map { "$it$it" }.joinToString("")
        if (v.length < 6) return true
        return runCatching {
            val r = v.substring(0, 2).toInt(16)
            val g = v.substring(2, 4).toInt(16)
            val b = v.substring(4, 6).toInt(16)
            (0.299 * r + 0.587 * g + 0.114 * b) > 150
        }.getOrDefault(true)
    }

    internal fun slugify(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "imported-theme" }
}
