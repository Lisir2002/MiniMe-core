package com.mini.me_core.core.theme.tokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角缩放档位。
 *
 * 由 [CornerStyle] + 用户自定义 [CornerScale.from] / [CornerScale.custom] 动态映射而来，
 * 组件通过 [LocalCornerRadius] 读取，不再硬编码 `RoundedCornerShape(LocalCornerRadius.current.xl)`。
 *
 * - Sharp   → 全部 0dp（直角）
 * - ROUNDED  → 默认使用 PrimitiveRadius 基准值（12dp 基准）；
 *              若用户设置了自定义半径 cornerRadius，则走 [custom] 按比例生成各档位
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
        /** 默认圆角档位：与 PrimitiveRadius 对齐（基准 xl = 12dp） */
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

        /**
         * 按用户自定义基准半径 [radius] 生成各档位（比例相对 12dp 基准）：
         * - xs  = radius * 0.33
         * - sm  = radius * 0.5
         * - md  = radius * 0.67
         * - lg  = radius * 0.83
         * - xl  = radius（基准）
         * - xxl = radius * 1.33
         */
        fun custom(radius: Dp): CornerScale = CornerScale(
            xs = radius * 0.33f,
            sm = radius * 0.5f,
            md = radius * 0.67f,
            lg = radius * 0.83f,
            xl = radius,
            xxl = radius * 1.33f,
        )

        /** 根据 CornerStyle 映射到对应档位（使用默认 12dp 基准） */
        fun from(style: CornerStyle): CornerScale = when (style) {
            CornerStyle.Sharp -> Sharp
            CornerStyle.ROUNDED -> Rounded
        }

        /**
         * 根据 CornerStyle + 用户自定义基准半径映射到对应档位。
         * - Sharp   → 全 0dp
         * - ROUNDED → [custom](customRadius.dp)
         */
        fun from(style: CornerStyle, customRadius: Float): CornerScale = when (style) {
            CornerStyle.Sharp -> Sharp
            CornerStyle.ROUNDED -> custom(customRadius.dp)
        }
    }

    /**
     * 将任意原始圆角值映射到当前圆角风格。
     *
     * 用于非标圆角（18dp/24dp/28dp 等）跟随风格切换：
     * - Sharp   → 返回 0.dp（直角）
     * - 默认 ROUNDED（xl=12dp 基准）→ 透传 [original]（视觉零变化）
     * - 自定义半径档位 → 按 12dp 基准等比缩放：original / 12 * xl
     *
     * 用法：`RoundedCornerShape(LocalCornerRadius.current.map(18.dp))`
     */
    fun map(original: Dp): Dp {
        if (this == Sharp) return 0.dp
        // 默认 12dp 基准下透传原值；自定义半径下按比例缩放
        if (xl.value == 12f) return original
        return (original.value / 12f * xl.value).dp
    }
}

/**
 * CompositionLocal：当前圆角档位。
 *
 * 用法：`RoundedCornerShape(LocalCornerRadius.current.md)`
 *
 * 由 MiniMeTheme 根据用户 cornerStyle + cornerRadius 设置提供。
 */
val LocalCornerRadius = staticCompositionLocalOf { CornerScale.Rounded }
