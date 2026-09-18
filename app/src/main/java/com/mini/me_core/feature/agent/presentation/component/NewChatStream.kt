package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.AgentAttachment
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.newui.designsystem.component.AppAttachmentCard
import com.mini.me_core.newui.designsystem.component.AppChatMarker
import com.mini.me_core.newui.designsystem.component.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppMessageRow
import com.mini.me_core.newui.designsystem.component.AppThinkingBlock
import com.mini.me_core.newui.designsystem.component.AppTodoCard
import com.mini.me_core.newui.designsystem.component.AppTodoItem
import com.mini.me_core.newui.designsystem.component.AppTodoStatus
import com.mini.me_core.newui.designsystem.component.AppToolCallCard
import com.mini.me_core.newui.designsystem.component.AppToolCallState
import com.mini.me_core.newui.designsystem.component.AppTerminalLog
import com.mini.me_core.newui.designsystem.component.LogLevel
import com.mini.me_core.newui.designsystem.component.TerminalLogLine
import com.mini.me_core.newui.designsystem.component.AppWebHit
import com.mini.me_core.newui.designsystem.component.AppWebSearchCard
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppStroke
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 新版对话流节点渲染：把一条 [AgentUIMessage] 映射为 newui 对话流组件。
 *
 * 本文件只做 UI 接线，不持有状态、不改 ViewModel：
 *  - 用户消息 / 助手消息 → AppMessageRow（内部 AppChatBubble + AppMarkdownText）。
 *  - 助手内嵌思考 → AppThinkingBlock（作为 row 的 leadingContent，与正文同列对齐）。
 *  - 工具调用 → AppToolCallCard；todo / 联网搜索解析后叠加 AppTodoCard / AppWebSearchCard。
 *  - 上下文压缩锚点 → AppChatMarker(System)。
 *
 * 数据解析（parseTodoResult / parseWebSearchResult / parseEditDiff / formatToolResult /
 * extractBashCommand / toolArgHint）复用既有 helper，不重复实现。
 */
@Composable
internal fun ChatMessageNode(
    msg: AgentUIMessage,
    onOpenAttachment: (AgentAttachment) -> Unit,
    onEditMessage: (AgentUIMessage) -> Unit,
    onNewChatFromMessage: (AgentUIMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 上下文压缩锚点：系统级居中 marker，不再渲染为气泡。
    if (msg.isCompactionMarker) {
        AppChatMarker(
            text = stringResource(R.string.common_compaction_marker),
            kind = AppChatMarkerKind.System,
            modifier = modifier,
        )
        return
    }

    when (msg.role) {
        MessageRole.USER -> UserMessageNode(msg, onOpenAttachment, modifier)

        MessageRole.ASSISTANT -> AssistantMessageNode(
            msg = msg,
            onEditMessage = onEditMessage,
            onNewChatFromMessage = onNewChatFromMessage,
            modifier = modifier,
        )

        MessageRole.TOOL -> ToolMessageNode(msg, modifier)
    }
}

/** 用户气泡：靠右，附件以 AppAttachmentCard 横排在气泡上方。 */
@Composable
private fun UserMessageNode(
    msg: AgentUIMessage,
    onOpenAttachment: (AgentAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppMessageRow(
        text = msg.content,
        isUser = true,
        modifier = modifier,
        leadingContent = if (msg.attachments.isNotEmpty()) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                    msg.attachments.forEach { att ->
                        AppAttachmentCard(
                            fileName = att.fileName.ifBlank { stringResource(R.string.common_attachment_fallback) },
                            mimeType = att.mimeType,
                            sizeBytes = att.sizeBytes,
                            isImage = att.isImage,
                            onClick = { onOpenAttachment(att) },
                        )
                    }
                }
            }
        } else null,
    )
}

/** 助手气泡：思考在前、正文在后；错误态给重试。 */
@Composable
private fun AssistantMessageNode(
    msg: AgentUIMessage,
    onEditMessage: (AgentUIMessage) -> Unit,
    onNewChatFromMessage: (AgentUIMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasReasoning = !msg.reasoning.isNullOrBlank()
    // 空正文兜底：模型只出了思考没落正文时，给一句明确提示，避免空白气泡。
    val bodyText = msg.content.ifBlank { "无正文回复［请查阅思考过程］" }
    AppMessageRow(
        text = bodyText,
        isUser = false,
        state = if (msg.isError) AppChatMessageState.Error else AppChatMessageState.Complete,
        modifier = modifier,
        leadingContent = if (hasReasoning) {
            { AppThinkingBlock(text = msg.reasoning.orEmpty(), initiallyExpanded = true) }
        } else null,
        // 长按助手消息提供「编辑此条重写」「从这里新开对话」。
        onRegenerate = { onEditMessage(msg) },
        onDelete = { onNewChatFromMessage(msg) },
    )
}

/** 工具调用卡：已完成工具渲染为 Success/Error；todo / websearch 解析后叠加结构化卡。 */
@Composable
private fun ToolMessageNode(msg: AgentUIMessage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = if (msg.isError) AppToolCallState.Error else AppToolCallState.Success
    val command = remember(msg.toolArgs) { bashCommandFromArgs(msg.toolArgs) }
    val isBash = command != null
    val argHint = remember(msg.toolName, msg.toolArgs) { toolArgHint(msg.toolArgs) }
    val argsFull = remember(msg.toolArgs) { formatToolArgs(msg.toolArgs) }
    val resultText = remember(msg.content) { formatToolResult(msg.content) }
    // bash/shell 类执行命令工具：改走 AppTerminalLog 终端卡（黑底等宽 + $ 前缀 + stderr 标红）。
    val isBashTool = msg.toolName?.let {
        it == "bash" || it == "shell" || it == "run_command" ||
            it == "execute_command" || it == "Bash" || it.contains("bash", true) || it.contains("shell", true)
    } == true

    Column(modifier = modifier) {
        if (isBashTool) {
            val termLines = remember(msg.toolName, msg.toolArgs, msg.content) {
                val cmd = bashCommandFromArgs(msg.toolArgs).orEmpty()
                buildList {
                    if (cmd.isNotBlank()) add(TerminalLogLine("$ $cmd", LogLevel.Info, ""))
                    resultText.lineSequence().forEach { l ->
                        val lower = l.lowercase()
                        val danger = msg.isError || lower.startsWith("error") || lower.startsWith("stderr") || lower.contains("fatal") || lower.contains("exception")
                        add(TerminalLogLine(l, if (danger) LogLevel.Danger else LogLevel.Info, ""))
                    }
                }
            }
            AppTerminalLog(
                title = msg.toolName?.replaceFirst("mcp__", "").orEmpty(),
                lines = termLines,
                running = false,
                onRerun = { /* 重跑占位：接现有重试入口暂 no-op */ },
                modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
            )
        } else {
            AppToolCallCard(
                title = msg.toolName?.replaceFirst("mcp__", "").orEmpty().ifBlank { stringResource(R.string.common_tool_call_fallback) },
                summary = argHint,
                state = state,
                command = command,
                input = if (isBash) null else argsFull,
                output = resultText,
            )
        }

        // todo 工具不再在消息流里重复插卡：实时列表由吸附输入框上方的常驻任务条（TodoDockBar）承载，
        // 避免同一任务两处重复展示。此处仅保留普通工具卡渲染。

        // 联网搜索工具：解析命中并叠加 AppWebSearchCard。
        val web = remember(msg.toolName, msg.content) {
            if (msg.toolName == "websearch" || msg.toolName == "web_search" ||
                msg.toolName?.contains("search", ignoreCase = true) == true
            ) parseWebSearchResult(msg.content) else null
        }
        if (web != null) {
            Spacer(Modifier.height(AppSpacing.Sm))
            AppWebSearchCard(
                hits = web.results.map { item ->
                    AppWebHit(
                        title = item.title.ifBlank { item.url },
                        domain = runCatching { java.net.URI(item.url).host ?: item.url }.getOrDefault(item.url),
                        snippet = item.excerpts.firstOrNull().orEmpty(),
                    )
                },
                onOpen = { hit ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(web.results.firstOrNull { it.title == hit.title }?.url ?: hit.domain),
                            ),
                        )
                    }
                },
            )
        }
    }
}

/** 从工具参数 JSON 中提取 bash 的 command 字段（对齐旧 ToolMessageComponents.extractCommandFromArgs）。 */
private fun bashCommandFromArgs(argsJson: String?): String? {
    if (argsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(argsJson).jsonObject
        val cmd = (obj["command"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?: (obj["cmd"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        cmd?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * 对话流渲染块：单条消息 / 连续工具调用折叠链。
 *
 * [Single] 单条普通消息；[ToolGroup] 同一轮里相邻的 N 条 TOOL 消息合并成一条折叠链，
 * 折叠态只显示一行摘要「调用 N 个工具 · 用时」，展开后逐条列出；组内任一失败则默认展开并标红。
 */
internal sealed interface ChatBlock {
    data class Single(val msg: AgentUIMessage) : ChatBlock
    data class ToolGroup(val msgs: List<AgentUIMessage>) : ChatBlock {
        val anyError: Boolean get() = msgs.any { it.isError }
        /** 组内总耗时 = 末条时间戳 - 首条时间戳（AgentUIMessage 无 per-tool duration 字段，用时间戳差近似）。 */
        val totalMs: Long
            get() = if (msgs.size > 1) (msgs.last().timestamp - msgs.first().timestamp).coerceAtLeast(0L) else 0L
    }
}

/** 把时序消息流折叠成渲染块：相邻 TOOL 消息合并为 [ChatBlock.ToolGroup]，其余单条成块。 */
internal fun List<AgentUIMessage>.toChatBlocks(): List<ChatBlock> {
    val result = ArrayList<ChatBlock>()
    var group = ArrayList<AgentUIMessage>()
    fun flush() {
        if (group.isNotEmpty()) {
            result.add(
                if (group.size == 1) ChatBlock.Single(group[0]) else ChatBlock.ToolGroup(group.toList())
            )
            group = ArrayList()
        }
    }
    for (m in this) {
        if (m.role == MessageRole.TOOL) {
            group.add(m)
        } else {
            flush()
            result.add(ChatBlock.Single(m))
        }
    }
    flush()
    return result
}

/**
 * 连续工具调用折叠链：默认折叠态单行摘要「调用 N 个工具 · 用时」，点按展开逐条列出；
 * 组内任一工具失败时默认展开并以错误色描边/文字标注。折叠交互风格对齐 [AppThinkingBlock]。
 */
@Composable
internal fun ToolCallGroupBlock(group: ChatBlock.ToolGroup, modifier: Modifier = Modifier) {
    var expanded by remember(group) { mutableStateOf(group.anyError) }
    val shape = RoundedCornerShape(AppRadius.Md)
    val palette = appPalette()
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.card)
                .border(
                    AppStroke.Thin,
                    if (group.anyError) AppColor.StatusDanger.copy(alpha = 0.45f) else palette.separator,
                    shape,
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = null,
                tint = if (group.anyError) AppColor.StatusDanger else palette.labelSecondary,
                modifier = Modifier.size(AppSizingForGroup),
            )
            Spacer(Modifier.size(AppSpacing.Sm))
            Text(
                text = "调用 ${group.msgs.size} 个工具" +
                    if (group.totalMs > 0) " · %.1fs".format(group.totalMs / 1000.0) else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (group.anyError) AppColor.StatusDanger else palette.ink,
                modifier = Modifier.weight(1f),
            )
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                label = "toolGroupChevron",
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起工具链" else "展开工具链",
                tint = palette.labelSecondary,
                modifier = Modifier
                    .size(AppSizingForGroup)
                    .rotate(rotation),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(AppSpacing.Sm))
            group.msgs.forEachIndexed { index, m ->
                ToolMessageNode(msg = m)
                if (index != group.msgs.lastIndex) Spacer(Modifier.height(AppSpacing.Sm))
            }
        }
    }
}

/** 折叠链摘要行图标尺寸（复用 IconXl 刻度，避免表外裸 dp）。 */
private val AppSizingForGroup = com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconXs
