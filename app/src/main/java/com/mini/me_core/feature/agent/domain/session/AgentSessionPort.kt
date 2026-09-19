package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.feature.agent.data.local.dao.ChatSessionWithCount
import com.mini.me_core.feature.agent.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Agent 会话数据端口（domain 定义，data 层实现）。
 * domain 只面向本接口，不直接 import datalayer.sqldelight 生成类型（ARC-02）。
 * 实现见 feature/agent/data/，由 Hilt 绑定接口→实现。
 */
interface AgentSessionPort {
    suspend fun getSessionById(id: String): ChatSessionEntity?
    suspend fun upsert(entity: ChatSessionEntity)
    suspend fun insertModeSwitch(
        sessionId: String,
        fromMode: String,
        toMode: String,
        reason: String,
        atMs: Long,
    )
    // SessionUseCase 扩展面
    suspend fun markPendingToolsInterrupted(toolRole: String, pendingPrefix: String, interruptedContent: String): Long
    suspend fun deleteSessionCascade(id: String)
    suspend fun listByWorkspace(workspaceId: String): List<ChatSessionEntity>
    suspend fun listUnbound(): List<ChatSessionEntity>
    suspend fun getMostRecent(): ChatSessionEntity?
    suspend fun getWorkspaceId(sessionId: String): String
    suspend fun setWorkspaceBinding(sessionId: String, workspaceId: String, workspacePath: String)
    suspend fun updateTitle(sessionId: String, title: String)
    suspend fun touch(sessionId: String, timestamp: Long)
    suspend fun updateProviderModel(sessionId: String, providerId: String?, model: String?)
    suspend fun updateReasoningEffort(sessionId: String, effort: String)
    fun observeAll(): Flow<List<ChatSessionEntity>>
    fun observeAllWithCount(): Flow<List<ChatSessionWithCount>>
    suspend fun countByWorkspace(workspaceId: String): Int
    suspend fun addTokenUsage(sessionId: String, inputTokens: Long, outputTokens: Long)
    suspend fun updateLastInputTokens(sessionId: String, inputTokens: Long)
}
