package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F3：`requireApprovalTools` 执行侧接通的回归测试。
 *
 * 这个配置此前是**断线**的：用户能在 `McpEditorDialog` 里勾选「需审批」，勾选结果会
 * 正确写入 `mcp.json`，但执行侧没有任何地方读它——工具照样直接放行。这是权限提示
 * 失真，属安全缺陷而非 UI bug，故用测试钉死「勾了就必须真的是 ASK」。
 */
class McpToolApprovalPolicyTest {

    private fun client() = McpClient(serverName = "github", transport = StubTransport)

    private fun tool(remoteName: String, policy: ServerPolicy): McpTool =
        McpTool(client(), McpToolDescriptor(name = remoteName), policy)

    @Test
    fun `grey listed tool becomes ASK`() {
        val t = tool("push", ServerPolicy(requireApprovalTools = setOf("push")))
        assertEquals(
            "用户勾选「需审批」的工具必须在执行侧真的走审批",
            ToolPermissionPolicy.ASK,
            t.permissionPolicy
        )
    }

    @Test
    fun `tool not in grey list stays AUTO_APPROVE`() {
        val t = tool("pull", ServerPolicy(requireApprovalTools = setOf("push")))
        assertEquals(ToolPermissionPolicy.AUTO_APPROVE, t.permissionPolicy)
    }

    @Test
    fun `unrestricted policy leaves tools auto approved`() {
        val t = tool("anything", ServerPolicy.Unrestricted)
        assertEquals(ToolPermissionPolicy.AUTO_APPROVE, t.permissionPolicy)
    }

    @Test
    fun `hidden tool is not marked as ASK`() {
        val policy = ServerPolicy(
            disabledTools = setOf("x"),
            requireApprovalTools = setOf("x")
        )
        val t = tool("x", policy)
        assertFalse("不可见工具不参与审批判定", t.permissionPolicy == ToolPermissionPolicy.ASK)
    }

    @Test
    fun `serverName and remoteName are preserved for routing`() {
        val t = tool("create_issue", ServerPolicy(requireApprovalTools = setOf("create_issue")))
        assertEquals("远端原始名用于 tools/call", "create_issue", t.remoteName)
        assertEquals("归属 server 名用于按 server 分组", "github", t.serverName)
        // 命名空间化后的名字才是模型看到与审批卡展示的名字
        assertEquals("mcp__github__create_issue", t.name)
    }

    @Test
    fun `approval marking is independent of exposure level`() {
        // 需审批与「按需加载」是两个正交维度：DEFERRED 工具同样可能必须审批。
        // 二者若被混为一谈，就会出现「加载了就没审批」这类致命漏洞。
        val t = tool("push", ServerPolicy(requireApprovalTools = setOf("push")))
        assertEquals(
            com.mini.me_core.feature.agent.domain.execution.tool.ToolExposure.DEFERRED,
            t.exposure
        )
        assertTrue(t.permissionPolicy == ToolPermissionPolicy.ASK)
    }

    @Test
    fun `legacy config without whitelist produces no ASK tools`() {
        // 存量 mcp.json 无白名单：除灰名单外全部维持 AUTO_APPROVE，
        // 保证 F3 接通不会让既有用户的工具突然开始弹审批。
        val cfg = McpServerConfig(
            name = "legacy",
            url = "https://example.com/mcp",
            disabledTools = setOf("a")
        )
        val policy = ServerPolicy.from(cfg)
        assertEquals(ToolPermissionPolicy.AUTO_APPROVE, tool("b", policy).permissionPolicy)
    }

    @Test
    fun `whitelisted grey list tool still requires approval`() {
        val cfg = McpServerConfig(
            name = "s",
            url = "https://example.com/mcp",
            enabledTools = setOf("a", "b"),
            requireApprovalTools = setOf("b")
        )
        val policy = ServerPolicy.from(cfg)
        assertEquals(ToolPermissionPolicy.ASK, tool("b", policy).permissionPolicy)
        assertEquals(ToolPermissionPolicy.AUTO_APPROVE, tool("a", policy).permissionPolicy)
    }

    /**
     * 只需满足构造契约：本测试只验证审批标记（构造期判定），从不触发真实调用。
     * `request` 返回空结果而非抛错，是为了让「万一将来测试里加了调用」也不会误挂。
     */
    private object StubTransport : McpTransport {
        override suspend fun request(method: String, params: JsonObject?): JsonRpcResponse =
            JsonRpcResponse(id = 1, result = JsonObject(emptyMap()))

        override suspend fun notify(method: String, params: JsonObject?) = Unit

        override fun close() = Unit
    }
}
