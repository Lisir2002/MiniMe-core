package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.core.util.LogLineParser
import com.mini.me_core.core.util.ParsedLogLine

/**
 * 日志列表项：一条「结构化日志行」。
 *
 * - [Entry]：一条带行头的日志（时间戳 + 等级徽章 + Tag + 消息），其后可跟随若干堆栈附属行。
 * - [Loose]：游离附属行（没有对应行头的孤立行，如文件重置分隔线），灰色缩进渲染。
 */
sealed interface LogListItem {
    data class Entry(
        val headerRaw: String,
        val parsed: ParsedLogLine,
        val stack: List<String>,
    ) : LogListItem

    data class Loose(val line: String) : LogListItem

    /** 某等级整组折叠后的汇总行（点击展开该等级所有日志）。 */
    data class Collapsed(val level: LogLevel, val count: Int) : LogListItem
}

/**
 * 把扁平的日志行列表聚合成「行头 + 堆栈附属行」分组。
 *
 * 能被 [LogLineParser] 解析的行作为新 Entry 的行头；紧随其后无法解析的行归入该 Entry 的堆栈。
 * 在任何行头之前出现的孤立行作为 [LogListItem.Loose] 保留。
 */
fun buildLogEntries(lines: List<String>): List<LogListItem> {
    val items = mutableListOf<LogListItem>()
    var currentHeader: String? = null
    var currentParsed: ParsedLogLine? = null
    var stack = mutableListOf<String>()

    fun flush() {
        val h = currentHeader
        val p = currentParsed
        if (h != null && p != null) {
            items.add(LogListItem.Entry(h, p, stack.toList()))
        }
    }

    for (line in lines) {
        val parsed = LogLineParser.parse(line)
        if (parsed != null) {
            flush()
            currentHeader = line
            currentParsed = parsed
            stack = mutableListOf()
        } else {
            if (currentHeader == null) {
                items.add(LogListItem.Loose(line))
            } else {
                stack.add(line)
            }
        }
    }
    flush()
    return items
}

/**
 * 按折叠等级把连续同等级 Entry 折叠成汇总行。
 *
 * 被折叠等级的连续 Entry 合并为一条 [LogListItem.Collapsed]；Loose 行（堆栈）随其所属 Entry 一起消失。
 * 点击折叠行由父组件调用 onToggleCollapse 展开。
 */
fun applyCollapse(items: List<LogListItem>, collapsedLevels: Set<LogLevel>): List<LogListItem> {
    if (collapsedLevels.isEmpty()) return items
    val result = mutableListOf<LogListItem>()
    var i = 0
    while (i < items.size) {
        val item = items[i]
        if (item is LogListItem.Entry && item.parsed.level in collapsedLevels) {
            val level = item.parsed.level!!
            var count = 0
            // 跳过该等级的连续 Entry（及其后的 Loose 堆栈行）
            while (i < items.size) {
                val cur = items[i]
                when (cur) {
                    is LogListItem.Entry -> {
                        if (cur.parsed.level == level) { count++; i++ } else break
                    }
                    is LogListItem.Loose -> i++ // 堆栈行随父 Entry 折叠
                    is LogListItem.Collapsed -> i++
                }
            }
            result.add(LogListItem.Collapsed(level, count))
        } else {
            result.add(item)
            i++
        }
    }
    return result
}

/** 折叠汇总行：居中显示「N 条 LEVEL 日志已折叠，点击展开」。 */
@Composable
fun CollapsedRow(
    level: LogLevel,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = levelVisual(level).accent
    Text(
        text = "── $count 条 ${level.name} 日志已折叠，点击展开 ──",
        style = MaterialTheme.typography.bodySmall,
        color = accent,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(vertical = PrimitiveSpacing.Sm, horizontal = PrimitiveSpacing.Md),
    )
}

/** 单个日志等级对应的视觉规范（颜色全部来自主题色板，见设计文档 §10.3）。 */
private data class LevelVisual(
    val badgeBg: Color,
    val badgeText: Color,
    val accent: Color,
    val borderWidth: Dp,
    val backgroundTint: Color,
)

@Composable
private fun levelVisual(level: LogLevel): LevelVisual {
    val c = LocalAppTheme.current.colors
    return when (level) {
        LogLevel.ERROR -> LevelVisual(
            badgeBg = c.error, badgeText = c.onError, accent = c.error,
            borderWidth = 4.dp, backgroundTint = c.error.copy(alpha = 0.08f),
        )
        LogLevel.WARN -> LevelVisual(
            badgeBg = c.warning, badgeText = c.onWarning, accent = c.warning,
            borderWidth = 3.dp, backgroundTint = c.warning.copy(alpha = 0.05f),
        )
        LogLevel.INFO -> LevelVisual(
            badgeBg = c.info, badgeText = c.onInfo, accent = c.info,
            borderWidth = 2.dp, backgroundTint = Color.Transparent,
        )
        LogLevel.DEBUG -> LevelVisual(
            badgeBg = c.surfaceSunken, badgeText = c.textSecondary, accent = c.borderStrong,
            borderWidth = 1.dp, backgroundTint = Color.Transparent,
        )
        LogLevel.VERBOSE -> LevelVisual(
            badgeBg = Color.Transparent, badgeText = c.textTertiary, accent = c.borderMuted,
            borderWidth = 0.dp, backgroundTint = Color.Transparent,
        )
        LogLevel.NONE -> LevelVisual(
            badgeBg = c.surfaceSunken, badgeText = c.textTertiary, accent = c.borderMuted,
            borderWidth = 0.dp, backgroundTint = Color.Transparent,
        )
    }
}

/** 从行头原始串中截取 HH:mm:ss.SSS 时间戳（原始格式 `yyyy-MM-dd HH:mm:ss.SSS ...`）。 */
private fun extractTimestamp(raw: String): String =
    if (raw.length >= 23) raw.substring(11, 23) else raw

/** 从行头原始串中提取消息体（`... [TAG] message` 的 message 部分）。 */
private fun extractMessage(raw: String): String = raw.substringAfter("] ", "")

/**
 * 构建带关键词高亮的消息文本：命中项用 [matchStyle] 背景，当前定位项用 [currentMatchStyle]。
 * 颜色由调用方从主题色板取（设计文档 §10.5）。
 */
private fun highlightMessage(
    message: String,
    query: String,
    currentMatchStart: Int,
    matchStyle: SpanStyle,
    currentMatchStyle: SpanStyle,
): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(message)
    return buildAnnotatedString {
        val lower = message.lowercase()
        val q = query.lowercase()
        var start = 0
        while (true) {
            val idx = lower.indexOf(q, start)
            if (idx < 0) {
                append(message.substring(start))
                break
            }
            if (idx > start) append(message.substring(start, idx))
            withStyle(style = if (idx == currentMatchStart) currentMatchStyle else matchStyle) {
                append(message.substring(idx, idx + query.length))
            }
            start = idx + query.length
        }
    }
}

/**
 * 结构化日志行。
 *
 * @param searchQuery 当前搜索词（用于高亮）
 * @param currentMatchCharStart 当前定位命中在消息中的起始下标，-1 表示无定位
 * @param expanded 是否展开长消息
 * @param onToggleExpand 点击行切换展开/收起
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogLineItem(
    entry: LogListItem.Entry,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    currentMatchCharStart: Int = -1,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
) {
    val visual = levelVisual(entry.parsed.level ?: LogLevel.VERBOSE)
    val clipboard = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var stackExpanded by remember { mutableStateOf(false) }

    val timestamp = extractTimestamp(entry.headerRaw)
    val tag = entry.parsed.tag
    val message = extractMessage(entry.headerRaw)

    // 关键词高亮：普通命中用 warningContainer，当前定位用 orange（均来自主题色板）
    val highlightColors = LocalAppTheme.current.colors
    val matchStyle = SpanStyle(background = highlightColors.warningContainer)
    val currentMatchStyle = SpanStyle(
        background = highlightColors.orange,
        color = highlightColors.onOrange,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(visual.backgroundTint)
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = { menuExpanded = true },
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 等级左边框（VERBOSE 为 0dp 不绘制）
            if (visual.borderWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .width(visual.borderWidth)
                        .background(visual.accent)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = PrimitiveSpacing.Sm, top = PrimitiveSpacing.Xs, bottom = PrimitiveSpacing.Xs)
            ) {
                // ── 行头：时间戳 ┃ 等级徽章 Tag ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = visual.accent,
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.Xxs))
                    LevelBadge(level = entry.parsed.level ?: LogLevel.VERBOSE, visual = visual)
                    Spacer(Modifier.width(PrimitiveSpacing.Xxs))
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp),
                    )
                }

                // ── 消息体（关键词高亮）──
                Text(
                    text = highlightMessage(
                        message,
                        searchQuery.trim(),
                        currentMatchCharStart,
                        matchStyle,
                        currentMatchStyle,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = PrimitiveSpacing.Xxs),
                )

                // ── 堆栈附属行（默认折叠）──
                if (entry.stack.isNotEmpty()) {
                    Spacer(Modifier.height(PrimitiveSpacing.Xxs))
                    if (stackExpanded) {
                        entry.stack.forEach { frame ->
                            Text(
                                text = frame,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = PrimitiveSpacing.Lg),
                            )
                        }
                    } else {
                        Text(
                            text = "+ ${entry.stack.size} 行堆栈",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.accent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = PrimitiveSpacing.Lg)
                                .combinedClickable(
                                    onClick = { stackExpanded = true },
                                    onLongClick = { menuExpanded = true },
                                ),
                        )
                    }
                }
            }
        }

        // 长按复制菜单
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("复制全文") },
                onClick = {
                    clipboard.setText(AnnotatedString(entry.headerRaw + if (entry.stack.isEmpty()) "" else "\n" + entry.stack.joinToString("\n")))
                    menuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text("复制消息") },
                onClick = { clipboard.setText(AnnotatedString(message)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("复制时间戳") },
                onClick = { clipboard.setText(AnnotatedString(timestamp)); menuExpanded = false },
            )
            DropdownMenuItem(
                text = { Text("复制 Tag") },
                onClick = { clipboard.setText(AnnotatedString(tag)); menuExpanded = false },
            )
        }
    }
}

/** 等级徽章：彩色圆角背景 + 白/深色文字。 */
@Composable
private fun LevelBadge(level: LogLevel, visual: LevelVisual) {
    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.xs),
        color = visual.badgeBg,
    ) {
        Text(
            text = level.name,
            style = MaterialTheme.typography.labelSmall,
            color = visual.badgeText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.width(60.dp).padding(horizontal = PrimitiveSpacing.Xxs, vertical = PrimitiveSpacing.None),
        )
    }
}

/** 游离附属行（无行头）：灰色缩进等宽渲染。 */
@Composable
fun LooseLogLine(line: String, modifier: Modifier = Modifier) {
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PrimitiveSpacing.Lg, top = PrimitiveSpacing.Xxs, bottom = PrimitiveSpacing.Xxs),
    )
}

// ──────────────────────────────────────────────
// Previews
// ──────────────────────────────────────────────

@Composable
private fun PreviewWrapper(content: @Composable () -> Unit) {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.sm)
        ) { content() }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun LogLineItemPreview() {
    PreviewWrapper {
        val lines = listOf(
            "2026-09-23 09:23:45.123 ERROR [McpManager] Connection refused at port 5432, retrying in 5s",
            "java.net.ConnectException: Connection refused",
            "\tat java.net.PlainSocketImpl.socketConnect(Native Method)",
            "\tat java.net.AbstractPlainSocketImpl.doConnect(AbstractPlainSocketImpl.java:350)",
            "2026-09-23 09:23:46.001 WARN [AIAgent] Retry attempt 2/3, waiting 5s before next retry",
            "2026-09-23 09:23:46.200 INFO [Container] Container started successfully, pid=12844",
            "2026-09-23 09:23:46.300 DEBUG [ToolRegistry] Registered 24 tools",
            "2026-09-23 09:23:46.400 VERBOSE [FileLogger] Flushed 128 lines to log-2026-09-23.txt",
        )
        buildLogEntries(lines).forEach { item ->
            when (item) {
                is LogListItem.Entry -> LogLineItem(entry = item, searchQuery = "retry")
                is LogListItem.Loose -> LooseLogLine(line = item.line)
                is LogListItem.Collapsed -> CollapsedRow(level = item.level, count = item.count, onClick = {})
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 200)
@Composable
private fun LogLineItemExpandedPreview() {
    PreviewWrapper {
        val lines = listOf(
            "2026-09-23 09:23:45.123 ERROR [McpManager] Connection refused at port 5432",
            "java.net.ConnectException: Connection refused",
            "\tat java.net.PlainSocketImpl.socketConnect(Native Method)",
        )
        val entry = buildLogEntries(lines).filterIsInstance<LogListItem.Entry>().first()
        LogLineItem(entry = entry, expanded = true)
    }
}
