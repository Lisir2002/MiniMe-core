package com.mini.me_core.feature.agent.domain.zth

import com.mini.me_core.feature.agent.data.local.entity.HallucinationFuseEntity

interface FusePort {
    suspend fun getVersion(id: String): Long?
    suspend fun upsert(entity: HallucinationFuseEntity)
    suspend fun casUpdateState(id: String, expectedVersion: Long, newState: String, nowMs: Long): Long
    suspend fun triggerKillSwitch1(id: String, nowMs: Long)
    suspend fun get(scope: String, scopeId: String): HallucinationFuseEntity?
    suspend fun listAll(): List<HallucinationFuseEntity>

    /** 写入用户确认哨兵（ciphertext 字段由调用方加密后传入）。 */
    suspend fun insertSentinel(
        id: String, sessionId: String, linkageVersion: Long, chainId: String, chainIndex: Long,
        cardTemplateId: String, triggerSubClass: String, sPlanPayloadCiphertext: String,
        sUserTextCiphertext: String?, sCardPayloadCiphertext: String, userChoice: String,
        swipeVerified: Long, sModifiedPlanCiphertext: String?, expireAtMs: Long, rollbackFlag: Long, createdAtMs: Long,
    )

    /** 写入拒绝/修改审计。 */
    suspend fun insertRejectionAudit(
        id: String, sentinelId: String, rejectionType: String, sReasonCiphertext: String?,
        sRejectedPlanSnapshotCiphertext: String, createdAtMs: Long,
    )
}
