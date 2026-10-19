package com.mini.me_core.core.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.ScaledAnimation

/**
 * Classic splash style: static gradient background + "MiniMe" logo fade-in.
 *
 * 正名实现：原 CLASSIC 与 OFF 同行为（无动画），名不副实；
 * 现实现为「静态 Logo 淡入」——无粒子、无漂移动画，只有一次 800ms 淡入。
 *
 * 退场门闩与 [MinimalSplashScreen] 一致：就绪即退 + 最短展示 + [MINIMAL_TOTAL_MS] 硬上限；
 * 动效关闭时跳过淡入，就绪即退。
 */
@Composable
@Suppress("FunctionNaming") // @Composable 函数按项目惯例使用 PascalCase，detekt 已知误报
fun ClassicSplashScreen(
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val logoAlpha = remember { Animatable(0f) }
    val animScale = LocalAnimationScale.current
    val animDisabled = animScale <= 0f

    LaunchedEffect(Unit) {
        if (!animDisabled) {
            logoAlpha.animateTo(1f, ScaledAnimation.tween(800, scale = animScale, easing = LinearEasing))
        } else {
            logoAlpha.snapTo(1f)
        }
        if (animDisabled) {
            // 关闭动效——无淡入，就绪即退。
            AppInitState.awaitReady()
            onDismiss()
        } else {
            // 就绪即退 + 最短展示 + 硬上限。
            awaitReadyThenDismiss(onDismiss, MINIMAL_TOTAL_MS)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 静态径向渐变背景（无漂移动画——与 MINIMAL 的区别即在于此）。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.15f),
                        colors.background
                    ),
                    center = Offset(w / 2f, h * 0.4f),
                    radius = maxOf(w, h) * 0.8f
                ),
                size = Size(w, h)
            )
        }

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
