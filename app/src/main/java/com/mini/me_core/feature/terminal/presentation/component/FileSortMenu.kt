package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppBottomSheet
import com.mini.me_core.core.theme.components.AppSheetHeader
import com.mini.me_core.core.theme.components.AppSheetSelectionItem
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

/** 排序依据。 */
enum class FileSortBy(val labelRes: Int) {
    NAME(R.string.fm_sort_name),
    TIME(R.string.fm_sort_time),
    SIZE(R.string.fm_sort_size),
}

/**
 * P1 模块7：排序与视图菜单（统一底部抽屉）。
 */
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
    AppBottomSheet(onDismiss = onDismiss) {
        AppSheetHeader(title = stringResource(R.string.fm_sort), onClose = onDismiss)
        Spacer(Modifier.height(PrimitiveSpacing.Sm))

        FileSortBy.entries.forEach { opt ->
            AppSheetSelectionItem(
                label = stringResource(opt.labelRes),
                selected = sortBy == opt,
                onClick = { onSortBy(opt) },
            )
        }

        Spacer(Modifier.height(PrimitiveSpacing.Sm))
        Row(
            modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
        ) {
            FilterChip(selected = ascending, onClick = { onAscending(true) },
                label = { Text(stringResource(R.string.fm_asc)) })
            FilterChip(selected = !ascending, onClick = { onAscending(false) },
                label = { Text(stringResource(R.string.fm_desc)) })
        }

        Spacer(Modifier.height(PrimitiveSpacing.Md))
        SwitchRow(
            label = stringResource(R.string.fm_show_hidden),
            checked = showHidden,
            onCheckedChange = onShowHidden,
        )
        SwitchRow(
            label = stringResource(R.string.fm_view_grid),
            checked = gridView,
            onCheckedChange = onGridView,
        )
        Spacer(Modifier.height(PrimitiveSpacing.Sm))
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
