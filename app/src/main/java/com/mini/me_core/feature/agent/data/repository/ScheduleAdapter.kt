package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_schedules
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.ScheduleEntity
import com.mini.me_core.feature.agent.domain.schedule.SchedulePort
import javax.inject.Inject

class ScheduleAdapter @Inject constructor(private val v2: AgentRepository) : SchedulePort {
    override suspend fun upsert(entity: ScheduleEntity) {
        v2.upsertSchedule(entity.scheduleId, entity.sessionId, entity.rule, entity.args,
            entity.status, entity.enabled.toLong(), entity.createdAtMs, entity.lastFiredAtMs, entity.updatedAtMs)
    }
    override suspend fun list(sessionId: String): List<ScheduleEntity> = v2.listSchedules(sessionId).map { it.toEntity() }
    override suspend fun getById(id: String): ScheduleEntity? = v2.getScheduleById(id)?.toEntity()
    override suspend fun updateState(id: String, status: String, enabled: Long, lastFiredAtMs: Long, updatedAtMs: Long) {
        v2.updateScheduleState(id, status, enabled, lastFiredAtMs, updatedAtMs)
    }
    override suspend fun getPending(): List<ScheduleEntity> = v2.getPendingSchedules().map { it.toEntity() }
    private fun Agent_schedules.toEntity() = ScheduleEntity(
        schedule_id, session_id, rule, args, status, enabled.toInt(), created_at_ms, last_fired_at_ms, updated_at_ms
    )
}
