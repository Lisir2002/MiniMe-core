package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Zth_sentinel_plan_rejection_audits as V2RejectionAudit
import com.mini.mecore.datalayer.sqldelight.agent.Zth_user_confirmed_sentinels as V2Sentinel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C.4.1/C.4.3 ConfirmationCard Repository（主表 sentinel + 从表 rejection audit）。
 *
 *
 * LINK-INV 4 写事务不在这里（在 ZthConfirmationCardManager 内显式 4 步 + CAS）。
 * 这里只提供：
 *   - Phase 5 prePlan 查「此 chainId 是否已被用户决策过」（幂等 ConfirmationCard 不重复弹）
 *   - UI sentinel 时间线 observeBySession
 *   - C.4.3 崩溃恢复 listUnexpiredBySession
 *   - C.4.2 一键回滚 markAllRollbackBySession
 *   - Phase 4.2 Firestore Dto 映射
 *
 * 不变性：
 *   CARD-REPO-INV-1：所有 s_* 加密列绝不在 Repo 层解密（解密仅在需要展示时由 UI VM 完成）
 *   CARD-REPO-INV-2：跨设备同步不发送 s_* 密文（每台设备 Keystore 密钥不同；同步只存元数据 + choice）
 */
@Singleton
class ZthConfirmationCardRepository @Inject constructor(
    private val v2Agent: V2AgentRepository,
    private val telemetry: ZthTelemetryRepository
) {

    /** Phase 5 Facade：弹卡前查询 chainId 是否已决策（已存在 → 直接复用 choice，不重复弹卡）。 */
    suspend fun getSentinelsByChain(chainId: String): List<V2Sentinel> =
        v2Agent.listSentinelsByChain(chainId)

    /** UI 时间线：流式观察会话内所有 sentinel（新→旧）。 */
    fun observeSentinelsBySession(sessionId: String): Flow<List<V2Sentinel>> =
        v2Agent.observeSentinelsBySession(sessionId)

    /** C.4.3 崩溃恢复：会话下所有未过期 sentinel（expireAtMs=-1 永不过期）。 */
    suspend fun listUnexpiredBySession(sessionId: String, nowMs: Long = System.currentTimeMillis()):
            List<V2Sentinel> =
        v2Agent.listSentinelsUnexpiredBySession(sessionId, nowMs)

    /** C.4.2 Red Banner 一键回滚：标记会话内所有 sentinel rollbackFlag=true。 */
    suspend fun markAllRollbackBySession(sessionId: String) {
        v2Agent.markAllSentinelsRollbackBySession(sessionId)
    }

    /** 审计查询：某 sentinel 的拒绝/修改理由（外键）。 */
    suspend fun getRejectionAudit(sentinelId: String): V2RejectionAudit? =
        v2Agent.listRejectionAudits(sentinelId).firstOrNull()

    /** Manager 写入成功后，打一条 CARD.DECISION 遥测（Phase 4.1 14 指标写入路径之一）。 */
    suspend fun recordDecisionTelemetry(
        sessionId: String?, tier: Int, cardTemplateId: String,
        choice: String, swipeVerified: Boolean, latencyMs: Long
    ) {
        telemetry.recordCardDecision(sessionId, tier, cardTemplateId, choice, swipeVerified, latencyMs)
    }

    // ── Phase 4.2 Firestore：Sentinel + RejectionAudit ↔ Dto 映射 ────────
    // CARD-REPO-INV-2：不跨设备同步 s_* 加密列（本地 Keystore-only）；同步只含元数据。

    fun sentinelToDto(e: V2Sentinel): Map<String, Any?> = mapOf(
        "id" to e.id, "sessionId" to e.session_id,
        "linkageVersion" to e.linkage_version, "chainId" to e.chain_id,
        "chainIndex" to e.chain_index, "cardTemplateId" to e.card_template_id,
        "triggerSubClass" to e.trigger_sub_class, "userChoice" to e.user_choice,
        "swipeVerified" to (e.swipe_verified != 0L), "expireAtMs" to e.expire_at_ms,
        "rollbackFlag" to (e.rollback_flag != 0L), "createdAtMs" to e.created_at_ms,
        "_lwwMs" to System.currentTimeMillis()
        // 注意：s_planPayloadCiphertext / s_userTextCiphertext / s_cardPayloadCiphertext /
        // s_modifiedPlanCiphertext 不同步（CARD-REPO-INV-2）
    )

    fun sentinelFromDto(m: Map<String, Any?>): V2Sentinel =
        V2Sentinel(
            id = m["id"] as? String ?: "",
            session_id = m["sessionId"] as? String ?: "",
            linkage_version = (m["linkageVersion"] as? Number)?.toLong() ?: 0L,
            chain_id = m["chainId"] as? String ?: "",
            chain_index = (m["chainIndex"] as? Number)?.toLong() ?: 1L,
            card_template_id = m["cardTemplateId"] as? String ?: "",
            trigger_sub_class = m["triggerSubClass"] as? String ?: "",
            s_planPayloadCiphertext = "", // 跨设备拉到后空值（不影响只读决策展示）
            s_userTextCiphertext = null,
            s_cardPayloadCiphertext = "",
            user_choice = m["userChoice"] as? String ?: "CONFIRM",
            swipe_verified = if ((m["swipeVerified"] as? Boolean) ?: false) 1L else 0L,
            s_modifiedPlanCiphertext = null,
            expire_at_ms = (m["expireAtMs"] as? Number)?.toLong() ?: -1L,
            rollback_flag = if ((m["rollbackFlag"] as? Boolean) ?: false) 1L else 0L,
            created_at_ms = (m["createdAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )

    fun rejectionAuditToDto(e: V2RejectionAudit): Map<String, Any?> = mapOf(
        "id" to e.id, "sentinelId" to e.sentinel_id,
        "rejectionType" to e.rejection_type, "createdAtMs" to e.created_at_ms,
        "_lwwMs" to System.currentTimeMillis()
        // s_reasonCiphertext / s_rejectedPlanSnapshotCiphertext 不同步
    )

    fun rejectionAuditFromDto(m: Map<String, Any?>): V2RejectionAudit =
        V2RejectionAudit(
            id = m["id"] as? String ?: "",
            sentinel_id = m["sentinelId"] as? String ?: "",
            rejection_type = m["rejectionType"] as? String ?: "REJECT",
            s_reasonCiphertext = null,
            s_rejectedPlanSnapshotCiphertext = "",
            created_at_ms = (m["createdAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )

    // ── Phase 4.2 Sync 辅助：批量全量拉（push 到 Firestore） ─────────

    suspend fun getAllSentinels(): List<V2Sentinel> =
        v2Agent.listAllSentinels()

    suspend fun getAllRejectionAudits(): List<V2RejectionAudit> =
        v2Agent.listAllRejectionAudits()
}