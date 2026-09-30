package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.core.security.ZthSensitiveColumnCrypto
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hard_constraint_delete_audits as V2DeleteAudit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C.4.5 PlanApproval + C.4.11 LINK-INV 删除审计 Repository。
 *
 *
 * 两个独立子功能：
 *   1) HardConstraintDeleteAuditDao：写「AI 删除了用户保留行/sentinel/checkpoint」审计
 *      （ZthDbAutoReconciler C.4.11 用本表做 LINK-INV 迁移回滚扫描）
 *   2) PlanApproval 本身的 choice 写入由 [PlanApprovalManager] 直接操作，
 *      这里只提供：结果写 Telemetry + Firestore HardConstraintDelete Dto 映射
 *
 * 不变性：
 *   PA-INV-1：写 HardConstraintDeleteAuditEntity 前必须加密 s_affectedKeysCiphertext（不能存明文 keys）
 *   PA-INV-2：rollbackApplied 单向置位（0→1→0 不允许；回滚完成后不能「撤销回滚」）
 */
@Singleton
class ZthPlanApprovalRepository @Inject constructor(
    private val v2Agent: V2AgentRepository,
    private val crypto: ZthSensitiveColumnCrypto,
    private val telemetry: ZthTelemetryRepository
) {

    /** Phase 5 onFailure：AI 删除了 sentinel/checkpoint/保留行 → 写审计表（加密 keys JSON）。 */
    suspend fun recordHardConstraintDelete(
        sessionId: String, affectedTableName: String,
        affectedKeysJsonPlaintext: String, triggerSubClass: String
    ): String {
        crypto.assertSensitiveColumnName("s_affectedKeysCiphertext")
        val cipher = crypto.encrypt(affectedKeysJsonPlaintext)
        val id = "HCD:${UUID.randomUUID()}"
        v2Agent.insertDeleteAudit(
            id = id, sessionId = sessionId,
            affectedTableName = affectedTableName,
            sAffectedKeysCiphertext = cipher,
            triggerSubClass = triggerSubClass,
            rollbackApplied = 0L,
            createdAtMs = System.currentTimeMillis(),
        )
        telemetry.recordCapabilityAudit(
            sessionId, tier = 3, subKind = "HARD_CONSTRAINT_DELETE",
            batchSize = 1L, hitCount = 1L, latencyMs = 0L
        )
        return id
    }

    /** C.4.11 DB Migration 兜底：扫所有 rollbackApplied=0 的删除审计 → 调用者做回滚。 */
    suspend fun listPendingRollbacks(): List<V2DeleteAudit> =
        v2Agent.listDeleteAuditsPendingRollback()

    /** 回滚成功后：标记 rollbackApplied=1（PA-INV-2 单向）。 */
    suspend fun markRolledBack(auditId: String) =
        v2Agent.markDeleteAuditRolledBack(auditId)

    // ── Phase 4.2 Firestore：HardConstraintDelete ↔ Dto 映射 ──────────
    // 说明：跨设备同步不存 s_affectedKeysCiphertext（只有本地 Keystore 能解开；同步只用于「统计与通知」）。

    fun deleteAuditToDto(e: V2DeleteAudit): Map<String, Any?> = mapOf(
        "id" to e.id, "sessionId" to e.session_id,
        "affectedTableName" to e.affected_table_name,
        "triggerSubClass" to e.trigger_sub_class,
        "rollbackApplied" to (e.rollback_applied != 0L),
        "createdAtMs" to e.created_at_ms,
        "_lwwMs" to System.currentTimeMillis()
        // 注意：s_affectedKeysCiphertext 不跨设备同步（本地加密）
    )

    fun deleteAuditFromDto(m: Map<String, Any?>): V2DeleteAudit = V2DeleteAudit(
        id = m["id"] as? String ?: "",
        session_id = m["sessionId"] as? String ?: "",
        affected_table_name = m["affectedTableName"] as? String ?: "",
        s_affectedKeysCiphertext = "", // 跨设备拉到后无明文意义（保留空）
        trigger_sub_class = m["triggerSubClass"] as? String ?: "",
        rollback_applied = if ((m["rollbackApplied"] as? Boolean) ?: false) 1L else 0L,
        created_at_ms = (m["createdAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}