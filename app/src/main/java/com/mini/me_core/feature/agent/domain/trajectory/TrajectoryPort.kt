package com.mini.me_core.feature.agent.domain.trajectory

import com.mini.me_core.feature.agent.data.local.entity.TrajectoryEntity

data class TrajectoryAggregateDto(val tokensIn: Long, val tokensOut: Long, val count: Long)

interface TrajectoryPort {
    suspend fun insert(entity: TrajectoryEntity)
    suspend fun list(sessionId: String): List<TrajectoryEntity>
    suspend fun listByTask(taskId: String): List<TrajectoryEntity>
    suspend fun aggregate(sessionId: String): TrajectoryAggregateDto
    suspend fun latestTaskId(sessionId: String): String?
    suspend fun maxTurnIndex(sessionId: String, taskId: String): Int?
}
