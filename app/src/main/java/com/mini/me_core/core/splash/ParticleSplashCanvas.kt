package com.mini.me_core.core.splash

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * Canvas layer that renders the particle splash animation.
 *
 * Draws:
 * 1. Solid background fill + radial gradient overlay
 * 2. Particle dots (inner core + outer glow)
 * 3. Glass shatter effect overlay
 * 4. White flash at shatter moment
 */
@Composable
fun ParticleSplashCanvas(
    modifier: Modifier,
    particleSystem: ParticleSystem,
    glassShatter: GlassShatterEffect?,
    elapsedMs: Long,
    backgroundColor: Color,
    primaryColor: Color,
    onBackgroundColor: Color,
    density: Float,
    quality: SplashQualityLevel = SplashQualityLevel.HIGH,
) {
    val stage = SplashStage.fromElapsed(elapsedMs, quality)
    val stageProgress = SplashStage.stageProgress(elapsedMs, quality)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.4f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Solid background fill first
            native.drawColor(backgroundColor.toArgb())

            // Radial gradient overlay — radius 1.2x max dimension for seamless coverage
            val centerGlowAlpha = when (stage) {
                SplashStage.CONVERGE -> 0.12f
                SplashStage.HOLD -> 0.12f
                SplashStage.EXPLODE -> 0.06f + stageProgress * 0.06f
                else -> 0.06f
            }
            val gradientRadius = kotlin.math.max(w, h) * 1.2f
            val gradient = RadialGradient(
                cx, cy,
                gradientRadius,
                primaryColor.copy(alpha = centerGlowAlpha).toArgb(),
                backgroundColor.toArgb(),
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint()
            bgPaint.shader = gradient
            native.drawRect(0f, 0f, w, h, bgPaint)

            // Draw particles (round dots only)
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            val mix = particleSystem.colorMix
            val explosionArgb = primaryColor.toArgb()
            val onBgArgb = onBackgroundColor.toArgb()

            val exR = explosionArgb shr 16 and 0xFF
            val exG = explosionArgb shr 8 and 0xFF
            val exB = explosionArgb and 0xFF
            val obR = onBgArgb shr 16 and 0xFF
            val obG = onBgArgb shr 8 and 0xFF
            val obB = onBgArgb and 0xFF

            for (i in 0 until particleSystem.count) {
                val px = particleSystem.x[i]
                val py = particleSystem.y[i]
                val sz = particleSystem.size[i]
                val accent = particleSystem.isAccent[i]

                val targetR = if (accent) exR else obR
                val targetG = if (accent) exG else obG
                val targetB = if (accent) exB else obB

                val r = (exR * (1 - mix) + targetR * mix).toInt()
                val g = (exG * (1 - mix) + targetG * mix).toInt()
                val b = (exB * (1 - mix) + targetB * mix).toInt()

                val alpha = (255 * (0.65f + 0.35f * mix)).toInt()
                val color = (alpha shl 24) or (r shl 16) or (g shl 8) or b

                // Outer glow
                dotPaint.color = color
                dotPaint.alpha = (alpha * 0.3f).toInt()
                native.drawCircle(px, py, sz * 2f, dotPaint)
                // Inner core
                dotPaint.alpha = alpha
                native.drawCircle(px, py, sz, dotPaint)
            }

            // Shimmer sweep during HOLD
            if (stage == SplashStage.HOLD && quality.enableCracks) {
                val sweepX = cx - w * 0.5f + (w * 1.5f) * stageProgress
                val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                shimmerPaint.color = Color.White.copy(alpha = 0.06f).toArgb()
                native.save()
                native.clipRect(sweepX - 80f * density, 0f, sweepX + 80f * density, h)
                native.drawColor(shimmerPaint.color)
                native.restore()
            }

            // Glass shatter effect (skipped on LOW quality)
            if ((stage == SplashStage.SHATTER || stage == SplashStage.FADE_OUT) && quality.enable3D) {
                val shatterProgress = if (stage == SplashStage.SHATTER) stageProgress else 1f
                val crackProgress = if (quality.enableCracks && stage == SplashStage.SHATTER) {
                    (stageProgress / 0.2f).coerceAtMost(1f)
                } else if (quality.enableCracks) 1f else 0f

                glassShatter?.draw(
                    canvas = native,
                    shatterProgress = shatterProgress,
                    contentBitmap = null,
                    crackProgress = crackProgress,
                    alpha = if (stage == SplashStage.FADE_OUT) 1f - stageProgress else 1f,
                )
            }

            // White flash at shatter start (100ms fade)
            if (stage == SplashStage.SHATTER && stageProgress < 0.1f) {
                val flashAlpha = (1f - stageProgress / 0.1f) * 0.5f
                val flashPaint = Paint()
                flashPaint.color = Color.White.copy(alpha = flashAlpha).toArgb()
                native.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }
}
