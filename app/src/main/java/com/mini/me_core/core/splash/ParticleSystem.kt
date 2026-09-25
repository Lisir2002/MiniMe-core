package com.mini.me_core.core.splash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Particle system for the splash animation.
 *
 * Uses flat FloatArrays to avoid per-frame allocation.
 * Particle types: 0 = glowing dot (60%), 1 = rotating square shard (40%).
 *
 * All positions are in pixel coordinates.
 */
class ParticleSystem(
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    density: Float,
) {
    // Dynamic particle count based on screen area.
    val count: Int = run {
        val area = screenWidthPx * screenHeightPx
        // ~1080x2400 = 2.5M px area -> target ~1000 particles
        val estimated = (area / 2500f).toInt()
        estimated.coerceIn(500, 1200)
    }

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
    val size = FloatArray(count)        // radius in px for dots, half-size for shards
    val type = IntArray(count)          // 0=dot, 1=shard
    val rotation = FloatArray(count)    // current rotation for shards
    val rotationSpeed = FloatArray(count)
    private val seed = FloatArray(count) // random seed for micro-vibration

    // Color mix: 0 = mixed explosion colors, 1 = final onBackground color
    var colorMix: Float = 0f
        private set

    private val centerX = screenWidthPx / 2f
    private val centerY = screenHeightPx / 2f

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

            // Explosion: random direction, speed 8-20 dp/frame -> convert to px/s
            val angle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val speedDp = 8f + Random.nextFloat() * 12f  // 8-20 dp/frame
            val speedPxPerSec = speedDp * density * 60f   // per frame at 60fps -> per second
            vx[i] = cos(angle) * speedPxPerSec
            vy[i] = sin(angle) * speedPxPerSec

            // Shatter: outward from center, 12-25 dp/frame
            val shatterAngle = Random.nextFloat() * (2f * Math.PI).toFloat()
            val shatterSpeedDp = 12f + Random.nextFloat() * 13f
            val shatterSpeedPx = shatterSpeedDp * density * 60f
            shatterVx[i] = cos(shatterAngle) * shatterSpeedPx
            shatterVy[i] = sin(shatterAngle) * shatterSpeedPx + 600f * density // gravity bias

            // Type: 60% dots, 40% shards
            type[i] = if (Random.nextFloat() < 0.6f) 0 else 1

            // Size: dots 2-6 dp, shards 3-8 dp
            size[i] = if (type[i] == 0) {
                (2f + Random.nextFloat() * 4f) * density
            } else {
                (3f + Random.nextFloat() * 5f) * density
            }

            rotation[i] = Random.nextFloat() * 360f
            rotationSpeed[i] = (Random.nextFloat() - 0.5f) * 720f // deg/s
            seed[i] = Random.nextFloat() * 1000f
        }
    }

    /**
     * Sample "MiniMe-core" text pixels as target positions.
     * Returns FloatArray of [x0, y0, x1, y1, ...].
     */
    private fun sampleTextTargets(density: Float): FloatArray {
        val text = "MiniMe-core"
        // Text width ~70% of screen width
        val targetWidth = screenWidthPx * 0.7f

        // Find appropriate text size by measuring
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Binary search for text size that fits targetWidth
        var textSize = 80f * density
        paint.textSize = textSize
        var measuredWidth = paint.measureText(text)
        val step = 4f * density
        while (measuredWidth > targetWidth && textSize > 20f * density) {
            textSize -= step
            paint.textSize = textSize
            measuredWidth = paint.measureText(text)
        }
        while (measuredWidth < targetWidth && textSize < 200f * density) {
            textSize += step
            paint.textSize = textSize
            measuredWidth = paint.measureText(text)
        }

        // Create offscreen bitmap for text sampling
        val bitmapW = screenWidthPx.toInt()
        val bitmapH = (textSize * 2.5f).toInt()
        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0) // transparent

        paint.color = android.graphics.Color.WHITE
        canvas.drawText(text, bitmapW / 2f, bitmapH / 2f - (paint.descent() + paint.ascent()) / 2f, paint)

        // Sample non-transparent pixels
        val stepPx = (3.5f * density).toInt().coerceAtLeast(3)
        val points = mutableListOf<Float>()
        val centerOffsetY = centerY - bitmapH / 2f

        for (py in 0 until bitmapH step stepPx) {
            for (px in 0 until bitmapW step stepPx) {
                val pixel = bitmap.getPixel(px, py)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha > 128) {
                    points.add(px.toFloat())
                    points.add(py.toFloat() + centerOffsetY)
                }
            }
        }

        bitmap.recycle()

        // If too many points, thin them
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
        val stage = SplashStage.fromElapsed(elapsedMs)
        val t = SplashStage.stageProgress(elapsedMs)
        val dt = dtMs / 1000f // seconds

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
        // Ease out: velocity decays over time
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

        // Start position is where particles ended up after explode
        // We need to interpolate from current to target
        // Store start positions on first frame of converge
        // Actually, since we interpolate from current to target, we use the converge start positions
        // But we don't have them stored... Let's use a different approach:
        // The explode phase ended at some position. We need to capture that.
        // For simplicity, we'll use the tx/ty and interpolate from a stored start.

        // Actually, let's store converge start positions lazily
        if (!convergeInit) {
            convergeInit = true
            for (i in 0 until count) {
                convergeStartX[i] = x[i]
                convergeStartY[i] = y[i]
            }
        }

        for (i in 0 until count) {
            // Overshoot: add a small spring back at the end
            val overshoot = if (t > 0.85f) {
                val overshootT = (t - 0.85f) / 0.15f
                sin(overshootT * Math.PI).toFloat() * 0.05f
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
        // Breathing: scale 1.0 -> 1.03 -> 1.0, sine period 1s
        val breath = 1f + 0.015f * sin((elapsedMs / 1000f) * 2f * Math.PI).toFloat()

        // Micro vibration: +-1dp
        val vib = 1f // 1dp vibration handled in draw

        // Shimmer sweep: highlight moves left to right
        for (i in 0 until count) {
            // Scale around center
            val dx = tx[i] - centerX
            val dy = ty[i] - centerY
            x[i] = centerX + dx * breath + (sin(seed[i] + elapsedMs * 0.003f) * vib)
            y[i] = centerY + dy * breath + (cos(seed[i] + elapsedMs * 0.0027f) * vib)
        }
        colorMix = 1f
    }

    private fun updateShatter(t: Float, dt: Float) {
        // Particles explode outward from their current positions
        // with gravity pulling down
        val gravity = 1500f // px/s^2
        for (i in 0 until count) {
            x[i] += shatterVx[i] * dt
            shatterVy[i] += gravity * dt
            y[i] += shatterVy[i] * dt
            rotation[i] += rotationSpeed[i] * dt
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
            rotation[i] = Random.nextFloat() * 360f
        }
    }
}
