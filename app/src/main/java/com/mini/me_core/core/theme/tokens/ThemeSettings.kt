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
 * 第一期只包含外观模式 + 预设选择，自定义颜色覆盖留到 Phase 5。
 *
 * @property mode 外观模式
 * @property presetId 当前应用的预设 ID
 * @property customOverrides 自定义颜色覆盖（Phase 5 使用，第一期为空 Map）
 */
@Serializable
data class ThemeSettings(
    val mode: String = "AUTO",
    val presetId: String = "default",
    val customOverrides: Map<String, String> = emptyMap(),
) {
    fun themeMode(): ThemeMode = ThemeMode.fromPersisted(mode)

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
