package com.mini.me_core.di

import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.backup.domain.AgentMessageDto
import com.mini.me_core.feature.backup.domain.BackupDataSource
import com.mini.me_core.feature.backup.domain.ChatSessionDto
import com.mini.me_core.feature.backup.domain.TodoItemDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupDataSource] 的 :app 实现：把 v2Agent 分页导出/导入与 V2↔DTO 映射全部收在这里，
 * 使 feature/backup 不再 import feature.agent.data.entity 或 datalayer 生成类型。
 */
@Singleton
class AgentBackupDataSourceAdapter @Inject constructor(
    private val v2: AgentRepository,
) : BackupDataSource {

    override suspend fun getSessionDto(sessionId: String): ChatSessionDto? =
        v2.getSessionById(sessionId)?.toDto()

    override suspend fun pageMessagesForExport(sessionId: String, lastTs: Long, lastId: String, limit: Long): List<AgentMessageDto> =
        v2.getPageBySessionAfter(sessionId, lastTs, lastId, limit).map { it.toDto() }

    override suspend fun pageTodosForExport(sessionId: String, lastCreatedAtMs: Long, lastId: String, limit: Long): List<TodoItemDto> =
        v2.getTodoPageBySessionAfter(sessionId, lastCreatedAtMs, lastId, limit).map { it.toDto() }

    override suspend fun pageAllSessions(lastUpdatedAtMs: Long, lastId: String, limit: Long): List<ChatSessionDto> =
        v2.getSessionPageAfter(lastUpdatedAtMs, lastId, limit).map { it.toDto() }

    override suspend fun pageAllMessages(lastTs: Long, lastId: String, limit: Long): List<AgentMessageDto> =
        v2.getMessagePageAfter(lastTs, lastId, limit).map { it.toDto() }

    override suspend fun pageAllTodos(lastCreatedAtMs: Long, lastId: String, limit: Long): List<TodoItemDto> =
        v2.getTodoPageAfter(lastCreatedAtMs, lastId, limit).map { it.toDto() }

    override suspend fun importSessions(items: List<ChatSessionDto>) {
        v2.upsertAllSessions(items.map { it.toV2() })
    }

    override suspend fun importMessages(items: List<AgentMessageDto>) {
        v2.insertAllMessages(items.map { it.toV2() })
    }

    override suspend fun importTodos(items: List<TodoItemDto>) {
        v2.upsertAllTodos(items.map { it.toV2() })
    }

    // ── V2 ↔ DTO 直接映射（实体层不出适配器）────────────────────────

    private fun com.mini.mecore.datalayer.sqldelight.agent.Agent_session.toDto() = ChatSessionDto(
        id = id, title = title ?: "",
        createdAt = created_at, updatedAt = updated_at,
        workspacePath = workspace_path, workspaceId = workspace_id,
        mode = mode, reasoningEffort = reasoning_effort,
        providerId = provider_id, model = model,
    )

    private fun com.mini.mecore.datalayer.sqldelight.agent.Agent_message.toDto() = AgentMessageDto(
        id, session_id, task_id, role, content, created_at, tool_calls_json, tool_call_id,
        tool_name, tool_args, is_error == 1L, reasoning, signature, attachments_json,
        is_compacted == 1L, is_context_summary == 1L, is_compaction_marker == 1L,
        chunk_group_id, chunk_index.toInt(),
    )

    private fun com.mini.mecore.datalayer.sqldelight.agent.Todo_items.toDto() = TodoItemDto(
        id = id, sessionId = session_id, subject = subject, description = description,
        status = status, priority = priority.toInt(), order = sort_order.toInt(),
        createdAt = created_at_ms, updatedAt = updated_at_ms,
    )

    private fun ChatSessionDto.toV2() = com.mini.mecore.datalayer.sqldelight.agent.Agent_session(
        id = id, title = title, mode = mode, model = model, status = "active",
        created_at = createdAt, updated_at = updatedAt,
        workspace_path = workspacePath, workspace_id = workspaceId, reasoning_effort = reasoningEffort,
        provider_id = providerId,
        total_input_tokens = 0L, total_output_tokens = 0L, last_input_tokens = 0L,
    )

    private fun AgentMessageDto.toV2() = com.mini.mecore.datalayer.sqldelight.agent.Agent_message(
        id = id, session_id = sessionId, role = role, seq = timestamp, created_at = timestamp,
        task_id = taskId, content = content, tool_calls_json = toolCallsJson,
        tool_call_id = toolCallId, tool_name = toolName, tool_args = toolArgs,
        is_error = if (isError) 1L else 0L, reasoning = reasoning, signature = signature,
        attachments_json = attachmentsJson, is_compacted = if (isCompacted) 1L else 0L,
        is_context_summary = if (isContextSummary) 1L else 0L,
        is_compaction_marker = if (isCompactionMarker) 1L else 0L,
        input_tokens = 0L, output_tokens = 0L,
        chunk_group_id = chunkGroupId, chunk_index = chunkIndex.toLong(),
    )

    private fun TodoItemDto.toV2() = com.mini.mecore.datalayer.sqldelight.agent.Todo_items(
        id = id, session_id = sessionId, subject = subject, description = description,
        status = status, priority = priority.toLong(), sort_order = order.toLong(),
        created_at_ms = createdAt, updated_at_ms = updatedAt,
    )
}
