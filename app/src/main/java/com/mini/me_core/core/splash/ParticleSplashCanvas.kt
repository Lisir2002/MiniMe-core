package com.mini.me_core.core.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity

/**
 * Pre-rendered glow sprite: a small radial-gradient bitmap for efficient particle glow.
 * Draw once, then blit scaled at each particle position.
 */
private fun createGlowSprite(color: Int, sizePx: Int = 64): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f
    val cx = r
    val cy = r
    val glow = RadialGradient(
        cx, cy, r,
        intArrayOf(
            color,                           // center: full color
            (color and 0x00FFFFFF) or 0x66000000.toInt(), // 40% alpha at 50% radius
            0x00000000                       // fully transparent at edge
        ),
        floatArrayOf(0f, 0.4f, 1f),
        Shader.TileMode.CLAMP
    )
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = glow
    canvas.drawCircle(cx, cy, r, p)
    return bmp
}

@Composable
fun ParticleSplashCanvas(
    modifier: Modifier,
    particleSystem: ParticleSystem,
    glassShatter: GlassShatterEffect?,
    elapsedMs: Long,
    backgroundColor: androidx.compose.ui.graphics.Color,
    primaryColor: androidx.compose.ui.graphics.Color,
    onBackgroundColor: androidx.compose.ui.graphics.Color,
    density: Float,
    quality: SplashQualityLevel = SplashQualityLevel.HIGH,
) {
    val stage = SplashStage.fromElapsed(elapsedMs, quality)
    val stageProgress = SplashStage.stageProgress(elapsedMs, quality)

    // Pre-render glow sprites for both colors
    val primaryArgb = primaryColor.toArgb()
    val onBgArgb = onBackgroundColor.toArgb()
    val primaryGlow = remember(primaryArgb) { createGlowSprite(primaryArgb) }
    val onBgGlow = remember(onBgArgb) { createGlowSprite(onBgArgb) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.4f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Solid background fill
            native.drawColor(backgroundColor.toArgb())

            // Radial gradient overlay
            val centerGlowAlpha = when (stage) {
                SplashStage.CONVERGE -> 0.15f
                SplashStage.HOLD -> 0.12f
                SplashStage.EXPLODE -> 0.06f + stageProgress * 0.06f
                else -> 0.06f
            }
            val gradientRadius = maxOf(w, h) * 1.2f
            val gradient = RadialGradient(
                cx, cy, gradientRadius,
                primaryColor.copy(alpha = centerGlowAlpha).toArgb(),
                backgroundColor.toArgb(),
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint()
            bgPaint.shader = gradient
            native.drawRect(0f, 0f, w, h, bgPaint)

            // ── Draw particles with glow sprites ──
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }

            val mix = particleSystem.colorMix

            for (i in 0 until particleSystem.count) {
                val px = particleSystem.x[i]
                val py = particleSystem.y[i]
                val sz = particleSystem.size[i]
                val accent = particleSystem.isAccent[i]

                // Determine which glow sprite to use
                // During explosion (mix=0): all particles glow primary color
                // As mix→1: normal particles fade to onBackground glow, accents stay primary
                val usePrimaryGlow = accent || mix < 0.5f

                // Glow radius: 4x particle size for soft halo
                val glowRadius = sz * 4f
                val glowBmp = if (usePrimaryGlow) primaryGlow else onBgGlow

                // Glow alpha: stronger during explode, softer when formed
                val glowAlpha = if (stage == SplashStage.HOLD) {
                    (0.25f + 0.1f * kotlin.math.sin(elapsedMs * 0.005f)).coerceIn(0.15f, 0.4f)
                } else {
                    0.35f + 0.2f * (1f - mix)
                }
                glowPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
                val dstRect = RectF(px - glowRadius, py - glowRadius, px + glowRadius, py + glowRadius)
                native.drawBitmap(glowBmp, null, dstRect, glowPaint)

                // Core: bright solid circle
                val coreColor = if (usePrimaryGlow) primaryArgb else onBgArgb
                corePaint.color = coreColor
                corePaint.alpha = (255 * (0.7f + 0.3f * mix)).toInt()
                native.drawCircle(px, py, sz, corePaint)
            }

            // Text glow pulse at start of HOLD
            if (stage == SplashStage.HOLD && stageProgress < 0.3f) {
                val pulseAlpha = (1f - stageProgress / 0.3f) * 0.2f
                val pulsePaint = Paint()
                val pulseGlow = RadialGradient(
                    cx, cy, maxOf(w, h) * 0.4f,
                    primaryColor.copy(alpha = pulseAlpha).toArgb(),
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
                pulsePaint.shader = pulseGlow
                native.drawRect(0f, 0f, w, h, pulsePaint)
            }

            // Shimmer sweep during HOLD
            if (stage == SplashStage.HOLD && quality.enableCracks) {
                val sweepX = cx - w * 0.5f + (w * 1.5f) * stageProgress
                val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                shimmerPaint.color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f).toArgb()
                native.save()
                native.clipRect(sweepX - 80f * density, 0f, sweepX + 80f * density, h)
                native.drawColor(shimmerPaint.color)
                native.restore()
            }

            // Glass shatter effect
            if ((stage == SplashStage.SHATTER || stage == SplashStage.FADE_OUT) && quality.enable3D) {
                val shatterProgress = if (stage == SplashStage.SHATTER) stageProgress else 1f
                glassShatter?.draw(
                    canvas = native,
                    shatterProgress = shatterProgress,
                    alpha = if (stage == SplashStage.FADE_OUT) 1f - stageProgress else 1f,
                )
            }

            // Flash at shatter start (150ms, alpha 0.8→0)
            if (stage == SplashStage.SHATTER && stageProgress < 0.15f) {
                val flashAlpha = (1f - stageProgress / 0.15f) * 0.8f
                val flashPaint = Paint()
                flashPaint.color = androidx.compose.ui.graphics.Color.White.copy(alpha = flashAlpha).toArgb()
                native.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }
}
