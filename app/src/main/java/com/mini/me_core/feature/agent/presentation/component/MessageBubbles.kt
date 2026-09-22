package com.mini.me_core.feature.agent.presentation.component

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.EnvironmentSnapshot
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.hasVisibleContent
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.component.agentfirst.AgentFirstFeatureFlags
import com.mini.me_core.feature.agent.presentation.component.agentfirst.ToolCallCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AgentMessageItem(
    message: AgentUIMessage,
    liveOutput: String? = null,
    markdownCache: MarkdownRenderCache? = null,
    onEditClick: ((AgentUIMessage) -> Unit)? = null,
    onNewChatClick: ((AgentUIMessage) -> Unit)? = null,
    initiallyExpanded: Boolean = true,
    environmentSnapshots: Map<String, EnvironmentSnapshot> = emptyMap(),
    agentState: AgentUIState = AgentUIState.Idle,
    onRetryTool: ((messageId: String) -> Unit)? = null,
    // Stage 4：连续同角色消息视觉分组（默认 null 保持向后兼容：单条消息四角大圆角）
    previousRole: MessageRole? = null,
    nextRole: MessageRole? = null
) {
    if (message.isCompactionMarker) {
        CompactionDivider()
        return
    }

    if (message.isBackgroundNotification) {
        BackgroundNotificationBar(message)
        return
    }

    val hasReasoning = message.role == MessageRole.ASSISTANT && !message.reasoning.isNullOrEmpty()
    val hasContent = message.content.hasVisibleContent()
    val hasAttachments = message.attachments.isNotEmpty()
    if (message.role == MessageRole.ASSISTANT && !hasContent && !hasReasoning) return

    val isUser = message.role == MessageRole.USER
    val isAssistant = message.role == MessageRole.ASSISTANT
    // Stage 4：连续同角色消息视觉分组。
    // sameAsPrev/sameAsNext 决定上下圆角收缩；startsNewGroup 在组间断开处补出更大纵向间距。
    val sameAsPrev = previousRole != null && previousRole == message.role
    val sameAsNext = nextRole != null && nextRole == message.role
    val startsNewGroup = previousRole != null && !sameAsPrev
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 紧凑优化：用户气泡放宽到 92% 屏宽，减少换行、降低整体纵向占用
    val maxUserBubbleWidth = remember(screenWidthDp) { (screenWidthDp * 0.92).dp }
    // Stage 4：AI 气泡轻量化，左对齐且限宽 90% 屏宽（不再全宽），提升阅读节奏
    val maxAssistantBubbleWidth = remember(screenWidthDp) { (screenWidthDp * 0.90).dp }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val copyScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Stage 4：组间断点补 12dp 顶距（父列已有 4dp，合计约 16dp）；组内保持 4dp
            .then(if (startsNewGroup) Modifier.padding(top = Spacing.md) else Modifier),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        if (hasReasoning) {
            ReasoningBubble(text = message.reasoning.orEmpty(), initiallyExpanded = false, cache = markdownCache)
        }
        if (hasContent || hasAttachments || message.role != MessageRole.ASSISTANT) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                // 助手消息左对齐，用户消息右对齐
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                if (hasContent || message.role == MessageRole.TOOL) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = when {
                                isUser -> RoundedCornerShape(Radius.md, Radius.md, Radius.xs, Radius.md)
                                else -> {
                                    // Stage 4：非用户气泡按连续同角色分组——
                                    // 组首上圆角大/下小，组中上下均小，组末上小/下大；单条四角大圆角。
                                    val topR = if (sameAsPrev) Radius.xs else Radius.lg
                                    val bottomR = if (sameAsNext) Radius.xs else Radius.lg
                                    RoundedCornerShape(topR, topR, bottomR, bottomR)
                                }
                            },
                            color = when (message.role) {
                                MessageRole.USER -> MaterialTheme.colorScheme.primary
                                MessageRole.ASSISTANT -> MaterialTheme.colorScheme.surface
                                MessageRole.TOOL -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            // Stage 4：去掉 ASSISTANT 的 1dp 边框，改用轻投影区分层次
                            border = null,
                            shadowElevation = if (isAssistant) 2.dp else 0.dp,
                            // 用户/AI 气泡按内容自适应宽度并限宽；工具气泡填满可用宽度，两侧外边距由 LazyColumn contentPadding 统一提供
                            modifier = when {
                                isUser -> Modifier.widthIn(max = maxUserBubbleWidth)
                                isAssistant -> Modifier.widthIn(max = maxAssistantBubbleWidth)
                                else -> Modifier.fillMaxWidth()
                            }
                        ) {
                        if (message.role == MessageRole.TOOL) {
                            // Agent-First ToolCallCard：feature flag 控制，默认 false 走旧 ToolMessageBody（行为完全不变）
                            if (AgentFirstFeatureFlags.isToolCallCardEnabled()) {
                                val live = liveOutput?.let {
                                    RunningToolOutput(
                                        messageId = message.id,
                                        text = it,
                                        toolName = message.toolName ?: "",
                                        toolArgs = message.toolArgs ?: ""
                                    )
                                }
                                ToolCallCard(
                                    message = message,
                                    liveOutput = live,
                                    environmentSnapshot = environmentSnapshots[message.id],
                                    agentState = agentState,
                                    onRetry = onRetryTool
                                )
                            } else {
                                ToolMessageBody(message, liveOutput = liveOutput, initiallyExpanded = initiallyExpanded, environmentSnapshots = environmentSnapshots)
                            }
                        } else {
                            val textColor = when (message.role) {
                                MessageRole.USER -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            SelectionContainer {
                                val selectionColors = if (isUser) {
                                    TextSelectionColors(
                                        handleColor = MaterialTheme.colorScheme.onPrimary,
                                        backgroundColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f),
                                    )
                                } else {
                                    TextSelectionColors(
                                        handleColor = MaterialTheme.colorScheme.primary,
                                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                    )
                                }
                                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                                    if (isUser) {
                                        Text(
                                            text = message.content,
                                            color = textColor,
                                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                                        )
                                    } else {
                                        MarkdownContent(
                                            text = message.content,
                                            color = textColor,
                                            // Stage 4：AI 气泡内边距提升到 16/12，视觉更透气
                                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                            cache = markdownCache
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                if (isUser && hasAttachments) {
                    MessageAttachmentPreviewRow(attachments = message.attachments)
                }
                // 气泡下方操作按钮（工具消息不显示）
                if (message.content.hasVisibleContent() && message.role != MessageRole.TOOL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        // 复制
                        MessageActionIconButton(
                            icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = if (copied) stringResource(R.string.chat_copied) else stringResource(R.string.chat_copy),
                            tint = iconTint,
                            onClick = {
                                copyScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("message", message.content))
                                    )
                                    copied = true
                                }
                            }
                        )
                        // 编辑（仅用户消息）：填入输入框，允许修改后重发
                        if (isUser && onEditClick != null) {
                            MessageActionIconButton(
                                icon = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.chat_action_edit),
                                tint = iconTint,
                                onClick = { onEditClick(message) }
                            )
                        }
                        // 创建新聊天（仅用户消息）
                        if (isUser && onNewChatClick != null) {
                            MessageActionIconButton(
                                icon = Icons.Rounded.ChatBubble,
                                contentDescription = stringResource(R.string.chat_action_new_chat),
                                tint = iconTint,
                                onClick = { onNewChatClick(message) }
                            )
                        }
                        if (message.role == MessageRole.ASSISTANT && (message.inputTokens > 0 || message.outputTokens > 0)) {
                            val inStr = formatTokenCount(message.inputTokens)
                            val outStr = formatTokenCount(message.outputTokens)
                            Text(
                                text = "↑$inStr ↓$outStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 复制成功 1.5s 后恢复图标
                    if (copied) {
                        LaunchedEffect(copied) {
                            delay(1500)
                            copied = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(13.dp))
    }
}

/**
 * 后台任务完成通知的轻量提示条：不作为普通用户气泡展示，仅以紧凑横条形式告知用户
 * 哪个后台命令结束了、成功与否。从通知文本里提取 <status>/<summary> 字段。
 */
@Composable
private fun BackgroundNotificationBar(message: AgentUIMessage) {
    val content = message.content
    val statuses = Regex("<status>(.*?)</status>")
        .findAll(content).map { it.groupValues.getOrNull(1)?.trim()?.lowercase() }.filterNotNull().toList()
    val summaries = Regex("<summary>(.*?)</summary>")
        .findAll(content).map { it.groupValues.getOrNull(1)?.trim() }.filterNotNull().toList()
    val isSuccess = statuses.all { it == "completed" }
    val dotColor = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val label = when {
        summaries.size <= 1 -> summaries.firstOrNull() ?: stringResource(R.string.chat_bg_command_done)
        else -> {
            val failedCount = statuses.count { it != "completed" }
            val prefix = stringResource(R.string.ui______d1490ef3)
            val namePart = summaries.joinToString("、") { s -> s.removePrefix(prefix).substringBefore("」") }
            if (failedCount > 0) {
                stringResource(R.string.chat_bg_commands_partial_failed, summaries.size, failedCount, namePart)
            } else {
                stringResource(R.string.chat_bg_commands_done, summaries.size, namePart)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactionDivider() {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = stringResource(R.string.chat_context_compressed),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}