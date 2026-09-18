package com.mini.me_core.newui.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mini.me_core.newui.designsystem.token.generated.AppColor

/**
 * 暗色感知调色板：组件层禁止再直接引用 `AppColor.Brand*` / `SeparatorOnLight` 等浅色硬编码常量，
 * 一律走 [appPalette] 或 `LocalAppPalette.current`，由 [AppTheme] 按明暗模式注入对应值。
 *
 * 语义色（StatusSuccess/Warning/Danger/Info）明暗通用，不在此 Palette 内，仍直接用 `AppColor.Status*`。
 */
data class AppPalette(
    val primary: Color,
    val onPrimary: Color,
    val surface: Color,
    val surfaceDim: Color,
    val card: Color,
    val ink: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val separator: Color,
    val accent: Color,
    val primaryOverlay12: Color,
    val primaryOverlay14: Color,
)

val LightPalette = AppPalette(
    primary = AppColor.BrandPrimary,
    onPrimary = AppColor.OnPrimary,
    surface = AppColor.BrandSurface,
    surfaceDim = AppColor.BrandSurfaceDim,
    card = AppColor.BrandCard,
    ink = AppColor.BrandInk,
    labelSecondary = AppColor.LabelSecondary,
    labelTertiary = AppColor.LabelTertiary,
    separator = AppColor.SeparatorOnLight,
    accent = AppColor.BrandAccent,
    primaryOverlay12 = AppColor.PrimaryOverlay12,
    primaryOverlay14 = AppColor.PrimaryOverlay14,
)

val DarkPalette = AppPalette(
    primary = AppColor.OnDarkPrimary,
    onPrimary = AppColor.OnPrimaryDark,
    surface = AppColor.OnDarkSurface,
    surfaceDim = AppColor.OnDarkSurfaceDim,
    card = AppColor.OnDarkSurfaceRaised,
    ink = AppColor.OnDarkInk,
    labelSecondary = AppColor.OnDarkSecondaryLabel,
    labelTertiary = AppColor.OnDarkSecondaryLabel,
    separator = AppColor.SeparatorOnDark,
    accent = AppColor.OnDarkAccent,
    primaryOverlay12 = AppColor.PrimaryOverlay12Dark,
    primaryOverlay14 = AppColor.PrimaryOverlay14Dark,
)

val LocalAppPalette = staticCompositionLocalOf { LightPalette }

/** @Composable 包装：默认参数与函数体内均可安全取色。 */
@Composable
fun appPalette(): AppPalette = LocalAppPalette.current
