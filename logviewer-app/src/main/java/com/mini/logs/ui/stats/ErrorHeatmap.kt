package com.mini.logs.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
// Calendar.DAY_OF_WEEK: 1=Sun,2=Mon,...,7=Sat。行顺序按周一~周日。
private val CalendarDaysOrder = listOf(2, 3, 4, 5, 6, 7, 1)

/**
 * 错误时段热力图：7 行（周一~周日）× 24 列（0-23 点）。
 * 格子颜色深浅表示该时段 ERROR 数量（5 档色阶），点击显示 tooltip。
 *
 * @param heatmap key = (Calendar.DAY_OF_WEEK, hour) -> error count
 */
@Composable
fun ErrorHeatmapChart(heatmap: Map<Pair<Int, Int>, Int>) {
    val colors = LocalAppTheme.current.colors
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val maxCount = heatmap.values.maxOrNull() ?: 0

    Column {
        // 顶部小时刻度（每 3 小时一个标签）
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(28.dp))
            for (hour in 0 until 24) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (hour % 3 == 0) {
                        Text(
                            text = String.format("%02d", hour),
                            fontSize = 9.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        CalendarDaysOrder.forEachIndexed { rowIndex, calDay ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = DayLabels[rowIndex],
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.width(28.dp),
                )
                for (hour in 0 until 24) {
                    val count = heatmap[calDay to hour] ?: 0
                    val cellColor = heatColor(base = colors.error, count = count, max = maxCount)
                    val isSelected = selected == (calDay to hour)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(0.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cellColor)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) colors.textPrimary else Color.Transparent,
                                shape = RoundedCornerShape(2.dp),
                            )
                            .clickable { selected = calDay to hour },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))
        // tooltip / 说明
        val sel = selected
        Text(
            text = if (sel != null) {
                val (calDay, hour) = sel
                val dayIndex = CalendarDaysOrder.indexOf(calDay)
                val count = heatmap[sel] ?: 0
                "周${DayLabels[dayIndex]} ${String.format("%02d:00", hour)} · $count 条 ERROR"
            } else {
                "点击格子查看该时段错误数"
            },
            fontSize = 11.sp,
            color = colors.textSecondary,
        )
    }
}

/** 根据数量计算格子颜色（5 档色阶）。 */
private fun heatColor(base: Color, count: Int, max: Int): Color {
    if (count <= 0 || max <= 0) return Color.Transparent
    // 归一化到 0..1，分 5 档
    val ratio = count.toFloat() / max.toFloat()
    val tier = (ratio * 4).toInt().coerceIn(0, 4)
    val alpha = 0.15f + tier * 0.2f // 0.15 / 0.35 / 0.55 / 0.75 / 0.95
    return base.copy(alpha = alpha)
}
