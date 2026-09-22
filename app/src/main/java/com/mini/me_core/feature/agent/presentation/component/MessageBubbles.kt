package com.mini.me_core.feature.agent.presentation.component

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.tokens.LocalAppTheme
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val colors = LocalAppTheme.current.colors
    // Stage 4：连续同角色消息视觉分组。
    // sameAsPrev/sameAsNext 决定上下圆角收缩；startsNewGroup 在组间断开处补出更大纵向间距。
    val sameAsPrev = previousRole != null && previousRole == message.role
    val sameAsNext = nextRole != null && nextRole == message.role
    val startsNewGroup = previousRole != null && !sameAsPrev
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    // 混合模式：用户气泡限宽 82% 屏宽，右对齐
    val maxUserBubbleWidth = remember(screenWidthDp) { (screenWidthDp * 0.82).dp }
    // 混合模式：AI 回复左对齐限宽 95% 屏宽（无气泡，透明背景）
    val maxAssistantBubbleWidth = remember(screenWidthDp) { (screenWidthDp * 0.95).dp }
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
                    // 混合模式：AI 回复左侧 2dp 竖线，高度跟随内容
                    val isDark = LocalAppDarkMode.current
                    Row(
                        modifier = if (isAssistant) Modifier.height(IntrinsicSize.Min) else Modifier,
                        verticalAlignment = if (isAssistant) Alignment.Top else Alignment.CenterVertically
                    ) {
                        // 混合模式：AI 回复左侧竖线标识（亮色 #3B82F6 / 暗色 #60A5FA）
                        if (isAssistant) {
                            val barColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(barColor)
                            )
                            Spacer(Modifier.width(Spacing.md))
                        }
                        Surface(
                            shape = when {
                                // 混合模式：用户气泡圆角 16/16/4/16（右下小圆角指向用户）
                                isUser -> RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                                // 混合模式：工具块圆角 10dp
                                message.role == MessageRole.TOOL -> RoundedCornerShape(Radius.md)
                                else -> {
                                    // AI 回复透明背景，形状无视觉影响
                                    RoundedCornerShape(Radius.lg)
                                }
                            },
                            color = when (message.role) {
                                // 混合模式：用户气泡统一 #3B82F6（亮/暗相同）
                                MessageRole.USER -> Color(0xFF3B82F6)
                                // 混合模式：AI 回复透明背景（无气泡），直接在页面背景上
                                MessageRole.ASSISTANT -> Color.Transparent
                                // 工具块背景 surfaceVariant（#F1F5F9 / #1E293B）
                                MessageRole.TOOL -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            // 混合模式：工具块 1dp 边框（outlineVariant）
                            border = if (message.role == MessageRole.TOOL) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            } else null,
                            // 混合模式：去掉 AI 回复投影（透明背景不需要阴影）
                            shadowElevation = 0.dp,
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
                                // 混合模式：用户气泡文字统一白色
                                MessageRole.USER -> Color.White
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            SelectionContainer {
                                val selectionColors = if (isUser) {
                                    TextSelectionColors(
                                        handleColor = Color.White,
                                        backgroundColor = Color.White.copy(alpha = 0.28f),
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
                                            // 混合模式：用户消息 14sp/行高20sp
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                lineHeight = 20.sp,
                                                fontSize = 14.sp
                                            ),
                                            // 混合模式：用户气泡内边距 12dp 水平 / 8dp 垂直
                                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
                                        )
                                    } else {
                                        MarkdownContent(
                                            text = message.content,
                                            color = textColor,
                                            // 混合模式：AI 回复内边距（左侧竖线已有 12dp 间距，start=0；右侧 16dp；垂直 4dp）
                                            modifier = Modifier.padding(start = 0.dp, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xs),
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
                    // 混合模式：AI 回复操作行撑满宽度，使 token 统计可右对齐
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (isAssistant) Modifier.fillMaxWidth() else Modifier
                    ) {
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
                            // 混合模式：token 统计右对齐，10sp/14sp 弱化色
                            Spacer(Modifier.weight(1f))
                            val tokenColor = colors.textTertiary
                            Text(
                                text = "↑$inStr ↓$outStr",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                ),
                                color = tokenColor
                            )
                        }
                        // v2 混合模式：时间戳（HH:mm），10sp 弱化色，右对齐
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = formatMessageTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
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
        // v2 混合模式：按钮 20dp（16dp 图标 + 2dp 两侧 padding），图标间间距 4dp
        modifier = Modifier.size(20.dp),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
    ) {
        // 混合模式：图标 16dp 弱化色
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(16.dp))
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

/** v2 混合模式：将消息时间戳格式化为 HH:mm 显示。 */
private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}