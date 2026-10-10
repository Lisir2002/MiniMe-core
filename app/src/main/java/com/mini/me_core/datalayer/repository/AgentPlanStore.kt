package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_goals
import com.mini.mecore.datalayer.sqldelight.agent.Agent_plans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 目标与计划存储（GoalService / PlanService 等价）：agent_goals、agent_plans 两表。
 * 两者通过 [AgentTx.activateGoal] / [AgentTx.proposePlan] 在事务内联动，故同放一个协作者。
 */
internal class AgentPlanStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── Goals ───────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertGoal(
        goalId: String, sessionId: String, text: String, status: String, revision: Long,
        parentGoalId: String, roundSeq: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertGoal(goalId, sessionId, text, status, revision, parentGoalId, roundSeq, createdAtMs, updatedAtMs)
    }

    suspend fun listGoals(sessionId: String): List<Agent_goals> =
        withContext(Dispatchers.IO) { q.selectGoalsBySession(sessionId).executeAsList() }

    fun observeGoalsBySession(sessionId: String): Flow<List<Agent_goals>> =
        q.selectGoalsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun updateGoalStatus(goalId: String, status: String, updatedAtMs: Long) =
        withContext(Dispatchers.IO) { q.updateGoalStatus(status, updatedAtMs, goalId) }

    suspend fun deleteGoal(goalId: String) = withContext(Dispatchers.IO) { q.deleteGoal(goalId) }

    // ── P2-4 GoalService 等价 ──────────────────────────────────────────

    suspend fun getGoalById(goalId: String): Agent_goals? =
        withContext(Dispatchers.IO) { q.selectGoalById(goalId).executeAsOneOrNull() }

    suspend fun getActiveGoalBySession(sessionId: String): Agent_goals? =
        withContext(Dispatchers.IO) { q.selectActiveGoalBySession(sessionId).executeAsOneOrNull() }

    /** 阻塞版（仅供 [runInTx] 事务块内使用，同线程）。 */
    fun getActiveGoalBySessionBlocking(sessionId: String): Agent_goals? =
        q.selectActiveGoalBySession(sessionId).executeAsOneOrNull()

    suspend fun casUpdateGoalStatusAndText(
        goalId: String, status: String, text: String, newRevision: Long, expectedRevision: Long, updatedAtMs: Long,
    ): Long = withContext(Dispatchers.IO) {
        q.casUpdateGoalStatusAndText(status, text, newRevision, updatedAtMs, goalId, expectedRevision).value
    }

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertGoal(
        goalId: String, sessionId: String, text: String, status: String, revision: Long,
        parentGoalId: String, roundSeq: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertGoal(goalId, sessionId, text, status, revision, parentGoalId, roundSeq, createdAtMs, updatedAtMs)
    }

    suspend fun deleteGoalsBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteGoalsBySession(sessionId) }

    // ── Plans ───────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertPlan(
        planId: String, sessionId: String, title: String, steps: String, status: String,
        pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertPlan(planId, sessionId, title, steps, status, pendingSelection, createdAtMs, updatedAtMs)
    }

    suspend fun listPlans(sessionId: String): List<Agent_plans> =
        withContext(Dispatchers.IO) { q.selectPlansBySession(sessionId).executeAsList() }

    fun observePlansBySession(sessionId: String): Flow<List<Agent_plans>> =
        q.selectPlansBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun updatePlanStatus(planId: String, status: String, updatedAtMs: Long) =
        withContext(Dispatchers.IO) { q.updatePlanStatus(status, updatedAtMs, planId) }

    suspend fun deletePlan(planId: String) = withContext(Dispatchers.IO) { q.deletePlan(planId) }

    // ── P2-4 PlanService 等价 ──────────────────────────────────────────

    suspend fun getPlanById(planId: String): Agent_plans? =
        withContext(Dispatchers.IO) { q.selectPlanById(planId).executeAsOneOrNull() }

    suspend fun getLatestPlanBySession(sessionId: String): Agent_plans? =
        withContext(Dispatchers.IO) { q.selectLatestPlanBySession(sessionId).executeAsOneOrNull() }

    /** 阻塞版（仅供 [runInTx] 事务块内使用，同线程）。 */
    fun getLatestPlanBySessionBlocking(sessionId: String): Agent_plans? =
        q.selectLatestPlanBySession(sessionId).executeAsOneOrNull()

    suspend fun updatePlanContent(
        planId: String, status: String, steps: String, pendingSelection: String, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.updatePlanContent(status, steps, pendingSelection, updatedAtMs, planId)
    }

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertPlan(
        planId: String, sessionId: String, title: String, steps: String, status: String,
        pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertPlan(planId, sessionId, title, steps, status, pendingSelection, createdAtMs, updatedAtMs)
    }

    suspend fun deletePlansBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deletePlansBySession(sessionId) }
}
