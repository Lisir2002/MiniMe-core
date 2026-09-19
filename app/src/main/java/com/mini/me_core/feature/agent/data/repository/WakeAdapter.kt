package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Wake_queue
import com.mini.me_core.datalayer.repository.WakeQueueStore
import com.mini.me_core.feature.agent.data.local.entity.WakeItemEntity
import com.mini.me_core.feature.agent.domain.wake.WakePort
import javax.inject.Inject

class WakeAdapter @Inject constructor(
    private val store: WakeQueueStore,
) : WakePort {

    override suspend fun listBySessionAndStatus(sessionId: String, status: String): List<WakeItemEntity> =
        store.listWakeBySessionAndStatus(sessionId, status).map { it.toEntity() }

    override suspend fun listPending(): List<WakeItemEntity> =
        store.listPendingWakeItems().map { it.toEntity() }

    override suspend fun upsert(item: WakeItemEntity) {
        store.upsertWakeItem(
            wakeId = item.wakeId, sessionId = item.sessionId, source = item.source,
            type = item.type, content = item.content, status = item.status, createdAtMs = item.createdAtMs,
        )
    }

    override suspend fun markConsumed(ids: List<String>, status: String) {
        store.markWakeItemsConsumedBatch(ids, status)
    }

    private fun Wake_queue.toEntity() = WakeItemEntity(
        wakeId = wake_id, sessionId = session_id, source = source, type = type,
        content = content, status = status, createdAtMs = created_at_ms,
    )
}
