package com.mini.logs.ui.logs

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.components.AppChip
import com.mini.me_core.core.theme.components.AppChipColor
import com.mini.me_core.core.theme.components.AppChipVariant
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel

/**
 * 等级筛选栏：横向滚动 Chip 组，每个 chip 显示完整等级名 + 计数。
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
                val levelColor = LogLevelColors.colorFor(level)

                // 选中=实心底；折叠=描边；未选=浅底
                val (containerColor, contentColor, border) = when {
                    isCollapsed -> Triple(
                        Color.Transparent,
                        levelColor,
                        BorderStroke(PrimitiveSpacing.Hairline, levelColor),
                    )
                    isSelected -> Triple(levelColor, Color.White, null)
                    else -> Triple(
                        LogLevelColors.containerColorFor(level),
                        levelColor,
                        null,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(PrimitiveRadius.Md),
                    color = containerColor,
                    border = border,
                    modifier = Modifier.combinedClickable(
                        onClick = { onLevelClick(level) },
                        onLongClick = { onLevelLongClick(level) },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = PrimitiveSpacing.MdPlus,
                            vertical = PrimitiveSpacing.Xs,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = LogLevelColors.displayName(level),
                            color = contentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        if (count > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = count.toString(),
                                color = contentColor.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
