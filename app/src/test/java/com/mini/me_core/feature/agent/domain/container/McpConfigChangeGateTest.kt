package com.mini.me_core.feature.agent.domain.container

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [McpConfigChangeGate] 单测：容器侧 mcp.json 变更默认只标记「待确认」，
 * 首次基线不报警、未变更不报警、内容被改才报警（宿主不得自动热加载）。
 */
class McpConfigChangeGateTest {

    private val path = "/data/data/x/files/minime/mcp.json"

    @Test
    fun `first sight adopts baseline without pending`() {
        assertFalse(McpConfigChangeGate.shouldFlagPending(null, path, 1000L))
    }

    @Test
    fun `unchanged config not flagged`() {
        val base = McpConfigChangeGate.Snapshot(path, 1000L)
        assertFalse(McpConfigChangeGate.shouldFlagPending(base, path, 1000L))
    }

    @Test
    fun `mtime change flagged pending`() {
        val base = McpConfigChangeGate.Snapshot(path, 1000L)
        assertTrue(McpConfigChangeGate.shouldFlagPending(base, path, 2000L))
    }

    @Test
    fun `different file path not flagged (new file = adopt)`() {
        val base = McpConfigChangeGate.Snapshot(path, 1000L)
        assertFalse(McpConfigChangeGate.shouldFlagPending(base, "/other/mcp.json", 2000L))
    }
}
