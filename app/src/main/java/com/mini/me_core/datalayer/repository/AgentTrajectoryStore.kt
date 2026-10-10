package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_playbook_runs
import com.mini.mecore.datalayer.sqldelight.agent.Agent_trajectories
import com.mini.mecore.datalayer.sqldelight.agent.SelectTrajectoryAggregateBySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 执行轨迹与 Playbook 运行存储：agent_trajectories、agent_playbook_runs 两表。
 */
internal class AgentTrajectoryStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── Trajectories ───────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTrajectory(
        trajectoryId: String, sessionId: String, taskId: String, turnIndex: Long, kind: String,
        toolName: String, argsHash: String, resultSummary: String, isError: Long, durationMs: Long,
        tokensIn: Long, tokensOut: Long, ts: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertTrajectory(
            trajectoryId, sessionId, taskId, turnIndex, kind, toolName, argsHash,
            resultSummary, isError, durationMs, tokensIn, tokensOut, ts,
        )
    }

    suspend fun listTrajectories(sessionId: String): List<Agent_trajectories> =
        withContext(Dispatchers.IO) { q.selectTrajectoriesBySession(sessionId).executeAsList() }

    fun observeTrajectoriesBySession(sessionId: String): Flow<List<Agent_trajectories>> =
        q.selectTrajectoriesBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun listTrajectoriesByTask(taskId: String): List<Agent_trajectories> =
        withContext(Dispatchers.IO) { q.selectTrajectoriesByTask(taskId).executeAsList() }

    suspend fun getLatestTaskId(sessionId: String): String? =
        withContext(Dispatchers.IO) { q.selectLatestTaskIdBySession(sessionId).executeAsOneOrNull() }

    suspend fun getMaxTurnIndex(sessionId: String, taskId: String): Int? =
        withContext(Dispatchers.IO) { q.selectMaxTurnIndexBySessionTask(sessionId, taskId).executeAsOneOrNull()?.MAX?.toInt() }

    suspend fun getTrajectoryAggregate(sessionId: String): SelectTrajectoryAggregateBySession =
        withContext(Dispatchers.IO) { q.selectTrajectoryAggregateBySession(sessionId).executeAsOne() }

    suspend fun deleteTrajectories(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteTrajectoriesBySession(sessionId) }

    // ── Playbook runs ──────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertPlaybookRun(
        playbookRunId: String, sessionId: String, playbookName: String, currentStageIndex: Long,
        stageStatuses: String, status: String, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertPlaybookRun(
            playbookRunId, sessionId, playbookName, currentStageIndex, stageStatuses,
            status, createdAtMs, updatedAtMs,
        )
    }

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertPlaybookRun(
        playbookRunId: String, sessionId: String, playbookName: String, currentStageIndex: Long,
        stageStatuses: String, status: String, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertPlaybookRun(
            playbookRunId, sessionId, playbookName, currentStageIndex, stageStatuses,
            status, createdAtMs, updatedAtMs,
        )
    }

    suspend fun getPlaybookRunById(runId: String): Agent_playbook_runs? =
        withContext(Dispatchers.IO) { q.selectPlaybookRunById(runId).executeAsOneOrNull() }

    suspend fun listPlaybookRuns(sessionId: String): List<Agent_playbook_runs> =
        withContext(Dispatchers.IO) { q.selectPlaybookRunsBySession(sessionId).executeAsList() }

    fun observePlaybookRunsBySession(sessionId: String): Flow<List<Agent_playbook_runs>> =
        q.selectPlaybookRunsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun getLatestPlaybookBySession(sessionId: String): Agent_playbook_runs? =
        withContext(Dispatchers.IO) { q.selectLatestPlaybookBySession(sessionId).executeAsOneOrNull() }

    suspend fun getLatestPlaybookBySessionAndStatus(sessionId: String, status: String): Agent_playbook_runs? =
        withContext(Dispatchers.IO) { q.selectLatestPlaybookBySessionAndStatus(sessionId, status).executeAsOneOrNull() }

    suspend fun listRunningPlaybookRuns(): List<Agent_playbook_runs> =
        withContext(Dispatchers.IO) { q.selectRunningPlaybookRuns().executeAsList() }

    suspend fun updatePlaybookRun(
        playbookRunId: String, currentStageIndex: Long, stageStatuses: String, status: String, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.updatePlaybookRun(currentStageIndex, stageStatuses, status, updatedAtMs, playbookRunId)
    }

    suspend fun deletePlaybookRunsBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deletePlaybookRunsBySession(sessionId) }
}
