package com.mini.me_core.core.theme.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用主题状态。
 *
 * 包含语义色、暗色模式标志、背景图相关参数。
 * Phase 0：只实现 colors + isDark，背景图参数预留接口（Phase 4 实现）。
 *
 * 不可变，主题切换时整体替换。
 */
@Immutable
data class AppThemeState(
    val colors: SemanticColors,
    val isDark: Boolean,
    // ── 背景图相关（Phase 4 实现，当前为默认值）──
    val backgroundImageUri: String? = null,
    val backgroundScrim: Float = PrimitiveAlpha.Scrim,
    val cardAlpha: Float = PrimitiveAlpha.Card,
    // ── 显示偏好（问题3：全链路消费）──
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val fontScale: Float = 1.0f,
    val animationScale: Float = 1.0f,
) {
    companion object {
        val Light = AppThemeState(colors = SemanticColors.Light, isDark = false)
        val Dark = AppThemeState(colors = SemanticColors.Dark, isDark = true)
    }
}

/**
 * CompositionLocal：在 Composable 树中提供 AppThemeState。
 *
 * 用法：`val theme = LocalAppTheme.current`
 * 然后 `theme.colors.surfaceCard` 等。
 */
val LocalAppTheme = compositionLocalOf { AppThemeState.Light }

/**
 * 便捷扩展：在 Composable 中直接获取当前主题。
 *
 * 用法：`AppTheme.colors.surfaceCard`
 */
object AppTheme {
    val colors: SemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.colors

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.isDark
}

/**
 * 应用主题 Provider。
 *
 * Phase 0：包裹现有 MaterialTheme，同时提供 LocalAppTheme。
 * 现有 `AIEditorTheme()` 调用点零改动，内部委托给本 Provider。
 *
 * @param isDark 是否暗色模式（由现有 LocalAppDarkMode 传入）
 * @param content 子内容
 */
@Composable
fun AppThemeProvider(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val themeState = if (isDark) AppThemeState.Dark else AppThemeState.Light

    CompositionLocalProvider(
        LocalAppTheme provides themeState,
    ) {
        content()
    }
}

/**
 * 便捷函数：获取指定模式下的 SemanticColors。
 * 用于非 Composable 上下文（如 ViewModel 中计算颜色）。
 */
fun semanticColorsFor(isDark: Boolean): SemanticColors =
    if (isDark) SemanticColors.Dark else SemanticColors.Light

/**
 * 将颜色应用透明度。
 * 用于背景图模式下的 surface 颜色自动注入 alpha。
 */
fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)
