package com.mini.me_core.datalayer.repository

import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Todo_items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 待办存储（TodoItemDao 等价）：todo_items 表的 CRUD、分页、批量 upsert 与级联删除。
 */
internal class AgentTodoStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertTodo(
        id: String, sessionId: String, subject: String, description: String, status: String,
        priority: Long, order: Long, createdAtMs: Long, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertTodoItem(
            id, sessionId, subject, description, status, priority, order, createdAtMs, updatedAtMs,
        )
    }

    suspend fun listTodos(sessionId: String): List<Todo_items> =
        withContext(Dispatchers.IO) { q.selectTodoItemsBySession(sessionId).executeAsList() }

    suspend fun updateTodoStatus(id: String, status: String, updatedAtMs: Long) =
        withContext(Dispatchers.IO) { q.updateTodoItemStatus(status, updatedAtMs, id) }

    suspend fun deleteTodo(id: String) = withContext(Dispatchers.IO) { q.deleteTodoItem(id) }

    // ── P2-3 备份流式导出/还原（对齐 TodoItemDao 分页 + upsertAll）─────────

    suspend fun getTodoPageAfter(
        lastCreatedAtMs: Long, lastId: String, limit: Long,
    ): List<Todo_items> = withContext(Dispatchers.IO) {
        q.selectTodoPageAfter(lastCreatedAtMs, lastId, limit).executeAsList()
    }

    suspend fun getTodoPageBySessionAfter(
        sessionId: String, lastCreatedAtMs: Long, lastId: String, limit: Long,
    ): List<Todo_items> = withContext(Dispatchers.IO) {
        q.selectTodoPageBySessionAfter(sessionId, lastCreatedAtMs, lastId, limit).executeAsList()
    }

    /**
     * 批量 upsert 待办（备份恢复用）。
     * P1 修复：原 forEach 逐条 upsert 无事务包裹，改为单事务保证原子性和性能。
     */
    suspend fun upsertAllTodos(todos: List<Todo_items>) = withContext(Dispatchers.IO) {
        db.transaction {
            todos.forEach { t ->
                q.upsertTodoItem(
                    t.id, t.session_id, t.subject, t.description, t.status, t.priority,
                    t.sort_order, t.created_at_ms, t.updated_at_ms,
                )
            }
        }
    }

    // ── P2-3 会话级联删除（对齐 SessionUseCase.deleteSession）─────────────

    suspend fun deleteTodosBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteTodoItemsBySession(sessionId) }

    /** 备份「覆盖恢复」专用：清空待办大表。 */
    suspend fun deleteAllTodos() = withContext(Dispatchers.IO) { q.deleteAllTodos() }
}
