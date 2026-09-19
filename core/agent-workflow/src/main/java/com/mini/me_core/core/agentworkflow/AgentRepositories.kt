package com.mini.me_core.core.agentworkflow

/**
 * Agent 域 Repository 端口（纯 Kotlin，不依赖 SQLDelight / Android）。
 *
 * 架构规则（ARC-02/03）：domain / workflow 层只面向这些接口，不直接 import
 * datalayer.sqldelight 生成类型；SQLDelight 适配实现放 feature/agent/data/repository/，
 * 在 :app 经 Hilt @Binds 绑定接口→实现。
 *
 * 方法签名只用本文件定义的 domain model（SessionModel/MessageModel），
 * 不用 SQLDelight 生成的 Agent_session/Agent_message。
 */

data class SessionModel(
    val id: String,
    val title: String,
    val mode: String,
    val model: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val workspacePath: String,
    val workspaceId: String,
    val reasoningEffort: String,
    val providerId: String?,
)

data class MessageModel(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val toolCallsJson: String?,
    val isError: Boolean,
)

interface AgentSessionRepository {
    suspend fun getById(id: String): SessionModel?
    suspend fun upsert(session: SessionModel)
    suspend fun delete(id: String)
    suspend fun touch(id: String, timestamp: Long)
    suspend fun updateTitle(id: String, title: String)
    suspend fun updateMode(id: String, mode: String)
    suspend fun mostRecent(): SessionModel?
}

interface AgentMessageRepository {
    suspend fun listBySession(sessionId: String): List<MessageModel>
    suspend fun insert(message: MessageModel)
    suspend fun countBySession(sessionId: String): Int
}

interface CheckpointRepository {
    suspend fun save(sessionId: String, checkpointJson: String)
    suspend fun latest(sessionId: String): String?
}
