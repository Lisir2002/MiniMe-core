package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F3：[ServerPolicy] 判定矩阵的真值表测试。
 *
 * 三组集合（黑名单 / 白名单 / 灰名单）的语义必须**互不混淆**。历史上最容易出错的
 * 两点是：
 * 1. 白名单与黑名单同时命中时到底谁优先（约定：**黑名单优先**，见 [ServerPolicy]
 *    类注释中联网核实的生态依据）；
 * 2. 「空集合」被当成「白名单为空」而把该 server 的工具全部隐藏——空集必须解释为
 *    「未启用白名单」。这两点都用真值表钉死。
 */
class ServerPolicyTest {

    // ── 黑名单（未启用白名单） ──────────────────────────────────────

    @Test
    fun `empty policy allows everything`() {
        val policy = ServerPolicy()
        listOf("a", "b", "c").forEach {
            assertTrue("未配置任何集合时工具应可见：$it", policy.isToolEnabled(it))
            assertFalse(policy.requiresApproval(it))
        }
        assertFalse("空集合不等于启用了白名单", policy.hasWhitelist)
    }

    @Test
    fun `blacklisted tool is hidden`() {
        val policy = ServerPolicy(disabledTools = setOf("danger"))
        assertFalse(policy.isToolEnabled("danger"))
        assertTrue(policy.isToolEnabled("safe"))
    }

    // ── 白名单 ───────────────────────────────────────────────────

    @Test
    fun `whitelist narrows to only listed tools`() {
        val policy = ServerPolicy(enabledTools = setOf("read", "write"))
        assertTrue(policy.hasWhitelist)
        assertTrue(policy.isToolEnabled("read"))
        assertTrue(policy.isToolEnabled("write"))
        assertFalse("不在白名单内的工具必须隐藏", policy.isToolEnabled("delete"))
    }

    @Test
    fun `blacklist wins when a tool is in both lists`() {
        // 联网核实后的约定（见 ServerPolicy 类注释）：拒绝优先，与 mcp-cli 的
        // security-first precedence model、mcp-compress-router 的 "denylist wins" 一致。
        // 用户把工具同时写进两表，几乎总是说明他曾想封禁它——让黑名单赢才不会被白名单里
        // 残留的名字重新放开。
        val policy = ServerPolicy(
            enabledTools = setOf("a", "b"),
            disabledTools = setOf("a")
        )
        assertFalse("同时命中时应以黑名单为准", policy.isToolEnabled("a"))
        assertTrue(policy.isToolEnabled("b"))
        assertFalse(policy.isToolEnabled("c"))
    }

    @Test
    fun `blacklist still wins for tools outside the whitelist`() {
        // b 不在白名单 → 即便也不在黑名单，仍不可见
        val policy = ServerPolicy(
            enabledTools = setOf("a"),
            disabledTools = setOf("z")
        )
        assertFalse(policy.isToolEnabled("b"))
    }

    // ── 灰名单（需审批） ──────────────────────────────────────────

    @Test
    fun `grey listed tool requires approval`() {
        val policy = ServerPolicy(requireApprovalTools = setOf("push"))
        assertTrue(policy.requiresApproval("push"))
        assertFalse(policy.requiresApproval("pull"))
        // 需审批不等于不可见
        assertTrue(policy.isToolEnabled("push"))
    }

    @Test
    fun `hidden tool never requires approval`() {
        // 被黑名单隐藏的工具不参与审批判定：它根本不会被调用，
        // 给它挂审批标记只会让白名单/黑名单语义互相污染。
        val policy = ServerPolicy(
            disabledTools = setOf("x"),
            requireApprovalTools = setOf("x")
        )
        assertFalse(policy.isToolEnabled("x"))
        assertFalse("不可见工具不应标记为需审批", policy.requiresApproval("x"))
    }

    @Test
    fun `grey list is ignored for tools outside whitelist`() {
        // 白名单外的工具本来就不可见，不该同时被标成「需审批」
        val policy = ServerPolicy(
            enabledTools = setOf("a"),
            requireApprovalTools = setOf("b")
        )
        assertFalse("白名单外且要求审批的工具不可见", policy.isToolEnabled("b"))
        assertFalse(policy.requiresApproval("b"))
    }

    @Test
    fun `all three sets compose correctly`() {
        val policy = ServerPolicy(
            enabledTools = setOf("read", "write", "delete", "push"),
            disabledTools = setOf("delete"),
            requireApprovalTools = setOf("push", "write")
        )
        assertTrue(policy.isToolEnabled("read"))
        assertFalse("黑名单应隐藏", policy.isToolEnabled("delete"))
        assertFalse("白名单外应隐藏", policy.isToolEnabled("unknown"))

        assertFalse("read 未入灰名单", policy.requiresApproval("read"))
        assertTrue("write 入灰名单", policy.requiresApproval("write"))
        assertTrue("push 入灰名单", policy.requiresApproval("push"))
        assertFalse("delete 不可见故不审批", policy.requiresApproval("delete"))
    }

    @Test
    fun `blacklisted tool hidden even when whitelisted`() {
        // 与 all three sets compose 同源的边界：两表同时命中按拒绝优先处理
        val policy = ServerPolicy(
            enabledTools = setOf("a", "b"),
            disabledTools = setOf("a")
        )
        assertFalse(policy.isToolEnabled("a"))
        assertFalse("黑名单工具即便在白名单内也不参与审批", policy.requiresApproval("a"))
    }

    // ── 与配置的桥接 ─────────────────────────────────────────────

    @Test
    fun `from config carries all three sets`() {
        val cfg = McpServerConfig(
            name = "github",
            url = "https://example.com/mcp",
            disabledTools = setOf("x"),
            enabledTools = setOf("a", "b"),
            requireApprovalTools = setOf("b")
        )
        val policy = ServerPolicy.from(cfg)
        assertTrue(policy.hasWhitelist)
        assertTrue(policy.isToolEnabled("a"))
        assertTrue(policy.requiresApproval("b"))
        // 三组集合都确实被带过来：x 被黑名单隐藏
        assertFalse(policy.isToolEnabled("x"))
    }

    @Test
    fun `from legacy config without whitelist keeps all tools visible`() {
        // 存量 mcp.json 只有 disabledTools：无白名单时行为必须与 F3 之前逐字节一致
        val cfg = McpServerConfig(name = "legacy", url = "https://x/mcp", disabledTools = setOf("a"))
        val policy = ServerPolicy.from(cfg)
        assertFalse(policy.hasWhitelist)
        assertFalse(policy.isToolEnabled("a"))
        assertTrue(policy.isToolEnabled("b"))
    }

    @Test
    fun `unrestricted policy allows and never asks`() {
        val policy = ServerPolicy.Unrestricted
        assertTrue(policy.isToolEnabled("anything"))
        assertFalse(policy.requiresApproval("anything"))
        assertFalse(policy.hasWhitelist)
    }

    @Test
    fun `builtin server config yields unrestricted policy`() {
        // 内置 server 不落盘、无三组集合，策略须为空
        val cfg = McpServerConfig(name = "MiniMe-Core", builtinGroup = BuiltinGroup.CORE)
        val policy = ServerPolicy.from(cfg)
        assertFalse(policy.hasWhitelist)
        assertTrue(policy.isToolEnabled("readFile"))
        assertFalse(
            "内置工具的审批判定完全走本地 permissionPolicy，不经用户配置",
            policy.requiresApproval("readFile")
        )
    }

    @Test
    fun `policy is a value type`() {
        // G2：kind/enabled 也参与值语义，故期望值必须写全——
        // 只写三组集合会漏掉新字段，让「值类型」这条守卫形同虚设。
        assertEquals(
            ServerPolicy(
                disabledTools = setOf("a"),
                enabledTools = setOf("a", "b"),
                requireApprovalTools = setOf("b"),
                kind = ServerKind.REMOTE_HTTP,
                enabled = true
            ),
            ServerPolicy.from(
                McpServerConfig(
                    name = "s",
                    url = "u",
                    disabledTools = setOf("a"),
                    enabledTools = setOf("a", "b"),
                    requireApprovalTools = setOf("b")
                )
            )
        )
    }

    /**
     * [ServerPolicy.from] 必须映射 [McpServerConfig] 中**全部** `Set<String>` 型字段。
     *
     * 这条守卫是被真实缺陷逼出来的：`from()` 起初漏了 `enabledTools`，导致用户配的白名单
     * 静默失效——而所有针对 `from()` 的行为测试都会因为传的是「恰好只填了部分字段」的
     * 配置而看不出问题。靠人肉同步映射迟早再漏，故直接断言字段全集。
     */
    @Test
    fun `from maps every set-of-strings field of the config`() {
        val configSetFields = McpServerConfig::class.java.declaredFields
            .filter { Set::class.java.isAssignableFrom(it.type) }
            .map { it.name }
            .toSet()

        assertTrue(
            "前提：配置里应至少有三组集合（黑/白/灰名单），实际 $configSetFields",
            configSetFields.containsAll(
                setOf("disabledTools", "enabledTools", "requireApprovalTools")
            )
        )

        // 每组集合都给一个互不相同的哨兵值，from() 后若任一字段丢失，对应值会变 null/空
        val cfg = McpServerConfig(
            name = "s",
            url = "u",
            disabledTools = setOf("D"),
            enabledTools = setOf("E"),
            requireApprovalTools = setOf("R")
        )
        val policy = ServerPolicy.from(cfg)
        assertEquals(setOf("D"), policy.disabledTools)
        assertEquals(setOf("E"), policy.enabledTools)
        assertEquals(setOf("R"), policy.requireApprovalTools)

        // 反向断言：策略侧不得比配置侧多出集合字段（多出来的会永远是空集）
        val policySetFields = ServerPolicy::class.java.declaredFields
            .filter { Set::class.java.isAssignableFrom(it.type) }
            .map { it.name }
            .toSet()
        assertEquals(
            "策略字段集必须与配置字段集一一对应",
            configSetFields,
            policySetFields
        )
    }

    // ── G2：server 级字段 ─────────────────────────────────────────

    @Test
    fun `禁用 server 的工具一律不可见`() {
        // 生态通行约定（联网核实 2026-10）：VS Code「禁用即不启动、工具不进对话」、
        // Codex「enabled=false 避免连接该server」、Gemini CLI「禁用即不连接」。
        val policy = ServerPolicy(enabled = false)
        assertFalse(policy.isToolEnabled("anything"))
    }

    @Test
    fun `禁用 server 时白名单也救不回工具`() {
        val policy = ServerPolicy(enabled = false, enabledTools = setOf("a"))
        assertFalse(policy.isToolEnabled("a"))
    }

    @Test
    fun `禁用 server 时工具不需审批`() {
        val policy = ServerPolicy(enabled = false, requireApprovalTools = setOf("a"))
        assertFalse(policy.requiresApproval("a"))
    }

    @Test
    fun `启用 server 不改变原有判定语义`() {
        val policy = ServerPolicy(enabled = true, disabledTools = setOf("x"))
        assertFalse(policy.isToolEnabled("x"))
        assertTrue(policy.isToolEnabled("y"))
    }

    @Test
    fun `kind 由配置派生且优先级为内置大于 stdio 大于 HTTP`() {
        assertEquals(ServerKind.BUILTIN, ServerKind.derive(McpServerConfig(name = "b", builtinGroup = BuiltinGroup.CORE)))
        assertEquals(ServerKind.REMOTE_STDIO, ServerKind.derive(McpServerConfig(name = "s", command = "python mcp.py")))
        assertEquals(ServerKind.REMOTE_HTTP, ServerKind.derive(McpServerConfig(name = "h", url = "https://x/mcp")))
    }

    @Test
    fun `内置优先于传输字段`() {
        // 畸形配置：builtinGroup 与 url 同时存在。内置语义更具体，以其为准。
        val cfg = McpServerConfig(name = "weird", builtinGroup = BuiltinGroup.DATA, url = "https://x/mcp")
        assertEquals(ServerKind.BUILTIN, ServerKind.derive(cfg))
    }

    @Test
    fun `stdio 优先于 HTTP`() {
        // 同时带 command 与 url 时，command 是实际被执行的连接方式。
        val cfg = McpServerConfig(name = "both", command = "python mcp.py", url = "https://x/mcp")
        assertEquals(ServerKind.REMOTE_STDIO, ServerKind.derive(cfg))
    }

    @Test
    fun `空白 command 视为非 stdio`() {
        val cfg = McpServerConfig(name = "blank", command = "   ", url = "https://x/mcp")
        assertEquals(ServerKind.REMOTE_HTTP, ServerKind.derive(cfg))
    }

    @Test
    fun `from 映射 kind 与 enabled`() {
        val cfg = McpServerConfig(
            name = "s",
            url = "u",
            enabled = false,
            disabledTools = setOf("D")
        )
        val policy = ServerPolicy.from(cfg)
        assertEquals(ServerKind.REMOTE_HTTP, policy.kind)
        assertFalse(policy.enabled)
        assertFalse(policy.isToolEnabled("anything"))
    }

    @Test
    fun `内置策略的 isBuiltin 为真`() {
        assertTrue(ServerPolicy(kind = ServerKind.BUILTIN).isBuiltin)
        assertFalse(ServerPolicy(kind = ServerKind.REMOTE_HTTP).isBuiltin)
    }

    @Test
    fun `Unrestricted 恒为启用的内置策略`() {
        assertTrue(ServerPolicy.Unrestricted.enabled)
        assertEquals(ServerKind.BUILTIN, ServerPolicy.Unrestricted.kind)
    }

    @Test
    fun `kind 计入值语义`() {
        // 漏掉任一字段都会让「不同 server 的策略被误判为相同」，故单独立一条。
        assertEquals(
            ServerPolicy(kind = ServerKind.REMOTE_HTTP),
            ServerPolicy(kind = ServerKind.REMOTE_HTTP)
        )
        assertFalse(ServerPolicy(kind = ServerKind.REMOTE_HTTP) == ServerPolicy(kind = ServerKind.REMOTE_STDIO))
    }

    @Test
    fun `enabled 计入值语义`() {
        assertEquals(ServerPolicy(enabled = true), ServerPolicy(enabled = true))
        assertFalse(ServerPolicy(enabled = true) == ServerPolicy(enabled = false))
    }

    @Test
    fun `toString 含 kind 与 enabled 便于日志排查`() {
        val text = ServerPolicy(kind = ServerKind.REMOTE_STDIO, enabled = false).toString()
        assertTrue(text.contains("REMOTE_STDIO"))
        assertTrue(text.contains("enabled=false"))
    }
}
