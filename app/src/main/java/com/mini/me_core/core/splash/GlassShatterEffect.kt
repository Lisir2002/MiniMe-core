package com.mini.me_core.core.splash

import android.graphics.BlurMaskFilter
import android.graphics.Camera
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single glass shard.
 */
class GlassShard(
    val polygon: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val outwardX: Float,
    val outwardY: Float,
    val rotateXDeg: Float,
    val rotateYDeg: Float,
    val speedDp: Float,
    val scaleTarget: Float,
    val baseAlpha: Float,
    val specularX: Float, // relative offset for specular highlight (0-1)
    val specularY: Float,
    val tailLengthDp: Float, // length of flying light trail behind shard
)

/**
 * Glass shatter effect — realistic glass material.
 *
 * Each shard renders 5 layers:
 * 1. Soft blurred shadow (BlurMaskFilter, offset 4dp x 6dp, alpha 0.15)
 * 2. High-transparency fill (alpha 0.12–0.25) — see-through glass
 * 3. Edge stroke with LinearGradient: top bright white (0.9) → bottom dark gray (0.3)
 *    — simulates glass thickness / beveled edge
 * 4. Diagonal specular band: LinearGradient from corner to opposite corner,
 *    white 0.6 → transparent — light reflection
 * 5. Flying light tail: gradient trail along motion direction (20–40dp, 0.4→0)
 *
 * 3D: Camera.rotateX/rotateY with back-face dimming.
 */
class GlassShatterEffect(
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    density: Float,
    private val targetShardCount: Int = 35,
) {
    val shards: List<GlassShard>

    private val camera = Camera()
    private val matrix = Matrix()

    // Glass fill paint — high transparency
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // Edge stroke — gradient bevel (shader set per shard)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    // Soft shadow — blurred, low alpha
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        maskFilter = BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL)
    }
    // Specular / tail paint
    private val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Shadow offset in px
    private val shadowDx = 4f * density
    private val shadowDy = 6f * density

    // Reusable path (cleared per shard)
    private val path = Path()

    init {
        camera.setLocation(0f, 0f, -(screenHeightPx / density / 72f * 2f))

        val seedCount = targetShardCount
        val seedsX = FloatArray(seedCount)
        val seedsY = FloatArray(seedCount)
        for (i in 0 until seedCount) {
            seedsX[i] = Random.nextFloat() * screenWidthPx
            seedsY[i] = Random.nextFloat() * screenHeightPx
        }

        val screenCx = screenWidthPx / 2f
        val screenCy = screenHeightPx * 0.4f
        val shardList = mutableListOf<GlassShard>()

        for (i in 0 until seedCount) {
            var polyX = floatArrayOf(0f, 0f, screenWidthPx, screenWidthPx, 0f)
            var polyY = floatArrayOf(0f, screenHeightPx, screenHeightPx, 0f, 0f)

            for (j in 0 until seedCount) {
                if (i == j) continue
                val ax = seedsX[i]; val ay = seedsY[i]
                val bx = seedsX[j]; val by = seedsY[j]
                val dx = bx - ax; val dy = by - ay
                val pcx = (ax + bx) / 2f; val pcy = (ay + by) / 2f
                val (newX, newY) = clipHalfPlane(polyX, polyY, dx, dy, pcx, pcy)
                if (newX.size < 3) break
                polyX = newX; polyY = newY
            }

            if (polyX.size < 6) continue

            var sumX = 0f; var sumY = 0f
            val n = polyX.size
            for (k in 0 until n) { sumX += polyX[k]; sumY += polyY[k] }
            val scx = sumX / n; val scy = sumY / n

            var ox = scx - screenCx
            var oy = scy - screenCy
            val len = kotlin.math.sqrt(ox * ox + oy * oy)
            if (len > 0.001f) { ox /= len; oy /= len } else { ox = 0f; oy = 1f }

            // Random 3D rotation per shard
            val rotMag = 30f + Random.nextFloat() * 60f
            val rotX = (Random.nextFloat() - 0.5f) * 2f * rotMag
            val rotY = (Random.nextFloat() - 0.5f) * 2f * rotMag

            // Explosion speed: foreground faster
            val speedDp = 120f + Random.nextFloat() * 180f
            // Scale down during flight
            val scaleTarget = 0.6f + Random.nextFloat() * 0.3f
            // Glass transparency: HIGH transparency (0.12–0.25) — real see-through glass
            val baseAlpha = 0.12f + Random.nextFloat() * 0.13f

            // Specular highlight position (random corner of shard)
            val specX = Random.nextFloat()
            val specY = Random.nextFloat()

            // Flying tail length: 20–40dp
            val tailLen = 20f + Random.nextFloat() * 20f

            val flatPoly = FloatArray(n * 2)
            for (k in 0 until n) {
                flatPoly[k * 2] = polyX[k]
                flatPoly[k * 2 + 1] = polyY[k]
            }

            shardList.add(
                GlassShard(
                    polygon = flatPoly,
                    centerX = scx,
                    centerY = scy,
                    outwardX = ox,
                    outwardY = oy,
                    rotateXDeg = rotX,
                    rotateYDeg = rotY,
                    speedDp = speedDp,
                    scaleTarget = scaleTarget,
                    baseAlpha = baseAlpha,
                    specularX = specX,
                    specularY = specY,
                    tailLengthDp = tailLen,
                )
            )
        }

        shardList.shuffle()
        shards = shardList
    }

    private fun clipHalfPlane(
        polyX: FloatArray, polyY: FloatArray,
        dx: Float, dy: Float,
        cx: Float, cy: Float
    ): Pair<FloatArray, FloatArray> {
        val outX = mutableListOf<Float>()
        val outY = mutableListOf<Float>()
        val n = polyX.size

        fun inside(x: Float, y: Float) = (x - cx) * dx + (y - cy) * dy <= 0

        for (i in 0 until n) {
            val j = (i + 1) % n
            val xi = polyX[i]; val yi = polyY[i]
            val xj = polyX[j]; val yj = polyY[j]
            val iIn = inside(xi, yi)
            val jIn = inside(xj, yj)
            if (iIn) { outX.add(xi); outY.add(yi) }
            if (iIn != jIn) {
                val t = ((cx - xi) * dx + (cy - yi) * dy) / ((xj - xi) * dx + (yj - yi) * dy)
                outX.add(xi + t * (xj - xi))
                outY.add(yi + t * (yj - yi))
            }
        }
        return Pair(outX.toFloatArray(), outY.toFloatArray())
    }

    fun draw(
        canvas: android.graphics.Canvas,
        shatterProgress: Float,
        alpha: Float,
    ) {
        if (shatterProgress <= 0f) return

        // Ease out cubic for motion
        val ease = 1f - (1f - shatterProgress) * (1f - shatterProgress) * (1f - shatterProgress)
        // Gravity increases over time
        val gravity = 1200f * ease

        for (shard in shards) {
            // Displacement: damped velocity
            val displace = ease * shard.speedDp
            val tx = shard.outwardX * displace
            val ty = shard.outwardY * displace + gravity * 0.3f

            // Scale: shrink from 1.0 to scaleTarget
            val scale = 1f + (shard.scaleTarget - 1f) * ease

            // ── Flying light tail: gradient trail behind shard along motion direction ──
            // Drawn before the shard (so shard sits on top of tail).
            val tailLen = shard.tailLengthDp * ease * 3f
            if (tailLen > 1f) {
                val tailStartX = shard.centerX + tx
                val tailStartY = shard.centerY + ty
                val tailEndX = tailStartX - shard.outwardX * tailLen
                val tailEndY = tailStartY - shard.outwardY * tailLen
                val tailGradient = LinearGradient(
                    tailStartX, tailStartY, tailEndX, tailEndY,
                    Color.argb((100 * alpha * (1f - shatterProgress)).toInt().coerceIn(0, 255), 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
                specPaint.shader = tailGradient
                specPaint.strokeWidth = 2f
                specPaint.style = Paint.Style.STROKE
                val tailPath = Path()
                tailPath.moveTo(tailStartX, tailStartY)
                tailPath.lineTo(tailEndX, tailEndY)
                canvas.drawPath(tailPath, specPaint)
                specPaint.shader = null
            }

            // 3D rotation
            camera.save()
            camera.rotateX(shard.rotateXDeg * ease)
            camera.rotateY(shard.rotateYDeg * ease)

            val scx = shard.centerX
            val scy = shard.centerY
            matrix.reset()
            camera.getMatrix(matrix)
            matrix.preScale(scale, scale, scx, scy)
            matrix.preTranslate(-scx, -scy)
            matrix.postTranslate(scx + tx, scy + ty)
            camera.restore()

            // Build path from polygon vertices
            path.reset()
            path.moveTo(shard.polygon[0], shard.polygon[1])
            for (k in 1 until shard.polygon.size / 2) {
                path.lineTo(shard.polygon[k * 2], shard.polygon[k * 2 + 1])
            }
            path.close()

            canvas.save()
            canvas.concat(matrix)

            // Back-face dimming: when rotated past 90°, shard "back" is darker
            val backFace = (kotlin.math.abs(shard.rotateXDeg * ease) > 89f ||
                    kotlin.math.abs(shard.rotateYDeg * ease) > 89f)
            val faceDim = if (backFace) 0.4f else 1f

            // ── 1. Soft blurred shadow ──
            shadowPaint.alpha = (38 * alpha * (1f - shatterProgress * 0.7f)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(shadowDx, shadowDy)
            canvas.drawPath(path, shadowPaint)
            canvas.restore()

            // ── 2. High-transparency glass fill ──
            fillPaint.alpha = (shard.baseAlpha * 255 * alpha * faceDim).toInt().coerceIn(0, 255)
            fillPaint.color = Color.WHITE
            canvas.drawPath(path, fillPaint)

            // ── 3. Beveled edge stroke: LinearGradient top bright → bottom dark ──
            // Find polygon bounds in local (matrix-applied) space for gradient.
            var polyTop = Float.MAX_VALUE
            var polyBottom = -Float.MAX_VALUE
            var polyLeft = Float.MAX_VALUE
            var polyRight = -Float.MAX_VALUE
            for (k in 0 until shard.polygon.size / 2) {
                val vx = shard.polygon[k * 2]
                val vy = shard.polygon[k * 2 + 1]
                if (vy < polyTop) polyTop = vy
                if (vy > polyBottom) polyBottom = vy
                if (vx < polyLeft) polyLeft = vx
                if (vx > polyRight) polyRight = vx
            }
            // Edge gradient: bright white top (0.9 alpha) → dark gray bottom (0.3 alpha)
            val edgeGradient = LinearGradient(
                scx, polyTop, scx, polyBottom,
                Color.argb((230 * faceDim).toInt().coerceIn(0, 255), 255, 255, 255),
                Color.argb((76 * faceDim).toInt().coerceIn(0, 255), 60, 60, 60),
                Shader.TileMode.CLAMP
            )
            edgePaint.shader = edgeGradient
            edgePaint.alpha = (255 * alpha * faceDim).toInt().coerceIn(0, 255)
            canvas.drawPath(path, edgePaint)
            edgePaint.shader = null

            // ── 4. Diagonal specular highlight band ──
            // From one corner (based on specularX/Y) to opposite corner.
            val sx = polyLeft + (polyRight - polyLeft) * shard.specularX
            val sy = polyTop + (polyBottom - polyTop) * shard.specularY
            val ex = polyLeft + (polyRight - polyLeft) * (1f - shard.specularX)
            val ey = polyTop + (polyBottom - polyTop) * (1f - shard.specularY)
            val specGradient = LinearGradient(
                sx, sy, ex, ey,
                Color.argb((150 * faceDim).toInt().coerceIn(0, 255), 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            specPaint.shader = specGradient
            specPaint.alpha = (200 * alpha * faceDim).toInt().coerceIn(0, 255)
            specPaint.style = Paint.Style.FILL
            canvas.drawPath(path, specPaint)
            specPaint.shader = null

            canvas.restore()
        }
    }
}
