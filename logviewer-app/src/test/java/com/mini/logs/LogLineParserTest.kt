package com.mini.logs

import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.core.util.LogLineParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LogLineParser] 单元测试。
 */
class LogLineParserTest {

    @Test
    fun `parse normal line extracts level tag message`() {
        val line = "2026-09-23 09:23:45.123 ERROR [McpManager] Connection refused"
        val parsed = LogLineParser.parse(line)
        requireNotNull(parsed)
        assertEquals("2026-09-23", parsed.date)
        assertEquals(LogLevel.ERROR, parsed.level)
        assertEquals("McpManager", parsed.tag)
        assertEquals("Connection refused", parsed.message)
        assertEquals(line, parsed.raw)
        assertNull(parsed.threadName)
        assertTrue(parsed.timestamp > 0)
    }

    @Test
    fun `parse line with thread segment extracts thread name`() {
        val line = "2026-09-23 09:23:45.123 INFO [Container] [thread:main] PRoot started"
        val parsed = LogLineParser.parse(line)
        requireNotNull(parsed)
        assertEquals(LogLevel.INFO, parsed.level)
        assertEquals("Container", parsed.tag)
        assertEquals("main", parsed.threadName)
        assertEquals("PRoot started", parsed.message)
    }

    @Test
    fun `parse legacy format without thread segment is compatible`() {
        val line = "2026-09-23 09:23:45.123 WARN [OldTag] legacy message body"
        val parsed = LogLineParser.parse(line)
        requireNotNull(parsed)
        assertEquals(LogLevel.WARN, parsed.level)
        assertEquals("OldTag", parsed.tag)
        assertEquals("legacy message body", parsed.message)
        assertNull(parsed.threadName)
    }

    @Test
    fun `format header line returns null`() {
        val line = "# MiniMe Log Format v1"
        assertNull(LogLineParser.parse(line))
        assertTrue(LogLineParser.isFormatHeader(line))
        assertEquals(1, LogLineParser.parseFormatVersion(line))
    }

    @Test
    fun `stack trace line returns null`() {
        val line = "at java.net.PlainSocketImpl.socketConnect(Native Method)"
        assertNull(LogLineParser.parse(line))
    }

    @Test
    fun `each level parses correctly`() {
        val cases = listOf(
            "VERBOSE" to LogLevel.VERBOSE,
            "DEBUG" to LogLevel.DEBUG,
            "INFO" to LogLevel.INFO,
            "WARN" to LogLevel.WARN,
            "ERROR" to LogLevel.ERROR,
            "FATAL" to LogLevel.FATAL,
        )
        for ((levelName, expected) in cases) {
            val line = "2026-09-23 09:23:45.123 $levelName [Tag] msg"
            val parsed = LogLineParser.parse(line)
            requireNotNull(parsed) { "failed to parse $levelName" }
            assertEquals(expected, parsed.level)
        }
    }

    @Test
    fun `extractTags dedupes and preserves order`() {
        val lines = listOf(
            "2026-09-23 09:23:45.123 INFO [Alpha] a",
            "2026-09-23 09:23:46.123 INFO [Beta] b",
            "2026-09-23 09:23:47.123 INFO [Alpha] a2",
            "at java.lang.Thread.run(Thread.java:123)",
            "# MiniMe Log Format v1",
            "2026-09-23 09:23:48.123 ERROR [Gamma] c",
        )
        val tags = LogLineParser.extractTags(lines)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), tags)
    }
}
