package com.mini.logs.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * Shimmer 骨架屏。
 *
 * 用一个从左到右无限流动的浅色渐变模拟"正在加载"的高级感，
 * 比传统的 CircularProgressIndicator 更有设计感。
 */
@Composable
fun ShimmerBrush(): Brush {
    val colors = LocalAppTheme.current.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-translate",
    )
    return Brush.linearGradient(
        colors = listOf(
            colors.surfaceSunken,
            colors.surfacePressed,
            colors.surfaceSunken,
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim),
    )
}

/** 日志列表骨架屏：模拟多行日志条目。 */
@Composable
fun LogListSkeleton(modifier: Modifier = Modifier) {
    val brush = ShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(12) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (i % 3 == 0) 48.dp else 24.dp, 16.dp)
                        .background(brush, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(brush, RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

/** 统计页骨架屏：模拟概览卡片 + 图表占位。 */
@Composable
fun StatsSkeleton(modifier: Modifier = Modifier) {
    val brush = ShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 概览卡片行
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .background(brush, RoundedCornerShape(12.dp)),
                )
            }
        }
        // 趋势图占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(brush, RoundedCornerShape(12.dp)),
        )
        // Tag 分布占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(brush, RoundedCornerShape(12.dp)),
        )
        // 热力图占位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(brush, RoundedCornerShape(12.dp)),
        )
    }
}

/** 崩溃页骨架屏：模拟崩溃卡片列表。 */
@Composable
fun CrashSkeleton(modifier: Modifier = Modifier) {
    val brush = ShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(brush, RoundedCornerShape(12.dp)),
            )
        }
    }
}
