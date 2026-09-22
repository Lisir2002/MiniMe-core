package com.mini.me_core.core.theme.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.tokens.PrimitiveRadius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/**
 * 统一徽章组件。
 *
 * 基于现有 MessageCountBadge（TaskAccordion）、StreamingBadge、TypeBadge、
 * LatencyBadge、PermissionBadge、RiskBadge 等归纳。
 *
 * 用于在图标或内容旁显示数字计数、状态标签等。
 *
 * @param text 徽章文字内容（如 "3"、"NEW"、"128"）
 * @param modifier 修饰符
 * @param color 徽章颜色（默认 Error，红色背景白字）
 * @param textColor 文字颜色（默认 onError）
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalAppTheme.current.colors.error,
    textColor: Color = LocalAppTheme.current.colors.onError,
) {
    Surface(
        shape = RoundedCornerShape(PrimitiveRadius.Pill),
        color = color,
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = PrimitiveSpacing.MdPlus,
                vertical = PrimitiveSpacing.Xxs,
            ),
        )
    }
}

/**
 * 状态点枚举。
 */
enum class AppStatusDotColor {
    Success, Warning, Error, Info, Neutral
}

/**
 * 统一状态点组件。
 *
 * 基于现有 ToolStatusDot（ToolMessageComponents）、ConnectionIndicator、
 * StreamingBadge 中的脉冲点等归纳。
 *
 * 用于表示在线/离线/运行中/错误等状态。
 *
 * @param color 状态颜色
 * @param modifier 修饰符
 * @param size 圆点直径（默认 8dp，与现有 ToolStatusDot 一致）
 * @param pulse 是否启用脉冲动画（用于"运行中"等进行态）
 */
@Composable
fun AppStatusDot(
    color: AppStatusDotColor,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 8.dp,
    pulse: Boolean = false,
) {
    val colors = LocalAppTheme.current.colors
    val dotColor = when (color) {
        AppStatusDotColor.Success -> colors.success
        AppStatusDotColor.Warning -> colors.warning
        AppStatusDotColor.Error -> colors.error
        AppStatusDotColor.Info -> colors.info
        AppStatusDotColor.Neutral -> colors.textTertiary
    }

    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "status-dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-dot-alpha",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(dotColor),
    )
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

/** Preview：展示 AppBadge 各种颜色。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 300, heightDp = 200)
@Composable
private fun AppBadgePreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        val colors = LocalAppTheme.current.colors
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBadge(text = "3", color = colors.error, textColor = colors.onError)
            Spacer(Modifier.width(Spacing.sm))
            AppBadge(text = "NEW", color = colors.success, textColor = colors.onSuccess)
            Spacer(Modifier.width(Spacing.sm))
            AppBadge(text = "128", color = colors.warning, textColor = colors.onWarning)
            Spacer(Modifier.width(Spacing.sm))
            AppBadge(text = "INFO", color = colors.info, textColor = colors.onInfo)
        }
    }
}

/** Preview：展示 AppStatusDot 各种颜色和脉冲态。 */
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 300, heightDp = 200)
@Composable
private fun AppStatusDotPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppStatusDot(color = AppStatusDotColor.Success)
            Spacer(Modifier.width(Spacing.md))
            AppStatusDot(color = AppStatusDotColor.Warning)
            Spacer(Modifier.width(Spacing.md))
            AppStatusDot(color = AppStatusDotColor.Error)
            Spacer(Modifier.width(Spacing.md))
            AppStatusDot(color = AppStatusDotColor.Info)
            Spacer(Modifier.width(Spacing.md))
            AppStatusDot(color = AppStatusDotColor.Neutral)
            Spacer(Modifier.width(Spacing.md))
            AppStatusDot(color = AppStatusDotColor.Info, pulse = true)
        }
    }
}
