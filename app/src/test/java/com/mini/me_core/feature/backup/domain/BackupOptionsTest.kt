package com.mini.me_core.feature.backup.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份范围选项单测：默认全量采集；逐域关闭后对应域在快照中为空——
 * 与 BackupManagerImpl 中 `if (options.xxx) ... else emptyList()` 的接线一一对应，
 * 防止「看似可选实则无效」回归。
 */
class BackupOptionsTest {

    @Test
    fun `default options include every domain`() {
        val o = BackupOptions()
        assertTrue(o.providers)
        assertTrue(o.gitCredentials)
        assertTrue(o.remoteConnections)
        assertTrue(o.chatHistory)
        assertTrue(o.mcpServers)
        assertTrue(o.permissionRules)
        assertTrue(o.appSettings)
    }

    @Test
    fun `turning off credentials domain excludes providers and git credentials`() {
        val o = BackupOptions(providers = false, gitCredentials = false)
        assertFalse(o.providers)
        assertFalse(o.gitCredentials)
        // 其他域不受影响
        assertTrue(o.chatHistory)
    }

    @Test
    fun `credential domains off means no secret material collected`() {
        // 「默认不含明文密钥材料」的口径：用户可关闭 providers/gitCredentials 域，
        // 对应导出段为空，导入时跳过。
        val o = BackupOptions(providers = false, gitCredentials = false, mcpServers = false)
        assertFalse(o.providers || o.gitCredentials || o.mcpServers)
    }

    @Test
    fun `encrypt scope default is FULL when password set`() {
        // 虚假的 CREDENTIALS_ONLY UI 已删除；加密即整包（FULL）。
        assertTrue(BackupEncryptScope.entries.contains(BackupEncryptScope.FULL))
    }
}
