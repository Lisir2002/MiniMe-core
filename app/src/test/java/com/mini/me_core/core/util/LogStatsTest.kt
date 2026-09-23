package com.mini.me_core.core.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogStatsTest {

    @After
    fun tearDown() {
        LogStats.reset()
    }

    @Test
    fun increment_levelCounts() {
        LogStats.increment(LogLevel.ERROR, "McpManager")
        LogStats.increment(LogLevel.ERROR, "Other")
        LogStats.increment(LogLevel.WARN, "McpManager")

        val levels = LogStats.getLevelCounts()
        assertEquals(2, levels[LogLevel.ERROR])
        assertEquals(1, levels[LogLevel.WARN])
        assertFalse(levels.containsKey(LogLevel.INFO))
    }

    @Test
    fun increment_tagCounts() {
        LogStats.increment(LogLevel.ERROR, "McpManager")
        LogStats.increment(LogLevel.INFO, "McpManager")
        LogStats.increment(LogLevel.DEBUG, "AIAgent")

        val tags = LogStats.getTagCounts()
        assertEquals(2, tags["McpManager"])
        assertEquals(1, tags["AIAgent"])
    }

    @Test
    fun reset_clearsCounts() {
        LogStats.increment(LogLevel.ERROR, "X")
        LogStats.reset()
        assertEquals(0, LogStats.getLevelCounts().size)
        assertEquals(0, LogStats.getTagCounts().size)
    }

    @Test
    fun getCounts_returnSnapshot_notLiveView() {
        LogStats.increment(LogLevel.ERROR, "X")
        val snapshot = LogStats.getLevelCounts()
        // 拿到快照后再写，不应影响快照
        LogStats.increment(LogLevel.ERROR, "X")
        assertEquals(1, snapshot[LogLevel.ERROR])
        assertEquals(2, LogStats.getLevelCounts()[LogLevel.ERROR])
    }

    @Test
    fun concurrentIncrements_threadSafe() {
        val threads = (1..8).map { t ->
            Thread {
                repeat(1000) { LogStats.increment(LogLevel.DEBUG, "tag$t") }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(8000, LogStats.getLevelCounts()[LogLevel.DEBUG])
        assertEquals(8, LogStats.getTagCounts().size)
    }
}
