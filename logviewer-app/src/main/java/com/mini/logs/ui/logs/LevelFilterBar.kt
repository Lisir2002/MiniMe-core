package com.mini.logs.ui.logs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.components.AppBadge
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel

/**
 * 等级筛选栏：横向滚动 Chip 组，带数量徽章。
 *
 * 交互：
 * - 单击 = 该等级及以上
 * - 长按 = 多选切换
 * - 再次点击已选中 = 折叠该等级
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LevelFilterBar(
    levelCounts: Map<LogLevel, Int>,
    selectedLevels: Set<LogLevel>,
    collapsedLevels: Set<LogLevel>,
    onLevelClick: (LogLevel) -> Unit,
    onLevelLongClick: (LogLevel) -> Unit,
    onClearFilter: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfacePage)
            .padding(horizontal = PrimitiveSpacing.SmPlus, vertical = PrimitiveSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "全部" chip
        val allSelected = selectedLevels.isEmpty()
        AppChip(
            text = "全部",
            variant = if (allSelected) AppChipVariant.Filled else AppChipVariant.Outlined,
            chipColor = AppChipColor.Primary,
            onClose = if (allSelected) null else ({ onClearFilter() }),
            modifier = Modifier.combinedClickable(
                onClick = { onClearFilter() },
            ),
        )
        Spacer(Modifier.width(PrimitiveSpacing.Xs))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Xs),
        ) {
            items(LogLevelColors.filterOrder) { level ->
                val count = levelCounts[level] ?: 0
                val isSelected = level in selectedLevels
                val isCollapsed = level in collapsedLevels
                val chipColor = when (level) {
                    LogLevel.ERROR, LogLevel.FATAL -> AppChipColor.Error
                    LogLevel.WARN -> AppChipColor.Warning
                    LogLevel.INFO -> AppChipColor.Info
                    else -> AppChipColor.Neutral
                }
                val variant = when {
                    isCollapsed -> AppChipVariant.Outlined
                    isSelected -> AppChipVariant.Filled
                    else -> AppChipVariant.Outlined
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.combinedClickable(
                        onClick = { onLevelClick(level) },
                        onLongClick = { onLevelLongClick(level) },
                    ),
                ) {
                    AppChip(
                        text = LogLevelColors.labelFor(level),
                        variant = variant,
                        chipColor = chipColor,
                    )
                    if (count > 0) {
                        Spacer(Modifier.width(2.dp))
                        AppBadge(
                            text = count.toString(),
                            color = LogLevelColors.colorFor(level).copy(alpha = 0.8f),
                            textColor = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(start = 0.dp),
                        )
                    }
                }
            }
        }
    }
}
