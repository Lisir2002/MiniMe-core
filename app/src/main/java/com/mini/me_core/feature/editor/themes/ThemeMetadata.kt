package com.mini.me_core.feature.editor.themes

/**
 * 主题分类。
 */
enum class ThemeCategory {
    DARK,
    LIGHT,
    HIGH_CONTRAST,
    COLORBLIND,
    CUSTOM,
    IMPORTED;

    companion object {
        /** 从 index.json 中的字符串解析，未知值回退到 DARK。 */
        fun fromRaw(raw: String?): ThemeCategory = when (raw?.lowercase()) {
            "light" -> LIGHT
            "highcontrast", "high_contrast" -> HIGH_CONTRAST
            "colorblind", "colourblind" -> COLORBLIND
            "custom" -> CUSTOM
            "imported" -> IMPORTED
            else -> DARK
        }
    }
}

/**
 * 一套编辑器主题的元数据（不含完整配色内容）。
 *
 * @property name 主题唯一标识（英文，用作 ThemeRegistry 中的键）
 * @property displayName 展示名
 * @property category 分类
 * @property isDark 是否深色主题
 * @property author 作者
 * @property assetPath 内置主题在 assets 中的路径；内置主题必填
 * @property filePath 自定义/导入主题在 filesDir 中的路径；自定义主题必填
 * @property isCustom true=用户自建或导入，false=内置
 */
data class ThemeMetadata(
    val name: String,
    val displayName: String,
    val category: ThemeCategory,
    val isDark: Boolean,
    val author: String = "",
    val assetPath: String? = null,
    val filePath: String? = null,
    val isCustom: Boolean = false,
) {
    companion object {
        const val CUSTOM_DIR = "themes"
    }
}

/**
 * 主题缩略图预览色板（从主题 JSON 提取，供 Compose 绘制迷你代码预览）。
 * 所有颜色为 #RRGGBB 字符串。
 */
data class ThemePreviewColors(
    val background: String = "#1E1E1E",
    val foreground: String = "#D4D4D4",
    val keyword: String = "#569CD6",
    val string: String = "#CE9178",
    val comment: String = "#6A9955",
    val number: String = "#B5CEA8",
    val function: String = "#DCDCAA",
) {
    companion object {
        val EMPTY = ThemePreviewColors()
    }
}
