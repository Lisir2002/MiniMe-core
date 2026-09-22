package com.mini.me_core.feature.agent.presentation.component.agentfirst

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.EnvironmentSnapshot
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.component.DiffView
import com.mini.me_core.feature.agent.presentation.component.EnvironmentStatusStrip
import com.mini.me_core.feature.agent.presentation.component.formatToolResult
import com.mini.me_core.feature.agent.presentation.component.parseEditDiff
import com.mini.me_core.feature.agent.presentation.component.toolArgHint
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ErrorDiagnoser
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ToolCallState
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ToolStateDeriver
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ToolType
import com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers.ToolTypeDeriver

/**
 * Agent-First 工具调用卡片（Stage 1）。
 *
 * 替代旧的 `ToolMessageBody`，以「折叠态一行摘要 + 展开态详情」的形态呈现一次工具调用：
 * - 折叠态：左侧 24dp 类型图标（按工具类型着色）+ 中间工具名与命令预览 + 右侧状态标记 + 展开箭头；
 * - 展开态：完整命令区、输出区域（流式自动贴底 / 落库可折叠）、复制命令/输出按钮、错误诊断提示条、
 *   环境状态条（SHELL 且有旁路快照时）。
 *
 * 所有展示状态均由现有数据推导（不引入新的持久化状态）：
 * - 状态来自 [ToolStateDeriver]（Running/Success/Error/TimedOut/Cancelled）；
 * - 类型来自 [ToolTypeDeriver]（决定图标与着色）；
 * - 错误诊断来自 [ErrorDiagnoser]（仅 Error/TimedOut 状态）。
 *
 * 由 [AgentFirstFeatureFlags.isToolCallCardEnabled] 控制是否启用；默认 false，回退旧实现。
 *
 * @param message TOOL 类型的消息
 * @param liveOutput 匹配该消息的实时输出；null 表示非运行中
 * @param environmentSnapshot 旁路环境探测快照（构建/环境变更命令后自动探测）
 * @param agentState 全局 Agent 状态（用于冷启动取消判定）
 * @param initiallyExpanded 预览/测试用：强制初始展开态；null 时按状态自动决定
 * @param onRetry 点击「重试」回调（messageId）；null 时不显示重试按钮
 * @param modifier 外部修饰符
 */
@Composable
fun ToolCallCard(
    message: AgentUIMessage,
    liveOutput: RunningToolOutput?,
    environmentSnapshot: EnvironmentSnapshot?,
    agentState: AgentUIState,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean? = null,
    onRetry: ((messageId: String) -> Unit)? = null
) {
    // 1. 状态推导（优先级 Running > Cancelled > TimedOut > Error > Success）
    val state = remember(message.id, liveOutput, agentState) {
        ToolStateDeriver.derive(message, liveOutput, agentState)
    }
    // 2. 类型推导（决定图标与着色）
    val toolType = remember(message.toolName, message.toolArgs) {
        ToolTypeDeriver.derive(message.toolName, message.toolArgs)
    }

    // check_environment 走现有紧凑环境状态条分支（与旧实现一致），不走通用卡片
    if (toolType == ToolType.ENV) {
        EnvironmentStatusStrip(message = message)
        return
    }

    val running = state == ToolCallState.RUNNING

    // 3. 错误诊断（仅 Error / TimedOut）
    val diagnosis = remember(message.id, message.content, state) {
        if (state == ToolCallState.ERROR || state == ToolCallState.TIMED_OUT) {
            ErrorDiagnoser.diagnose(message.content, message.toolName)
        } else null
    }

    // 命令预览：优先 toolArgs.command，回退 toolArgHint
    val command = remember(message.toolArgs) {
        ToolTypeDeriver.extractCommand(message.toolArgs ?: "")
            .ifBlank { toolArgHint(message.toolArgs).orEmpty() }
    }
    val toolLabel = message.toolName ?: "工具"

    // 输出内容：流式用实时累积文本，落库用清洗后的结果
    val outputContent = if (running && liveOutput != null) {
        liveOutput.text
    } else {
        message.content
    }
    val outputForCopy = if (running) {
        liveOutput?.text.orEmpty()
    } else {
        formatToolResult(message.content)
    }

    // 展开状态：Running/Error/TimedOut 自动展开；其余默认折叠
    val autoExpand = state == ToolCallState.RUNNING ||
        state == ToolCallState.ERROR || state == ToolCallState.TIMED_OUT
    var expanded by remember(message.id) { mutableStateOf(initiallyExpanded ?: autoExpand) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        // —— 折叠态头部 ——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolTypeIcon(toolType)
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toolLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (command.isNotBlank()) {
                    Text(
                        text = command,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            StatusMarker(state)
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Brand.IconGray,
                modifier = Modifier.size(18.dp)
            )
        }

        // —— 展开态详情 ——
        if (expanded) {
            Spacer(Modifier.height(Spacing.sm))
            // 完整命令区
            if (command.isNotBlank()) {
                CommandSection(command = command)
                Spacer(Modifier.height(Spacing.sm))
            }
            // editFile/writeFile：复用现有 DiffView 渲染结构化差异；其余走输出区域
            val edit = if (!running && !message.isError && toolType == ToolType.FILE_WRITE) {
                remember(message.id, message.content) { parseEditDiff(message.content) }
            } else null
            if (edit != null) {
                edit.hunks.forEach { h ->
                    DiffView(diff = h.diff, startLine = h.startLine)
                    Spacer(Modifier.height(Spacing.xs))
                }
            } else {
                ToolCallCardOutput(content = outputContent, isStreaming = running)
            }
            Spacer(Modifier.height(Spacing.xs))
            // 操作按钮：复制命令 / 复制输出 / 重试（仅 Error/TimedOut 且 onRetry 非空）
            val showRetry = onRetry != null &&
                (state == ToolCallState.ERROR || state == ToolCallState.TIMED_OUT)
            ToolCallCardActionButtons(
                command = command,
                output = outputForCopy,
                onCopyCommand = {},
                onCopyOutput = {},
                onRetry = if (showRetry) { { onRetry.invoke(message.id) } } else null
            )
            // 错误诊断提示条
            if ((state == ToolCallState.ERROR || state == ToolCallState.TIMED_OUT) && diagnosis != null) {
                Spacer(Modifier.height(Spacing.xs))
                ToolCallCardErrorDiagnosis(diagnosis = diagnosis)
            }
            // 旁路环境状态条：仅 SHELL 类工具且有快照时（与旧实现一致）
            if (toolType == ToolType.SHELL && environmentSnapshot != null) {
                Spacer(Modifier.height(Spacing.xs))
                EnvironmentStatusStrip(snapshot = environmentSnapshot)
            }
        }
    }
}

/**
 * 折叠态左侧 24dp 圆角工具类型图标：淡色底 + 类型色图标。
 */
@Composable
private fun ToolTypeIcon(type: ToolType) {
    val (icon, tint) = when (type) {
        ToolType.SHELL -> Icons.Rounded.Terminal to Brand.Blue
        ToolType.FILE_READ -> Icons.Rounded.Visibility to Color(0xFF0D9488)
        ToolType.FILE_WRITE -> Icons.Rounded.Edit to Color(0xFFB45309)
        ToolType.BUILD_RUN -> Icons.Rounded.Build to Color(0xFFD97706)
        ToolType.SEARCH -> Icons.Rounded.Search to Color(0xFF7C3AED)
        ToolType.MCP -> Icons.Rounded.Extension to Color(0xFFDB2777)
        ToolType.ENV -> Icons.Rounded.Tune to Brand.Sky
        ToolType.TODO -> Icons.Rounded.CheckCircle to Brand.StatusGreen.run {
            if (LocalAppDarkMode.current) Dark else Light
        }
        ToolType.USAGE -> Icons.Rounded.BarChart to Color(0xFF64748B)
        ToolType.OTHER -> Icons.Rounded.Code to Brand.IconGray
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * 折叠态右侧状态标记：彩色圆点（运行中脉冲）+ 状态文字。
 */
@Composable
private fun StatusMarker(state: ToolCallState) {
    val isDark = LocalAppDarkMode.current
    val (color, label, pulsing) = when (state) {
        ToolCallState.RUNNING -> Triple(Brand.Blue, "运行中", true)
        ToolCallState.SUCCESS -> Triple(
            if (isDark) Brand.StatusGreen.Dark else Brand.StatusGreen.Light, "成功", false
        )
        ToolCallState.ERROR -> Triple(Color(0xFFEF4444), "失败", false)
        ToolCallState.TIMED_OUT -> Triple(Color(0xFFF59E0B), "超时", false)
        ToolCallState.CANCELLED -> Triple(Color(0xFF9CA3AF), "已取消", false)
        ToolCallState.PENDING -> Triple(Color(0xFF9CA3AF), "等待中", false)
    }

    val dotAlpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "toolcall-status")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "toolcall-status-alpha"
        ).value
    } else {
        1f
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dotAlpha }
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 展开态的完整命令区：等宽字体展示 toolArgs 中的 command。
 */
@Composable
private fun CommandSection(command: String) {
    Text(
        text = "命令",
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = command,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    )
}
