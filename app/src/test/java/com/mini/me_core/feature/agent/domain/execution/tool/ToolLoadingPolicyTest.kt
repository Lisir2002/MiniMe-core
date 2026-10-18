package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B2 每轮工具组装策略单测（批次 B：Deferred Loading）。
 * 覆盖：灰度开关（关=全量原样返回）、CORE 规范序、DEFERRED 展开尾部追加、
 * 无会话仅 CORE、未知 CORE 名称排序、assembleCoreTools（SubAgent 收窄）、开关读取失败降级。
 */
class ToolLoadingPolicyTest {

    private class FakeTool(
        override val name: String,
        override val exposure: ToolExposure = ToolExposure.DEFERRED,
        override val description: String = "工具 $name"
    ) : AgentTool() {
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("ok"))
        }
    }

    private fun core(name: String) = FakeTool(name, ToolExposure.CORE)
    private fun deferred(name: String) = FakeTool(name, ToolExposure.DEFERRED)

    private val settings = mockk<NormFlowSettingsRepository>(relaxed = true)
    private val expansion = DeferredToolExpansion()
    private val policy = ToolLoadingPolicy(expansion, settings)

    /** 一套最小工具集：3 个 CORE（含未列入规范表的 unknown_core）+ 3 个 DEFERRED。 */
    private fun toolSet() = listOf(
        deferred("websearch"),
        core("readFile"),
        deferred("browser"),
        core("unknown_core"),
        core("todo")
    )

    @Test
    fun assembleRoundTools_switchOff_returnsAllUnchanged() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns false
        val all = toolSet()
        // 同一实例返回（逐字节等价，存量行为）
        assertEquals(all, policy.assembleRoundTools("s1", all))
    }

    @Test
    fun assembleRoundTools_switchOn_coreInCanonicalOrderOnly() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns true
        val result = policy.assembleRoundTools("s1", toolSet())
        // CORE 按规范序：readFile < todo；未列入的 unknown_core 按名称序排在其后
        assertEquals(listOf("readFile", "todo", "unknown_core"), result.map { it.name })
    }

    @Test
    fun assembleRoundTools_switchOn_expandedDeferredAppendedInInputOrder() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns true
        expansion.recordCall("s1", "browser")
        expansion.recordHit("s1", listOf("websearch"))
        val result = policy.assembleRoundTools("s1", toolSet())
        // CORE 在前，展开的 DEFERRED 按输入序（websearch 在 browser 前）追加尾部
        assertEquals(
            listOf("readFile", "todo", "unknown_core", "websearch", "browser"),
            result.map { it.name }
        )
    }

    @Test
    fun assembleRoundTools_switchOn_nullSession_coreOnly() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns true
        expansion.recordCall("other", "websearch")
        val result = policy.assembleRoundTools(null, toolSet())
        assertEquals(listOf("readFile", "todo", "unknown_core"), result.map { it.name })
    }

    @Test
    fun assembleRoundTools_switchOn_otherSessionExpansionNotLeaked() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns true
        expansion.recordCall("s2", "websearch")
        val result = policy.assembleRoundTools("s1", toolSet())
        assertFalse(result.any { it.name == "websearch" })
    }

    @Test
    fun assembleRoundTools_settingsThrows_treatedAsOff() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } throws RuntimeException("kv 故障")
        val all = toolSet()
        // 读取失败按关闭处理：不因设置层故障收窄模型能力
        assertEquals(all, policy.assembleRoundTools("s1", all))
    }

    @Test
    fun assemble_unknownCoreSortedAfterKnownByName() {
        val tools = listOf(core("zeta_core"), core("alpha_core"), core("readFile"))
        val result = policy.assemble(tools, emptySet())
        assertEquals(listOf("readFile", "alpha_core", "zeta_core"), result.map { it.name })
    }

    @Test
    fun assemble_expandedIntersectsCurrentTools_staleNamesIgnored() {
        // MCP 工具注销后：展开集残留名字与当前清单求交后自然失效
        val result = policy.assemble(listOf(core("readFile")), setOf("websearch"))
        assertEquals(listOf("readFile"), result.map { it.name })
    }

    @Test
    fun assembleCoreTools_switchOff_returnsAll() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns false
        val all = toolSet()
        assertEquals(all, policy.assembleCoreTools(all))
    }

    @Test
    fun assembleCoreTools_switchOn_coreOnly_evenWithExpansion() = runBlocking {
        coEvery { settings.isDeferredToolLoadingActive() } returns true
        expansion.recordCall("s1", "websearch")
        val result = policy.assembleCoreTools(toolSet())
        // SubAgent 不消费展开集：静态 CORE + catalog
        assertEquals(listOf("readFile", "todo", "unknown_core"), result.map { it.name })
    }

    @Test
    fun assemble_canonicalOrder_matchesFullCoreSequence() {
        val shuffled = listOf("tool_catalog", "switchMode", "askUserQuestion", "plan", "todo", "Bash", "search", "list", "editFile", "writeFile", "readFile")
            .map { core(it) }
            .shuffled()
        val result = policy.assemble(shuffled, emptySet())
        assertEquals(
            listOf("readFile", "writeFile", "editFile", "list", "search", "Bash", "todo", "plan", "askUserQuestion", "switchMode", "tool_catalog"),
            result.map { it.name }
        )
    }

    @Test
    fun assemble_emptyTools_emptyResult() {
        assertTrue(policy.assemble(emptyList(), setOf("websearch")).isEmpty())
    }
}
