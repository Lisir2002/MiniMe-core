package com.mini.me_core.core.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * 全局动画缩放持有者：用于非 Composable 上下文（如 NavHost 过渡动画）。
 * 由 MiniMeTheme 在 Composition 中同步更新。
 */
object AnimationScaleHolder {
    @Volatile
    var scale: Float = 1.0f
        internal set
}

/**
 * 全局动画缩放工具：所有动效统一走这里，确保「动效强度」设置覆盖全应用。
 *
 * 设计原则：
 *  - scale = 0  → 所有 tween 替换为 duration=0（瞬间完成，无动画）
 *  - scale = 1  → 正常速度
 *  - scale > 1  → 更慢（调试用）
 *  - scale < 0  → 视为 0
 *
 * spring 动画没有固定时长，通过调整 stiffness（刚度）间接缩放。
 */
object ScaledAnimation {

    /** 计算缩放后的时长（毫秒）。scale<=0 返回 0。 */
    @Composable
    fun duration(millis: Int): Int = duration(millis, LocalAnimationScale.current)

    /** 计算缩放后的时长（毫秒），非 Composable 版本。 */
    fun duration(millis: Int, scale: Float): Int {
        if (scale <= 0f) return 0
        return (millis * scale).roundToInt().coerceAtLeast(0)
    }

    /** 缩放版 tween（Composable 上下文）。 */
    @Composable
    fun <T> tween(
        millis: Int,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): TweenSpec<T> = tweenImpl(millis, delayMillis, easing, LocalAnimationScale.current)

    /** 缩放版 tween（非 Composable，使用全局 holder 的 scale）。用于 NavHost 过渡动画等。 */
    fun <T> tweenGlobal(
        millis: Int,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): TweenSpec<T> = tweenImpl(millis, delayMillis, easing, AnimationScaleHolder.scale)

    /** 缩放版 tween（手动传入 scale）。 */
    fun <T> tween(
        millis: Int,
        scale: Float,
        delayMillis: Int = 0,
        easing: Easing = FastOutSlowInEasing
    ): TweenSpec<T> = tweenImpl(millis, delayMillis, easing, scale)

    private fun <T> tweenImpl(
        millis: Int,
        delayMillis: Int,
        easing: Easing,
        scale: Float
    ): TweenSpec<T> {
        if (scale <= 0f) {
            return tween(durationMillis = 0, delayMillis = 0, easing = LinearEasing)
        }
        val scaledDuration = (millis * scale).roundToInt().coerceAtLeast(0)
        val scaledDelay = (delayMillis * scale).roundToInt().coerceAtLeast(0)
        return tween(durationMillis = scaledDuration, delayMillis = scaledDelay, easing = easing)
    }

    /** 缩放版 spring（Composable 上下文）。 */
    @Composable
    fun <T> spring(
        dampingRatio: Float = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        stiffness: Float = androidx.compose.animation.core.Spring.StiffnessMedium,
        visibilityThreshold: T? = null
    ): AnimationSpec<T> = springImpl(dampingRatio, stiffness, visibilityThreshold, LocalAnimationScale.current)

    /** 缩放版 spring（手动传入 scale）。 */
    fun <T> spring(
        dampingRatio: Float,
        stiffness: Float,
        scale: Float,
        visibilityThreshold: T? = null
    ): AnimationSpec<T> = springImpl(dampingRatio, stiffness, visibilityThreshold, scale)

    @Suppress("UNCHECKED_CAST")
    private fun <T> springImpl(
        dampingRatio: Float,
        stiffness: Float,
        visibilityThreshold: T?,
        scale: Float
    ): AnimationSpec<T> {
        val effectiveStiffness = if (scale <= 0f) {
            androidx.compose.animation.core.Spring.StiffnessHigh * 10f
        } else {
            (stiffness / scale).coerceAtLeast(1f)
        }
        return if (visibilityThreshold != null) {
            androidx.compose.animation.core.spring(dampingRatio = dampingRatio, stiffness = effectiveStiffness, visibilityThreshold = visibilityThreshold)
        } else {
            androidx.compose.animation.core.spring(dampingRatio = dampingRatio, stiffness = effectiveStiffness)
        } as AnimationSpec<T>
    }

    /** 当前是否禁用了动画（scale<=0）。 */
    @Composable
    fun isDisabled(): Boolean = LocalAnimationScale.current <= 0f

    /** 当前是否禁用了动画，非 Composable 版本。 */
    fun isDisabled(scale: Float): Boolean = scale <= 0f
}
