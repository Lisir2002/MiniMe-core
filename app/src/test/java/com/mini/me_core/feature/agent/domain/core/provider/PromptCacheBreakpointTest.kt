package com.mini.me_core.feature.agent.domain.core.provider

import com.mini.me_core.feature.agent.data.remote.anthropic.AnthropicSystemBlock
import com.mini.me_core.feature.agent.data.remote.anthropic.AnthropicToolDefinition
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.domain.execution.tool.ParameterType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1 prompt caching 打点逻辑回归：
 * - 分段且含稳定段 → system 为 block 列表，断点只打在最后一个稳定段末尾；
 * - 无分段/无稳定段 → 退化为单字符串（历史行为，无缓存打点）；
 * - tools 断点仅在存在稳定段时追加，置于数组末尾元素。
 */
class PromptCacheBreakpointTest {

    private fun tool(name: String) = object : AgentTool() {
        override val name: String = name
        override val description: String = "desc-$name"
        override val parameters: Map<String, ToolParameter> = mapOf(
            "path" to ToolParameter("path", ParameterType.STRING, "文件路径")
        )
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult =
            ToolResult.Success(JsonPrimitive("ok"))
    }

    @Test
    fun noSegments_fallsBackToPlainString() {
        val payload = AnthropicAdapter.buildSystemPayload("系统提示", null)
        assertEquals("系统提示", payload)
        assertFalse(AnthropicAdapter.hasCacheableSegment(null))
    }

    @Test
    fun blankPrompt_staysNull() {
        assertNull(AnthropicAdapter.buildSystemPayload("  ", null))
    }

    @Test
    fun segmentsWithoutCacheable_fallsBackToPlainString() {
        val segments = listOf(
            SystemPromptSegment("稳定", cacheable = false),
            SystemPromptSegment("易变", cacheable = false)
        )
        assertEquals("稳定易变", AnthropicAdapter.buildSystemPayload("稳定易变", segments))
        assertFalse(AnthropicAdapter.hasCacheableSegment(segments))
    }

    @Test
    fun cacheableSegment_buildsBlocksWithBreakpointOnLastStable() {
        val segments = listOf(
            SystemPromptSegment("静态规则", cacheable = true),
            SystemPromptSegment("易变内容", cacheable = false)
        )
        @Suppress("UNCHECKED_CAST")
        val blocks = AnthropicAdapter.buildSystemPayload("ignored", segments) as List<AnthropicSystemBlock>
        assertEquals(2, blocks.size)
        assertEquals("静态规则", blocks[0].text)
        // 断点打在稳定段末尾，其后的易变段不参与缓存
        assertEquals(mapOf("type" to "ephemeral"), blocks[0].cache_control)
        assertNull(blocks[1].cache_control)
        assertTrue(AnthropicAdapter.hasCacheableSegment(segments))
    }

    @Test
    fun multipleCacheableSegments_onlyLastStableGetsBreakpoint() {
        val segments = listOf(
            SystemPromptSegment("A", cacheable = true),
            SystemPromptSegment("B", cacheable = true),
            SystemPromptSegment("C", cacheable = false)
        )
        @Suppress("UNCHECKED_CAST")
        val blocks = AnthropicAdapter.buildSystemPayload("ignored", segments) as List<AnthropicSystemBlock>
        // 断点打在最后一个稳定段（B）末尾：缓存前缀覆盖 A+B，C 为易变段不参与
        assertNull(blocks[0].cache_control)
        assertEquals(mapOf("type" to "ephemeral"), blocks[1].cache_control)
        assertNull(blocks[2].cache_control)
    }

    @Test
    fun toolDefs_emptyTools_returnsNull() {
        assertNull(AnthropicAdapter.buildToolDefs(emptyList(), withCacheBreakpoint = true))
    }

    @Test
    fun toolDefs_withBreakpoint_marksLastElementOnly() {
        val tools = listOf(tool("readFile"), tool("writeFile"), tool("bash"))
        val defs = AnthropicAdapter.buildToolDefs(tools, withCacheBreakpoint = true)!!
        assertEquals(3, defs.size)
        assertNull(defs[0].cache_control)
        assertNull(defs[1].cache_control)
        assertEquals(mapOf("type" to "ephemeral"), defs[2].cache_control)
        // schema 仍完整透传
        assertEquals("object", defs[0].input_schema["type"])
        assertEquals(listOf("path"), defs[0].input_schema["required"])
    }

    @Test
    fun toolDefs_withoutBreakpoint_neverMarksElements() {
        val tools = listOf(tool("readFile"), tool("bash"))
        val defs: List<AnthropicToolDefinition> = AnthropicAdapter.buildToolDefs(tools, withCacheBreakpoint = false)!!
        assertTrue(defs.all { it.cache_control == null })
    }
}
