package com.mini.logs.ui.logs

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.HighlightColor
import com.mini.logs.data.LogEntry
import com.mini.logs.data.ViewMode
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.theme.tokens.SemanticColors

/**
 * 单条日志行。
 *
 * @param entry 日志条目
 * @param viewMode 紧凑/舒适
 * @param isExpanded 是否展开
 * @param hasStacktrace 是否有后续堆栈行
 * @param stackTraceCount 堆栈行数
 * @param searchQuery 当前搜索关键词（用于高亮）
 * @param isSearchMatch 当前行是否是搜索匹配行
 * @param inContextWindow 是否处于上下文查看窗口（灰色背景）
 * @param onClick 点击行
 * @param onLongClick 长按行
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogListItem(
    entry: LogEntry,
    viewMode: ViewMode,
    isExpanded: Boolean,
    hasStacktrace: Boolean,
    stackTraceCount: Int,
    searchQuery: String,
    isSearchMatch: Boolean,
    inContextWindow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val levelColor = LogLevelColors.colorFor(entry.level)

    // 高亮颜色竖条
    val highlightBarColor: Color? = if (entry.isHighlighted) {
        HighlightColor.fromIndex(entry.highlightColor)?.let { Color(it.argb) }
    } else null

    // 行背景
    val rowBackground = when {
        inContextWindow -> colors.surfaceSunken
        isSearchMatch && searchQuery.isNotEmpty() -> colors.brandPrimary.copy(alpha = 0.08f)
        entry.isHighlighted -> (HighlightColor.fromIndex(entry.highlightColor)?.let {
            Color(it.argb).copy(alpha = 0.12f)
        } ?: colors.surfaceCard)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PrimitiveSpacing.SmPlus),
    ) {
        // 左侧高亮竖条
        if (highlightBarColor != null) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (viewMode == ViewMode.COMFORTABLE) 48.dp else 24.dp)
                    .background(highlightBarColor, RoundedCornerShape(0.dp)),
            )
            Spacer(Modifier.width(PrimitiveSpacing.Xs))
        }

        if (viewMode == ViewMode.COMFORTABLE) {
            ComfortableRow(
                entry = entry,
                isExpanded = isExpanded,
                hasStacktrace = hasStacktrace,
                stackTraceCount = stackTraceCount,
                searchQuery = searchQuery,
                levelColor = levelColor,
                modifier = Modifier.weight(1f),
            )
        } else {
            CompactRow(
                entry = entry,
                isExpanded = isExpanded,
                searchQuery = searchQuery,
                levelColor = levelColor,
                modifier = Modifier.weight(1f),
            )
        }

        // 书签图标
        if (entry.bookmarkNote != null) {
            Spacer(Modifier.width(PrimitiveSpacing.Xs))
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = "书签",
                tint = colors.warning,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 舒适模式：两行布局。
 */
@Composable
private fun ComfortableRow(
    entry: LogEntry,
    isExpanded: Boolean,
    hasStacktrace: Boolean,
    stackTraceCount: Int,
    searchQuery: String,
    levelColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = modifier.padding(vertical = PrimitiveSpacing.Xs),
    ) {
        // 行头
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.time,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            Box(
                modifier = Modifier
                    .background(levelColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                    .padding(horizontal = PrimitiveSpacing.Xs, vertical = 1.dp),
            ) {
                Text(
                    text = entry.levelLetter,
                    color = levelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            Text(
                text = entry.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = colors.brandPrimary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(PrimitiveSpacing.Xs))

        // 消息
        Text(
            text = highlightText(entry.message, searchQuery, colors),
            fontSize = 13.sp,
            color = colors.textPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            lineHeight = 18.sp,
        )

        // 堆栈折叠提示
        if (hasStacktrace && !isExpanded && stackTraceCount > 0) {
            Spacer(Modifier.height(PrimitiveSpacing.Xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(PrimitiveSpacing.Xxs))
                Text(
                    text = "+$stackTraceCount 行堆栈",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * 紧凑模式：单行布局。
 */
@Composable
private fun CompactRow(
    entry: LogEntry,
    isExpanded: Boolean,
    searchQuery: String,
    levelColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    Row(
        modifier = modifier.padding(vertical = PrimitiveSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.time,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = entry.levelLetter,
            color = levelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = entry.tag,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = colors.brandPrimary,
            maxLines = 1,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = highlightText(entry.message, searchQuery, colors),
            fontSize = 12.sp,
            color = colors.textPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 在文本中高亮搜索关键词。
 */
@Composable
private fun highlightText(
    text: String,
    query: String,
    colors: SemanticColors,
): AnnotatedString {
    if (query.isBlank() || text.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        var start = 0
        while (start < text.length) {
            val idx = lowerText.indexOf(lowerQuery, start)
            if (idx < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, idx))
            withStyle(SpanStyle(
                background = colors.brandPrimary.copy(alpha = 0.25f),
                color = colors.onBrandContainer,
                fontWeight = FontWeight.Bold,
            )) {
                append(text.substring(idx, idx + query.length))
            }
            start = idx + query.length
        }
    }
}
