package com.mini.me_core.core.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity

/**
 * Pre-rendered multi-layer glow sprite.
 *
 * 3-stop radial gradient baked into one bitmap:
 *   0.00 → bright core (near-white, 100%)
 *   0.15 → particle color at 50% alpha (mid halo)
 *   0.45 → particle color at 20% alpha (outer glow)
 *   1.00 → fully transparent
 *
 * Blitted scaled at each particle position. A crisp solid core is drawn on top.
 */
private fun createGlowSprite(color: Int, sizePx: Int = 128): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f
    val cx = r
    val cy = r

    // Brighten the core by mixing with white for a hot center.
    val cr = (color shr 16) and 0xFF
    val cg = (color shr 8) and 0xFF
    val cb = color and 0xFF
    val hotR = ((cr + 255) / 2).coerceIn(0, 255)
    val hotG = ((cg + 255) / 2).coerceIn(0, 255)
    val hotB = ((cb + 255) / 2).coerceIn(0, 255)
    val hotCore = (0xFF shl 24) or (hotR shl 16) or (hotG shl 8) or hotB

    val midColor = (0x80 shl 24) or (cr shl 16) or (cg shl 8) or cb  // 50% alpha
    val outerColor = (0x33 shl 24) or (cr shl 16) or (cg shl 8) or cb // 20% alpha

    val glow = RadialGradient(
        cx, cy, r,
        intArrayOf(
            hotCore,    // 0.00: hot bright core
            midColor,   // 0.15: mid halo 50%
            outerColor, // 0.45: outer glow 20%
            0x00000000  // 1.00: transparent edge
        ),
        floatArrayOf(0f, 0.15f, 0.45f, 1f),
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

    val primaryArgb = primaryColor.toArgb()
    val onBgArgb = onBackgroundColor.toArgb()
    val bgArgb = backgroundColor.toArgb()

    // Pre-render glow sprites for both colors (larger 128px for softer bloom)
    val primaryGlow = remember(primaryArgb) { createGlowSprite(primaryArgb) }
    val onBgGlow = remember(onBgArgb) { createGlowSprite(onBgArgb) }

    // Pre-compute per-particle tinted colors whenever theme colors change.
    LaunchedEffect(primaryArgb, onBgArgb, particleSystem) {
        particleSystem.precomputeColors(primaryArgb, onBgArgb)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.4f

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // ── Background: solid fill ──
            native.drawColor(bgArgb)

            // ── Background atmosphere: strong radial primary glow at text center ──
            // Center bright → edge dark, adapts automatically to light/dark via colorScheme.
            val centerGlowAlpha = when (stage) {
                SplashStage.EXPLODE -> 0.06f + stageProgress * 0.08f
                SplashStage.CONVERGE -> 0.14f + stageProgress * 0.10f
                SplashStage.HOLD -> 0.22f
                SplashStage.SHATTER -> 0.10f * (1f - stageProgress)
                else -> 0.06f
            }
            val gradientRadius = maxOf(w, h) * 0.9f
            val gradient = RadialGradient(
                cx, cy, gradientRadius,
                primaryColor.copy(alpha = centerGlowAlpha).toArgb(),
                bgArgb,
                Shader.TileMode.CLAMP
            )
            val bgPaint = Paint()
            bgPaint.shader = gradient
            native.drawRect(0f, 0f, w, h, bgPaint)

            // ── Per-frame paints (reused, no allocation) ──
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }
            // Temp RectF for glow blit — reused across iterations.
            val dstRect = RectF()

            val mix = particleSystem.colorMix
            val count = particleSystem.count

            for (i in 0 until count) {
                val px = particleSystem.x[i]
                val py = particleSystem.y[i]
                val baseSz = particleSystem.size[i]
                val scale = particleSystem.sizeScale[i]
                val sz = baseSz * scale
                val accent = particleSystem.isAccent[i]

                // During explosion (mix=0): all particles glow primary.
                // As mix→1: normal particles fade to onBackground, accents stay primary.
                val usePrimaryGlow = accent || mix < 0.5f

                // Glow radius: tuned for fine luminous dots.
                // Subtler when settled (HOLD), puffier during flight.
                val glowMul = if (stage == SplashStage.HOLD) 4.5f else 7f
                val glowRadius = sz * glowMul
                val glowBmp = if (usePrimaryGlow) primaryGlow else onBgGlow

                // Glow alpha: stronger bloom for luminous dot look.
                val glowAlpha = when (stage) {
                    SplashStage.HOLD -> (0.38f + 0.12f * kotlin.math.sin(elapsedMs * 0.005f)).coerceIn(0.3f, 0.5f)
                    SplashStage.EXPLODE -> 0.55f
                    SplashStage.CONVERGE -> 0.55f - 0.15f * mix
                    else -> 0.4f
                }
                glowPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
                dstRect.set(px - glowRadius, py - glowRadius, px + glowRadius, py + glowRadius)
                native.drawBitmap(glowBmp, null, dstRect, glowPaint)

                // Core: crisp solid dot using per-particle tinted color.
                val coreColor = if (usePrimaryGlow) particleSystem.primaryTinted[i]
                                else particleSystem.onBgTinted[i]
                corePaint.color = coreColor
                // Core alpha: punchy when formed, slightly transparent during flight.
                corePaint.alpha = (255 * (0.75f + 0.25f * mix)).toInt().coerceIn(0, 255)
                native.drawCircle(px, py, sz, corePaint)
            }

            // ── Aggregation flash pulse at text formation (start of HOLD, 300ms) ──
            // Strong radial pulse from text center, expanding outward then decaying.
            if (stage == SplashStage.HOLD && stageProgress < 0.6f) {
                val pulseT = stageProgress / 0.6f  // 0→1 over 300ms of 500ms hold
                val pulseAlpha = (1f - pulseT) * 0.35f
                val pulseRadius = maxOf(w, h) * (0.15f + 0.45f * pulseT)
                val pulsePaint = Paint()
                val pulseGlow = RadialGradient(
                    cx, cy, pulseRadius,
                    primaryColor.copy(alpha = pulseAlpha).toArgb(),
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
                pulsePaint.shader = pulseGlow
                native.drawRect(0f, 0f, w, h, pulsePaint)
            }

            // Shimmer sweep during HOLD (subtle light sweep across settled text)
            if (stage == SplashStage.HOLD && quality.enableCracks) {
                val sweepX = cx - w * 0.5f + (w * 1.5f) * stageProgress
                val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                shimmerPaint.color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f).toArgb()
                native.save()
                native.clipRect(sweepX - 80f * density, 0f, sweepX + 80f * density, h)
                native.drawColor(shimmerPaint.color)
                native.restore()
            }

            // ── Refraction / frost overlay at shatter start (400ms) ──
            // Simulates the moment glass fractures and background light refracts.
            // API 31+ additionally gets a real RenderEffect blur applied at the Compose layer;
            // this canvas overlay is the universal fallback.
            if (stage == SplashStage.SHATTER && stageProgress < 0.133f) {
                val frostT = stageProgress / 0.133f  // 0→1 over ~400ms of 3s shatter
                val frostAlpha = (1f - frostT) * 0.18f
                val frostPaint = Paint()
                frostPaint.color = androidx.compose.ui.graphics.Color.White.copy(alpha = frostAlpha).toArgb()
                native.drawRect(0f, 0f, w, h, frostPaint)
            }

            // ── Glass shatter effect ──
            if ((stage == SplashStage.SHATTER || stage == SplashStage.FADE_OUT) && quality.enable3D) {
                val shatterProgress = if (stage == SplashStage.SHATTER) stageProgress else 1f
                glassShatter?.draw(
                    canvas = native,
                    shatterProgress = shatterProgress,
                    alpha = if (stage == SplashStage.FADE_OUT) 1f - stageProgress else 1f,
                )
            }

            // ── Shatter flash: strong white flash (alpha 0.9→0 over 200ms) ──
            // 200ms out of 3000ms shatter = 0.0667 progress window.
            if (stage == SplashStage.SHATTER && stageProgress < 0.0667f) {
                val flashT = stageProgress / 0.0667f
                val flashAlpha = (1f - flashT) * 0.9f
                val flashPaint = Paint()
                flashPaint.color = androidx.compose.ui.graphics.Color.White.copy(alpha = flashAlpha).toArgb()
                native.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }
}
