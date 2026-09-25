package com.mini.me_core.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.feature.settings.data.repository.SettingsChangeHistory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * F5.3 变更历史屏幕。
 *
 * - 按时间倒序、按天分组（今天/昨天/更早）
 * - 单条回滚（确认对话框 → 恢复旧值）
 * - 批量模式：多选后批量回滚
 * - 清空全部历史（二次确认）
 */
@Composable
fun ChangeHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChangeHistoryViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val batchMode by viewModel.batchMode.collectAsStateWithLifecycle()
    val resultEvent by viewModel.resultEvent.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingRollback by remember { mutableStateOf<SettingsChangeHistory.ChangeRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(resultEvent) {
        val ev = resultEvent ?: return@LaunchedEffect
        when {
            ev.restoredCount > 0 -> snackbarHostState.showSnackbar(
                message = context.getString(R.string.settings_history_restored, ev.restoredCount)
            )
            ev.failed -> snackbarHostState.showSnackbar(
                message = context.getString(R.string.settings_history_failed)
            )
        }
        viewModel.consumeResultEvent()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.setBatchMode(!batchMode) }) {
                        Text(stringResource(R.string.settings_history_batch))
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.settings_history_clear))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (records.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.settings_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val groups = groupByDay(records)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "header_${group.key}") {
                            Text(
                                text = group.label(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(group.items, key = { it.id }) { record ->
                            HistoryRow(
                                record = record,
                                batchMode = batchMode,
                                checked = record.id in selected,
                                onRowClick = {
                                    if (batchMode) viewModel.toggleSelect(record.id) else pendingRollback = record
                                },
                                onRollbackClick = { pendingRollback = record },
                                onCheckChange = { viewModel.toggleSelect(record.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (batchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_history_selected_count, selected.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { viewModel.rollbackSelected() },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.settings_history_batch_rollback))
                    }
                }
            }
        }
    }

    pendingRollback?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text(stringResource(R.string.settings_history_rollback_confirm_title)) },
            text = { Text(stringResource(R.string.settings_history_rollback_confirm_msg, rec.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rollback(rec)
                    pendingRollback = null
                }) {
                    Text(
                        text = stringResource(R.string.settings_history_rollback_now),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) {
                    Text(stringResource(R.string.settings_history_cancel))
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_history_clear)) },
            text = { Text(stringResource(R.string.settings_history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.settings_history_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.settings_history_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    record: SettingsChangeHistory.ChangeRecord,
    batchMode: Boolean,
    checked: Boolean,
    onRowClick: () -> Unit,
    onRollbackClick: () -> Unit,
    onCheckChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (batchMode) {
            Checkbox(checked = checked, onCheckedChange = onCheckChange)
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue(record.oldValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = displayValue(record.newValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sourceLabelText(record.source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (!batchMode) {
            TextButton(onClick = onRollbackClick) {
                Text(stringResource(R.string.settings_history_rollback))
            }
        }
    }
}

private fun displayValue(raw: String?): String {
    if (raw == null) return "—"
    return if (raw.length > 24) raw.take(24) + "…" else raw
}

@Composable
private fun sourceLabelText(source: String): String {
    val res = when (source) {
        "IMPORT" -> R.string.settings_history_source_import
        "ROLLBACK" -> R.string.settings_history_source_rollback
        "DEFAULT" -> R.string.settings_history_source_default
        else -> R.string.settings_history_source_user
    }
    return stringResource(res)
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

private enum class DayKey { TODAY, YESTERDAY, EARLIER }

private data class DayGroup(
    val key: DayKey,
    val items: List<SettingsChangeHistory.ChangeRecord>,
) {
    @Composable
    fun label(): String = when (key) {
        DayKey.TODAY -> stringResource(R.string.settings_history_today)
        DayKey.YESTERDAY -> stringResource(R.string.settings_history_yesterday)
        DayKey.EARLIER -> stringResource(R.string.settings_history_earlier)
    }
}

private fun groupByDay(records: List<SettingsChangeHistory.ChangeRecord>): List<DayGroup> {
    val fmtDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayKey = fmtDay.format(Calendar.getInstance().time)
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayKey = fmtDay.format(yesterdayCal.time)

    val today = mutableListOf<SettingsChangeHistory.ChangeRecord>()
    val yesterday = mutableListOf<SettingsChangeHistory.ChangeRecord>()
    val earlier = mutableListOf<SettingsChangeHistory.ChangeRecord>()

    for (r in records) {
        when (fmtDay.format(Date(r.timestamp))) {
            todayKey -> today.add(r)
            yesterdayKey -> yesterday.add(r)
            else -> earlier.add(r)
        }
    }
    return buildList {
        if (today.isNotEmpty()) add(DayGroup(DayKey.TODAY, today))
        if (yesterday.isNotEmpty()) add(DayGroup(DayKey.YESTERDAY, yesterday))
        if (earlier.isNotEmpty()) add(DayGroup(DayKey.EARLIER, earlier))
    }
}
