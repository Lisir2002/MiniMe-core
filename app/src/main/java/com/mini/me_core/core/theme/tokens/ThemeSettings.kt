package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 外观模式枚举。
 *
 * AUTO = 跟随系统
 * LIGHT = 强制浅色
 * DARK = 强制深色
 *
 * 与现有 [com.mini.me_core.feature.settings.data.repository.AppThemeMode] 一一对应，
 * 本枚举放在 core/theme 层避免向上依赖 feature 模块。
 */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK;

    companion object {
        fun fromPersisted(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * 圆角风格枚举（Phase 5 显示偏好）。
 *
 * ROUNDED = 圆角（默认，12dp 圆角）
 * Sharp = 直角（0dp）
 * Pill = 胶囊（999dp，全圆角）
 */
enum class CornerStyle {
    ROUNDED,
    Sharp,
    Pill;

    companion object {
        fun fromPersisted(value: String?): CornerStyle =
            entries.firstOrNull { it.name == value } ?: ROUNDED
    }
}

/**
 * 主题预设定义。
 *
 * 每套预设包含亮色和暗色两套 [SemanticColors]，
 * 用户选择预设后，整个 App 的语义色随之切换。
 *
 * 预设配色从现有代码中归纳：
 * - Default：现有默认配色（Blue 主色，Slate 中性底）
 * - Cyber：偏蓝调暗色（#0D1B2E 底），青蓝主色
 * - Sunset：暖色调，橙红主色
 * - Forest：绿色调，绿主色
 * - Ocean：青蓝色调，青主色
 * - Mono：灰阶，无彩色主色
 *
 * @property id 唯一标识，用于持久化
 * @property displayName 显示名称
 * @property description 简短描述
 * @property lightColors 亮色模式语义色
 * @property darkColors 暗色模式语义色
 * @property previewPrimary 预览卡片主色块
 * @property previewBackground 预览卡片背景色块
 * @property previewSurface 预览卡片卡片色块
 */
data class ThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val lightColors: SemanticColors,
    val darkColors: SemanticColors,
    val previewPrimary: Color,
    val previewBackground: Color,
    val previewSurface: Color,
)

/**
 * 用户主题设置（持久化存储）。
 *
 * Phase 4：外观模式 + 预设选择
 * Phase 5：扩展自定义颜色覆盖、背景图、显示偏好（圆角/字体/动效）
 *
 * @property mode 外观模式
 * @property presetId 当前应用的预设 ID
 * @property customOverrides 自定义颜色覆盖 Map（key=友好色名字, value=ARGB hex 字符串）。
 *           Phase 5 复用此字段存储 9 项可自定义颜色，与 Phase 4 设计一致。
 * @property backgroundImage 背景图 URI（null=无背景图）
 * @property backgroundMask 背景图遮罩浓度（0.0-1.0，默认 0.5）
 * @property cardOpacity 卡片透明度（0.0-1.0，默认 1.0）
 * @property cornerStyle 圆角风格（默认 ROUNDED）
 * @property fontScale 字体大小缩放（0.8-1.4，默认 1.0）
 * @property animationScale 动效强度（0.0-1.0，默认 1.0，0.0=关闭动效）
 */
@Serializable
data class ThemeSettings(
    val mode: String = "AUTO",
    val presetId: String = "default",
    val customOverrides: Map<String, String> = emptyMap(),
    val backgroundImage: String? = null,
    val backgroundMask: Float = 0.5f,
    val cardOpacity: Float = 1.0f,
    val cornerStyle: String = "ROUNDED",
    val fontScale: Float = 1.0f,
    val animationScale: Float = 1.0f,
) {
    fun themeMode(): ThemeMode = ThemeMode.fromPersisted(mode)

    fun cornerStyleEnum(): CornerStyle = CornerStyle.fromPersisted(cornerStyle)

    companion object {
        val DEFAULT = ThemeSettings(mode = "AUTO", presetId = "default")

        private val json = Json { ignoreUnknownKeys = true }

        fun toJson(settings: ThemeSettings): String =
            json.encodeToString(ThemeSettings.serializer(), settings)

        fun fromJson(raw: String?): ThemeSettings {
            if (raw.isNullOrBlank()) return DEFAULT
            return runCatching {
                json.decodeFromString(ThemeSettings.serializer(), raw)
            }.getOrDefault(DEFAULT)
        }
    }
}

/**
 * 9 项可自定义颜色的友好名称到 SemanticColors 字段的映射（Phase 5）。
 *
 * key = 用户在 UI 上看到的友好名称，value = SemanticColors 对应字段的取值函数。
 * 自定义颜色覆盖时，先从预设获取基础色，再用 customOverrides Map 覆盖指定字段。
 */
object CustomColorFields {

    /** 品牌主色 → [SemanticColors.brandPrimary] */
    const val PRIMARY = "primary"

    /** 页面背景 → [SemanticColors.surfacePage] */
    const val BACKGROUND = "background"

    /** 卡片背景 → [SemanticColors.surfaceCard] */
    const val SURFACE = "surface"

    /** 工具块背景 → [SemanticColors.surfaceSunken] */
    const val SURFACE_VARIANT = "surfaceVariant"

    /** 主文字色 → [SemanticColors.textPrimary] */
    const val TEXT_PRIMARY = "textPrimary"

    /** 次文字色 → [SemanticColors.textSecondary] */
    const val TEXT_SECONDARY = "textSecondary"

    /** 默认边框 → [SemanticColors.borderDefault] */
    const val BORDER_DEFAULT = "borderDefault"

    /** 错误色 → [SemanticColors.error] */
    const val ERROR = "error"

    /** 成功色 → [SemanticColors.success] */
    const val SUCCESS = "success"

    /** 全部 9 项可自定义颜色 key 列表 */
    val ALL_KEYS = listOf(
        PRIMARY, BACKGROUND, SURFACE, SURFACE_VARIANT,
        TEXT_PRIMARY, TEXT_SECONDARY, BORDER_DEFAULT,
        ERROR, SUCCESS,
    )

    /** 友好名称到中文显示名的映射 */
    val DISPLAY_NAMES = mapOf(
        PRIMARY to "品牌主色",
        BACKGROUND to "页面背景",
        SURFACE to "卡片背景",
        SURFACE_VARIANT to "工具块背景",
        TEXT_PRIMARY to "主文字色",
        TEXT_SECONDARY to "次文字色",
        BORDER_DEFAULT to "默认边框",
        ERROR to "错误色",
        SUCCESS to "成功色",
    )

    /**
     * 将自定义颜色 Map 应用到基础 SemanticColors 上，返回覆盖后的新 SemanticColors。
     *
     * @param base 预设提供的基础颜色
     * @param overrides 自定义颜色覆盖 Map（key=友好色名, value=ARGB hex）
     * @return 覆盖后的 SemanticColors（未覆盖的字段保持 base 值）
     */
    fun applyOverrides(base: SemanticColors, overrides: Map<String, String>): SemanticColors {
        if (overrides.isEmpty()) return base
        return base.copy(
            brandPrimary = overrides[PRIMARY].toColor() ?: base.brandPrimary,
            surfacePage = overrides[BACKGROUND].toColor() ?: base.surfacePage,
            surfaceCard = overrides[SURFACE].toColor() ?: base.surfaceCard,
            surfaceSunken = overrides[SURFACE_VARIANT].toColor() ?: base.surfaceSunken,
            textPrimary = overrides[TEXT_PRIMARY].toColor() ?: base.textPrimary,
            textSecondary = overrides[TEXT_SECONDARY].toColor() ?: base.textSecondary,
            borderDefault = overrides[BORDER_DEFAULT].toColor() ?: base.borderDefault,
            error = overrides[ERROR].toColor() ?: base.error,
            success = overrides[SUCCESS].toColor() ?: base.success,
        )
    }

    /**
     * 获取指定颜色字段在当前配色中的实际 Color 值（用于对比度计算）。
     */
    fun getColorForField(field: String, colors: SemanticColors): Color = when (field) {
        PRIMARY -> colors.brandPrimary
        BACKGROUND -> colors.surfacePage
        SURFACE -> colors.surfaceCard
        SURFACE_VARIANT -> colors.surfaceSunken
        TEXT_PRIMARY -> colors.textPrimary
        TEXT_SECONDARY -> colors.textSecondary
        BORDER_DEFAULT -> colors.borderDefault
        ERROR -> colors.error
        SUCCESS -> colors.success
        else -> colors.brandPrimary
    }

    /**
     * 获取指定颜色字段应该与之对比的背景色（用于对比度警告）。
     * 文字类颜色对比页面背景，其他颜色对比卡片/页面背景。
     */
    fun getContrastBackground(field: String, colors: SemanticColors): Color = when (field) {
        TEXT_PRIMARY, TEXT_SECONDARY -> colors.surfacePage
        PRIMARY, SURFACE, SURFACE_VARIANT, BORDER_DEFAULT -> colors.surfacePage
        ERROR, SUCCESS -> colors.surfaceCard
        BACKGROUND -> colors.textPrimary
        else -> colors.surfacePage
    }

    /** ARGB hex 字符串转 Color，解析失败返回 null */
    private fun String?.toColor(): Color? {
        if (this.isNullOrBlank()) return null
        return runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()
    }
}

/**
 * 6 套主题预设。
 *
 * 配色策略：以现有 [SemanticColors.Light] / [SemanticColors.Dark] 为基底，
 * 仅覆盖品牌色和 surface 色调，状态色/文字/边框保持统一以保证可读性。
 */
object ThemePresets {

    /** 1. Default：当前默认配色（蓝主色，Slate 中性底） */
    val Default = ThemePreset(
        id = "default",
        displayName = "默认",
        description = "清爽专业的蓝白配色",
        lightColors = SemanticColors.Light,
        darkColors = SemanticColors.Dark,
        previewPrimary = PrimitiveColors.Blue600,
        previewBackground = PrimitiveColors.Slate50,
        previewSurface = PrimitiveColors.White,
    )

    /** 2. Cyber：偏蓝调暗色，青蓝主色 */
    val Cyber = ThemePreset(
        id = "cyber",
        displayName = "赛博",
        description = "深邃蓝黑，青蓝点缀",
        lightColors = SemanticColors.Light.copy(
            brandPrimary = PrimitiveColors.Cyan600,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Cyan100,
            onBrandContainer = PrimitiveColors.Cyan800,
            brandSecondary = PrimitiveColors.Blue600,
            surfacePage = Color(0xFFF0F7FF),
            surfaceAccent = PrimitiveColors.Cyan50,
            borderFocus = PrimitiveColors.Cyan600,
        ),
        darkColors = SemanticColors.Dark.copy(
            brandPrimary = PrimitiveColors.Cyan400,
            onBrandPrimary = PrimitiveColors.Slate900,
            brandContainer = Color(0xFF0D3A4A),
            onBrandContainer = PrimitiveColors.Cyan100,
            brandSecondary = PrimitiveColors.Blue400,
            surfacePage = Color(0xFF0D1B2E),
            surfaceCard = Color(0xFF132842),
            surfaceSunken = Color(0xFF1A3354),
            surfaceOverlay = Color(0xFF132842),
            surfaceAccent = PrimitiveColors.Cyan900.copy(alpha = 0.5f),
            borderFocus = PrimitiveColors.Cyan400,
        ),
        previewPrimary = PrimitiveColors.Cyan400,
        previewBackground = Color(0xFF0D1B2E),
        previewSurface = Color(0xFF132842),
    )

    /** 3. Sunset：暖色调，橙红主色 */
    val Sunset = ThemePreset(
        id = "sunset",
        displayName = "日落",
        description = "温暖橙红，舒适护眼",
        lightColors = SemanticColors.Light.copy(
            brandPrimary = PrimitiveColors.Amber600,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Amber100,
            onBrandContainer = PrimitiveColors.Amber800,
            brandSecondary = PrimitiveColors.Amber500,
            surfacePage = Color(0xFFFFF8F0),
            surfaceAccent = PrimitiveColors.Amber50,
            borderFocus = PrimitiveColors.Amber600,
        ),
        darkColors = SemanticColors.Dark.copy(
            brandPrimary = PrimitiveColors.Amber400,
            onBrandPrimary = Color(0xFF451A03),
            brandContainer = Color(0xFF4A2508),
            onBrandContainer = PrimitiveColors.Amber100,
            brandSecondary = Color(0xFFFB923C),
            surfacePage = Color(0xFF1C1208),
            surfaceCard = Color(0xFF2A1D10),
            surfaceSunken = Color(0xFF3A2818),
            surfaceOverlay = Color(0xFF2A1D10),
            surfaceAccent = PrimitiveColors.Amber900.copy(alpha = 0.4f),
            borderFocus = PrimitiveColors.Amber400,
        ),
        previewPrimary = PrimitiveColors.Amber500,
        previewBackground = Color(0xFF1C1208),
        previewSurface = Color(0xFF2A1D10),
    )

    /** 4. Forest：绿色调，绿主色 */
    val Forest = ThemePreset(
        id = "forest",
        displayName = "森林",
        description = "自然墨绿，沉静护眼",
        lightColors = SemanticColors.Light.copy(
            brandPrimary = PrimitiveColors.Green600,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Green100,
            onBrandContainer = PrimitiveColors.Green800,
            brandSecondary = PrimitiveColors.Teal600,
            surfacePage = Color(0xFFF4FBF5),
            surfaceAccent = PrimitiveColors.Green50,
            borderFocus = PrimitiveColors.Green600,
        ),
        darkColors = SemanticColors.Dark.copy(
            brandPrimary = PrimitiveColors.Green400,
            onBrandPrimary = Color(0xFF052E12),
            brandContainer = Color(0xFF0A3A1E),
            onBrandContainer = PrimitiveColors.Green100,
            brandSecondary = PrimitiveColors.Teal400,
            surfacePage = Color(0xFF0A1A10),
            surfaceCard = Color(0xFF122818),
            surfaceSunken = Color(0xFF1A3822),
            surfaceOverlay = Color(0xFF122818),
            surfaceAccent = PrimitiveColors.Green900.copy(alpha = 0.4f),
            borderFocus = PrimitiveColors.Green400,
        ),
        previewPrimary = PrimitiveColors.Green500,
        previewBackground = Color(0xFF0A1A10),
        previewSurface = Color(0xFF122818),
    )

    /** 5. Ocean：青蓝色调，青主色 */
    val Ocean = ThemePreset(
        id = "ocean",
        displayName = "海洋",
        description = "清新青蓝，如临深海",
        lightColors = SemanticColors.Light.copy(
            brandPrimary = PrimitiveColors.Teal600,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Teal100,
            onBrandContainer = PrimitiveColors.Teal800,
            brandSecondary = PrimitiveColors.Cyan600,
            surfacePage = Color(0xFFF0FAFA),
            surfaceAccent = PrimitiveColors.Teal50,
            borderFocus = PrimitiveColors.Teal600,
        ),
        darkColors = SemanticColors.Dark.copy(
            brandPrimary = PrimitiveColors.Teal400,
            onBrandPrimary = Color(0xFF042F2A),
            brandContainer = Color(0xFF0A3A35),
            onBrandContainer = PrimitiveColors.Teal100,
            brandSecondary = PrimitiveColors.Cyan400,
            surfacePage = Color(0xFF081418),
            surfaceCard = Color(0xFF0E2228),
            surfaceSunken = Color(0xFF163038),
            surfaceOverlay = Color(0xFF0E2228),
            surfaceAccent = PrimitiveColors.Teal900.copy(alpha = 0.4f),
            borderFocus = PrimitiveColors.Teal400,
        ),
        previewPrimary = PrimitiveColors.Teal400,
        previewBackground = Color(0xFF081418),
        previewSurface = Color(0xFF0E2228),
    )

    /** 6. Mono：灰阶，无彩色主色 */
    val Mono = ThemePreset(
        id = "mono",
        displayName = "单色",
        description = "纯粹灰阶，极简主义",
        lightColors = SemanticColors.Light.copy(
            brandPrimary = PrimitiveColors.Slate700,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Slate200,
            onBrandContainer = PrimitiveColors.Slate800,
            brandSecondary = PrimitiveColors.Slate600,
            surfacePage = Color(0xFFFAFAFA),
            surfaceAccent = PrimitiveColors.Slate100,
            borderFocus = PrimitiveColors.Slate600,
            accentBuild = PrimitiveColors.Slate700,
            accentPlan = PrimitiveColors.Slate600,
            accentAuto = PrimitiveColors.Slate600,
            accentReasoning = PrimitiveColors.Slate600,
            accentSkill = PrimitiveColors.Slate600,
        ),
        darkColors = SemanticColors.Dark.copy(
            brandPrimary = PrimitiveColors.Slate300,
            onBrandPrimary = PrimitiveColors.Slate900,
            brandContainer = PrimitiveColors.Slate700,
            onBrandContainer = PrimitiveColors.Slate100,
            brandSecondary = PrimitiveColors.Slate400,
            surfacePage = Color(0xFF0A0A0A),
            surfaceCard = Color(0xFF1A1A1A),
            surfaceSunken = Color(0xFF2A2A2A),
            surfaceOverlay = Color(0xFF1A1A1A),
            surfaceAccent = PrimitiveColors.Slate800,
            borderFocus = PrimitiveColors.Slate300,
            accentBuild = PrimitiveColors.Slate300,
            accentPlan = PrimitiveColors.Slate400,
            accentAuto = PrimitiveColors.Slate400,
            accentReasoning = PrimitiveColors.Slate400,
            accentSkill = PrimitiveColors.Slate400,
        ),
        previewPrimary = PrimitiveColors.Slate500,
        previewBackground = Color(0xFF0A0A0A),
        previewSurface = Color(0xFF1A1A1A),
    )

    /** 全部预设列表 */
    val All: List<ThemePreset> = listOf(Default, Cyber, Sunset, Forest, Ocean, Mono)

    /** 根据 ID 查找预设，找不到返回 Default */
    fun byId(id: String?): ThemePreset =
        All.firstOrNull { it.id == id } ?: Default
}
