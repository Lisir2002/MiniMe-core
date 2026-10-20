package org.minime.core.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.minime.core.fallback.FallbackTheme
import org.minime.core.tokens.ColorTokens
import org.minime.core.tokens.ElevationTokens
import org.minime.core.tokens.GlassTokens
import org.minime.core.tokens.InteractionTokens
import org.minime.core.tokens.MotionTokens
import org.minime.core.tokens.OpacityTokens
import org.minime.core.tokens.ShapeTokens
import org.minime.core.tokens.SizeTokens
import org.minime.core.tokens.SpacingTokens
import org.minime.core.tokens.ThemeSnapshot
import org.minime.core.tokens.TypographyTokens

/**
 * 十子系统提供层（§6.2）——组件唯一消费面。
 *
 * **为什么用 `staticCompositionLocalOf`**：换肤是全树数据替换（§8.1 约定 1 直切），
 * 值一变则整棵子树重组是**预期行为**；`static` 只记录「读了该 local 的组合」，
 * 比 `compositionLocalOf`（逐个记录依赖）在全局变更时更快、开销更低。
 *
 * 默认值为内置 fallback 快照对应子系统——**任何组合都能取到值**，
 * 未包裹 `MiniMeTheme` 也不会崩（预览与独立 composable 场景友好）。
 */
object MiniMeTokens {

    val LocalColor: ProvidableCompositionLocal<ColorTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.color }

    val LocalTypography: ProvidableCompositionLocal<TypographyTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.typography }

    val LocalShape: ProvidableCompositionLocal<ShapeTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.shape }

    val LocalSize: ProvidableCompositionLocal<SizeTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.size }

    val LocalSpacing: ProvidableCompositionLocal<SpacingTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.spacing }

    val LocalMotion: ProvidableCompositionLocal<MotionTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.motion }

    val LocalInteraction: ProvidableCompositionLocal<InteractionTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.interaction }

    val LocalElevation: ProvidableCompositionLocal<ElevationTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.elevation }

    val LocalOpacity: ProvidableCompositionLocal<OpacityTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.opacity }

    val LocalGlass: ProvidableCompositionLocal<GlassTokens> =
        staticCompositionLocalOf { FallbackTheme.snapshot.glass }
}

/**
 * 快照整体（调试面板 / Gallery / 工坊用）。
 *
 * 组件**不要**直接读它——读子系统 local 才能在「该子系统未变」时跳过重组；
 * 只有需要跨子系统取值的场景（调试面板显示版本与修饰符）才读这个。
 */
val LocalThemeSnapshot: ProvidableCompositionLocal<ThemeSnapshot> =
    staticCompositionLocalOf { FallbackTheme.snapshot }
