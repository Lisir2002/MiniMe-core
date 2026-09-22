package com.mini.me_core.core.theme.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Primitive 阴影/层级 Token。
 *
 * 基于现有 `Elevation` 对象（AppComponents.kt 中定义 z0-z4）扩展。
 * 现有值保持不变：z0=0, z1=1, z2=3, z3=8, z4=12
 */
object PrimitiveElevation {
    val Z0: Dp = 0.dp    // 背景层：页面背景、容器背景
    val Z1: Dp = 1.dp    // 内容层：列表、卡片、正文区域
    val Z2: Dp = 3.dp    // 交互层：输入栏、按钮、可交互卡片
    val Z3: Dp = 8.dp    // 浮层：面板、弹出 Sheet、Dialog
    val Z4: Dp = 12.dp   // 通知层：状态横幅、Toast
}
