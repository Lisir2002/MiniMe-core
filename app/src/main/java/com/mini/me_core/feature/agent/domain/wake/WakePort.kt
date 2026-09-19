package com.mini.me_core.feature.agent.domain.wake

import com.mini.me_core.feature.agent.data.local.entity.WakeItemEntity

/** 唤醒队列（Wake）数据端口。 */
interface WakePort {
    suspend fun listBySessionAndStatus(sessionId: String, status: String): List<WakeItemEntity>
    suspend fun listPending(): List<WakeItemEntity>
    suspend fun upsert(item: WakeItemEntity)
    suspend fun markConsumed(ids: List<String>, status: String)
}
