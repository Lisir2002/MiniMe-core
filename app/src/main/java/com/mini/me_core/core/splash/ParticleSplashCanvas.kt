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
 * 1. Radial gradient background (brighter in center)
 * 2. Particles (glowing dots + rotating shards)
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
) {
    val stage = SplashStage.fromElapsed(elapsedMs)
    val stageProgress = SplashStage.stageProgress(elapsedMs)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // ── Background: base color ──
            native.drawColor(backgroundColor.toArgb())

            // ── Radial gradient overlay ──
            val centerGlowAlpha = when (stage) {
                SplashStage.CONVERGE -> 0.15f
                SplashStage.HOLD -> 0.15f
                SplashStage.EXPLODE -> 0.08f + stageProgress * 0.07f
                else -> 0.08f
            }
            val gradient = RadialGradient(
                cx, cy,
                kotlin.math.max(w, h) * 0.6f,
                primaryColor.copy(alpha = centerGlowAlpha).toArgb(),
                backgroundColor.toArgb(),
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint()
            bgPaint.shader = gradient
            native.drawRect(0f, 0f, w, h, bgPaint)

            // ── Draw particles ──
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            shardPaint.style = Paint.Style.FILL

            val mix = particleSystem.colorMix
            val explosionColor = primaryColor.toArgb()
            val targetColor = onBackgroundColor.toArgb()

            for (i in 0 until particleSystem.count) {
                val px = particleSystem.x[i]
                val py = particleSystem.y[i]
                val sz = particleSystem.size[i]

                // Interpolate color between explosion color and target color
                val r = ((explosionColor shr 16 and 0xFF) * (1 - mix) + (targetColor shr 16 and 0xFF) * mix).toInt()
                val g = ((explosionColor shr 8 and 0xFF) * (1 - mix) + (targetColor shr 8 and 0xFF) * mix).toInt()
                val b = ((explosionColor and 0xFF) * (1 - mix) + (targetColor and 0xFF) * mix).toInt()
                val a = (255 * (0.7f + 0.3f * mix)).toInt()
                val color = (a shl 24) or (r shl 16) or (g shl 8) or b

                if (particleSystem.type[i] == 0) {
                    // Glowing dot: outer glow + inner core
                    dotPaint.color = color
                    dotPaint.alpha = (a * 0.3f).toInt()
                    native.drawCircle(px, py, sz * 2f, dotPaint)
                    dotPaint.alpha = a
                    native.drawCircle(px, py, sz, dotPaint)
                } else {
                    // Rotating square shard
                    shardPaint.color = color
                    shardPaint.alpha = a
                    native.save()
                    native.rotate(particleSystem.rotation[i], px, py)
                    native.drawRect(px - sz, py - sz, px + sz, py + sz, shardPaint)
                    native.restore()
                }
            }

            // ── Shimmer sweep during HOLD ──
            if (stage == SplashStage.HOLD) {
                val sweepX = cx - w * 0.5f + (w * 1.5f) * stageProgress
                val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                shimmerPaint.color = Color.White.copy(alpha = 0.08f).toArgb()
                native.save()
                native.clipRect(sweepX - 80f * density, 0f, sweepX + 80f * density, h)
                native.drawColor(shimmerPaint.color)
                native.restore()
            }

            // ── Glass shatter effect ──
            if (stage == SplashStage.SHATTER || stage == SplashStage.FADE_OUT) {
                val shatterProgress = if (stage == SplashStage.SHATTER) stageProgress else 1f
                val crackProgress = if (stage == SplashStage.SHATTER) {
                    (stageProgress / 0.2f).coerceAtMost(1f)
                } else 1f

                glassShatter?.draw(
                    canvas = native,
                    shatterProgress = shatterProgress,
                    contentBitmap = null,
                    crackProgress = crackProgress,
                    alpha = if (stage == SplashStage.FADE_OUT) 1f - stageProgress else 1f,
                )
            }

            // ── White flash at shatter start ──
            if (stage == SplashStage.SHATTER && stageProgress < 0.05f) {
                val flashAlpha = (1f - stageProgress / 0.05f) * 0.6f
                val flashPaint = Paint()
                flashPaint.color = Color.White.copy(alpha = flashAlpha).toArgb()
                native.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }
}
