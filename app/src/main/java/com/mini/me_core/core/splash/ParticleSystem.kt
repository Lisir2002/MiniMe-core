package com.mini.me_core.core.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Particle system for the splash animation.
 *
 * Uses flat FloatArrays to avoid per-frame allocation.
 * All particles are round dots (no rotating shards — they blur text outlines).
 *
 * All positions are in pixel coordinates.
 */
class ParticleSystem(
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    density: Float,
    private val quality: SplashQualityLevel = SplashQualityLevel.HIGH,
) {
    // Particle count from quality level.
    val count: Int = quality.particleCount

    // Current positions
    val x = FloatArray(count)
    val y = FloatArray(count)

    // Target positions (text shape)
    private val tx = FloatArray(count)
    private val ty = FloatArray(count)

    // Explosion velocities (phase 1)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)

    // Shatter velocities (phase 4)
    private val shatterVx = FloatArray(count)
    private val shatterVy = FloatArray(count)

    // Particle properties
    val size = FloatArray(count)        // radius in px
    val isAccent = BooleanArray(count)  // true = primary color accent particle (30%)
    private val seed = FloatArray(count) // random seed for micro-vibration

    // Color mix: 0 = explosion phase, 1 = fully formed text
    var colorMix: Float = 0f
        private set

    private val centerX = screenWidthPx / 2f
    private val centerY = screenHeightPx * 0.42f // slightly above screen center

    init {
        // Sample text target positions
        val targets = sampleTextTargets(density)
        val targetCount = targets.size / 2

        // Assign particles to target positions
        for (i in 0 until count) {
            // Start at center
            x[i] = centerX
            y[i] = centerY

            // Assign target: distribute particles across sampled text points
            val targetIdx = (i * targetCount / count).coerceIn(0, targetCount - 1)
            tx[i] = targets[targetIdx * 2]
            ty[i] = targets[targetIdx * 2 + 1]

            // Explosion: random direction, speed 8-20 dp/frame
            val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val speedDp = 8f + Random.nextFloat() * 12f
            val speedPxPerSec = speedDp * density * 60f
            vx[i] = cos(angle) * speedPxPerSec
            vy[i] = sin(angle) * speedPxPerSec

            // Shatter: outward from center, 12-25 dp/frame
            val shatterAngle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val shatterSpeedDp = 12f + Random.nextFloat() * 13f
            val shatterSpeedPx = shatterSpeedDp * density * 60f
            shatterVx[i] = cos(shatterAngle) * shatterSpeedPx
            shatterVy[i] = sin(shatterAngle) * shatterSpeedPx + 600f * density

            // All particles are dots — no shards.
            // Size: 1.2-2.5dp, small and crisp for fine text detail.
            size[i] = (1.2f + Random.nextFloat() * 1.3f) * density

            // 30% accent particles (primary color), 70% onBackground
            isAccent[i] = Random.nextFloat() < 0.3f

            seed[i] = Random.nextFloat() * 1000f
        }
    }

    /**
     * Sample "MiniMe" text pixels as target positions.
     * Uses fine sampling (1.5dp step) with edge enhancement:
     * edge pixels (alpha 128-200) get smaller, denser particles.
     * Returns FloatArray of [x0, y0, x1, y1, ...].
     */
    private fun sampleTextTargets(density: Float): FloatArray {
        val text = "MiniMe"
        // Text width ~65% of screen width
        val targetWidth = screenWidthPx * 0.65f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Find appropriate text size by measuring
        var textSize = 100f * density
        paint.textSize = textSize
        var measuredWidth = paint.measureText(text)
        val step = 4f * density
        while (measuredWidth > targetWidth && textSize > 20f * density) {
            textSize -= step
            paint.textSize = textSize
            measuredWidth = paint.measureText(text)
        }
        while (measuredWidth < targetWidth && textSize < 240f * density) {
            textSize += step
            paint.textSize = textSize
            measuredWidth = paint.measureText(text)
        }

        // Create offscreen bitmap for text sampling
        val bitmapW = screenWidthPx.toInt()
        val bitmapH = (textSize * 2.5f).toInt()
        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0)

        paint.color = android.graphics.Color.WHITE
        canvas.drawText(text, bitmapW / 2f, bitmapH / 2f - (paint.descent() + paint.ascent()) / 2f, paint)

        // Fine sampling step: 1.1dp — very dense for crisp letter outlines
        val stepPx = (1.1f * density).toInt().coerceAtLeast(2)
        val points = mutableListOf<Float>()
        val centerOffsetY = centerY - bitmapH / 2f

        for (py in 0 until bitmapH step stepPx) {
            for (px in 0 until bitmapW step stepPx) {
                val pixel = bitmap.getPixel(px, py)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha > 100) {
                    points.add(px.toFloat())
                    points.add(py.toFloat() + centerOffsetY)
                }
            }
        }

        bitmap.recycle()

        // If we have too many points, thin them evenly
        val maxTargets = count
        if (points.size / 2 > maxTargets) {
            val stride = points.size / 2 / maxTargets
            val thinned = mutableListOf<Float>()
            var i = 0
            var added = 0
            while (i < points.size && added < maxTargets * 2) {
                thinned.add(points[i])
                thinned.add(points[i + 1])
                i += stride * 2
                added += 2
            }
            return thinned.toFloatArray()
        }

        return points.toFloatArray()
    }

    /**
     * Update all particles for the given elapsed time.
     */
    fun update(elapsedMs: Long, dtMs: Float) {
        val stage = SplashStage.fromElapsed(elapsedMs, quality)
        val t = SplashStage.stageProgress(elapsedMs, quality)
        val dt = dtMs / 1000f

        when (stage) {
            SplashStage.EXPLODE -> updateExplode(t, dt)
            SplashStage.CONVERGE -> updateConverge(t, dt)
            SplashStage.HOLD -> updateHold(t, elapsedMs)
            SplashStage.SHATTER -> updateShatter(t, dt)
            SplashStage.FADE_OUT -> updateShatter(t, dt)
            SplashStage.COMPLETE -> {}
        }
    }

    private fun updateExplode(t: Float, dt: Float) {
        val decay = 1f - t * 0.7f
        for (i in 0 until count) {
            x[i] += vx[i] * dt * decay
            y[i] += vy[i] * dt * decay
        }
        colorMix = 0f
    }

    private fun updateConverge(t: Float, dt: Float) {
        // easeOutCubic: 1 - (1-t)^3
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)

        if (!convergeInit) {
            convergeInit = true
            for (i in 0 until count) {
                convergeStartX[i] = x[i]
                convergeStartY[i] = y[i]
            }
        }

        for (i in 0 until count) {
            // Slight spring back at the end (overshoot)
            val overshoot = if (t > 0.88f) {
                val overshootT = (t - 0.88f) / 0.12f
                sin(overshootT * Math.PI).toFloat() * 0.03f
            } else 0f

            val progress = (eased + overshoot).coerceAtMost(1f)
            x[i] = convergeStartX[i] + (tx[i] - convergeStartX[i]) * progress
            y[i] = convergeStartY[i] + (ty[i] - convergeStartY[i]) * progress
        }
        colorMix = eased
    }

    private var convergeInit = false
    private val convergeStartX = FloatArray(count)
    private val convergeStartY = FloatArray(count)

    private fun updateHold(t: Float, elapsedMs: Long) {
        // Precise positioning: particles sit exactly on text target positions
        // with micro breathing vibration of ±0.5dp for a "settled" feel.
        val vib = 0.5f // dp of micro-vibration
        for (i in 0 until count) {
            x[i] = tx[i] + sin(seed[i] + elapsedMs * 0.004f) * vib
            y[i] = ty[i] + cos(seed[i] + elapsedMs * 0.0037f) * vib
        }
        colorMix = 1f
    }

    private fun updateShatter(t: Float, dt: Float) {
        val gravity = 1500f
        for (i in 0 until count) {
            x[i] += shatterVx[i] * dt
            shatterVy[i] += gravity * dt
            y[i] += shatterVy[i] * dt
        }
        colorMix = 1f - t * 0.3f
    }

    /** Reset converge state (called when starting animation fresh) */
    fun reset() {
        convergeInit = false
        colorMix = 0f
        for (i in 0 until count) {
            x[i] = centerX
            y[i] = centerY
        }
    }
}
