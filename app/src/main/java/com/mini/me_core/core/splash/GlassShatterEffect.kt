package com.mini.me_core.core.splash

import android.graphics.Camera
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single glass shard.
 *
 * Properties:
 * - polygon: Voronoi cell vertices
 * - centerX/centerY: centroid for rotation pivot
 * - outwardX/outwardY: explosion direction
 * - rotateXDeg/rotateYDeg: target 3D rotation
 * - speedX/speedY: initial explosion velocity (dp/s)
 * - scale: target scale during flight
 * - baseAlpha: base transparency (0.5-0.8)
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
)

/**
 * Glass shatter effect — real glass material look.
 *
 * Each shard:
 * 1. Draws a soft shadow offset below
 * 2. Semi-transparent white fill (alpha 0.5-0.75)
 * 3. LinearGradient overlay: top bright → bottom transparent (refraction)
 * 4. 1.5px edge highlight (white, alpha 0.7)
 * 5. Small specular highlight (radial gradient at shard corner)
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

    // Shard fill paint — semi-transparent glass
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    // Edge highlight — bright glass edge reflection
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    // Shadow paint
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

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
            // Glass transparency: varies per shard
            val baseAlpha = 0.5f + Random.nextFloat() * 0.25f

            // Specular highlight position (random corner of shard)
            val specX = Random.nextFloat()
            val specY = Random.nextFloat()

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

            val path = Path()
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

            // Shadow (offset down-right, dark translucent)
            shadowPaint.alpha = (40 * alpha * (1f - shatterProgress)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(6f, 10f)
            canvas.drawPath(path, shadowPaint)
            canvas.restore()

            // Glass fill: semi-transparent white
            fillPaint.alpha = (shard.baseAlpha * 255 * alpha * faceDim).toInt().coerceIn(0, 255)
            fillPaint.color = Color.WHITE
            canvas.drawPath(path, fillPaint)

            // LinearGradient overlay on shard: top bright → bottom transparent
            // Simulates glass refraction / light passing through
            val polyTop = shard.polygon.minByOrNull { it } ?: 0f
            val polyBottom = shard.polygon.maxByOrNull { it } ?: 100f
            val gradient = LinearGradient(
                scx, polyTop, scx, polyBottom,
                Color.argb((80 * faceDim).toInt(), 255, 255, 255).toInt(),
                Color.argb(0, 255, 255, 255).toInt(),
                Shader.TileMode.CLAMP
            )
            fillPaint.shader = gradient
            fillPaint.alpha = (shard.baseAlpha * 200 * alpha * faceDim).toInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
            fillPaint.shader = null

            // Edge highlight: bright white glass edge
            edgePaint.alpha = (180 * alpha * faceDim).toInt().coerceIn(0, 255)
            canvas.drawPath(path, edgePaint)

            canvas.restore()
        }
    }
}
