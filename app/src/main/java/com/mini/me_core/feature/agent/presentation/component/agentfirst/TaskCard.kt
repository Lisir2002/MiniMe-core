package com.mini.me_core.feature.agent.presentation.component.agentfirst

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.domain.model.CodeChange
import com.mini.me_core.feature.agent.domain.tool.PendingToolPermission
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.EnvironmentSnapshot
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.component.EditDiff
import com.mini.me_core.feature.agent.presentation.component.parseEditDiff
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskState
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskStateDeriver
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskStep
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.TaskStepDeriver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Agent-First TaskCard 主组件（Stage 2 - S2-1）。
 *
 * 替代旧的 `TaskAccordion`，以「折叠态一行摘要 + 展开态完整时间线」的形态呈现一个任务：
 * - 折叠态：左侧状态图标 + 中间任务标题与统计 + 右侧状态文字与时间 + 展开箭头；
 * - 展开态：头部（状态标签 + 进度）+ 步骤列表 + 子分组渲染（用户/思考/回复/工具）+ 产物区域 + 底部操作栏。
 *
 * 所有展示状态均由现有数据推导（不引入新的持久化状态）：
 * - 任务状态来自 [TaskStateDeriver]（7 状态：Idle/Planning/Running/WaitingApproval/Completed/Failed/Cancelled）；
 * - 步骤列表来自 [TaskStepDeriver]（从 TOOL 子分组推导）；
 * - 文件产物从 TOOL 消息的 editFile/writeFile 结果中解析。
 *
 * 由 [AgentFirstFeatureFlags.isTaskCardEnabled] 控制是否启用；默认 false，回退旧实现。
 *
 * @param group 任务分组（含标题、时间、子分组、展开状态）
 * @param agentState 全局 Agent 状态
 * @param runningTools 当前会话所有运行中工具的实时输出
 * @param environmentSnapshots 旁路环境探测快照（key = tool messageId）
 * @param changes 全局文件变更列表（预留，当前从 TOOL 消息解析产物）
 * @param pendingPermission 待审批的工具权限（null 表示无待审批）
 * @param onToggleTask 点击任务头部切换展开/折叠回调
 * @param onToggleSubGroup 点击子分组头部切换展开/折叠回调 (taskId, subGroupId)
 * @param onStop 点击「停止执行」回调
 * @param onViewChanges 点击「查看日志/产物」回调
 * @param modifier 外部修饰符
 */
@Composable
internal fun TaskCard(
    group: TaskGroup,
    agentState: AgentUIState,
    runningTools: List<RunningToolOutput>,
    environmentSnapshots: Map<String, EnvironmentSnapshot>,
    changes: List<CodeChange> = emptyList(),
    pendingPermission: PendingToolPermission? = null,
    onToggleTask: (String) -> Unit,
    onToggleSubGroup: (String, String) -> Unit,
    onStop: () -> Unit = {},
    onViewChanges: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. 状态推导
    val state = remember(group, agentState, runningTools, pendingPermission) {
        TaskStateDeriver.derive(group, agentState, runningTools, pendingPermission)
    }

    // 2. 步骤列表推导
    val steps = remember(group, runningTools) {
        TaskStepDeriver.derive(group, runningTools)
    }

    // 3. 文件产物解析（从 TOOL 消息的 editFile/writeFile 结果中提取）
    val fileDiffs = remember(group) {
        parseFileDiffsFromGroup(group)
    }

    val stateVisual = rememberTaskStateVisual(state)
    val expanded = group.isExpanded
    val toolCount = remember(group) { TaskStateDeriver.collectToolMessages(group).size }

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
        ) {
            // —— 头部（折叠/展开共用，点击切换）——
            TaskCardHeader(
                group = group,
                stateVisual = stateVisual,
                stepCount = steps.size,
                toolCount = toolCount,
                expanded = expanded,
                onClick = { onToggleTask(group.taskId) }
            )

            // —— 展开态内容 ——
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                ) {
                    // 步骤列表
                    TaskStepList(steps = steps)

                    // 子分组渲染
                    group.subGroups.forEach { subGroup ->
                        Spacer(Modifier.height(Spacing.xs))
                        TaskSubGroupRenderer(
                            taskId = group.taskId,
                            subGroup = subGroup,
                            liveOutputs = runningTools,
                            environmentSnapshots = environmentSnapshots,
                            agentState = agentState,
                            onToggleSubGroup = onToggleSubGroup
                        )
                    }

                    // 产物区域
                    TaskArtifactSection(
                        fileDiffs = fileDiffs,
                        onViewDiff = { onViewChanges() }
                    )

                    // 底部操作栏
                    TaskBottomBar(
                        state = state,
                        onStop = onStop,
                        onViewChanges = onViewChanges
                    )
                }
            }
        }
    }
}

/**
 * TaskCard 头部：状态图标 + 标题/统计 + 状态文字/时间 + 展开箭头。
 */
@Composable
private fun TaskCardHeader(
    group: TaskGroup,
    stateVisual: TaskStateVisual,
    stepCount: Int,
    toolCount: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧状态图标
        TaskStateIcon(stateVisual = stateVisual)

        Spacer(Modifier.width(Spacing.sm))

        // 中间：标题 + 统计
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val stats = buildList {
                if (stepCount > 0) add("$stepCount 步骤")
                if (toolCount > 0) add("$toolCount 工具")
                if (group.timestamp > 0) add(formatTaskTime(group.timestamp))
            }.joinToString(" · ")
            if (stats.isNotBlank()) {
                Text(
                    text = stats,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        // 右侧：状态文字 + 展开箭头
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stateVisual.label,
                color = stateVisual.color,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }

        Spacer(Modifier.width(Spacing.xs))

        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "折叠" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(Radius.sm))
        )
    }
}

/**
 * 任务状态视觉定义：图标 + 颜色 + 标签。
 */
internal data class TaskStateVisual(
    val icon: ImageVector,
    val color: Color,
    val label: String,
    val pulsing: Boolean = false
)

/**
 * 根据 TaskState 返回对应的视觉定义（图标 + 颜色 + 标签 + 是否脉冲）。
 */
@Composable
private fun rememberTaskStateVisual(state: TaskState): TaskStateVisual {
    val isDark = LocalAppDarkMode.current
    return when (state) {
        TaskState.RUNNING -> TaskStateVisual(
            icon = Icons.Rounded.Timelapse,
            color = Brand.Blue,
            label = "运行中",
            pulsing = true
        )
        TaskState.COMPLETED -> TaskStateVisual(
            icon = Icons.Rounded.CheckCircle,
            color = if (isDark) Brand.StatusGreen.Dark else Brand.StatusGreen.Light,
            label = "已完成"
        )
        TaskState.FAILED -> TaskStateVisual(
            icon = Icons.Rounded.ErrorOutline,
            color = Color(0xFFDC2626),
            label = "失败"
        )
        TaskState.WAITING_APPROVAL -> TaskStateVisual(
            icon = Icons.Rounded.Pending,
            color = Color(0xFFF59E0B),
            label = "待审批",
            pulsing = true
        )
        TaskState.CANCELLED -> TaskStateVisual(
            icon = Icons.Rounded.StopCircle,
            color = Color(0xFF9CA3AF),
            label = "已取消"
        )
        TaskState.PLANNING -> TaskStateVisual(
            icon = Icons.Rounded.Timer,
            color = Color(0xFF7C3AED),
            label = "规划中",
            pulsing = true
        )
        TaskState.IDLE -> TaskStateVisual(
            icon = Icons.Rounded.Pending,
            color = Color(0xFF9CA3AF),
            label = "空闲"
        )
    }
}

/**
 * 左侧状态图标：圆形淡色背景 + 状态图标（Running/WaitingApproval/Planning 脉冲）。
 */
@Composable
private fun TaskStateIcon(stateVisual: TaskStateVisual) {
    val iconModifier = if (stateVisual.pulsing) {
        val transition = rememberInfiniteTransition(label = "task-state")
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "task-state-alpha"
        )
        Modifier.graphicsLayer { this.alpha = alpha }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(stateVisual.color.copy(alpha = 0.12f))
            .then(iconModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = stateVisual.icon,
            contentDescription = null,
            tint = stateVisual.color,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 从 TaskGroup 的 TOOL 消息中解析文件变更（editFile/writeFile）。
 *
 * 简化版 collectBatchFileDiffs：只解析 editFile/writeFile 的结构化 diff，
 * 不处理 Bash rm 删除通配符（第一版足够覆盖主要产物场景）。
 */
private fun parseFileDiffsFromGroup(group: TaskGroup): List<EditDiff> {
    val toolMessages = TaskStateDeriver.collectToolMessages(group)
    if (toolMessages.isEmpty()) return emptyList()

    val byPath = LinkedHashMap<String, EditDiff>()
    toolMessages.forEach { msg ->
        if (msg.role != MessageRole.TOOL || msg.isError) return@forEach
        when (msg.toolName) {
            "editFile", "writeFile" -> {
                val diff = parseEditDiff(msg.content) ?: return@forEach
                val existing = byPath[diff.path]
                byPath[diff.path] = if (existing != null) {
                    existing.copy(
                        added = existing.added + diff.added,
                        removed = existing.removed + diff.removed,
                        hunks = existing.hunks + diff.hunks
                    )
                } else {
                    diff
                }
            }
        }
    }
    return byPath.values.toList()
}

/**
 * 格式化任务时间戳为 HH:mm。
 */
private fun formatTaskTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}
