@file:Suppress("FunctionNaming", "LongMethod") // Compose @Composable PascalCase 命名约定；长 Composable 逐字搬迁，行为保持
package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.tokens.IconSizeTokens
import com.mini.me_core.R
import com.mini.me_core.core.theme.LocalAnimationScale
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.datalayer.toChatSession
import com.mini.me_core.feature.agent.domain.core.model.ChatSession
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount as V2SessionWithCount
import androidx.compose.ui.res.stringResource

/**
 * 会话行长按弹出的功能菜单：重命名 / 删除。底部 sheet 样式参照 git 分支的 RefActionSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionActionSheet(
    session: ChatSession,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            SheetActionRow(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.common_rename),
                tint = MaterialTheme.colorScheme.onSurface,
                iconBgLight = Color(0xFF4C8DFF),
                iconBgDark = Color(0xFF2B4E9E),
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            SheetActionRow(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.chat_export_session),
                tint = MaterialTheme.colorScheme.onSurface,
                iconBgLight = Color(0xFF22C55E),
                iconBgDark = Color(0xFF14693A),
                onClick = {
                    onDismiss()
                    onExport()
                }
            )
            SheetActionRow(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.primary,
                iconBgLight = Color(0xFF4C8DFF),
                iconBgDark = Color(0xFF2B4E9E),
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconBgLight: Color,
    iconBgDark: Color,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                    .background(if (LocalAppDarkMode.current) iconBgDark else iconBgLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizeTokens.medium),
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}

/**
 * 侧边栏顶部 tab 项：文字标签 + 底部选中指示条，高 44dp 与全局标题栏一致。
 * 选中态以主色文字 + 圆角指示条表达（替代 Material3 TabRow 的 48dp 默认高度）。
 */
@Composable
internal fun DrawerTopTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.map(2.dp)))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            )
        }
    }
}

/** 对话列表的扁平条目：分组头 或 会话行（A3 四档吸顶分组用）。 */
private sealed interface SessionListEntry {
    data class Header(val bucket: SessionBucket, val count: Int) : SessionListEntry
    data class Row(val session: V2SessionWithCount) : SessionListEntry
}

/**
 * 按 updatedAtMs 将会话分档（今天/昨天/7天内/更早），并按档序（今天→昨天→7天内→更早）
 * 展平为 [SessionListEntry] 列表。组内保持输入顺序（数据源已按更新时间降序）。
 */
private fun buildSessionEntries(
    sessions: List<V2SessionWithCount>,
    nowMs: Long
): List<SessionListEntry> {
    val grouped = sessions.groupBy { sessionBucket(it.updated_at, nowMs) }
    val order = listOf(
        SessionBucket.TODAY,
        SessionBucket.YESTERDAY,
        SessionBucket.WITHIN_7D,
        SessionBucket.EARLIER
    )
    return buildList {
        for (bucket in order) {
            val list = grouped[bucket].orEmpty()
            if (list.isNotEmpty()) {
                add(SessionListEntry.Header(bucket, list.size))
                list.forEach { add(SessionListEntry.Row(it)) }
            }
        }
    }
}

/** 分组头：分档名 + 右侧会话计数，作为 LazyColumn stickyHeader 吸顶。 */
@Composable
private fun SessionGroupHeader(bucket: SessionBucket, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 吸顶时背景需不透明（surface），避免下方行内容透出
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (bucket) {
                SessionBucket.TODAY -> stringResource(R.string.chat_session_today)
                SessionBucket.YESTERDAY -> stringResource(R.string.chat_session_yesterday)
                SessionBucket.WITHIN_7D -> stringResource(R.string.chat_session_within_7d)
                SessionBucket.EARLIER -> stringResource(R.string.chat_session_earlier)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 左滑露出操作按钮的锚点状态：收起 / 展开。 */
private enum class SwipeRevealState { Collapsed, Expanded }

/**
 * 左滑露出操作按钮的会话行：基于 AnchoredDraggable 实现（standard swipe-to-reveal）。
 *
 * - 底层：右侧两个操作按钮（重命名=primary / 删除=error），固定不动。
 * - 前景：[ChatSessionRow] 整体左移时露出底层按钮；前景带不透明 surface 背景，
 *   收起时完全遮盖按钮（修复旧版 SwipeToDismissBox 背景透过透明行底泄露的 bug）。
 *
 * [expanded] 由父级通过 expandedSessionId 统一管理；展开新行时旧行自动收起。
 */
@Suppress("LongParameterList") // 签名保持
@Composable
private fun SwipeableSessionRow(
    session: V2SessionWithCount,
    selected: Boolean,
    isExecuting: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDirectDelete: () -> Unit,
    onSwipeRename: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val density = LocalDensity.current
    // 两个操作按钮各 72dp 宽
    val actionWidthPx = with(density) { 72.dp.toPx() * 2 }
    val velocityThresholdPx = with(density) { 100.dp.toPx() }

    val animScale = LocalAnimationScale.current
    val snapSpec: androidx.compose.animation.core.AnimationSpec<Float> = if (animScale <= 0f) {
        tween(durationMillis = 0)
    } else {
        tween(durationMillis = (220 * animScale).toInt())
    }

    val dragState = remember(expanded, actionWidthPx, snapSpec, density) {
        AnchoredDraggableState<SwipeRevealState>(
            initialValue = if (expanded) SwipeRevealState.Expanded else SwipeRevealState.Collapsed,
            anchors = DraggableAnchors {
                SwipeRevealState.Collapsed at 0f
                SwipeRevealState.Expanded at -actionWidthPx
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { velocityThresholdPx },
            snapAnimationSpec = snapSpec,
            decayAnimationSpec = splineBasedDecay(density),
            confirmValueChange = { true }
        )
    }

    // 父级外部状态变化（展开新行→本行收起；点击行区域→收起）时动画同步
    LaunchedEffect(expanded) {
        val target = if (expanded) SwipeRevealState.Expanded else SwipeRevealState.Collapsed
        if (dragState.currentValue != target || dragState.settledValue != target) {
            dragState.animateTo(target)
        }
    }

    // 手势松手吸附后通知父级更新 expandedSessionId
    LaunchedEffect(dragState.settledValue) {
        onExpandedChange(dragState.settledValue == SwipeRevealState.Expanded)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // ── 底层操作按钮（右侧固定）──
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
            ) {
                // 重命名按钮（蓝）
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(
                                topStart = LocalCornerRadius.current.md,
                                bottomStart = LocalCornerRadius.current.md,
                                topEnd = 2.dp,
                                bottomEnd = 2.dp
                            )
                        )
                        .background(Color(0xFF3B82F6))
                        .clickable {
                            onSwipeRename()
                            onExpandedChange(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.common_rename),
                            tint = Color.White,
                            modifier = Modifier.size(IconSizeTokens.medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "重命名",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.width(2.dp))
                // 删除按钮（红，最右侧）
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(
                                topStart = 2.dp,
                                bottomStart = 2.dp,
                                topEnd = LocalCornerRadius.current.md,
                                bottomEnd = LocalCornerRadius.current.md
                            )
                        )
                        .background(Color(0xFFEF4444))
                        .clickable {
                            onDirectDelete()
                            onExpandedChange(false)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color.White,
                            modifier = Modifier.size(IconSizeTokens.medium)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // ── 前景行内容（整体左移滑动）──
            Box(
                modifier = Modifier
                    .offset { IntOffset(dragState.requireOffset().toInt(), 0) }
                    .anchoredDraggable(
                        state = dragState,
                        orientation = Orientation.Horizontal
                    )
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ChatSessionRow(
                    session = session,
                    selected = selected,
                    isExecuting = isExecuting,
                    onClick = {
                        // 展开状态下点击行内容 → 收起，不触发选中
                        if (dragState.settledValue == SwipeRevealState.Expanded) {
                            onExpandedChange(false)
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = onLongClick
                )
            }
        }
    }
}

/**
 * 侧边栏「对话列表」tab：中部历史记录列表（新建会话入口在聊天页顶栏）。
 * 四档日期分组（今天/昨天/7天内/更早）吸顶；列表项两行增强；支持左滑露出操作按钮。
 * 每次进入该 tab 会自动滚动到当前会话（按分组后的全局下标换算）。
 *
 * [expandedSessionId] 统一管理哪一行处于展开状态（rememberSaveable 防回收错乱），
 * 同一时刻仅一行展开；展开新行时旧行自动收起。
 */
@Suppress("LongParameterList") // 签名保持
@Composable
internal fun ChatSessionListPanel(
    sessions: List<V2SessionWithCount>,
    currentSessionId: String?,
    agentStates: Map<String, AgentUIState>,
    onSelect: (ChatSession) -> Unit,
    onLongPress: (ChatSession) -> Unit,
    onDirectDelete: (V2SessionWithCount) -> Unit,
    onSwipeRename: (V2SessionWithCount) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val entries = remember(sessions) { buildSessionEntries(sessions, System.currentTimeMillis()) }
    var expandedSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentSessionId, entries) {
        if (entries.isEmpty()) return@LaunchedEffect
        val index = entries.indexOfFirst {
            it is SessionListEntry.Row && it.session.id == currentSessionId
        }
        listState.scrollToItem(if (index >= 0) index else 0)
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.chat_history),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.chat_no_sessions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    entries.forEach { entry ->
                        when (entry) {
                            is SessionListEntry.Header -> stickyHeader(key = "header_${entry.bucket}") {
                                SessionGroupHeader(bucket = entry.bucket, count = entry.count)
                            }
                            is SessionListEntry.Row -> item(key = entry.session.id) {
                                val state = agentStates[entry.session.id]
                                val isExecuting =
                                    state is AgentUIState.Loading || state is AgentUIState.Streaming
                                SwipeableSessionRow(
                                    session = entry.session,
                                    selected = entry.session.id == currentSessionId,
                                    isExecuting = isExecuting,
                                    expanded = expandedSessionId == entry.session.id,
                                    onExpandedChange = { expanded ->
                                        expandedSessionId = if (expanded) entry.session.id else null
                                    },
                                    onDirectDelete = { onDirectDelete(entry.session) },
                                    onSwipeRename = { onSwipeRename(entry.session) },
                                    onClick = { onSelect(entry.session.toChatSession()) },
                                    onLongClick = { onLongPress(entry.session.toChatSession()) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
