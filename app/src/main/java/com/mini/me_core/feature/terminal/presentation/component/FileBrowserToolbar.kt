package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/**
 * P1 模块5：文件浏览器底部工具栏。
 *
 * 普通模式：搜索/排序/视图 + 新建(突出) + 导入；剪贴板有内容时独立显示"粘贴 N"。
 * 多选模式：全选 + "已选 N 项" + 复制/剪切/删除 + 退出。
 */
@Composable
fun FileBrowserToolbar(
    multiSelect: Boolean,
    selectedCount: Int,
    clipboardCount: Int,
    onSearch: () -> Unit,
    onOpenSortMenu: () -> Unit,
    onToggleView: () -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onPaste: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onExitMultiSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiSelect) {
            IconButton(onClick = onExitMultiSelect) {
                Icon(Icons.Rounded.Close, stringResource(R.string.fm_multi_select),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                stringResource(R.string.fm_selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.CheckCircle, stringResource(R.string.fm_select_all),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.fm_copy),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onCut) {
                Icon(Icons.Rounded.ContentCut, stringResource(R.string.fm_cut),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.fm_delete),
                    tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, stringResource(R.string.fm_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenSortMenu) {
                Icon(Icons.Rounded.Sort, stringResource(R.string.fm_sort),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleView) {
                Icon(Icons.Rounded.ViewAgenda, stringResource(R.string.fm_view_list),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onNew) {
                Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fm_new_folder))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onImport) {
                Icon(Icons.Rounded.ImportExport, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fm_import))
            }
            if (clipboardCount > 0) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onPaste) {
                    Text(stringResource(R.string.fm_paste_hint, clipboardCount))
                }
            }
        }
    }
}
