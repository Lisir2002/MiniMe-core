package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Todo_items
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.TodoItemEntity
import com.mini.me_core.feature.agent.domain.tool.todo.TodoPort
import javax.inject.Inject

class TodoAdapter @Inject constructor(
    private val v2: AgentRepository,
) : TodoPort {

    override suspend fun listBySession(sessionId: String): List<TodoItemEntity> =
        v2.listTodos(sessionId).map { it.toEntity() }

    override suspend fun replaceAll(sessionId: String, items: List<TodoItemEntity>) {
        v2.runInTx { tx -> tx.replaceTodos(sessionId, items.map { it.toV2() }) }
    }

    private fun Todo_items.toEntity() = TodoItemEntity(
        id = id, sessionId = session_id, subject = subject, description = description,
        status = status, priority = priority.toInt(), order = sort_order.toInt(),
        createdAtMs = created_at_ms, updatedAtMs = updated_at_ms,
    )

    private fun TodoItemEntity.toV2() = Todo_items(
        id = id, session_id = sessionId, subject = subject, description = description,
        status = status, priority = priority.toLong(), sort_order = order.toLong(),
        created_at_ms = createdAtMs, updated_at_ms = updatedAtMs,
    )
}
