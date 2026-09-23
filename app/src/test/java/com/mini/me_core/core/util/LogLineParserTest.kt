package com.mini.me_core.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLineParserTest {

    @Test
    fun oldFormat_withoutThread_parses() {
        val line = "2026-09-23 09:23:45.123 INFO [McpManager] Connection refused"
        val p = LogLineParser.parse(line)
        requireNotNull(p)
        assertEquals("2026-09-23", p.date)
        assertEquals(LogLevel.INFO, p.level)
        assertEquals("McpManager", p.tag)
        assertEquals("Connection refused", p.message)
        assertNull(p.threadName)
        assertTrue("timestamp 应被解析为正的 epoch millis", p.timestamp > 0L)
    }

    @Test
    fun newFormat_withThread_parsesThreadName() {
        val line = "2026-09-23 09:23:45.123 ERROR [AIAgent] [thread:DefaultDispatcher-worker-2] retry failed"
        val p = LogLineParser.parse(line)
        requireNotNull(p)
        assertEquals(LogLevel.ERROR, p.level)
        assertEquals("AIAgent", p.tag)
        assertEquals("DefaultDispatcher-worker-2", p.threadName)
        assertEquals("retry failed", p.message)
    }

    @Test
    fun fatalLevel_parsedAsFatal() {
        val line = "2026-09-23 10:00:00.000 FATAL [CRASH] 未捕获异常"
        val p = LogLineParser.parse(line)
        requireNotNull(p)
        assertEquals(LogLevel.FATAL, p.level)
        assertEquals("CRASH", p.tag)
    }

    @Test
    fun verboseAndDebug_levelsParsed() {
        assertEquals(LogLevel.VERBOSE, LogLineParser.parse("2026-09-23 10:00:00.000 VERBOSE [T] m")?.level)
        assertEquals(LogLevel.DEBUG, LogLineParser.parse("2026-09-23 10:00:00.000 DEBUG [T] m")?.level)
        assertEquals(LogLevel.WARN, LogLineParser.parse("2026-09-23 10:00:00.000 WARN [T] m")?.level)
    }

    @Test
    fun stackTraceLine_returnsNull() {
        val line = "\tat java.base/java.lang.Thread.run(Thread.java:1571)"
        assertNull(LogLineParser.parse(line))
    }

    @Test
    fun formatHeaderLine_returnsNullFromParse() {
        val line = "# MiniMe Log Format v1"
        assertNull(LogLineParser.parse(line))
    }

    @Test
    fun isFormatHeader_detectsVersionLine() {
        assertTrue(LogLineParser.isFormatHeader("# MiniMe Log Format v1"))
        assertTrue(LogLineParser.isFormatHeader("# MiniMe Log Format v2"))
        // 其它头部行不是「格式版本头」
        assertEquals(false, LogLineParser.isFormatHeader("# app-version: 0.0.0.12"))
        assertEquals(false, LogLineParser.isFormatHeader("2026-09-23 10:00:00.000 INFO [T] m"))
    }

    @Test
    fun parseFormatVersion_extractsNumber() {
        assertEquals(1, LogLineParser.parseFormatVersion("# MiniMe Log Format v1"))
        assertEquals(2, LogLineParser.parseFormatVersion("# MiniMe Log Format v2"))
        assertNull(LogLineParser.parseFormatVersion("# app-version: 0.0.0.12"))
        assertNull(LogLineParser.parseFormatVersion("not a header"))
    }

    @Test
    fun extractTags_dedupesInOrder() {
        val lines = listOf(
            "# MiniMe Log Format v1",
            "2026-09-23 10:00:00.000 INFO [App] start",
            "2026-09-23 10:00:01.000 DEBUG [Net] connecting",
            "2026-09-23 10:00:02.000 INFO [App] resumed",
            "\tat java.lang.Thread.run(Thread.java:1)",
        )
        assertEquals(listOf("App", "Net"), LogLineParser.extractTags(lines))
    }

    @Test
    fun messageContainingSpaces_preserved() {
        val line = "2026-09-23 10:00:00.000 INFO [T] multi word message with   spaces"
        val p = LogLineParser.parse(line)
        requireNotNull(p)
        assertEquals("multi word message with   spaces", p.message)
    }

    @Test
    fun messageContainingBrackets_preserved() {
        // 消息里出现方括号不应影响 thread 段提取（thread 段紧跟在 tag 后）
        val line = "2026-09-23 10:00:00.000 INFO [T] got [unexpected bracket] inside"
        val p = LogLineParser.parse(line)
        requireNotNull(p)
        assertNull(p.threadName)
        assertEquals("got [unexpected bracket] inside", p.message)
    }
}
