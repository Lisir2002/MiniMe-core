package com.mini.me_core.feature.agent.domain.tool.todo

import com.mini.me_core.feature.agent.data.local.entity.TodoItemEntity

/** 待办（Todo）数据端口。 */
interface TodoPort {
    suspend fun listBySession(sessionId: String): List<TodoItemEntity>
    suspend fun replaceAll(sessionId: String, items: List<TodoItemEntity>)
}
