package com.mini.me_core.feature.agent.domain.execution.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 工具目录检索器单测（批次 B：Deferred Loading）。
 * 覆盖：中英混合分词（camelCase/snake_case/CJK bigram）、权重排序（name>alias>param>desc）、
 * 别名命中、无命中/空查询、limit 与确定性 tie-break。
 */
class ToolCatalogTest {

    private val catalog = ToolCatalog()

    private class FakeTool(
        override val name: String,
        override val description: String,
        override val parameters: Map<String, ToolParameter> = emptyMap(),
        override val aliases: Set<String> = emptySet()
    ) : AgentTool() {
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("ok"))
        }
    }

    private fun param(name: String, desc: String = "参数 $name") =
        ToolParameter(name, ParameterType.STRING, desc)

    // ── 分词 ──

    @Test
    fun tokenize_camelCase_splitsAndKeepsFull() {
        val tokens = catalog.tokenize("readFile").toSet()
        assertTrue(tokens.contains("read"))
        assertTrue(tokens.contains("file"))
        assertTrue(tokens.contains("readfile"))
    }

    @Test
    fun tokenize_snakeCase_splitsOnUnderscore() {
        val tokens = catalog.tokenize("load_rule").toSet()
        assertTrue(tokens.contains("load"))
        assertTrue(tokens.contains("rule"))
        assertFalse(tokens.contains("load_rule"))
    }

    @Test
    fun tokenize_cjk_bigram() {
        val tokens = catalog.tokenize("读取文件").toSet()
        assertTrue(tokens.contains("读取"))
        assertTrue(tokens.contains("取文"))
        assertTrue(tokens.contains("文件"))
    }

    @Test
    fun tokenize_cjkSingleChar_keptAsIs() {
        assertEquals(setOf("读"), catalog.tokenize("读").toSet())
    }

    @Test
    fun tokenize_mixed_cjkAndLatin() {
        val tokens = catalog.tokenize("用 readFile 读取").toSet()
        assertTrue(tokens.contains("read"))
        assertTrue(tokens.contains("file"))
        assertTrue(tokens.contains("读取"))
    }

    @Test
    fun tokenize_punctuationAsSeparator() {
        val tokens = catalog.tokenize("hello, world!").toSet()
        assertTrue(tokens.contains("hello"))
        assertTrue(tokens.contains("world"))
        assertFalse(tokens.any { it.contains(",") })
    }

    // ── 检索 ──

    @Test
    fun search_nameMatchOutranksDescriptionMatch() {
        val tools = listOf(
            FakeTool(name = "tool_a", description = "一个与搜索无关的工具"),
            FakeTool(name = "search", description = "在项目中检索代码")
        )
        val hits = catalog.search(tools, "search")
        assertEquals("search", hits.first().name)
    }

    @Test
    fun search_paramNameMatch_beatsDescriptionOnly() {
        val tools = listOf(
            FakeTool(name = "tool_a", description = "读取文件内容"),
            FakeTool(name = "tool_b", description = "通用工具", parameters = mapOf("path" to param("path")))
        )
        val hits = catalog.search(tools, "path")
        assertEquals("tool_b", hits.first().name)
    }

    @Test
    fun search_aliasMatch_hits() {
        val tools = listOf(
            FakeTool(name = "Bash", description = "执行 shell 命令", aliases = setOf("bash", "shell"))
        )
        val hits = catalog.search(tools, "shell")
        assertEquals(1, hits.size)
        assertEquals("Bash", hits.first().name)
    }

    @Test
    fun search_chineseQuery_hitsChineseDescription() {
        val tools = listOf(
            FakeTool(name = "websearch", description = "联网搜索网页内容")
        )
        val hits = catalog.search(tools, "网页")
        assertEquals("websearch", hits.first().name)
    }

    @Test
    fun search_camelCaseQuery_hitsSnakeCaseTool() {
        val tools = listOf(
            FakeTool(name = "job_start", description = "启动后台任务")
        )
        // 查询 "jobStart"：分词 → job/start/jobstart；工具名分词 → job/start，交集非空
        val hits = catalog.search(tools, "jobStart")
        assertEquals("job_start", hits.first().name)
    }

    @Test
    fun search_noHit_returnsEmpty() {
        val tools = listOf(FakeTool(name = "todo", description = "任务清单管理"))
        assertTrue(catalog.search(tools, "完全不存在的关键词xyz").isEmpty())
    }

    @Test
    fun search_emptyQuery_returnsEmpty() {
        val tools = listOf(FakeTool(name = "todo", description = "任务清单"))
        assertTrue(catalog.search(tools, "   ").isEmpty())
    }

    @Test
    fun search_emptyTools_returnsEmpty() {
        assertTrue(catalog.search(emptyList(), "todo").isEmpty())
    }

    @Test
    fun search_limit_respected() {
        val tools = (1..10).map { FakeTool(name = "todo_$it", description = "任务清单 $it") }
        val hits = catalog.search(tools, "todo", limit = 3)
        assertEquals(3, hits.size)
    }

    @Test
    fun search_tieBreak_byNameAscending_deterministic() {
        val tools = listOf(
            FakeTool(name = "todo_b", description = "任务清单"),
            FakeTool(name = "todo_a", description = "任务清单")
        )
        val hits = catalog.search(tools, "todo")
        assertEquals(listOf("todo_a", "todo_b"), hits.map { it.name })
    }
}
