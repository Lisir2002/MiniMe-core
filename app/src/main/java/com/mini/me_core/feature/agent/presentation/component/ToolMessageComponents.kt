package com.mini.me_core.feature.agent.presentation.component
import com.mini.me_core.core.theme.ScaledAnimation
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Brand
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.feature.agent.domain.container.progress.InstallProgress
import com.mini.me_core.feature.agent.domain.container.progress.InstallProgressParsers
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.EnvironmentSnapshot
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R

internal val DiffAddBg = Color(0x3322C55E)
internal val DiffAddText = Color(0xFF22C55E)
internal val DiffRemoveBg = Color(0x33EF4444)
internal val DiffRemoveText = Color(0xFFEF4444)

internal const val DIFF_COLLAPSE_THRESHOLD = 20
internal const val TOOL_SECTION_LINE_LIMIT = 20

/**
 * 工具消息：默认折叠为一行「状态圆点 + 工具名 + 参数摘要 + 箭头」，点击展开查看「指令」与「结果」。
 * 状态圆点仿 Claude Code：运行中=白点闪烁、成功=绿、失败=红。
 * [liveOutput] 非空时进入「实时输出」模式：显示逐行累积输出。
 * 对 edit_file / write_file 这类带结构化差异的结果，展开后以「+新增/−删除」的彩色差异视图呈现。
 */
@Composable
internal fun ToolMessageBody(
    message: AgentUIMessage,
    liveOutput: String? = null,
    initiallyExpanded: Boolean = true,
    environmentSnapshots: Map<String, EnvironmentSnapshot> = emptyMap()
) {
    val colors = LocalAppTheme.current.colors
    val streaming = liveOutput != null
    val running = streaming || message.content.startsWith(SessionUseCase.PENDING_TOOL_MARKER) ||
        message.content.startsWith(SessionUseCase.LEGACY_PENDING_TOOL_MARKER)

    // 模型主动调用的环境探测工具：以「一行状态条 + 可折叠详情」的紧凑形态内嵌在回复气泡顶部，
    // 不展开为普通工具气泡（避免列出全部组件造成噪音）。
    if (message.toolName == "check_environment") {
        EnvironmentStatusStrip(
            message = message
        )
        return
    }

    // 浏览器操作工具：以「操作图标 + 动作名 + 目标 + 状态」的时间线卡片展示，
    // 替代通用工具气泡，让用户直观看到模型在浏览器里做了什么。
    if (message.toolName == "browser") {
        BrowserOperationCard(
            message = message,
            running = running,
            initiallyExpanded = initiallyExpanded
        )
        return
    }

    // 旁路环境探测快照：构建/环境变更命令执行后自动探测，绑定到触发它的 Bash 工具气泡底部。
    val envSnapshot = environmentSnapshots[message.id]

    val edit = if (!running && !message.isError &&
        (message.toolName == "editFile" || message.toolName == "writeFile")
    ) {
        remember(message.id, message.content) { parseEditDiff(message.content) }
    } else null

    val resultText = if (!running) {
        remember(message.id, message.content) { formatToolResult(message.content) }
    } else null
    val argHint = remember(message.toolArgs) { toolArgHint(message.toolArgs) }
    val argsFull = remember(message.toolArgs) { formatToolArgs(message.toolArgs) }

    val todoData = if (message.toolName == "todo" && !running && !message.isError) {
        remember(message.id, message.content) { parseTodoResult(message.content) }
    } else null
    val webSearchData = if (message.toolName == "websearch" && !running && !message.isError) {
        remember(message.id, message.content) { parseWebSearchResult(message.content) }
    } else null

    val expandable = !running && (edit != null || !resultText.isNullOrBlank() || !argsFull.isNullOrBlank()
            || (todoData != null && todoData.items.isNotEmpty()) || webSearchData != null)
    var expanded by remember(message.id) { mutableStateOf(initiallyExpanded && (edit != null || todoData != null)) }

    val toolLabel = if (edit != null) edit.path.substringAfterLast('/') else (message.toolName ?: stringResource(R.string.common_tool))

    // 安装进度：Bash 安装命令运行时，在工具气泡内实时显示进度与状态（进度条 + 当前阶段 + ETA）
    val installProgress = if (streaming && (message.toolName == "Bash" || message.toolName.isNullOrEmpty())) {
        resolveBubbleInstallProgress(message, liveOutput)
    } else null

    // 混合模式：工具块内边距 12dp
    Column(modifier = Modifier.padding(Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable) Modifier.clickable { expanded = !expanded } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusDot(running = running, isError = message.isError)
            Spacer(Modifier.width(Spacing.sm))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工具名（过长横向滚动，不再用省略号截断）
                HorizontalScrollableText(
                    text = toolLabel,
                    // 混合模式：工具头部 12sp 弱化色
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize
                    ),
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (edit == null && !argHint.isNullOrBlank()) {
                    Spacer(Modifier.width(Spacing.sm))
                    // 过长参数摘要横向滚动展示，不再用省略号截断
                    HorizontalScrollableText(
                        text = argHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            if (edit != null) {
                DiffStat(added = edit.added, removed = edit.removed)
                Spacer(Modifier.width(Spacing.sm))
            }
            if (todoData != null && todoData.total > 0) {
                Text(
                    text = "${todoData.completed}/${todoData.total}",
                    color = if (todoData.completed == todoData.total) DiffAddText
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            if (streaming) {
                TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant, dotSize = 5.dp)
            } else if (expandable) {
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.common_collapse_action) else stringResource(R.string.common_expand),
                    tint = Brand.IconGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (installProgress != null) {
            Spacer(Modifier.height(Spacing.xs))
            InstallProgressRow(installProgress)
        }
        if (streaming) {
            if (!liveOutput.isNullOrBlank()) {
                val truncated = remember(liveOutput) { liveOutput.takeLastLines(TOOL_SECTION_LINE_LIMIT) }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = truncated,
                    // 混合模式：工具输出 12sp 等宽，行高 18sp，灰色
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                        lineHeight = 18.sp
                    )
                )
            }
        } else if (expanded) {
            Column(
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(
                        onDoubleTap = { expanded = false }
                    )
                }
            ) {
                if (todoData != null && todoData.items.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    TodoCard(items = todoData.items)
                } else if (webSearchData != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    WebSearchResultCard(result = webSearchData)
                } else if (edit != null) {
                    edit.hunks.forEach { h ->
                        Spacer(Modifier.height(Spacing.xs))
                        DiffView(diff = h.diff, startLine = h.startLine)
                    }
                } else {
                    if (!argsFull.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        ToolSection(label = stringResource(R.string.tool_instruction), content = argsFull)
                    }
                    if (!resultText.isNullOrBlank()) {
                        Spacer(Modifier.height(Spacing.sm))
                        ToolSection(label = stringResource(R.string.tool_result), content = resultText)
                    }
                }
            }
        }
        // 旁路环境探测状态条：绑定到触发它的 Bash 工具气泡底部（仅构建/环境变更命令触发后展示）
        if (envSnapshot != null && !running) {
            Spacer(Modifier.height(Spacing.xs))
            EnvironmentStatusStrip(snapshot = envSnapshot)
        }
        // 文件卡片：工具结束后常显在消息底部，点击用系统 app 打开。
        if (!running && message.attachments.isNotEmpty()) {
            val context = LocalContext.current
            MessageAttachmentPreviewRow(
                attachments = message.attachments,
                onClick = { openAttachment(context, it) }
            )
        }
    }
}

/**
 * 连续相同工具调用的聚合面板：把「连续且工具名相同」的多次调用合并为一个可折叠面板，
 * 避免重复调用（如连续创建/编辑多个文件）时页面拥挤。
 *
 * 头部显示：工具图标 + 工具名 + 调用次数 + 状态汇总（成功/失败）+ 展开箭头。
 * 展开后渲染每个调用的紧凑行（[ToolMessageBody]，默认折叠为一行，可再点开看 diff/结果）。
 * 面板默认折叠；仅当 [messages] 中至少有一个正在流式（liveOutput 非空）时保持展开以实时展示进度。
 */
@Composable
internal fun ToolCallGroup(
    toolName: String,
    messages: List<AgentUIMessage>,
    runningTool: List<RunningToolOutput>,
    markdownCache: MarkdownRenderCache?,
    environmentSnapshots: Map<String, EnvironmentSnapshot> = emptyMap(),
    agentState: AgentUIState = AgentUIState.Idle
) {
    val anyStreaming = messages.any { m ->
        runningTool.any { it.messageId == m.id }
    }
    var expanded by remember(toolName, messages.map { it.id }) { mutableStateOf(anyStreaming) }
    val errorCount = messages.count { it.isError }
    val successCount = messages.size - errorCount

    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        // 混合模式：工具块背景 surfaceVariant（#F1F5F9 / #1E293B）
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 聚合面板头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // 工具图标（琥珀强调色，与 TOOL 子分类一致）
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Construction,
                    contentDescription = null,
                    tint = if (LocalAppDarkMode.current) Color(0xFFFBBF24) else Color(0xFFD97706),
                    modifier = Modifier.size(16.dp)
                )
                // 工具名（过长横向滚动，不再用省略号截断）
                HorizontalScrollableText(
                    text = toolName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                // 调用次数
                Text(
                    text = "×${messages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                // 状态汇总
                if (errorCount > 0) {
                    Text(
                        text = stringResource(R.string.tool_group_failed, errorCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = DiffRemoveText,
                        fontWeight = FontWeight.Medium
                    )
                } else if (successCount > 0 && !anyStreaming) {
                    Text(
                        text = stringResource(R.string.tool_group_success, successCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = DiffAddText,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 展开/折叠箭头
                androidx.compose.material3.Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.common_collapse_action) else stringResource(R.string.common_expand),
                    tint = Brand.IconGray,
                    modifier = Modifier.size(18.dp)
                )
            }
            // 聚合面板内容：每个调用一行（默认折叠）
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = ScaledAnimation.tween(300, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(ScaledAnimation.tween(200)),
                exit = shrinkVertically(
                    animationSpec = ScaledAnimation.tween(200),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(ScaledAnimation.tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    messages.forEachIndexed { index, message ->
                        val live = runningTool.firstOrNull { it.messageId == message.id }?.text
                        AgentMessageItem(
                            message = message,
                            liveOutput = live,
                            markdownCache = markdownCache,
                            initiallyExpanded = false,
                            environmentSnapshots = environmentSnapshots,
                            agentState = agentState,
                            previousRole = messages.getOrNull(index - 1)?.role,
                            nextRole = messages.getOrNull(index + 1)?.role
                        )
                    }
                }
            }
        }
    }
}

private fun String.takeLastLines(maxLines: Int): String {
    if (maxLines <= 0 || isEmpty()) return ""
    var seen = 0
    for (i in lastIndex downTo 0) {
        if (this[i] == '\n' && ++seen == maxLines) {
            return substring(i + 1)
        }
    }
    return this
}

/**
 * 工具状态圆点（仿 Claude Code）：运行中=主题中性「白点」并循环闪烁，成功=绿，失败=红。
 */
@Composable
internal fun ToolStatusDot(running: Boolean, isError: Boolean) {
    val baseColor = when {
        running -> MaterialTheme.colorScheme.onSurface
        isError -> DiffRemoveText
        else -> DiffAddText
    }
    val dotAlpha = if (running && !ScaledAnimation.isDisabled()) {
        val transition = rememberInfiniteTransition(label = "tool-status-dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(animation = ScaledAnimation.tween(650), repeatMode = RepeatMode.Reverse),
            label = "tool-status-dot-alpha"
        ).value
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { alpha = dotAlpha }
            .clip(CircleShape)
            .background(baseColor)
    )
}

/** 展开区的一段带小标题的内容块（如「指令」「结果」） */
@Composable
internal fun ToolSection(label: String, content: String) {
    val colors = LocalAppTheme.current.colors
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(2.dp))

    val lines = remember(content) { content.split("\n") }
    val collapsible = lines.size > TOOL_SECTION_LINE_LIMIT
    var expanded by remember(content) { mutableStateOf(false) }
    val visibleLines = if (collapsible && !expanded) lines.takeLast(TOOL_SECTION_LINE_LIMIT) else lines
    val hiddenCount = lines.size - TOOL_SECTION_LINE_LIMIT

    SelectionContainer {
        Text(
            text = visibleLines.joinToString("\n"),
            // 混合模式：工具输出 12sp 等宽，行高 18sp，灰色
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = LocalComponentTokens.current.text.bodySmallFontSize,
                lineHeight = 18.sp
            )
        )
    }

    if (collapsible) {
        DiffExpandToggle(
            expanded = expanded,
            hiddenCount = hiddenCount,
            onToggle = { expanded = !expanded }
        )
    }
}

/** 增删统计胶囊：绿色「+N」与红色「−M」。 */
@Composable
internal fun DiffStat(added: Int, removed: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (added > 0) {
            Text(
                text = "+$added",
                color = DiffAddText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (added > 0 && removed > 0) Spacer(Modifier.width(Spacing.xs))
        if (removed > 0) {
            Text(
                text = "−$removed",
                color = DiffRemoveText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 彩色行级差异视图
 */
@Composable
internal fun DiffView(diff: String, startLine: Int) {
    val lines = remember(diff) { diff.split("\n") }
    val collapsible = lines.size > DIFF_COLLAPSE_THRESHOLD
    var expanded by remember(diff) { mutableStateOf(false) }
    val visibleLines = if (collapsible && !expanded) lines.take(DIFF_COLLAPSE_THRESHOLD) else lines

    val mono = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace
    )
    val removeCount = lines.count { it.startsWith("-") }
    val addCount = lines.count { it.startsWith("+") }
    val maxLineNo = startLine + lines.size - removeCount - addCount + maxOf(removeCount, addCount)
    val gutterChars = maxOf(2, maxLineNo.toString().length)
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Column(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                    .background(MaterialTheme.colorScheme.background)
                    .horizontalScroll(rememberScrollState())
            ) {
                var oldLineNo = startLine
                var newLineNo = startLine
                visibleLines.forEach { line ->
                    val marker = line.firstOrNull()
                    val (bg, fg) = when (marker) {
                        '+' -> DiffAddBg to DiffAddText
                        '-' -> DiffRemoveBg to DiffRemoveText
                        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val lineNo = when (marker) {
                        '-' -> oldLineNo++
                        '+' -> newLineNo++
                        else -> { val n = newLineNo; oldLineNo++; newLineNo++; n }
                    }
                    val gutter = lineNo.toString().padStart(gutterChars)
                    val styled = buildAnnotatedString {
                        withStyle(SpanStyle(color = gutterColor)) {
                            append(gutter)
                            append("  ")
                        }
                        withStyle(SpanStyle(color = fg)) {
                            append(line.ifEmpty { " " })
                        }
                    }
                    Text(
                        text = styled,
                        style = mono,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = Spacing.sm, vertical = 1.dp)
                    )
                }
            }
        }
        if (collapsible) {
            DiffExpandToggle(
                expanded = expanded,
                hiddenCount = lines.size - DIFF_COLLAPSE_THRESHOLD,
                onToggle = { expanded = !expanded }
            )
        }
    }
}

/** 长差异的页脚切换 */
@Composable
internal fun DiffExpandToggle(expanded: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(R.string.common_collapse_action) else stringResource(R.string.common_expand),
            tint = Brand.IconGray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = if (expanded) stringResource(R.string.common_collapse_action) else stringResource(R.string.tool_expand_remaining, hiddenCount),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** edit_file 单处编辑的差异片段。 */
internal data class EditHunk(val startLine: Int, val diff: String)

/** 文件变更类型：增 / 改 / 删，用于弹窗内分类标签与差异化展示。 */
internal enum class FileChangeType { CREATE, MODIFY, DELETE }

/** 工具执行日志条目：用于弹窗「日志」Tab，按时间顺序展示任务内全部工具调用。 */
internal data class ToolLogEntry(
    val toolName: String?,
    val args: String?,
    val result: String?,
    val isError: Boolean
)

/** 底部弹窗数据：文件变更（增/删/改）+ 工具执行日志。 */
internal data class TaskChangesSheetData(
    val fileDiffs: List<EditDiff>,
    val logs: List<ToolLogEntry>
)

/** edit_file 结果中解析出的结构化差异 */
internal data class EditDiff(
    val path: String,
    val added: Int,
    val removed: Int,
    val hunks: List<EditHunk>,
    val type: FileChangeType = FileChangeType.MODIFY,
    /** 删除条目是否为通配符模式（如 `*.log`），用于弹窗差异化展示。 */
    val isWildcard: Boolean = false
)

/**
 * 从持久化的 TOOL 内容中解析 edit_file / write_file 的结构化差异
 */
internal fun parseEditDiff(content: String): EditDiff? {
    val dataObj = extractToolDataObject(content)
    if (dataObj != null) {
        return parseEditDiffObject(dataObj)
    }

    val start = content.indexOf('{')
    val end = content.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
        parseEditDiffObject(Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject)
    }.getOrNull()
}

private fun parseEditDiffObject(obj: JsonObject): EditDiff? {
    val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: ""
    val added = obj["added_lines"]?.jsonPrimitive?.intOrNull ?: 0
    val removed = obj["removed_lines"]?.jsonPrimitive?.intOrNull ?: 0

    // 判断文件变更类型：writeFile 的 created=true 为新增，否则默认为修改
    val type = if (obj["created"]?.jsonPrimitive?.booleanOrNull == true) FileChangeType.CREATE
               else FileChangeType.MODIFY

    val hunks = obj["hunks"]?.jsonArray?.mapNotNull { el ->
        val ho = el.jsonObject
        val d = ho["diff"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        EditHunk(startLine = ho["start_line"]?.jsonPrimitive?.intOrNull ?: 1, diff = d)
    } ?: run {
        val d = obj["diff"]?.jsonPrimitive?.contentOrNull ?: return null
        listOf(EditHunk(startLine = obj["start_line"]?.jsonPrimitive?.intOrNull ?: 1, diff = d))
    }
    if (hunks.isEmpty()) return null
    return EditDiff(path = path, added = added, removed = removed, hunks = hunks, type = type)
}

/**
 * 把落库的原始工具结果清洗成可读文本
 */
internal fun formatToolResult(raw: String): String {
    val s = raw.withoutToolStatusPrefix()
    parseToolTransport(s)?.let { obj ->
        return when (obj["status"]?.jsonPrimitive?.contentOrNull) {
            "error" -> obj["message"]?.jsonPrimitive?.contentOrNull ?: s
            "success", "partial" -> formatToolData(obj["data"]) ?: s
            else -> s
        }
    }

    when {
        s.startsWith("Error(") -> {
            val msgIdx = s.indexOf("message=")
            if (msgIdx >= 0) {
                var body = s.substring(msgIdx + "message=".length)
                val codeIdx = body.lastIndexOf(", code=")
                body = if (codeIdx >= 0) body.substring(0, codeIdx) else body.removeSuffix(")")
                return body.trim()
            }
        }
        s.startsWith("Success(data=") -> {
            val inner = s.removePrefix("Success(data=").removeSuffix(")")
            return formatJsonData(inner) ?: inner.trim()
        }
        s.startsWith("Partial(data=") -> {
            var inner = s.removePrefix("Partial(data=")
            val msgIdx = inner.lastIndexOf(", message=")
            inner = if (msgIdx >= 0) inner.substring(0, msgIdx) else inner.removeSuffix(")")
            return formatJsonData(inner) ?: inner.trim()
        }
    }
    return s
}

private fun parseToolTransport(raw: String): JsonObject? {
    return runCatching {
        val obj = Json.parseToJsonElement(raw.trim()).jsonObject
        if (obj["status"] != null) obj else null
    }.getOrNull()
}

private fun extractToolDataObject(raw: String): JsonObject? {
    return (parseToolTransport(raw)?.get("data") as? JsonObject)
}

private fun formatToolData(data: JsonElement?): String? {
    return when (data) {
        is JsonPrimitive -> data.contentOrNull ?: data.toString()
        is JsonObject -> {
            val main = data["content"] ?: data["output"] ?: data["stdout"] ?: data["text"]
            val mainStr = (main as? JsonPrimitive)?.contentOrNull
            mainStr ?: data.entries.joinToString("\n") { (k, v) ->
                val vv = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
                "$k: $vv"
            }
        }
        null -> null
        else -> data.toString()
    }
}

internal fun String.withoutToolStatusPrefix(): String = trim()
    .removePrefix(SessionUseCase.LEGACY_STOPPED_TOOL_MARKER)
    .removePrefix(SessionUseCase.LEGACY_PENDING_TOOL_MARKER)
    .removePrefix(SessionUseCase.PENDING_TOOL_MARKER)
    .trim()

/**
 * 把 `data=` 里的 JsonElement 文本渲染成可读结果
 */
internal fun formatJsonData(jsonStr: String): String? = runCatching {
    when (val el = Json.parseToJsonElement(jsonStr.trim())) {
        is JsonPrimitive -> formatToolData(el) ?: jsonStr.trim()
        is JsonObject -> formatToolData(el) ?: jsonStr.trim()
        else -> jsonStr.trim()
    }
}.getOrNull()

/** 把传入参数 JSON 列成 `key: value` 多行 */
internal fun formatToolArgs(argsJson: String?): String? {
    if (argsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        if (obj.isEmpty()) return null
        obj.entries.joinToString("\n") { (k, v) ->
            val vv = (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
            "$k: $vv"
        }
    }.getOrNull() ?: argsJson.trim()
}

/** 标题行内联的参数摘要 */
internal fun toolArgHint(argsJson: String?): String? {
    if (argsJson.isNullOrBlank()) return null
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        val preferred = listOf("command", "cmd", "path", "file_path", "file", "query", "pattern", "url", "name")
        val v = preferred.firstNotNullOfOrNull { obj[it] } ?: obj.values.firstOrNull()
        val str = (v as? JsonPrimitive)?.contentOrNull ?: v?.toString()
        str?.replace("\n", " ")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

/**
 * 从工具消息中解析正在运行的安装进度，供工具气泡内联展示。
 * 仅当命令命中安装类包管理器（apt/apk/pip/sdkmanager）且能解析出进度时返回非空。
 * 完成（DONE）状态返回 null，避免在气泡内残留已完成进度条。
 */
private fun resolveBubbleInstallProgress(message: AgentUIMessage, liveOutput: String?): InstallProgress? {
    if (liveOutput.isNullOrBlank()) return null
    val parser = InstallProgressParsers.parserFor(extractCommandFromArgs(message.toolArgs)) ?: return null
    val lastLine = liveOutput.lineSequence().lastOrNull { it.isNotBlank() } ?: return null
    val progress = parser.parse(lastLine)
    if (progress != null && !progress.isDone) return progress
    return null
}

/** 从工具参数 JSON 预览中提取 command 字段。 */
private fun extractCommandFromArgs(argsPreview: String?): String {
    if (argsPreview.isNullOrBlank()) return ""
    return runCatching {
        val obj = Json.parseToJsonElement(argsPreview).jsonObject
        obj["command"]?.jsonPrimitive?.contentOrNull ?: ""
    }.getOrDefault("")
}

/**
 * 环境探测状态条：以「一行状态条 + 可折叠详情」的紧凑形态内嵌在回复气泡顶部。
 * - 探测中：显示「正在检测构建环境…」+ 转圈；
 * - 完成：一行结论（就绪 ✓ / 缺少 X，正在安装… / 未就绪：缺少 X、Y）+ 可折叠展开组件明细。
 *
 * 两个数据来源：
 * - 模型主动调用 check_environment → [message] 解析其 content；
 * - 旁路探测（构建/环境变更命令后）→ [snapshot] 直接携带组件状态。
 */
@Composable
internal fun EnvironmentStatusStrip(
    message: AgentUIMessage? = null,
    snapshot: EnvironmentSnapshot? = null
) {
    val isDark = LocalAppDarkMode.current
    val components = when {
        snapshot != null -> snapshot.components
        message != null -> remember(message.id, message.content) { parseEnvironmentComponents(message.content) }
        else -> emptyList()
    }
    val running = snapshot?.probing == true
    val key = snapshot?.key ?: message?.id ?: "env"
    var expanded by remember(key) { mutableStateOf(false) }
    val installedCount = components.count { it.status == EnvironmentStatus.INSTALLED }
    val missing = components.filter { it.status == EnvironmentStatus.MISSING }
    val installing = components.filter { it.status == EnvironmentStatus.INSTALLING }

    val ready = missing.isEmpty() && installing.isEmpty() && components.isNotEmpty()
    val statusColor = when {
        running -> MaterialTheme.colorScheme.primary
        ready -> Color(0xFF22C55E)
        else -> Color(0xFFEF4444)
    }
    val statusIcon = when {
        running -> Icons.Rounded.Sync
        ready -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Cancel
    }
    val summaryText = when {
        running -> stringResource(R.string.env_strip_checking)
        ready -> stringResource(R.string.env_strip_ready)
        installing.isNotEmpty() -> stringResource(R.string.env_strip_installing, installing.first().name)
        else -> stringResource(
            R.string.env_strip_missing,
            missing.take(2).joinToString("、") { it.name }
        )
    }

    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF1F5F9),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = components.isNotEmpty() && !running) { expanded = !expanded }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                // 过长摘要横向滚动展示，不再用省略号截断
                HorizontalScrollableText(
                    text = summaryText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                if (components.isNotEmpty() && !running) {
                    Text(
                        text = stringResource(R.string.env_strip_summary, installedCount, components.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = ScaledAnimation.tween(200, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(ScaledAnimation.tween(150)),
                exit = shrinkVertically(
                    animationSpec = ScaledAnimation.tween(150),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(ScaledAnimation.tween(100))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    components.forEach { component ->
                        EnvironmentComponentRow(component)
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 浏览器操作时间线卡片 ───────────────────────────

/**
 * 浏览器操作元数据：动作名、显示图标、目标字段优先级（用于提取操作目标摘要）。
 */
private data class BrowserActionMeta(
    val label: String,
    val icon: ImageVector,
    val targetFields: List<String> = emptyList()
)

/**
 * 浏览器动作元数据映射表：覆盖最常用的 30+ 种操作，
 * 未在表中的动作回退为通用浏览器图标。
 */
private val browserActionMeta: Map<String, BrowserActionMeta> = mapOf(
    "navigate" to BrowserActionMeta("打开网页", Icons.Rounded.Public, listOf("url")),
    "view" to BrowserActionMeta("查看页面", Icons.Rounded.Visibility, listOf("url")),
    "snapshot" to BrowserActionMeta("页面快照", Icons.Rounded.ContentPaste, listOf("snapshot_level")),
    "page_text" to BrowserActionMeta("提取正文", Icons.Rounded.Article, emptyList()),
    "extract" to BrowserActionMeta("结构化抽取", Icons.Rounded.FilterList, listOf("selector", "mode")),
    "click" to BrowserActionMeta("点击元素", Icons.Rounded.TouchApp, listOf("element_id", "id")),
    "type" to BrowserActionMeta("输入文本", Icons.Rounded.Keyboard, listOf("element_id", "text")),
    "fill_form" to BrowserActionMeta("填写表单", Icons.Rounded.EditNote, listOf("fields")),
    "select_option" to BrowserActionMeta("下拉选择", Icons.Rounded.ArrowDropDown, listOf("element_id", "value")),
    "submit" to BrowserActionMeta("提交表单", Icons.Rounded.Send, listOf("element_id")),
    "scroll" to BrowserActionMeta("滚动页面", Icons.Rounded.SwipeVertical, listOf("direction")),
    "hover" to BrowserActionMeta("悬停元素", Icons.Rounded.Mouse, listOf("element_id")),
    "drag" to BrowserActionMeta("拖拽元素", Icons.Rounded.DragIndicator, listOf("element_id")),
    "press_key" to BrowserActionMeta("按键", Icons.Rounded.Keyboard, listOf("element_id", "key")),
    "upload_file" to BrowserActionMeta("上传文件", Icons.Rounded.Upload, listOf("element_id", "file_path")),
    "back" to BrowserActionMeta("后退", Icons.Rounded.ArrowBack, emptyList()),
    "forward" to BrowserActionMeta("前进", Icons.Rounded.ArrowForward, emptyList()),
    "reload" to BrowserActionMeta("刷新", Icons.Rounded.Refresh, emptyList()),
    "screenshot" to BrowserActionMeta("截图", Icons.Rounded.PhotoCamera, emptyList()),
    "evaluate" to BrowserActionMeta("执行JS", Icons.Rounded.Code, listOf("js")),
    "wait_for" to BrowserActionMeta("等待元素", Icons.Rounded.Schedule, listOf("selector", "timeout_ms")),
    "wait_for_change" to BrowserActionMeta("等待变化", Icons.Rounded.HourglassEmpty, listOf("timeout_ms")),
    "wait_for_network_idle" to BrowserActionMeta("等待网络空闲", Icons.Rounded.NetworkCheck, listOf("timeout_ms")),
    "history" to BrowserActionMeta("操作历史", Icons.Rounded.History, emptyList()),
    "get_attribute" to BrowserActionMeta("读取属性", Icons.Rounded.Info, listOf("element_id", "attribute")),
    "handle_dialog" to BrowserActionMeta("处理弹窗", Icons.Rounded.Chat, listOf("accept")),
    "login" to BrowserActionMeta("自动登录", Icons.Rounded.Login, emptyList()),
    "takeover" to BrowserActionMeta("请求接管", Icons.Rounded.Person, emptyList()),
    "new_tab" to BrowserActionMeta("新建标签", Icons.Rounded.Add, listOf("url")),
    "switch_tab" to BrowserActionMeta("切换标签", Icons.Rounded.Tab, listOf("tab_id")),
    "close_tab" to BrowserActionMeta("关闭标签", Icons.Rounded.Close, listOf("tab_id")),
    "list_tabs" to BrowserActionMeta("标签列表", Icons.Rounded.Tab, emptyList()),
    "safe_click" to BrowserActionMeta("安全点击", Icons.Rounded.VerifiedUser, listOf("element_id")),
    "human_type" to BrowserActionMeta("模拟输入", Icons.Rounded.Psychology, listOf("element_id", "text")),
    "detect_captcha" to BrowserActionMeta("检测验证码", Icons.Rounded.Security, emptyList()),
    "screenshot_full_page" to BrowserActionMeta("全页截图", Icons.Rounded.PhotoLibrary, emptyList()),
)

/** 通用浏览器操作图标（未在映射表中的动作回退） */
private val defaultBrowserIcon = Icons.Rounded.Language

/**
 * 浏览器操作时间线卡片：
 * - 头部一行：状态圆点 + 操作图标 + 动作名 + 目标摘要 + 展开箭头
 * - 运行中显示打字动画，失败显示红色
 * - 点击展开查看完整参数和返回结果
 */
@Composable
internal fun BrowserOperationCard(
    message: AgentUIMessage,
    running: Boolean,
    initiallyExpanded: Boolean = false
) {
    val args = remember(message.toolArgs) { parseBrowserArgs(message.toolArgs) }
    val action = args["action"] ?: "unknown"
    val meta = browserActionMeta[action] ?: BrowserActionMeta(action, defaultBrowserIcon)
    val target = remember(args) { extractBrowserTarget(args, meta) }
    val resultText = if (!running) {
        remember(message.id, message.content) { formatToolResult(message.content) }
    } else null
    val argsFull = remember(message.toolArgs) { formatToolArgs(message.toolArgs) }
    val expandable = !running && (!resultText.isNullOrBlank() || !argsFull.isNullOrBlank())
    var expanded by remember(message.id) { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolStatusDot(running = running, isError = message.isError)
            Spacer(Modifier.width(Spacing.sm))
            // 操作图标
            Icon(
                imageVector = meta.icon,
                contentDescription = null,
                tint = when {
                    running -> MaterialTheme.colorScheme.primary
                    message.isError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            // 动作名
            Text(
                text = meta.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = LocalComponentTokens.current.text.bodySmallFontSize
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            // 目标摘要
            if (!target.isNullOrBlank()) {
                Spacer(Modifier.width(Spacing.sm))
                HorizontalScrollableText(
                    text = target,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (running) {
                TypingDots(color = MaterialTheme.colorScheme.onSurfaceVariant, dotSize = 5.dp)
            } else if (expandable) {
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = Brand.IconGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 展开详情：参数 + 返回结果
        if (expanded && !running) {
            Spacer(Modifier.height(Spacing.xs))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp)
            ) {
                if (!argsFull.isNullOrBlank()) {
                    Text(
                        text = "参数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = argsFull,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!resultText.isNullOrBlank()) {
                    if (!argsFull.isNullOrBlank()) Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "结果",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (message.isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = if (message.isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 解析 browser 工具参数 JSON 为 Map */
private fun parseBrowserArgs(argsJson: String?): Map<String, String> {
    if (argsJson.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = Json.parseToJsonElement(argsJson).jsonObject
        obj.mapValues { (_, v) ->
            (v as? JsonPrimitive)?.contentOrNull ?: v.toString()
        }
    }.getOrDefault(emptyMap())
}

/** 从 browser 参数中提取操作目标摘要（url / element_id / text 等） */
private fun extractBrowserTarget(args: Map<String, String>, meta: BrowserActionMeta): String? {
    // 优先使用元数据中指定的目标字段
    for (field in meta.targetFields) {
        args[field]?.let { value ->
            if (value.isNotBlank()) {
                // URL 过长时截断显示
                val display = if (value.length > 60) value.take(57) + "..." else value
                return display.replace("\n", " ")
            }
        }
    }
    // 回退：取第一个非 action 的字段值
    return args.entries
        .firstOrNull { it.key != "action" && it.value.isNotBlank() }
        ?.value
        ?.replace("\n", " ")
        ?.take(60)
}
