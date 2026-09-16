package com.mini.me_core.newui.designsystem.primitive

import androidx.compose.foundation.layout.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一最小触控目标封装（设计系统强制收口）。
 *
 * 设计哲学：iOS 简约风视觉紧凑，但交互可点区不能跟着紧凑。所有可点击 primitive
 * （AppIconButton / AppChip / AppSwitch / AppCheckRow / AppTagInput 等）在内部默认挂本修饰，
 * 业务调用方想绕都绕不开——把"48dp 最小触控"从"靠人记得写"变成"组件自带"。
 *
 * 实现：
 *  - [sizeIn] 保证可视内容再小，命中热区也至少有 [min] 见方；
 *  - [minimumInteractiveComponentSize]（M3 提供）在未占满时居中对齐，并与涟漪/焦点对齐。
 *
 * @param min 最小触控边长，默认 48.dp（Material 推荐最小触控区）。
 */
fun Modifier.touchTarget(min: Dp = 48.dp): Modifier = this
    .sizeIn(minWidth = min, minHeight = min)
    .minimumInteractiveComponentSize()
