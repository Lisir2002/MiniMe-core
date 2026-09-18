package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.mini.me_core.newui.designsystem.component.AppWebHit
import com.mini.me_core.newui.designsystem.component.AppWebSearchCard
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
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
    AppMessageRow(
        text = msg.content,
        isUser = false,
        state = if (msg.isError) AppChatMessageState.Error else AppChatMessageState.Complete,
        modifier = modifier,
        leadingContent = if (hasReasoning) {
            { AppThinkingBlock(text = msg.reasoning.orEmpty(), initiallyExpanded = false) }
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

    Column(modifier = modifier) {
        AppToolCallCard(
            title = msg.toolName?.replaceFirst("mcp__", "").orEmpty().ifBlank { stringResource(R.string.common_tool_call_fallback) },
            summary = argHint,
            state = state,
            command = command,
            input = if (isBash) null else argsFull,
            output = resultText,
        )

        // todo 工具：解析成 AppTodoCard 叠加在工具卡下方。
        val todo = remember(msg.toolName, msg.content) {
            if (msg.toolName == "todo" || msg.toolName == "todowrite" || msg.toolName == "todo_list") {
                parseTodoResult(msg.content)
            } else null
        }
        if (todo != null) {
            Spacer(Modifier.height(AppSpacing.Sm))
            AppTodoCard(
                items = todo.items.map { item ->
                    AppTodoItem(
                        text = item.subject,
                        status = when (item.status) {
                            "completed" -> AppTodoStatus.Done
                            "in_progress" -> AppTodoStatus.Running
                            else -> AppTodoStatus.Pending
                        },
                    )
                },
            )
        }

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
