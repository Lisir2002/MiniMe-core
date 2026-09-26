package com.mini.me_core.feature.settings.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.components.AppStatusDot
import com.mini.me_core.core.theme.components.AppStatusDotColor
import com.mini.me_core.feature.workspace.data.local.entity.RemoteAuditLogEntity
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 操作审计页面（MiniMe）。
 *
 * 按 UI 规范 v1.0 迁移：
 * - 不创建内层顶栏/操作行；搜索与溢出菜单由外层 SettingsScreen 顶栏 actions 提供。
 * - 顶栏下方固定分类 Tab（[AppSegmentedControl]，横向滚动）。
 * - 内容头（统计卡片 2x2）随内容滚动。
 * - 日志按日期分组，支持下拉刷新、触底分页、卡片点击展开详情。
 */
@Composable
fun RemoteAuditLogsScreen(
    viewModel: AuditLogsViewModel = hiltViewModel(),
    searchActive: Boolean = false,
    searchQuery: String = "",
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    val english = LocalConfiguration.current.locales[0]?.language == "en"
    val listState = rememberLazyListState()

    // 顶栏内联搜索状态由外层 SettingsScreen 持有，这里同步进 ViewModel（跳过首次初始化，避免与 init refresh 重复）。
    var searchSynced by remember { mutableStateOf(false) }
    LaunchedEffect(searchActive, searchQuery) {
        if (!searchSynced) {
            searchSynced = true
            return@LaunchedEffect
        }
        viewModel.applySearch(searchActive, searchQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuditEvent.Refreshed -> snackbarHost.showSnackbar(context.getString(R.string.audit_refreshed))
                AuditEvent.ExportDone -> Toast.makeText(context, R.string.audit_export_done, Toast.LENGTH_SHORT).show()
                AuditEvent.ExportFailed -> Toast.makeText(context, R.string.audit_export_failed, Toast.LENGTH_SHORT).show()
                AuditEvent.Cleared -> Unit
            }
        }
    }

    // 触底自动加载下一页（搜索模式不分页）。
    LaunchedEffect(listState, ui.endReached, searchActive, ui.loadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastIndex ->
                if (!ui.endReached && !searchActive && !ui.loading && !ui.loadingMore && lastIndex >= ui.logs.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    val tabLabels = listOf(
        stringResource(R.string.audit_tab_all),
        stringResource(R.string.audit_tab_connect),
        stringResource(R.string.audit_tab_credential),
        stringResource(R.string.audit_tab_backup),
        stringResource(R.string.audit_tab_security),
        stringResource(R.string.audit_tab_skill),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
    ) {
        // ── 副标题：当前筛选分类 · 总数 ──
        Text(
            text = stringResource(
                R.string.audit_subtitle_count,
                tabLabels[ui.selectedTab],
                ui.stats.total,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
        )

        // ── 分类 Tab（固定于顶栏下方，横向滚动）──
        AppSegmentedControl(
            tabs = tabLabels,
            selectedIndex = ui.selectedTab,
            onSelect = { viewModel.selectTab(it) },
            scrollable = true,
        )

        // ── 内容区（下拉刷新 + 列表）──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = { viewModel.pullRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    ui.loading && ui.logs.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    ui.error != null && ui.logs.isEmpty() -> {
                        AuditErrorState(onRetry = { viewModel.refresh() })
                    }

                    ui.logs.isEmpty() -> {
                        AppEmptyState(
                            title = stringResource(R.string.audit_empty_title),
                            subtitle = stringResource(R.string.audit_empty_subtitle),
                            icon = Icons.Rounded.History,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        AuditLogList(
                            stats = ui.stats,
                            logs = ui.logs,
                            listState = listState,
                            english = english,
                            searchActive = searchActive,
                            loadingMore = ui.loadingMore,
                            loadMoreError = ui.loadMoreError,
                            endReached = ui.endReached,
                            onRetryLoadMore = { viewModel.loadMore() },
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 列表项：日期分组标题或单条日志。 */
private sealed interface MiniMeAuditListItem {
    data class DayHeader(val dayStartMs: Long) : MiniMeAuditListItem
    data class Entry(val log: RemoteAuditLogEntity) : MiniMeAuditListItem
}

@Composable
private fun AuditLogList(
    stats: AuditLogsViewModel.Stats,
    logs: List<RemoteAuditLogEntity>,
    listState: LazyListState,
    english: Boolean,
    searchActive: Boolean,
    loadingMore: Boolean,
    loadMoreError: Boolean,
    endReached: Boolean,
    onRetryLoadMore: () -> Unit,
) {
    // 数据量少（不足一屏）时不分组，直接平铺。
    val group = logs.size > 6
    val items = remember(logs, group) {
        if (!group) {
            logs.map { MiniMeAuditListItem.Entry(it) }
        } else {
            buildList<MiniMeAuditListItem> {
                var lastDay = -1L
                for (log in logs) {
                    val day = startOfDay(log.createdAt)
                    if (day != lastDay) {
                        add(MiniMeAuditListItem.DayHeader(day))
                        lastDay = day
                    }
                    add(MiniMeAuditListItem.Entry(log))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "audit_stats") {
            AuditStatsCards(stats = stats)
        }

        items(items, key = { item ->
            when (item) {
                is MiniMeAuditListItem.DayHeader -> "day_${item.dayStartMs}"
                is MiniMeAuditListItem.Entry -> "log_${item.log.id}"
            }
        }) { item ->
            when (item) {
                is MiniMeAuditListItem.DayHeader -> AppSectionHeader(
                    title = groupHeaderLabel(item.dayStartMs, english),
                    modifier = Modifier.padding(top = Spacing.md),
                )

                is MiniMeAuditListItem.Entry -> AuditLogCard(log = item.log, english = english)
            }
        }

        item(key = "audit_footer") {
            AuditListFooter(
                searchActive = searchActive,
                loadingMore = loadingMore,
                loadMoreError = loadMoreError,
                endReached = endReached,
                onRetry = onRetryLoadMore,
            )
        }
    }
}

/** 分页底部状态：加载中 / 没有更多 / 失败重试。 */
@Composable
private fun AuditListFooter(
    searchActive: Boolean,
    loadingMore: Boolean,
    loadMoreError: Boolean,
    endReached: Boolean,
    onRetry: () -> Unit,
) {
    if (searchActive) return
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.audit_loading_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            loadMoreError -> TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.audit_load_more_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            endReached -> Text(
                text = stringResource(R.string.audit_no_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> TextButton(onClick = onRetry) {
                Text(stringResource(R.string.audit_more))
            }
        }
    }
}

// ── 统计卡片：2x2 网格 ──

@Composable
private fun AuditStatsCards(stats: AuditLogsViewModel.Stats) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = stats.total.toString(),
                label = stringResource(R.string.audit_stats_total),
                icon = Icons.Rounded.List,
                tint = MaterialTheme.colorScheme.primary,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = stats.failed.toString(),
                label = stringResource(R.string.audit_stats_failed),
                icon = Icons.Rounded.Error,
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = stats.today.toString(),
                label = stringResource(R.string.audit_stats_today),
                icon = Icons.Rounded.Today,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = "10000",
                label = stringResource(R.string.audit_stats_retention_label),
                icon = Icons.Rounded.Storage,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 单条日志卡片 ──

@Composable
private fun AuditLogCard(
    log: RemoteAuditLogEntity,
    english: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val actionName = AuditActionMapper.displayName(log.action, english)
    val categoryText = AuditActionMapper.categoryLabel(log.category, log.action, english)
    val categoryIcon = auditCategoryIcon(log.category, log.action)
    // 成功=tertiary(绿)，失败=error(红)。
    val barColor = if (log.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val titleColor = if (log.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error

    AppCard(modifier = modifier.then(Modifier.clickable { expanded = !expanded })) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(barColor),
            )
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppStatusDot(
                        if (log.success) AppStatusDotColor.Success else AppStatusDotColor.Error,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = actionName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = eventTimeLabel(log.createdAt, english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(
                        text = categoryText,
                        variant = AppChipVariant.Default,
                        chipColor = AppChipColor.Neutral,
                        icon = categoryIcon,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    val host = listOfNotNull(log.connectionName, log.remoteHost)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (host.isNotEmpty()) {
                        Text(
                            text = host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!log.message.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(Spacing.sm))
                    AuditDetailRow(stringResource(R.string.audit_detail_source_ip), log.sourceIp)
                    AuditDetailRow(stringResource(R.string.audit_detail_connection_id), log.connectionId)
                }
            }
        }
    }
}

@Composable
private fun AuditDetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.padding(vertical = Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── 错误状态 + 重试 ──

@Composable
private fun AuditErrorState(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.audit_load_error),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.audit_retry))
            }
        }
    }
}

// ── 时间 / 分组工具 ──

/** 取当天 0 点时间戳。 */
private fun startOfDay(ts: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ts
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun groupHeaderLabel(dayStartMs: Long, english: Boolean): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = startOfDay(System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
    return when (dayStartMs) {
        today -> stringResource(R.string.audit_group_today)
        yesterday -> stringResource(R.string.audit_group_yesterday)
        else -> {
            val pattern = if (english) "MMM d, EEEE" else "M月d日 EEEE"
            val locale = if (english) Locale.ENGLISH else Locale.getDefault()
            remember(dayStartMs, english) {
                SimpleDateFormat(pattern, locale).format(Date(dayStartMs))
            }
        }
    }
}

@Composable
private fun eventTimeLabel(createdAt: Long, english: Boolean): String {
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = startOfDay(System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
    val day = startOfDay(createdAt)
    val hm = remember(createdAt) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
    }
    return when (day) {
        today -> stringResource(R.string.audit_time_today, hm)
        yesterday -> stringResource(R.string.audit_time_yesterday, hm)
        else -> remember(createdAt) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createdAt))
        }
    }
}

/** 分类 -> Chip 图标。 */
private fun auditCategoryIcon(category: String, action: String): ImageVector {
    if (action.startsWith("SKILL_")) return Icons.Rounded.Code
    return when (category) {
        RemoteAuditCategory.CONNECT,
        RemoteAuditCategory.SYNC,
        RemoteAuditCategory.RECONNECT_FAIL -> Icons.Rounded.SwapHoriz

        RemoteAuditCategory.CREDENTIAL -> Icons.Rounded.Key
        RemoteAuditCategory.BACKUP -> Icons.Rounded.Backup
        RemoteAuditCategory.SECURITY -> Icons.Rounded.Security
        else -> Icons.Rounded.List
    }
}
