package com.mini.logs.data

import com.mini.me_core.core.util.LogLevel

/**
 * 统计计算结果。
 */
data class LogStatistics(
    val totalLines: Int,
    val levelCounts: Map<LogLevel, Int>,
    val errorCount: Int,
    val errorRate: Double,
    val tagDistribution: List<Pair<String, Int>>,
    val hourlyTrend: Map<String, Map<LogLevel, Int>>, // "yyyy-MM-dd HH" -> level -> count
    val errorHeatmap: Map<Pair<Int, Int>, Int>, // (dayOfWeek 1-7, hour 0-23) -> error count
    val previousPeriodTotal: Int,
    val previousPeriodErrors: Int,
)

/**
 * 日志统计计算器。
 */
object StatsCalculator {

    /**
     * 计算日志统计。
     *
     * @param entries 当前时间范围的日志条目
     * @param previousEntries 上一个相同时间范围的条目（用于环比）
     */
    fun calculate(entries: List<LogEntry>, previousEntries: List<LogEntry> = emptyList()): LogStatistics {
        val mainLines = entries.filter { it.isMainLine }

        // 等级统计
        val levelCounts = mutableMapOf<LogLevel, Int>()
        for (entry in mainLines) {
            val level = entry.level ?: continue
            levelCounts[level] = levelCounts.getOrDefault(level, 0) + 1
        }

        val totalLines = mainLines.size
        val errorCount = levelCounts.getOrDefault(LogLevel.ERROR, 0) +
                levelCounts.getOrDefault(LogLevel.FATAL, 0)
        val errorRate = if (totalLines > 0) errorCount.toDouble() / totalLines * 100 else 0.0

        // Tag 分布（Top 10）
        val tagCounts = mutableMapOf<String, Int>()
        for (entry in mainLines) {
            if (entry.tag.isNotEmpty()) {
                tagCounts[entry.tag] = tagCounts.getOrDefault(entry.tag, 0) + 1
            }
        }
        val tagDistribution = tagCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        // 小时趋势：按 "yyyy-MM-dd HH" 分组
        val hourlyTrend = mutableMapOf<String, MutableMap<LogLevel, Int>>()
        for (entry in mainLines) {
            if (entry.timestamp <= 0) continue
            val key = entry.date + " " + entry.time.substring(0, 2).padStart(2, '0')
            val hourMap = hourlyTrend.getOrPut(key) { mutableMapOf() }
            val level = entry.level ?: continue
            hourMap[level] = hourMap.getOrDefault(level, 0) + 1
        }

        // 错误热力图：(dayOfWeek, hour) -> error count
        val errorHeatmap = mutableMapOf<Pair<Int, Int>, Int>()
        val cal = java.util.Calendar.getInstance()
        for (entry in mainLines) {
            if (entry.level != LogLevel.ERROR && entry.level != LogLevel.FATAL) continue
            if (entry.timestamp <= 0) continue
            cal.timeInMillis = entry.timestamp
            val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sunday ... 7=Saturday
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val key = dayOfWeek to hour
            errorHeatmap[key] = errorHeatmap.getOrDefault(key, 0) + 1
        }

        // 环比
        val prevMain = previousEntries.filter { it.isMainLine }
        val previousPeriodTotal = prevMain.size
        val previousPeriodErrors = prevMain.count {
            it.level == LogLevel.ERROR || it.level == LogLevel.FATAL
        }

        return LogStatistics(
            totalLines = totalLines,
            levelCounts = levelCounts,
            errorCount = errorCount,
            errorRate = errorRate,
            tagDistribution = tagDistribution,
            hourlyTrend = hourlyTrend,
            errorHeatmap = errorHeatmap,
            previousPeriodTotal = previousPeriodTotal,
            previousPeriodErrors = previousPeriodErrors,
        )
    }

    /** 计算环比变化百分比。 */
    fun periodChange(current: Int, previous: Int): Double {
        if (previous == 0) return if (current > 0) 100.0 else 0.0
        return (current - previous).toDouble() / previous * 100
    }
}
