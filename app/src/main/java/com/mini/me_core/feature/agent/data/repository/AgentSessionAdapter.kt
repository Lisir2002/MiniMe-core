package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_session
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.ChatSessionEntity
import com.mini.me_core.feature.agent.domain.session.AgentSessionPort
import com.mini.me_core.feature.agent.data.local.dao.ChatSessionWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [AgentSessionPort] 的 SQLDelight 实现：domain 不感知 datalayer 生成类型，
 * 本类负责 V2AgentSession <-> ChatSessionEntity 的双向映射。
 */
class AgentSessionAdapter @Inject constructor(
    private val v2: AgentRepository,
) : AgentSessionPort {

    override suspend fun getSessionById(id: String): ChatSessionEntity? =
        v2.getSessionById(id)?.toEntity()

    override suspend fun upsert(entity: ChatSessionEntity) {
        v2.upsertSession(
            id = entity.id,
            title = entity.title,
            mode = entity.mode,
            model = entity.model,
            status = "active",
            createdAtMs = entity.createdAtMs,
            updatedAtMs = entity.updatedAtMs,
            workspacePath = entity.workspacePath,
            workspaceId = entity.workspaceId,
            reasoningEffort = entity.reasoningEffort,
            providerId = entity.providerId,
            totalInputTokens = entity.totalInputTokens.toLong(),
            totalOutputTokens = entity.totalOutputTokens.toLong(),
            lastInputTokens = entity.lastInputTokens.toLong(),
        )
    }

    override suspend fun insertModeSwitch(
        sessionId: String,
        fromMode: String,
        toMode: String,
        reason: String,
        atMs: Long,
    ) { v2.insertModeSwitch(sessionId, fromMode, toMode, reason, atMs) }

    override suspend fun markPendingToolsInterrupted(toolRole: String, pendingPrefix: String, interruptedContent: String): Long =
        v2.markPendingToolsInterrupted(toolRole, pendingPrefix, interruptedContent)

    override suspend fun deleteSessionCascade(id: String) {
        v2.runInTx { tx ->
            tx.deleteBySession(id)
            tx.deleteTodosBySession(id)
            tx.deleteFileEditHunksBySession(id)
            tx.deleteModeSwitchesBySession(id)
            tx.deleteSkillConversationStatesBySession(id)
            tx.deleteWakeItemsBySession(id)
            tx.deleteGoalsBySession(id)
            tx.deletePlansBySession(id)
            tx.deleteJobsBySession(id)
            tx.deleteSchedulesBySession(id)
            tx.deleteTrajectories(id)
            tx.deleteSession(id)
        }
    }

    override suspend fun listByWorkspace(workspaceId: String): List<ChatSessionEntity> =
        v2.getAllSessionsByWorkspaceIdOnce(workspaceId).map { it.toEntity() }

    override suspend fun listUnbound(): List<ChatSessionEntity> =
        v2.getUnboundSessionsOnce().map { it.toEntity() }

    override suspend fun getMostRecent(): ChatSessionEntity? =
        v2.getMostRecentOnce()?.toEntity()

    override suspend fun getWorkspaceId(sessionId: String): String =
        v2.getSessionById(sessionId)?.workspace_id ?: ""

    override suspend fun setWorkspaceBinding(sessionId: String, workspaceId: String, workspacePath: String) {
        v2.setSessionWorkspaceBinding(sessionId, workspaceId, workspacePath)
    }

    override suspend fun updateTitle(sessionId: String, title: String) { v2.updateTitle(sessionId, title) }

    override suspend fun touch(sessionId: String, timestamp: Long) { v2.touch(sessionId, timestamp) }

    override suspend fun updateProviderModel(sessionId: String, providerId: String?, model: String?) {
        v2.updateProviderModel(sessionId, providerId, model)
    }

    override suspend fun updateReasoningEffort(sessionId: String, effort: String) {
        v2.updateReasoningEffort(sessionId, effort)
    }

override fun observeAll(): Flow<List<ChatSessionEntity>> =
        v2.observeAllSessions().map { list -> list.map { it.toEntity() } }

    override fun observeAllWithCount(): Flow<List<ChatSessionWithCount>> =
        v2.observeAllSessionsWithCount().map { list -> list.map { it.toCountEntity() } }

    override suspend fun countByWorkspace(workspaceId: String): Int =
        v2.countSessionsByWorkspaceId(workspaceId).toInt()

    override suspend fun addTokenUsage(sessionId: String, inputTokens: Long, outputTokens: Long) {
        v2.addTokenUsage(sessionId, inputTokens, outputTokens)
    }

    override suspend fun updateLastInputTokens(sessionId: String, inputTokens: Long) {
        v2.updateLastInputTokens(sessionId, inputTokens)
    }

    private fun com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount.toCountEntity() =
        ChatSessionWithCount(id, title ?: "", created_at, updated_at, workspace_path, workspace_id, mode, message_count.toInt())

    private fun Agent_session.toEntity() = ChatSessionEntity(
        id = id,
        title = title ?: "",
        createdAtMs = created_at,
        updatedAtMs = updated_at,
        workspacePath = workspace_path,
        workspaceId = workspace_id,
        mode = mode,
        reasoningEffort = reasoning_effort,
        providerId = provider_id,
        model = model,
        totalInputTokens = total_input_tokens.toInt(),
        totalOutputTokens = total_output_tokens.toInt(),
        lastInputTokens = last_input_tokens.toInt(),
    )
}
