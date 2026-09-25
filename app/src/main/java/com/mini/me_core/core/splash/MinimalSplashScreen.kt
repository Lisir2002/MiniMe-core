package com.mini.me_core.core.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Minimal splash style: flowing gradient + "MiniMe" text fade-in, no particles.
 * Duration ~2.5s, then dismisses.
 */
@Composable
fun MinimalSplashScreen(
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val logoAlpha = remember { Animatable(0f) }

    val infinite = rememberInfiniteTransition(label = "minimal_splash")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_drift"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(800))
        delay(2500)
        onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                size = androidx.compose.ui.geometry.Size(w, h)
            )
        }

        Box(
            modifier = Modifier.align(Alignment.Center).alpha(logoAlpha.value),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "MiniMe",
                fontSize = 40.sp,
                color = colors.onBackground,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
    }
}
