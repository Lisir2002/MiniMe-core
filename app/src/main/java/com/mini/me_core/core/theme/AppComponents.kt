package com.mini.me_core.core.theme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

// ──────────────────────────────────────────────
// Z 轴层次常量（elevation）
// 已合并到 PrimitiveElevation（tokens/），此处保留兼容别名
// ──────────────────────────────────────────────
object Elevation {
    /** 背景层：页面背景、容器背景 */
    val z0: Dp = com.mini.me_core.core.theme.tokens.PrimitiveElevation.Z0
    /** 内容层：列表、卡片、正文区域 */
    val z1: Dp = com.mini.me_core.core.theme.tokens.PrimitiveElevation.Z1
    /** 交互层：输入栏、按钮、可交互卡片 */
    val z2: Dp = com.mini.me_core.core.theme.tokens.PrimitiveElevation.Z2
    /** 浮层：面板、弹出 Sheet、Dialog */
    val z3: Dp = com.mini.me_core.core.theme.tokens.PrimitiveElevation.Z3
    /** 通知层：状态横幅、Toast */
    val z4: Dp = com.mini.me_core.core.theme.tokens.PrimitiveElevation.Z4
}

// ──────────────────────────────────────────────
// 统一页面过渡动画
//
// 注意：Terminal 页面使用 AndroidView（TerminalView），
//   fade 过渡会导致新旧 composable 共存时 TerminalView 覆盖新页面。
//   因此 terminal 路由使用纯 slide 过渡，其他页面用 slide+fade。
// ──────────────────────────────────────────────
val pageEnterTransition: EnterTransition =
    slideInHorizontally(
        animationSpec = ScaledAnimation.tweenGlobal(250),
        initialOffsetX = { it / 4 }
    ) + fadeIn(animationSpec = ScaledAnimation.tweenGlobal(250))

val pageExitTransition: ExitTransition =
    fadeOut(animationSpec = ScaledAnimation.tweenGlobal(200))

val pagePopEnterTransition: EnterTransition =
    fadeIn(animationSpec = ScaledAnimation.tweenGlobal(200))

val pagePopExitTransition: ExitTransition =
    slideOutHorizontally(
        animationSpec = ScaledAnimation.tweenGlobal(250),
        targetOffsetX = { it / 4 }
    ) + fadeOut(animationSpec = ScaledAnimation.tweenGlobal(200))

val terminalEnterTransition: EnterTransition =
    slideInHorizontally(
        animationSpec = ScaledAnimation.tweenGlobal(250),
        initialOffsetX = { it / 4 }
    )

val terminalExitTransition: ExitTransition =
    slideOutHorizontally(
        animationSpec = ScaledAnimation.tweenGlobal(250),
        targetOffsetX = { it / 4 }
    )
