package com.mini.me_core.feature.settings.data

import com.mini.me_core.feature.agent.domain.execution.mcp.BuiltinMcpServerFactory
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.settings.data.repository.deferredToolLoadingEffective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1：三个能力治理灰度开关的**生效条件**。
 *
 * 这批之前，这些开关只有仓储层的 setter、零调用点——用户开不起来，
 * 于是批次 B / F1 / F2 / F4 的成果默认状态下全部不可见。G1 补上 UI 出口后，
 * 「开关写进 KV」与「功能真的生效」是两件事：本测试钉住后者。
 *
 * **为什么不用 mock 仓储**：[com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository]
 * 的 `isXxxActive()` 本身就是要验的对象，mock 它等于把被测逻辑一起桩掉——那种测试
 * 无论断言怎么写都能通过，看着绿，实际什么都没验证。这里改为直接测抽出的纯函数
 * 与真实的工厂对象。
 */
class FeatureGateEffectTest {

    // ── 延迟加载：与规范流总开关的与关系 ────────────────────────

    @Test
    fun `延迟加载 双开关同开才生效`() {
        assertTrue(deferredToolLoadingEffective(normFlowEnabled = true, deferredEnabled = true))
    }

    @Test
    fun `延迟加载 本开关关闭时恒不生效`() {
        assertFalse(deferredToolLoadingEffective(normFlowEnabled = true, deferredEnabled = false))
        assertFalse(deferredToolLoadingEffective(normFlowEnabled = false, deferredEnabled = false))
    }

    @Test
    fun `延迟加载 仅开本开关但总开关关时不生效`() {
        // 这是 UI 上「开关显示已开却没效果」的唯一成因，故单独立一条用例钉住。
        assertFalse(deferredToolLoadingEffective(normFlowEnabled = false, deferredEnabled = true))
    }

    // ── 内置能力 MCP 化 ────────────────────────────────────────

    private fun factory() = BuiltinMcpServerFactory(
        toolRegistry = ToolRegistry(),
        permissionManager = ToolPermissionManager()
    )

    @Test
    fun `内置 server 恒为三分组且分组名互不相同`() {
        val servers = factory().builtinServers()
        assertEquals(BuiltinGroup.entries.size, servers.size)
        assertEquals(
            "分组名重复会让 MCP 客户端无法区分工具来源",
            BuiltinGroup.entries.size,
            servers.map { it.name }.toSet().size
        )
    }

    @Test
    fun `对外可达分组恰为 CORE 一组`() {
        val exposed = factory().externallyExposedGroups()
        assertTrue("CORE 必须对外可达", BuiltinGroup.CORE in exposed)
        assertFalse("Chat 不应默认对外可达", BuiltinGroup.CHAT in exposed)
        assertFalse("Data 不应默认对外可达", BuiltinGroup.DATA in exposed)
    }

    @Test
    fun `内置 server 的 remotelyExposed 取自分组默认值`() {
        val byName = factory().builtinServers().associateBy { it.name }
        assertTrue(byName.getValue(BuiltinGroup.CORE.serverName).remotelyExposed)
        assertFalse(byName.getValue(BuiltinGroup.CHAT.serverName).remotelyExposed)
        assertFalse(byName.getValue(BuiltinGroup.DATA.serverName).remotelyExposed)
    }

    @Test
    fun `内置 server 均标记为 isBuiltin 不与远端混淆`() {
        assertTrue(factory().builtinServers().all { it.isBuiltin })
    }
}
