package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Zth_l0_soft_compact_restore_logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 上下文压缩/摘要持久化：消息压缩标记、按时间戳裁剪消息、L0 soft-compact 恢复日志。
 * 与普通消息 CRUD（[AgentMessageStore]）拆开，对应审计报告「按压缩拆仓库」。
 */
internal class AgentCompactionStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    suspend fun deleteMessagesBeforeTimestamp(sessionId: String, cutoffTimestamp: Long) =
        withContext(Dispatchers.IO) { q.deleteMessagesBeforeTimestamp(sessionId, cutoffTimestamp) }

    suspend fun markMessagesCompactedBeforeTimestamp(sessionId: String, cutoffTimestamp: Long) =
        withContext(Dispatchers.IO) { q.markMessagesCompactedBeforeTimestamp(sessionId, cutoffTimestamp) }

    suspend fun markMessagesCompactedInclusiveFromTimestamp(sessionId: String, cutoffTimestamp: Long) =
        withContext(Dispatchers.IO) {
            q.markMessagesCompactedInclusiveFromTimestamp(sessionId, cutoffTimestamp)
        }

    suspend fun deleteMessagesInclusiveFromTimestamp(sessionId: String, cutoffTimestamp: Long) =
        withContext(Dispatchers.IO) {
            q.deleteMessagesInclusiveFromTimestamp(sessionId, cutoffTimestamp)
        }

    suspend fun deleteMessagesExclusiveAfterTimestamp(sessionId: String, cutoffTimestamp: Long) =
        withContext(Dispatchers.IO) {
            q.deleteMessagesExclusiveAfterTimestamp(sessionId, cutoffTimestamp)
        }

    fun observeRestoreLogsBySession(sessionId: String): Flow<List<Zth_l0_soft_compact_restore_logs>> =
        q.selectRestoreLogsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertRestoreLog(
        id: String, sessionId: String, firstMessageId: String, lastMessageId: String,
        originalRowCount: Long, tokensBefore: Long, tokensAfter: Long,
        sCompactSourceDigestCiphertext: String, expireAtMs: Long, restoredFlag: Long, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertRestoreLog(
            id, sessionId, firstMessageId, lastMessageId, originalRowCount, tokensBefore,
            tokensAfter, sCompactSourceDigestCiphertext, expireAtMs, restoredFlag, createdAtMs,
        )
    }

    suspend fun listRestoreLogs(sessionId: String): List<Zth_l0_soft_compact_restore_logs> =
        withContext(Dispatchers.IO) { q.selectRestoreLogsBySession(sessionId).executeAsList() }

    suspend fun listRestoreLogsExpiredNotRestored(nowMs: Long): List<Zth_l0_soft_compact_restore_logs> =
        withContext(Dispatchers.IO) { q.selectRestoreLogsExpiredNotRestored(nowMs).executeAsList() }

    suspend fun listAllRestoreLogs(): List<Zth_l0_soft_compact_restore_logs> =
        withContext(Dispatchers.IO) { q.selectAllRestoreLogs().executeAsList() }

    suspend fun markRestoreLogRestored(id: String) {
        withContext(Dispatchers.IO) { q.updateRestoreLogRestored(id) }
    }
}
