package com.mini.logs

import com.mini.logs.data.LogEntry
import com.mini.logs.data.StatsCalculator
import com.mini.me_core.core.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StatsCalculator] 单元测试。
 */
class StatsCalculatorTest {

    private fun mainLine(level: LogLevel, tag: String) = LogEntry(
        rawLine = "msg",
        level = level,
        tag = tag,
        message = "msg",
    )

    @Test
    fun `level counts are correct`() {
        val entries = listOf(
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "B"),
            mainLine(LogLevel.ERROR, "A"),
            mainLine(LogLevel.WARN, "C"),
        )
        val stats = StatsCalculator.calculate(entries)
        assertEquals(5, stats.totalLines)
        assertEquals(3, stats.levelCounts[LogLevel.INFO])
        assertEquals(1, stats.levelCounts[LogLevel.ERROR])
        assertEquals(1, stats.levelCounts[LogLevel.WARN])
        assertEquals(1, stats.errorCount)
    }

    @Test
    fun `tag distribution is sorted by count descending`() {
        val entries = listOf(
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "B"),
            mainLine(LogLevel.INFO, "B"),
            mainLine(LogLevel.INFO, "C"),
        )
        val stats = StatsCalculator.calculate(entries)
        val tags = stats.tagDistribution.map { it.first }
        assertEquals(listOf("A", "B", "C"), tags)
        assertEquals(3, stats.tagDistribution[0].second)
    }

    @Test
    fun `error rate equals error over total times 100`() {
        val entries = listOf(
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.INFO, "A"),
            mainLine(LogLevel.ERROR, "B"),
        )
        val stats = StatsCalculator.calculate(entries)
        // 1 error / 5 total * 100 = 20.0
        assertEquals(20.0, stats.errorRate, 0.0001)
    }

    @Test
    fun `empty list returns zero values`() {
        val stats = StatsCalculator.calculate(emptyList())
        assertEquals(0, stats.totalLines)
        assertEquals(0, stats.errorCount)
        assertEquals(0.0, stats.errorRate, 0.0001)
        assertTrue(stats.tagDistribution.isEmpty())
        assertTrue(stats.levelCounts.isEmpty())
    }

    @Test
    fun `period change is computed correctly`() {
        assertEquals(50.0, StatsCalculator.periodChange(150, 100), 0.0001)
        assertEquals(0.0, StatsCalculator.periodChange(100, 100), 0.0001)
        assertEquals(-100.0, StatsCalculator.periodChange(0, 100), 0.0001)
        // 上期为 0 且本期 > 0 → 100%
        assertEquals(100.0, StatsCalculator.periodChange(100, 0), 0.0001)
        // 两期均为 0 → 0%
        assertEquals(0.0, StatsCalculator.periodChange(0, 0), 0.0001)
    }
}
