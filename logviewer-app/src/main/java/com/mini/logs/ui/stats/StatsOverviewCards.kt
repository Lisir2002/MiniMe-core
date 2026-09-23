package com.mini.logs.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.LogStatistics
import com.mini.logs.data.StatsCalculator
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 概览卡片行：总行数 / ERROR 数 / 错误率，3 个等宽 Elevated 卡片。
 * 底部显示环比变化（↑绿 / ↓红 / —灰）。
 */
@Composable
fun StatsOverviewCards(stats: LogStatistics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val prevRate = if (stats.previousPeriodTotal > 0) {
            stats.previousPeriodErrors.toDouble() / stats.previousPeriodTotal * 100
        } else 0.0

        OverviewCard(
            modifier = Modifier.weight(1f),
            value = FormatUtils.formatCount(stats.totalLines),
            label = "总行数",
            changePct = StatsCalculator.periodChange(stats.totalLines, stats.previousPeriodTotal),
            changeIsAbsolute = false,
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            value = FormatUtils.formatCount(stats.errorCount),
            label = "ERROR",
            changePct = StatsCalculator.periodChange(stats.errorCount, stats.previousPeriodErrors),
            changeIsAbsolute = false,
        )
        OverviewCard(
            modifier = Modifier.weight(1f),
            value = String.format("%.2f%%", stats.errorRate),
            label = "错误率",
            changePct = stats.errorRate - prevRate,
            changeIsAbsolute = true,
        )
    }
}

@Composable
private fun OverviewCard(
    value: String,
    label: String,
    changePct: Double,
    changeIsAbsolute: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppTheme.current.colors
    AppCard(variant = AppCardVariant.Elevated, modifier = modifier) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(Spacing.xs))
            ChangeBadge(changePct = changePct, absolute = changeIsAbsolute)
        }
    }
}

/** 环比徽标：↑绿 / ↓红 / —灰。 */
@Composable
private fun ChangeBadge(changePct: Double, absolute: Boolean) {
    val colors = LocalAppTheme.current.colors
    val arrow: String
    val color = when {
        changePct > 0.0001 -> { arrow = "↑"; colors.success }
        changePct < -0.0001 -> { arrow = "↓"; colors.error }
        else -> { arrow = "—"; colors.textTertiary }
    }
    val text = if (absolute) {
        // 错误率按百分点展示，保留两位
        "${arrow}${String.format("%.2f", changePct)}pp"
    } else {
        "${arrow}${String.format("%.0f", changePct)}%"
    }
    Text(text = text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
}
