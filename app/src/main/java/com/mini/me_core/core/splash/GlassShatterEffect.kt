package com.mini.me_core.core.splash

import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single glass shard from the Voronoi tessellation.
 *
 * Each shard belongs to a depth layer:
 * - FOREGROUND: large rotation, far displacement, flies first
 * - MID: medium rotation and displacement
 * - BACKGROUND: small rotation, near displacement, flies last
 */
enum class ShardLayer(val rotationRange: ClosedFloatingPointRange<Float>, val displacementDp: ClosedFloatingPointRange<Float>) {
    FOREGROUND(45f..90f, 100f..200f),
    MID(20f..45f, 50f..100f),
    BACKGROUND(0f..20f, 20f..50f);
}

class GlassShard(
    val polygon: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val outwardX: Float,
    val outwardY: Float,
    val layer: ShardLayer,
    val rotateXDeg: Float,
    val rotateYDeg: Float,
    val zDepth: Float,
)

/**
 * Glass shatter effect using Voronoi tessellation + Camera 3D transforms.
 *
 * Shards are split into 3 depth layers for parallax.
 * Camera position = 1.5x screen height, perspective strength 0.8.
 * Vanishing point at screen center shifted up by 10%.
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
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        alpha = 100
    }
    // Edge highlight: 1px white, alpha 0.6 — glass fracture reflection
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = android.graphics.Color.WHITE
        alpha = 153 // 0.6 * 255
    }

    init {
        // Camera distance = 1.5x screen height (in z-units; Android Camera uses ~72 dpi units)
        camera.setLocation(0f, 0f, -(screenHeightPx / density / 72f * 1.5f))

        val seedCount = targetShardCount
        val seedsX = FloatArray(seedCount)
        val seedsY = FloatArray(seedCount)
        for (i in 0 until seedCount) {
            seedsX[i] = Random.nextFloat() * screenWidthPx
            seedsY[i] = Random.nextFloat() * screenHeightPx
        }

        val screenCx = screenWidthPx / 2f
        val screenCy = screenHeightPx * 0.4f // vanishing point: center + 10% up
        val shardList = mutableListOf<GlassShard>()

        for (i in 0 until seedCount) {
            var polyX = floatArrayOf(0f, 0f, screenWidthPx, screenWidthPx, 0f)
            var polyY = floatArrayOf(0f, screenHeightPx, screenHeightPx, 0f, 0f)

            for (j in 0 until seedCount) {
                if (i == j) continue
                val ax = seedsX[i]; val ay = seedsY[i]
                val bx = seedsX[j]; val by = seedsY[j]
                val dx = bx - ax; val dy = by - ay
                val cx = (ax + bx) / 2f; val cy = (ay + by) / 2f
                val (newX, newY) = clipHalfPlane(polyX, polyY, dx, dy, cx, cy)
                if (newX.size < 3) break
                polyX = newX; polyY = newY
            }

            if (polyX.size < 6) continue

            // Shard center
            var sumX = 0f; var sumY = 0f
            val n = polyX.size
            for (k in 0 until n) { sumX += polyX[k]; sumY += polyY[k] }
            val scx = sumX / n; val scy = sumY / n

            // Outward direction from vanishing point
            var ox = scx - screenCx
            var oy = scy - screenCy
            val len = kotlin.math.sqrt(ox * ox + oy * oy)
            if (len > 0.001f) { ox /= len; oy /= len } else { ox = 0f; oy = 1f }

            // Assign depth layer by random: 30% foreground, 40% mid, 30% background
            val layerRoll = Random.nextFloat()
            val layer = when {
                layerRoll < 0.30f -> ShardLayer.FOREGROUND
                layerRoll < 0.70f -> ShardLayer.MID
                else -> ShardLayer.BACKGROUND
            }

            // Rotation from layer range
            val rotRange = layer.rotationRange
            val rotMag = rotRange.start + Random.nextFloat() * (rotRange.endInclusive - rotRange.start)
            val rotX = (Random.nextFloat() - 0.5f) * 2f * rotMag
            val rotY = (Random.nextFloat() - 0.5f) * 2f * rotMag

            // Z-depth: foreground = closest to viewer (smaller z), background = farther
            val zDepth = when (layer) {
                ShardLayer.FOREGROUND -> 0f
                ShardLayer.MID -> 1f
                ShardLayer.BACKGROUND -> 2f
            } + Random.nextFloat() * 0.3f

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
                    layer = layer,
                    rotateXDeg = rotX,
                    rotateYDeg = rotY,
                    zDepth = zDepth,
                )
            )
        }

        // Sort by z-depth: far (background) first, near (foreground) last
        shardList.sortByDescending { it.zDepth }
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
        contentBitmap: android.graphics.Bitmap?,
        crackProgress: Float,
        alpha: Float,
    ) {
        if (shatterProgress <= 0f && crackProgress <= 0f) return

        val easeOut = 1f - (1f - shatterProgress) * (1f - shatterProgress)
        val gravity = 800f * easeOut * easeOut

        for (shard in shards) {
            // Displacement based on layer
            val dispRange = shard.layer.displacementDp
            val dispDp = dispRange.start + Random.nextFloat() * (dispRange.endInclusive - dispRange.start)
            val displace = easeOut * dispDp
            val tx = shard.outwardX * displace
            val ty = shard.outwardY * displace + gravity

            camera.save()
            camera.rotateX(shard.rotateXDeg * easeOut)
            camera.rotateY(shard.rotateYDeg * easeOut)

            val cx = shard.centerX
            val cy = shard.centerY
            matrix.reset()
            camera.getMatrix(matrix)
            matrix.preTranslate(-cx, -cy)
            matrix.postTranslate(cx + tx, cy + ty)
            camera.restore()

            val path = Path()
            path.moveTo(shard.polygon[0], shard.polygon[1])
            for (k in 1 until shard.polygon.size / 2) {
                path.lineTo(shard.polygon[k * 2], shard.polygon[k * 2 + 1])
            }
            path.close()

            canvas.save()
            canvas.concat(matrix)

            if (contentBitmap != null) {
                shardPaint.alpha = (200 * alpha).toInt().coerceIn(0, 255)
                canvas.clipPath(path)
                canvas.drawBitmap(contentBitmap, 0f, 0f, shardPaint)
            } else {
                shardPaint.alpha = (120 * alpha).toInt().coerceIn(0, 255)
                shardPaint.color = android.graphics.Color.WHITE
                canvas.drawPath(path, shardPaint)
            }

            // Edge highlight: white fracture reflection
            edgePaint.alpha = (153 * alpha).toInt().coerceIn(0, 255)
            canvas.drawPath(path, edgePaint)

            canvas.restore()
        }
    }
}
