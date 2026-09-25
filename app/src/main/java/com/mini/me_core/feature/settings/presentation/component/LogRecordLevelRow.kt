package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel

/**
 * 控制栏第一行：记录等级（写入阈值）。
 *
 * 折叠时只显示「记录: VERBOSE ▼」一行；展开后横向排列 6 个等级 chip + 当前等级说明。
 * 设计文档 §5.2。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogRecordLevelRow(
    currentLevel: LogLevel,
    onSelectLevel: (LogLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // 折叠行：整行可点击展开/收起
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.log_record_title) + ":",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentLevel.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.SmPlus),
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            FlowRow(
                modifier = Modifier.padding(top = PrimitiveSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
            ) {
                LogLevel.values().forEach { level ->
                    FilterChip(
                        selected = level == currentLevel,
                        onClick = { onSelectLevel(level) },
                        label = { Text(level.name) },
                    )
                }
            }
            Text(
                text = recordLevelDescription(currentLevel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = PrimitiveSpacing.Xs),
            )
        }
    }
}

/** 当前记录等级的说明文案（设计文档 §5.2）。 */
@Composable
private fun recordLevelDescription(level: LogLevel): String = when (level) {
    LogLevel.VERBOSE -> stringResource(R.string.log_record_desc_verbose)
    LogLevel.DEBUG -> stringResource(R.string.log_record_desc_debug)
    LogLevel.INFO -> stringResource(R.string.log_record_desc_info)
    LogLevel.WARN -> stringResource(R.string.log_record_desc_warn)
    LogLevel.ERROR, LogLevel.FATAL -> stringResource(R.string.log_record_desc_error)
    LogLevel.NONE -> stringResource(R.string.log_record_desc_none)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 200)
@Composable
private fun LogRecordLevelRowPreview() {
    com.mini.me_core.core.theme.MiniMeTheme(darkTheme = false) {
        Column(Modifier.padding(Spacing.sm)) {
            LogRecordLevelRow(currentLevel = LogLevel.VERBOSE, onSelectLevel = {})
        }
    }
}
