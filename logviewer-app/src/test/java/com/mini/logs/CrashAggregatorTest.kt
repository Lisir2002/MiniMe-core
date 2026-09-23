package com.mini.logs

import com.mini.logs.data.CrashAggregator
import com.mini.logs.data.LogEntry
import com.mini.me_core.core.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CrashAggregator] 单元测试。
 */
class CrashAggregatorTest {

    private fun errorEntry(message: String, tag: String, timestamp: Long) = LogEntry(
        rawLine = "2026-09-23 09:23:45.123 ERROR [$tag] $message",
        timestamp = timestamp,
        level = LogLevel.ERROR,
        tag = tag,
        message = message,
    )

    private fun stackLine(text: String) = LogEntry(
        rawLine = text,
        level = null,
        tag = "",
        message = text,
        isStackTraceLine = true,
    )

    @Test
    fun `single error is aggregated into one group`() {
        val entries = listOf(
            errorEntry("java.net.ConnectException: Connection refused", "McpManager", 1000L),
            stackLine("at java.net.PlainSocketImpl.socketConnect(Native Method)"),
        )
        val groups = CrashAggregator.aggregate(entries)
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].occurrences)
        assertEquals("java.net.ConnectException", groups[0].exceptionType)
        assertTrue(groups[0].stackTraceFirstLine.startsWith("java.net.PlainSocketImpl"))
    }

    @Test
    fun `same exception and same stack first line are merged`() {
        val entries = listOf(
            errorEntry("java.net.ConnectException: Connection refused", "McpManager", 1000L),
            stackLine("at java.net.PlainSocketImpl.socketConnect(Native Method)"),
            errorEntry("java.net.ConnectException: Connection refused again", "McpManager", 2000L),
            stackLine("at java.net.PlainSocketImpl.socketConnect(Native Method)"),
        )
        val groups = CrashAggregator.aggregate(entries)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].occurrences)
    }

    @Test
    fun `same exception different stack first line are separated`() {
        val entries = listOf(
            errorEntry("java.io.IOException: stream closed", "Net", 1000L),
            stackLine("at java.io.FileInputStream.read(Native Method)"),
            errorEntry("java.io.IOException: broken pipe", "Net", 2000L),
            stackLine("at java.net.SocketOutputStream.socketWrite(SocketOutputStream.java:100)"),
        )
        val groups = CrashAggregator.aggregate(entries)
        assertEquals(2, groups.size)
    }

    @Test
    fun `no error returns empty list`() {
        val entries = listOf(
            LogEntry(rawLine = "hello", level = LogLevel.INFO, tag = "T", message = "hello"),
        )
        assertTrue(CrashAggregator.aggregate(entries).isEmpty())
    }

    @Test
    fun `groups are sorted by occurrences descending`() {
        val entries = mutableListOf<LogEntry>()
        // 1 occurrence of RuntimeException
        entries += errorEntry("java.lang.RuntimeException: boom", "A", 1000L)
        entries += stackLine("at com.app.Main.run(Main.java:1)")
        // 3 occurrences of ConnectException
        repeat(3) { i ->
            entries += errorEntry("java.net.ConnectException: refused", "B", 2000L + i)
            entries += stackLine("at java.net.Socket.connect(Socket.java:50)")
        }
        val groups = CrashAggregator.aggregate(entries)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].occurrences)
        assertEquals("java.net.ConnectException", groups[0].exceptionType)
        assertEquals(1, groups[1].occurrences)
    }
}
