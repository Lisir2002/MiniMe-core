package com.mini.me_core.core.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import kotlinx.coroutines.isActive

/**
 * Particle splash screen host.
 *
 * Handles:
 * - F1.1: Quality detection → particle count / duration
 * - F1.5: Upgraded skip button (pill shape, outline, pressed scale, last-2s error color)
 * - F1.6: Cross-fade transition during FADE_OUT stage
 * - F1.7: Bottom progress bar + stage text (5 init phases)
 *
 * @param onDismiss Called when animation completes (or skip is pressed).
 */
@Composable
fun ParticleSplashScreen(
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    // Detect device quality level
    val quality = remember { SplashQualityLevel.detect(context) }

    // Animation elapsed time in ms
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var isDismissing by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(1f) }

    // Screen size
    var screenWidthPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableStateOf(0f) }

    // Particle system and glass shatter
    var particleSystem by remember { mutableStateOf<ParticleSystem?>(null) }
    var glassShatter by remember { mutableStateOf<GlassShatterEffect?>(null) }

    // Skip button press state
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val skipScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "skip_scale"
    )
    var skipButtonVisible by remember { mutableStateOf(true) }

    // Countdown seconds
    val remainingSec = SplashStage.remainingSeconds(elapsedMs, quality)
    val isUrgent = remainingSec <= 2 && remainingSec > 0

    // Main animation loop
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (screenWidthPx <= 0f || screenHeightPx <= 0f) return@LaunchedEffect
        if (particleSystem == null) {
            particleSystem = ParticleSystem(screenWidthPx, screenHeightPx, density, quality)
            glassShatter = if (quality.enable3D) {
                GlassShatterEffect(screenWidthPx, screenHeightPx, density, quality.shardCount)
            } else null
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

            if (elapsedMs >= SplashStage.totalDurationMs(quality)) {
                isDismissing = true
            }
        }
    }

    // F1.6: Cross-fade — overlay fades out, main content underneath fades in
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            skipButtonVisible = false
            overlayAlpha.animateTo(0f, tween(500))
            onDismiss()
        }
    }

    // F1.7: Progress percentage — simulated init progress mapped to animation timeline
    val progress = (elapsedMs.toFloat() / SplashStage.totalDurationMs(quality).toFloat()).coerceIn(0f, 1f)
    val stageResId = when {
        progress < 0.20f -> R.string.splash_stage_data
        progress < 0.45f -> R.string.splash_stage_ai
        progress < 0.70f -> R.string.splash_stage_terminal
        progress < 0.85f -> R.string.splash_stage_browser
        else -> R.string.splash_stage_ui
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
                quality = quality,
            )
        }

        // F1.7: Bottom progress indicator
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(stageResId),
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(200.dp)
                    .height(2.dp),
                color = colors.primary,
                trackColor = colors.onSurfaceVariant.copy(alpha = 0.2f),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = colors.onSurfaceVariant,
            )
        }

        // F1.5: Upgraded skip button (pill shape, top-right)
        if (skipButtonVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 16.dp)
                    .scale(skipScale),
                color = colors.surface.copy(alpha = if (isUrgent) 0.8f else 0.5f),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                onClick = {
                    skipButtonVisible = false
                    isDismissing = true
                },
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.splash_skip),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "$remainingSec",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUrgent) colors.error else colors.primary,
                        )
                    }
                }
            }
        }
    }
}
