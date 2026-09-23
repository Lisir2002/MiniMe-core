package com.mini.logs.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.LogRepository
import com.mini.logs.data.LogStatistics
import com.mini.logs.data.StatsCalculator
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/**
 * 统计分析页。顶栏时间范围切换，内容：概览卡片 / 等级趋势 / Tag 分布 / 错误热力图。
 */
@Composable
fun StatsScreen() {
    val colors = LocalAppTheme.current.colors
    val context = LocalContext.current
    val repository = remember { LogRepository(context) }
    var range by remember { mutableStateOf(StatsRange.LAST_7_DAYS) }
    var stats by remember { mutableStateOf<LogStatistics?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(range) {
        isLoading = true
        val files = repository.listLogFiles()
        val (currentFiles, prevFiles) = selectFilesForRange(files, range)
        val currentEntries = repository.loadEntries(currentFiles, maxLines = 60_000)
        val prevEntries = repository.loadEntries(prevFiles, maxLines = 60_000)
        stats = StatsCalculator.calculate(currentEntries, prevEntries)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage),
    ) {
        AppTopAppBar(
            title = "统计分析",
            actions = {
                RangeDropdown(range = range, onRangeChange = { range = it })
            },
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brandPrimary)
            }

            stats == null || stats!!.totalLines == 0 -> AppEmptyState(
                title = "暂无日志数据",
                subtitle = "在「日志」页产生日志后即可查看统计",
                icon = Icons.Rounded.BarChart,
                modifier = Modifier.fillMaxSize(),
            )

            else -> StatsContent(stats = stats!!)
        }
    }
}

@Composable
private fun StatsContent(stats: LogStatistics) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item { StatsOverviewCards(stats) }

        item {
            AppCard(variant = AppCardVariant.Default) {
                Column(Modifier.padding(Spacing.md)) {
                    AppSectionHeader(title = "日志等级趋势")
                    Spacer(Modifier.height(Spacing.sm))
                    LevelTrendChart(hourlyTrend = stats.hourlyTrend)
                }
            }
        }

        item {
            AppCard(variant = AppCardVariant.Default) {
                Column(Modifier.padding(Spacing.md)) {
                    AppSectionHeader(title = "Tag 分布（Top 10）")
                    Spacer(Modifier.height(Spacing.sm))
                    TagDistributionChart(tagDistribution = stats.tagDistribution)
                }
            }
        }

        item {
            AppCard(variant = AppCardVariant.Default) {
                Column(Modifier.padding(Spacing.md)) {
                    AppSectionHeader(title = "错误时段热力图")
                    Spacer(Modifier.height(Spacing.sm))
                    ErrorHeatmapChart(heatmap = stats.errorHeatmap)
                }
            }
        }
    }
}

/** 顶栏右侧时间范围下拉。 */
@Composable
private fun RangeDropdown(range: StatsRange, onRangeChange: (StatsRange) -> Unit) {
    val colors = LocalAppTheme.current.colors
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Rounded.DateRange,
            contentDescription = null,
            tint = colors.textSecondary,
        )
        Text(
            text = range.label,
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs),
        )
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "选择时间范围",
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatsRange.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (option == range) colors.brandPrimary else colors.textPrimary,
                        )
                    },
                    onClick = {
                        onRangeChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
