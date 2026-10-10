package com.mini.me_core.datalayer.repository

import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_checkpoint
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message
import com.mini.mecore.datalayer.sqldelight.agent.Agent_goals
import com.mini.mecore.datalayer.sqldelight.agent.Agent_jobs
import com.mini.mecore.datalayer.sqldelight.agent.Agent_playbook_runs
import com.mini.mecore.datalayer.sqldelight.agent.Agent_plans
import com.mini.mecore.datalayer.sqldelight.agent.Agent_schedules
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session
import com.mini.mecore.datalayer.sqldelight.agent.Agent_trajectories
import com.mini.mecore.datalayer.sqldelight.agent.Checkpoint_file_snapshots
import com.mini.mecore.datalayer.sqldelight.agent.File_edit_hunks
import com.mini.mecore.datalayer.sqldelight.agent.Model_capability_overrides
import com.mini.mecore.datalayer.sqldelight.agent.Model_custom_configs
import com.mini.mecore.datalayer.sqldelight.agent.Model_sampling_configs
import com.mini.mecore.datalayer.sqldelight.agent.Mode_switch_history
import com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount
import com.mini.mecore.datalayer.sqldelight.agent.SelectTrajectoryAggregateBySession
import com.mini.mecore.datalayer.sqldelight.agent.Session_checkpoints
import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state
import com.mini.mecore.datalayer.sqldelight.agent.Skill_state
import com.mini.mecore.datalayer.sqldelight.agent.Todo_items
import com.mini.mecore.datalayer.sqldelight.agent.Wake_queue
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hallucination_fuses
import com.mini.mecore.datalayer.sqldelight.agent.Zth_hard_constraint_delete_audits
import com.mini.mecore.datalayer.sqldelight.agent.Zth_l0_soft_compact_restore_logs
import com.mini.mecore.datalayer.sqldelight.agent.Zth_sentinel_plan_rejection_audits
import com.mini.mecore.datalayer.sqldelight.agent.Zth_telemetry_events
import com.mini.mecore.datalayer.sqldelight.agent.Zth_user_confirmed_sentinels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * agent 域 Repository（设计 §11.1 / L2）：会话/消息/子块/工具调用/检查点 的访问门面。
 * 业务只依赖本门面，不直接写 SQL。
 *
 * v2-full-takeover P0-1：补 Flow 响应式读，对齐 Room DAO 的 22 个 Flow 查询。
 * v2-full-takeover P2-3：agent_session / agent_message 对齐 Room 实体全列，
 *  本门面补齐 ChatSessionDao(22) + AgentMessageDao(25) 全部等价方法。
 *
 * P3-16（本阶段拆分）：按职责下沉为同包协作者，本类保留为薄门面——
 * 类名、构造签名、[WakeQueueStore] 实现、Hilt 绑定、所有 public 方法签名与行为均不变；
 * 仅把调用转发给下列 store，事务封装 [runInTx] 仍在门面。
 */
class AgentRepository(private val db: AgentDb) : WakeQueueStore {

    private val q get() = db.agentQueries

    private val sessions = AgentSessionStore(db)
    private val messages = AgentMessageStore(db)
    private val compaction = AgentCompactionStore(db)
    private val todos = AgentTodoStore(db)
    private val checkpoints = AgentCheckpointStore(db)
    private val plans = AgentPlanStore(db)
    private val jobSchedules = AgentJobScheduleStore(db)
    private val trajectories = AgentTrajectoryStore(db)
    private val skills = AgentSkillStore(db)
    private val wake = AgentWakeStore(db)
    private val zth = AgentZthStore(db)
    private val modelConfigs = AgentModelConfigStore(db)

    // ── 会话（ChatSessionDao 等价）──────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertSession(
        id: String, title: String?, mode: String, model: String?, status: String,
        createdAtMs: Long, updatedAtMs: Long, workspacePath: String, reasoningEffort: String,
        providerId: String?, totalInputTokens: Long, totalOutputTokens: Long, lastInputTokens: Long,
    ) = sessions.upsertSession(
        id, title, mode, model, status, createdAtMs, updatedAtMs, workspacePath,
        reasoningEffort, providerId, totalInputTokens, totalOutputTokens, lastInputTokens,
    )

    suspend fun createSession(
        id: String, title: String?, mode: String, model: String?, now: Long = System.currentTimeMillis(),
    ) = sessions.createSession(id, title, mode, model, now)

    suspend fun listSessions(): List<Agent_session> = sessions.listSessions()

    suspend fun getSession(id: String): Agent_session? = sessions.getSession(id)

    suspend fun renameSession(id: String, title: String, now: Long = System.currentTimeMillis()) =
        sessions.renameSession(id, title, now)

    suspend fun deleteSession(id: String) = sessions.deleteSession(id)

    /** 备份「覆盖恢复」专用：清空会话大表（消息/待办需各自调用 deleteAllMessages / deleteAllTodos）。 */
    suspend fun deleteAllSessions() = sessions.deleteAllSessions()

    suspend fun getAllSessionsByWorkspaceOnce(workspacePath: String): List<Agent_session> =
        sessions.getAllSessionsByWorkspaceOnce(workspacePath)

    fun observeAllSessionsByWorkspace(workspacePath: String): Flow<List<Agent_session>> =
        sessions.observeAllSessionsByWorkspace(workspacePath)

    fun observeAllSessionsWithCount(): Flow<List<SelectAllSessionsWithCount>> =
        sessions.observeAllSessionsWithCount()

    suspend fun getAllOnce(): List<Agent_session> = sessions.getAllOnce()

    suspend fun getMostRecentOnce(): Agent_session? = sessions.getMostRecentOnce()

    suspend fun getUnboundSessionsOnce(): List<Agent_session> = sessions.getUnboundSessionsOnce()

    suspend fun countSessions(): Long = sessions.countSessions()

    suspend fun getSessionPageAfter(
        lastUpdatedAtMs: Long, lastId: String, limit: Long,
    ): List<Agent_session> = sessions.getSessionPageAfter(lastUpdatedAtMs, lastId, limit)

    suspend fun upsertAllSessions(sessions: List<Agent_session>) = this.sessions.upsertAllSessions(sessions)

    suspend fun getSessionById(id: String): Agent_session? = sessions.getSessionById(id)

    suspend fun updateTitle(id: String, title: String) = sessions.updateTitle(id, title)

    suspend fun updateWorkspacePath(oldPath: String, newPath: String) =
        sessions.updateWorkspacePath(oldPath, newPath)

    suspend fun setWorkspacePath(id: String, path: String) = sessions.setWorkspacePath(id, path)

    suspend fun touch(id: String, updatedAtMs: Long) = sessions.touch(id, updatedAtMs)

    suspend fun updateProviderModel(id: String, providerId: String?, model: String?) =
        sessions.updateProviderModel(id, providerId, model)

    suspend fun updateReasoningEffort(id: String, effort: String) =
        sessions.updateReasoningEffort(id, effort)

    suspend fun addTokenUsage(id: String, inputTokens: Long, outputTokens: Long) =
        sessions.addTokenUsage(id, inputTokens, outputTokens)

    suspend fun updateLastInputTokens(id: String, lastInputTokens: Long) =
        sessions.updateLastInputTokens(id, lastInputTokens)

    fun observeAllSessions(): Flow<List<Agent_session>> = sessions.observeAllSessions()

    fun observeSessionById(id: String): Flow<Agent_session?> = sessions.observeSessionById(id)

    // ── 消息（AgentMessageDao 等价）──────────────────────────────────────

    suspend fun insertMessage(e: Agent_message) = messages.insertMessage(e)

    suspend fun insertAllMessages(messages: List<Agent_message>) =
        this.messages.insertAllMessages(messages)

    fun observeMessagesBySession(sessionId: String): Flow<List<Agent_message>> =
        messages.observeMessagesBySession(sessionId)

    fun observeMessagesBySessionPaged(sessionId: String, limit: Long): Flow<List<Agent_message>> =
        messages.observeMessagesBySessionPaged(sessionId, limit)

    suspend fun getMessagesBySessionOnce(sessionId: String): List<Agent_message> =
        messages.getMessagesBySessionOnce(sessionId)

    suspend fun deleteBySession(sessionId: String) = messages.deleteBySession(sessionId)

    suspend fun deleteAllMessages() = messages.deleteAllMessages()

    suspend fun getMessageById(id: String): Agent_message? = messages.getMessageById(id)

    suspend fun updateMessageContent(id: String, content: String) =
        messages.updateMessageContent(id, content)

    suspend fun updateMessageContentAndReasoning(id: String, content: String, reasoning: String?) =
        messages.updateMessageContentAndReasoning(id, content, reasoning)

    suspend fun deleteMessageById(id: String) = messages.deleteMessageById(id)

    suspend fun markPendingToolsInterrupted(
        toolRole: String, pendingPrefix: String, interruptedContent: String,
    ): Long = messages.markPendingToolsInterrupted(toolRole, pendingPrefix, interruptedContent)

    suspend fun searchMessages(query: String): List<Agent_message> = messages.searchMessages(query)

    suspend fun getAllMessagesOnce(): List<Agent_message> = messages.getAllMessagesOnce()

    suspend fun getMessagePageAfter(
        lastTimestamp: Long, lastId: String, limit: Long,
    ): List<Agent_message> = messages.getMessagePageAfter(lastTimestamp, lastId, limit)

    suspend fun getPageBySessionAfter(
        sessionId: String, lastTimestamp: Long, lastId: String, limit: Long,
    ): List<Agent_message> =
        messages.getPageBySessionAfter(sessionId, lastTimestamp, lastId, limit)

    // ── 上下文压缩/摘要 ────────────────────────────────────────────────

    suspend fun deleteMessagesBeforeTimestamp(sessionId: String, cutoffTimestamp: Long) =
        compaction.deleteMessagesBeforeTimestamp(sessionId, cutoffTimestamp)

    suspend fun markMessagesCompactedBeforeTimestamp(sessionId: String, cutoffTimestamp: Long) =
        compaction.markMessagesCompactedBeforeTimestamp(sessionId, cutoffTimestamp)

    suspend fun markMessagesCompactedInclusiveFromTimestamp(sessionId: String, cutoffTimestamp: Long) =
        compaction.markMessagesCompactedInclusiveFromTimestamp(sessionId, cutoffTimestamp)

    suspend fun deleteMessagesInclusiveFromTimestamp(sessionId: String, cutoffTimestamp: Long) =
        compaction.deleteMessagesInclusiveFromTimestamp(sessionId, cutoffTimestamp)

    suspend fun deleteMessagesExclusiveAfterTimestamp(sessionId: String, cutoffTimestamp: Long) =
        compaction.deleteMessagesExclusiveAfterTimestamp(sessionId, cutoffTimestamp)

    fun observeRestoreLogsBySession(sessionId: String): Flow<List<Zth_l0_soft_compact_restore_logs>> =
        compaction.observeRestoreLogsBySession(sessionId)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertRestoreLog(
        id: String, sessionId: String, firstMessageId: String, lastMessageId: String,
        originalRowCount: Long, tokensBefore: Long, tokensAfter: Long,
        sCompactSourceDigestCiphertext: String, expireAtMs: Long, restoredFlag: Long, createdAtMs: Long,
    ) = compaction.insertRestoreLog(
        id, sessionId, firstMessageId, lastMessageId, originalRowCount, tokensBefore,
        tokensAfter, sCompactSourceDigestCiphertext, expireAtMs, restoredFlag, createdAtMs,
    )

    suspend fun listRestoreLogs(sessionId: String): List<Zth_l0_soft_compact_restore_logs> =
        compaction.listRestoreLogs(sessionId)

    suspend fun listRestoreLogsExpiredNotRestored(nowMs: Long): List<Zth_l0_soft_compact_restore_logs> =
        compaction.listRestoreLogsExpiredNotRestored(nowMs)

    suspend fun listAllRestoreLogs(): List<Zth_l0_soft_compact_restore_logs> =
        compaction.listAllRestoreLogs()

    suspend fun markRestoreLogRestored(id: String) = compaction.markRestoreLogRestored(id)

    // P3 已完成：原 legacy V1 写入方法块（appendMessage/appendPart/appendToolCall/saveCheckpoint）
    // 及其唯一依赖 V1toV2FullMigrator 已全仓零调用、迁移类已不存在，按「P3 后删除」清理。

    // ── 阶段 1 补表：todo / checkpoint 快照 / 编辑 hunk / 模式切换 ──────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTodo(
        id: String, sessionId: String, subject: String, description: String, status: String,
        priority: Long, order: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = todos.insertTodo(
        id, sessionId, subject, description, status, priority, order, createdAtMs, updatedAtMs,
    )

    suspend fun listTodos(sessionId: String): List<Todo_items> = todos.listTodos(sessionId)

    suspend fun updateTodoStatus(id: String, status: String, updatedAtMs: Long) =
        todos.updateTodoStatus(id, status, updatedAtMs)

    suspend fun deleteTodo(id: String) = todos.deleteTodo(id)

    suspend fun getTodoPageAfter(
        lastCreatedAtMs: Long, lastId: String, limit: Long,
    ): List<Todo_items> = todos.getTodoPageAfter(lastCreatedAtMs, lastId, limit)

    suspend fun getTodoPageBySessionAfter(
        sessionId: String, lastCreatedAtMs: Long, lastId: String, limit: Long,
    ): List<Todo_items> =
        todos.getTodoPageBySessionAfter(sessionId, lastCreatedAtMs, lastId, limit)

    suspend fun upsertAllTodos(todos: List<Todo_items>) = this.todos.upsertAllTodos(todos)

    suspend fun deleteTodosBySession(sessionId: String) = todos.deleteTodosBySession(sessionId)

    suspend fun deleteAllTodos() = todos.deleteAllTodos()

    // ── checkpoint（V1 列表 + V2 session_checkpoints + 文件快照）─────────

    suspend fun listCheckpoints(sessionId: String): List<Agent_checkpoint> =
        checkpoints.listCheckpoints(sessionId)

    fun observeCheckpointsBySession(sessionId: String): Flow<List<Agent_checkpoint>> =
        checkpoints.observeCheckpointsBySession(sessionId)

    suspend fun insertCheckpointFull(
        id: String, sessionId: String, userMessageId: String, promptSnippet: String, createdAtMs: Long,
    ) = checkpoints.insertCheckpointFull(id, sessionId, userMessageId, promptSnippet, createdAtMs)

    suspend fun getCheckpointById(checkpointId: String): Session_checkpoints? =
        checkpoints.getCheckpointById(checkpointId)

    suspend fun getCheckpointByMessageId(messageId: String): Session_checkpoints? =
        checkpoints.getCheckpointByMessageId(messageId)

    suspend fun listCheckpointsForSession(sessionId: String): List<Session_checkpoints> =
        checkpoints.listCheckpointsForSession(sessionId)

    suspend fun deleteCheckpointsBySession(sessionId: String) =
        checkpoints.deleteCheckpointsBySession(sessionId)

    suspend fun deleteCheckpointsBefore(cutoffTimestamp: Long) =
        checkpoints.deleteCheckpointsBefore(cutoffTimestamp)

    suspend fun insertCheckpointFileSnapshot(
        id: String, checkpointId: String, filePath: String, snapshotRelativePath: String,
        changeType: String, createdAt: Long,
    ) = checkpoints.insertCheckpointFileSnapshot(
        id, checkpointId, filePath, snapshotRelativePath, changeType, createdAt,
    )

    suspend fun listCheckpointFileSnapshots(checkpointId: String): List<Checkpoint_file_snapshots> =
        checkpoints.listCheckpointFileSnapshots(checkpointId)

    suspend fun countCheckpointFileSnapshot(checkpointId: String, filePath: String): Long =
        checkpoints.countCheckpointFileSnapshot(checkpointId, filePath)

    suspend fun deleteCheckpointFileSnapshotsBySession(sessionId: String) =
        checkpoints.deleteCheckpointFileSnapshotsBySession(sessionId)

    // ── 文件编辑 hunk / 模式切换 ────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertFileEditHunk(
        id: String, sessionId: String, filePath: String, operation: String, hunk: String,
        oldContent: String, newContent: String, createdAtMs: Long,
    ) = checkpoints.insertFileEditHunk(
        id, sessionId, filePath, operation, hunk, oldContent, newContent, createdAtMs,
    )

    suspend fun listFileEditHunks(sessionId: String): List<File_edit_hunks> =
        checkpoints.listFileEditHunks(sessionId)

    fun observeFileEditHunksBySession(sessionId: String): Flow<List<File_edit_hunks>> =
        checkpoints.observeFileEditHunksBySession(sessionId)

    suspend fun deleteFileEditHunksBySession(sessionId: String) =
        checkpoints.deleteFileEditHunksBySession(sessionId)

    suspend fun insertModeSwitch(
        sessionId: String, fromMode: String, toMode: String, reason: String, timestampMs: Long,
    ) = checkpoints.insertModeSwitch(sessionId, fromMode, toMode, reason, timestampMs)

    suspend fun listModeSwitches(sessionId: String): List<Mode_switch_history> =
        checkpoints.listModeSwitches(sessionId)

    fun observeModeSwitchesBySession(sessionId: String): Flow<List<Mode_switch_history>> =
        checkpoints.observeModeSwitchesBySession(sessionId)

    suspend fun deleteModeSwitchesBySession(sessionId: String) =
        checkpoints.deleteModeSwitchesBySession(sessionId)

    // ── 模型覆盖：能力 / token 上限 / 采样参数 ───────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertCapabilityOverride(
        id: String, providerType: String, modelId: String,
        overrideVision: Long?, overrideTools: Long?, overrideReasoning: Long?,
        overrideVideo: Long?, overrideAudio: Long?, overrideCode: Long?, overrideStructuredOutput: Long?,
        updatedAtMs: Long,
    ) = modelConfigs.upsertCapabilityOverride(
        id, providerType, modelId, overrideVision, overrideTools, overrideReasoning,
        overrideVideo, overrideAudio, overrideCode, overrideStructuredOutput, updatedAtMs,
    )

    suspend fun getCapabilityOverride(providerType: String, modelId: String): Model_capability_overrides? =
        modelConfigs.getCapabilityOverride(providerType, modelId)

    fun observeCapabilityOverride(
        providerType: String, modelId: String,
    ): Flow<List<Model_capability_overrides>> =
        modelConfigs.observeCapabilityOverride(providerType, modelId)

    suspend fun deleteCapabilityOverride(providerType: String, modelId: String) =
        modelConfigs.deleteCapabilityOverride(providerType, modelId)

    suspend fun deleteCapabilityOverridesByProvider(providerType: String) =
        modelConfigs.deleteCapabilityOverridesByProvider(providerType)

    suspend fun upsertCustomConfig(
        id: String, providerType: String, modelId: String,
        customInputTokens: Long?, customOutputTokens: Long?, updatedAtMs: Long,
    ) = modelConfigs.upsertCustomConfig(
        id, providerType, modelId, customInputTokens, customOutputTokens, updatedAtMs,
    )

    suspend fun getCustomConfig(providerType: String, modelId: String): Model_custom_configs? =
        modelConfigs.getCustomConfig(providerType, modelId)

    fun observeCustomConfig(providerType: String, modelId: String): Flow<Model_custom_configs?> =
        modelConfigs.observeCustomConfig(providerType, modelId)

    suspend fun deleteCustomConfig(providerType: String, modelId: String) =
        modelConfigs.deleteCustomConfig(providerType, modelId)

    suspend fun deleteCustomConfigsByProvider(providerType: String) =
        modelConfigs.deleteCustomConfigsByProvider(providerType)

    suspend fun upsertSamplingConfig(
        id: String, providerType: String, modelId: String,
        customTemperature: Double?, customTopP: Double?, customMaxTokens: Long?, updatedAtMs: Long,
    ) = modelConfigs.upsertSamplingConfig(
        id, providerType, modelId, customTemperature, customTopP, customMaxTokens, updatedAtMs,
    )

    suspend fun getSamplingConfig(providerType: String, modelId: String): Model_sampling_configs? =
        modelConfigs.getSamplingConfig(providerType, modelId)

    fun observeSamplingConfig(
        providerType: String, modelId: String,
    ): Flow<Model_sampling_configs?> =
        modelConfigs.observeSamplingConfig(providerType, modelId)

    suspend fun deleteSamplingConfig(providerType: String, modelId: String) =
        modelConfigs.deleteSamplingConfig(providerType, modelId)

    suspend fun deleteSamplingConfigsByProvider(providerType: String) =
        modelConfigs.deleteSamplingConfigsByProvider(providerType)

    // ── Goals / Plans ──────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertGoal(
        goalId: String, sessionId: String, text: String, status: String, revision: Long,
        parentGoalId: String, roundSeq: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = plans.insertGoal(
        goalId, sessionId, text, status, revision, parentGoalId, roundSeq, createdAtMs, updatedAtMs,
    )

    suspend fun listGoals(sessionId: String): List<Agent_goals> = plans.listGoals(sessionId)

    fun observeGoalsBySession(sessionId: String): Flow<List<Agent_goals>> =
        plans.observeGoalsBySession(sessionId)

    suspend fun updateGoalStatus(goalId: String, status: String, updatedAtMs: Long) =
        plans.updateGoalStatus(goalId, status, updatedAtMs)

    suspend fun deleteGoal(goalId: String) = plans.deleteGoal(goalId)

    suspend fun getGoalById(goalId: String): Agent_goals? = plans.getGoalById(goalId)

    suspend fun getActiveGoalBySession(sessionId: String): Agent_goals? =
        plans.getActiveGoalBySession(sessionId)

    /** 阻塞版（仅供 [runInTx] 事务块内使用，同线程）。 */
    fun getActiveGoalBySessionBlocking(sessionId: String): Agent_goals? =
        plans.getActiveGoalBySessionBlocking(sessionId)

    suspend fun casUpdateGoalStatusAndText(
        goalId: String, status: String, text: String, newRevision: Long,
        expectedRevision: Long, updatedAtMs: Long,
    ): Long = plans.casUpdateGoalStatusAndText(
        goalId, status, text, newRevision, expectedRevision, updatedAtMs,
    )

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertGoal(
        goalId: String, sessionId: String, text: String, status: String, revision: Long,
        parentGoalId: String, roundSeq: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = plans.upsertGoal(
        goalId, sessionId, text, status, revision, parentGoalId, roundSeq, createdAtMs, updatedAtMs,
    )

    suspend fun deleteGoalsBySession(sessionId: String) = plans.deleteGoalsBySession(sessionId)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertPlan(
        planId: String, sessionId: String, title: String, steps: String, status: String,
        pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    ) = plans.insertPlan(
        planId, sessionId, title, steps, status, pendingSelection, createdAtMs, updatedAtMs,
    )

    suspend fun listPlans(sessionId: String): List<Agent_plans> = plans.listPlans(sessionId)

    fun observePlansBySession(sessionId: String): Flow<List<Agent_plans>> =
        plans.observePlansBySession(sessionId)

    suspend fun updatePlanStatus(planId: String, status: String, updatedAtMs: Long) =
        plans.updatePlanStatus(planId, status, updatedAtMs)

    suspend fun deletePlan(planId: String) = plans.deletePlan(planId)

    suspend fun getPlanById(planId: String): Agent_plans? = plans.getPlanById(planId)

    suspend fun getLatestPlanBySession(sessionId: String): Agent_plans? =
        plans.getLatestPlanBySession(sessionId)

    /** 阻塞版（仅供 [runInTx] 事务块内使用，同线程）。 */
    fun getLatestPlanBySessionBlocking(sessionId: String): Agent_plans? =
        plans.getLatestPlanBySessionBlocking(sessionId)

    suspend fun updatePlanContent(
        planId: String, status: String, steps: String, pendingSelection: String, updatedAtMs: Long,
    ) = plans.updatePlanContent(planId, status, steps, pendingSelection, updatedAtMs)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertPlan(
        planId: String, sessionId: String, title: String, steps: String, status: String,
        pendingSelection: String, createdAtMs: Long, updatedAtMs: Long,
    ) = plans.upsertPlan(
        planId, sessionId, title, steps, status, pendingSelection, createdAtMs, updatedAtMs,
    )

    suspend fun deletePlansBySession(sessionId: String) = plans.deletePlansBySession(sessionId)

    // ── Jobs / Schedules ──────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertJob(
        jobId: String, sessionId: String, kind: String, title: String, status: String,
        exitCode: Long?, outputLocator: String, createdAtMs: Long, finishedAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.insertJob(
        jobId, sessionId, kind, title, status, exitCode, outputLocator,
        createdAtMs, finishedAtMs, updatedAtMs,
    )

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertJob(
        jobId: String, sessionId: String, kind: String, title: String, status: String,
        exitCode: Long?, outputLocator: String, createdAtMs: Long, finishedAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.upsertJob(
        jobId, sessionId, kind, title, status, exitCode, outputLocator,
        createdAtMs, finishedAtMs, updatedAtMs,
    )

    suspend fun getJobById(jobId: String): Agent_jobs? = jobSchedules.getJobById(jobId)

    suspend fun listJobs(sessionId: String): List<Agent_jobs> = jobSchedules.listJobs(sessionId)

    fun observeJobsBySession(sessionId: String): Flow<List<Agent_jobs>> =
        jobSchedules.observeJobsBySession(sessionId)

    suspend fun listRunningJobs(): List<Agent_jobs> = jobSchedules.listRunningJobs()

    suspend fun updateJobStatus(
        jobId: String, status: String, exitCode: Long?, outputLocator: String,
        finishedAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.updateJobStatus(
        jobId, status, exitCode, outputLocator, finishedAtMs, updatedAtMs,
    )

    suspend fun updateJobResult(
        jobId: String, status: String, exitCode: Long?, finishedAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.updateJobResult(jobId, status, exitCode, finishedAtMs, updatedAtMs)

    suspend fun deleteJob(jobId: String) = jobSchedules.deleteJob(jobId)

    suspend fun deleteJobsBySession(sessionId: String) =
        jobSchedules.deleteJobsBySession(sessionId)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertSchedule(
        scheduleId: String, sessionId: String, rule: String, args: String, status: String,
        enabled: Long, createdAtMs: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.insertSchedule(
        scheduleId, sessionId, rule, args, status, enabled, createdAtMs, lastFiredAtMs, updatedAtMs,
    )

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertSchedule(
        scheduleId: String, sessionId: String, rule: String, args: String, status: String,
        enabled: Long, createdAtMs: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.upsertSchedule(
        scheduleId, sessionId, rule, args, status, enabled, createdAtMs, lastFiredAtMs, updatedAtMs,
    )

    suspend fun getScheduleById(scheduleId: String): Agent_schedules? =
        jobSchedules.getScheduleById(scheduleId)

    suspend fun listSchedules(sessionId: String): List<Agent_schedules> =
        jobSchedules.listSchedules(sessionId)

    fun observeSchedulesBySession(sessionId: String): Flow<List<Agent_schedules>> =
        jobSchedules.observeSchedulesBySession(sessionId)

    suspend fun getPendingSchedules(): List<Agent_schedules> =
        jobSchedules.getPendingSchedules()

    suspend fun updateScheduleStatus(
        scheduleId: String, status: String, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.updateScheduleStatus(scheduleId, status, lastFiredAtMs, updatedAtMs)

    suspend fun updateScheduleState(
        scheduleId: String, status: String, enabled: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = jobSchedules.updateScheduleState(
        scheduleId, status, enabled, lastFiredAtMs, updatedAtMs,
    )

    suspend fun deleteSchedule(scheduleId: String) = jobSchedules.deleteSchedule(scheduleId)

    suspend fun deleteSchedulesBySession(sessionId: String) =
        jobSchedules.deleteSchedulesBySession(sessionId)

    // ── Trajectories / Playbook runs ───────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTrajectory(
        trajectoryId: String, sessionId: String, taskId: String, turnIndex: Long, kind: String,
        toolName: String, argsHash: String, resultSummary: String, isError: Long, durationMs: Long,
        tokensIn: Long, tokensOut: Long, ts: Long,
    ) = trajectories.insertTrajectory(
        trajectoryId, sessionId, taskId, turnIndex, kind, toolName, argsHash,
        resultSummary, isError, durationMs, tokensIn, tokensOut, ts,
    )

    suspend fun listTrajectories(sessionId: String): List<Agent_trajectories> =
        trajectories.listTrajectories(sessionId)

    fun observeTrajectoriesBySession(sessionId: String): Flow<List<Agent_trajectories>> =
        trajectories.observeTrajectoriesBySession(sessionId)

    suspend fun listTrajectoriesByTask(taskId: String): List<Agent_trajectories> =
        trajectories.listTrajectoriesByTask(taskId)

    suspend fun getLatestTaskId(sessionId: String): String? =
        trajectories.getLatestTaskId(sessionId)

    suspend fun getMaxTurnIndex(sessionId: String, taskId: String): Int? =
        trajectories.getMaxTurnIndex(sessionId, taskId)

    suspend fun getTrajectoryAggregate(sessionId: String): SelectTrajectoryAggregateBySession =
        trajectories.getTrajectoryAggregate(sessionId)

    suspend fun deleteTrajectories(sessionId: String) =
        trajectories.deleteTrajectories(sessionId)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertPlaybookRun(
        playbookRunId: String, sessionId: String, playbookName: String, currentStageIndex: Long,
        stageStatuses: String, status: String, createdAtMs: Long, updatedAtMs: Long,
    ) = trajectories.insertPlaybookRun(
        playbookRunId, sessionId, playbookName, currentStageIndex, stageStatuses,
        status, createdAtMs, updatedAtMs,
    )

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertPlaybookRun(
        playbookRunId: String, sessionId: String, playbookName: String, currentStageIndex: Long,
        stageStatuses: String, status: String, createdAtMs: Long, updatedAtMs: Long,
    ) = trajectories.upsertPlaybookRun(
        playbookRunId, sessionId, playbookName, currentStageIndex, stageStatuses,
        status, createdAtMs, updatedAtMs,
    )

    suspend fun getPlaybookRunById(runId: String): Agent_playbook_runs? =
        trajectories.getPlaybookRunById(runId)

    suspend fun listPlaybookRuns(sessionId: String): List<Agent_playbook_runs> =
        trajectories.listPlaybookRuns(sessionId)

    fun observePlaybookRunsBySession(sessionId: String): Flow<List<Agent_playbook_runs>> =
        trajectories.observePlaybookRunsBySession(sessionId)

    suspend fun getLatestPlaybookBySession(sessionId: String): Agent_playbook_runs? =
        trajectories.getLatestPlaybookBySession(sessionId)

    suspend fun getLatestPlaybookBySessionAndStatus(
        sessionId: String, status: String,
    ): Agent_playbook_runs? = trajectories.getLatestPlaybookBySessionAndStatus(sessionId, status)

    suspend fun listRunningPlaybookRuns(): List<Agent_playbook_runs> =
        trajectories.listRunningPlaybookRuns()

    suspend fun updatePlaybookRun(
        playbookRunId: String, currentStageIndex: Long, stageStatuses: String,
        status: String, updatedAtMs: Long,
    ) = trajectories.updatePlaybookRun(
        playbookRunId, currentStageIndex, stageStatuses, status, updatedAtMs,
    )

    suspend fun deletePlaybookRunsBySession(sessionId: String) =
        trajectories.deletePlaybookRunsBySession(sessionId)

    // ── Skill states ───────────────────────────────────────────────────

    suspend fun upsertSkillState(
        id: String, enabled: Long, version: String, source: String, installedAtMs: Long,
        scopeOverride: String?, agentTypeOverride: String?,
    ) = skills.upsertSkillState(
        id, enabled, version, source, installedAtMs, scopeOverride, agentTypeOverride,
    )

    suspend fun getSkillState(id: String): Skill_state? = skills.getSkillState(id)

    suspend fun listSkillStates(): List<Skill_state> = skills.listSkillStates()

    fun observeAllSkillStates(): Flow<List<Skill_state>> = skills.observeAllSkillStates()

    suspend fun upsertSkillConversationState(skillId: String, sessionId: String, enabled: Long) =
        skills.upsertSkillConversationState(skillId, sessionId, enabled)

    suspend fun getSkillConversationState(
        skillId: String, sessionId: String,
    ): Skill_conversation_state? = skills.getSkillConversationState(skillId, sessionId)

    suspend fun listSkillConversationStates(sessionId: String): List<Skill_conversation_state> =
        skills.listSkillConversationStates(sessionId)

    suspend fun listEnabledSkillIds(sessionId: String): List<String> =
        skills.listEnabledSkillIds(sessionId)

    suspend fun listDisabledSkillIds(sessionId: String): List<String> =
        skills.listDisabledSkillIds(sessionId)

    fun observeSkillConversationStatesBySession(
        sessionId: String,
    ): Flow<List<Skill_conversation_state>> =
        skills.observeSkillConversationStatesBySession(sessionId)

    suspend fun setSkillStateEnabled(id: String, enabled: Long) =
        skills.setSkillStateEnabled(id, enabled)

    suspend fun setSkillStateScopeOverride(id: String, scope: String?, agentType: String?) =
        skills.setSkillStateScopeOverride(id, scope, agentType)

    suspend fun listScopedSkillStates(): List<Skill_state> = skills.listScopedSkillStates()

    suspend fun deleteSkillStateById(id: String) = skills.deleteSkillStateById(id)

    suspend fun deleteSkillConversationState(skillId: String, sessionId: String) =
        skills.deleteSkillConversationState(skillId, sessionId)

    suspend fun deleteSkillConversationStatesBySkill(skillId: String) =
        skills.deleteSkillConversationStatesBySkill(skillId)

    suspend fun deleteSkillConversationStatesBySession(sessionId: String) =
        skills.deleteSkillConversationStatesBySession(sessionId)

    // ── Wake queue ─────────────────────────────────────────────────────

    suspend fun insertWakeItem(
        wakeId: String, sessionId: String, source: String, type: String, content: String,
        status: String, createdAtMs: Long,
    ) = wake.insertWakeItem(
        wakeId, sessionId, source, type, content, status, createdAtMs,
    )

    override suspend fun upsertWakeItem(
        wakeId: String, sessionId: String, source: String, type: String, content: String,
        status: String, createdAtMs: Long,
    ): Unit = wake.upsertWakeItem(
        wakeId, sessionId, source, type, content, status, createdAtMs,
    )

    override suspend fun listPendingWakeItems(): List<Wake_queue> = wake.listPendingWakeItems()

    override suspend fun listWakeBySessionAndStatus(
        sessionId: String, status: String,
    ): List<Wake_queue> = wake.listWakeBySessionAndStatus(sessionId, status)

    suspend fun markWakeItemConsumed(wakeId: String) = wake.markWakeItemConsumed(wakeId)

    override suspend fun markWakeItemsConsumedBatch(ids: List<String>, status: String): Unit =
        wake.markWakeItemsConsumedBatch(ids, status)

    fun observePendingWakeItems(): Flow<List<Wake_queue>> = wake.observePendingWakeItems()

    suspend fun deleteWakeItemsBySession(sessionId: String) =
        wake.deleteWakeItemsBySession(sessionId)

    // ── ZTH：sentinels / fuses / audits / telemetry ────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertSentinel(
        id: String, sessionId: String, linkageVersion: Long, chainId: String, chainIndex: Long,
        cardTemplateId: String, triggerSubClass: String, sPlanPayloadCiphertext: String,
        sUserTextCiphertext: String?, sCardPayloadCiphertext: String, userChoice: String,
        swipeVerified: Long, sModifiedPlanCiphertext: String?, expireAtMs: Long, rollbackFlag: Long, createdAtMs: Long,
    ) = zth.insertSentinel(
        id, sessionId, linkageVersion, chainId, chainIndex, cardTemplateId, triggerSubClass,
        sPlanPayloadCiphertext, sUserTextCiphertext, sCardPayloadCiphertext, userChoice,
        swipeVerified, sModifiedPlanCiphertext, expireAtMs, rollbackFlag, createdAtMs,
    )

    suspend fun listSentinels(sessionId: String): List<Zth_user_confirmed_sentinels> =
        zth.listSentinels(sessionId)

    fun observeSentinelsBySession(
        sessionId: String,
    ): Flow<List<Zth_user_confirmed_sentinels>> =
        zth.observeSentinelsBySession(sessionId)

    suspend fun listSentinelsByChain(chainId: String): List<Zth_user_confirmed_sentinels> =
        zth.listSentinelsByChain(chainId)

    suspend fun listSentinelsUnexpiredBySession(
        sessionId: String, nowMs: Long,
    ): List<Zth_user_confirmed_sentinels> =
        zth.listSentinelsUnexpiredBySession(sessionId, nowMs)

    suspend fun listAllSentinels(): List<Zth_user_confirmed_sentinels> =
        zth.listAllSentinels()

    suspend fun markAllSentinelsRollbackBySession(sessionId: String) =
        zth.markAllSentinelsRollbackBySession(sessionId)

    suspend fun deleteSentinelById(id: String) = zth.deleteSentinelById(id)

    suspend fun updateSentinelChoice(
        id: String, userChoice: String, swipeVerified: Long, sModifiedPlanCiphertext: String?,
    ) = zth.updateSentinelChoice(id, userChoice, swipeVerified, sModifiedPlanCiphertext)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertFuse(
        id: String, scope: String, scopeId: String, state: String, linkageVersion: Long,
        failureCount: Long, openSinceMs: Long, lastProbeAtMs: Long, killSwitch1Triggered: Long,
        killSwitch2SoftDisabled: Long, lastTripSubclass: String?, updatedAtMs: Long,
    ) = zth.upsertFuse(
        id, scope, scopeId, state, linkageVersion, failureCount, openSinceMs, lastProbeAtMs,
        killSwitch1Triggered, killSwitch2SoftDisabled, lastTripSubclass, updatedAtMs,
    )

    suspend fun getFuse(scope: String, scopeId: String): Zth_hallucination_fuses? =
        zth.getFuse(scope, scopeId)

    fun observeFuse(scope: String, scopeId: String): Flow<Zth_hallucination_fuses?> =
        zth.observeFuse(scope, scopeId)

    suspend fun getFuseVersion(id: String): Long? = zth.getFuseVersion(id)

    suspend fun casUpdateFuseState(
        id: String, expectedVersion: Long, newState: String, nowMs: Long,
    ): Long = zth.casUpdateFuseState(id, expectedVersion, newState, nowMs)

    suspend fun triggerFuseKillSwitch1(id: String, nowMs: Long) =
        zth.triggerFuseKillSwitch1(id, nowMs)

    suspend fun listAllFuses(): List<Zth_hallucination_fuses> = zth.listAllFuses()

    fun observeAllFuses(): Flow<List<Zth_hallucination_fuses>> = zth.observeAllFuses()

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun updateFuseState(
        scope: String, scopeId: String, state: String, failureCount: Long, lastProbeAtMs: Long,
        killSwitch1Triggered: Long, killSwitch2SoftDisabled: Long, lastTripSubclass: String?, updatedAtMs: Long,
    ) = zth.updateFuseState(
        scope = scope, scopeId = scopeId, state = state, failureCount = failureCount,
        lastProbeAtMs = lastProbeAtMs, killSwitch1Triggered = killSwitch1Triggered,
        killSwitch2SoftDisabled = killSwitch2SoftDisabled, lastTripSubclass = lastTripSubclass,
        updatedAtMs = updatedAtMs,
    )

    suspend fun insertRejectionAudit(
        id: String, sentinelId: String, rejectionType: String, sReasonCiphertext: String?,
        sRejectedPlanSnapshotCiphertext: String, createdAtMs: Long,
    ) = zth.insertRejectionAudit(
        id, sentinelId, rejectionType, sReasonCiphertext, sRejectedPlanSnapshotCiphertext, createdAtMs,
    )

    suspend fun listRejectionAudits(sentinelId: String): List<Zth_sentinel_plan_rejection_audits> =
        zth.listRejectionAudits(sentinelId)

    fun observeRejectionAuditsBySentinel(
        sentinelId: String,
    ): Flow<List<Zth_sentinel_plan_rejection_audits>> =
        zth.observeRejectionAuditsBySentinel(sentinelId)

    suspend fun listAllRejectionAudits(): List<Zth_sentinel_plan_rejection_audits> =
        zth.listAllRejectionAudits()

    suspend fun insertDeleteAudit(
        id: String, sessionId: String, affectedTableName: String, sAffectedKeysCiphertext: String,
        triggerSubClass: String, rollbackApplied: Long, createdAtMs: Long,
    ) = zth.insertDeleteAudit(
        id, sessionId, affectedTableName, sAffectedKeysCiphertext, triggerSubClass,
        rollbackApplied, createdAtMs,
    )

    suspend fun listDeleteAuditsPendingRollback(): List<Zth_hard_constraint_delete_audits> =
        zth.listDeleteAuditsPendingRollback()

    suspend fun listAllDeleteAudits(): List<Zth_hard_constraint_delete_audits> =
        zth.listAllDeleteAudits()

    suspend fun markDeleteAuditRolledBack(id: String) = zth.markDeleteAuditRolledBack(id)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTelemetryEvent(
        eventKind: String, eventSubKind: String, severityTier: Long, sessionSha256Prefix: String?,
        latencyMs: Long?, flagA: Long?, flagB: Long?, metricA: Long?, metricB: Long?, createdAtMs: Long,
    ) = zth.insertTelemetryEvent(
        eventKind, eventSubKind, severityTier, sessionSha256Prefix, latencyMs,
        flagA, flagB, metricA, metricB, createdAtMs,
    )

    suspend fun listTelemetryByKind(eventKind: String, limit: Long): List<Zth_telemetry_events> =
        zth.listTelemetryByKind(eventKind, limit)

    fun observeTelemetryByKind(
        eventKind: String, limit: Long,
    ): Flow<List<Zth_telemetry_events>> =
        zth.observeTelemetryByKind(eventKind, limit)

    suspend fun countTelemetryByKind(eventKind: String): Long =
        zth.countTelemetryByKind(eventKind)

    suspend fun listAllTelemetry(): List<Zth_telemetry_events> = zth.listAllTelemetry()

    fun observeAllTelemetry(): Flow<List<Zth_telemetry_events>> = zth.observeAllTelemetry()

    suspend fun listTelemetryRange(fromMs: Long, toMs: Long): List<Zth_telemetry_events> =
        zth.listTelemetryRange(fromMs, toMs)

    suspend fun countAllTelemetry(): Long = zth.countAllTelemetry()

    suspend fun deleteTelemetryOlderThan(beforeMs: Long) =
        zth.deleteTelemetryOlderThan(beforeMs)

    // ── P2-4 事务封装（对齐 Room withTransaction 语义）────────────────────

    /**
     * 在单事务内执行 [block]（同线程顺序执行，SQLDelight 2.2.1 的 ThreadLocal 事务）。
     * 供 SessionUseCase.deleteSession 等「多表原子写」使用；事务块内只能用 [AgentTx] 暴露的
     * 阻塞方法（不得调用本 Repository 的 suspend 方法，否则 withContext 会跳线程破坏事务绑定）。
     *
     * ⚠️ **必须是 suspend + 切到 [Dispatchers.IO]**（H6）：
     * 过去它是**阻塞 `fun`**，而 `deleteSession` / `deleteSessionsByWorkspace` 是 `suspend fun`
     * 且内部没有 `withContext`——12 张表级联 DELETE 的事务就跑在调用方调度器上，
     * ViewModel 场景即 **Main 线程**，SQLCipher 加密库上必 ANR。
     * 现在整段（含事务开闭）都在 IO 线程内完成：SQLDelight 的事务是 ThreadLocal 绑定，
     * 只要 block 内不再切线程，绑定始终成立。
     */
    suspend fun runInTx(block: (AgentTx) -> Unit) {
        withContext(Dispatchers.IO) {
            db.transaction {
                block(AgentTx(q))
            }
        }
    }
}
