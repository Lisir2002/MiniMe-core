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
 * 全屏背景径向渐变缓存持有者。
 *
 * 渐变中心/半径由画布尺寸决定、颜色由主题决定——splash 生命周期内两者恒定，
 * 实际仅构建一次。非 Compose 状态：draw 内惰性构建，零重组、零每帧分配。
 */
private class BackgroundGradientCache(
    private val primaryArgb: Int,
    private val bgArgb: Int,
) {
    private var cx = Float.NaN
    private var cy = Float.NaN
    private var radius = Float.NaN
    private var cached: Shader? = null

    fun obtain(cx: Float, cy: Float, radius: Float): Shader {
        val hit = cached
        val sameGeometry = hit != null && this.cx == cx && this.cy == cy && this.radius == radius
        if (sameGeometry) return hit
        return RadialGradient(
            cx, cy, radius,
            primaryArgb,
            bgArgb,
            Shader.TileMode.CLAMP
        ).also {
            cached = it
            this.cx = cx; this.cy = cy; this.radius = radius
        }
    }
}

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
            midColor,   // 0.10: mid halo 50%
            outerColor, // 0.35: outer glow 20%
            0x00000000  // 1.00: transparent edge
        ),
        floatArrayOf(0f, 0.1f, 0.35f, 1f),
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

    // 背景渐变按主题色缓存（尺寸在 draw 内惰然补齐），不再每帧新建。
    val bgGradientCache = remember(primaryArgb, bgArgb) { BackgroundGradientCache(primaryArgb, bgArgb) }

    // Pre-compute per-particle tinted colors whenever theme colors change.
    LaunchedEffect(primaryArgb, onBgArgb, particleSystem) {
        particleSystem.precomputeColors(primaryArgb, onBgArgb)
    }

    // 每帧绘制的 Paint 与 RectF 全部提到组合层复用，杜绝每帧分配。
    val bgPaint = remember { Paint() }
    val corePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val glowPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true } }
    val pulsePaint = remember { Paint() }
    val shimmerPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val frostPaint = remember { Paint() }
    val flashPaint = remember { Paint() }
    val dstRect = remember { RectF() }
    // 窗口特效用的预计算色值（白 × 固定 alpha），避免每帧 Color 对象分配。
    val shimmerArgb = remember { androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f).toArgb() }
    val whiteBase = remember { androidx.compose.ui.graphics.Color.White.toArgb() and 0x00FFFFFF }

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
            // 优化：中心光晕增强，HOLD时0.3
            val centerGlowAlpha = when (stage) {
                SplashStage.EXPLODE -> 0.08f + stageProgress * 0.1f
                SplashStage.CONVERGE -> 0.18f + stageProgress * 0.12f
                SplashStage.HOLD -> 0.3f
                SplashStage.SHATTER -> 0.15f * (1f - stageProgress)
                else -> 0.08f
            }
            // shader 缓存复用；浓度变化只调 paint.alpha（等效原端点 alpha 调制）。
            val gradientRadius = maxOf(w, h) * 0.9f
            bgPaint.shader = bgGradientCache.obtain(cx, cy, gradientRadius)
            bgPaint.alpha = (centerGlowAlpha * 255).toInt().coerceIn(0, 255)
            native.drawRect(0f, 0f, w, h, bgPaint)

            val mix = particleSystem.colorMix
            val count = particleSystem.count

            // 优化：HOLD 呼吸光晕的 sin 值与核心 alpha 只取决于 stage/mix/elapsedMs，
            // 与粒子索引无关——提到循环外算一次（原每粒子每帧重算）。
            val glowAlpha = when (stage) {
                SplashStage.HOLD -> (0.45f + 0.2f * kotlin.math.sin(elapsedMs * 0.005f)).coerceIn(0.4f, 0.65f)
                SplashStage.EXPLODE -> 0.6f
                SplashStage.CONVERGE -> 0.6f - 0.15f * mix
                else -> 0.45f
            }
            val coreAlpha = (255 * (0.8f + 0.2f * mix)).toInt().coerceIn(0, 255)
            // 优化：发光半径增强，HOLD时6.5倍，飞行时9倍
            val glowMul = if (stage == SplashStage.HOLD) 6.5f else 9f

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

                val glowRadius = sz * glowMul
                val glowBmp = if (usePrimaryGlow) primaryGlow else onBgGlow

                glowPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
                dstRect.set(px - glowRadius, py - glowRadius, px + glowRadius, py + glowRadius)
                native.drawBitmap(glowBmp, null, dstRect, glowPaint)

                // 核心圆改实心绘制（预着色数组取色）——
                // 原每粒子每帧新建 RadialGradient + intArrayOf + floatArrayOf
                // （1800 粒子 ≈ 每帧 5400 个临时对象），柔边由外层 glow sprite 提供。
                corePaint.color = if (usePrimaryGlow) {
                    particleSystem.primaryTinted[i]
                } else {
                    particleSystem.onBgTinted[i]
                }
                corePaint.alpha = coreAlpha
                native.drawCircle(px, py, sz * 1.2f, corePaint)
            }

            // ── Aggregation flash pulse at text formation (start of HOLD, 300ms) ──
            // Strong radial pulse from text center, expanding outward then decaying.
            if (stage == SplashStage.HOLD && stageProgress < 0.6f) {
                val pulseT = stageProgress / 0.6f  // 0→1 over 300ms of 500ms hold
                val pulseAlpha = (1f - pulseT) * 0.35f
                val pulseRadius = maxOf(w, h) * (0.15f + 0.45f * pulseT)
                val pulseGlow = RadialGradient(
                    cx, cy, pulseRadius,
                    primaryColor.copy(alpha = pulseAlpha).toArgb(),
                    0x00000000,
                    Shader.TileMode.CLAMP
                )
                pulsePaint.shader = pulseGlow
                native.drawRect(0f, 0f, w, h, pulsePaint)
                pulsePaint.shader = null
            }

            // Shimmer sweep during HOLD (subtle light sweep across settled text)
            if (stage == SplashStage.HOLD && quality.enableCracks) {
                val sweepX = cx - w * 0.5f + (w * 1.5f) * stageProgress
                shimmerPaint.color = shimmerArgb
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
                val frostAlpha = ((1f - frostT) * 0.18f * 255).toInt().coerceIn(0, 255)
                frostPaint.color = whiteBase or (frostAlpha shl 24)
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
                val flashAlpha = ((1f - flashT) * 0.9f * 255).toInt().coerceIn(0, 255)
                flashPaint.color = whiteBase or (flashAlpha shl 24)
                native.drawRect(0f, 0f, w, h, flashPaint)
            }
        }
    }
}
