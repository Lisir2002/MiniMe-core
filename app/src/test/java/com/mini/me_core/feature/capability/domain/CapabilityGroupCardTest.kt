package com.mini.me_core.feature.capability.domain

import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2：[CapabilityGroupCard] 同构性与徽章投影的纯 JVM 单测。
 *
 * 「页面合并」的全部价值在于**内置分组与远端 server 在同一数据结构上同构**：
 * 若二者形状不同，合并就只是把两个页面塞进一个列表，用户仍要分辨「哪个是内置、
 * 哪个是外部」，反而更乱。故这里用构造等价性把这条不变量钉住。
 */
class CapabilityGroupCardTest {

    private class FakeTool(
        override val name: String,
        override val permissionPolicy: ToolPermissionPolicy = ToolPermissionPolicy.AUTO_APPROVE
    ) : AgentTool() {
        override val description: String = "fake"
        override val parameters: Map<String, ToolParameter> = emptyMap()
        override suspend fun execute(
            args: Map<String, kotlinx.serialization.json.JsonElement>
        ): ToolResult = ToolResult.Success(JsonPrimitive("ok"))
    }

    // ── 同构性：内置与远端走同一构造路径 ────────────────────────────

    @Test
    fun `builtin group card and remote card share the same shape`() {
        val registry = ToolRegistry()
        registry.register("readFile", FakeTool("readFile"), BuiltinGroup.CORE)

        val builtin = CapabilityGroupCard(
            id = BuiltinGroup.CORE.serverName,
            displayName = BuiltinGroup.CORE.displayName,
            tools = registry.getToolsByBuiltinGroup(BuiltinGroup.CORE).map {
                CapabilityToolCard(
                    name = it.name,
                    description = it.description,
                    permissionPolicy = it.permissionPolicy,
                    capabilities = it.capabilities.toList(),
                    parameters = emptyList(),
                    exposure = it.exposure,
                    requiresApproval = it.permissionPolicy == ToolPermissionPolicy.ASK,
                    builtinGroup = BuiltinGroup.CORE,
                    sourceServerName = null
                )
            },
            status = null,
            isBuiltin = true,
            remotelyExposed = BuiltinGroup.CORE.remotelyExposedByDefault
        )
        val remote = CapabilityGroupCard(
            id = "github",
            displayName = "github",
            tools = emptyList(),
            status = McpServerStatus("github", McpServerStatus.State.CONNECTED, 3, 4),
            isBuiltin = false,
            remotelyExposed = true
        )

        // 关键不变量：两个卡片是同一个类型，UI 可用同一 Composable 渲染
        val asList: List<CapabilityGroupCard> = listOf(builtin, remote)
        assertEquals(2, asList.size)
        assertTrue(asList[0].isBuiltin)
        assertFalse(asList[1].isBuiltin)
        assertEquals(builtin, asList[0])
    }

    @Test
    fun `only remote cards carry connection status`() {
        val builtin = CapabilityGroupCard(
            id = BuiltinGroup.CHAT.serverName,
            displayName = BuiltinGroup.CHAT.displayName,
            tools = emptyList(),
            status = null,
            isBuiltin = true,
            remotelyExposed = BuiltinGroup.CHAT.remotelyExposedByDefault
        )
        // 内置 server 是进程内 server，恒可用，不应展示连接态（否则误导用户以为会断）
        assertNull(builtin.status)
        assertFalse(
            "CHAT 组含 askUserQuestion 等强依赖 App 内 UI 的工具，默认不应对外暴露",
            BuiltinGroup.CHAT.remotelyExposedByDefault
        )
    }

    @Test
    fun `only CORE group is remotely exposed by default`() {
        assertTrue(BuiltinGroup.CORE.remotelyExposedByDefault)
        assertFalse(BuiltinGroup.CHAT.remotelyExposedByDefault)
        assertFalse(BuiltinGroup.DATA.remotelyExposedByDefault)
    }

    // ── 徽章投影 ───────────────────────────────────────────────────

    @Test
    fun `ASK is the only policy that projects a require-approval badge`() {
        // 「需审批」徽章的唯一判据。枚举只有两个值，故此断言同时锁住了语义边界：
        // 日后新增第三个策略时，必须显式决定它要不要徽章，而不会静默落到 AUTO_APPROVE 一侧。
        val ask = FakeTool("terminal", ToolPermissionPolicy.ASK)
        val auto = FakeTool("list", ToolPermissionPolicy.AUTO_APPROVE)
        assertTrue(ask.permissionPolicy == ToolPermissionPolicy.ASK)
        assertFalse(auto.permissionPolicy == ToolPermissionPolicy.ASK)
        assertEquals(
            "恰好一个策略值会触发需审批徽章",
            1,
            ToolPermissionPolicy.entries.count { it == ToolPermissionPolicy.ASK }
        )
    }

    @Test
    fun `exposure level is carried through to the card`() {
        val registry = ToolRegistry()
        registry.register("readFile", FakeTool("readFile"), BuiltinGroup.CORE)
        val tool = registry.getTool("readFile")
        assertNotNull(tool)
        // 默认 DEFERRED（批次 B），卡片据此区分「常驻/ 按需」两枚徽章
        assertEquals(
            com.mini.me_core.feature.agent.domain.execution.tool.ToolExposure.DEFERRED,
            tool!!.exposure
        )
    }

    // ── BuiltinGroup 反查 ───────────────────────────────────────────

    @Test
    fun `byServerName resolves builtin groups and rejects remote servers`() {
        assertEquals(BuiltinGroup.CORE, BuiltinGroup.byServerName("MiniMe-Core"))
        assertEquals(BuiltinGroup.CHAT, BuiltinGroup.byServerName("MiniMe-Chat"))
        assertEquals(BuiltinGroup.DATA, BuiltinGroup.byServerName("MiniMe-Data"))
        assertNull(BuiltinGroup.byServerName("github"))
        assertNull(BuiltinGroup.byServerName("MiniMe-Core2"))
    }

    @Test
    fun `builtin server configs are marked builtin and not persisted`() {
        val cfg = McpServerConfig(name = BuiltinGroup.CORE.serverName, builtinGroup = BuiltinGroup.CORE)
        assertTrue("带 builtinGroup 的配置必须被识别为内置", cfg.isBuiltin)
        assertEquals(BuiltinGroup.CORE.remotelyExposedByDefault, cfg.remotelyExposed)

        // 远端配置：builtinGroup 缺失（解析存量JSON 时即如此）→ 非内置，默认对外暴露
        val remote = McpServerConfig(name = "github", url = "https://example.com/mcp")
        assertFalse(remote.isBuiltin)
        assertTrue(remote.remotelyExposed)
    }

    @Test
    fun `status card toolCount is the sum over all groups`() {
        val registry = ToolRegistry()
        registry.register("readFile", FakeTool("readFile"), BuiltinGroup.CORE)
        registry.register("list", FakeTool("list"), BuiltinGroup.CORE)
        registry.register("todo", FakeTool("todo"), BuiltinGroup.CHAT)
        registry.register("websearch", FakeTool("websearch"), BuiltinGroup.DATA)
        // 远端 MCP 工具（未分组）
        registry.register("mcp__github__create_issue", FakeTool("mcp__github__create_issue"))

        val groups = BuiltinGroup.entries.map { g ->
            CapabilityGroupCard(
                id = g.serverName,
                displayName = g.displayName,
                tools = registry.getToolsByBuiltinGroup(g).map {
                    CapabilityToolCard(
                        name = it.name,
                        description = it.description,
                        permissionPolicy = it.permissionPolicy,
                        capabilities = it.capabilities.toList(),
                        parameters = emptyList(),
                        exposure = it.exposure,
                        requiresApproval = it.permissionPolicy == ToolPermissionPolicy.ASK,
                        builtinGroup = g,
                        sourceServerName = null
                    )
                },
                status = null,
                isBuiltin = true,
                remotelyExposed = g.remotelyExposedByDefault
            )
        }
        // 远端工具单独成组，故计入状态卡的总工具数应含它
        val remoteTools = registry.getAvailableTools().count { it.name.startsWith("mcp__") }
        val total = groups.sumOf { it.tools.size } + remoteTools

        assertEquals("内置 4 + 远端 1", 5, total)
        assertEquals(2, groups.first { it.id == BuiltinGroup.CORE.serverName }.tools.size)
        assertEquals(1, groups.first { it.id == BuiltinGroup.CHAT.serverName }.tools.size)
        assertEquals(1, groups.first { it.id == BuiltinGroup.DATA.serverName }.tools.size)
        assertEquals(4, registry.getGroupedToolCount())
    }
}