package com.mini.logs

import com.mini.logs.data.FilterState
import com.mini.logs.data.LogEntry
import com.mini.me_core.core.util.LogLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FilterState.matches] 单元测试。
 */
class FilterStateTest {

    private fun mainEntry(
        level: LogLevel,
        tag: String,
        rawLine: String = "message",
    ) = LogEntry(
        rawLine = rawLine,
        level = level,
        tag = tag,
        message = rawLine,
    )

    private fun stackEntry() = LogEntry(
        rawLine = "at java.net.Socket.connect(Socket.java:50)",
        level = null,
        tag = "",
        message = "at java.net.Socket.connect(Socket.java:50)",
        isStackTraceLine = true,
    )

    @Test
    fun `level filter matches selected level`() {
        val state = FilterState(selectedLevels = setOf(LogLevel.ERROR))
        assertTrue(state.matches(mainEntry(LogLevel.ERROR, "A")))
        assertFalse(state.matches(mainEntry(LogLevel.INFO, "A")))
    }

    @Test
    fun `tag filter matches selected tag`() {
        val state = FilterState(selectedTags = setOf("McpManager"))
        assertTrue(state.matches(mainEntry(LogLevel.INFO, "McpManager")))
        assertFalse(state.matches(mainEntry(LogLevel.INFO, "Container")))
    }

    @Test
    fun `excluded tag is filtered out`() {
        val state = FilterState(excludedTags = setOf("Noise"))
        assertTrue(state.matches(mainEntry(LogLevel.INFO, "McpManager")))
        assertFalse(state.matches(mainEntry(LogLevel.INFO, "Noise")))
    }

    @Test
    fun `search query matches case insensitively`() {
        val state = FilterState(searchQuery = "connection")
        assertTrue(state.matches(mainEntry(LogLevel.ERROR, "A", "Connection refused")))
        assertTrue(state.matches(mainEntry(LogLevel.ERROR, "A", "CONNECTION reset")))
        assertFalse(state.matches(mainEntry(LogLevel.ERROR, "A", "timeout")))
    }

    @Test
    fun `combined level tag and search filters`() {
        val state = FilterState(
            selectedLevels = setOf(LogLevel.ERROR),
            selectedTags = setOf("Net"),
            searchQuery = "refused",
        )
        assertTrue(
            state.matches(mainEntry(LogLevel.ERROR, "Net", "Connection refused"))
        )
        // 等级不符
        assertFalse(state.matches(mainEntry(LogLevel.INFO, "Net", "Connection refused")))
        // Tag 不符
        assertFalse(state.matches(mainEntry(LogLevel.ERROR, "Other", "Connection refused")))
        // 搜索词不符
        assertFalse(state.matches(mainEntry(LogLevel.ERROR, "Net", "timeout")))
    }

    @Test
    fun `stack trace line always passes regardless of filters`() {
        val state = FilterState(
            selectedLevels = setOf(LogLevel.ERROR),
            selectedTags = setOf("Net"),
            searchQuery = "refused",
        )
        assertTrue(state.matches(stackEntry()))
    }

    @Test
    fun `empty filter state matches all main lines`() {
        val state = FilterState()
        assertTrue(state.matches(mainEntry(LogLevel.VERBOSE, "Any", "anything")))
        assertTrue(state.matches(mainEntry(LogLevel.FATAL, "Any", "anything")))
        assertTrue(state.matches(stackEntry()))
    }
}
