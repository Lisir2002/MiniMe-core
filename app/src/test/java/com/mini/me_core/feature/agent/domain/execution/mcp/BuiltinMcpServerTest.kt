package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.mcp.server.AgentToolMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionChoice
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F1：本地能力作为**真实 MCP server** 的端到端验证。
 *
 * 走的是与远端完全相同的链路：
 * `McpClient` → [BuiltinTransport] → `McpServerSession` → [AgentToolMcpAdapter] → `ToolRegistry`。
 *
 * 重点验证三件事：
 * 1. 握手与枚举：三分组各自 `tools/list` 拿到**互不重叠**的工具集，且工具名保持原名
 *    （不被 `McpTool.buildNamespacedName` 命名空间化——那会破坏批次 B 的 CORE 序与历史会话）；
 * 2. 调用：`tools/call` 能真实执行并回 content 块；
 * 3. 边界：未知方法、id 失配、远程黑名单、跨组调用、用户拒绝审批。
 */
class BuiltinMcpServerTest {

    private lateinit var registry: ToolRegistry
    private lateinit var factory: BuiltinMcpServerFactory

    private class EchoTool(
        override val name: String,
        override val description: String = "回显工具",
        override val permissionPolicy: ToolPermissionPolicy = ToolPermissionPolicy.AUTO_APPROVE,
        override val parameters: Map<String, ToolParameter> = emptyMap()
    ) : AgentTool() {
        override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
            val echoed = (args["echo"] as? JsonPrimitive)?.content
            return ToolResult.Success(buildJsonObject { put("ok", echoed ?: "done") })
        }
    }

    @Before
    fun setUp() {
        registry = ToolRegistry()
        // CORE：读文件
        registry.register("readFile", EchoTool("readFile"), BuiltinGroup.CORE)
        registry.register("writeFile", EchoTool("writeFile"), BuiltinGroup.CORE)
        // CHAT：含远程黑名单里的 terminal
        registry.register("todo", EchoTool("todo"), BuiltinGroup.CHAT)
        registry.register("terminal", EchoTool("terminal"), BuiltinGroup.CHAT)
        // DATA
        registry.register("gitops", EchoTool("gitops"), BuiltinGroup.DATA)
        // 远端 MCP 工具：无分组，不应出现在任何内置 server 的 tools/list 里
        registry.register("mcp__remote__fetch", EchoTool("mcp__remote__fetch"))
        factory = BuiltinMcpServerFactory(registry, ToolPermissionManager())
    }

    private fun toolsOf(group: BuiltinGroup): List<String> = runBlocking {
        val client = McpClient(group.serverName, factory.createTransport(group))
        client.connect()
        client.tools.map { it.name }.also { client.close() }
    }

    // ── 握手与枚举 ────────────────────────────────────────────────

    @Test
    fun `each group exposes exactly its own tools`() {
        assertEquals(listOf("readFile", "writeFile"), toolsOf(BuiltinGroup.CORE).sorted())
        assertEquals(listOf("terminal", "todo"), toolsOf(BuiltinGroup.CHAT).sorted())
        assertEquals(listOf("gitops"), toolsOf(BuiltinGroup.DATA))
    }

    @Test
    fun `groups do not leak into each other`() {
        val core = toolsOf(BuiltinGroup.CORE)
        val chat = toolsOf(BuiltinGroup.CHAT)
        val data = toolsOf(BuiltinGroup.DATA)
        assertTrue("分组间工具不应重叠", (core intersect chat.toSet()).isEmpty())
        assertTrue((core intersect data.toSet()).isEmpty())
        assertTrue((chat intersect data.toSet()).isEmpty())
    }

    @Test
    fun `remote mcp tools are not exposed by any builtin server`() {
        val all = toolsOf(BuiltinGroup.CORE) + toolsOf(BuiltinGroup.CHAT) + toolsOf(BuiltinGroup.DATA)
        assertFalse("远端工具不得被内置 server 枚举", all.contains("mcp__remote__fetch"))
    }

    @Test
    fun `builtin server bypasses remote blacklist so tools never vanish`() {
        // terminal 在 REMOTE_BLACKLIST 内；若沿用黑名单，CHAT 分组会显示 1/2。
        assertEquals(2, toolsOf(BuiltinGroup.CHAT).size)
        assertTrue(toolsOf(BuiltinGroup.CHAT).contains("terminal"))
    }

    @Test
    fun `initialize handshake succeeds and reports tool count`() = runBlocking {
        val client = McpClient(BuiltinGroup.DATA.serverName, factory.createTransport(BuiltinGroup.DATA))
        val count = client.connect()
        assertEquals(1, count)
        client.close()
    }

    @Test
    fun `tool descriptors carry schema and description`() = runBlocking {
        val client = McpClient(BuiltinGroup.CORE.serverName, factory.createTransport(BuiltinGroup.CORE))
        client.connect()
        val readFile = client.tools.first { it.name == "readFile" }
        assertEquals("回显工具", readFile.description)
        assertNotNull("inputSchema 必须透传，供 UI 渲染参数标签", readFile.inputSchema)
        client.close()
    }

    // ── 调用 ──────────────────────────────────────────────────────

    @Test
    fun `tools_call executes and returns content block`() = runBlocking {
        val client = McpClient(BuiltinGroup.CORE.serverName, factory.createTransport(BuiltinGroup.CORE))
        client.connect()
        val text = client.callTool("readFile", buildJsonObject { put("echo", "hello") })
        assertTrue("应回显参数内容，实际: $text", text.contains("hello"))
        client.close()
    }

    @Test
    fun `unknown tool returns isError instead of throwing`() = runBlocking {
        val client = McpClient(BuiltinGroup.CORE.serverName, factory.createTransport(BuiltinGroup.CORE))
        client.connect()
        val text = client.callTool("notExist", buildJsonObject { })
        assertTrue(text.startsWith("Error:"))
        client.close()
    }

    @Test
    fun `cross group call is rejected`() = runBlocking {
        val client = McpClient(BuiltinGroup.CORE.serverName, factory.createTransport(BuiltinGroup.CORE))
        client.connect()
        // todo 属于 CHAT 分组，CORE server 不得代为执行
        val text = client.callTool("todo", buildJsonObject { })
        assertTrue(text.startsWith("Error:"))
        assertTrue(text.contains("TOOL_NOT_IN_GROUP"))
        client.close()
    }

    // ── 传输层边界 ────────────────────────────────────────────────

    @Test
    fun `unknown rpc method surfaces as McpException with code`() = runBlocking {
        val transport = factory.createTransport(BuiltinGroup.CORE)
        val e = runCatching { transport.request("resources/list") }.exceptionOrNull()
        assertTrue("未知方法应抛 McpException，实际: $e", e is McpException)
        assertEquals(-32601, (e as McpException).rpcCode)
    }

    @Test
    fun `request after close is rejected`() = runBlocking {
        val transport = factory.createTransport(BuiltinGroup.CORE)
        transport.close()
        val e = runCatching { transport.request("tools/list") }.exceptionOrNull()
        assertTrue("关闭后请求应失败，实际: $e", e is IllegalStateException)
    }

    @Test
    fun `notify never throws even when server ignores it`() = runBlocking {
        val transport = factory.createTransport(BuiltinGroup.CORE)
        // 通知无 id，服务端按规范不应答；传输层必须吞掉 null 而不是抛异常
        transport.notify("notifications/initialized")
        transport.notify("notifications/progress", buildJsonObject { put("progress", 1) })
    }

    @Test
    fun `close is idempotent`() {
        val transport = factory.createTransport(BuiltinGroup.CORE)
        transport.close()
        transport.close()
    }

    @Test
    fun `reused transport keeps incrementing request ids`() = runBlocking {
        val transport = factory.createTransport(BuiltinGroup.CORE)
        val first = transport.request("tools/list")
        val second = transport.request("tools/list")
        assertTrue("id 应递增", (second.id ?: 0) > (first.id ?: 0))
    }

    @Test
    fun `response id mismatch is rejected`() = runBlocking {
        // 桩 session 回一个 id 对不上的响应，验证传输层的配对校验
        val badSession = BuiltinMcpSession { """{"jsonrpc":"2.0","id":9999,"result":{}}""" }
        val transport = BuiltinTransport("stub", badSession)
        val e = runCatching { transport.request("tools/list") }.exceptionOrNull()
        assertTrue("id 失配应抛 McpException，实际: $e", e is McpException)
        assertTrue(e!!.message!!.contains("id 不匹配"))
    }

    // ── 审批拒绝通路 ──────────────────────────────────────────────

    @Test
    fun `ask-policy tool call is blocked when user rejects`() = runBlocking {
        val permissionManager = ToolPermissionManager()
        val rejectingRegistry = ToolRegistry().apply {
            register(
                "git_remote",
                EchoTool("git_remote", permissionPolicy = ToolPermissionPolicy.ASK),
                BuiltinGroup.DATA
            )
        }
        val localFactory = BuiltinMcpServerFactory(rejectingRegistry, permissionManager)
        val client = McpClient(BuiltinGroup.DATA.serverName, localFactory.createTransport(BuiltinGroup.DATA))
        client.connect()

        // UNDISPATCHED 保证协程立即跑到 awaitApproval 挂起点，之后再回传 REJECT
        val text = coroutineScope {
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                client.callTool("git_remote", buildJsonObject { })
            }
            var appeared = false
            repeat(100) {
                if (permissionManager.hasPendingFor(null)) {
                    appeared = true
                    return@repeat
                }
                delay(10)
            }
            assertTrue("ASK 工具调用应挂起等待审批", appeared)
            permissionManager.cancelPending(sessionId = null, choice = PermissionChoice.REJECT)
            pending.await()
        }
        assertTrue("用户拒绝后应返回 PERMISSION_DENIED，实际: $text", text.contains("PERMISSION_DENIED"))
        client.close()
    }

    // ── 工厂元数据 ────────────────────────────────────────────────

    @Test
    fun `builtinServers exposes three configs with exposure defaults`() {
        val configs = factory.builtinServers()
        assertEquals(3, configs.size)
        assertTrue(configs.all { it.isBuiltin })
        assertTrue("内置 server 不应有 url/command", configs.all { it.url == null && it.command == null })
        assertEquals(listOf("MiniMe-Core", "MiniMe-Chat", "MiniMe-Data"), configs.map { it.name })
        val core = configs.first { it.builtinGroup == BuiltinGroup.CORE }
        val chat = configs.first { it.builtinGroup == BuiltinGroup.CHAT }
        assertTrue("CORE 组默认允许外部访问", core.remotelyExposed)
        assertFalse("CHAT 组默认拒绝外部访问", chat.remotelyExposed)
    }

    @Test
    fun `config round-trips builtinGroup through serialization`() {
        val original = factory.builtinServers().first()
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(McpServerConfig.serializer(), original)
        val decoded = json.decodeFromString(McpServerConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `legacy config without builtinGroup decodes to null`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            McpServerConfig.serializer(),
            """{"name":"remote","url":"https://example.com/mcp"}"""
        )
        assertNull(decoded.builtinGroup)
        assertFalse("存量远端配置不得被误判为内置 server", decoded.isBuiltin)
        assertTrue("存量远端配置保持 remotelyExposed 默认 true", decoded.remotelyExposed)
    }

    // ── 与 HostServer 的行为隔离 ──────────────────────────────────

    @Test
    fun `host server adapter still applies remote blacklist`() {
        val hostLike = AgentToolMcpAdapter(
            toolRegistry = registry,
            permissionManager = ToolPermissionManager(),
            requireApproval = false
        )
        val hostNames = hostLike.listTools().map { it.name }
        assertFalse("HostServer 侧黑名单仍应生效", hostNames.contains("terminal"))
        assertTrue(hostNames.contains("readFile"))
    }

    @Test
    fun `host server adapter still exposes remote mcp tools`() {
        val hostLike = AgentToolMcpAdapter(
            toolRegistry = registry,
            permissionManager = ToolPermissionManager(),
            requireApproval = false
        )
        assertTrue(
            "HostServer 行为不变：远端工具照常暴露",
            hostLike.listTools().any { it.name == "mcp__remote__fetch" }
        )
    }

    @Test
    fun `builtin adapter listTools carries schema for every tool`() {
        val descriptors = AgentToolMcpAdapter(
            toolRegistry = registry,
            permissionManager = ToolPermissionManager(),
            requireApproval = false,
            builtinGroup = BuiltinGroup.CORE,
            respectRemoteBlacklist = false
        ).listTools()
        assertEquals(2, descriptors.size)
        descriptors.forEach {
            assertNotNull("${it.name} 缺 inputSchema", it.inputSchema)
        }
    }
}
