package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppBottomSheet
import com.mini.me_core.core.theme.components.AppSheetHeader
import com.mini.me_core.core.theme.components.AppSheetSelectionItem
import com.mini.me_core.feature.settings.data.repository.AppThemeMode

/**
 * 主题选择 BottomSheet 弹窗。
 *
 * @param selected 当前选中的主题模式。
 * @param onSelected 选中回调。
 * @param onDismiss 关闭弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSelectionSheet(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheet(onDismiss = onDismiss) {
        AppSheetHeader(
            title = stringResource(R.string.settings_theme_title),
            onClose = onDismiss,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            AppThemeMode.entries.forEach { mode ->
                AppSheetSelectionItem(
                    label = stringResource(mode.labelRes),
                    selected = mode == selected,
                    onClick = {
                        onDismiss()
                        onSelected(mode)
                    },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}
