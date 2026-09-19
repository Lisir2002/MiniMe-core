package com.mini.me_core.feature.backup.domain

/**
 * 备份数据源端口：feature/backup 只面向此接口与 DTO，不直接 import feature.agent.data.entity / datalayer。
 * 分页导出返回 DTO；导入接收 DTO。V2↔DTO 映射收进 :app 的 AgentBackupDataSourceAdapter。
 */
interface BackupDataSource {
    suspend fun getSessionDto(sessionId: String): ChatSessionDto?

    // 单会话导出分页
    suspend fun pageMessagesForExport(sessionId: String, lastTs: Long, lastId: String, limit: Long): List<AgentMessageDto>
    suspend fun pageTodosForExport(sessionId: String, lastCreatedAtMs: Long, lastId: String, limit: Long): List<TodoItemDto>

    // 全量导出分页
    suspend fun pageAllSessions(lastUpdatedAtMs: Long, lastId: String, limit: Long): List<ChatSessionDto>
    suspend fun pageAllMessages(lastTs: Long, lastId: String, limit: Long): List<AgentMessageDto>
    suspend fun pageAllTodos(lastCreatedAtMs: Long, lastId: String, limit: Long): List<TodoItemDto>

    // 导入
    suspend fun importSessions(items: List<ChatSessionDto>)
    suspend fun importMessages(items: List<AgentMessageDto>)
    suspend fun importTodos(items: List<TodoItemDto>)
}
