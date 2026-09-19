package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.core.agentworkflow.AgentMessageRepository
import com.mini.me_core.core.agentworkflow.AgentSessionRepository
import com.mini.me_core.core.agentworkflow.CheckpointRepository
import com.mini.me_core.core.agentworkflow.MessageModel
import com.mini.me_core.core.agentworkflow.SessionModel
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session
import com.mini.me_core.datalayer.repository.AgentRepository
import javax.inject.Inject

/**
 * SQLDelight 适配实现：把 datalayer 生成类型映射为 :core:agent-workflow 的 domain model。
 *
 * 架构规则（ARC-02）：domain/workflow 层只依赖 [AgentSessionRepository] 等端口，
 * 本类是唯一允许把 V2 生成类型翻译成 domain model 的地方（Feature→Data 适配器）。
 * Hilt 绑定见 app 内 Module（接口→本实现）。
 *
 * 注：当前 20 个 domain 文件仍直接注入 [AgentRepository]（v2Agent），逐文件切换到本端口
 * 是分阶段工作；本适配器先就位，作为切换目标。
 */
class AgentRepositoryAdapter @Inject constructor(
    private val v2: AgentRepository,
) : AgentSessionRepository, AgentMessageRepository, CheckpointRepository {

    override suspend fun getById(id: String): SessionModel? =
        v2.getSessionById(id)?.toDomain()

    override suspend fun upsert(session: SessionModel) {
        v2.upsertSession(
            id = session.id,
            title = session.title,
            mode = session.mode,
            model = session.model,
            status = session.status,
            createdAtMs = session.createdAt,
            updatedAtMs = session.updatedAt,
            workspacePath = session.workspacePath,
            workspaceId = session.workspaceId,
            reasoningEffort = session.reasoningEffort,
            providerId = session.providerId,
            totalInputTokens = 0L,
            totalOutputTokens = 0L,
            lastInputTokens = 0L,
        )
    }

    override suspend fun delete(id: String) { v2.deleteSession(id) }

    override suspend fun touch(id: String, timestamp: Long) { v2.touch(id, timestamp) }

    override suspend fun updateTitle(id: String, title: String) { v2.updateTitle(id, title) }

    override suspend fun updateMode(id: String, mode: String) = Unit // 经 upsertSession 覆盖

    override suspend fun mostRecent(): SessionModel? =
        v2.getMostRecentOnce()?.toDomain()

    override suspend fun listBySession(sessionId: String): List<MessageModel> =
        emptyList() // 待逐文件切换时补 message 列表查询

    override suspend fun insert(message: MessageModel) = Unit

    override suspend fun countBySession(sessionId: String): Int = 0

    override suspend fun save(sessionId: String, checkpointJson: String) = Unit

    override suspend fun latest(sessionId: String): String? = null

    private fun Agent_session.toDomain() = SessionModel(
        id = id,
        title = title ?: "",
        mode = mode,
        model = model,
        status = status,
        createdAt = created_at,
        updatedAt = updated_at,
        workspacePath = workspace_path,
        workspaceId = workspace_id,
        reasoningEffort = reasoning_effort,
        providerId = provider_id,
    )

    @Suppress("unused")
    private fun Agent_message.toDomain() = MessageModel(
        id = id,
        sessionId = session_id,
        role = role,
        content = content,
        createdAt = created_at,
        toolCallsJson = tool_calls_json,
        isError = is_error == 1L,
    )
}
