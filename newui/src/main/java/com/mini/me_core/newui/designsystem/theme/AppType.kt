package com.mini.me_core.newui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS 简约风排版族（排版令牌，§排版族）。
 *
 * 对齐 SF Pro 类型尺度：Large Title / Title 1–3 / Headline / Body / Callout / Subheadline /
 * Footnote / Caption 1–2，外加 iOS 分组「区块小标题」SectionHeader。
 *
 * 命名自解释、作为设计系统唯一「排版令牌」：后期统一调整字号 / 字重 / 行高只需改这一处，
 * 页面统一走 `MaterialTheme.typography.*`（或直接引用 AppType.*），杜绝散落的硬编码字号。
 */
object AppType {

    // ===== 命名排版（页面可直接引用 AppType.*）=====
    /** iOS Large Title：导航大标题（34/41, Bold） */
    val LargeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
    /** iOS Title 1：首屏区块主标题（28/34, Bold） */
    val Title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    /** iOS Title 2：次级区块标题（22/28, SemiBold） */
    val Title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    /** iOS Title 3：小标题（20/25, SemiBold） */
    val Title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    /** iOS Headline：加粗正文（17/22, SemiBold），列表行标题用 */
    val Headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    /** iOS Body：标准正文（17/22, Regular） */
    val Body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    /** iOS Callout：次要正文（16/21, Regular） */
    val Callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    /** iOS Subheadline：注释行（15/20, Regular） */
    val Subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    /** iOS Footnote：脚注（13/18, Regular） */
    val Footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    /** iOS Caption 1：辅助说明（12/16, Regular） */
    val Caption1 = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** iOS Caption 2：徽标浓度文字（11/13, Regular） */
    val Caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)
    /** iOS 分组「区块小标题」（分组/表单分节，SemiBold），DesignGallery.Section 使用 */
    val SectionHeader = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)

    // ===== 映射到 M3 排版槽位 =====
    // 让 MaterialTheme.typography.* 直达 iOS 尺度；其余槽位与 iOS 对齐到邻近档位。
    val Material: Typography = Typography(
        displayLarge = LargeTitle,
        displayMedium = Title1,
        displaySmall = Title2,
        headlineLarge = Title3,
        headlineMedium = Headline,
        headlineSmall = Title2,
        titleLarge = Title1,
        titleMedium = Headline,
        titleSmall = SectionHeader,
        bodyLarge = Body,
        bodyMedium = Callout,
        bodySmall = Footnote,
        labelLarge = Headline,
        labelMedium = Subhead,
        labelSmall = Caption1,
    )
}