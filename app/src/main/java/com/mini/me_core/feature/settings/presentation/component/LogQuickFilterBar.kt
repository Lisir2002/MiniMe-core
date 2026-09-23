package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel

/**
 * 控制栏第二行：显示筛选常驻栏（带数量徽章）。
 *
 * - 「全部」chip：选中表示不按等级过滤；点击清除所有等级筛选。
 * - 各等级 chip：显示该等级行数徽章；点击快速过滤到该等级，再次点击折叠该等级整组行。
 * 设计文档 §5.3。
 *
 * @param onSelectAll 点击「全部」
 * @param onLevelClick 点击某等级 chip（由父组件决定是过滤还是折叠）
 */
@Composable
fun LogQuickFilterBar(
    levelCounts: Map<LogLevel, Int>,
    selectedLevels: Set<LogLevel>,
    collapsedLevels: Set<LogLevel>,
    onSelectAll: () -> Unit,
    onLevelClick: (LogLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
    ) {
        val total = levelCounts.values.sum()
        FilterChip(
            selected = selectedLevels.isEmpty(),
            onClick = onSelectAll,
            label = {
                Row {
                    Text(stringResource(R.string.log_filter_all))
                    Text(
                        text = " $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        // 仅展示有日志的等级（ERROR/WARN/INFO/DEBUG/VERBOSE），按严重程度倒序
        val levels = listOf(LogLevel.ERROR, LogLevel.WARN, LogLevel.INFO, LogLevel.DEBUG, LogLevel.VERBOSE)
        levels.forEach { level ->
            val count = levelCounts[level] ?: 0
            val collapsed = level in collapsedLevels
            FilterChip(
                selected = level in selectedLevels,
                onClick = { onLevelClick(level) },
                label = {
                    Row {
                        Text(level.name + if (collapsed) " ▼" else "")
                        Text(
                            text = " $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 380, heightDp = 80)
@Composable
private fun LogQuickFilterBarPreview() {
    com.mini.me_core.core.theme.AIEditorTheme(darkTheme = false) {
        Row(Modifier.padding(PrimitiveSpacing.Sm)) {
            LogQuickFilterBar(
                levelCounts = mapOf(LogLevel.ERROR to 5, LogLevel.WARN to 23, LogLevel.INFO to 156, LogLevel.DEBUG to 40),
                selectedLevels = setOf(LogLevel.ERROR),
                collapsedLevels = emptySet(),
                onSelectAll = {},
                onLevelClick = {},
            )
        }
    }
}
