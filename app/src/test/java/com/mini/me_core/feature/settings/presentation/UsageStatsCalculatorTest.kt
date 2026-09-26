package com.mini.me_core.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [computeUsageStats] 纯逻辑单测：统计聚合、重置纪元过滤、活跃天数去重。
 */
class UsageStatsCalculatorTest {

    @Test
    fun `empty sessions yields zeros`() {
        val stats = computeUsageStats(
            sessions = emptyList(),
            messageCount = 0,
            resetEpochMs = 0L,
        )
        assertEquals(0, stats.totalSessions)
        assertEquals(0, stats.totalMessages)
        assertEquals(0L, stats.totalInputTokens)
        assertEquals(0L, stats.totalOutputTokens)
        assertEquals(0L, stats.firstUsedMs)
        assertEquals(0, stats.activeDays)
    }

    @Test
    fun `sums tokens and counts sessions and messages`() {
        val sessions = listOf(
            SessionCountInput(createdAtMs = 1_000L, inputTokens = 100, outputTokens = 50),
            SessionCountInput(createdAtMs = 2_000L, inputTokens = 200, outputTokens = 80),
            SessionCountInput(createdAtMs = 3_000L, inputTokens = 50, outputTokens = 200),
        )
        val stats = computeUsageStats(sessions, messageCount = 42, resetEpochMs = 0L)
        assertEquals(3, stats.totalSessions)
        assertEquals(42, stats.totalMessages)
        assertEquals(350L, stats.totalInputTokens)
        assertEquals(330L, stats.totalOutputTokens)
        assertEquals(1_000L, stats.firstUsedMs)
    }

    @Test
    fun `active days dedupes by UTC day bucket`() {
        // 两个会话落在同一天（UTC），应只算 1 个活跃日
        val day1 = utcStartOfDay(2024, 1, 15, 10, 0)
        val day1OtherTime = utcStartOfDay(2024, 1, 15, 23, 0)
        val day2 = utcStartOfDay(2024, 2, 3, 8, 0)
        val sessions = listOf(
            SessionCountInput(day1, 1, 1),
            SessionCountInput(day1OtherTime, 1, 1),
            SessionCountInput(day2, 1, 1),
        )
        val stats = computeUsageStats(sessions, messageCount = 0, resetEpochMs = 0L)
        assertEquals(2, stats.activeDays)
    }

    @Test
    fun `reset epoch filters old sessions upstream is honored`() {
        // computeUsageStats 假定上层已按 resetEpoch 过滤；这里验证传入空列表时归零。
        val stats = computeUsageStats(
            sessions = emptyList(),
            messageCount = 0,
            resetEpochMs = 9_999L,
        )
        assertEquals(0, stats.totalSessions)
        assertEquals(0L, stats.firstUsedMs)
        assertTrue(stats.activeDays == 0)
    }

    private fun utcStartOfDay(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
