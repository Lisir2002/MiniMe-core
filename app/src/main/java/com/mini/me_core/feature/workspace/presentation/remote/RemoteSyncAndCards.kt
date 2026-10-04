package com.mini.me_core.feature.workspace.presentation.remote
import com.mini.me_core.core.theme.components.AppDropdownMenu
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteMount
import com.mini.me_core.feature.workspace.domain.model.RemoteProtocol
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.components.AppStatusDot
import com.mini.me_core.core.theme.components.AppStatusType

// ──────────────────────────────────────────────
// 同步设置区
// ──────────────────────────────────────────────

@Composable
fun SyncSettingsSection(
    ignoredPatterns: String,
    useGitIgnore: Boolean,
    maxSyncBatchSize: Int,
    conflictStrategy: String,
    autoSyncEnabled: Boolean,
    autoSyncIntervalMinutes: Int,
    onPatternsChange: (String) -> Unit,
    onUseGitIgnoreChange: (Boolean) -> Unit,
    onMaxSyncBatchSizeChange: (Int) -> Unit,
    onDirectionChange: (String) -> Unit,
    onConflictStrategyChange: (String) -> Unit,
    onAutoSyncChange: (Boolean, Int) -> Unit,
) {
    var patternsText by remember(ignoredPatterns) { mutableStateOf(ignoredPatterns) }
    var maxBatchSizeText by remember(maxSyncBatchSize) { mutableStateOf(maxSyncBatchSize.toString()) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PrimitiveSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md)
    ) {
        // ── 忽略规则卡片 ──
        AppCard {
            Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Text(
                        text = stringResource(R.string.sync_ignore_list),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.sync_ignore_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = patternsText,
                    onValueChange = { patternsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sync_ignore_rules)) },
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                // 常用模板快捷按钮
                Text(
                    text = stringResource(R.string.remote_ignore_templates_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                val templates = listOf(".git/", "node_modules/", "*.tmp", "*.log", "__pycache__/", "build/")
                FlowChipRow(templates) { tpl ->
                    patternsText = appendPattern(patternsText, tpl)
                }
                Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                AppButton(
                    text = stringResource(R.string.sync_save_rules),
                    onClick = {
                        onPatternsChange(patternsText)
                        android.widget.Toast.makeText(context, context.getString(R.string.sync_ignore_saved), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    size = AppButtonSize.Small,
                )
            }
        }

        // ── 遵循 .gitignore ──
        AppCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PrimitiveSpacing.Lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Rounded.CheckBox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Column {
                        Text(
                            text = stringResource(R.string.sync_follow_gitignore),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.sync_gitignore_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Switch(
                    checked = useGitIgnore,
                    onCheckedChange = {
                        onUseGitIgnoreChange(it)
                        android.widget.Toast.makeText(context, if (it) context.getString(R.string.sync_gitignore_enabled) else context.getString(R.string.sync_gitignore_disabled), android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── 同步方向 ──
        AppCard {
            Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Text(
                        text = stringResource(R.string.remote_sync_direction),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                val directions = listOf("bidirectional", "upload_only", "download_only")
                val labels = listOf(
                    stringResource(R.string.remote_dir_bidirectional),
                    stringResource(R.string.remote_dir_upload_only),
                    stringResource(R.string.remote_dir_download_only)
                )
                // 默认按双向展示（全局方向应用到所有挂载点）
                var selectedDirection by remember { mutableStateOf(directions.first()) }
                AppSegmentedControl(
                    tabs = labels,
                    selectedIndex = directions.indexOf(selectedDirection).coerceAtLeast(0),
                    onSelect = {
                        selectedDirection = directions[it]
                        onDirectionChange(directions[it])
                    }
                )
            }
        }

        // ── 最大批量大小 ──
        AppCard {
            Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Text(
                        text = stringResource(R.string.sync_max_batch_size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.sync_batch_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = maxBatchSizeText,
                    onValueChange = { maxBatchSizeText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sync_max_batch_count)) }
                )
                Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                AppButton(
                    text = stringResource(R.string.sync_save_batch_size),
                    onClick = {
                        val size = maxBatchSizeText.toIntOrNull() ?: 50
                        onMaxSyncBatchSizeChange(size)
                        android.widget.Toast.makeText(context, context.getString(R.string.sync_batch_size_saved), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    size = AppButtonSize.Small,
                )
            }
        }

        // ── 冲突处理策略 ──
        AppCard {
            Column(modifier = Modifier
                .fillMaxWidth()
                .clickable { showConflictDialog = true }
                .padding(PrimitiveSpacing.Lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.remote_conflict_strategy),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.remote_conflict_strategy_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = conflictStrategyLabel(conflictStrategy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── 自动同步 ──
        AppCard {
            Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                        Column {
                            Text(
                                text = stringResource(R.string.remote_auto_sync),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.remote_auto_sync_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { onAutoSyncChange(it, autoSyncIntervalMinutes) }
                    )
                }
                if (autoSyncEnabled) {
                    Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIntervalDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.remote_auto_sync_interval),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = intervalLabel(autoSyncIntervalMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (showConflictDialog) {
        val strategies = listOf("remote_overwrite", "local_overwrite", "skip", "rename")
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { showConflictDialog = false },
            title = { Text(stringResource(R.string.remote_conflict_strategy)) },
            text = {
                Column {
                    strategies.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onConflictStrategyChange(s)
                                    showConflictDialog = false
                                }
                                .padding(vertical = PrimitiveSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (s == conflictStrategy),
                                onClick = {
                                    onConflictStrategyChange(s)
                                    showConflictDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(PrimitiveSpacing.Sm))
                            Text(strategyLabel(s), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConflictDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showIntervalDialog) {
        val intervals = listOf(5, 15, 30, 60, 0) // 0 = manual
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.remote_auto_sync_interval)) },
            text = {
                Column {
                    intervals.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAutoSyncChange(true, m)
                                    showIntervalDialog = false
                                }
                                .padding(vertical = PrimitiveSpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (m == autoSyncIntervalMinutes),
                                onClick = {
                                    onAutoSyncChange(true, m)
                                    showIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(PrimitiveSpacing.Sm))
                            Text(intervalLabel(m), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 简单的流式 Chip 行（自动换行）。 */
@Composable
private fun FlowChipRow(items: List<String>, onClick: (String) -> Unit) {
    // 用 Row + 简易换行：由于数量少，直接 Row wrap 模拟（Column 内 Row 手动分两组即可，这里用简单 Row）
    // 为保证换行，采用 Row 内 weight 不适用，故用 Column 堆叠两行。
    Column(verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
                rowItems.forEach { tpl ->
                    AppChip(
                        text = tpl,
                        chipColor = AppChipColor.Info,
                        variant = AppChipVariant.Outlined,
                        modifier = Modifier.clickable { onClick(tpl) }
                    )
                }
            }
        }
    }
}

private fun appendPattern(existing: String, template: String): String {
    val cleaned = existing.trim().trimEnd(',')
    return if (cleaned.isEmpty()) template else "$cleaned,$template"
}

@Composable
private fun strategyLabel(s: String): String = when (s) {
    "remote_overwrite" -> stringResource(R.string.remote_conflict_remote_overwrite)
    "local_overwrite" -> stringResource(R.string.remote_conflict_local_overwrite)
    "skip" -> stringResource(R.string.remote_conflict_skip)
    "rename" -> stringResource(R.string.remote_conflict_rename)
    else -> s
}

@Composable
private fun conflictStrategyLabel(s: String): String = strategyLabel(s)

@Composable
private fun intervalLabel(minutes: Int): String = when (minutes) {
    5 -> stringResource(R.string.remote_interval_5min)
    15 -> stringResource(R.string.remote_interval_15min)
    30 -> stringResource(R.string.remote_interval_30min)
    60 -> stringResource(R.string.remote_interval_1h)
    0 -> stringResource(R.string.remote_interval_manual)
    else -> "${minutes}min"
}

// ──────────────────────────────────────────────
// 连接卡片
// ──────────────────────────────────────────────

@Composable
fun RemoteConnectionCard(
    conn: RemoteConnection,
    testing: Boolean = false,
    onTest: (RemoteConnection) -> Unit = {},
    onEdit: (RemoteConnection) -> Unit,
    onDelete: (RemoteConnection) -> Unit
) {
    val isLocal = conn.protocol == RemoteProtocol.LOCAL
    val (protocolIcon, chipColor) = when (conn.protocol) {
        RemoteProtocol.SFTP -> Icons.Rounded.Lock to AppChipColor.Info
        RemoteProtocol.FTP -> Icons.Rounded.Dns to AppChipColor.Warning
        RemoteProtocol.LOCAL -> Icons.Rounded.Storage to AppChipColor.Neutral
    }
    val context = LocalContext.current

    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PrimitiveSpacing.Lg),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧协议图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    protocolIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
            // 中间内容
            Column(modifier = Modifier.weight(1f)) {
                // 标题行：名称 + 协议 Chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = conn.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(PrimitiveSpacing.Sm))
                    AppChip(
                        text = conn.protocol.name,
                        chipColor = chipColor,
                        variant = AppChipVariant.Default,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                // 副标题：user@host + 端口单独显示
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLocal) conn.host else "${conn.username}@${conn.host}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isLocal) {
                        Spacer(modifier = Modifier.width(PrimitiveSpacing.Sm))
                        AppChip(
                            text = ":${conn.port}",
                            chipColor = AppChipColor.Neutral,
                            variant = AppChipVariant.Outlined,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                // 最后连接时间
                Text(
                    text = stringResource(
                        R.string.remote_last_connected,
                        relativeTimeText(conn.lastConnectedAt)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 右侧操作：测试 / 编辑 / 删除
            Column {
                IconButton(onClick = { onTest(conn) }, enabled = !testing) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Rounded.NetworkCheck,
                            contentDescription = stringResource(R.string.remote_action_test),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = { onEdit(conn) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(conn) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun relativeTimeText(timestamp: Long?): String {
    if (timestamp == null) return stringResource(R.string.remote_never_connected)
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> stringResource(R.string.remote_time_just_now)
        minutes < 60 -> stringResource(R.string.remote_time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.remote_time_hours_ago, hours.toInt())
        else -> stringResource(R.string.remote_time_days_ago, days.toInt())
    }
}

// ──────────────────────────────────────────────
// 挂载卡片
// ──────────────────────────────────────────────

@Composable
fun RemoteMountCard(
    mount: RemoteMount,
    isFailed: Boolean = false,
    direction: String = "bidirectional",
    onDirectionChange: (String) -> Unit = {},
    onEdit: (RemoteMount) -> Unit,
    onDelete: (RemoteMount) -> Unit,
    onUpload: (RemoteMount) -> Unit,
    onDownload: (RemoteMount) -> Unit,
    onConnect: (RemoteMount) -> Unit,
    onDisconnect: (RemoteMount) -> Unit
) {
    val isLocal = mount.connection?.protocol == RemoteProtocol.LOCAL
    var directionMenuExpanded by remember { mutableStateOf(false) }
    val mountTitle = mount.remotePath.trimEnd('/').substringAfterLast('/').ifEmpty { mount.remotePath }

    AppCard {
        Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(PrimitiveSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    // 标题：挂载名称（远程路径最后一级）
                    Text(
                        text = mountTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 副标题1：通过「连接名」同步
                    Text(
                        text = stringResource(R.string.sync_via_connection, mount.connection?.name ?: stringResource(R.string.sync_unknown_connection)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(PrimitiveSpacing.Xs))
                    // 副标题2：远程路径 → 本地路径
                    PathRow(label = stringResource(R.string.remote_mount_remote_path_label), path = mount.remotePath)
                    Spacer(modifier = Modifier.height(2.dp))
                    PathRow(label = stringResource(R.string.remote_mount_local_path_label), path = mount.localMountPath)
                }
                // 右侧编辑/删除
                Row {
                    IconButton(onClick = { onEdit(mount) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.common_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(mount) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))

            // 状态行 + 同步方向 Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
                    val statusType = when {
                        mount.isActive -> AppStatusType.Running
                        isFailed -> AppStatusType.Error
                        else -> AppStatusType.Stopped
                    }
                    AppStatusDot(status = statusType, size = 8.dp)
                    val statusText = when {
                        mount.isActive -> stringResource(R.string.status_connected)
                        isFailed -> stringResource(R.string.status_connection_failed)
                        else -> stringResource(R.string.status_disconnected)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            mount.isActive -> MaterialTheme.colorScheme.tertiary
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
                // 同步方向 Chip（点击切换）
                Box {
                    AppChip(
                        text = directionLabel(direction),
                        chipColor = AppChipColor.Primary,
                        variant = AppChipVariant.Outlined,
                        modifier = Modifier.clickable { directionMenuExpanded = true }
                    )
                    AppDropdownMenu(
                        expanded = directionMenuExpanded,
                        onDismissRequest = { directionMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        listOf("bidirectional", "upload_only", "download_only").forEach { d ->
                            DropdownMenuItem(
                                text = { Text(directionLabel(d)) },
                                onClick = {
                                    onDirectionChange(d)
                                    directionMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(PrimitiveSpacing.Sm))

            // 操作按钮
            if (mount.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)
                ) {
                    AppButton(
                        text = stringResource(R.string.sync_disconnect),
                        onClick = { onDisconnect(mount) },
                        variant = AppButtonVariant.Tonal,
                        size = AppButtonSize.Small,
                        modifier = Modifier.weight(1f)
                    )
                    AppButton(
                        text = if (isLocal) stringResource(R.string.sync_all) else stringResource(R.string.sync_upload_all),
                        onClick = { onUpload(mount) },
                        variant = AppButtonVariant.Filled,
                        size = AppButtonSize.Small,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isLocal) {
                        AppButton(
                            text = stringResource(R.string.sync_download_all),
                            onClick = { onDownload(mount) },
                            variant = AppButtonVariant.Tonal,
                            size = AppButtonSize.Small,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                AppButton(
                    text = stringResource(R.string.sync_connect_and_sync),
                    onClick = { onConnect(mount) },
                    variant = AppButtonVariant.Filled,
                    size = AppButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PathRow(label: String, path: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun directionLabel(direction: String): String = when (direction) {
    "upload_only" -> stringResource(R.string.remote_dir_upload_only)
    "download_only" -> stringResource(R.string.remote_dir_download_only)
    else -> stringResource(R.string.remote_dir_bidirectional)
}
