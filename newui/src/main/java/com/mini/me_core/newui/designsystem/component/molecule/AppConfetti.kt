package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val startX: Float,
    val drift: Float,
    val sizeFactor: Float,
    val fall: Float,
    val phaseY: Float,
    val phase: Float,
    val freq: Float,
    val swing: Float,
    val rotationSpeed: Float,
    val color: Color,
)

/**
 * 庆祝彩带（分子组 · AppConfetti）：一簇彩色纸屑在卡片范围内飘落 + 往复摆动，循环播放下落，
 * 用于发版成功 / 任务完成 / 达成里程碑的庆祝反馈。
 */
@Composable
fun AppConfetti(
    modifier: Modifier = Modifier,
    count: Int = 26,
    durationMillis: Int = 3800,
    colors: List<Color> = listOf(
        AppColor.BrandPrimary,
        AppColor.BrandAccent,
        AppColor.StatusDanger,
        AppColor.StatusWarning,
        AppColor.StatusInfo,
    ),
) {
    if (count <= 0) return
    val particles = remember(count, colors) {
        val rnd = Random(7)
        List(count) {
            ConfettiParticle(
                startX = rnd.nextFloat(),
                drift = (rnd.nextFloat() - 0.5f) * 0.14f,
                sizeFactor = 0.035f + rnd.nextFloat() * 0.04f,
                // 每粒子在一个周期内完整飘落（0.85~1.35 倍周期），
                // 配 phaseY 垂直错峰：任意时刻粒子在全高度均匀分布，不再同帧从顶部挤成一簇。
                fall = 0.85f + rnd.nextFloat() * 0.5f,
                phaseY = rnd.nextFloat(),
                phase = rnd.nextFloat() * 2f * PI.toFloat(),
                freq = 1f + rnd.nextFloat() * 3f,
                swing = 0.03f + rnd.nextFloat() * 0.04f,
                rotationSpeed = (rnd.nextFloat() - 0.5f) * 3f,
                color = colors[rnd.nextInt(colors.size)],
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiProgress",
    )

    Canvas(modifier) {
        particles.forEach { p ->
            // 独立相位取模：粒子从顶部外 (-0.15) 飘到底部外 (1.20)，循环无缝；
            // phaseY 错峰让首帧起粒子就铺满全高度，而不是整簇同帧回顶。
            val loop = (t * p.fall + p.phaseY) % 1f
            val py = (-0.15f + loop * 1.35f) * size.height
            val x = p.startX + sin(t * 2f * PI.toFloat() * p.freq + p.phase) * p.swing + t * p.drift
            val px = x * size.width
            val unit = p.sizeFactor * size.minDimension.coerceAtLeast(32f)
            withTransform({
                rotate(t * 360f * p.rotationSpeed, pivot = Offset(px, py))
            }) {
                drawRect(p.color, topLeft = Offset(px, py), size = Size(unit, unit * 1.6f))
            }
        }
    }
}