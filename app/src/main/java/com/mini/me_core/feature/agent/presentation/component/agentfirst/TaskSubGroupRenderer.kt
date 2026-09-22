package com.mini.me_core.feature.agent.presentation.component.agentfirst

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.EnvironmentSnapshot
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskSubGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType
import com.mini.me_core.feature.agent.presentation.component.AgentMessageItem

/**
 * TaskCard 子分组渲染器（Stage 2 - S2-3）。
 *
 * 按 [TaskSubGroup.type] 分发渲染：
 * - [TaskSubGroupType.USER] / [TaskSubGroupType.REPLY] / [TaskSubGroupType.REASONING]：
 *   复用 [AgentMessageItem] 逐条渲染消息气泡；
 * - [TaskSubGroupType.TOOL]：每条消息渲染为 [ToolCallCard]（Stage 1 组件），
 *   从 [liveOutputs] 中按 message.id 匹配实时输出，从 [environmentSnapshots] 匹配环境快照。
 *
 * 直接从 subGroup.messages 逐条渲染，不使用 buildRenderUnits 的归并逻辑。
 * 子分组展开/折叠状态由 [TaskSubGroup.isExpanded] 控制（ViewModel 维护），点击头部调用 [onToggleSubGroup]。
 *
 * @param taskId 所属任务 ID（用于 onToggleSubGroup 回调）
 * @param subGroup 任务子分组
 * @param liveOutputs 当前会话所有运行中工具的实时输出
 * @param environmentSnapshots 旁路环境探测快照（key = tool messageId）
 * @param agentState 全局 Agent 状态
 * @param onToggleSubGroup 点击子分组头部切换展开/折叠回调 (taskId, subGroupId)
 * @param onRetryTool 点击工具卡片「重试」回调（messageId）；null 时不显示重试按钮
 * @param modifier 外部修饰符
 */
@Composable
internal fun TaskSubGroupRenderer(
    taskId: String,
    subGroup: TaskSubGroup,
    liveOutputs: List<RunningToolOutput>,
    environmentSnapshots: Map<String, EnvironmentSnapshot>,
    agentState: AgentUIState,
    onToggleSubGroup: (String, String) -> Unit,
    onRetryTool: ((messageId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visual = subGroupVisualColor(subGroup.type)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // —— 子分组头部（可点击切换展开/折叠）——
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleSubGroup(taskId, subGroup.id) }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = subGroupTypeIcon(subGroup.type),
                contentDescription = null,
                tint = visual,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = subGroupTypeLabel(subGroup.type),
                color = visual,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${subGroup.messages.size}",
                color = visual.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = if (subGroup.isExpanded) Icons.Rounded.KeyboardArrowDown
                else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = visual.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }

        // —— 子分组内容（展开时渲染）——
        AnimatedVisibility(
            visible = subGroup.isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                when (subGroup.type) {
                    TaskSubGroupType.TOOL -> {
                        subGroup.messages.forEach { message ->
                            val live = liveOutputs.firstOrNull { it.messageId == message.id }
                            val snapshot = environmentSnapshots[message.id]
                            ToolCallCard(
                                message = message,
                                liveOutput = live,
                                environmentSnapshot = snapshot,
                                agentState = agentState,
                                onRetry = onRetryTool
                            )
                        }
                    }
                    else -> {
                        // USER / REPLY / REASONING：复用 AgentMessageItem（Stage 4：传入前后 role 分组）
                        subGroup.messages.forEachIndexed { index, message ->
                            val live = liveOutputs.firstOrNull { it.messageId == message.id }?.text
                            AgentMessageItem(
                                message = message,
                                liveOutput = live,
                                environmentSnapshots = environmentSnapshots,
                                agentState = agentState,
                                previousRole = subGroup.messages.getOrNull(index - 1)?.role,
                                nextRole = subGroup.messages.getOrNull(index + 1)?.role
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 子分组类型图标。
 */
private fun subGroupTypeIcon(type: TaskSubGroupType): ImageVector = when (type) {
    TaskSubGroupType.USER -> Icons.Rounded.Person
    TaskSubGroupType.REASONING -> Icons.Rounded.Star
    TaskSubGroupType.REPLY -> Icons.Rounded.ChatBubble
    TaskSubGroupType.TOOL -> Icons.Rounded.Construction
}

/**
 * 子分组类型标签（简洁中文）。
 */
private fun subGroupTypeLabel(type: TaskSubGroupType): String = when (type) {
    TaskSubGroupType.USER -> "用户"
    TaskSubGroupType.REASONING -> "思考"
    TaskSubGroupType.REPLY -> "回复"
    TaskSubGroupType.TOOL -> "工具"
}

/**
 * 子分组类型强调色（明暗模式适配）。
 */
@Composable
private fun subGroupVisualColor(type: TaskSubGroupType): Color {
    val isDark = LocalAppDarkMode.current
    return when (type) {
        TaskSubGroupType.USER -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        TaskSubGroupType.REASONING -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        TaskSubGroupType.REPLY -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        TaskSubGroupType.TOOL -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    }
}
