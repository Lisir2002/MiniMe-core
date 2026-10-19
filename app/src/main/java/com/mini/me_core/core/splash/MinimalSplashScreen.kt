package com.mini.me_core.core.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.ScaledAnimation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.mini.me_core.R

/**
 * Minimal splash style: flowing gradient + "MiniMe" text fade-in, no particles.
 *
 * A1 退场门闩：初始化就绪即退（[awaitReadyThenDismiss]），最短展示 [MIN_DISPLAY_MS] 防闪，
 * 最长 [MINIMAL_TOTAL_MS] 兜底。A3：动效关闭时跳过淡入与漂移，就绪即退。
 */
@Composable
fun MinimalSplashScreen(
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val logoAlpha = remember { Animatable(0f) }
    val animScale = LocalAnimationScale.current
    val animDisabled = animScale <= 0f

    val drift by if (!animDisabled) {
        val infinite = rememberInfiniteTransition(label = "minimal_splash")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = ScaledAnimation.tween(3000, scale = animScale, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradient_drift"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, ScaledAnimation.tween(800, scale = animScale))
        if (animDisabled) {
            AppInitState.awaitReady()
            onDismiss()
        } else {
            awaitReadyThenDismiss(onDismiss, MINIMAL_TOTAL_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MinimalSplashBackground(drift = drift, colors = colors)

        Box(
            modifier = Modifier.align(Alignment.Center).alpha(logoAlpha.value),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.splash_logo_text),
                fontSize = 40.sp,
                color = colors.onBackground,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}

/** Minimal splash 的径向渐变背景（漂移量 [drift] 0..1，0.5 为静态中心）。 */
@Composable
@Suppress("FunctionNaming") // @Composable 函数按项目惯例使用 PascalCase，detekt 已知误报
private fun MinimalSplashBackground(
    drift: Float,
    colors: ColorScheme,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f + (drift - 0.5f) * w * 0.1f
        val cy = h * 0.4f + (drift - 0.5f) * h * 0.05f
        val radius = maxOf(w, h) * 0.8f

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.primary.copy(alpha = 0.15f),
                    colors.background
                ),
                center = Offset(cx, cy),
                radius = radius
            ),
            size = Size(w, h)
        )
    }
}
