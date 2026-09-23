package com.mini.me_core.feature.agent.presentation.component.agentfirst
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskState

/**
 * TaskCard 底部操作栏（Stage 2 - S2-5）。
 *
 * 根据任务状态展示对应的操作按钮：
 * - [TaskState.RUNNING]：显示「停止执行」按钮（红色背景，白色文字）；
 * - [TaskState.COMPLETED] / [TaskState.FAILED]：显示「查看日志」按钮（次要样式）；
 * - [TaskState.FAILED] 且 [onRetry] 非空时：额外显示「重试任务」按钮；
 * - 其他状态（WaitingApproval / Cancelled / Planning / Idle）：不渲染底部操作栏。
 *
 * @param state 任务推导状态
 * @param onStop 点击「停止执行」回调
 * @param onViewChanges 点击「查看日志」回调
 * @param onRetry 点击「重试任务」回调；null 时不显示重试按钮
 * @param modifier 外部修饰符
 */
@Composable
fun TaskBottomBar(
    state: TaskState,
    onStop: () -> Unit,
    onViewChanges: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    when (state) {
        TaskState.RUNNING -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LocalCornerRadius.current.pill))
                        .background(Color(0xFFDC2626))
                        .clickable { onStop() }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "停止执行",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        }

        TaskState.COMPLETED, TaskState.FAILED -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(LocalCornerRadius.current.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onViewChanges() }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "查看日志",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
                // Failed 状态且 onRetry 非空时显示「重试任务」按钮
                if (state == TaskState.FAILED && onRetry != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(LocalCornerRadius.current.pill))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { onRetry() }
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "重试任务",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        }

        else -> {
            // WaitingApproval / Cancelled / Planning / Idle：不显示底部操作栏
        }
    }
}
