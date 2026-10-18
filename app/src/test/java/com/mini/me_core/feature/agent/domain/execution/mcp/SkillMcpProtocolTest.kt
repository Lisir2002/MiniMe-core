package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.mcp.server.AgentToolMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.mcp.server.EmptySkillMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerSession
import com.mini.me_core.feature.agent.domain.execution.mcp.server.SkillMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 * F4：`prompts/list` / `prompts/get` 与 `resources/list` / `resources/read` 两个 MCP 原生方法的协议往返。
 *
 * 走真实的 [McpServerSession.handle]，验证的是**线上字节**：
 * 1. `prompts/list` / `resources/list` 的 result 形状；
 * 2. `prompts/get` 回 messages（role/content），`resources/read` 回 contents（uri/mimeType/text）；
 * 3. 未知名称/畸形 URI 按规范回 `-32602`（Invalid params）而非 `-32601`——
 *    方法存在、参数值无效，这是协议明确区分的两种错误；
 * 4. `initialize` 的 capabilities 按是否接入技能**条件声明**。
 */
class SkillMcpProtocolTest {

    private lateinit var session: McpServerSession

    private val json = Json { ignoreUnknownKeys = true }

    private fun promptSkill(id: String) = Skill(
        id = id,
        name = "写作助手",
        description = "帮你写文档",
        type = SkillType.PROMPT,
        instructions = "请用要点式写作。"
    )

    private val scriptSkill = Skill(
        id = "s1",
        name = "构建脚本",
        description = "执行构建",
        type = SkillType.SCRIPT,
        entry = "build.sh",
        instructions = ""
    )

    /**
     * 可控技能源：只提供技能列表，不碰磁盘/DB。
     *
     * **同样执行 type/enabled 分派**（与 [DefaultSkillMcpAdapter] 口径一致）——
     * 若桩不做分派，「MCP 型不出现在列表」这类测试就变成在测桩而非测实现。
     */
    private class StubSkillAdapter(
        private val prompts: List<Skill> = emptyList(),
        private val resources: List<Skill> = emptyList()
    ) : SkillMcpAdapter {
        private val visiblePrompts = prompts.filter { it.type == SkillType.PROMPT && it.enabled }
        private val visibleResources = resources.filter { it.type == SkillType.SCRIPT && it.enabled }

        override fun hasSkills(): Boolean = visiblePrompts.isNotEmpty() || visibleResources.isNotEmpty()
        override fun listPrompts(): List<Skill> = visiblePrompts
        override fun getPrompt(id: String): Skill? = visiblePrompts.firstOrNull { it.id == id }
        override fun listResources(): List<Skill> = visibleResources
        override fun getResource(uri: String): Skill? {
            val id = uri.substringAfterLast('/')
            return visibleResources.firstOrNull { it.id == id }
        }
    }

    private fun newSession(skillAdapter: SkillMcpAdapter) {
        val registry = ToolRegistry()
        val adapter = AgentToolMcpAdapter(registry, ToolPermissionManager(), requireApproval = false)
        session = McpServerSession(adapter, skillAdapter = skillAdapter)
    }

    private fun call(method: String, params: JsonObject? = null): JsonObject? = runBlocking {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            params?.let { put("params", it) }
        }
        session.handle(payload.toString())
    }

    private fun result(method: String, params: JsonObject? = null): JsonObject {
        val res = call(method, params)
        assertNotNull("$method 应有响应", res)
        return res!!["result"] as? JsonObject ?: error("$method 无 result：$res")
    }

    private fun errorCode(method: String, params: JsonObject? = null): Int {
        val res = call(method, params)
        assertNotNull("$method 应有响应", res)
        val err = res!!["error"] as? JsonObject ?: error("$method 应返回 error：$res")
        return (err["code"] as JsonPrimitive).content.toInt()
    }

    @Before
    fun setUp() {
        newSession(StubSkillAdapter(prompts = listOf(promptSkill("p1")), resources = listOf(scriptSkill)))
    }

    // ── list ────────────────────────────────────────────────

    @Test
    fun `prompts-list 返回 name title description`() {
        val prompts = result("prompts/list")["prompts"] as JsonArray
        assertEquals(1, prompts.size)
        val first = prompts.first() as JsonObject
        assertEquals("p1", (first["name"] as JsonPrimitive).content)
        assertEquals("写作助手", (first["title"] as JsonPrimitive).content)
        assertEquals("帮你写文档", (first["description"] as JsonPrimitive).content)
    }

    @Test
    fun `prompts-list 单页返回不带 nextCursor`() {
        val res = result("prompts/list")
        assertFalse("单页不应带 nextCursor", res.containsKey("nextCursor"))
    }

    @Test
    fun `resources-list 返回 uri name mimeType`() {
        val resources = result("resources/list")["resources"] as JsonArray
        assertEquals(1, resources.size)
        val first = resources.first() as JsonObject
        assertEquals("minime://skill/s1", (first["uri"] as JsonPrimitive).content)
        assertEquals("s1", (first["name"] as JsonPrimitive).content)
        assertTrue((first["mimeType"] as JsonPrimitive).content.startsWith("text/plain"))
    }

    // ── get ────────────────────────────────────────────────

    @Test
    fun `prompts-get 回单条user 消息`() {
        val res = result("prompts/get", buildJsonObject { put("name", "p1") })
        val messages = res["messages"] as JsonArray
        assertEquals(1, messages.size)
        val msg = messages.first() as JsonObject
        assertEquals("user", (msg["role"] as JsonPrimitive).content)
        val content = msg["content"] as JsonObject
        assertEquals("text", (content["type"] as JsonPrimitive).content)
        assertEquals("请用要点式写作。", (content["text"] as JsonPrimitive).content)
    }

    @Test
    fun `resources-read 回 contents 且只给脚本文本不执行`() {
        val res = result("resources/read", buildJsonObject { put("uri", "minime://skill/s1") })
        val contents = res["contents"] as JsonArray
        assertEquals(1, contents.size)
        val c = contents.first() as JsonObject
        assertEquals("minime://skill/s1", (c["uri"] as JsonPrimitive).content)
        val text = (c["text"] as JsonPrimitive).content
        assertTrue("应含入口脚本名", text.contains("build.sh"))
        assertTrue("应明示不执行", text.contains("runSkillScript"))
    }

    // ── 错误码 ──────────────────────────────────────────────

    @Test
    fun `未知 prompt 名回 32602 而非 32601`() {
        assertEquals(-32602, errorCode("prompts/get", buildJsonObject { put("name", "nope") }))
    }

    @Test
    fun `未知 resource uri 回 32602 而非 32601`() {
        assertEquals(-32602, errorCode("resources/read", buildJsonObject { put("uri", "minime://skill/nope") }))
    }

    @Test
    fun `缺 params 回 32602`() {
        assertEquals(-32602, errorCode("prompts/get"))
        assertEquals(-32602, errorCode("resources/read"))
    }

    @Test
    fun `缺 name 或 uri 字段回 32602`() {
        assertEquals(-32602, errorCode("prompts/get", buildJsonObject { }))
        assertEquals(-32602, errorCode("resources/read", buildJsonObject { }))
    }

    @Test
    fun `确实不存在的方法才回 32601`() {
        assertEquals(-32601, errorCode("prompts/subscribe"))
    }

    // ── capabilities 条件声明 ───────────────────────────────

    @Test
    fun `接入技能时 initialize 声明 prompts 与 resources`() {
        val caps = result("initialize")["capabilities"] as JsonObject
        assertTrue(caps.containsKey("prompts"))
        assertTrue(caps.containsKey("resources"))
        assertTrue(caps.containsKey("tools"))
    }

    @Test
    fun `未接入技能时不声明 prompts 与 resources`() {
        newSession(EmptySkillMcpAdapter)
        val caps = result("initialize")["capabilities"] as JsonObject
        assertFalse(caps.containsKey("prompts"))
        assertFalse(caps.containsKey("resources"))
    }

    @Test
    fun `未接入技能时 list 方法仍可用但返回空数组`() {
        newSession(EmptySkillMcpAdapter)
        assertEquals(0, (result("prompts/list")["prompts"] as JsonArray).size)
        assertEquals(0, (result("resources/list")["resources"] as JsonArray).size)
    }

    @Test
    fun `MCP 型技能不出现在任何列表`() {
        val mcpOnly = Skill(
            id = "m1",
            name = "远端技能",
            description = "包装 MCP 工具",
            type = SkillType.MCP,
            instructions = ""
        )
        newSession(StubSkillAdapter(prompts = listOf(mcpOnly)))
        assertEquals(0, (result("prompts/list")["prompts"] as JsonArray).size)
        val caps = result("initialize")["capabilities"] as JsonObject
        assertFalse("无任何可暴露技能时不应声明 prompts", caps.containsKey("prompts"))
    }

    @Test
    fun `内置分组 server 不接技能故不声明 prompts`() {
        val registry = ToolRegistry()
        val adapter = AgentToolMcpAdapter(registry, ToolPermissionManager(), requireApproval = false)
        val builtin = McpServerSession(adapter, serverName = "MiniMe-Core")
        val res = runBlocking {
            builtin.handle(
                json.parseToJsonElement(
                    """{"jsonrpc":"2.0","id":1,"method":"initialize"}"""
                ).toString()
            )
        }
        val caps = (res!!["result"] as JsonObject)["capabilities"] as JsonObject
        assertNull(caps["prompts"])
        assertNull(caps["resources"])
    }
}
