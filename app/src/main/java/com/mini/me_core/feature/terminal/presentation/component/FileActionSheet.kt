package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppBottomSheet
import com.mini.me_core.core.theme.components.AppSheetActionItem
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.theme.components.fileIconVisual
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import com.mini.me_core.feature.terminal.domain.ContainerFileType

/**
 * P0 模块4：长按文件弹出的操作菜单（统一底部抽屉）。
 */
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
    AppBottomSheet(onDismiss = onDismiss) {
        // 头部：图标 + 文件名 + 大小·路径
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val visual = fileIconVisual(
                name = entry.name,
                isDir = entry.isDir,
                executable = type == ContainerFileType.EXECUTABLE,
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(visual.iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(visual.icon, null, tint = visual.iconFg,
                    modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(PrimitiveSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${formatSize(entry.sizeBytes)} · ${entry.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(PrimitiveSpacing.Lg))

        // 打开/编辑
        AppSheetActionItem(
            icon = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.OpenInNew,
            label = stringResource(if (entry.isDir) R.string.fm_enter else R.string.fm_open),
            onClick = { onOpen(); onDismiss() },
        )
        if (!entry.isDir) {
            AppSheetActionItem(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.fm_edit),
                onClick = { onEdit(); onDismiss() },
            )
        }
        AppSheetActionItem(
            icon = Icons.Rounded.Share,
            label = stringResource(R.string.fm_share),
            onClick = { onShare(); onDismiss() },
        )
        Spacer(Modifier.height(PrimitiveSpacing.Sm))
        AppSheetActionItem(
            icon = Icons.Rounded.ContentCopy,
            label = stringResource(R.string.fm_copy),
            onClick = { onCopy(); onDismiss() },
        )
        AppSheetActionItem(
            icon = Icons.Rounded.ContentCut,
            label = stringResource(R.string.fm_cut),
            onClick = { onCut(); onDismiss() },
        )
        AppSheetActionItem(
            icon = Icons.Rounded.Edit,
            label = stringResource(R.string.fm_rename),
            onClick = { onRename(); onDismiss() },
        )
        AppSheetActionItem(
            icon = Icons.Rounded.Info,
            label = stringResource(R.string.fm_properties),
            onClick = { onProperties(); onDismiss() },
        )
        AppSheetActionItem(
            icon = Icons.Rounded.SelectAll,
            label = stringResource(R.string.fm_multi_select),
            onClick = { onMultiSelect(); onDismiss() },
        )
        Spacer(Modifier.height(PrimitiveSpacing.Sm))
        AppSheetActionItem(
            icon = Icons.Rounded.Delete,
            label = stringResource(R.string.fm_delete),
            isDestructive = true,
            onClick = { onDelete(); onDismiss() },
        )
    }
}
