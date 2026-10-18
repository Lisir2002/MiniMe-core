package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BuiltinGroup] 与 [ToolRegistry] 分组登记的纯 JVM 单测。
 *
 * 分组是本地能力成为三台内置 MCP server 的基础（见 [BuiltinGroup]）；
 * 「新增内置工具必须声明分组」是项目强制约束，故用源码扫描断言覆盖率，
 * 防止漏标注的工具悄悄落到任何分组之外。
 */
class BuiltinGroupTest {

    private class FakeTool(
        override val name: String,
        override val description: String = "fake",
        override val parameters: Map<String, ToolParameter> = emptyMap()
    ) : AgentTool() {
        override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult =
            ToolResult.Success(kotlinx.serialization.json.JsonPrimitive("ok"))
    }

    private fun registry(vararg entries: Pair<String, BuiltinGroup>): ToolRegistry =
        ToolRegistry().apply { entries.forEach { (n, g) -> register(n, FakeTool(n), g) } }

    // ── 注册与查询 ────────────────────────────────────────────────

    @Test
    fun `register with group is queryable`() {
        val reg = registry("readFile" to BuiltinGroup.CORE)
        assertEquals(BuiltinGroup.CORE, reg.getBuiltinGroup("readFile"))
    }

    @Test
    fun `register without group yields null (remote tools)`() {
        val reg = ToolRegistry().apply { register("mcp__srv__fetch", FakeTool("mcp__srv__fetch")) }
        assertNull("远端 MCP 工具不应出现在任何内置分组里", reg.getBuiltinGroup("mcp__srv__fetch"))
        assertEquals(0, reg.getGroupedToolCount())
    }

    @Test
    fun `getToolsByBuiltinGroup returns only that group sorted by name`() {
        val reg = registry(
            "writeFile" to BuiltinGroup.CORE,
            "readFile" to BuiltinGroup.CORE,
            "Bash" to BuiltinGroup.CORE,
            "todo" to BuiltinGroup.CHAT,
            "gitops" to BuiltinGroup.DATA
        )
        assertEquals(
            listOf("Bash", "readFile", "writeFile"),
            reg.getToolsByBuiltinGroup(BuiltinGroup.CORE).map { it.name }
        )
        assertEquals(listOf("todo"), reg.getToolsByBuiltinGroup(BuiltinGroup.CHAT).map { it.name })
        assertEquals(listOf("gitops"), reg.getToolsByBuiltinGroup(BuiltinGroup.DATA).map { it.name })
    }

    @Test
    fun `groups are disjoint - a tool belongs to exactly one group`() {
        val reg = registry(
            "readFile" to BuiltinGroup.CORE,
            "todo" to BuiltinGroup.CHAT,
            "gitops" to BuiltinGroup.DATA
        )
        val total = BuiltinGroup.entries.sumOf { reg.getToolsByBuiltinGroup(it).size }
        assertEquals(3, total)
        assertEquals(3, reg.getGroupedToolCount())
    }

    @Test
    fun `unregister clears group to avoid stale entry`() {
        val reg = registry("readFile" to BuiltinGroup.CORE, "todo" to BuiltinGroup.CHAT)
        reg.unregister("readFile")
        assertNull(reg.getBuiltinGroup("readFile"))
        assertEquals(1, reg.getGroupedToolCount())
        assertEquals(listOf("todo"), reg.getToolsByBuiltinGroup(BuiltinGroup.CHAT).map { it.name })
        assertTrue(reg.getToolsByBuiltinGroup(BuiltinGroup.CORE).isEmpty())
    }

    // ── 分组元数据 ────────────────────────────────────────────────

    @Test
    fun `server names are unique and follow MCP naming rules`() {
        val names = BuiltinGroup.entries.map { it.serverName }
        assertEquals("三台内置 server 名称必须互不相同", names.size, names.toSet().size)
        names.forEach {
            assertTrue("[$it] 不符合 MCP 工具/server 名允许字符 [A-Za-z0-9_.-]", it.matches(Regex("^[A-Za-z0-9_.-]{1,128}$")))
        }
    }

    @Test
    fun `only CORE group is externally exposed by default`() {
        assertTrue(BuiltinGroup.CORE.remotelyExposedByDefault)
        assertFalse(
            "含 askUserQuestion 等 UI 依赖工具的 CHAT 组不得默认对外暴露",
            BuiltinGroup.CHAT.remotelyExposedByDefault
        )
        assertFalse(
            "含 browser/device_storage 的 DATA 组不得默认对外暴露",
            BuiltinGroup.DATA.remotelyExposedByDefault
        )
    }

    // ── 强制约束：AgentModule 中每个 registerTool 都必须声明分组 ──

    @Test
    fun `every registerTool call in AgentModule declares a builtinGroup`() {
        val source = agentModuleSource()
        val calls = Regex("""registerTool\("([^"]+)",\s*[A-Za-z0-9_]+(\s*,\s*BuiltinGroup\.[A-Z]+)?\s*\)""")
            .findAll(source)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        assertTrue("未能在 AgentModule.kt 中解析出 registerTool 调用", calls.isNotEmpty())

        val missing = calls.filter { it.second.isBlank() }.map { it.first }
        assertTrue(
            "以下内置工具未声明 builtinGroup（违反 AGENTS.md 强制约束）: $missing",
            missing.isEmpty()
        )

        val duplicated = calls.groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertTrue("以下工具被重复注册: $duplicated", duplicated.isEmpty())
    }

    @Test
    fun `builtin tool count matches the documented 43`() {
        val source = agentModuleSource()
        val count = Regex("""registerTool\("[^"]+",\s*[A-Za-z0-9_]+\s*,\s*BuiltinGroup\.[A-Z]+\s*\)""")
            .findAll(source).count()
        assertEquals("内置工具数量变化时须同步更新规划 §3 的分组清单", 43, count)
    }

    private fun agentModuleSource(): String {
        val candidates = listOf(
            "src/main/java/com/mini/me_core/di/AgentModule.kt",
            "app/src/main/java/com/mini/me_core/di/AgentModule.kt"
        )
        val file = candidates.map { File(it) }.firstOrNull { it.isFile }
            ?: error("未找到 AgentModule.kt（尝试过: ${candidates.joinToString()}）")
        return file.readText()
    }
}
