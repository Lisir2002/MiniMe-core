package com.mini.me_core.feature.agent.presentation.component.agentfirst

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.presentation.component.TOOL_SECTION_LINE_LIMIT
import com.mini.me_core.feature.agent.presentation.component.formatToolResult
import kotlinx.coroutines.launch

/**
 * 超大输出截断阈值：流式/落库输出超过此行数时只渲染末尾部分，避免长输出撑爆渲染与滚动性能。
 */
private const val LARGE_OUTPUT_LINE_LIMIT = 500

/**
 * 工具调用输出区域（Agent-First ToolCallCard 的子组件）。
 *
 * 两种模式：
 * - **非流式**（[isStreaming] == false）：调用现有 [formatToolResult] 清洗落库结果，渲染在
 *   `verticalScroll` 容器中；超过 [TOOL_SECTION_LINE_LIMIT]（20 行）时默认只显示末尾并提供展开切换。
 * - **流式**（[isStreaming] == true）：实时累积输出，自动滚动到底部；用户上翻时悬浮显示
 * 「↓ 有新输出」按钮，点击回到底部。
 *
 * 超大输出（超过 [LARGE_OUTPUT_LINE_LIMIT] 行）只渲染末尾部分并提示已截断前 N 行。
 *
 * @param content 非流式时为原始工具结果（会被清洗）；流式时为实时累积的输出文本
 * @param isStreaming 是否处于流式实时输出模式
 * @param modifier 外部修饰符
 */
@Composable
fun ToolCallCardOutput(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    if (isStreaming) {
        StreamingToolOutput(content = content, modifier = modifier)
    } else {
        FinishedToolOutput(content = content, modifier = modifier)
    }
}

/**
 * 非流式输出：清洗后的落库结果，超长可折叠。
 */
@Composable
private fun FinishedToolOutput(content: String, modifier: Modifier = Modifier) {
    // 复用现有清洗逻辑：把落库的原始工具结果（Success(data=...)/Error(...)/JSON transport）清洗成可读文本
    val cleaned = remember(content) { formatToolResult(content) }
    if (cleaned.isBlank()) return

    val scrollState = rememberScrollState()
    val lines = remember(cleaned) { cleaned.split("\n") }
    val totalLines = lines.size

    // 超大输出：只渲染末尾 LARGE_OUTPUT_LINE_LIMIT 行，顶部加截断提示
    val truncated = totalLines > LARGE_OUTPUT_LINE_LIMIT
    val baseText = if (truncated) {
        val hidden = totalLines - LARGE_OUTPUT_LINE_LIMIT
        "…（已截断前 $hidden 行，仅显示末尾内容）\n" +
            lines.takeLast(LARGE_OUTPUT_LINE_LIMIT).joinToString("\n")
    } else cleaned

    // 普通超长（20 行）：默认折叠只显示末尾，提供「展开/收起」切换
    val collapsible = !truncated && totalLines > TOOL_SECTION_LINE_LIMIT
    var expanded by remember(cleaned) { mutableStateOf(false) }
    val displayText = if (collapsible && !expanded) {
        val hidden = totalLines - TOOL_SECTION_LINE_LIMIT
        "…（省略前 $hidden 行）\n" +
            lines.takeLast(TOOL_SECTION_LINE_LIMIT).joinToString("\n")
    } else baseText

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        SelectionContainer {
            Text(
                text = displayText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        if (collapsible) {
            FinishedExpandToggle(expanded = expanded, onToggle = { expanded = !expanded })
        }
    }
}

/**
 * 非流式超长输出的「展开/收起」切换行。
 */
@Composable
private fun FinishedExpandToggle(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(onClick = onToggle)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (expanded) "收起" else "展开全部",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/**
 * 流式实时输出：自动贴底滚动，用户上翻时显示「↓ 有新输出」悬浮按钮。
 */
@Composable
private fun StreamingToolOutput(content: String, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 是否贴在底部：新输出到达时若已在底部则继续跟随；上翻则停止跟随并提示
    val atBottom by remember {
        derivedStateOf { scrollState.value == scrollState.maxValue }
    }
    var showNewOutput by remember { mutableStateOf(false) }

    // 新输出到达：贴底则平滑滚动到底；否则显示「有新输出」提示
    LaunchedEffect(content, atBottom) {
        if (atBottom && content.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
            showNewOutput = false
        } else if (!atBottom && content.isNotEmpty()) {
            showNewOutput = true
        }
    }

    // 流式输出同样做行数截断，避免长时间运行的命令累积数十万行导致性能问题
    val lines = remember(content) { content.split("\n") }
    val displayContent = if (lines.size > LARGE_OUTPUT_LINE_LIMIT) {
        val hidden = lines.size - LARGE_OUTPUT_LINE_LIMIT
        "…（已截断前 $hidden 行）\n" + lines.takeLast(LARGE_OUTPUT_LINE_LIMIT).joinToString("\n")
    } else content

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Text(
                text = displayContent,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        if (showNewOutput) {
            NewOutputPill(
                modifier = Modifier.align(Alignment.BottomCenter),
                onClick = {
                    scope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    showNewOutput = false
                }
            )
        }
    }
}

/**
 * 「↓ 有新输出」悬浮胶囊按钮：用户上翻后出现，点击回到底部。
 */
@Composable
private fun NewOutputPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
        modifier = modifier
            .padding(bottom = Spacing.xs)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = "↓ 有新输出",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}
