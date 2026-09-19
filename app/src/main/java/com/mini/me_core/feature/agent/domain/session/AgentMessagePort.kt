package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.feature.agent.data.local.entity.AgentMessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Agent 消息数据端口（domain 定义，data 实现）。domain 不感知 SQLDelight 生成类型。
 */
interface AgentMessagePort {
    suspend fun insert(entity: AgentMessageEntity)
    suspend fun insertAll(entities: List<AgentMessageEntity>)
    suspend fun updateContent(messageId: String, newContent: String)
    suspend fun listBySession(sessionId: String): List<AgentMessageEntity>
    suspend fun markCompactedBeforeTimestamp(sessionId: String, cutoffTimestamp: Long)
    fun observeBySessionPaged(sessionId: String, limit: Long): Flow<List<AgentMessageEntity>>
    suspend fun getMessageById(messageId: String): AgentMessageEntity?
    suspend fun markCompactedInclusiveFromTimestamp(sessionId: String, timestamp: Long)
}
