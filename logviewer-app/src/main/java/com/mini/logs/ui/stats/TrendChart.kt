package com.mini.logs.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.DefaultPointConnector
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.mini.me_core.core.util.LogLevel

/**
 * 日志等级趋势折线图（Vico LineChart）。
 * X 轴：按小时分桶（"yyyy-MM-dd HH"），Y 轴：数量。
 * 三条线：ERROR=红 / WARN=橙 / INFO=蓝，图例在底部。
 */
@Composable
fun LevelTrendChart(hourlyTrend: Map<String, Map<LogLevel, Int>>) {
    val colors = LocalAppTheme.current.colors
    val sortedKeys = remember(hourlyTrend) { hourlyTrend.keys.sorted() }

    if (sortedKeys.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无趋势数据", fontSize = 12.sp, color = colors.textSecondary)
        }
        return
    }

    val model = remember(hourlyTrend, sortedKeys) {
        val errorSeries = sortedKeys.mapIndexed { idx, key ->
            val m = hourlyTrend[key].orEmpty()
            val v = (m[LogLevel.ERROR] ?: 0) + (m[LogLevel.FATAL] ?: 0)
            FloatEntry(idx.toFloat(), v.toFloat())
        }
        val warnSeries = sortedKeys.mapIndexed { idx, key ->
            FloatEntry(idx.toFloat(), (hourlyTrend[key]?.get(LogLevel.WARN) ?: 0).toFloat())
        }
        val infoSeries = sortedKeys.mapIndexed { idx, key ->
            FloatEntry(idx.toFloat(), (hourlyTrend[key]?.get(LogLevel.INFO) ?: 0).toFloat())
        }
        entryModelOf(errorSeries, warnSeries, infoSeries)
    }

    Column {
        Chart(
            model = model,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            chart = lineChart(
                lines = listOf(
                    lineSpec(lineColor = colors.error, pointConnector = DefaultPointConnector()),
                    lineSpec(lineColor = colors.warning, pointConnector = DefaultPointConnector()),
                    lineSpec(lineColor = colors.info, pointConnector = DefaultPointConnector()),
                ),
            ),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = AxisValueFormatter { x, _ ->
                    val idx = x.toInt()
                    // key 形如 "2025-09-23 14"，展示为 "MM-dd HH:00"
                    sortedKeys.getOrNull(idx)?.let { "${it.substring(5)}:00" } ?: ""
                }
            ),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(colors.error, "ERROR")
            Spacer(Modifier.width(Spacing.lg))
            LegendDot(colors.warning, "WARN")
            Spacer(Modifier.width(Spacing.lg))
            LegendDot(colors.info, "INFO")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(label, fontSize = 11.sp, color = color)
    }
}
