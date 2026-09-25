package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mini.me_core.R

/** 排序依据。 */
enum class FileSortBy(val labelRes: Int) {
    NAME(R.string.fm_sort_name),
    TIME(R.string.fm_sort_time),
    SIZE(R.string.fm_sort_size),
}

/**
 * P1 模块7：排序与视图菜单（BottomSheet）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSortMenu(
    sortBy: FileSortBy,
    ascending: Boolean,
    showHidden: Boolean,
    gridView: Boolean,
    onSortBy: (FileSortBy) -> Unit,
    onAscending: (Boolean) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onGridView: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.fm_sort), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            FileSortBy.entries.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = sortBy == opt, onClick = { onSortBy(opt) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = sortBy == opt, onClick = { onSortBy(opt) })
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(opt.labelRes), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ascending, onClick = { onAscending(true) },
                    label = { Text(stringResource(R.string.fm_asc)) })
                FilterChip(selected = !ascending, onClick = { onAscending(false) },
                    label = { Text(stringResource(R.string.fm_desc)) })
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.fm_show_hidden), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = showHidden, onCheckedChange = onShowHidden)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.fm_view_grid), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = gridView, onCheckedChange = onGridView)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
