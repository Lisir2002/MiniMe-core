package com.mini.me_core.core.theme.tokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.ui.unit.dp

/**
 * Primitive 圆角 Token。
 *
 * 基于现有 `Radius` 对象扩展，补充高频使用的 12dp/16dp/24dp。
 * 现有 `LocalCornerRadius.current.xs/sm/md/lg/pill` 保持不变。
 *
 * 数据来源：项目硬编码圆角统计：
 * 12dp(37次) / 10dp(15次) / 8dp(14次) / 4dp(14次) / 6dp(13次) / 16dp(6次)
 */
object PrimitiveRadius {
    val None = 0.dp
    val Xxs = 2.dp          // 极小圆角
    val Xs = 4.dp           // 现有 LocalCornerRadius.current.xs，小标签/徽章
    val Sm = 6.dp           // 小卡片/按钮
    val Md = 8.dp           // 现有 LocalCornerRadius.current.md，标准卡片
    val Lg = 10.dp          // 现有 LocalCornerRadius.current.lg，大卡片
    val Xl = 12.dp          // 高频：特大卡片/Sheet(37次)
    val Xxl = 16.dp         // 高频：超大圆角(6次)
    val Xxxl = 24.dp        // 全圆角元素(2次)
    val Pill = 999.dp       // 现有 LocalCornerRadius.current.pill，胶囊形
}
