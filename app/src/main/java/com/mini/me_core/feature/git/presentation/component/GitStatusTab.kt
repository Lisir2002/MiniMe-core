package com.mini.me_core.feature.git.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.git.domain.model.GitFileChange
import com.mini.me_core.feature.git.domain.model.GitStash
import com.mini.me_core.feature.git.domain.model.GitStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Upload

@Composable
internal fun StatusTab(
    status: GitStatus?,
    busy: Boolean,
    hasRemote: Boolean,
    hasIdentity: Boolean,
    stashes: List<GitStash>,
    stashLoading: Boolean,
    onStage: (String) -> Unit,
    onUnstage: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onFileDiff: (String) -> Unit,
    onStashPush: (String?, Boolean) -> Unit,
    onStashPop: (Int) -> Unit,
    onStashApply: (Int) -> Unit,
    onStashDrop: (Int) -> Unit,
    onStashClear: () -> Unit
) {
    val s = status
    val clean = s == null || (s.staged.isEmpty() && s.unstaged.isEmpty() && s.untracked.isEmpty())

    Column(Modifier.fillMaxSize()) {
        StatusOverview(status = s, clean = clean)
        StatusActionsBar(
            busy = busy,
            hasStagedChanges = s?.staged?.isNotEmpty() == true,
            hasRemote = hasRemote,
            hasIdentity = hasIdentity,
            onStageAll = onStageAll,
            onCommit = onCommit,
            onPull = onPull,
            onPush = onPush
        )

        HorizontalDivider()

        StashSection(
            stashes = stashes,
            stashLoading = stashLoading,
            busy = busy,
            onStashPush = onStashPush,
            onStashPop = onStashPop,
            onStashApply = onStashApply,
            onStashDrop = onStashDrop,
            onStashClear = onStashClear
        )

        HorizontalDivider()

        if (clean) {
            EmptyState(stringResource(R.string.git_clean_with_changes))
        } else {
            val ss = s ?: return
            val listState = rememberPersistentLazyListState("git_status")
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xl)
            ) {
                if (ss.staged.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.git_staged_count, ss.staged.size)) }
                    items(ss.staged, key = { "s-${it.path}" }) { f ->
                        FileRow(f, actionIcon = Icons.Rounded.Remove, actionDesc = stringResource(R.string.git_unstage), onAction = { onUnstage(f.path) }, enabled = !busy)
                    }
                }
                if (ss.unstaged.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.git_modified_count, ss.unstaged.size)) }
                    items(ss.unstaged, key = { "u-${it.path}" }) { f ->
                        FileRow(f, actionIcon = Icons.Rounded.Add, actionDesc = stringResource(R.string.git_stage), onAction = { onStage(f.path) }, enabled = !busy, onClick = { onFileDiff(f.path) })
                    }
                }
                if (ss.untracked.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.git_untracked_count, ss.untracked.size)) }
                    items(ss.untracked, key = { it }) { path ->
                        FileRow(
                            file = GitFileChange(path, "?", staged = false),
                            actionIcon = Icons.Rounded.Add,
                            actionDesc = stringResource(R.string.git_stage),
                            onAction = { onStage(path) },
                            enabled = !busy
                        )
                    }
                }
            }
        }
    }
}

/**
 * stash 管理区域：可折叠，默认展开。展示 stash 列表（index/分支/说明/基线哈希），
 * 每条提供恢复(pop)/应用(apply)/删除(drop)；顶部提供「新建 stash」与「清空全部」入口。
 * 删除与清空属于危险操作，由上层（GitScreen 的 DangerousActionDialog）二次确认。
 */
@Composable
private fun StashSection(
    stashes: List<GitStash>,
    stashLoading: Boolean,
    busy: Boolean,
    onStashPush: (String?, Boolean) -> Unit,
    onStashPop: (Int) -> Unit,
    onStashApply: (Int) -> Unit,
    onStashDrop: (Int) -> Unit,
    onStashClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var showNewDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, top = Spacing.sm, end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = buildString {
                append(stringResource(R.string.git_stash_section))
                if (stashes.isNotEmpty()) append(" (").append(stashes.size).append(")")
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { showNewDialog = true }, enabled = !busy) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.git_stash_new))
        }
        if (stashes.isNotEmpty()) {
            TextButton(onClick = onStashClear, enabled = !busy) {
                Text(stringResource(R.string.git_stash_clear_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (expanded) {
        when {
            stashLoading -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            stashes.isEmpty() -> Text(
                text = stringResource(R.string.git_stash_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            )
            else -> Column {
                stashes.forEach { stash ->
                    StashRow(
                        stash = stash,
                        enabled = !busy,
                        onPop = { onStashPop(stash.index) },
                        onApply = { onStashApply(stash.index) },
                        onDrop = { onStashDrop(stash.index) }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NewStashDialog(
            onDismiss = { showNewDialog = false },
            onConfirm = { msg, untracked ->
                showNewDialog = false
                onStashPush(msg, untracked)
            }
        )
    }
}

@Composable
private fun StashRow(
    stash: GitStash,
    enabled: Boolean,
    onPop: () -> Unit,
    onApply: () -> Unit,
    onDrop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(LocalCornerRadius.current.xs)
            ) {
                Text(
                    text = stringResource(R.string.git_stash_index_label, stash.index),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stash.message.ifBlank { stash.commitHash.ifBlank { "—" } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (stash.branch.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.git_stash_on_branch, stash.branch),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onPop, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.git_stash_pop), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onApply, enabled = enabled, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                Text(stringResource(R.string.git_stash_apply), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDrop, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.git_stash_drop), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = Spacing.lg))
    }
}

@Composable
private fun NewStashDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?, Boolean) -> Unit
) {
    var message by remember { mutableStateOf("") }
    var includeUntracked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text(stringResource(R.string.git_stash_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(stringResource(R.string.git_stash_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.git_stash_include_untracked), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includeUntracked, onCheckedChange = { includeUntracked = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(message.trim().ifBlank { null }, includeUntracked) }) {
                Text(stringResource(R.string.git_stash_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun StatusOverview(status: GitStatus?, clean: Boolean) {
    val staged = status?.staged?.size ?: 0
    val modified = status?.unstaged?.size ?: 0
    val untracked = status?.untracked?.size ?: 0

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(LocalCornerRadius.current.md),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (clean) stringResource(R.string.git_clean) else stringResource(R.string.git_has_changes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = status?.branch ?: stringResource(R.string.git_no_branch),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status != null && (status.ahead > 0 || status.behind > 0)) {
                    Spacer(Modifier.width(Spacing.sm))
                    SyncPill(ahead = status.ahead, behind = status.behind)
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusMetric(stringResource(R.string.git_staged_label), staged, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                StatusMetric(stringResource(R.string.git_modified_label), modified, Color(0xFFD97706), Modifier.weight(1f))
                StatusMetric(stringResource(R.string.git_untracked_label), untracked, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SyncPill(ahead: Int, behind: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(LocalCornerRadius.current.pill)
    ) {
        Text(
            text = buildString {
                if (ahead > 0) append("↑$ahead")
                if (behind > 0) {
                    if (isNotEmpty()) append("  ")
                    append("↓$behind")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun StatusActionsBar(
    busy: Boolean,
    hasStagedChanges: Boolean,
    hasRemote: Boolean,
    hasIdentity: Boolean,
    onStageAll: () -> Unit,
    onCommit: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit
) {
    val canCommit = !busy && hasStagedChanges && hasIdentity
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ActionButton(stringResource(R.string.git_commit_changes), Icons.Rounded.Commit, prominent = true, enabled = canCommit, onClick = onCommit, modifier = Modifier.fillMaxWidth())
                if (!hasIdentity) {
                    Text(
                        stringResource(R.string.git_no_identity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton(stringResource(R.string.git_stage_all), Icons.Rounded.Add, enabled = !busy, onClick = onStageAll, modifier = Modifier.weight(1f))
                    ActionButton(stringResource(R.string.git_pull), Icons.Rounded.Download, enabled = !busy && hasRemote, onClick = onPull, modifier = Modifier.weight(1f))
                    ActionButton(stringResource(R.string.git_push), Icons.Rounded.Upload, enabled = !busy && hasRemote, onClick = onPush, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionButton(stringResource(R.string.git_commit_changes), Icons.Rounded.Commit, prominent = true, enabled = canCommit, onClick = onCommit, modifier = Modifier.weight(1.4f))
                    ActionButton(stringResource(R.string.git_stage_all), Icons.Rounded.Add, enabled = !busy, onClick = onStageAll, modifier = Modifier.weight(1f))
                    ActionButton(stringResource(R.string.git_pull), Icons.Rounded.Download, enabled = !busy && hasRemote, onClick = onPull, modifier = Modifier.weight(1f))
                    ActionButton(stringResource(R.string.git_push), Icons.Rounded.Upload, enabled = !busy && hasRemote, onClick = onPush, modifier = Modifier.weight(1f))
                }
                if (!hasIdentity) {
                    Text(
                        stringResource(R.string.git_no_identity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: GitFileChange,
    actionIcon: ImageVector,
    actionDesc: String,
    onAction: () -> Unit,
    enabled: Boolean,
    onClick: (() -> Unit)? = null
) {
    val fileName = file.path.substringAfterLast('/')
    val directory = file.path.substringBeforeLast('/', missingDelimiterValue = "")

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusChip(file.statusCode)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (directory.isNotEmpty()) {
                    Text(
                        text = directory,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            IconButton(onClick = onAction, enabled = enabled) {
                Icon(
                    actionIcon,
                    contentDescription = actionDesc,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 60.dp)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    prominent: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (prominent) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(LocalCornerRadius.current.md),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = Spacing.md)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(LocalCornerRadius.current.md),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = Spacing.sm)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
