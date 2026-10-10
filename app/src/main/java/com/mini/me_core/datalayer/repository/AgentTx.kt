package com.mini.me_core.datalayer.repository

import com.mini.me_core.datalayer.exception.DataLayerErrorCode
import com.mini.me_core.datalayer.exception.DataLayerException
import com.mini.mecore.datalayer.sqldelight.agent.AgentQueries
import com.mini.mecore.datalayer.sqldelight.agent.Agent_goals
import com.mini.mecore.datalayer.sqldelight.agent.Agent_plans
import com.mini.mecore.datalayer.sqldelight.agent.Todo_items

/** V2 agent 域事务门面：仅供 [AgentRepository.runInTx] 事务块内使用（阻塞执行，同线程）。 */
class AgentTx internal constructor(private val q: AgentQueries) {
    fun deleteBySession(sessionId: String) { q.deleteMessagesBySession(sessionId) }
    fun deleteTodosBySession(sessionId: String) { q.deleteTodoItemsBySession(sessionId) }
    fun deleteFileEditHunksBySession(sessionId: String) { q.deleteFileEditHunksBySession(sessionId) }
    fun deleteModeSwitchesBySession(sessionId: String) { q.deleteModeSwitchesBySession(sessionId) }
    fun deleteSkillConversationStatesBySession(sessionId: String) { q.deleteSkillConversationStatesBySession(sessionId) }
    fun deleteWakeItemsBySession(sessionId: String) { q.deleteWakeItemsBySession(sessionId) }
    fun deleteGoalsBySession(sessionId: String) { q.deleteGoalsBySession(sessionId) }
    fun deletePlansBySession(sessionId: String) { q.deletePlansBySession(sessionId) }
    fun deleteJobsBySession(sessionId: String) { q.deleteJobsBySession(sessionId) }
    fun deleteSchedulesBySession(sessionId: String) { q.deleteSchedulesBySession(sessionId) }
    fun deleteTrajectories(sessionId: String) { q.deleteTrajectoriesBySession(sessionId) }
    fun deleteSession(id: String) { q.deleteSession(id) }

    // TodoTool replaceTodos 事务：delete + upsert 原子替换
    fun replaceTodos(sessionId: String, todos: List<Todo_items>) {
        q.deleteTodoItemsBySession(sessionId)
        todos.forEach { t ->
            q.upsertTodoItem(t.id, t.session_id, t.subject, t.description, t.status, t.priority, t.sort_order, t.created_at_ms, t.updated_at_ms)
        }
    }

    // GoalService.activate 事务：CAS 放弃旧 ACTIVE + 插入新目标
    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    fun activateGoal(
        sessionId: String,
        old: Agent_goals?,
        goalId: String, text: String, status: String, revision: Long,
        parentGoalId: String, roundSeq: Long, createdAtMs: Long, updatedAtMs: Long,
    ) {
        old?.let { o ->
            // M9：CAS 的**受影响行数必须判等 1**。
            // 过去返回值被丢弃，一旦 revision 被并发改掉（CAS 未命中，返回 0），
            // 旧 ACTIVE 目标不会被放弃，而新目标照常插入 —— 同一会话出现两个 ACTIVE 目标，
            // Agent 行为随之分裂。现在未命中即抛 CONCURRENT_ACCESS，整事务回滚，由调用方重试。
            val affected = q.casUpdateGoalStatusAndText(
                "ABANDONED", o.text, o.revision + 1, updatedAtMs, o.goal_id, o.revision,
            ).value
            if (affected != 1L) {
                throw DataLayerException(
                    "放弃旧 ACTIVE 目标失败（CAS 未命中，revision 疑似被并发修改）：" +
                        "goal=${o.goal_id}, expectedRevision=${o.revision}, affected=$affected",
                    DataLayerErrorCode.CONCURRENT_ACCESS,
                )
            }
        }
        q.upsertGoal(goalId, sessionId, text, status, revision, parentGoalId, roundSeq, createdAtMs, updatedAtMs)
    }

    // PlanService.propose 事务：旧计划置 ABANDONED + 插入新 DRAFT
    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    fun proposePlan(
        sessionId: String,
        old: Agent_plans?,
        planId: String, title: String, steps: String, status: String, pendingSelection: String,
        createdAtMs: Long, updatedAtMs: Long,
    ) {
        old?.let { o ->
            q.updatePlanContent("ABANDONED", o.steps, o.pending_selection, updatedAtMs, o.plan_id)
        }
        q.upsertPlan(planId, sessionId, title, steps, status, pendingSelection, createdAtMs, updatedAtMs)
    }
}
