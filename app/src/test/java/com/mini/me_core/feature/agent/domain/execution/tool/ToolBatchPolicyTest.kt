package com.mini.me_core.feature.agent.domain.execution.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C2 批量执行策略单测（批次 C：并行调用强化）。
 * 覆盖 batch 上限分 chunk、写操作同层串行判定、partial failure 依赖失败跳过。
 */
class ToolBatchPolicyTest {

    private class FakeTool(
        override val name: String,
        override val capabilities: Set<ToolCapability> = emptySet(),
        override val dependsOn: Set<String> = emptySet()
    ) : AgentTool() {
        override val description: String = "fake"
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("ok"))
        }
    }

    private fun call(id: String, name: String = "read") = ToolCall(id = id, name = name, arguments = emptyMap())

    private fun registryOf(vararg tools: AgentTool): ToolRegistry {
        val registry = ToolRegistry()
        tools.forEach { registry.register(it.name, it) }
        return registry
    }

    // —— chunkCalls：batch 上限 8 ——

    @Test
    fun chunkCalls_empty_returnsEmpty() {
        assertTrue(ToolBatchPolicy.chunkCalls(emptyList()).isEmpty())
    }

    @Test
    fun chunkCalls_atCap_returnsSingleChunk() {
        val calls = (1..8).map { call("c$it") }
        val chunks = ToolBatchPolicy.chunkCalls(calls)
        assertEquals(1, chunks.size)
        assertEquals(8, chunks.first().size)
    }

    @Test
    fun chunkCalls_overCap_splitsByEightPreservingOrder() {
        val calls = (1..10).map { call("c$it") }
        val chunks = ToolBatchPolicy.chunkCalls(calls)
        assertEquals(2, chunks.size)
        assertEquals(8, chunks[0].size)
        assertEquals(2, chunks[1].size)
        // 顺序保持：chunk 拼接后与原列表一致
        assertEquals(calls.map { it.id }, chunks.flatten().map { it.id })
    }

    @Test
    fun chunkCalls_exactMultiple_noEmptyTrailingChunk() {
        val calls = (1..16).map { call("c$it") }
        val chunks = ToolBatchPolicy.chunkCalls(calls)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.isNotEmpty() })
    }

    // —— hasWriteCapability / layerRequiresSerialization ——

    @Test
    fun hasWriteCapability_writeWorkspace_true() {
        assertTrue(ToolBatchPolicy.hasWriteCapability(FakeTool("editFile", setOf(ToolCapability.WRITE_WORKSPACE))))
    }

    @Test
    fun hasWriteCapability_executeCommands_true() {
        assertTrue(ToolBatchPolicy.hasWriteCapability(FakeTool("Bash", setOf(ToolCapability.EXECUTE_COMMANDS))))
    }

    @Test
    fun hasWriteCapability_readOnly_false() {
        assertFalse(ToolBatchPolicy.hasWriteCapability(FakeTool("readFile", setOf(ToolCapability.READ_WORKSPACE))))
    }

    @Test
    fun hasWriteCapability_unregisteredTool_false() {
        assertFalse(ToolBatchPolicy.hasWriteCapability(null))
    }

    @Test
    fun hasWriteCapability_otherCaps_false() {
        assertFalse(
            ToolBatchPolicy.hasWriteCapability(
                FakeTool("websearch", setOf(ToolCapability.NETWORK_READ, ToolCapability.READ_AGENT_CONFIG))
            )
        )
    }

    @Test
    fun layerRequiresSerialization_containsWriteCall_true() {
        val registry = registryOf(
            FakeTool("readFile", setOf(ToolCapability.READ_WORKSPACE)),
            FakeTool("Bash", setOf(ToolCapability.EXECUTE_COMMANDS))
        )
        val layer = listOf(call("c1", "readFile"), call("c2", "Bash"))
        assertTrue(ToolBatchPolicy.layerRequiresSerialization(layer, registry))
    }

    @Test
    fun layerRequiresSerialization_allReadCalls_false() {
        val registry = registryOf(
            FakeTool("readFile", setOf(ToolCapability.READ_WORKSPACE)),
            FakeTool("search", setOf(ToolCapability.READ_WORKSPACE))
        )
        val layer = listOf(call("c1", "readFile"), call("c2", "search"))
        assertFalse(ToolBatchPolicy.layerRequiresSerialization(layer, registry))
    }

    @Test
    fun layerRequiresSerialization_unregisteredCall_false() {
        val registry = registryOf(FakeTool("readFile", setOf(ToolCapability.READ_WORKSPACE)))
        val layer = listOf(call("c1", "mystery_tool"))
        assertFalse(ToolBatchPolicy.layerRequiresSerialization(layer, registry))
    }

    // —— failedDependency：partial failure 跳过判定 ——

    @Test
    fun failedDependency_noDeps_returnsNull() {
        val c = call("c1")
        assertNull(ToolBatchPolicy.failedDependency(c, emptyMap()) { false })
    }

    @Test
    fun failedDependency_depSucceeded_returnsNull() {
        val c = call("c2")
        val deps = mapOf("c2" to setOf("c1"))
        assertNull(ToolBatchPolicy.failedDependency(c, deps) { it == "c1" && false })
    }

    @Test
    fun failedDependency_depFailed_returnsFailedDepId() {
        val c = call("c2")
        val deps = mapOf("c2" to setOf("c1"))
        assertEquals("c1", ToolBatchPolicy.failedDependency(c, deps) { it == "c1" })
    }

    @Test
    fun failedDependency_multipleDeps_returnsFirstFailed() {
        val c = call("c3")
        val deps = mapOf("c3" to setOf("c1", "c2"))
        assertEquals("c2", ToolBatchPolicy.failedDependency(c, deps) { it == "c2" })
    }

    @Test
    fun failedDependency_allDepsSucceeded_returnsNull() {
        val c = call("c3")
        val deps = mapOf("c3" to setOf("c1", "c2"))
        assertNull(ToolBatchPolicy.failedDependency(c, deps) { false })
    }
}
