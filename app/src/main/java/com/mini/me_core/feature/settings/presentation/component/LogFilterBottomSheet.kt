package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.feature.settings.presentation.DateRangeMode
import com.mini.me_core.feature.settings.presentation.LogViewerUiState

/**
 * 高级筛选底部弹窗（设计文档 §9）。
 *
 * 日期快捷范围、等级多选、Tag 多选均与主控制栏状态同步；底部「重置全部 / 应用」。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogFilterBottomSheet(
    state: LogViewerUiState,
    onDismiss: () -> Unit,
    onQuickRange: (DateRangeMode) -> Unit,
    onToggleLevel: (LogLevel) -> Unit,
    onToggleTag: (String) -> Unit,
    onReset: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(PrimitiveSpacing.Lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.log_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onReset(); onDismiss() }) {
                    Text(stringResource(R.string.log_filter_reset_all))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.log_filter_apply))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = PrimitiveSpacing.Sm))

            // ── 日期快捷范围（与文件下拉同步）──
            Text(stringResource(R.string.log_filter_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
            ) {
                quickRangeLabels.forEach { (mode, res) ->
                    FilterChip(
                        selected = state.dateRangeMode == mode,
                        onClick = { onQuickRange(mode) },
                        label = { Text(stringResource(res)) },
                    )
                }
            }
            if (state.dateRangeMode == DateRangeMode.CUSTOM) {
                Text(
                    text = "${state.customDateStart ?: "?"} ~ ${state.customDateEnd ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = PrimitiveSpacing.Sm))

            // ── 等级筛选（与显示筛选栏同步）──
            Text(stringResource(R.string.log_filter_level), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
            ) {
                LogLevel.values().filter { it != LogLevel.NONE }.forEach { level ->
                    FilterChip(
                        selected = level in state.selectedLevels,
                        onClick = { onToggleLevel(level) },
                        label = { Text(level.name) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = PrimitiveSpacing.Sm))

            // ── Tag 多选 ──
            Text(stringResource(R.string.log_filter_tag), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.allAvailableTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
                    horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
                ) {
                    state.allAvailableTags.forEach { tag ->
                        FilterChip(
                            selected = tag in state.selectedTags,
                            onClick = { onToggleTag(tag) },
                            label = { Text(tag, maxLines = 1) },
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.log_filter_no_tags),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
                )
            }
        }
    }
}

private val quickRangeLabels = listOf(
    DateRangeMode.TODAY to R.string.log_quick_today,
    DateRangeMode.YESTERDAY to R.string.log_quick_yesterday,
    DateRangeMode.LAST_3_DAYS to R.string.log_quick_last3,
    DateRangeMode.LAST_7_DAYS to R.string.log_quick_last7,
    DateRangeMode.ALL to R.string.log_quick_all,
)
