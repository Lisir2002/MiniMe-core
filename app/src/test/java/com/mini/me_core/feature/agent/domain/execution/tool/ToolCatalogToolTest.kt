package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.core.model.AgentContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 tool_catalog 元工具单测（批次 B：Deferred Loading）。
 * 覆盖：检索结果 JSON 结构、命中刷新展开集（仅 DEFERRED）、CORE 命中不占 LRU、
 * 无 sessionId 仍返回结果、缺 query 报错、无命中提示。
 */
class ToolCatalogToolTest {

    private class FakeTool(
        override val name: String,
        override val description: String,
        override val exposure: ToolExposure = ToolExposure.DEFERRED
    ) : AgentTool() {
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("ok"))
        }
    }

    private val expansion = DeferredToolExpansion()
    private val registry = ToolRegistry()

    private fun toolWith(registry: ToolRegistry, vararg tools: AgentTool): ToolCatalogTool {
        tools.forEach { registry.register(it.name, it) }
        return ToolCatalogTool(ToolCatalog(), expansion, dagger.Lazy { registry })
    }

    private fun context(sessionId: String?) = AgentContext(
        currentFile = null,
        selectedCode = null,
        projectRoot = "/root",
        language = null,
        sessionId = sessionId
    )

    private fun args(query: String) = mapOf<String, JsonElement>("query" to JsonPrimitive(query))

    @Test
    fun execute_hitsDeferred_recordsExpansionAndReturnsJson() = runBlocking {
        val tool = toolWith(
            registry,
            FakeTool("readFile", "读取文件内容", ToolExposure.CORE),
            FakeTool("websearch", "联网搜索网页内容")
        )
        val result = tool.executeWithContext(args("网页搜索"), context("s1"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JsonObject
        assertEquals(1, data["hit_count"]?.jsonPrimitive?.content?.toInt())
        val firstTool = data["tools"]?.jsonArray?.get(0) as? JsonObject
        assertEquals("websearch", firstTool?.get("name")?.jsonPrimitive?.content)
        // 仅 DEFERRED 命中进展开集；CORE 的 readFile 不占 LRU
        assertEquals(setOf("websearch"), expansion.getExpanded("s1"))
    }

    @Test
    fun execute_coreOnlyHit_notRecordedButStillReturned() = runBlocking {
        val tool = toolWith(registry, FakeTool("readFile", "读取文件内容", ToolExposure.CORE))
        val result = tool.executeWithContext(args("读取文件"), context("s1"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JsonObject
        assertEquals(1, data["hit_count"]?.jsonPrimitive?.content?.toInt())
        // CORE 命中不刷新展开集（本就常驻）
        assertTrue(expansion.getExpanded("s1").isEmpty())
    }

    @Test
    fun execute_noHit_returnsZeroWithHint() = runBlocking {
        val tool = toolWith(registry, FakeTool("todo", "任务清单管理"))
        val result = tool.executeWithContext(args("完全不存在的关键词zzz"), context("s1"))
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JsonObject
        assertEquals(0, data["hit_count"]?.jsonPrimitive?.content?.toInt())
        assertTrue(data["hint"]?.jsonPrimitive?.content?.contains("未命中") == true)
        assertTrue(expansion.getExpanded("s1").isEmpty())
    }

    @Test
    fun execute_nullSession_stillReturnsResult() = runBlocking {
        val tool = toolWith(registry, FakeTool("websearch", "联网搜索网页内容"))
        val result = tool.executeWithContext(args("网页"), context(null))
        // 无会话上下文：仍返回检索结果，仅跳过展开集刷新
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JsonObject
        assertEquals(1, data["hit_count"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun execute_missingQuery_error() = runBlocking {
        val tool = toolWith(registry, FakeTool("websearch", "联网搜索网页内容"))
        val result = tool.executeWithContext(emptyMap(), context("s1"))
        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_QUERY", (result as ToolResult.Error).code)
    }

    @Test
    fun execute_blankQuery_error() = runBlocking {
        val tool = toolWith(registry, FakeTool("websearch", "联网搜索网页内容"))
        val result = tool.executeWithContext(mapOf<String, JsonElement>("query" to JsonPrimitive("  ")), context("s1"))
        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_QUERY", (result as ToolResult.Error).code)
    }

    @Test
    fun metadata_coreExposureAndName() {
        val tool = toolWith(registry)
        assertEquals("tool_catalog", tool.name)
        assertEquals(ToolExposure.CORE, tool.exposure)
        assertTrue(tool.parameters.containsKey("query"))
        assertTrue(tool.parameters["query"]?.required == true)
    }
}
