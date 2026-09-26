package com.mini.me_core.feature.backup.presentation
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.backup.data.guard.SentinelVerdict
import com.mini.me_core.feature.backup.domain.AutoBackupConfig
import com.mini.me_core.feature.backup.domain.BackupHistoryItem
import com.mini.me_core.feature.backup.domain.BackupOptions
import com.mini.me_core.feature.backup.domain.BackupSource
import com.mini.me_core.feature.backup.domain.PasswordStrength
import com.mini.me_core.feature.backup.domain.RestoreMode
import com.mini.me_core.feature.backup.domain.evaluatePasswordStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupSection(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dataSafety by viewModel.dataSafety.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refreshDataSafety() }

    var password by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingExportOptions by remember { mutableStateOf(BackupOptions()) }
    var exportOptions by remember { mutableStateOf(BackupOptions()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri ->
        if (uri != null) {
            val pw = pendingExportPassword
            val opts = pendingExportOptions
            scope.launch {
                val os = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri) }
                if (os != null) {
                    viewModel.export(pw, opts, os)
                } else {
                    Toast.makeText(context, context.getString(R.string.backup_write_failed, ""), Toast.LENGTH_LONG).show()
                    viewModel.reset()
                }
            }
        } else {
            viewModel.reset()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) { pendingAction = null; return@rememberLauncherForActivityResult }
        pendingImportUri = uri
        pendingAction = PendingAction.ImportPassword
    }

    // 一次性 Snackbar
    LaunchedEffect(dataSafety.snackbar) {
        dataSafety.snackbar?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.snackbarConsumed()
        }
    }

    // 导出完成
    LaunchedEffect(state) {
        if (state is BackupState.ExportDone) {
            viewModel.reset()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 分区1：数据安全总览（异常时合并恢复入口）
        OverviewCard(
            verdict = dataSafety.verdict,
            hasBackup = dataSafety.backupCount > 0,
            lastBackupTime = dataSafety.lastBackupTime,
            occupiedBytes = dataSafety.localStorageBytes + externalBytes(dataSafety),
            onRestoreLatest = { viewModel.restoreLatestLocal() }
        )

        // 分区2：自动备份（本机 + 外部合并卡组）
        AutoBackupSection(
            config = dataSafety.config,
            localCount = dataSafety.backupCount,
            localBytes = dataSafety.localStorageBytes,
            lastLocal = dataSafety.lastBackupTime,
            externalCount = dataSafety.externalBackupCount,
            lastExternal = dataSafety.lastExternalBackupTime,
            externalAvailable = dataSafety.externalAvailable,
            working = dataSafety.working,
            onSetEnabled = viewModel::setAutoBackupEnabled,
            onSetKeepMax = viewModel::setKeepMax,
            onSetInterval = viewModel::setIntervalDays,
            onSetBackupOnUpgrade = viewModel::setBackupOnUpgrade,
            onBackupNow = viewModel::backupNow,
            onBackupToExternal = viewModel::backupToExternal,
        )

        // 分区3：备份历史
        HistorySection(
            history = dataSafety.history,
            onRestore = viewModel::restoreFromHistory,
            onDelete = viewModel::deleteHistory,
            onVerify = viewModel::verifyHistory,
            onShare = viewModel::shareHistory,
            onClearExcess = viewModel::clearExcessLocal,
        )

        // 分区4：手动备份与迁移
        ManualSection(
            working = state is BackupState.Working,
            onExport = { pendingAction = PendingAction.ExportOptions },
            onImport = {
                pendingAction = PendingAction.ImportPassword
                importLauncher.launch(arrayOf("application/gzip", "application/octet-stream", "*/*"))
            },
        )

        // 卡片内线性进度（非阻塞）
        if (state is BackupState.Working) {
            WorkingCard(state as BackupState.Working, onCancel = viewModel::cancelWorking)
        }
    }

    // 导出：选数据范围
    if (pendingAction == PendingAction.ExportOptions) {
        ExportOptionsDialog(
            options = exportOptions,
            onOptionsChange = { exportOptions = it },
            onConfirm = { pendingAction = PendingAction.ExportPassword },
            onDismiss = { pendingAction = null }
        )
    }

    // 导出：口令（强度提示）
    if (pendingAction == PendingAction.ExportPassword) {
        PasswordDialog(
            title = stringResource(R.string.backup_set_password),
            subtitle = stringResource(R.string.backup_password_hint),
            confirmText = stringResource(R.string.backup_export_btn),
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                pendingExportPassword = password
                pendingExportOptions = exportOptions
                password = ""
                pendingAction = null
                exportLauncher.launch(viewModel.defaultExportFileName())
            },
            onDismiss = { password = ""; pendingAction = null }
        )
    }

    // 导入：先输口令（SAF 选完文件后）
    if (pendingAction == PendingAction.ImportPassword && pendingImportUri != null) {
        PasswordDialog(
            title = stringResource(R.string.backup_password_input),
            subtitle = stringResource(R.string.backup_password_optional_hint),
            confirmText = stringResource(R.string.backup_import_btn),
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                val pw = password
                val uri = pendingImportUri
                password = ""
                pendingAction = null
                pendingImportUri = null
                if (uri != null) viewModel.startPreviewFromSaf(uri, pw)
            },
            onDismiss = { password = ""; pendingAction = null; pendingImportUri = null }
        )
    }

    // 预览就绪：展示差异 + 选模式
    if (state is BackupState.PreviewReady) {
        val s = state as BackupState.PreviewReady
        PreviewDialog(
            preview = s.preview,
            onConfirm = { mode -> viewModel.confirmRestore(mode) },
            onDismiss = { viewModel.reset() }
        )
    }

    // 结果：成功（可撤销）/ 失败
    when (state) {
        is BackupState.ImportSuccess -> {
            val s = state as BackupState.ImportSuccess
            ResultDialog(
                title = stringResource(R.string.backup_import_done),
                message = buildImportSummary(context, s.stats),
                canUndo = s.canUndo,
                onUndo = { viewModel.undoRestore() },
                onDismiss = { viewModel.reset() }
            )
        }
        is BackupState.Error -> ResultDialog(
            title = stringResource(R.string.backup_operation_failed),
            message = (state as BackupState.Error).message,
            canUndo = false,
            onUndo = {},
            onDismiss = { viewModel.reset() }
        )
        else -> {}
    }
}

private enum class PendingAction { ExportOptions, ExportPassword, ImportPassword }

// ── 分区1：数据安全总览 ──────────────────────────────────────

@Composable
private fun OverviewCard(
    verdict: SentinelVerdict?,
    hasBackup: Boolean,
    lastBackupTime: Long?,
    occupiedBytes: Long,
    onRestoreLatest: () -> Unit,
) {
    val alert = verdict == SentinelVerdict.DATA_LOST || verdict == SentinelVerdict.PACKAGE_CHANGED
    val container = if (alert) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surface
    val accent = if (alert) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.backup_overview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(
                    text = if (alert) stringResource(R.string.backup_overview_unprotected)
                    else stringResource(R.string.backup_overview_protected),
                    container = if (alert) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    content = if (alert) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (alert) {
                Text(
                    stringResource(
                        if (verdict == SentinelVerdict.PACKAGE_CHANGED) R.string.backup_package_changed_desc
                        else R.string.backup_data_lost_desc
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                if (lastBackupTime != null) stringResource(R.string.backup_overview_last_backup, formatTime(lastBackupTime))
                else stringResource(R.string.backup_overview_never_backup),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.backup_overview_occupied, BackupViewModel.formatBytes(occupiedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alert && hasBackup) {
                TextButton(onClick = onRestoreLatest) {
                    Text(stringResource(R.string.backup_overview_alert_restore))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Spacer(Modifier.size(6.dp))
        androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) { drawCircle(content) }
        Spacer(Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

// ── 分区2：自动备份 ──────────────────────────────────────

@Composable
private fun AutoBackupSection(
    config: AutoBackupConfig,
    localCount: Int,
    localBytes: Long,
    lastLocal: Long?,
    externalCount: Int,
    lastExternal: Long?,
    externalAvailable: Boolean,
    working: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onSetKeepMax: (Int) -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetBackupOnUpgrade: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onBackupToExternal: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.backup_auto_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = config.enabled, onCheckedChange = onSetEnabled)
            }

            // 本机子项
            SourceRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = stringResource(R.string.backup_history_source_local),
                subtitle = stringResource(R.string.backup_auto_local_status, localCount, BackupViewModel.formatBytes(localBytes)) +
                    (lastLocal?.let { " · " + formatTime(it) } ?: ""),
            )
            // 外部子项
            SourceRow(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.backup_history_source_external),
                subtitle = if (externalAvailable)
                    stringResource(R.string.backup_auto_external_status, externalCount, "") +
                        (lastExternal?.let { " · " + formatTime(it) } ?: "")
                else stringResource(R.string.backup_auto_external_unavailable),
                trailing = {
                    if (!externalAvailable) {
                        OutlinedButton(onClick = onBackupToExternal) {
                            Text(stringResource(R.string.backup_auto_go_authorize))
                        }
                    }
                }
            )

            // 保留份数
            LabeledChips(
                label = stringResource(R.string.backup_auto_config_keep),
                options = AutoBackupConfig.CHOICES_KEEP_MAX,
                selected = config.keepMax,
                onSelect = onSetKeepMax,
            )
            // 周期
            val offLabel = stringResource(R.string.backup_auto_config_interval_off)
            LabeledChips(
                label = stringResource(R.string.backup_auto_config_interval),
                options = AutoBackupConfig.CHOICES_INTERVAL_DAYS,
                selected = config.intervalDays,
                optionLabel = { if (it == 0) offLabel else it.toString() },
                onSelect = onSetInterval,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.backup_auto_config_on_upgrade),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = config.backupOnUpgrade, onCheckedChange = onSetBackupOnUpgrade)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onBackupNow, enabled = !working) {
                    Text(stringResource(R.string.backup_auto_now))
                }
                TextButton(onClick = onBackupToExternal, enabled = !working && externalAvailable) {
                    Text(stringResource(R.string.backup_external_backup_now))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun LabeledChips(
    label: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    optionLabel: (Int) -> String = { it.toString() },
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    label = { Text(optionLabel(opt)) },
                )
            }
        }
    }
}

// ── 分区3：备份历史 ──────────────────────────────────────

@Composable
private fun HistorySection(
    history: List<BackupHistoryItem>,
    onRestore: (BackupHistoryItem) -> Unit,
    onDelete: (BackupHistoryItem) -> Unit,
    onVerify: (BackupHistoryItem) -> Unit,
    onShare: (BackupHistoryItem) -> Unit,
    onClearExcess: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.backup_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (history.isNotEmpty()) {
                    TextButton(onClick = onClearExcess) {
                        Text(stringResource(R.string.backup_history_clear_excess))
                    }
                }
            }
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.backup_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                history.take(20).forEach { item ->
                    HistoryRow(item, onRestore, onDelete, onVerify, onShare)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: BackupHistoryItem,
    onRestore: (BackupHistoryItem) -> Unit,
    onDelete: (BackupHistoryItem) -> Unit,
    onVerify: (BackupHistoryItem) -> Unit,
    onShare: (BackupHistoryItem) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onRestore(item) }.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩略图：来源图标容器 44dp
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.source == BackupSource.LOCAL) Icons.Rounded.PhoneAndroid else Icons.Rounded.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                formatTime(item.epochMs),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val sourceLabel = stringResource(
                if (item.source == BackupSource.LOCAL) R.string.backup_history_source_local
                else R.string.backup_history_source_external
            )
            val detail = item.stats?.let {
                stringResource(R.string.backup_history_detail, it.chatSessions, it.agentMessages, it.todoItems)
            } ?: ""
            Text(
                "$sourceLabel · ${BackupViewModel.formatBytes(item.sizeBytes)}" +
                    (item.appVersion.let { v -> if (v.isNotBlank()) " · " + stringResource(R.string.backup_history_app_version, v) else "" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButtonLike(onClick = { onShare(item) }) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.backup_history_share), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButtonLike(onClick = { onVerify(item) }) {
            Icon(Icons.Rounded.Verified, contentDescription = stringResource(R.string.backup_history_verify), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButtonLike(onClick = { onDelete(item) }) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.backup_history_delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun IconButtonLike(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, content = content)
}

// ── 分区4：手动备份与迁移 ──────────────────────────────────────

@Composable
private fun ManualSection(
    working: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.backup_manual_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ActionRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.backup_export_title),
                subtitle = stringResource(R.string.backup_manual_export_desc),
                enabled = !working,
                onClick = onExport,
            )
            ActionRow(
                icon = Icons.Rounded.Upload,
                title = stringResource(R.string.backup_import_title),
                subtitle = stringResource(R.string.backup_manual_import_desc),
                enabled = !working,
                onClick = onImport,
            )
            Text(
                stringResource(R.string.backup_security_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── 卡片内线性进度（非阻塞、可取消） ──────────────────────────

@Composable
private fun WorkingCard(state: BackupState.Working, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(state.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (state.fraction >= 0f) {
                LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.backup_cancel))
            }
        }
    }
}

// ── 对话框 ──────────────────────────────────────────────

@Composable
private fun ExportOptionsDialog(
    options: BackupOptions,
    onOptionsChange: (BackupOptions) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_select_data)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OptionRow(stringResource(R.string.common_ai_providers), options.providers) { onOptionsChange(options.copy(providers = it)) }
                OptionRow(stringResource(R.string.backup_data_git_credentials), options.gitCredentials) { onOptionsChange(options.copy(gitCredentials = it)) }
                OptionRow(stringResource(R.string.backup_data_remote), options.remoteConnections) { onOptionsChange(options.copy(remoteConnections = it)) }
                OptionRow(stringResource(R.string.backup_data_chat_history), options.chatHistory) { onOptionsChange(options.copy(chatHistory = it)) }
                OptionRow(stringResource(R.string.backup_data_mcp), options.mcpServers) { onOptionsChange(options.copy(mcpServers = it)) }
                OptionRow(stringResource(R.string.backup_data_permissions), options.permissionRules) { onOptionsChange(options.copy(permissionRules = it)) }
                OptionRow(stringResource(R.string.backup_data_app_settings), options.appSettings) { onOptionsChange(options.copy(appSettings = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_next)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    subtitle: String,
    confirmText: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strength = evaluatePasswordStrength(password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (strength != PasswordStrength.EMPTY) {
                    Spacer(Modifier.height(Spacing.xs))
                    val labelRes = when (strength) {
                        PasswordStrength.WEAK -> R.string.backup_password_strength_weak
                        PasswordStrength.MEDIUM -> R.string.backup_password_strength_medium
                        PasswordStrength.STRONG -> R.string.backup_password_strength_strong
                        PasswordStrength.EMPTY -> R.string.backup_password_label
                    }
                    val color = when (strength) {
                        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                        PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
                        PasswordStrength.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(stringResource(labelRes), style = MaterialTheme.typography.bodySmall, color = color)
                    Text(stringResource(R.string.backup_password_strength_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun PreviewDialog(
    preview: com.mini.me_core.feature.backup.domain.BackupPreview,
    onConfirm: (RestoreMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.backup_preview_backup_contains), style = MaterialTheme.typography.titleSmall)
                Text(buildStatLines(context, preview.backup), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.backup_preview_current), style = MaterialTheme.typography.titleSmall)
                Text(buildStatLines(context, preview.current), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.backup_preview_mode), style = MaterialTheme.typography.titleSmall)
                FilterChip(
                    selected = mode == RestoreMode.MERGE,
                    onClick = { mode = RestoreMode.MERGE },
                    label = { Text(stringResource(R.string.backup_preview_mode_merge)) },
                )
                Text(stringResource(R.string.backup_preview_mode_merge_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(
                    selected = mode == RestoreMode.OVERWRITE,
                    onClick = { mode = RestoreMode.OVERWRITE },
                    label = { Text(stringResource(R.string.backup_preview_mode_overwrite)) },
                )
                Text(stringResource(R.string.backup_preview_mode_overwrite_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode) }) { Text(stringResource(R.string.backup_preview_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun ResultDialog(
    title: String,
    message: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_got_it)) } },
        dismissButton = {
            if (canUndo) {
                TextButton(onClick = onUndo) { Text(stringResource(R.string.backup_undo_restore)) }
            }
        }
    )
}

// ── 工具 ──────────────────────────────────────────────

private fun externalBytes(s: com.mini.me_core.feature.backup.presentation.DataSafetyUiState): Long = 0L

private fun formatTime(epochMs: Long?): String {
    val ts = epochMs ?: return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}

private fun buildStatLines(context: android.content.Context, stats: com.mini.me_core.feature.backup.domain.RestoreStats): String = buildString {
    if (stats.chatSessions > 0) appendLine(context.getString(R.string.backup_stat_chat_sessions, stats.chatSessions))
    if (stats.agentMessages > 0) appendLine(context.getString(R.string.backup_stat_chat_messages, stats.agentMessages))
    if (stats.todoItems > 0) appendLine(context.getString(R.string.backup_stat_todo_items, stats.todoItems))
    if (stats.providers > 0) appendLine(context.getString(R.string.backup_stat_providers, stats.providers))
    if (stats.gitCredentials > 0) appendLine(context.getString(R.string.backup_stat_git_credentials, stats.gitCredentials))
    if (stats.remoteConnections > 0) appendLine(context.getString(R.string.backup_stat_remote_connections, stats.remoteConnections))
    if (stats.mcpServers > 0) appendLine(context.getString(R.string.backup_stat_mcp_servers, stats.mcpServers))
    if (stats.globalPermissionRules > 0) appendLine(context.getString(R.string.backup_stat_permission_rules, stats.globalPermissionRules))
    if (isEmpty()) append("-")
}

private fun buildImportSummary(context: android.content.Context, stats: com.mini.me_core.feature.backup.domain.RestoreStats): String = buildString {
    appendLine(context.getString(R.string.backup_restored_data))
    if (stats.providers > 0) appendLine(context.getString(R.string.backup_stat_providers, stats.providers))
    if (stats.gitCredentials > 0) appendLine(context.getString(R.string.backup_stat_git_credentials, stats.gitCredentials))
    if (stats.remoteConnections > 0) appendLine(context.getString(R.string.backup_stat_remote_connections, stats.remoteConnections))
    if (stats.remoteMounts > 0) appendLine(context.getString(R.string.backup_stat_remote_mounts, stats.remoteMounts))
    if (stats.chatSessions > 0) appendLine(context.getString(R.string.backup_stat_chat_sessions, stats.chatSessions))
    if (stats.agentMessages > 0) appendLine(context.getString(R.string.backup_stat_chat_messages, stats.agentMessages))
    if (stats.todoItems > 0) appendLine(context.getString(R.string.backup_stat_todo_items, stats.todoItems))
    if (stats.mcpServers > 0) appendLine(context.getString(R.string.backup_stat_mcp_servers, stats.mcpServers))
    if (stats.globalPermissionRules > 0) appendLine(context.getString(R.string.backup_stat_permission_rules, stats.globalPermissionRules))
    append(context.getString(R.string.backup_settings_covered))
}
