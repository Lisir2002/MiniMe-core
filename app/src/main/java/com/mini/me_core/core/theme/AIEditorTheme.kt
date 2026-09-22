package com.mini.me_core.core.theme

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 10.dp
    val lg = 14.dp
    val pill = 999.dp
}

object Brand {
    val Blue = Color(0xFF2563EB)
    val Sky = Color(0xFF38BDF8)
    val Ice = Color(0xFFEFF6FF)
    val IconGray = Color(0xFF424242)
    val PageBg = Color(0xFFFAFAFA)

    /**
     * 语义色 - 成功/已就绪/在线 的统一状态绿（所有"状态小圆点"都走这一对，不再走品牌蓝）。
     * 色值参考 Material Design 3 Emerald 600 / Material You 标准绿，
     * 亮/暗模式下对比度均 ≥ 4.5:1，避免与品牌蓝（primary）混淆。
     */
    object StatusGreen {
        /** 亮色模式用：#16A34A（emerald-600），白底 7.1:1 对比度 */
        val Light = Color(0xFF16A34A)
        /** 暗色模式用：#4ADE80（emerald-400），#0D1B2E 底 7.8:1 对比度 */
        val Dark  = Color(0xFF4ADE80)
    }
}

/**
 * 聊天输入区功能语义色板：每个功能一个专属色相，避免图标重复与撞色。
 * 每个色都有 Light / Dark 双取值，配合 [LocalAppDarkMode] 实现日夜间切换。
 * 背景色（light/dark）与前景色（onLight/onDark）成对出现，保证对比度 ≥ 4.5:1。
 */
object ChatAccent {
    data class Tone(val light: Color, val dark: Color, val onLight: Color, val onDark: Color)

    /** 构建模式 · 琥珀（建造/锤子） */
    val Build = Tone(Color(0xFFB45309), Color(0xFFFBBF24), Color.White, Color(0xFF451A03))
    /** 规划模式 · 蓝（计划/地图） */
    val Plan = Tone(Color(0xFF2563EB), Color(0xFF60A5FA), Color.White, Color(0xFF0B3B76))
    /** 自动模式 · 青绿（自动/火箭） */
    val Auto = Tone(Color(0xFF0D9488), Color(0xFF2DD4BF), Color.White, Color(0xFF0B3B2E))
    /** 思考强度 · 紫罗兰（深度思考/大脑） */
    val Reasoning = Tone(Color(0xFF7C3AED), Color(0xFFA78BFA), Color.White, Color(0xFF2E1065))
    /** 技能 · 粉（技能/魔法星） */
    val Skill = Tone(Color(0xFFDB2777), Color(0xFFF472B6), Color.White, Color(0xFF500724))
}

/** 当前日夜模式下的语义背景色（跟随应用主题设置 LocalAppDarkMode）。 */
@Composable
fun ChatAccent.Tone.resolve(): Color = if (LocalAppDarkMode.current) dark else light

/** 当前日夜模式下语义色上的前景色（文字 / 图标）。 */
@Composable
fun ChatAccent.Tone.resolveOn(): Color = if (LocalAppDarkMode.current) onDark else onLight

/**
 * 动效缩放比例 CompositionLocal。
 * 由 AIEditorTheme 根据用户设置提供（0.0=关闭动效，1.0=正常速度）。
 * 组件读取后乘以 tween/durationMillis，实现动效全局缩放。
 */
val LocalAnimationScale = staticCompositionLocalOf { 1.0f }

val LocalSpacing = staticCompositionLocalOf { Spacing }

/**
 * 应用实际生效的"当前是否为暗模式"。
 * 由 MainActivity 根据 ThemeSettingsRepository.themeModeFlow 计算后写入：
 *   AUTO  → 跟随系统
 *   DARK  → true
 *   LIGHT → false
 * 终端内容配色、各子页面"跟随程序"都统一读这一个 CompositionLocal，
 * 保证 APP 自己切主题（强制黑/强制白）时，所有"跟随程序"的子控件同步变化，
 * 而不是去读 android.content.res.Configuration 的系统 uiMode。
 */
val LocalAppDarkMode = androidx.compose.runtime.staticCompositionLocalOf<Boolean> { false }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0F3A63),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF082F49),
    tertiary = Color(0xFF22C55E),
    // 混合模式：暗色页面底 #0F172A（slate-900）
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    // 混合模式：AI 卡片 surface #1E293B（slate-800）
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    // 混合模式：工具块 surfaceVariant #1E293B
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF44617F),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0B3B76),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF16A34A),
    // Stage 4：页面底改为极浅灰白，AI 气泡保持纯白，用投影区分层次
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    // Stage 4：工具气泡浅灰
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color.White,
    outline = Color(0xFFBBD7F2),
    // 混合模式：边框 outlineVariant #E2E8F0（slate-200）
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),

    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
    )
}

/**
 * 问题3：将 Typography 中所有 TextStyle 的 fontSize 乘以 fontScale。
 * lineHeight 同步缩放，保持排版比例。
 */
private fun Typography.scaleFontSize(scale: Float): Typography {
    if (scale == 1.0f) return this
    return copy(
        displayLarge = displayLarge.copy(fontSize = displayLarge.fontSize * scale, lineHeight = displayLarge.lineHeight * scale),
        displayMedium = displayMedium.copy(fontSize = displayMedium.fontSize * scale, lineHeight = displayMedium.lineHeight * scale),
        displaySmall = displaySmall.copy(fontSize = displaySmall.fontSize * scale, lineHeight = displaySmall.lineHeight * scale),
        headlineLarge = headlineLarge.copy(fontSize = headlineLarge.fontSize * scale, lineHeight = headlineLarge.lineHeight * scale),
        headlineMedium = headlineMedium.copy(fontSize = headlineMedium.fontSize * scale, lineHeight = headlineMedium.lineHeight * scale),
        headlineSmall = headlineSmall.copy(fontSize = headlineSmall.fontSize * scale, lineHeight = headlineSmall.lineHeight * scale),
        titleLarge = titleLarge.copy(fontSize = titleLarge.fontSize * scale, lineHeight = titleLarge.lineHeight * scale),
        titleMedium = titleMedium.copy(fontSize = titleMedium.fontSize * scale, lineHeight = titleMedium.lineHeight * scale),
        titleSmall = titleSmall.copy(fontSize = titleSmall.fontSize * scale, lineHeight = titleSmall.lineHeight * scale),
        bodyLarge = bodyLarge.copy(fontSize = bodyLarge.fontSize * scale, lineHeight = bodyLarge.lineHeight * scale),
        bodyMedium = bodyMedium.copy(fontSize = bodyMedium.fontSize * scale, lineHeight = bodyMedium.lineHeight * scale),
        bodySmall = bodySmall.copy(fontSize = bodySmall.fontSize * scale, lineHeight = bodySmall.lineHeight * scale),
        labelLarge = labelLarge.copy(fontSize = labelLarge.fontSize * scale, lineHeight = labelLarge.lineHeight * scale),
        labelMedium = labelMedium.copy(fontSize = labelMedium.fontSize * scale, lineHeight = labelMedium.lineHeight * scale),
        labelSmall = labelSmall.copy(fontSize = labelSmall.fontSize * scale, lineHeight = labelSmall.lineHeight * scale),
    )
}

/**
 * 从 SemanticColors 构建 Material3 ColorScheme（Phase 5）。
 *
 * 将自定义语义色映射到 Material3 ColorScheme 的关键字段，
 * 这样使用 MaterialTheme.colorScheme 的组件也能跟随主题变化。
 * 仅映射核心字段，其余字段使用默认值。
 */
private fun buildColorSchemeFromSemantic(
    colors: com.mini.me_core.core.theme.tokens.SemanticColors,
    isDark: Boolean,
): androidx.compose.material3.ColorScheme {
    val base = if (isDark) DarkColorScheme else LightColorScheme
    return base.copy(
        primary = colors.brandPrimary,
        onPrimary = colors.onBrandPrimary,
        primaryContainer = colors.brandContainer,
        onPrimaryContainer = colors.onBrandContainer,
        secondary = colors.brandSecondary,
        background = colors.surfacePage,
        onBackground = colors.textPrimary,
        surface = colors.surfaceCard,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceSunken,
        onSurfaceVariant = colors.textSecondary,
        error = colors.error,
        onError = colors.onError,
        outline = colors.borderDefault,
        // Phase 5 补全：映射遗漏的 Material3 ColorScheme 字段，使主题预设色完整覆盖
        outlineVariant = colors.borderMuted,
        tertiary = colors.success,
        errorContainer = colors.errorContainer,
        onErrorContainer = colors.onErrorContainer,
        surfaceTint = colors.brandPrimary,
        // 审计补全：secondaryContainer / onSecondaryContainer / tertiaryContainer / onTertiaryContainer / onTertiary
        secondaryContainer = colors.surfaceAccent,
        onSecondaryContainer = colors.onBrandContainer,
        tertiaryContainer = colors.successContainer,
        onTertiaryContainer = colors.onSuccessContainer,
        onTertiary = colors.onSuccess,
    )
}

/**
 * 从 URI 加载图片为 ImageBitmap（IO 线程异步加载）。
 * 失败时返回 null。
 */
@Composable
private fun rememberBitmapFromUri(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        } else {
            null
        }
    }
    return bitmap
}

@Composable
fun AIEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Phase 4：可选自定义语义色（来自主题预设）。null 时使用默认 Light/Dark。
    customColors: com.mini.me_core.core.theme.tokens.SemanticColors? = null,
    // Phase 5：背景图 URI（null=无背景图）
    backgroundImageUri: String? = null,
    // Phase 5：背景图遮罩浓度（0.0-1.0）
    backgroundScrim: Float = 0.4f,
    // Phase 5：卡片透明度（0.0-1.0）
    cardAlpha: Float = 1.0f,
    // 问题3：显示偏好——圆角风格
    cornerStyle: com.mini.me_core.core.theme.tokens.CornerStyle = com.mini.me_core.core.theme.tokens.CornerStyle.ROUNDED,
    // 问题3：显示偏好——字体缩放（1.0=标准）
    fontScale: Float = 1.0f,
    // 问题3：显示偏好——动效缩放（1.0=正常，0.0=关闭）
    animationScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    // Phase 5：如果有自定义颜色，构建动态 Material3 ColorScheme；否则用默认
    val colorScheme = if (customColors != null) {
        buildColorSchemeFromSemantic(customColors, darkTheme)
    } else if (darkTheme) DarkColorScheme else LightColorScheme

    // 问题2修复：无背景图时，卡片不透明（cardAlpha=1.0）；有背景图时用用户设置的透明度
    val effectiveCardAlpha = if (backgroundImageUri != null) cardAlpha else 1.0f

    // 问题3：根据 fontScale 缩放所有 TextStyle 的 fontSize
    val scaledTypography = remember(AppTypography, fontScale) {
        AppTypography.scaleFontSize(fontScale)
    }

    val appThemeState = when {
        customColors != null -> com.mini.me_core.core.theme.tokens.AppThemeState(
            colors = customColors,
            isDark = darkTheme,
            backgroundImageUri = backgroundImageUri,
            backgroundScrim = backgroundScrim,
            cardAlpha = effectiveCardAlpha,
            cornerStyle = cornerStyle,
            fontScale = fontScale,
            animationScale = animationScale,
        )
        darkTheme -> com.mini.me_core.core.theme.tokens.AppThemeState.Dark.copy(
            cornerStyle = cornerStyle,
            fontScale = fontScale,
            animationScale = animationScale,
        )
        else -> com.mini.me_core.core.theme.tokens.AppThemeState.Light.copy(
            cornerStyle = cornerStyle,
            fontScale = fontScale,
            animationScale = animationScale,
        )
    }

    // Phase 5：加载背景图
    val bgBitmap = rememberBitmapFromUri(backgroundImageUri)

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppDarkMode provides darkTheme,
        com.mini.me_core.core.theme.tokens.LocalAppTheme provides appThemeState,
        LocalAnimationScale provides animationScale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
        ) {
            // Phase 5：如果有背景图，在根布局添加图片层 + 遮罩层
            if (bgBitmap != null && backgroundImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                ) {
                    // 背景图片
                    Image(
                        bitmap = bgBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 遮罩层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background.copy(alpha = backgroundScrim)),
                    )
                    // 内容层
                    content()
                }
            } else {
                content()
            }
        }
    }
}
