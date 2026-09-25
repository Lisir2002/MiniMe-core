package com.mini.me_core.core.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import kotlinx.coroutines.isActive

/**
 * Particle splash screen host.
 *
 * Manages the 6-second animation state machine, skip button with countdown,
 * and fade-out transition. Overlays on top of the main app navigation.
 *
 * @param onDismiss Called after the 300ms fade-out completes; the caller should remove this overlay.
 */
@Composable
fun ParticleSplashScreen(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme

    // Animation elapsed time in ms
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isDismissing by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(1f) }

    // Screen size
    var screenWidthPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableStateOf(0f) }

    // Particle system and glass shatter (created once when screen size is known)
    var particleSystem by remember { mutableStateOf<ParticleSystem?>(null) }
    var glassShatter by remember { mutableStateOf<GlassShatterEffect?>(null) }

    // Skip button countdown
    val remainingSec = SplashStage.remainingSeconds(elapsedMs)

    // Main animation loop: driven by Compose frame clock
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f) return@LaunchedEffect
        if (particleSystem == null) {
            particleSystem = ParticleSystem(screenWidthPx, screenHeightPx, density)
            glassShatter = GlassShatterEffect(screenWidthPx, screenHeightPx, density)
        }

        var startNanos = 0L
        var lastNanos = 0L
        while (isActive && !isDismissing) {
            val frameNanos = withFrameNanos { it }
            if (startNanos == 0L) {
                startNanos = frameNanos
                lastNanos = frameNanos
            }
            val dt = (frameNanos - lastNanos) / 1_000_000f
            lastNanos = frameNanos
            elapsedMs = (frameNanos - startNanos) / 1_000_000L
            particleSystem?.update(elapsedMs, dt)

            if (elapsedMs >= SplashStage.TOTAL_DURATION_MS) {
                isDismissing = true
            }
        }
    }

    // Fade out and dismiss
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            overlayAlpha.animateTo(0f, tween(300))
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .onSizeChanged { size ->
                screenWidthPx = size.width.toFloat()
                screenHeightPx = size.height.toFloat()
            },
    ) {
        // Canvas layer
        val ps = particleSystem
        if (ps != null) {
            ParticleSplashCanvas(
                modifier = Modifier.fillMaxSize(),
                particleSystem = ps,
                glassShatter = glassShatter,
                elapsedMs = elapsedMs,
                backgroundColor = colors.background,
                primaryColor = colors.primary,
                onBackgroundColor = colors.onBackground,
                density = density,
            )
        }

        // Skip button (top right, always on top)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp),
            color = colors.surface.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 0.dp,
            onClick = { isDismissing = true },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.splash_skip) + " $remainingSec",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
