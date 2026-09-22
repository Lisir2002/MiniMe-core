package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.graphics.Color

/**
 * Semantic 语义色。
 *
 * 按"用途"命名，换主题/暗色/用户自定义只改这一层，组件代码零改动。
 * 所有默认值从现有代码逐字段提取：
 * - 亮色：LightColorScheme + Brand + CyberColors 亮色值
 * - 暗色：DarkColorScheme + Brand.StatusGreen.Dark + CyberColors 暗色值
 *
 * 组件永远只引用本类，禁止直接引用 PrimitiveColors 或硬编码 Color。
 */
data class SemanticColors(
    // ── 品牌色 ──
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val brandContainer: Color,
    val onBrandContainer: Color,
    val brandSecondary: Color,
    val onBrandSecondary: Color,

    // ── 状态色（成功/警告/错误/信息）──
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    // ── 扩展状态色（从现有代码提取：AboutSection/CyberComponents）──
    val sky: Color,              // 天蓝色（浏览器核心/次要强调）
    val onSky: Color,
    val skyContainer: Color,
    val onSkyContainer: Color,
    val orange: Color,           // 橙色（Debug 变体/警告变体）
    val onOrange: Color,
    val orangeContainer: Color,
    val onOrangeContainer: Color,

    // ── Surface 分层（核心：支持背景图透明度）──
    val surfacePage: Color,        // 页面最底层背景
    val surfaceCard: Color,        // 卡片/气泡背景
    val surfaceSunken: Color,      // 卡片内嵌/代码块/工具输出
    val surfaceOverlay: Color,     // 弹出层/Sheet/对话框
    val surfaceAccent: Color,      // 强调色容器
    val surfaceHover: Color,       // 悬停态背景
    val surfacePressed: Color,     // 按下态背景

    // ── 文字 ──
    val textPrimary: Color,        // 主要文字
    val textSecondary: Color,      // 次要文字
    val textTertiary: Color,       // 辅助文字/说明
    val textDisabled: Color,       // 禁用文字
    val textInverse: Color,        // 反色文字（深色背景上）

    // ── 边框 ──
    val borderDefault: Color,      // 默认边框
    val borderMuted: Color,        // 弱化边框/分割线
    val borderStrong: Color,       // 强边框/输入框
    val borderFocus: Color,        // 聚焦边框

    // ── 功能语义色（ChatAccent 迁移）──
    val accentBuild: Color,        // BUILD 模式（琥珀）
    val accentPlan: Color,         // PLAN 模式（蓝）
    val accentAuto: Color,         // AUTO 模式（青绿）
    val accentReasoning: Color,    // 思考强度（紫罗兰）
    val accentSkill: Color,        // 技能（粉）
    val onAccentBuild: Color,
    val onAccentPlan: Color,
    val onAccentAuto: Color,
    val onAccentReasoning: Color,
    val onAccentSkill: Color,
) {
    companion object {
        /**
         * 亮色默认值。
         * 从 LightColorScheme + Brand + CyberColors 亮色值逐字段提取。
         */
        val Light = SemanticColors(
            // 品牌色（LightColorScheme.primary = Brand.Blue = #2563EB）
            brandPrimary = PrimitiveColors.Blue600,
            onBrandPrimary = PrimitiveColors.White,
            brandContainer = PrimitiveColors.Blue100,
            onBrandContainer = PrimitiveColors.Blue800,
            brandSecondary = PrimitiveColors.Cyan600,
            onBrandSecondary = PrimitiveColors.White,

            // 状态色
            success = PrimitiveColors.Green600,          // Brand.StatusGreen.Light = #16A34A
            onSuccess = PrimitiveColors.White,
            successContainer = PrimitiveColors.Green100,
            onSuccessContainer = PrimitiveColors.Green800,
            warning = PrimitiveColors.Amber600,          // #D97706
            onWarning = PrimitiveColors.White,
            warningContainer = PrimitiveColors.Amber100,
            onWarningContainer = PrimitiveColors.Amber800,
            error = PrimitiveColors.Red600,              // LightColorScheme.error = #DC2626
            onError = PrimitiveColors.White,
            errorContainer = PrimitiveColors.Red100,     // LightColorScheme.errorContainer = #FEE2E2
            onErrorContainer = PrimitiveColors.Red900,   // LightColorScheme.onErrorContainer = #7F1D1D
            info = PrimitiveColors.Blue600,              // #2563EB
            onInfo = PrimitiveColors.White,
            infoContainer = PrimitiveColors.Blue100,
            onInfoContainer = PrimitiveColors.Blue800,
            // 扩展状态色（从 AboutSection/CyberComponents 提取）
            sky = Color(0xFF0284C7),                     // AboutSection.skyAccent 亮色
            onSky = PrimitiveColors.White,
            skyContainer = Color(0xFFF0F9FF),            // AboutSection.skyBg 亮色 = Cyan50
            onSkyContainer = Color(0xFF0C4A6E),          // Cyan900
            orange = Color(0xFFB45309),                   // AboutSection.orangeText 亮色 = Amber700
            onOrange = PrimitiveColors.White,
            orangeContainer = Color(0xFFFFF3E0),          // AboutSection.orangeBg 亮色 = Orange50
            onOrangeContainer = Color(0xFF9A3412),        // Orange700

            // Surface 分层（亮色：不透明纯色）
            surfacePage = PrimitiveColors.Slate50,       // LightColorScheme.background = #F8FAFC
            surfaceCard = PrimitiveColors.White,         // LightColorScheme.surface = #FFFFFF
            surfaceSunken = PrimitiveColors.Slate100,    // LightColorScheme.surfaceVariant = #F1F5F9
            surfaceOverlay = PrimitiveColors.White,
            surfaceAccent = PrimitiveColors.Blue50,      // Brand.Ice = #EFF6FF
            surfaceHover = PrimitiveColors.Slate100.copy(alpha = PrimitiveAlpha.Hover),
            surfacePressed = PrimitiveColors.Slate200.copy(alpha = PrimitiveAlpha.Pressed),

            // 文字
            textPrimary = PrimitiveColors.Slate900,      // LightColorScheme.onBackground = #0F172A
            textSecondary = PrimitiveColors.Slate600,    // LightColorScheme.onSurfaceVariant = #475569
            textTertiary = PrimitiveColors.Slate400,
            textDisabled = PrimitiveColors.Slate300,
            textInverse = PrimitiveColors.White,

            // 边框
            borderDefault = PrimitiveColors.Slate200,    // LightColorScheme.outlineVariant = #E2E8F0
            borderMuted = PrimitiveColors.Slate100,
            borderStrong = PrimitiveColors.Slate300,
            borderFocus = PrimitiveColors.Blue600,

            // 功能语义色（ChatAccent 亮色值）
            accentBuild = PrimitiveColors.Amber700,      // #B45309
            accentPlan = PrimitiveColors.Blue600,        // #2563EB
            accentAuto = PrimitiveColors.Teal600,        // #0D9488
            accentReasoning = PrimitiveColors.Violet600, // #7C3AED
            accentSkill = PrimitiveColors.Pink600,       // #DB2777
            onAccentBuild = PrimitiveColors.White,
            onAccentPlan = PrimitiveColors.White,
            onAccentAuto = PrimitiveColors.White,
            onAccentReasoning = PrimitiveColors.White,
            onAccentSkill = PrimitiveColors.White,
        )

        /**
         * 暗色默认值（强设计感，多层级 Surface）。
         * 从 DarkColorScheme + Brand.StatusGreen.Dark + CyberColors 暗色值逐字段提取。
         * 设计理念：页面→卡片→内嵌，每一层亮一个色阶（参考 Carbon Design System）。
         */
        val Dark = SemanticColors(
            // 品牌色（DarkColorScheme.primary = #60A5FA）
            brandPrimary = PrimitiveColors.Blue400,
            onBrandPrimary = PrimitiveColors.Slate900,
            brandContainer = Color(0xFF0F3A63),    // DarkColorScheme.primaryContainer = #0F3A63（非标色阶）
            onBrandContainer = PrimitiveColors.Blue100,  // DarkColorScheme.onPrimaryContainer = #DBEAFE
            brandSecondary = PrimitiveColors.Cyan400,
            onBrandSecondary = PrimitiveColors.Slate900,

            // 状态色（暗色提亮：600→400）
            success = PrimitiveColors.Green400,          // Brand.StatusGreen.Dark = #4ADE80
            onSuccess = PrimitiveColors.Green900,
            successContainer = PrimitiveColors.Green900,
            onSuccessContainer = PrimitiveColors.Green100,
            warning = PrimitiveColors.Amber400,          // ChatAccent.Build.dark = #FBBF24
            onWarning = PrimitiveColors.Amber900,
            warningContainer = PrimitiveColors.Amber900,
            onWarningContainer = PrimitiveColors.Amber100,
            error = PrimitiveColors.Red400,              // DarkColorScheme.error = #F87171
            onError = PrimitiveColors.Red900,            // DarkColorScheme.onError = #450A0A
            errorContainer = PrimitiveColors.Red900,     // DarkColorScheme.errorContainer = #7F1D1D
            onErrorContainer = PrimitiveColors.Red200,   // DarkColorScheme.onErrorContainer = #FECACA
            info = PrimitiveColors.Blue400,
            onInfo = PrimitiveColors.Slate900,
            infoContainer = PrimitiveColors.Blue800,
            onInfoContainer = PrimitiveColors.Blue100,
            // 扩展状态色（从 AboutSection/CyberComponents 提取）
            sky = Color(0xFF38BDF8),                     // AboutSection.skyAccent 暗色 = Sky400
            onSky = PrimitiveColors.Slate900,
            skyContainer = Color(0xFF0C4A6E),            // AboutSection.skyBg 暗色 = Cyan900
            onSkyContainer = Color(0xFFBAE6FD),          // Sky200
            orange = Color(0xFFFDBA74),                   // AboutSection.orangeText 暗色 = Orange300
            onOrange = PrimitiveColors.Slate900,
            orangeContainer = Color(0xFF7C2D12),          // AboutSection.orangeBg 暗色 = Orange900
            onOrangeContainer = Color(0xFFFED7AA),        // Orange200

            // Surface 分层（暗色：多层级，每一层亮一阶）
            surfacePage = PrimitiveColors.Slate900,      // DarkColorScheme.background = #0F172A
            surfaceCard = PrimitiveColors.Slate800,      // DarkColorScheme.surface = #1E293B
            surfaceSunken = PrimitiveColors.Slate700,    // 比卡片亮一阶
            surfaceOverlay = PrimitiveColors.Slate800,
            surfaceAccent = PrimitiveColors.Blue900.copy(alpha = 0.5f),
            surfaceHover = PrimitiveColors.White.copy(alpha = PrimitiveAlpha.Hover),
            surfacePressed = PrimitiveColors.White.copy(alpha = PrimitiveAlpha.Pressed),

            // 文字（暗色：高对比主文字，柔和次文字）
            textPrimary = PrimitiveColors.Slate200,      // DarkColorScheme.onBackground = #E2E8F0
            textSecondary = PrimitiveColors.Slate400,    // DarkColorScheme.onSurfaceVariant = #94A3B8
            textTertiary = PrimitiveColors.Slate500,
            textDisabled = PrimitiveColors.Slate600,
            textInverse = PrimitiveColors.Slate900,

            // 边框（暗色：低对比边框，不抢内容）
            borderDefault = PrimitiveColors.Slate700,    // DarkColorScheme.outlineVariant = #334155
            borderMuted = PrimitiveColors.Slate800,
            borderStrong = PrimitiveColors.Slate600,
            borderFocus = PrimitiveColors.Blue400,

            // 功能语义色（ChatAccent 暗色值）
            accentBuild = PrimitiveColors.Amber400,      // #FBBF24
            accentPlan = PrimitiveColors.Blue400,        // #60A5FA
            accentAuto = PrimitiveColors.Teal400,        // #2DD4BF
            accentReasoning = PrimitiveColors.Violet400, // #A78BFA
            accentSkill = PrimitiveColors.Pink400,       // #F472B6
            onAccentBuild = Color(0xFF451A03),    // ChatAccent.Build.onDark = #451A03（非标色阶）
            onAccentPlan = Color(0xFF0B3B76),      // ChatAccent.Plan.onDark = #0B3B76（非标色阶）
            onAccentAuto = Color(0xFF0B3B2E),      // ChatAccent.Auto.onDark = #0B3B2E（非标色阶）
            onAccentReasoning = Color(0xFF2E1065), // ChatAccent.Reasoning.onDark = #2E1065（非标色阶）
            onAccentSkill = Color(0xFF500724),     // ChatAccent.Skill.onDark = #500724（非标色阶）
        )
    }
}
