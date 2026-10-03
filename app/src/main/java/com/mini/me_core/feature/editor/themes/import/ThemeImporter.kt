package com.mini.me_core.feature.editor.themes.import

import android.content.Context
import com.mini.me_core.feature.editor.textmate.TextMateManager
import com.mini.me_core.feature.editor.themes.ThemeCategory
import com.mini.me_core.feature.editor.themes.ThemeMetadata
import com.mini.me_core.feature.editor.themes.ThemeJsonSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** 主题导入结果。 */
sealed class ThemeImportResult {
    data class Success(val themeName: String, val displayName: String) : ThemeImportResult()
    data class Failure(val errors: List<String>) : ThemeImportResult()
}

/**
 * 主题导入器。支持：
 *  - VS Code color theme JSON（.json，colors + tokenColors，与 TextMate 兼容）
 *  - TextMate plist 主题（.tmTheme，XML），自动转换为 JSON
 *
 * 导入的主题统一保存到 filesDir/themes/ 下为 .json 文件，并注册到 [TextMateManager]。
 */
class ThemeImporter(private val appContext: Context) {

    companion object {
        private const val CUSTOM_DIR = "themes"
    }

    private val customDir: File
        get() = File(appContext.filesDir, CUSTOM_DIR).apply { mkdirs() }

    /**
     * 导入一份主题文件。
     * @param displayNameHint 用户提供的文件名或主题名
     * @param content 文件字节内容
     * @param isPlist true=.tmTheme(XML)，false=.json
     */
    suspend fun import(displayNameHint: String, content: ByteArray, isPlist: Boolean): ThemeImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val themeJson = if (isPlist) {
                    TmThemePlistConverter.convert(content.toString(Charsets.UTF_8))
                } else {
                    normalizeJsonTheme(content.toString(Charsets.UTF_8))
                }

                // 校验
                val errors = ThemeJsonSupport.validateThemeJson(themeJson)
                if (errors.isNotEmpty()) {
                    return@withContext ThemeImportResult.Failure(errors)
                }

                val root = JSONObject(themeJson)
                val name = root.optString("name").ifBlank { slugify(displayNameHint) }
                val displayName = root.optString("displayName").ifBlank { displayNameHint }
                val type = root.optString("type", "dark")
                val isDark = type != "light"

                // 重名时追加后缀
                var finalName = name
                var suffix = 2
                while (File(customDir, "$finalName.json").exists()) {
                    finalName = "$name-$suffix"
                    suffix++
                }
                root.put("name", finalName)
                root.put("displayName", displayName)
                root.put("author", root.optString("author", "imported"))

                val dest = File(customDir, "$finalName.json")
                dest.writeText(root.toString())

                val meta = ThemeMetadata(
                    name = finalName,
                    displayName = displayName,
                    category = ThemeCategory.CUSTOM,
                    isDark = isDark,
                    author = "imported",
                    filePath = dest.absolutePath,
                    isCustom = true,
                )
                TextMateManager.registerCustomTheme(meta)
                ThemeImportResult.Success(finalName, displayName)
            }.getOrElse {
                ThemeImportResult.Failure(listOf(it.message ?: "导入失败"))
            }
        }

    /**
     * 规范化 VS Code 主题 JSON：补全 type/colors，确保结构完整。
     * VS Code 的 tokenColors 与 TextMate 完全兼容，colors 键名也一致，这里仅做结构补全。
     */
    private fun normalizeJsonTheme(raw: String): String {
        val root = JSONObject(raw)
        if (!root.has("type")) {
            val bg = root.optJSONObject("colors")?.optString("editor.background", "") ?: ""
            root.put("type", if (TmThemePlistConverter.isLightColor(bg)) "light" else "dark")
        }
        // VS Code 主题可能把全局配色放在 tokenColors[0]，这里保留原样即可，
        // sora-editor 的 IThemeSource 能识别。
        return root.toString()
    }

    private fun slugify(name: String): String =
        name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "imported-theme" }
}
