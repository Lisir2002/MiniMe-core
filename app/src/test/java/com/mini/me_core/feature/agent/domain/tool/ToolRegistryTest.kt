package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRegistryTest {

    private lateinit var registry: ToolRegistry

    private class FakeAgentTool(
        override val name: String,
        override val description: String = "Test tool description",
        override val parameters: Map<String, ToolParameter> = emptyMap(),
        override val aliases: Set<String> = emptySet()
    ) : AgentTool() {
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            return ToolResult.Success(JsonPrimitive("Fake tool executed"))
        }
    }

    @Before
    fun setUp() {
        registry = ToolRegistry()
    }

    @Test
    fun registerAndGetTool_success() {
        val tool = FakeAgentTool(name = "test_tool")
        registry.register("test_tool", tool)

        assertTrue(registry.hasTool("test_tool"))
        assertEquals(tool, registry.getTool("test_tool"))
        assertEquals(setOf("test_tool"), registry.getToolNames())
    }

    @Test
    fun unregisterTool_success() {
        val tool = FakeAgentTool(name = "temp_tool")
        registry.register("temp_tool", tool)
        assertTrue(registry.hasTool("temp_tool"))

        registry.unregister("temp_tool")

        assertFalse(registry.hasTool("temp_tool"))
        assertNull(registry.getTool("temp_tool"))
        assertTrue(registry.getToolNames().isEmpty())
    }

    @Test
    fun getAvailableTools_returnsAllRegisteredTools() {
        val tool1 = FakeAgentTool(name = "tool_1")
        val tool2 = FakeAgentTool(name = "tool_2")

        registry.register("tool_1", tool1)
        registry.register("tool_2", tool2)

        val available = registry.getAvailableTools()
        assertEquals(2, available.size)
        assertTrue(available.contains(tool1))
        assertTrue(available.contains(tool2))
    }

    @Test
    fun getAvailableTools_withMode_returnsAllTools() {
        val tool = FakeAgentTool(name = "write_file")
        registry.register("write_file", tool)

        // PLAN 模式和 BUILD 模式下 getAvailableTools(mode) 都应返回注册的工具定义
        val planTools = registry.getAvailableTools(AgentMode.PLAN)
        val buildTools = registry.getAvailableTools(AgentMode.BUILD)

        assertEquals(1, planTools.size)
        assertEquals(1, buildTools.size)
        assertEquals(tool, planTools.first())
    }

    @Test
    fun getAllToolDefinitions_mapsToolsToDefinitions() {
        val tool = FakeAgentTool(
            name = "search_tool",
            description = "Searches codebase"
        )
        registry.register("search_tool", tool)

        val definitions = registry.getAllToolDefinitions()
        assertEquals(1, definitions.size)
        assertEquals("search_tool", definitions.first().name)
        assertEquals("Searches codebase", definitions.first().description)
    }

    @Test
    fun getTool_viaAlias_routesToCanonicalTool() {
        val tool = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        registry.register("Bash", tool)

        // 正式名直查
        assertEquals(tool, registry.getTool("Bash"))
        // 别名回查路由到同一实例
        assertEquals(tool, registry.getTool("bash"))
        assertTrue(registry.hasTool("bash"))
    }

    @Test
    fun aliases_notExposedAsToolNamesOrDefinitions() {
        val tool = FakeAgentTool(name = "Bash", aliases = setOf("bash", "sh"))
        registry.register("Bash", tool)

        // 别名只参与查找回退，不进入工具名集合与定义列表（避免重复暴露给 AI）
        assertEquals(setOf("Bash"), registry.getToolNames())
        assertEquals(1, registry.getAllToolDefinitions().size)
    }

    @Test
    fun unregister_removesAliasMappings() {
        val tool = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        registry.register("Bash", tool)

        registry.unregister("Bash")

        assertNull(registry.getTool("bash"))
        assertFalse(registry.hasTool("bash"))
        assertNull(registry.getTool("Bash"))
    }

    @Test
    fun unregister_viaAliasName_removesCanonicalTool() {
        val tool = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        registry.register("Bash", tool)

        registry.unregister("bash")

        assertNull(registry.getTool("Bash"))
        assertFalse(registry.hasTool("bash"))
        assertTrue(registry.getToolNames().isEmpty())
    }

    @Test
    fun alias_shadowingExistingCanonicalName_notRegistered() {
        val bash = FakeAgentTool(name = "Bash")
        val shadow = FakeAgentTool(name = "shadow_tool", aliases = setOf("Bash"))
        registry.register("Bash", bash)
        registry.register("shadow_tool", shadow)

        // 别名与既有正式名冲突时不登记，避免遮蔽已有工具
        assertEquals(bash, registry.getTool("Bash"))
    }

    @Test
    fun alias_conflictingWithExistingAlias_notOverwritten() {
        val first = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        val second = FakeAgentTool(name = "shell", aliases = setOf("bash"))
        registry.register("Bash", first)
        registry.register("shell", second)

        // 别名已被先行工具占用，后来的同名别名不劫持
        assertEquals(first, registry.getTool("bash"))
        assertEquals(second, registry.getTool("shell"))
    }

    @Test
    fun canonicalName_registeredAfterAlias_takesPriorityOverAlias() {
        val bash = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        registry.register("Bash", bash)

        // 后注册的正式名与既有别名同名：正式名直查优先，别名不遮蔽正式名
        val lowercase = FakeAgentTool(name = "bash")
        registry.register("bash", lowercase)

        assertEquals(lowercase, registry.getTool("bash"))
        assertEquals(bash, registry.getTool("Bash"))
    }

    @Test
    fun getTool_unknownAlias_returnsNull() {
        val tool = FakeAgentTool(name = "Bash", aliases = setOf("bash"))
        registry.register("Bash", tool)

        assertNull(registry.getTool("nonexistent"))
        assertFalse(registry.hasTool("nonexistent"))
    }
}
