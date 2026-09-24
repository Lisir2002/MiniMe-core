package com.mini.logs.ui.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.CrashGroup
import com.mini.logs.data.CrashKind
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.tokens.LocalAppTheme

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * 崩溃聚合卡片。左侧 4dp 竖条指示状态（未修复 error / 已修复 success）。
 * 根据 crashKind 显示不同图标。支持搜索关键词高亮、趋势徽标、忽略操作。
 */
@Composable
fun CrashCard(
    crash: CrashGroup,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onToggleFixed: () -> Unit,
    onIgnore: () -> Unit = {},
    searchQuery: String? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    val colors = LocalAppTheme.current.colors

    val statusColor = if (crash.isFixed) colors.success else colors.error

    val kindIcon = when (crash.crashKind) {
        CrashKind.ANR -> Icons.Rounded.WatchLater
        CrashKind.NATIVE -> Icons.Rounded.Memory
        CrashKind.JAVA_EXCEPTION -> Icons.Rounded.BugReport
    }
    val leadingIcon = if (crash.isFixed) Icons.Rounded.CheckCircle else kindIcon

    // 趋势：最近 24h vs 之前 24h
    val trend = remember(crash.key, crash.occurrences) {
        if (crash.occurrences < 3) return@remember null
        val now = System.currentTimeMillis()
        val recent = crash.entries.count { it.timestamp in (now - DAY_MS) until now }
        val previous = crash.entries.count { it.timestamp in (now - 2 * DAY_MS) until (now - DAY_MS) }
        when {
            recent > previous -> "up"
            recent < previous -> "down"
            else -> "flat"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (crash.isFixed) 0.6f else 1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        // 左侧状态竖条
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(64.dp)
                .background(statusColor),
        )

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
            variant = AppCardVariant.Default,
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.height(18.dp).width(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = crash.exceptionType,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (crash.isFixed) colors.textSecondary else colors.error,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    // 多选模式下显示 Checkbox
                    if (selectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onLongPress() },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                // 副标题行：次数 + 时间 + Tag + 趋势徽标
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "发生 ${crash.occurrences} 次 · 最近: ${FormatUtils.formatRelativeTime(crash.lastOccurrence)}" +
                            " · Tag: ${crash.tags.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    trend?.let { t ->
                        Spacer(Modifier.width(Spacing.sm))
                        val (arrow, label, color) = when (t) {
                            "up" -> Triple("↑", "恶化", colors.error)
                            "down" -> Triple("↓", "好转", colors.success)
                            else -> Triple("→", "持平", colors.textTertiary)
                        }
                        Text(
                            text = "$arrow$label",
                            fontSize = 11.sp,
                            color = color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                // 消息文本，搜索关键词黄色高亮
                HighlightedText(
                    text = crash.message.ifBlank { crash.stackTraceFirstLine },
                    query = searchQuery,
                    highlightColor = Color(0xFFFFF59D),
                    baseColor = colors.textPrimary,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    AppButton(
                        text = "详情",
                        onClick = onClick,
                        variant = AppButtonVariant.Filled,
                        buttonColor = AppButtonColor.Primary,
                        size = AppButtonSize.Small,
                    )
                    AppButton(
                        text = "复制",
                        onClick = onCopy,
                        variant = AppButtonVariant.Text,
                        size = AppButtonSize.Small,
                    )
                    AppButton(
                        text = if (crash.isFixed) "取消修复" else "标记已修复",
                        onClick = onToggleFixed,
                        variant = AppButtonVariant.Text,
                        buttonColor = if (crash.isFixed) AppButtonColor.Neutral else AppButtonColor.Success,
                        size = AppButtonSize.Small,
                    )
                    AppButton(
                        text = "忽略",
                        onClick = onIgnore,
                        variant = AppButtonVariant.Text,
                        buttonColor = AppButtonColor.Error,
                        size = AppButtonSize.Small,
                    )
                }
            }
        }
    }
}

/** 在文本中高亮 query 匹配段（黄色背景）。 */
@Composable
private fun HighlightedText(
    text: String,
    query: String?,
    highlightColor: Color,
    baseColor: Color,
) {
    val annotated: AnnotatedString = remember(text, query) {
        if (query.isNullOrBlank()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var idx = 0
                val lower = text.lowercase()
                val q = query.lowercase()
                while (idx < text.length) {
                    val hit = lower.indexOf(q, idx)
                    if (hit < 0) {
                        append(text.substring(idx))
                        break
                    }
                    if (hit > idx) append(text.substring(idx, hit))
                    pushStyle(SpanStyle(background = highlightColor, color = baseColor))
                    append(text.substring(hit, hit + q.length))
                    pop()
                    idx = hit + q.length
                }
            }
        }
    }
    Text(
        text = annotated,
        fontSize = 13.sp,
        color = baseColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
