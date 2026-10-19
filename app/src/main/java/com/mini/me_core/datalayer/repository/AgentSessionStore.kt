package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session
import com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 会话存储（ChatSessionDao 等价）：会话列表/创建/删除/标题/排序/token 统计。
 * 由 [AgentRepository] 装配并委托，行为与原门面内联实现保持一致。
 */
@Suppress("TooManyFunctions") // 单表 CRUD 内聚，行为保持提取
internal class AgentSessionStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertSession(
        id: String, title: String?, mode: String, model: String?, status: String,
        createdAtMs: Long, updatedAtMs: Long, workspacePath: String, reasoningEffort: String,
        providerId: String?, totalInputTokens: Long, totalOutputTokens: Long, lastInputTokens: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertSession(
            id, title, mode, model, status, createdAtMs, updatedAtMs, workspacePath,
            reasoningEffort, providerId, totalInputTokens, totalOutputTokens, lastInputTokens,
        )
    }

    suspend fun createSession(
        id: String, title: String?, mode: String, model: String?, now: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        q.insertSession(id, title, mode, model, "active", now, now, "", "MEDIUM", null, 0L, 0L, 0L)
    }

    suspend fun listSessions(): List<Agent_session> =
        withContext(Dispatchers.IO) { q.selectAllSessions().executeAsList() }

    suspend fun getSession(id: String): Agent_session? =
        withContext(Dispatchers.IO) { q.selectSessionById(id).executeAsOneOrNull() }

    suspend fun renameSession(id: String, title: String, now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) { q.updateSessionTitle(title, now, id) }

    // 删除会话一律走 AgentRepository.deleteSession（单事务级联，含外键层次）。
    // 这里不再暴露单表 deleteSession：agent_message / agent_checkpoint 外键指向 agent_session，
    // PRAGMA foreign_keys=ON 下直接删父行必抛 SQLiteConstraintException（删除崩溃根因）。
    // 全量清空（备份覆盖恢复）走 AgentRepository.deleteAllSessions（逐会话级联）。

    suspend fun getAllSessionsByWorkspaceOnce(workspacePath: String): List<Agent_session> =
        withContext(Dispatchers.IO) { q.selectSessionsByWorkspace(workspacePath).executeAsList() }

    fun observeAllSessionsByWorkspace(workspacePath: String): Flow<List<Agent_session>> =
        q.selectSessionsByWorkspace(workspacePath).asFlow().mapToList(Dispatchers.IO)

    fun observeAllSessionsWithCount(): Flow<List<SelectAllSessionsWithCount>> =
        q.selectAllSessionsWithCount().asFlow().mapToList(Dispatchers.IO)

    suspend fun getAllOnce(): List<Agent_session> =
        withContext(Dispatchers.IO) { q.selectAllSessions().executeAsList() }

    suspend fun getMostRecentOnce(): Agent_session? =
        withContext(Dispatchers.IO) { q.selectMostRecentSession().executeAsOneOrNull() }

    suspend fun getUnboundSessionsOnce(): List<Agent_session> =
        withContext(Dispatchers.IO) { q.selectUnboundSessions().executeAsList() }

    suspend fun countSessions(): Long =
        withContext(Dispatchers.IO) { q.countSessions().executeAsOne() }

    suspend fun getSessionPageAfter(lastUpdatedAtMs: Long, lastId: String, limit: Long): List<Agent_session> =
        withContext(Dispatchers.IO) {
            q.selectSessionPageAfter(lastUpdatedAtMs, lastUpdatedAtMs, lastId, limit).executeAsList()
        }

    /**
     * 批量 upsert 会话（备份恢复用）。
     * 原实现 forEach 逐条 upsert 无事务包裹，每条 INSERT 都是独立 autocommit，
     * 大数据量下产生大量 fsync 导致极慢。改为单事务包裹，要么全部成功要么全部回滚。
     */
    suspend fun upsertAllSessions(sessions: List<Agent_session>) = withContext(Dispatchers.IO) {
        db.transaction {
            sessions.forEach { s ->
                q.upsertSession(
                    s.id, s.title, s.mode, s.model, s.status, s.created_at, s.updated_at,
                    s.workspace_path, s.reasoning_effort, s.provider_id, s.total_input_tokens,
                    s.total_output_tokens, s.last_input_tokens,
                )
            }
        }
    }

    suspend fun getSessionById(id: String): Agent_session? =
        withContext(Dispatchers.IO) { q.selectSessionById(id).executeAsOneOrNull() }

    suspend fun updateTitle(id: String, title: String) =
        withContext(Dispatchers.IO) { q.updateSessionTitle(title, System.currentTimeMillis(), id) }

    suspend fun updateWorkspacePath(oldPath: String, newPath: String) =
        withContext(Dispatchers.IO) { q.updateSessionWorkspacePath(newPath, oldPath) }

    suspend fun setWorkspacePath(id: String, path: String) =
        withContext(Dispatchers.IO) { q.setSessionWorkspacePath(path, id) }

    suspend fun touch(id: String, updatedAtMs: Long) =
        withContext(Dispatchers.IO) { q.touchSession(updatedAtMs, id) }

    suspend fun updateProviderModel(id: String, providerId: String?, model: String?) =
        withContext(Dispatchers.IO) { q.updateSessionProviderModel(providerId, model, id) }

    suspend fun updateReasoningEffort(id: String, effort: String) =
        withContext(Dispatchers.IO) { q.updateSessionReasoningEffort(effort, id) }

    suspend fun addTokenUsage(id: String, inputTokens: Long, outputTokens: Long) =
        withContext(Dispatchers.IO) { q.addSessionTokenUsage(inputTokens, outputTokens, id) }

    suspend fun updateLastInputTokens(id: String, lastInputTokens: Long) =
        withContext(Dispatchers.IO) { q.updateSessionLastInputTokens(lastInputTokens, id) }

    fun observeAllSessions(): Flow<List<Agent_session>> =
        q.selectAllSessions().asFlow().mapToList(Dispatchers.IO)

    fun observeSessionById(id: String): Flow<Agent_session?> =
        q.selectSessionById(id).asFlow().mapToOneOrNull(Dispatchers.IO)
}
