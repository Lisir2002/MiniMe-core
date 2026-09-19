package com.mini.me_core.feature.agent.domain.schedule

import com.mini.me_core.feature.agent.data.local.entity.ScheduleEntity

interface SchedulePort {
    suspend fun upsert(entity: ScheduleEntity)
    suspend fun list(sessionId: String): List<ScheduleEntity>
    suspend fun getById(id: String): ScheduleEntity?
    suspend fun updateState(id: String, status: String, enabled: Long, lastFiredAtMs: Long, updatedAtMs: Long)
    suspend fun getPending(): List<ScheduleEntity>
}
