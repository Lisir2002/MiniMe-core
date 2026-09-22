package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.graphics.Color

/**
 * Primitive 颜色色阶（方案 B：完整色阶）。
 *
 * 每个色系提供 50-900 共 10 个色阶，参考 Tailwind / Material 3 色阶体系。
 * 纯数值，不表达用途，永远不被组件直接引用——组件只能引用 Semantic Colors。
 *
 * 色值来源：从项目现有 464 处硬编码颜色中归纳提取，确保与现有视觉一致。
 */
object PrimitiveColors {

    // ── 品牌蓝（现有 Brand.Blue = #2563EB，对应 Blue600）──
    val Blue50 = Color(0xFFEFF6FF)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue200 = Color(0xFFBFDBFE)
    val Blue300 = Color(0xFF93C5FD)
    val Blue400 = Color(0xFF60A5FA)   // 暗色主色（现有 DarkColorScheme.primary）
    val Blue500 = Color(0xFF3B82F6)
    val Blue600 = Color(0xFF2563EB)   // 亮色主色（现有 Brand.Blue）
    val Blue700 = Color(0xFF1D4ED8)
    val Blue800 = Color(0xFF1E40AF)   // 暗色主色容器
    val Blue900 = Color(0xFF1E3A8A)

    // ── 成功绿（现有成功色 #22C55E=Green500, #2E7D32=Green800, #16A34A=Green600, #4ADE80=Green400）──
    val Green50 = Color(0xFFF0FDF4)
    val Green100 = Color(0xFFDCFCE7)
    val Green200 = Color(0xFFBBF7D0)
    val Green300 = Color(0xFF86EFAC)
    val Green400 = Color(0xFF4ADE80)   // 暗色成功（现有 Brand.StatusGreen.Dark）
    val Green500 = Color(0xFF22C55E)   // 成功绿（出现 25 次）
    val Green600 = Color(0xFF16A34A)   // 亮色成功（现有 Brand.StatusGreen.Light）
    val Green700 = Color(0xFF15803D)
    val Green800 = Color(0xFF166534)   // 深成功绿（出现 20 次 #2E7D32 近似）
    val Green900 = Color(0xFF14532D)

    // ── 错误红（现有错误色 #EF4444=Red500, #C62828=Red800, #DC2626=Red600）──
    val Red50 = Color(0xFFFEF2F2)
    val Red100 = Color(0xFFFEE2E2)
    val Red200 = Color(0xFFFECACA)
    val Red300 = Color(0xFFFCA5A5)
    val Red400 = Color(0xFFF87171)   // 暗色错误（现有 DarkColorScheme.error）
    val Red500 = Color(0xFFEF4444)   // 错误红（出现 16 次）
    val Red600 = Color(0xFFDC2626)   // 亮色错误（现有 LightColorScheme.error）
    val Red700 = Color(0xFFB91C1C)
    val Red800 = Color(0xFF991B1B)   // 深错误红（出现 18 次 #C62828 近似）
    val Red900 = Color(0xFF7F1D1D)   // 错误容器（现有 DarkColorScheme.errorContainer）

    // ── 警告琥珀（现有警告色 #F59E0B=Amber500, #FBBF24=Amber400, #D97706=Amber600）──
    val Amber50 = Color(0xFFFFFBEB)
    val Amber100 = Color(0xFFFEF3C7)
    val Amber200 = Color(0xFFFDE68A)
    val Amber300 = Color(0xFFFCD34D)
    val Amber400 = Color(0xFFFBBF24)   // 暗色警告 / ChatAccent.Build.dark
    val Amber500 = Color(0xFFF59E0B)   // 警告琥珀（出现 12 次）
    val Amber600 = Color(0xFFD97706)   // 深警告（出现 9 次）
    val Amber700 = Color(0xFFB45309)   // 亮色 BUILD 模式（ChatAccent.Build.light）
    val Amber800 = Color(0xFF92400E)
    val Amber900 = Color(0xFF78350F)

    // ── 信息青（现有信息色 #0984E3=CyberColors.Blue, #00B894=CyberColors.Cyan）──
    val Cyan50 = Color(0xFFECFEFF)
    val Cyan100 = Color(0xFFCFFAFE)
    val Cyan200 = Color(0xFFA5F3FC)
    val Cyan300 = Color(0xFF67E8F9)
    val Cyan400 = Color(0xFF22D3EE)
    val Cyan500 = Color(0xFF06B6D4)
    val Cyan600 = Color(0xFF0891B2)
    val Cyan700 = Color(0xFF0E7490)
    val Cyan800 = Color(0xFF155E75)
    val Cyan900 = Color(0xFF164E63)

    // Teal（AUTO 模式青绿，现有 ChatAccent.Auto = #0D9488/#2DD4BF）
    val Teal50 = Color(0xFFF0FDFA)
    val Teal100 = Color(0xFFCCFBF1)
    val Teal200 = Color(0xFF99F6E4)
    val Teal300 = Color(0xFF5EEAD4)
    val Teal400 = Color(0xFF2DD4BF)   // 暗色 AUTO
    val Teal500 = Color(0xFF14B8A6)
    val Teal600 = Color(0xFF0D9488)   // 亮色 AUTO
    val Teal700 = Color(0xFF0F766E)
    val Teal800 = Color(0xFF115E59)
    val Teal900 = Color(0xFF134E4A)

    // ── 紫罗兰（Reasoning 模式，现有 ChatAccent.Reasoning = #7C3AED/#A78BFA）──
    val Violet50 = Color(0xFFF5F3FF)
    val Violet100 = Color(0xFFEDE9FE)
    val Violet200 = Color(0xFFDDD6FE)
    val Violet300 = Color(0xFFC4B5FD)
    val Violet400 = Color(0xFFA78BFA)   // 暗色 Reasoning
    val Violet500 = Color(0xFF8B5CF6)
    val Violet600 = Color(0xFF7C3AED)   // 亮色 Reasoning
    val Violet700 = Color(0xFF6D28D9)
    val Violet800 = Color(0xFF5B21B6)
    val Violet900 = Color(0xFF4C1D95)

    // ── 粉（Skill 模式，现有 ChatAccent.Skill = #DB2777/#F472B6）──
    val Pink50 = Color(0xFFFDF2F8)
    val Pink100 = Color(0xFFFCE7F3)
    val Pink200 = Color(0xFFFBCFE8)
    val Pink300 = Color(0xFFF9A8D4)
    val Pink400 = Color(0xFFF472B6)   // 暗色 Skill
    val Pink500 = Color(0xFFEC4899)
    val Pink600 = Color(0xFFDB2777)   // 亮色 Skill
    val Pink700 = Color(0xFFBE185D)
    val Pink800 = Color(0xFF9D174D)
    val Pink900 = Color(0xFF831843)

    // ── 中性灰（Slate，现有主用中性色）──
    val Slate50 = Color(0xFFF8FAFC)    // 亮色页面底（现有 LightColorScheme.background）
    val Slate100 = Color(0xFFF1F5F9)   // 亮色工具块/次级表面
    val Slate200 = Color(0xFFE2E8F0)   // 亮色边框/分割线
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)   // 暗色次要文字
    val Slate500 = Color(0xFF64748B)   // 亮色次要文字
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)   // 暗色边框/分割线
    val Slate800 = Color(0xFF1E293B)   // 暗色卡片/AI 气泡背景
    val Slate900 = Color(0xFF0F172A)   // 暗色页面背景

    // ── 纯白/纯黑/透明 ──
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val Transparent = Color(0x00000000)
}
