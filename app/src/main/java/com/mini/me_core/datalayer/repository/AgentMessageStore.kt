package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.me_core.datalayer.util.escapeSqlLike
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 消息存储（AgentMessageDao 等价）：消息追加/批量插入/分页查询/状态更新。
 * 压缩标记与时间戳删除由 [AgentCompactionStore] 负责；本类只保留常规消息 CRUD。
 */
internal class AgentMessageStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    suspend fun insertMessage(e: Agent_message) = withContext(Dispatchers.IO) {
        q.insertMessage(
            e.id, e.session_id, e.role, e.seq, e.created_at, e.task_id, e.content,
            e.tool_calls_json, e.tool_call_id, e.tool_name, e.tool_args, e.is_error,
            e.reasoning, e.signature, e.attachments_json, e.is_compacted, e.is_context_summary,
            e.is_compaction_marker, e.input_tokens, e.output_tokens, e.chunk_group_id, e.chunk_index,
            e.provider_id,
        )
    }

    /**
     * 批量插入消息。
     * 原实现 forEach 调用 suspend insertMessage，每条消息都 withContext 跳线程，
     * 既慢又无法保证原子性。改为单事务内直接调用 queries.insertMessage，同线程顺序执行。
     */
    suspend fun insertAllMessages(messages: List<Agent_message>) = withContext(Dispatchers.IO) {
        db.transaction {
            messages.forEach { e ->
                q.insertMessage(
                    e.id, e.session_id, e.role, e.seq, e.created_at, e.task_id, e.content,
                    e.tool_calls_json, e.tool_call_id, e.tool_name, e.tool_args, e.is_error,
                    e.reasoning, e.signature, e.attachments_json, e.is_compacted,
                    e.is_context_summary, e.is_compaction_marker, e.input_tokens, e.output_tokens,
                    e.chunk_group_id, e.chunk_index, e.provider_id,
                )
            }
        }
    }

    fun observeMessagesBySession(sessionId: String): Flow<List<Agent_message>> =
        q.selectMessagesBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    fun observeMessagesBySessionPaged(sessionId: String, limit: Long): Flow<List<Agent_message>> =
        q.selectMessagesBySessionPaged(sessionId, limit).asFlow().mapToList(Dispatchers.IO)

    suspend fun getMessagesBySessionOnce(sessionId: String): List<Agent_message> =
        withContext(Dispatchers.IO) { q.selectMessagesBySession(sessionId).executeAsList() }

    suspend fun deleteBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteMessagesBySession(sessionId) }

    suspend fun deleteAllMessages() = withContext(Dispatchers.IO) { q.deleteAllMessages() }

    suspend fun getMessageById(id: String): Agent_message? =
        withContext(Dispatchers.IO) { q.selectMessageById(id).executeAsOneOrNull() }

    suspend fun updateMessageContent(id: String, content: String) =
        withContext(Dispatchers.IO) { q.updateMessageContent(content, id) }

    suspend fun updateMessageContentAndReasoning(id: String, content: String, reasoning: String?) =
        withContext(Dispatchers.IO) { q.updateMessageContentAndReasoning(content, reasoning, id) }

    /**
     * 分块安全追加：把 [suffix] 拼到消息末尾——未分块消息命中主行，
     * 分块消息命中 chunk_index 最大的末块（SQL 子查询内部解析，见 agent.sq 注释）。
     * 冷启动恢复给「停在中途的流式助手消息」补记中断提示用。
     */
    suspend fun appendToLastChunkOfMessage(id: String, suffix: String) =
        withContext(Dispatchers.IO) { q.appendToLastChunkOfMessage(suffix, id, id) }

    suspend fun deleteMessageById(id: String) =
        withContext(Dispatchers.IO) { q.deleteMessageById(id) }

    suspend fun markPendingToolsInterrupted(
        toolRole: String, pendingPrefix: String, interruptedContent: String,
    ): Long = withContext(Dispatchers.IO) {
        q.markPendingToolsInterrupted(interruptedContent, toolRole, pendingPrefix).value
    }

    suspend fun searchMessages(query: String): List<Agent_message> =
        // M7：转义用户输入中的 \ % _，配合 .sq 的 `LIKE ... ESCAPE '\'`，避免被当通配符。
        withContext(Dispatchers.IO) { q.searchMessages(escapeSqlLike(query)).executeAsList() }

    suspend fun getAllMessagesOnce(): List<Agent_message> =
        withContext(Dispatchers.IO) { q.selectAllMessages().executeAsList() }

    suspend fun getMessagePageAfter(
        lastTimestamp: Long, lastId: String, limit: Long,
    ): List<Agent_message> = withContext(Dispatchers.IO) {
        q.selectMessagePageAfter(lastTimestamp, lastTimestamp, lastId, limit).executeAsList()
    }

    suspend fun getPageBySessionAfter(
        sessionId: String, lastTimestamp: Long, lastId: String, limit: Long,
    ): List<Agent_message> = withContext(Dispatchers.IO) {
        q.selectMessagePageBySessionAfter(sessionId, lastTimestamp, lastTimestamp, lastId, limit)
            .executeAsList()
    }
}
