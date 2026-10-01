package com.mini.me_core.feature.agent.domain.session.plan

import com.mini.me_core.core.util.EnumSafe
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_plans as V2AgentPlan
import com.mini.me_core.core.util.FileLogger
import java.util.UUID
import javax.inject.Inject

/**
 * 会话级「计划协作状态」服务（对齐 DSH plan mode + Claude Code Plan/Spec）。
 */
class PlanService @Inject constructor(
    private val v2Agent: V2AgentRepository,
) {

    private companion object {
        const val TAG = "PlanService"
    }

    /** 读取会话最近一份计划；无则返回 null。 */
    suspend fun getLatest(sessionId: String): V2AgentPlan? =
        v2Agent.getLatestPlanBySession(sessionId)

    /** 按 id 读取计划；无则返回 null。 */
    suspend fun getById(planId: String): V2AgentPlan? =
        v2Agent.getPlanById(planId)

    /** 按 id 整行更新（供 plan 工具在非终态校验后落库）。 */
    suspend fun update(planId: String, plan: V2AgentPlan): V2AgentPlan? {
        if (getById(planId) == null) return null
        v2Agent.upsertPlan(
            planId = plan.plan_id,
            sessionId = plan.session_id,
            title = plan.title,
            steps = plan.steps,
            status = plan.status,
            pendingSelection = plan.pending_selection,
            createdAtMs = plan.created_at_ms,
            updatedAtMs = plan.updated_at_ms
        )
        return getById(planId)
    }

    /**
     * 提议新计划（DRAFT）：事务内把会话旧计划置 ABANDONED（若仍非终态），再插入新 DRAFT。
     */
    suspend fun propose(sessionId: String, title: String, stepsJson: String, pendingSelection: String): V2AgentPlan {
        val now = System.currentTimeMillis()
        val planId = UUID.randomUUID().toString()
        v2Agent.runInTx { tx ->
            tx.proposePlan(
                sessionId = sessionId,
                old = v2Agent.getLatestPlanBySessionBlocking(sessionId),
                planId = planId,
                title = title,
                steps = stepsJson,
                status = PlanStatus.DRAFT.name,
                pendingSelection = pendingSelection,
                createdAtMs = now,
                updatedAtMs = now
            )
        }
        FileLogger.d(TAG, "propose: session=$sessionId planId=$planId status=DRAFT")
        return V2AgentPlan(
            plan_id = planId, session_id = sessionId, title = title,
            steps = stepsJson, status = PlanStatus.DRAFT.name,
            pending_selection = pendingSelection,
            created_at_ms = now, updated_at_ms = now
        )
    }

    /** 更新待定选择（用户选中某方案后落库，供下轮注入）。 */
    suspend fun setPendingSelection(planId: String, pendingSelection: String): V2AgentPlan? {
        val existing = getById(planId) ?: return null
        v2Agent.updatePlanContent(
            planId = planId,
            status = existing.status,
            steps = existing.steps,
            pendingSelection = pendingSelection,
            updatedAtMs = System.currentTimeMillis()
        )
        return getById(planId)
    }

    /** 置状态（APPROVED 时清空 pendingSelection）。目标不存在返回 null。 */
    suspend fun setStatus(planId: String, status: PlanStatus): V2AgentPlan? {
        val existing = getById(planId) ?: return null
        val nextPending = if (status == PlanStatus.APPROVED) "" else existing.pending_selection
        v2Agent.updatePlanContent(
            planId = planId,
            status = status.name,
            steps = existing.steps,
            pendingSelection = nextPending,
            updatedAtMs = System.currentTimeMillis()
        )
        return getById(planId)
    }

    /** 批准计划（DRAFT → APPROVED，清空 pendingSelection）。 */
    suspend fun approve(planId: String): V2AgentPlan? = setStatus(planId, PlanStatus.APPROVED)

    /** 放弃计划（置 ABANDONED）。 */
    suspend fun abandon(planId: String): V2AgentPlan? = setStatus(planId, PlanStatus.ABANDONED)
}

/** 计划生命周期状态（对齐 DSH plan status）。 */
enum class PlanStatus {
    DRAFT, APPROVED, EXECUTING, COMPLETED, ABANDONED
}

internal fun V2AgentPlan.statusEnum(): PlanStatus =
    EnumSafe.valueOf(status, PlanStatus.DRAFT, tag = "Agent_plans.status")
