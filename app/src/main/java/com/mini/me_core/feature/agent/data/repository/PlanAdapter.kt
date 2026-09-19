package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_plans
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.PlanEntity
import com.mini.me_core.feature.agent.domain.plan.PlanPort
import javax.inject.Inject

class PlanAdapter @Inject constructor(
    private val v2: AgentRepository,
) : PlanPort {

    override suspend fun getLatest(sessionId: String): PlanEntity? =
        v2.getLatestPlanBySession(sessionId)?.toEntity()

    override suspend fun getById(planId: String): PlanEntity? =
        v2.getPlanById(planId)?.toEntity()

    override suspend fun upsert(plan: PlanEntity) {
        v2.upsertPlan(
            planId = plan.planId, sessionId = plan.sessionId, title = plan.title,
            steps = plan.steps, status = plan.status, pendingSelection = plan.pendingSelection,
            createdAtMs = plan.createdAtMs, updatedAtMs = plan.updatedAtMs,
        )
    }

    override suspend fun propose(
        sessionId: String, planId: String, title: String, steps: String,
        status: String, pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    ) {
        v2.runInTx { tx ->
            tx.proposePlan(
                sessionId = sessionId,
                old = v2.getLatestPlanBySessionBlocking(sessionId),
                planId = planId, title = title, steps = steps, status = status,
                pendingSelection = pendingSelection, createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
            )
        }
    }

    override suspend fun updateContent(
        planId: String, status: String, steps: String, pendingSelection: String, updatedAtMs: Long,
    ) { v2.updatePlanContent(planId, status, steps, pendingSelection, updatedAtMs) }

    private fun Agent_plans.toEntity() = PlanEntity(
        planId = plan_id,
        sessionId = session_id,
        title = title,
        steps = steps,
        status = status,
        pendingSelection = pending_selection,
        createdAtMs = created_at_ms,
        updatedAtMs = updated_at_ms,
    )
}
