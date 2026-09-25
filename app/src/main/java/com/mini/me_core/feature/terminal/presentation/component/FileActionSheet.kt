package com.mini.me_core.feature.terminal.presentation.component

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import com.mini.me_core.feature.terminal.domain.ContainerFileType

/**
 * P0 模块4：长按文件弹出的操作菜单（ModalBottomSheet）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    entry: ContainerFileEntry,
    type: ContainerFileType,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 头部：图标 + 文件名 + 大小·路径
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(fileTypeBg(type)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(fileTypeIcon(type), null, tint = fileTypeFg(type),
                        modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${formatSize(entry.sizeBytes)} · ${entry.path}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 打开/编辑
            ActionRow(
                icon = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.OpenInNew,
                label = stringResource(if (entry.isDir) R.string.fm_enter else R.string.fm_open),
                onClick = { onOpen(); onDismiss() }
            )
            if (!entry.isDir) {
                ActionRow(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.fm_edit),
                    onClick = { onEdit(); onDismiss() }
                )
            }
            ActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.fm_share),
                onClick = { onShare(); onDismiss() }
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.fm_copy),
                onClick = { onCopy(); onDismiss() }
            )
            ActionRow(
                icon = Icons.Rounded.ContentCut,
                label = stringResource(R.string.fm_cut),
                onClick = { onCut(); onDismiss() }
            )
            ActionRow(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.fm_rename),
                onClick = { onRename(); onDismiss() }
            )
            ActionRow(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.fm_properties),
                onClick = { onProperties(); onDismiss() }
            )
            ActionRow(
                icon = Icons.Rounded.SelectAll,
                label = stringResource(R.string.fm_multi_select),
                onClick = { onMultiSelect(); onDismiss() }
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.fm_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = { onDelete(); onDismiss() }
            )
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
