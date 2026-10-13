package com.mini.me_core.feature.t2i.domain.permission

import com.mini.me_core.feature.settings.data.repository.ZthTierRepository
import com.mini.me_core.feature.t2i.domain.repository.T2IRepository
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_provider_models
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_providers
import com.mini.mecore.datalayer.sqldelight.t2i.T2i_task
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * T2I 权限策略引擎测试（issue #1：P4 月度额度此前恒被跳过，形同虚设）。
 *
 * 重点覆盖：
 * - P4 月度超限 → DENY + MONTHLY_QUOTA_EXCEEDED；
 * - P4 边界（刚好用满不过限）不误杀；
 * - 月度额度为 0 时 P4 不参与（免费用户默认不限）；
 * - **P4 的聚合起点必须是「本月 1 号 00:00」而不是「今日 00:00」**（回归关键）；
 * - P2/P3 日/会话额度既有行为不被破坏。
 *
 * 说明：ZthTierRepository 为 final class 且引擎当前未真正读取（tierQuotas() 恒回落默认），
 * 用 mockk relaxed mock 满足构造要求。
 */
class T2IPermissionPolicyEngineTest {

    private lateinit var repo: FakeT2IRepository
    private lateinit var engine: T2IPermissionPolicyEngine

    private val monthStartMs: Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val dayStartMs: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Before
    fun setUp() {
        repo = FakeT2IRepository(monthStartMs, dayStartMs)
        engine = T2IPermissionPolicyEngine(repo, mockk(relaxed = true))
    }

    // ══════════════ P4 月度额度 ══════════════

    @Test
    fun `P4 denies when monthly quota exceeded`() = runBlocking {
        repo.monthlyUsed = 90
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 20,
                overrideMonthlyQuotaTokens = 100,
                progressiveProtection = false,
            )
        )
        assertEquals(T2IPermissionPolicyEngine.Verdict.DENY, r.verdict)
        assertEquals("MONTHLY_QUOTA_EXCEEDED", r.denyCode)
        assertTrue("拒绝消息应含已用/上限与本次成本", r.denyMessage?.contains("90") == true)
    }

    @Test
    fun `P4 allows when monthly usage plus cost exactly hits quota`() = runBlocking {
        repo.monthlyUsed = 90
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 10, // 90 + 10 == 100，不大于上限 → 放行
                overrideMonthlyQuotaTokens = 100,
                progressiveProtection = false,
            )
        )
        assertEquals(
            "恰好用满月度额度不应拒绝（判定为 > 而非 >=）",
            T2IPermissionPolicyEngine.Verdict.ALLOW,
            r.verdict
        )
    }

    @Test
    fun `P4 skipped when monthly quota is zero`() = runBlocking {
        repo.monthlyUsed = Long.MAX_VALUE / 4 // 极端用量
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 10,
                overrideMonthlyQuotaTokens = 0, // 0 = 不限
                progressiveProtection = false,
            )
        )
        assertEquals("月度额度 0 表示不限，P4 不应参与", T2IPermissionPolicyEngine.Verdict.ALLOW, r.verdict)
    }

    @Test
    fun `P4 aggregates from month start not day start`() = runBlocking {
        repo.monthlyUsed = 999
        engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 1,
                overrideMonthlyQuotaTokens = 10,
                progressiveProtection = false,
            )
        )
        assertTrue(
            "P4 必须以「本月 1 号 00:00」为聚合起点，实际 cutoff=${repo.sumCutoffs}",
            repo.sumCutoffs.contains(monthStartMs)
        )
    }

    @Test
    fun `P4 denies before P5 progressive protection`() = runBlocking {
        repo.monthlyUsed = 100
        repo.successfulImagesToday = 50 // 同时满足 P5
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 10,
                overrideMonthlyQuotaTokens = 100,
                progressiveProtection = true,
            )
        )
        assertEquals("P4 优先级高于 P5，应先命中月度拒绝", T2IPermissionPolicyEngine.Verdict.DENY, r.verdict)
        assertEquals("MONTHLY_QUOTA_EXCEEDED", r.denyCode)
    }

    // ══════════════ P2 / P3 既有行为回归 ══════════════

    @Test
    fun `P2 daily quota still denies`() = runBlocking {
        repo.dailyUsed = 4990
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 20,
                overrideDailyQuotaTokens = 5000,
                progressiveProtection = false,
            )
        )
        assertEquals(T2IPermissionPolicyEngine.Verdict.DENY, r.verdict)
        assertEquals("DAILY_QUOTA_EXCEEDED", r.denyCode)
    }

    @Test
    fun `P3 session quota still denies`() = runBlocking {
        repo.sessionUsed = 1490
        val r = engine.evaluate(
            T2IPermissionPolicyEngine.Request(
                sessionId = "s1",
                costPerImageTokens = 20,
                overrideSessionQuotaTokens = 1500,
                progressiveProtection = false,
            )
        )
        assertEquals(T2IPermissionPolicyEngine.Verdict.DENY, r.verdict)
        assertEquals("SESSION_QUOTA_EXCEEDED", r.denyCode)
    }

    /** 最小 fake：只实现权限引擎用到的 3 个聚合，其余未走路径直接 fail。 */
    private class FakeT2IRepository(
        private val monthStartMs: Long,
        private val dayStartMs: Long,
    ) : T2IRepository {
        var monthlyUsed: Long = 0
        var dailyUsed: Long = 0
        var sessionUsed: Long = 0
        var successfulImagesToday: Long = 0

        /** 记录每次 sumDeductedTokensSince 的 cutoff，用于断言 P4 用的是月起点。 */
        val sumCutoffs = mutableListOf<Long>()

        override suspend fun sumDeductedTokensSince(dayStartMs: Long): Long {
            sumCutoffs += dayStartMs
            // 当月 1 号与今日 0 点相同（每月 1 号当天）时以月口径为准；其余按 cutoff 区分。
            return when {
                dayStartMs == monthStartMs -> monthlyUsed
                dayStartMs == this.dayStartMs -> dailyUsed
                else -> 0L
            }
        }

        override suspend fun sumDeductedTokensForSession(sessionId: String): Long = sessionUsed
        override suspend fun countSuccessfulImagesSince(dayStartMs: Long): Long = successfulImagesToday

        // ── 以下方法本测试用例不触及 ──
        override suspend fun getActiveProvider(): T2i_providers? = unsupported()
        override suspend fun getEnabledProviders(): List<T2i_providers> = unsupported()
        override suspend fun getProviderById(id: String): T2i_providers? = unsupported()
        override suspend fun upsertProvider(provider: T2i_providers) = unsupported()
        override suspend fun deleteProvider(id: String) = unsupported()
        override suspend fun deactivateAllProviders() = unsupported()
        override suspend fun activateProvider(id: String) = unsupported()
        override suspend fun updateProviderEndpointMode(id: String, mode: String) = unsupported()
        override suspend fun updateProviderEncryptedApiKey(id: String, newEncrypted: String) = unsupported()
        override suspend fun getModelsForProvider(providerId: String): List<T2i_provider_models> = unsupported()
        override suspend fun getModel(providerId: String, modelId: String): T2i_provider_models? = unsupported()
        override suspend fun upsertModel(model: T2i_provider_models) = unsupported()
        override suspend fun insertTask(task: T2i_task) = unsupported()
        override suspend fun getTaskById(id: String): T2i_task? = unsupported()
        override suspend fun getTaskByMessageId(messageId: String): T2i_task? = unsupported()
        override suspend fun listDanglingTasks(cutoffMs: Long): List<T2i_task> = unsupported()
        override suspend fun markTaskSuccess(id: String, imagePath: String, thumbnailPath: String, completedAtMs: Long) =
            unsupported()
        override suspend fun markTaskFailedOrRetry(
            id: String, finalStatus: String, errorCode: String, errorMessage: String, retryCount: Int, updatedAtMs: Long,
        ) = unsupported()
        override suspend fun setTaskPermissionDecision(id: String, decision: String, deducted: Int, updatedAtMs: Long) =
            unsupported()
        override suspend fun deleteTask(id: String) = unsupported()
        override suspend fun deleteTasksBySession(sessionId: String) = unsupported()

        private fun unsupported(): Nothing = error("T2IPermissionPolicyEngineTest 不应触及该仓储方法")
    }
}
