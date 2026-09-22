package com.mini.me_core.feature.agent.presentation.component.agentfirst

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskStep
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskStepStatus

/**
 * TaskCard 步骤列表（Stage 2 - S2-2）。
 *
 * 垂直列表展示任务的执行步骤，每个步骤一行：
 * 左侧状态图标 + 步骤标题 + 右侧状态文字 + 耗时。
 * Running 步骤高亮（淡蓝背景脉冲动画）。
 *
 * 顶部显示进度条：已完成步骤数 / 总步骤数。
 * 整个步骤列表可折叠（默认展开）。
 *
 * @param steps 步骤列表（从 TaskStepDeriver.derive 推导）
 * @param modifier 外部修饰符
 */
@Composable
internal fun TaskStepList(
    steps: List<TaskStep>,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }
    val completed = steps.count { it.status == TaskStepStatus.COMPLETED }
    val progress = if (steps.isNotEmpty()) completed.toFloat() / steps.size else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
    ) {
        // —— 头部：可折叠标题 + 进度条 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "执行步骤",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "$completed/${steps.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // 进度条
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(Radius.xs)),
            color = Brand.Blue,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(Spacing.xs))

        // —— 步骤列表（可折叠）——
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                steps.forEach { step ->
                    StepRow(step = step)
                }
            }
        }
    }
}

/**
 * 单个步骤行：状态图标 + 标题 + 状态文字 + 耗时。
 * Running 步骤有淡蓝背景脉冲。
 */
@Composable
private fun StepRow(step: TaskStep) {
    val (icon, iconColor, statusText) = when (step.status) {
        TaskStepStatus.PENDING -> Triple("▸", Color(0xFF9CA3AF), "等待")
        TaskStepStatus.RUNNING -> Triple("⟳", Brand.Blue, "运行中")
        TaskStepStatus.COMPLETED -> Triple("✓", Color(0xFF16A34A), "完成")
        TaskStepStatus.FAILED -> Triple("✗", Color(0xFFDC2626), "失败")
        TaskStepStatus.CANCELLED -> Triple("⊘", Color(0xFF9CA3AF), "取消")
    }

    // Running 步骤脉冲背景
    val isRunning = step.status == TaskStepStatus.RUNNING
    val bgModifier = if (isRunning) {
        val transition = rememberInfiniteTransition(label = "step-pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.18f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "step-pulse-alpha"
        )
        Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(Brand.Blue.copy(alpha = alpha))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标
        Text(
            text = icon,
            color = iconColor,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.size(width = 20.dp, height = 18.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        // 步骤标题
        Text(
            text = step.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 状态文字
        Text(
            text = statusText,
            color = iconColor,
            style = MaterialTheme.typography.labelSmall
        )
        // 耗时（非 0 时显示）
        if (step.durationMs > 0) {
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = formatDuration(step.durationMs),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/**
 * 格式化耗时显示。
 */
private fun formatDuration(ms: Long): String {
    if (ms < 1000) return "${ms}ms"
    val seconds = ms / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}m${remainingSeconds}s"
}
