package com.mini.logs.ui.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.mini.logs.data.FontSize
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
 * @param fontSize 消息字号档位
 * @param useMonospace 消息体是否等宽字体
 * @param showMilliseconds 时间是否显示毫秒
 * @param onClick 点击行
 * @param onLongClick 长按行
 * @param onDoubleClick 双击行（默认空，便于其他调用处兼容）
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
    onDoubleClick: () -> Unit = {},
    fontSize: FontSize = FontSize.MEDIUM,
    useMonospace: Boolean = false,
    showMilliseconds: Boolean = true,
) {
    val colors = LocalAppTheme.current.colors
    val levelColor = LogLevelColors.colorFor(entry.level)

    // 字号：消息体按档位，行头比消息小 2sp
    val messageSp = when (fontSize) {
        FontSize.SMALL -> 11.sp
        FontSize.MEDIUM -> 13.sp
        FontSize.LARGE -> 15.sp
        FontSize.XLARGE -> 17.sp
    }
    val headerSp = when (fontSize) {
        FontSize.SMALL -> 9.sp
        FontSize.MEDIUM -> 11.sp
        FontSize.LARGE -> 13.sp
        FontSize.XLARGE -> 15.sp
    }
    // 消息体字体族
    val messageFontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default
    // 时间显示：关闭毫秒时截取到秒
    val displayTime = if (showMilliseconds) entry.time else entry.time.substringBefore(".")

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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            )
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
                displayTime = displayTime,
                messageSp = messageSp,
                headerSp = headerSp,
                messageFontFamily = messageFontFamily,
                modifier = Modifier.weight(1f),
            )
        } else {
            CompactRow(
                entry = entry,
                isExpanded = isExpanded,
                searchQuery = searchQuery,
                levelColor = levelColor,
                displayTime = displayTime,
                messageSp = messageSp,
                headerSp = headerSp,
                messageFontFamily = messageFontFamily,
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
    displayTime: String,
    messageSp: androidx.compose.ui.unit.TextUnit,
    headerSp: androidx.compose.ui.unit.TextUnit,
    messageFontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    Column(
        modifier = modifier.padding(vertical = PrimitiveSpacing.Xs),
    ) {
        // 行头
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = displayTime,
                fontFamily = FontFamily.Monospace,
                fontSize = headerSp,
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
                    fontSize = headerSp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            Text(
                text = entry.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = headerSp,
                color = colors.brandPrimary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(PrimitiveSpacing.Xs))

        // 消息
        Text(
            text = highlightText(entry.message, searchQuery, colors),
            fontSize = messageSp,
            fontFamily = messageFontFamily,
            color = colors.textPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            lineHeight = messageSp * 1.38f,
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
                    fontSize = headerSp,
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
    displayTime: String,
    messageSp: androidx.compose.ui.unit.TextUnit,
    headerSp: androidx.compose.ui.unit.TextUnit,
    messageFontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors

    Row(
        modifier = modifier.padding(vertical = PrimitiveSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayTime,
            fontFamily = FontFamily.Monospace,
            fontSize = headerSp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = entry.levelLetter,
            color = levelColor,
            fontSize = headerSp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = entry.tag,
            fontFamily = FontFamily.Monospace,
            fontSize = headerSp,
            color = colors.brandPrimary,
            maxLines = 1,
            modifier = Modifier.width(80.dp),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Sm))
        Text(
            text = highlightText(entry.message, searchQuery, colors),
            fontSize = messageSp,
            fontFamily = messageFontFamily,
            color = colors.textPrimary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 1,
            lineHeight = messageSp * 1.33f,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
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
