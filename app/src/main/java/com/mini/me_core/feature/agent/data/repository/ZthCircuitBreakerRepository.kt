package com.mini.me_core.feature.agent.data.repository


import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hallucination_fuses as V2Fuse
import com.mini.me_core.feature.agent.domain.execution.permission.FuseState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * C.4.6 熔断 Repository（薄封装 HallucinationFuseDao）。
 *
 * 业务逻辑（计数 / 冷却 / CAS 迁移 / kill-switch）在 [ZthCircuitBreakerManager]，
 * 这里只提供：
 *   - 供 Phase 4.2 SyncManager 拉/推的 getAllOnce / upsertAll
 *   - 供 UI Red Banner observe 用的 observeGlobalAndSession
 *   - Firestore Entity↔Dto 映射
 *
 * 不变性：
 *   FUSE-REPO-INV-1：绝不在 Repository 层直接修改 linkageVersion / state（必须走 Manager CAS）
 *   FUSE-REPO-INV-2：跨设备同步只允许 CLOSED ↔ HALF_OPEN；OPEN / killSwitch1 必须本地手动清
 *   （C.4.2 KILL-1：killSwitch1Triggered 单向置位，不能由 Firestore 覆盖为 false）
 */
@Singleton
class ZthCircuitBreakerRepository @Inject constructor(
    private val v2Agent: V2AgentRepository,
) {

    /** UI Red Banner：实时观察全局 + 会话级 fuse。 */
    fun observeGlobalAndSession(sessionId: String): Flow<List<V2Fuse>> =
        v2Agent.observeAllFuses()

    /** Phase 4.2 Sync：全量拉取本地（push 到 Firestore）。 */
    suspend fun getAll(): List<V2Fuse> =
        v2Agent.listAllFuses()

    /** Phase 4.2 Sync：Firestore pull → 本地合并（按 KILL-1 不变性过滤 killSwitch1=true）。 */
    suspend fun mergeFromRemote(remoteList: List<V2Fuse>) {
        val locals = getAll().associateBy { it.scope to it.scope_id }
        val toUpsert = mutableListOf<V2Fuse>()
        for (r in remoteList) {
            val l = locals[r.scope to r.scope_id]
            if (l == null) {
                toUpsert.add(r)
                continue
            }
            // FUSE-REPO-INV-2：本地 killSwitch1Triggered=true → 拒绝远程覆盖为 false
            val merged = if (l.kill_switch1_triggered != 0L && r.kill_switch1_triggered == 0L) {
                r.copy(kill_switch1_triggered = 1L, state = FuseState.OPEN.name)
            } else r
            toUpsert.add(merged)
        }
        if (toUpsert.isNotEmpty()) {
            for (e in toUpsert) {
                v2Agent.upsertFuse(
                    id = e.id, scope = e.scope, scopeId = e.scope_id, state = e.state,
                    linkageVersion = e.linkage_version, failureCount = e.failure_count,
                    openSinceMs = e.open_since_ms, lastProbeAtMs = e.last_probe_at_ms,
                    killSwitch1Triggered = e.kill_switch1_triggered,
                    killSwitch2SoftDisabled = e.kill_switch2_soft_disabled,
                    lastTripSubclass = e.last_trip_subclass, updatedAtMs = e.updated_at_ms,
                )
            }
        }
    }

    // ── Phase 4.2 Firestore：Entity ↔ Dto 映射 ─────────────────────────

    fun toDto(e: V2Fuse): Map<String, Any?> = mapOf(
        "id" to e.id, "scope" to e.scope, "scopeId" to e.scope_id,
        "state" to e.state, "linkageVersion" to e.linkage_version,
        "failureCount" to e.failure_count, "openSinceMs" to e.open_since_ms,
        "lastProbeAtMs" to e.last_probe_at_ms,
        "killSwitch1Triggered" to (e.kill_switch1_triggered != 0L),
        "killSwitch2SoftDisabled" to (e.kill_switch2_soft_disabled != 0L),
        "lastTripSubclass" to e.last_trip_subclass, "updatedAtMs" to e.updated_at_ms,
        "_lwwMs" to System.currentTimeMillis()
    )

    fun fromDto(m: Map<String, Any?>): V2Fuse = V2Fuse(
        id = m["id"] as? String ?: "",
        scope = m["scope"] as? String ?: "GLOBAL",
        scope_id = m["scopeId"] as? String ?: GLOBAL_SCOPE_ID,
        state = m["state"] as? String ?: FuseState.CLOSED.name,
        linkage_version = (m["linkageVersion"] as? Number)?.toLong() ?: 0L,
        failure_count = (m["failureCount"] as? Number)?.toLong() ?: 0L,
        open_since_ms = (m["openSinceMs"] as? Number)?.toLong() ?: 0L,
        last_probe_at_ms = (m["lastProbeAtMs"] as? Number)?.toLong() ?: 0L,
        kill_switch1_triggered = if ((m["killSwitch1Triggered"] as? Boolean) ?: false) 1L else 0L,
        kill_switch2_soft_disabled = if ((m["killSwitch2SoftDisabled"] as? Boolean) ?: false) 1L else 0L,
        last_trip_subclass = m["lastTripSubclass"] as? String,
        updated_at_ms = (m["updatedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    companion object {
        const val GLOBAL_SCOPE_ID = "__zth_global__"
    }
}
