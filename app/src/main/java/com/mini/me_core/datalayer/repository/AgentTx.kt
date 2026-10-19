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
    fun deleteMessagePartsBySession(sessionId: String) { q.deleteMessagePartsBySession(sessionId) }
    fun deleteToolCallsBySession(sessionId: String) { q.deleteToolCallsBySession(sessionId) }
    fun deleteAgentCheckpointsBySession(sessionId: String) { q.deleteAgentCheckpointsBySession(sessionId) }
    fun deleteCheckpointsBySession(sessionId: String) { q.deleteCheckpointsBySession(sessionId) }
    fun deleteCheckpointFileSnapshotsBySession(sessionId: String) { q.deleteCheckpointFileSnapshotsBySession(sessionId) }
    fun deletePlaybookRunsBySession(sessionId: String) { q.deletePlaybookRunsBySession(sessionId) }
    fun deleteSentinelsBySession(sessionId: String) { q.deleteSentinelsBySession(sessionId) }
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

    /**
     * 会话级联删除：单事务内按外键层次顺序清空该会话的全部关联数据，最后删会话本身。
     *
     * 顺序依据（[com.mini.me_core.datalayer.encryption.EncryptedDriverFactory] 强制
     * `PRAGMA foreign_keys=ON`，删父行时子行必须已不存在）：
     * 1. agent_message_part / agent_tool_call（外键 → agent_message）
     * 2. agent_message（外键 → agent_session）
     * 3. checkpoint_file_snapshots（外键 → session_checkpoints）→ session_checkpoints
     * 4. agent_checkpoint（外键 → agent_session）
     * 5. 其余无外键的会话域表（待办/目标/计划/作业/日程/轨迹/玩法/技能态/唤醒/模式切换/文件块）
     * 6. agent_session
     *
     * 不级联的两张 zth 表是**审计/恢复账本**，按设计保留：
     * zth_hard_constraint_delete_audits（回滚恢复扫描的数据源）、
     * zth_l0_soft_compact_restore_logs（expire_at_ms TTL 清理），随会话删除会破坏恢复语义。
     */
    fun deleteSessionCascade(sessionId: String) {
        deleteMessagePartsBySession(sessionId)
        deleteToolCallsBySession(sessionId)
        deleteBySession(sessionId)
        deleteCheckpointFileSnapshotsBySession(sessionId)
        deleteCheckpointsBySession(sessionId)
        deleteAgentCheckpointsBySession(sessionId)
        deleteTodosBySession(sessionId)
        deleteFileEditHunksBySession(sessionId)
        deleteModeSwitchesBySession(sessionId)
        deleteSkillConversationStatesBySession(sessionId)
        deleteWakeItemsBySession(sessionId)
        deleteGoalsBySession(sessionId)
        deletePlansBySession(sessionId)
        deleteJobsBySession(sessionId)
        deleteSchedulesBySession(sessionId)
        deleteTrajectories(sessionId)
        deletePlaybookRunsBySession(sessionId)
        deleteSentinelsBySession(sessionId)
        deleteSession(sessionId)
    }

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
