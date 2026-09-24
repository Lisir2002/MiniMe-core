package com.mini.logs.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.HighlightColor
import com.mini.logs.data.LogAction
import com.mini.logs.data.LogEntry
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.theme.tokens.SemanticColors

/**
 * 日志长按操作 BottomSheet。
 *
 * 从底部弹出，按「复制 / 过滤 / 搜索 / 标记 / 分享与导航」分组，
 * 每行左侧 24dp Rounded 图标 + 标题 + 可选副标题；高亮颜色内联展开。
 *
 * @param entry 被长按的日志行
 * @param onDismiss 关闭 sheet
 * @param onAction 用户选择的操作；Bookmark(note=null) 由 UI 层拦截弹输入框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogActionSheet(
    entry: LogEntry,
    onDismiss: () -> Unit,
    onAction: (LogAction) -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    // 高亮选择区是否展开；已高亮行默认展开方便换色
    var highlightExpanded by remember(entry) { mutableStateOf(entry.isHighlighted) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PrimitiveSpacing.Md),
        ) {
            // 行摘要头
            Text(
                text = buildString {
                    if (entry.time.isNotBlank()) append(entry.time)
                    if (entry.levelLetter.isNotBlank()) {
                        if (isNotEmpty()) append("  ")
                        append(entry.levelLetter)
                    }
                    if (entry.tag.isNotBlank()) {
                        if (isNotEmpty()) append("  ")
                        append(entry.tag)
                    }
                }.trim(),
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = PrimitiveSpacing.Xs),
            )

            // ── 复制 ──
            GroupTitle("复制", colors)
            SheetRow(
                icon = Icons.Rounded.ContentCopy,
                title = "复制原始行",
                colors = colors,
            ) { onAction(LogAction.CopyRaw); onDismiss() }
            SheetRow(
                icon = if (entry.isStackTraceLine) Icons.Rounded.BugReport else Icons.Rounded.Message,
                title = if (entry.isStackTraceLine) "复制堆栈" else "复制消息",
                colors = colors,
            ) {
                if (entry.isStackTraceLine) {
                    onAction(LogAction.CopyAs(LogAction.CopyAs.CopyField.STACKTRACE))
                } else {
                    onAction(LogAction.CopyMessage)
                }
                onDismiss()
            }
            SheetRow(
                icon = Icons.Rounded.Schedule,
                title = "复制时间",
                subtitle = entry.time.takeIf { it.isNotBlank() },
                colors = colors,
            ) { onAction(LogAction.CopyAs(LogAction.CopyAs.CopyField.TIMESTAMP)); onDismiss() }
            if (entry.tag.isNotBlank()) {
                SheetRow(
                    icon = Icons.Rounded.Label,
                    title = "复制 Tag",
                    subtitle = entry.tag,
                    colors = colors,
                ) { onAction(LogAction.CopyAs(LogAction.CopyAs.CopyField.TAG)); onDismiss() }
            }
            if (entry.level != null) {
                SheetRow(
                    icon = Icons.Rounded.Info,
                    title = "复制等级",
                    subtitle = entry.level.name,
                    colors = colors,
                ) { onAction(LogAction.CopyAs(LogAction.CopyAs.CopyField.LEVEL)); onDismiss() }
            }

            // ── 过滤 ──
            if (entry.tag.isNotBlank() || entry.level != null) {
                Spacer(Modifier.height(PrimitiveSpacing.Sm))
                GroupTitle("过滤", colors)
                if (entry.tag.isNotBlank()) {
                    SheetRow(
                        icon = Icons.Rounded.FilterList,
                        title = "只显示此 Tag",
                        subtitle = entry.tag,
                        colors = colors,
                    ) { onAction(LogAction.FilterTagOnly(entry.tag)); onDismiss() }
                    SheetRow(
                        icon = Icons.Rounded.Block,
                        title = "隐藏此 Tag",
                        subtitle = entry.tag,
                        colors = colors,
                    ) { onAction(LogAction.FilterTagHide(entry.tag)); onDismiss() }
                }
                if (entry.level != null) {
                    SheetRow(
                        icon = Icons.Rounded.FilterAlt,
                        title = "只显示此等级",
                        subtitle = entry.level.name,
                        colors = colors,
                    ) { onAction(LogAction.FilterLevelOnly(entry.level)); onDismiss() }
                }
                SheetRow(
                    icon = Icons.Rounded.FilterList,
                    title = "相似消息筛选",
                    subtitle = entry.message.take(24).takeIf { it.isNotBlank() },
                    colors = colors,
                ) { onAction(LogAction.FilterSimilar(entry.message)); onDismiss() }
            }

            // ── 搜索 ──
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            GroupTitle("搜索", colors)
            SheetRow(
                icon = Icons.Rounded.Search,
                title = "用消息搜索",
                subtitle = entry.message.take(24).takeIf { it.isNotBlank() },
                colors = colors,
            ) { onAction(LogAction.SearchWith(entry.message)); onDismiss() }

            // ── 标记 ──
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            GroupTitle("标记", colors)

            // 高亮开关行：点击展开/收起颜色选择
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { highlightExpanded = !highlightExpanded }
                    .padding(vertical = PrimitiveSpacing.Sm, horizontal = PrimitiveSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Colorize,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(PrimitiveSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (entry.isHighlighted) "更换高亮颜色" else "高亮标记",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                    if (entry.isHighlighted) {
                        Text(
                            text = "已标记",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }
                }
                Icon(
                    if (highlightExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // 内联颜色选择区
            if (highlightExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, bottom = PrimitiveSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                ) {
                    HighlightColor.entries.forEach { hc ->
                        val isCurrent = entry.isHighlighted && entry.highlightColor == hc.index
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(hc.argb), CircleShape)
                                .clickable {
                                    onAction(LogAction.Highlight(hc.index))
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF222222),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        onAction(LogAction.Highlight(-1))
                        onDismiss()
                    }) {
                        Text("清除", color = colors.error)
                    }
                }
            }

            // 书签
            SheetRow(
                icon = if (entry.bookmarkNote != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                title = if (entry.bookmarkNote != null) "编辑书签" else "添加书签",
                subtitle = entry.bookmarkNote,
                colors = colors,
            ) { onAction(LogAction.Bookmark(null)); onDismiss() }

            // 清除所有标记（仅在有标记时显示，破坏性红色）
            if (entry.isHighlighted || entry.bookmarkNote != null) {
                SheetRow(
                    icon = Icons.Rounded.Delete,
                    title = "清除所有标记",
                    destructive = true,
                    colors = colors,
                ) { onAction(LogAction.ClearMarks); onDismiss() }
            }

            // ── 分享与导航 ──
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            GroupTitle("分享与导航", colors)
            SheetRow(
                icon = Icons.Rounded.Share,
                title = "分享此行",
                colors = colors,
            ) { onAction(LogAction.ShareLine); onDismiss() }
            SheetRow(
                icon = Icons.Rounded.IosShare,
                title = "分享上下文（±5 行）",
                colors = colors,
            ) { onAction(LogAction.ShareContext); onDismiss() }
            SheetRow(
                icon = Icons.Rounded.Visibility,
                title = "查看前后上下文",
                colors = colors,
            ) { onAction(LogAction.ViewContext); onDismiss() }

            // ── 取消 ──
            Spacer(Modifier.height(PrimitiveSpacing.Md))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("取消") }
            Spacer(Modifier.height(PrimitiveSpacing.Md))
        }
    }
}

/** 分组小标题。 */
@Composable
private fun GroupTitle(text: String, colors: SemanticColors) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textTertiary,
        modifier = Modifier.padding(
            start = PrimitiveSpacing.Xs,
            top = PrimitiveSpacing.Sm,
            bottom = PrimitiveSpacing.Xs,
        ),
    )
}

/** 一行菜单项：图标 + 标题 + 可选副标题。 */
@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    colors: SemanticColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = PrimitiveSpacing.Sm, horizontal = PrimitiveSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) colors.error else colors.textSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (destructive) colors.error else colors.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
