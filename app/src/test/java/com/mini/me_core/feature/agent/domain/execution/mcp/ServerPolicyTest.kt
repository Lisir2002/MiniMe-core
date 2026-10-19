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
 * 1. 白名单与黑名单同时命中时到底谁优先（约定：白名单优先，见 [ServerPolicy]）；
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
        assertEquals(ServerPolicy(setOf("a"), setOf("a", "b"), setOf("b")), ServerPolicy.from(
            McpServerConfig(
                name = "s",
                url = "u",
                disabledTools = setOf("a"),
                enabledTools = setOf("a", "b"),
                requireApprovalTools = setOf("b")
            )
        ))
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
}
