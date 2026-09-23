package com.mini.logs.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalAppTheme

private val BarShape = RoundedCornerShape(4.dp)

/**
 * Tag 分布图：Top 10 Tag 横向条形图。
 * 每行：Tag名 + 条形(宽度按占比) + 百分比 + 数量。
 */
@Composable
fun TagDistributionChart(tagDistribution: List<Pair<String, Int>>) {
    val colors = LocalAppTheme.current.colors

    if (tagDistribution.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂无 Tag 数据", fontSize = 12.sp, color = colors.textSecondary)
        }
        return
    }

    val total = tagDistribution.sumOf { it.second }.coerceAtLeast(1)
    val max = tagDistribution.maxOf { it.second }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tagDistribution.forEach { (tag, count) ->
            TagBarRow(
                tag = tag,
                count = count,
                percent = count.toDouble() / total * 100,
                fraction = count.toFloat() / max,
            )
        }
    }
}

@Composable
private fun TagBarRow(
    tag: String,
    count: Int,
    percent: Double,
    fraction: Float,
) {
    val colors = LocalAppTheme.current.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = tag,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = colors.textPrimary,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(colors.surfaceSunken, BarShape),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                    .height(10.dp)
                    .background(colors.brandPrimary, BarShape),
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = String.format("%.0f%%", percent),
            fontSize = 11.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(38.dp),
        )
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.width(44.dp),
        )
    }
}
