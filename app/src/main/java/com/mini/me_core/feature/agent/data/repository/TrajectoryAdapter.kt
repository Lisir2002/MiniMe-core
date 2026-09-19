package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_trajectories
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.TrajectoryEntity
import com.mini.me_core.feature.agent.domain.trajectory.TrajectoryAggregateDto
import com.mini.me_core.feature.agent.domain.trajectory.TrajectoryPort
import javax.inject.Inject

class TrajectoryAdapter @Inject constructor(private val v2: AgentRepository) : TrajectoryPort {
    override suspend fun insert(entity: TrajectoryEntity) {
        v2.insertTrajectory(entity.trajectoryId, entity.sessionId, entity.taskId, entity.turnIndex.toLong(),
            entity.kind, entity.toolName, entity.argsHash, entity.resultSummary,
            if (entity.isError) 1L else 0L, entity.durationMs, entity.tokensIn.toLong(), entity.tokensOut.toLong(), entity.ts)
    }
    override suspend fun list(sessionId: String): List<TrajectoryEntity> = v2.listTrajectories(sessionId).map { it.toEntity() }
    override suspend fun listByTask(taskId: String): List<TrajectoryEntity> = v2.listTrajectoriesByTask(taskId).map { it.toEntity() }
    override suspend fun aggregate(sessionId: String): TrajectoryAggregateDto {
        val a = v2.getTrajectoryAggregate(sessionId)
        return TrajectoryAggregateDto(a.tokens_in, a.tokens_out, a.count)
    }
    override suspend fun latestTaskId(sessionId: String): String? = v2.getLatestTaskId(sessionId)
    override suspend fun maxTurnIndex(sessionId: String, taskId: String): Int? = v2.getMaxTurnIndex(sessionId, taskId)
    private fun Agent_trajectories.toEntity() = TrajectoryEntity(
        trajectory_id, session_id, task_id, turn_index.toInt(), kind, tool_name, args_hash,
        result_summary, is_error != 0L, duration_ms, tokens_in.toInt(), tokens_out.toInt(), ts
    )
}
