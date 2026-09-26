package com.mini.me_core.feature.settings.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.components.AppStatusDot
import com.mini.me_core.core.theme.components.AppStatusDotColor
import com.mini.me_core.feature.workspace.data.local.entity.RemoteAuditLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 操作审计页面（MiniMe）。
 *
 * - 顶部统计概览（总数 / 失败 / 今日 + 保留策略提示）
 * - 分类 Tab（全部 / 连接 / 凭据 / 备份 / 安全 / 技能）
 * - 全局搜索（忽略 Tab）
 * - 日志卡片：状态点 + 中文动作名 + 分类 Chip + 消息
 * - 溢出菜单：导出 CSV / 清理过期 / 清空全部
 */
@Composable
fun RemoteAuditLogsScreen(
    viewModel: AuditLogsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val msgRes = when (event) {
                AuditEvent.ExportDone -> R.string.audit_export_done
                AuditEvent.ExportFailed -> R.string.audit_export_failed
                AuditEvent.Cleared -> 0
            }
            if (msgRes != 0) {
                Toast.makeText(context, msgRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md),
    ) {
        // ── 顶部操作行：搜索 + 溢出菜单 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleSearch(!ui.searchActive) }) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.audit_search_cd),
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.audit_more_cd),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.audit_export)) },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = { menuExpanded = false; viewModel.exportCsv() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ui______9b49362a)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    onClick = { menuExpanded = false; viewModel.purgeExpired() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.audit_clear_all)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
                    onClick = { menuExpanded = false; showClearDialog = true },
                )
            }
        }

        // ── 搜索框 ──
        if (ui.searchActive) {
            AuditSearchBar(
                query = ui.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onClose = { viewModel.toggleSearch(false) },
            )
        }

        // ── 统计概览卡片 ──
        AuditStatsCards(stats = ui.stats)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.audit_retention_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))

        // ── 分类 Tab ──
        AppSegmentedControl(
            tabs = listOf(
                stringResource(R.string.audit_tab_all),
                stringResource(R.string.audit_tab_connect),
                stringResource(R.string.audit_tab_credential),
                stringResource(R.string.audit_tab_backup),
                stringResource(R.string.audit_tab_security),
                stringResource(R.string.audit_tab_skill),
            ),
            selectedIndex = ui.selectedTab,
            onSelect = { viewModel.selectTab(it) },
        )

        // ── 内容区 ──
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
                AuditEmptyState()
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(ui.logs, key = { it.id }) { log ->
                        AuditLogCard(
                            log = log,
                            modifier = Modifier.padding(vertical = Spacing.xs),
                        )
                    }
                    if (!ui.endReached && !ui.searchActive) {
                        item {
                            TextButton(
                                onClick = { viewModel.loadMore() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.sm),
                            ) {
                                Text(stringResource(R.string.audit_more))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 清空确认弹窗 ──
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.audit_clear_title)) },
            text = { Text(stringResource(R.string.audit_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearAll()
                }) {
                    Text(
                        stringResource(R.string.audit_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.audit_cancel))
                }
            },
        )
    }
}

/** 搜索框。 */
@Composable
private fun AuditSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        placeholder = { Text(stringResource(R.string.audit_search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = null)
            }
        },
        singleLine = true,
    )
}

/** 顶部统计概览：总事件 / 失败事件 / 今日事件。 */
@Composable
private fun AuditStatsCards(stats: AuditLogsViewModel.Stats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.total.toString(),
            label = stringResource(R.string.audit_stats_total),
            icon = Icons.AutoMirrored.Rounded.Article,
            tint = MaterialTheme.colorScheme.primary,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.failed.toString(),
            label = stringResource(R.string.audit_stats_failed),
            icon = Icons.Rounded.Error,
            tint = MaterialTheme.colorScheme.error,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.today.toString(),
            label = stringResource(R.string.audit_stats_today),
            icon = Icons.Rounded.Today,
            tint = MaterialTheme.colorScheme.tertiary,
        )
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

/** 单条审计日志卡片。 */
@Composable
private fun AuditLogCard(
    log: RemoteAuditLogEntity,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val english = LocalConfiguration.current.locales[0]?.language == "en"
    val actionName = AuditActionMapper.displayName(log.action, english)
    val categoryText = AuditActionMapper.categoryLabel(log.category, log.action, english)
    val accentColor = if (log.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    AppCard(modifier = modifier) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧状态色条（成功=主题成功色，失败=error）
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor),
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
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = dateFormat.format(Date(log.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppChip(
                        text = categoryText,
                        variant = AppChipVariant.Outlined,
                        chipColor = AppChipColor.Neutral,
                    )
                    Spacer(Modifier.width(Spacing.xs))
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
                        color = if (log.success) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

/** 空状态。 */
@Composable
private fun AuditEmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.audit_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.audit_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 错误状态 + 重试。 */
@Composable
private fun AuditErrorState(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.audit_load_error),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(Spacing.sm))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.audit_retry))
            }
        }
    }
}
