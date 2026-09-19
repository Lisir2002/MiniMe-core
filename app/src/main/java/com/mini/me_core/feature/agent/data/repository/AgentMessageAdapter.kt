package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_message
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.AgentMessageEntity
import com.mini.me_core.feature.agent.domain.session.AgentMessagePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * [AgentMessagePort] 的 SQLDelight 实现：负责 V2AgentMessage <-> AgentMessageEntity 双向映射。
 */
class AgentMessageAdapter @Inject constructor(
    private val v2: AgentRepository,
) : AgentMessagePort {

    override suspend fun insert(entity: AgentMessageEntity) { v2.insertMessage(entity.toV2()) }

    override suspend fun insertAll(entities: List<AgentMessageEntity>) {
        v2.insertAllMessages(entities.map { it.toV2() })
    }

    override suspend fun updateContent(messageId: String, newContent: String) {
        v2.updateMessageContent(messageId, newContent)
    }

    override suspend fun markCompactedBeforeTimestamp(sessionId: String, cutoffTimestamp: Long) {
        v2.markMessagesCompactedBeforeTimestamp(sessionId, cutoffTimestamp)
    }

    override suspend fun listBySession(sessionId: String): List<AgentMessageEntity> =
        v2.getMessagesBySessionOnce(sessionId).map { it.toEntity() }

    override fun observeBySessionPaged(sessionId: String, limit: Long): Flow<List<AgentMessageEntity>> =
        v2.observeMessagesBySessionPaged(sessionId, limit).map { list -> list.map { it.toEntity() } }

    override suspend fun getMessageById(messageId: String): AgentMessageEntity? =
        v2.getMessageById(messageId)?.toEntity()

    override suspend fun markCompactedInclusiveFromTimestamp(sessionId: String, timestamp: Long) {
        v2.markMessagesCompactedInclusiveFromTimestamp(sessionId, timestamp)
    }

    private fun AgentMessageEntity.toV2() = Agent_message(
        id = id,
        session_id = sessionId,
        role = role,
        seq = timestamp,
        created_at = timestamp,
        task_id = taskId,
        content = content,
        tool_calls_json = toolCallsJson,
        tool_call_id = toolCallId,
        tool_name = toolName,
        tool_args = toolArgs,
        is_error = if (isError) 1L else 0L,
        reasoning = reasoning,
        signature = signature,
        attachments_json = attachmentsJson,
        is_compacted = if (isCompacted) 1L else 0L,
        is_context_summary = if (isContextSummary) 1L else 0L,
        is_compaction_marker = if (isCompactionMarker) 1L else 0L,
        input_tokens = inputTokens.toLong(),
        output_tokens = outputTokens.toLong(),
        chunk_group_id = chunkGroupId,
        chunk_index = chunkIndex.toLong(),
    )

    private fun Agent_message.toEntity() = AgentMessageEntity(
        id = id,
        sessionId = session_id,
        taskId = task_id,
        role = role,
        content = content,
        timestamp = seq,
        toolCallsJson = tool_calls_json,
        toolCallId = tool_call_id,
        toolName = tool_name,
        toolArgs = tool_args,
        isError = is_error == 1L,
        reasoning = reasoning,
        signature = signature,
        attachmentsJson = attachments_json,
        isCompacted = is_compacted == 1L,
        isContextSummary = is_context_summary == 1L,
        isCompactionMarker = is_compaction_marker == 1L,
        inputTokens = input_tokens.toInt(),
        outputTokens = output_tokens.toInt(),
        chunkGroupId = chunk_group_id,
        chunkIndex = chunk_index.toInt(),
    )
}
