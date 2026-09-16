package com.mini.me_core.newui.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius

/**
 * iOS 简约风映射（主风格定版）：
 *  - 浅色：系统分组底 #F2F2F7 + 白色成员卡片 #FFFFFF，一级/二级文字分级，主强调 iOS 蓝 #0A84FF；
 *  - 深色：纯黑系统底 #000000 + 分组卡片 #1C1C1E，文字白，强调蓝提亮 #3D9BFF；
 *  - 分隔线统一走 Separator 令牌，卡片靠底色分区而非阴影（简约去 Elevation 靠内容分层）。
 */
private val AppLightScheme = lightColorScheme(
    primary = AppColor.BrandPrimary,
    onPrimary = AppColor.OnPrimary,
    background = AppColor.BrandSurface,
    onBackground = AppColor.BrandInk,
    surface = AppColor.BrandCard,
    onSurface = AppColor.BrandInk,
    surfaceVariant = AppColor.BrandSurfaceDim,
    onSurfaceVariant = AppColor.LabelSecondary,
    secondary = AppColor.BrandAccent,
    onSecondary = AppColor.OnPrimary,
    error = AppColor.StatusDanger,
    onError = AppColor.OnPrimary,
    outline = AppColor.SeparatorOnLight,
)

private val AppDarkScheme = darkColorScheme(
    primary = AppColor.OnDarkPrimary,
    onPrimary = AppColor.OnPrimaryDark,
    background = AppColor.OnDarkSurface,
    onBackground = AppColor.OnDarkInk,
    surface = AppColor.OnDarkSurfaceRaised,
    onSurface = AppColor.OnDarkInk,
    surfaceVariant = AppColor.SeparatorOnDark,
    onSurfaceVariant = AppColor.OnDarkSecondaryLabel,
    secondary = AppColor.BrandPrimary,
    onSecondary = AppColor.OnPrimaryDark,
    error = AppColor.StatusDanger,
    onError = AppColor.OnPrimary,
    outline = AppColor.SeparatorOnDark,
)

/** 圆角令牌 → M3 Shapes（§3.10 对齐 M3 形状尺度） */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppRadius.Sm),
    small = RoundedCornerShape(AppRadius.Sm),
    medium = RoundedCornerShape(AppRadius.Md),
    large = RoundedCornerShape(AppRadius.Lg),
    extraLarge = RoundedCornerShape(AppRadius.Lg),
)

/** 独立排版族：iOS 类型尺度（AppType），页面统一走 MaterialTheme.typography 直达。 */
val AppTypography: Typography = AppType.Material

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) AppDarkScheme else AppLightScheme
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}