package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 图标尺寸 token（审计 P2-19）。
 *
 * 背景：对话流里图标尺寸一度散乱成 15/17/22/30/38dp 等奇数档，既无法对齐网格，
 * 也让「这个图标该多大」每次都要重新拍脑袋。这里收敛为 4 档标准尺寸，
 * 与代码块语言角标、工具卡片、消息操作行共用同一套度量。
 *
 * 使用约束：
 *  - 只允许这 4 档，新增图标必须落到其中一档，不允许再出现 15/17/22 这类奇数；
 *  - 触控目标（按钮的可点区域）不归这里管——那是 [PrimitiveTouchTarget] 的职责，
 *    视觉小图标 + 大触控区是两种不同的度量，混在一起会导致「为了触控把图标放大」。
 */
object IconSizeTokens {

    /** 行内小图标：状态点、列表前缀、行内提示。 */
    val small: Dp = 16.dp

    /** 常规图标：工具卡片头部、操作行、列表项主图标。 */
    val medium: Dp = 20.dp

    /** 强调图标：区块标题、空状态、主操作。 */
    val large: Dp = 24.dp

    /** 大图标：空状态插画、附件预览占位。 */
    val xLarge: Dp = 32.dp
}

/**
 * 触控目标 token（审计 P2-18/P2-19）。
 *
 * Material 建议最小触控目标 48dp；对话流消息操作行是横向紧凑图标带，
 * 直接上 48dp 会把每条消息行高撑高近一倍，故折中为 40dp（详见 MessageActionIconButton 注释）。
 * 这里集中定义，避免各处再散落魔法数字。
 */
object PrimitiveTouchTarget {
    /** 紧凑图标带（消息操作行）的触控目标。 */
    val compactButton: Dp = 40.dp

    /** 常规独立按钮的触控目标。 */
    val standardButton: Dp = 48.dp
}
