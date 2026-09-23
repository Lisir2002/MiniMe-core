package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.feature.settings.presentation.DateRangeMode

/**
 * 控制栏第三行：文件 / 日期范围下拉选择器。
 *
 * 下拉分三组：快捷范围（今天/昨天/近3天/近7天/全部）、单文件列表、自定义范围入口。
 * 设计文档 §7.2。
 *
 * @param onCustomRange 点击「自定义范围…」（由父组件打开高级筛选底部弹窗）
 */
@Composable
fun LogFileSelector(
    files: List<String>,
    dateRangeMode: DateRangeMode,
    customDateStart: String?,
    customDateEnd: String?,
    selectedFileName: String?,
    onQuickRange: (DateRangeMode) -> Unit,
    onSelectFile: (String) -> Unit,
    onCustomRange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selectorLabel(files, dateRangeMode, customDateStart, customDateEnd, selectedFileName)

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 快捷范围
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_quick_section_range), style = MaterialTheme.typography.labelMedium) },
                onClick = {},
            )
            quickRanges.forEach { (mode, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        Text(if (dateRangeMode == mode) "●" else "○")
                    },
                    onClick = {
                        onQuickRange(mode)
                        expanded = false
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = PrimitiveSpacing.Xxs))
            // 单文件
            if (files.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.log_quick_section_file), style = MaterialTheme.typography.labelMedium) },
                    onClick = {},
                )
                files.reversed().forEach { file ->
                    val displayName = file.removePrefix("log-").removeSuffix(".txt")
                    // 滚动文件 log-<date>.N.txt 含额外点号；无点号的 log-<date>.txt 为当前活跃写入文件
                    val isActive = !displayName.contains(".")
                    DropdownMenuItem(
                        text = {
                            Text(if (isActive) "$displayName（当前）" else displayName)
                        },
                        leadingIcon = {
                            Text(if (file == selectedFileName && dateRangeMode == DateRangeMode.SINGLE_FILE) "●" else "○")
                        },
                        onClick = {
                            onSelectFile(file)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = PrimitiveSpacing.Xxs))
            }
            // 自定义范围入口
            DropdownMenuItem(
                text = { Text(stringResource(R.string.log_quick_range_custom)) },
                onClick = {
                    expanded = false
                    onCustomRange()
                },
            )
        }
    }
}

private val quickRanges = listOf(
    DateRangeMode.TODAY to R.string.log_quick_today,
    DateRangeMode.YESTERDAY to R.string.log_quick_yesterday,
    DateRangeMode.LAST_3_DAYS to R.string.log_quick_last3,
    DateRangeMode.LAST_7_DAYS to R.string.log_quick_last7,
    DateRangeMode.ALL to R.string.log_quick_all,
)

/** 计算下拉按钮上显示的摘要文案（设计文档 §7.4 状态同步规则）。 */
@Composable
private fun selectorLabel(
    files: List<String>,
    mode: DateRangeMode,
    customStart: String?,
    customEnd: String?,
    selectedFileName: String?,
): String {
    return when (mode) {
        DateRangeMode.TODAY -> stringResource(R.string.log_quick_today)
        DateRangeMode.YESTERDAY -> stringResource(R.string.log_quick_yesterday)
        DateRangeMode.LAST_3_DAYS -> stringResource(R.string.log_quick_last3)
        DateRangeMode.LAST_7_DAYS -> stringResource(R.string.log_quick_last7)
        DateRangeMode.ALL -> stringResource(R.string.log_quick_all)
        DateRangeMode.SINGLE_FILE -> selectedFileName?.removePrefix("log-")?.removeSuffix(".txt")
            ?: stringResource(R.string.log_filter_all)
        DateRangeMode.CUSTOM -> {
            val s = customStart ?: ""
            val e = customEnd ?: ""
            "自定义 ($s-$e)"
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 120)
@Composable
private fun LogFileSelectorPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Row(Modifier.padding(PrimitiveSpacing.Sm)) {
            LogFileSelector(
                files = listOf("log-2026-09-23.txt", "log-2026-09-22.txt", "log-2026-09-21.txt"),
                dateRangeMode = DateRangeMode.LAST_3_DAYS,
                customDateStart = null,
                customDateEnd = null,
                selectedFileName = null,
                onQuickRange = {},
                onSelectFile = {},
                onCustomRange = {},
            )
        }
    }
}
