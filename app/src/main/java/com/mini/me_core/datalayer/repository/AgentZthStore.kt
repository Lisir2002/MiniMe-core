package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hallucination_fuses
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hard_constraint_delete_audits
import com.mini.mecore.datalayer.sqldelight.agent.Zth_sentinel_plan_rejection_audits
import com.mini.mecore.datalayer.sqldelight.agent.Zth_telemetry_events
import com.mini.mecore.datalayer.sqldelight.agent.Zth_user_confirmed_sentinels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * ZTH 安全/遥测存储：sentinels（用户确认卡链）、hallucination fuses（熔断）、
 * rejection audits、hard-constraint delete audits、telemetry events。
 */
@Suppress("TooManyFunctions") // 单表 CRUD 内聚，行为保持提取
internal class AgentZthStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── Sentinels ──────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertSentinel(
        id: String, sessionId: String, linkageVersion: Long, chainId: String, chainIndex: Long,
        cardTemplateId: String, triggerSubClass: String, sPlanPayloadCiphertext: String,
        sUserTextCiphertext: String?, sCardPayloadCiphertext: String, userChoice: String,
        swipeVerified: Long, sModifiedPlanCiphertext: String?, expireAtMs: Long, rollbackFlag: Long, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertSentinel(
            id, sessionId, linkageVersion, chainId, chainIndex, cardTemplateId, triggerSubClass,
            sPlanPayloadCiphertext, sUserTextCiphertext, sCardPayloadCiphertext, userChoice,
            swipeVerified, sModifiedPlanCiphertext, expireAtMs, rollbackFlag, createdAtMs,
        )
    }

    suspend fun listSentinels(sessionId: String): List<Zth_user_confirmed_sentinels> =
        withContext(Dispatchers.IO) { q.selectSentinelsBySession(sessionId).executeAsList() }

    fun observeSentinelsBySession(sessionId: String): Flow<List<Zth_user_confirmed_sentinels>> =
        q.selectSentinelsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun listSentinelsByChain(chainId: String): List<Zth_user_confirmed_sentinels> =
        withContext(Dispatchers.IO) { q.selectSentinelsByChain(chainId).executeAsList() }

    suspend fun listSentinelsUnexpiredBySession(sessionId: String, nowMs: Long): List<Zth_user_confirmed_sentinels> =
        withContext(Dispatchers.IO) { q.selectSentinelsUnexpiredBySession(sessionId, nowMs).executeAsList() }

    suspend fun listAllSentinels(): List<Zth_user_confirmed_sentinels> =
        withContext(Dispatchers.IO) { q.selectAllSentinels().executeAsList() }

    suspend fun markAllSentinelsRollbackBySession(sessionId: String) {
        withContext(Dispatchers.IO) { q.updateAllSentinelsRollbackBySession(sessionId) }
    }

    suspend fun deleteSentinelById(id: String) {
        withContext(Dispatchers.IO) { q.deleteSentinelById(id) }
    }

    suspend fun updateSentinelChoice(
        id: String, userChoice: String, swipeVerified: Long, sModifiedPlanCiphertext: String?,
    ) = withContext(Dispatchers.IO) { q.updateSentinelChoice(userChoice, swipeVerified, sModifiedPlanCiphertext, id) }

    // ── Fuses ──────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertFuse(
        id: String, scope: String, scopeId: String, state: String, linkageVersion: Long,
        failureCount: Long, openSinceMs: Long, lastProbeAtMs: Long, killSwitch1Triggered: Long,
        killSwitch2SoftDisabled: Long, lastTripSubclass: String?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertFuse(
            id, scope, scopeId, state, linkageVersion, failureCount, openSinceMs, lastProbeAtMs,
            killSwitch1Triggered, killSwitch2SoftDisabled, lastTripSubclass, updatedAtMs,
        )
    }

    suspend fun getFuse(scope: String, scopeId: String): Zth_hallucination_fuses? =
        withContext(Dispatchers.IO) { q.selectFuse(scope, scopeId).executeAsOneOrNull() }

    fun observeFuse(scope: String, scopeId: String): Flow<Zth_hallucination_fuses?> =
        q.selectFuse(scope, scopeId).asFlow().mapToOneOrNull(Dispatchers.IO)

    suspend fun getFuseVersion(id: String): Long? =
        withContext(Dispatchers.IO) { q.selectFuseVersion(id).executeAsOneOrNull() }

    suspend fun casUpdateFuseState(id: String, expectedVersion: Long, newState: String, nowMs: Long): Long =
        withContext(Dispatchers.IO) {
            q.casUpdateFuseState(newState, nowMs, id, expectedVersion).value
        }

    suspend fun triggerFuseKillSwitch1(id: String, nowMs: Long) =
        withContext(Dispatchers.IO) { q.triggerFuseKillSwitch1(nowMs, id) }

    suspend fun listAllFuses(): List<Zth_hallucination_fuses> =
        withContext(Dispatchers.IO) { q.selectAllFuses().executeAsList() }

    fun observeAllFuses(): Flow<List<Zth_hallucination_fuses>> =
        q.selectAllFuses().asFlow().mapToList(Dispatchers.IO)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun updateFuseState(
        state: String, failureCount: Long, lastProbeAtMs: Long,
        killSwitch1Triggered: Long, killSwitch2SoftDisabled: Long,
        lastTripSubclass: String?, updatedAtMs: Long, scope: String, scopeId: String,
    ) = withContext(Dispatchers.IO) {
        q.updateFuseState(
            state, failureCount, lastProbeAtMs, killSwitch1Triggered, killSwitch2SoftDisabled,
            lastTripSubclass, updatedAtMs, scope, scopeId,
        )
    }

    // ── Rejection audits ───────────────────────────────────────────────

    suspend fun insertRejectionAudit(
        id: String, sentinelId: String, rejectionType: String, sReasonCiphertext: String?,
        sRejectedPlanSnapshotCiphertext: String, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertRejectionAudit(id, sentinelId, rejectionType, sReasonCiphertext, sRejectedPlanSnapshotCiphertext, createdAtMs)
    }

    suspend fun listRejectionAudits(sentinelId: String): List<Zth_sentinel_plan_rejection_audits> =
        withContext(Dispatchers.IO) { q.selectRejectionAuditsBySentinel(sentinelId).executeAsList() }

    fun observeRejectionAuditsBySentinel(sentinelId: String): Flow<List<Zth_sentinel_plan_rejection_audits>> =
        q.selectRejectionAuditsBySentinel(sentinelId).asFlow().mapToList(Dispatchers.IO)

    suspend fun listAllRejectionAudits(): List<Zth_sentinel_plan_rejection_audits> =
        withContext(Dispatchers.IO) { q.selectRejectionAuditsAll().executeAsList() }

    // ── Hard-constraint delete audits ───────────────────────────────────

    suspend fun insertDeleteAudit(
        id: String, sessionId: String, affectedTableName: String, sAffectedKeysCiphertext: String,
        triggerSubClass: String, rollbackApplied: Long, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertDeleteAudit(id, sessionId, affectedTableName, sAffectedKeysCiphertext, triggerSubClass, rollbackApplied, createdAtMs)
    }

    suspend fun listDeleteAuditsPendingRollback(): List<Zth_hard_constraint_delete_audits> =
        withContext(Dispatchers.IO) { q.selectDeleteAuditsPendingRollback().executeAsList() }

    suspend fun listAllDeleteAudits(): List<Zth_hard_constraint_delete_audits> =
        withContext(Dispatchers.IO) { q.selectAllDeleteAudits().executeAsList() }

    suspend fun markDeleteAuditRolledBack(id: String) {
        withContext(Dispatchers.IO) { q.updateDeleteAuditRolledBack(id) }
    }

    // ── Telemetry ───────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTelemetryEvent(
        eventKind: String, eventSubKind: String, severityTier: Long, sessionSha256Prefix: String?,
        latencyMs: Long?, flagA: Long?, flagB: Long?, metricA: Long?, metricB: Long?, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertTelemetryEvent(
            eventKind, eventSubKind, severityTier, sessionSha256Prefix, latencyMs,
            flagA, flagB, metricA, metricB, createdAtMs,
        )
    }

    suspend fun listTelemetryByKind(eventKind: String, limit: Long): List<Zth_telemetry_events> =
        withContext(Dispatchers.IO) { q.selectTelemetryByKind(eventKind, limit).executeAsList() }

    fun observeTelemetryByKind(eventKind: String, limit: Long): Flow<List<Zth_telemetry_events>> =
        q.selectTelemetryByKind(eventKind, limit).asFlow().mapToList(Dispatchers.IO)

    suspend fun countTelemetryByKind(eventKind: String): Long =
        withContext(Dispatchers.IO) { q.countTelemetryByKind(eventKind).executeAsOne() }

    suspend fun listAllTelemetry(): List<Zth_telemetry_events> =
        withContext(Dispatchers.IO) { q.selectAllTelemetry().executeAsList() }

    fun observeAllTelemetry(): Flow<List<Zth_telemetry_events>> =
        q.selectAllTelemetry().asFlow().mapToList(Dispatchers.IO)

    suspend fun listTelemetryRange(fromMs: Long, toMs: Long): List<Zth_telemetry_events> =
        withContext(Dispatchers.IO) { q.selectTelemetryRange(fromMs, toMs).executeAsList() }

    suspend fun countAllTelemetry(): Long =
        withContext(Dispatchers.IO) { q.countAllTelemetry().executeAsOne() }

    suspend fun deleteTelemetryOlderThan(beforeMs: Long) {
        withContext(Dispatchers.IO) { q.deleteTelemetryOlderThan(beforeMs) }
    }
}
