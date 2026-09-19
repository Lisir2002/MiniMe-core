package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_goals
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.GoalEntity
import com.mini.me_core.feature.agent.domain.goal.GoalPort
import javax.inject.Inject

class GoalAdapter @Inject constructor(
    private val v2: AgentRepository,
) : GoalPort {

    override suspend fun getActive(sessionId: String): GoalEntity? =
        v2.getActiveGoalBySession(sessionId)?.toEntity()

    override suspend fun getById(goalId: String): GoalEntity? =
        v2.getGoalById(goalId)?.toEntity()

    override suspend fun activate(
        sessionId: String, goalId: String, text: String, status: String,
        revision: Long, parentGoalId: String, roundSeq: Long,
        createdAtMs: Long, updatedAtMs: Long,
    ) {
        v2.runInTx { tx ->
            tx.activateGoal(
                sessionId = sessionId,
                old = v2.getActiveGoalBySessionBlocking(sessionId),
                goalId = goalId, text = text, status = status, revision = revision,
                parentGoalId = parentGoalId, roundSeq = roundSeq,
                createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
            )
        }
    }

    override suspend fun casUpdateStatusAndText(
        goalId: String, status: String, text: String,
        newRevision: Long, expectedRevision: Long, updatedAtMs: Long,
    ): Long = v2.casUpdateGoalStatusAndText(
        goalId, status, text, newRevision, expectedRevision, updatedAtMs,
    )

    private fun Agent_goals.toEntity() = GoalEntity(
        goalId = goal_id,
        sessionId = session_id,
        text = text,
        status = status,
        revision = revision.toInt(),
        parentGoalId = parent_goal_id,
        roundSeq = round_seq.toInt(),
        createdAtMs = created_at_ms,
        updatedAtMs = updated_at_ms,
    )
}
