package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Zth_hallucination_fuses
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.HallucinationFuseEntity
import com.mini.me_core.feature.agent.domain.zth.FusePort
import javax.inject.Inject

class FuseAdapter @Inject constructor(private val v2: AgentRepository) : FusePort {
    override suspend fun getVersion(id: String): Long? = v2.getFuseVersion(id)
    override suspend fun upsert(entity: HallucinationFuseEntity) {
        v2.upsertFuse(entity.id, entity.scope, entity.scopeId, entity.state, entity.linkageVersion,
            entity.failureCount.toLong(), entity.openSinceMs, entity.lastProbeAtMs,
            if (entity.killSwitch1Triggered) 1L else 0L, if (entity.killSwitch2SoftDisabled) 1L else 0L,
            entity.lastTripSubclass, entity.updatedAtMs)
    }
    override suspend fun casUpdateState(id: String, expectedVersion: Long, newState: String, nowMs: Long): Long =
        v2.casUpdateFuseState(id, expectedVersion, newState, nowMs)
    override suspend fun triggerKillSwitch1(id: String, nowMs: Long) { v2.triggerFuseKillSwitch1(id, nowMs) }
    override suspend fun get(scope: String, scopeId: String): HallucinationFuseEntity? =
        v2.getFuse(scope, scopeId)?.toEntity()
    override suspend fun listAll(): List<HallucinationFuseEntity> = v2.listAllFuses().map { it.toEntity() }

    override suspend fun insertSentinel(
        id: String, sessionId: String, linkageVersion: Long, chainId: String, chainIndex: Long,
        cardTemplateId: String, triggerSubClass: String, sPlanPayloadCiphertext: String,
        sUserTextCiphertext: String?, sCardPayloadCiphertext: String, userChoice: String,
        swipeVerified: Long, sModifiedPlanCiphertext: String?, expireAtMs: Long, rollbackFlag: Long, createdAtMs: Long,
    ) {
        v2.insertSentinel(id, sessionId, linkageVersion, chainId, chainIndex, cardTemplateId,
            triggerSubClass, sPlanPayloadCiphertext, sUserTextCiphertext, sCardPayloadCiphertext,
            userChoice, swipeVerified, sModifiedPlanCiphertext, expireAtMs, rollbackFlag, createdAtMs)
    }

    override suspend fun insertRejectionAudit(
        id: String, sentinelId: String, rejectionType: String, sReasonCiphertext: String?,
        sRejectedPlanSnapshotCiphertext: String, createdAtMs: Long,
    ) {
        v2.insertRejectionAudit(id, sentinelId, rejectionType, sReasonCiphertext, sRejectedPlanSnapshotCiphertext, createdAtMs)
    }

    private fun Zth_hallucination_fuses.toEntity() = HallucinationFuseEntity(
        id, scope, scope_id, state, linkage_version, failure_count.toInt(), open_since_ms,
        last_probe_at_ms, kill_switch1_triggered != 0L, kill_switch2_soft_disabled != 0L,
        last_trip_subclass, updated_at_ms
    )
}
