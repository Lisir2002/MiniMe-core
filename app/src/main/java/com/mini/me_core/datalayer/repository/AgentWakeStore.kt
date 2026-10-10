package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Wake_queue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 唤醒队列存储：wake_queue 表。实现 [WakeQueueStore] 接口，由 [AgentRepository] 委托。
 */
internal class AgentWakeStore(private val db: AgentDb) : WakeQueueStore {

    private val q get() = db.agentQueries

    suspend fun insertWakeItem(
        wakeId: String, sessionId: String, source: String, type: String, content: String,
        status: String, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertWakeItem(wakeId, sessionId, source, type, content, status, createdAtMs)
    }

    override suspend fun upsertWakeItem(
        wakeId: String, sessionId: String, source: String, type: String, content: String,
        status: String, createdAtMs: Long,
    ): Unit = withContext(Dispatchers.IO) {
        q.upsertWakeItem(wakeId, sessionId, source, type, content, status, createdAtMs)
        Unit
    }

    override suspend fun listPendingWakeItems(): List<Wake_queue> =
        withContext(Dispatchers.IO) { q.selectPendingWakeItems().executeAsList() }

    override suspend fun listWakeBySessionAndStatus(sessionId: String, status: String): List<Wake_queue> =
        withContext(Dispatchers.IO) { q.selectWakeBySessionAndStatus(status, sessionId).executeAsList() }

    suspend fun markWakeItemConsumed(wakeId: String) =
        withContext(Dispatchers.IO) { q.markWakeItemConsumed(wakeId) }

    override suspend fun markWakeItemsConsumedBatch(ids: List<String>, status: String): Unit =
        withContext(Dispatchers.IO) {
            // 待确认2：SQLDelight 对空列表生成 `IN ()`，SQLite 允许但语义为 false（不更新任何行）。
            // 显式短路，避免「空列表被误当成全量」的歧义，也省一次无意义的写事务。
            if (ids.isEmpty()) return@withContext
            q.markWakeItemsConsumedBatch(status, ids)
            Unit
        }

    fun observePendingWakeItems(): Flow<List<Wake_queue>> =
        q.selectPendingWakeItems().asFlow().mapToList(Dispatchers.IO)

    suspend fun deleteWakeItemsBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteWakeItemsBySession(sessionId) }
}
