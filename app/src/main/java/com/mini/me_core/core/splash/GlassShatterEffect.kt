package com.mini.me_core.core.splash

import android.graphics.Camera
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A single glass shard from the Voronoi tessellation.
 *
 * Each shard is a polygon that starts as part of the screen, then during shatter:
 * 1. Translates outward along its normal direction
 * 2. Rotates in 3D around its own center (X/Y axis)
 * 3. Falls under gravity
 */
class GlassShard(
    val polygon: FloatArray, // flat [x0,y0, x1,y1, ...]
    val centerX: Float,
    val centerY: Float,
    val outwardX: Float, // normalized direction from screen center
    val outwardY: Float,
    val distanceFromCenter: Float, // for scaling motion magnitude
    val rotateXDeg: Float,
    val rotateYDeg: Float,
    val zDepth: Float, // for Z-sorting (far = larger)
)

/**
 * Glass shatter effect using Voronoi tessellation + Camera 3D transforms.
 *
 * Voronoi cells are precomputed once at initialization (not per frame).
 * During animation, each shard gets a 3D rotation matrix via android.graphics.Camera.
 */
class GlassShatterEffect(
    private val screenWidthPx: Float,
    private val screenHeightPx: Float,
    density: Float,
) {
    val shardCount: Int
    val shards: List<GlassShard>
    val crackLines: List<Pair<FloatArray, FloatArray>> // pairs of (start, end) points

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
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        alpha = 60
    }

    init {
        camera.setLocation(0f, 0f, -8f)

        // Generate random seed points
        val seedCount = 30 + Random.nextInt(11) // 30-40
        val seedsX = FloatArray(seedCount)
        val seedsY = FloatArray(seedCount)
        for (i in 0 until seedCount) {
            seedsX[i] = Random.nextFloat() * screenWidthPx
            seedsY[i] = Random.nextFloat() * screenHeightPx
        }

        // Compute Voronoi cells via Sutherland-Hodgman clipping
        val shardList = mutableListOf<GlassShard>()
        val screenCx = screenWidthPx / 2f
        val screenCy = screenHeightPx / 2f

        for (i in 0 until seedCount) {
            var polyX = floatArrayOf(0f, 0f, screenWidthPx, screenWidthPx, 0f)
            var polyY = floatArrayOf(0f, screenHeightPx, screenHeightPx, 0f, 0f)

            for (j in 0 until seedCount) {
                if (i == j) continue
                // Clip to half-plane: points closer to seed i than seed j
                val ax = seedsX[i]
                val ay = seedsY[i]
                val bx = seedsX[j]
                val by = seedsY[j]
                // Half-plane: (x - ax)*(bx-ax) + (y - ay)*(by-ay) <= (bx-ax)^2/2 + (by-ay)^2/2
                // Simplified: keep points on the i side of the perpendicular bisector
                val dx = bx - ax
                val dy = by - ay
                val cx = (ax + bx) / 2f
                val cy = (ay + by) / 2f
                // Clip: dot((p - c), d) <= 0 (i side)
                val (newX, newY) = clipHalfPlane(polyX, polyY, dx, dy, cx, cy)
                if (newX.size < 3) break
                polyX = newX
                polyY = newY
            }

            if (polyX.size < 6) continue // need at least triangle

            // Compute shard center
            var sumX = 0f
            var sumY = 0f
            val n = polyX.size
            for (k in 0 until n) {
                sumX += polyX[k]
                sumY += polyY[k]
            }
            val scx = sumX / n
            val scy = sumY / n

            // Outward direction from screen center
            var ox = scx - screenCx
            var oy = scy - screenCy
            val len = kotlin.math.sqrt(ox * ox + oy * oy)
            if (len > 0.001f) {
                ox /= len
                oy /= len
            } else {
                ox = 0f
                oy = 1f
            }

            val dist = len / kotlin.math.sqrt(screenWidthPx * screenWidthPx + screenHeightPx * screenHeightPx)

            // Random 3D rotation, scaled by distance
            val baseRot = 20f + dist * 60f
            val rotX = (Random.nextFloat() - 0.5f) * 2f * baseRot
            val rotY = (Random.nextFloat() - 0.5f) * 2f * baseRot

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
                    distanceFromCenter = dist,
                    rotateXDeg = rotX,
                    rotateYDeg = rotY,
                    zDepth = dist, // farther from center = "closer" to viewer for drama
                )
            )
        }

        // Sort by z-depth (draw far first, near last)
        shardList.sortByDescending { it.zDepth }
        shards = shardList
        shardCount = shards.size

        // Crack lines: from center to each seed (subset for performance)
        crackLines = mutableListOf()
        val crackSeedCount = (seedCount / 2).coerceAtLeast(8)
        for (i in 0 until crackSeedCount) {
            val idx = (i * seedCount / crackSeedCount).coerceAtMost(seedCount - 1)
            crackLines.add(
                Pair(
                    floatArrayOf(screenCx, screenCy),
                    floatArrayOf(seedsX[idx], seedsY[idx])
                )
            )
        }
    }

    /**
     * Sutherland-Hodgman half-plane clipping.
     * Keeps points where (p - c) dot d <= 0.
     */
    private fun clipHalfPlane(
        polyX: FloatArray, polyY: FloatArray,
        dx: Float, dy: Float,
        cx: Float, cy: Float
    ): Pair<FloatArray, FloatArray> {
        val outX = mutableListOf<Float>()
        val outY = mutableListOf<Float>()
        val n = polyX.size

        fun inside(x: Float, y: Float): Boolean {
            return (x - cx) * dx + (y - cy) * dy <= 0
        }

        for (i in 0 until n) {
            val j = (i + 1) % n
            val xi = polyX[i]; val yi = polyY[i]
            val xj = polyX[j]; val yj = polyY[j]
            val iInside = inside(xi, yi)
            val jInside = inside(xj, yj)

            if (iInside) {
                outX.add(xi); outY.add(yi)
            }
            if (iInside != jInside) {
                // Compute intersection
                val t = ((cx - xi) * dx + (cy - yi) * dy) / ((xj - xi) * dx + (yj - yi) * dy)
                outX.add(xi + t * (xj - xi))
                outY.add(yi + t * (yj - yi))
            }
        }
        return Pair(outX.toFloatArray(), outY.toFloatArray())
    }

    /**
     * Draw the shattered shards.
     *
     * @param canvas Native Android canvas
     * @param shatterProgress 0..1 progress of the shatter animation
     * @param contentBitmap The screen content bitmap (drawn into shards)
     * @param crackProgress 0..1 how far crack lines have spread
     * @param alpha overall alpha for fade out
     */
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

        // Draw crack lines first (under shards)
        if (crackProgress > 0f) {
            crackPaint.alpha = (100 * crackProgress * alpha).toInt().coerceIn(0, 255)
            for ((start, end) in crackLines) {
                // Crack grows from center outward
                val ex = start[0] + (end[0] - start[0]) * crackProgress
                val ey = start[1] + (end[1] - start[1]) * crackProgress
                canvas.drawLine(start[0], start[1], ex, ey, crackPaint)
            }
        }

        // Draw shards (sorted far to near)
        for (shard in shards) {
            // Outward displacement
            val displace = easeOut * (200f + shard.distanceFromCenter * 400f)
            val tx = shard.outwardX * displace
            val ty = shard.outwardY * displace + gravity

            // 3D rotation
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

            // Build path from polygon
            val path = Path()
            path.moveTo(shard.polygon[0], shard.polygon[1])
            for (k in 1 until shard.polygon.size / 2) {
                path.lineTo(shard.polygon[k * 2], shard.polygon[k * 2 + 1])
            }
            path.close()

            canvas.save()
            canvas.concat(matrix)

            // Draw shard content (screenshot of underlying screen)
            if (contentBitmap != null) {
                shardPaint.alpha = (200 * alpha).toInt().coerceIn(0, 255)
                // Clip to shard path and draw the bitmap
                canvas.clipPath(path)
                canvas.drawBitmap(contentBitmap, 0f, 0f, shardPaint)
            } else {
                shardPaint.alpha = (120 * alpha).toInt().coerceIn(0, 255)
                shardPaint.color = android.graphics.Color.WHITE
                canvas.drawPath(path, shardPaint)
            }

            // Edge highlight
            edgePaint.alpha = (80 * alpha).toInt().coerceIn(0, 255)
            canvas.drawPath(path, edgePaint)

            canvas.restore()
        }
    }
}
