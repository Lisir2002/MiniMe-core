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
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.AgentAttachment
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.newui.designsystem.component.AppAttachmentCard
import com.mini.me_core.newui.designsystem.component.AppChatMarker
import com.mini.me_core.newui.designsystem.component.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppCitationCard
import com.mini.me_core.newui.designsystem.component.AppCitationSource
import com.mini.me_core.newui.designsystem.component.AppDiffCard
import com.mini.me_core.newui.designsystem.component.AppDiffLine
import com.mini.me_core.newui.designsystem.component.AppDiffLineType
import com.mini.me_core.newui.designsystem.component.AppFileCard
import com.mini.me_core.newui.designsystem.component.AppFileState
import com.mini.me_core.newui.designsystem.component.AppMessageRow
import com.mini.me_core.newui.designsystem.component.AppThinkingBlock
import com.mini.me_core.newui.designsystem.component.AppTodoCard
import com.mini.me_core.newui.designsystem.component.AppTodoItem
import com.mini.me_core.newui.designsystem.component.AppTodoStatus
import com.mini.me_core.newui.designsystem.component.AppToolCallCard
import com.mini.me_core.newui.designsystem.component.AppToolCallState
import com.mini.me_core.newui.designsystem.component.AppToolSummaryCard
import com.mini.me_core.newui.designsystem.component.AppToolSummaryState
import com.mini.me_core.newui.designsystem.component.AppTerminalLog
import com.mini.me_core.newui.designsystem.component.TerminalLineKind
import com.mini.me_core.newui.designsystem.component.TerminalLogLine
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppStroke
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 新版对话流节点渲染：把一条 [AgentUIMessage] 映射为 newui 对话流组件。
 *
 * 本文件只做 UI 接线，不持有状态、不改 ViewModel：
 *  - 用户消息 / 助手消息 → AppMessageRow（内部 AppChatBubble + AppMarkdownText）。
 *  - 助手内嵌思考 → AppThinkingBlock（作为 row 的 leadingContent，与正文同列对齐）。
 *  - 工具调用 → AppToolCallCard；bash/shell 走 AppTerminalLog；文件类工具走 AppFileCard，
 *    edit/write 返回 diff 时叠 AppDiffCard；联网搜索来源走 AppCitationCard；
 *    工具链收尾（ChatBlock.ToolGroup）叠 AppToolSummaryCard 汇总本轮工具数。
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

/** 工具调用卡：已完成工具渲染为 Success/Error；文件 diff / 文件状态 / 引用来源走结构化新卡。 */
@Composable
private fun ToolMessageNode(msg: AgentUIMessage, modifier: Modifier = Modifier) {
    val state = if (msg.isError) AppToolCallState.Error else AppToolCallState.Success
    val command = remember(msg.toolArgs) { bashCommandFromArgs(msg.toolArgs) }
    val isBash = command != null
    val argHint = remember(msg.toolName, msg.toolArgs) { toolArgHint(msg.toolArgs) }
    val argsFull = remember(msg.toolArgs) { formatToolArgs(msg.toolArgs) }
    val resultText = remember(msg.content) { formatToolResult(msg.content) }
    // bash/shell 类执行命令工具：改走 AppTerminalLog 终端卡（黑底等宽 + $ 前缀 + stderr 标红）。
    val toolName = msg.toolName?.replaceFirst("mcp__", "").orEmpty()
    val isBashTool = toolName.let {
        it == "bash" || it == "shell" || it == "run_command" ||
            it == "execute_command" || it == "Bash" || it.contains("bash", true) || it.contains("shell", true)
    }
    // 文件类工具（readFile/writeFile/editFile，兼容 snake_case）：AppFileCard 展示路径+大小+状态；
    // edit/write 产出结构化 diff 时再叠 AppDiffCard，替换 AppToolCallCard 的纯文本输出。
    val isFileTool = remember(toolName) { isFileToolName(toolName) }
    val edit = remember(toolName, msg.content, msg.isError) {
        if (!msg.isError && (toolName.equals("editFile", true) || toolName.equals("writeFile", true) ||
                toolName.equals("edit_file", true) || toolName.equals("write_file", true))
        ) parseEditDiff(msg.content) else null
    }
    val fileMeta = remember(toolName, msg.content, msg.toolArgs, msg.isError) {
        if (isFileTool) buildFileCardMeta(msg) else null
    }

    Column(modifier = modifier) {
        when {
            isBashTool -> {
                val termLines = remember(toolName, msg.toolArgs, msg.content) {
                    val cmd = bashCommandFromArgs(msg.toolArgs).orEmpty()
                    buildList {
                        if (cmd.isNotBlank()) add(TerminalLogLine.command(cmd, at = ""))
                        resultText.lineSequence().forEach { l ->
                            val lower = l.lowercase()
                            val kind = when {
                                msg.isError || lower.startsWith("error") || lower.startsWith("stderr") ||
                                    lower.contains("fatal") || lower.contains("exception") -> TerminalLineKind.Stderr
                                lower.contains("warning") || lower.startsWith("warn:") -> TerminalLineKind.Warning
                                else -> TerminalLineKind.Stdout
                            }
                            add(TerminalLogLine(text = l, kind = kind, at = ""))
                        }
                    }
                }
                AppTerminalLog(
                    title = toolName.ifBlank { stringResource(R.string.common_tool_call_fallback) },
                    lines = termLines,
                    running = false,
                    succeeded = !msg.isError,
                    onRerun = { /* 重跑占位：接现有重试入口暂 no-op */ },
                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                )
            }

            isFileTool && fileMeta != null -> {
                // 文件状态卡：路径 + 大小 + 成功(Downloaded)/失败(Error) 态。
                AppFileCard(
                    fileName = fileMeta.displayPath.ifBlank {
                        toolName.ifBlank { stringResource(R.string.common_tool_call_fallback) }
                    },
                    fileSize = fileMeta.sizeText,
                    state = if (msg.isError) AppFileState.Error else AppFileState.Downloaded,
                )
                // edit/write 行级 diff（默认折叠）：替换普通 AppToolCallCard 的文本输出。
                if (edit != null) {
                    Spacer(Modifier.height(AppSpacing.Sm))
                    AppDiffCard(
                        filePath = edit.path.ifBlank { fileMeta.displayPath },
                        additions = edit.added,
                        deletions = edit.removed,
                        lines = editDiffLines(edit),
                    )
                }
            }

            else -> {
                AppToolCallCard(
                    title = toolName.ifBlank { stringResource(R.string.common_tool_call_fallback) },
                    summary = argHint,
                    state = state,
                    command = command,
                    input = if (isBash) null else argsFull,
                    output = resultText,
                )
            }
        }

        // todo 工具不再在消息流里重复插卡：实时列表由吸附输入框上方的常驻任务条（TodoDockBar）承载，
        // 避免同一任务两处重复展示。此处仅保留普通工具卡渲染。

        // 联网搜索工具：解析命中并叠加 AppCitationCard（title + url + snippet，点按跳浏览器）。
        // 与旧 AppWebSearchCard 二选一：统一走新引用卡，避免同一来源两处重复展示。
        val web = remember(toolName, msg.content) {
            if (toolName == "websearch" || toolName == "web_search" ||
                toolName.contains("search", ignoreCase = true)
            ) parseWebSearchResult(msg.content) else null
        }
        if (web != null) {
            Spacer(Modifier.height(AppSpacing.Sm))
            AppCitationCard(
                sources = web.results.map { item ->
                    AppCitationSource(
                        title = item.title.ifBlank { item.url },
                        url = item.url,
                        snippet = item.excerpts.firstOrNull().orEmpty(),
                    )
                },
            )
        }
    }
}

/** 文件类工具名判定（兼容驼峰 readFile/writeFile/editFile 与下划线 read_file/...）。 */
private fun isFileToolName(name: String): Boolean {
    if (name.isBlank()) return false
    return name.equals("readFile", true) || name.equals("writeFile", true) ||
        name.equals("editFile", true) || name.equals("read_file", true) ||
        name.equals("write_file", true) || name.equals("edit_file", true)
}

/** 文件卡元信息：展示路径 + 人类可读大小。大小从结果 JSON（bytes_written / read_lines）或入参推导。 */
private data class FileCardMeta(val displayPath: String, val sizeText: String)

private fun buildFileCardMeta(msg: AgentUIMessage): FileCardMeta {
    // 路径优先取结果 data.path（toDisplayPath 后的展示路径），回退入参 path/file_path。
    var displayPath = ""
    var sizeText = ""
    runCatching {
        val outer = Json.parseToJsonElement(msg.content.withoutToolStatusPrefix()).jsonObject
        val data = outer["data"] as? kotlinx.serialization.json.JsonObject
        displayPath = data?.get("path")?.jsonPrimitive?.contentOrNull.orEmpty()
        val bytes = data?.get("bytes_written")?.jsonPrimitive?.intOrNull
        val lines = data?.get("read_lines")?.jsonPrimitive?.intOrNull
        sizeText = when {
            bytes != null && bytes > 0 -> humanFileSize(bytes.toLong())
            lines != null && lines > 0 -> "$lines 行"
            else -> ""
        }
    }
    if (displayPath.isBlank()) {
        displayPath = runCatching {
            val obj = Json.parseToJsonElement(msg.toolArgs.orEmpty()).jsonObject
            (obj["path"] ?: obj["file_path"])?.jsonPrimitive?.contentOrNull.orEmpty()
        }.getOrDefault("")
    }
    // 结果无大小时回退入参 content 长度（writeFile 入参带完整内容）。
    if (sizeText.isBlank() && !msg.isError) {
        runCatching {
            val obj = Json.parseToJsonElement(msg.toolArgs.orEmpty()).jsonObject
            val len = obj["content"]?.jsonPrimitive?.contentOrNull?.length
            if (len != null && len > 0) sizeText = humanFileSize(len.toLong())
        }
    }
    return FileCardMeta(displayPath = displayPath, sizeText = sizeText)
}

/** 把 parseEditDiff 的 hunks 展平成 AppDiffCard 的行列表：+ 新增 / - 删除 / 其余为上下文。 */
private fun editDiffLines(edit: EditDiff): List<AppDiffLine> = buildList {
    edit.hunks.forEach { hunk ->
        hunk.diff.lineSequence().forEach { raw ->
            val type = when (raw.firstOrNull()) {
                '+' -> AppDiffLineType.Add
                '-' -> AppDiffLineType.Remove
                else -> AppDiffLineType.Context
            }
            val text = when (raw.firstOrNull()) {
                '+', '-', ' ' -> raw.substring(1)
                else -> raw
            }
            add(AppDiffLine(type, text))
        }
    }
}

/** 字节数人类可读（KB/MB），仅做展示文案，不引入裸 dp / 裸色。 */
private fun humanFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
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
        // 工具链收尾过渡摘要卡：固定在链尾（不随折叠收起），汇总本轮工具调用数与成败。
        // toolCount 直接取 ChatBlock.ToolGroup.msgs.size（同一轮相邻 TOOL 消息合并的条数）。
        Spacer(Modifier.height(AppSpacing.Sm))
        AppToolSummaryCard(
            text = buildString {
                append("本轮共调用 ${group.msgs.size} 个工具")
                val fail = group.msgs.count { it.isError }
                if (fail == 0) append("，全部成功")
                else append("，成功 ${group.msgs.size - fail} / 失败 $fail")
                if (group.totalMs > 0) append("，用时 %.1fs".format(group.totalMs / 1000.0))
                append("。")
            },
            state = AppToolSummaryState.Done,
            toolCount = group.msgs.size,
        )
    }
}

/** 折叠链摘要行图标尺寸（复用 IconXl 刻度，避免表外裸 dp）。 */
private val AppSizingForGroup = com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconXs
