package com.mini.me_core.core.theme.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 统一状态指示器圆点。
 *
 * @param status 状态：running / stopped / error / warning
 * @param size 圆点直径（8/10/12dp）
 * @param animate 是否启用脉冲动画（默认 false）
 */
@Composable
fun AppStatusDot(
    status: AppStatusType,
    size: Dp = 8.dp,
    animate: Boolean = false,
) {
    val colors = LocalAppTheme.current.colors

    val dotColor = when (status) {
        AppStatusType.Running -> colors.success
        AppStatusType.Stopped -> colors.textTertiary
        AppStatusType.Error -> colors.error
        AppStatusType.Warning -> colors.warning
    }

    val scale by if (animate) {
        val transition = rememberInfiniteTransition(label = "status_dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_scale",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(dotColor, CircleShape),
    )
}

/**
 * 状态类型枚举。
 */
enum class AppStatusType {
    /** 运行中（绿色） */
    Running,
    /** 已停止（灰色） */
    Stopped,
    /** 错误（红色） */
    Error,
    /** 警告（橙色） */
    Warning,
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：各种状态圆点。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 400, heightDp = 200)
@Composable
private fun AppStatusDotPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            AppStatusDot(status = AppStatusType.Running, size = 10.dp, animate = true)
            AppStatusDot(status = AppStatusType.Stopped, size = 10.dp)
            AppStatusDot(status = AppStatusType.Error, size = 10.dp)
            AppStatusDot(status = AppStatusType.Warning, size = 10.dp)
        }
    }
}
