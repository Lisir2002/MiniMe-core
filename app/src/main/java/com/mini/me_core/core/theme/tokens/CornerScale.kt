package com.mini.me_core.core.theme.tokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角缩放档位。
 *
 * 由 [CornerStyle] 动态映射而来，组件通过 [LocalCornerRadius] 读取，
 * 不再硬编码 `RoundedCornerShape(LocalCornerRadius.current.xl)`。
 *
 * - Sharp  → 全部 0dp（直角）
 * - ROUNDED → 使用 PrimitiveRadius 基准值（默认）
 * - Pill   → 全部 999dp（胶囊）
 *
 * 注意：[pill] 字段始终为 999dp，不随 CornerStyle 变化；
 * CircleShape 也保持圆形，不随 CornerStyle 变化（设计意图）。
 */
data class CornerScale(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    /** 胶囊形，始终 999dp，不随 CornerStyle 变化 */
    val pill: Dp = 999.dp,
) {
    companion object {
        /** 默认圆角档位：与 PrimitiveRadius 对齐 */
        val Rounded = CornerScale(
            xs = PrimitiveRadius.Xs,    // 4dp
            sm = PrimitiveRadius.Sm,    // 6dp
            md = PrimitiveRadius.Md,    // 8dp
            lg = PrimitiveRadius.Lg,    // 10dp
            xl = PrimitiveRadius.Xl,    // 12dp
            xxl = PrimitiveRadius.Xxl,  // 16dp（气泡/大卡片）
        )

        /** 直角档位：全部 0dp */
        val Sharp = CornerScale(
            xs = 0.dp, sm = 0.dp, md = 0.dp, lg = 0.dp, xl = 0.dp, xxl = 0.dp,
        )

        /** 胶囊档位：全部 999dp */
        val Pill = CornerScale(
            xs = 999.dp, sm = 999.dp, md = 999.dp, lg = 999.dp, xl = 999.dp, xxl = 999.dp,
        )

        /** 根据 CornerStyle 映射到对应档位 */
        fun from(style: CornerStyle): CornerScale = when (style) {
            CornerStyle.Sharp -> Sharp
            CornerStyle.Pill -> Pill
            CornerStyle.ROUNDED -> Rounded
        }
    }
}

/**
 * CompositionLocal：当前圆角档位。
 *
 * 用法：`RoundedCornerShape(LocalCornerRadius.current.md)`
 *
 * 由 AIEditorTheme 根据用户 cornerStyle 设置提供。
 */
val LocalCornerRadius = staticCompositionLocalOf { CornerScale.Rounded }
