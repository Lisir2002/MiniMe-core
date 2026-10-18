package com.mini.me_core.feature.agent.domain.execution.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2：[ToolRegistry.version] 变更信号的单测。
 *
 * 这个信号是「统一能力页能实时反映工具增减」的**唯一**依赖——旧聚合层在构造期读一次
 * `getAvailableTools()`存成普通 List，远端 MCP server 连接/断开后页面永不刷新。
 * 因此这里重点验证两件容易写错的事：
 * 1. 真正的增删一定推进版本号（否则 UI 不刷新，等于没修）；
 * 2. 重复注册同一工具**不**推进版本号（否则每次 server 重连都让整页重组，白烧性能）。
 */
class ToolRegistryVersionTest {

    private class FakeTool(override val name: String) : AgentTool() {
        override val description: String = "fake"
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(
            args: Map<String, kotlinx.serialization.json.JsonElement>
        ): ToolResult = ToolResult.Success(kotlinx.serialization.json.JsonPrimitive("ok"))
    }

    @Test
    fun `initial version is zero`() {
        assertEquals(0L, ToolRegistry().version.value)
    }

    @Test
    fun `registering a new tool bumps version`() {
        val registry = ToolRegistry()
        val before = registry.version.value
        registry.register("readFile", FakeTool("readFile"), BuiltinGroup.CORE)
        assertTrue("新工具注册应推进版本号", registry.version.value > before)
    }

    @Test
    fun `unregistering a tool bumps version`() {
        val registry = ToolRegistry()
        registry.register("websearch", FakeTool("websearch"), BuiltinGroup.DATA)
        val before = registry.version.value
        registry.unregister("websearch")
        assertTrue("工具注销应推进版本号", registry.version.value > before)
    }

    @Test
    fun `unregistering an absent tool does not bump version`() {
        val registry = ToolRegistry()
        val before = registry.version.value
        registry.unregister("neverRegistered")
        assertEquals("注销不存在的工具不应发信号", before, registry.version.value)
    }

    @Test
    fun `re-registering the same tool name does not bump version`() {
        val registry = ToolRegistry()
        val tool = FakeTool("websearch")
        registry.register("websearch", tool, BuiltinGroup.DATA)
        val afterFirst = registry.version.value

        // 远端 server 重连会走幂等路径重复注册同名工具
        registry.register("websearch", tool, BuiltinGroup.DATA)
        assertEquals(
            "同名同分组重复注册内容未变，不应触发 UI 重组",
            afterFirst,
            registry.version.value
        )
    }

    @Test
    fun `re-registering under a different group bumps version`() {
        val registry = ToolRegistry()
        registry.register("terminal", FakeTool("terminal"), BuiltinGroup.CORE)
        val before = registry.version.value
        registry.register("terminal", FakeTool("terminal"), BuiltinGroup.DATA)
        assertTrue(
            "分组发生变更必须发信号，否则分组卡片展示会停留在旧归属",
            registry.version.value > before
        )
        assertEquals(BuiltinGroup.DATA, registry.getBuiltinGroup("terminal"))
    }

    @Test
    fun `version is monotonic across mixed operations`() {
        val registry = ToolRegistry()
        val seen = mutableListOf(registry.version.value)
        repeat(3) { i ->
            registry.register("t$i", FakeTool("t$i"), BuiltinGroup.CORE)
            seen += registry.version.value
        }
        registry.unregister("t1")
        seen += registry.version.value

        seen.zipWithNext().forEach { (a, b) ->
            assertTrue("版本号必须单调递增，实际出现 $a -> $b", b > a)
        }
        assertEquals("4 次变更后版本号应为 4", 4L, seen.last())
    }

    @Test
    fun `grouped lookup reflects registry changes after version bump`() {
        val registry = ToolRegistry()
        registry.register("readFile", FakeTool("readFile"), BuiltinGroup.CORE)
        assertEquals(listOf("readFile"), registry.getToolsByBuiltinGroup(BuiltinGroup.CORE).map { it.name })

        // 模拟远端 server 断开后被注销
        val before = registry.version.value
        registry.unregister("readFile")
        assertTrue(registry.version.value > before)
        assertTrue(
            "注销后分组查询应同步为空",
            registry.getToolsByBuiltinGroup(BuiltinGroup.CORE).isEmpty()
        )
        assertEquals(0, registry.getGroupedToolCount())
        assertFalse(registry.hasTool("readFile"))
    }
}