package com.mini.me_core.feature.backup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份策略纯逻辑测试（无 Android/IO 依赖）：
 * - 周期性备份到期判定 [isPeriodicBackupDue]
 * - 口令强度评估 [evaluatePasswordStrength]
 * - RestoreStats 累加语义
 */
class BackupPolicyTest {

    private val dayMs = 24L * 60 * 60 * 1000

    // ── 周期性备份到期判定 ──────────────────────────────────────

    @Test
    fun `周期备份_间隔关闭_永不到期`() {
        assertFalse(isPeriodicBackupDue(lastMs = 0L, intervalDays = 0, nowMs = 10L))
        assertFalse(isPeriodicBackupDue(lastMs = 1_000L, intervalDays = 0, nowMs = 10_000L))
    }

    @Test
    fun `周期备份_从未备份_立即到期`() {
        assertTrue(isPeriodicBackupDue(lastMs = 0L, intervalDays = 3, nowMs = 10L))
    }

    @Test
    fun `周期备份_未到间隔_不到期`() {
        val last = 1_000_000L
        assertFalse(isPeriodicBackupDue(lastMs = last, intervalDays = 7, nowMs = last + 3 * dayMs))
    }

    @Test
    fun `周期备份_刚好达到间隔_到期`() {
        val last = 1_000_000L
        assertTrue(isPeriodicBackupDue(lastMs = last, intervalDays = 7, nowMs = last + 7 * dayMs))
    }

    @Test
    fun `周期备份_超过间隔_到期`() {
        val last = 1_000_000L
        assertTrue(isPeriodicBackupDue(lastMs = last, intervalDays = 1, nowMs = last + 30 * dayMs))
    }

    // ── 口令强度 ──────────────────────────────────────

    @Test
    fun `口令为空_EMPTY`() {
        assertEquals(PasswordStrength.EMPTY, evaluatePasswordStrength(""))
    }

    @Test
    fun `口令过短纯字母_WEAK`() {
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("abc"))
    }

    @Test
    fun `口令8位混合字母数字_MEDIUM`() {
        assertEquals(PasswordStrength.MEDIUM, evaluatePasswordStrength("abc12345"))
    }

    @Test
    fun `口令长且含符号_STRONG`() {
        assertEquals(PasswordStrength.STRONG, evaluatePasswordStrength("Abc12345!@#xyz"))
    }

    @Test
    fun `口令纯数字短_WEAK`() {
        assertEquals(PasswordStrength.WEAK, evaluatePasswordStrength("12345"))
    }

    // ── RestoreStats 累加 ──────────────────────────────────────

    @Test
    fun `RestoreStats_逐域累加`() {
        val a = RestoreStats(chatSessions = 2, agentMessages = 10)
        val b = RestoreStats(chatSessions = 3, providers = 1)
        val sum = a + b
        assertEquals(5, sum.chatSessions)
        assertEquals(10, sum.agentMessages)
        assertEquals(1, sum.providers)
        assertEquals(0, sum.gitCredentials)
    }

    @Test
    fun `BackupPreview_有聊天历史判定`() {
        val p = BackupPreview(
            backup = RestoreStats(chatSessions = 1),
            current = RestoreStats(chatSessions = 5),
        )
        assertTrue(p.hasChatHistory)
        val empty = BackupPreview(backup = RestoreStats(providers = 1), current = RestoreStats())
        assertFalse(empty.hasChatHistory)
    }
}
