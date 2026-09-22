package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.unit.dp

/**
 * Primitive 间距 Token。
 *
 * 基于现有 `Spacing` 对象扩展，补充高频使用的非标值（2dp/6dp/10dp 等）。
 * 现有 `Spacing.xs/sm/md/lg/xl/xxl` 保持不变，新增值作为补充。
 *
 * 数据来源：项目 1660 处硬编码间距统计，Top 高频值：
 * 2dp(41次) / 8dp(36次) / 4dp(27次) / 16dp(24次) / 6dp(23次) / 10dp(17次) / 12dp(12次)
 */
object PrimitiveSpacing {
    val None = 0.dp
    val Hairline = 1.dp      // 极细边框/分割线
    val Xxs = 2.dp          // 高频：图标与文字间距(41次)
    val Xs = 4.dp           // 现有 Spacing.xs，紧凑内边距
    val SmPlus = 6.dp       // 高频：较紧凑间距(23次)
    val Sm = 8.dp           // 现有 Spacing.sm，标准小间距
    val MdPlus = 10.dp      // 高频：中小编距(17次)
    val Md = 12.dp          // 现有 Spacing.md，卡片内边距
    val Lg = 16.dp          // 现有 Spacing.lg，页面边距
    val Xl = 20.dp          // 较大间距
    val Xxl = 24.dp         // 现有 Spacing.xl，区块间距
    val Xxxl = 32.dp        // 现有 Spacing.xxl，超大间距
    val Xxxxl = 48.dp       // 特大间距
    val AppBarHeight = 56.dp
    val InputBarHeight = 60.dp
}
