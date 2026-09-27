package com.mini.me_core.feature.agent.presentation

import android.content.Context
import com.mini.me_core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务分组器：将消息列表按 taskId 分组，并按时间顺序切分为连续同类型片段。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - 按 taskId 分组消息
 * - 构建任务组（TaskGroup）
 * - 构建二级子组（TaskSubGroup）
 * - 派生任务标题
 * - 判断消息子组类型
 */
@Singleton
class TaskGrouper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 按 taskId 分组消息，构建任务组列表。
     * 历史对话（无 taskId）放在顶部，默认折叠。
     */
    fun buildTaskGroups(
        messages: List<AgentUIMessage>,
        expandedTasks: Map<String, Boolean>,
        expandedSubGroups: Map<String, Boolean>,
        streamingTaskId: String?
    ): List<TaskGroup> {
        val historical = messages.filter { it.taskId.isBlank() }
        val tasked = messages.filter { it.taskId.isNotBlank() }
        val result = mutableListOf<TaskGroup>()

        // 历史对话（升级前 / 斜杠命令 / 压缩锚点等无 taskId 的消息）：顶部扁平组，默认折叠。
        if (historical.isNotEmpty()) {
            result += TaskGroup(
                taskId = "",
                title = context.getString(R.string.chat_task_history),
                timestamp = historical.first().timestamp,
                subGroups = buildSubGroups("", historical, expandedSubGroups),
                isExpanded = expandedTasks[""] ?: false
            )
        }

        // 按 taskId 分组，保持首次出现顺序（时间序）。
        val taskOrder = tasked.map { it.taskId }.distinct()
        val grouped = tasked.groupBy { it.taskId }
        for (taskId in taskOrder) {
            val taskMessages = grouped[taskId] ?: continue
            val firstUser = taskMessages.firstOrNull { it.role == MessageRole.USER }
            val title = firstUser?.content?.let { deriveTaskTitle(it) } ?: context.getString(R.string.chat_task_untitled)
            val timestamp = firstUser?.timestamp ?: taskMessages.first().timestamp
            result += TaskGroup(
                taskId = taskId,
                title = title,
                timestamp = timestamp,
                subGroups = buildSubGroups(taskId, taskMessages, expandedSubGroups),
                isExpanded = expandedTasks[taskId] ?: true,
                isStreaming = taskId == streamingTaskId
            )
        }
        return result
    }

    /**
     * 按时间顺序把消息切分为「连续同类型」片段：仅合并相邻同类型消息，绝不跨类型重排，
     * 从而完整保留任务内真实的执行时间线。
     */
    fun buildSubGroups(
        taskId: String,
        messages: List<AgentUIMessage>,
        expandedMap: Map<String, Boolean>
    ): List<TaskSubGroup> {
        val result = mutableListOf<TaskSubGroup>()
        var currentType: TaskSubGroupType? = null
        var currentMessages = mutableListOf<AgentUIMessage>()
        var seq = 0
        for (msg in messages) {
            val type = msg.subGroupType() ?: continue
            if (currentType != null && type != currentType) {
                val id = "$taskId-$seq"
                result += TaskSubGroup(
                    id = id,
                    type = currentType,
                    messages = currentMessages,
                    isExpanded = expandedMap[id] ?: true
                )
                seq++
                currentMessages = mutableListOf()
            }
            currentType = type
            currentMessages += msg
        }
        if (currentType != null && currentMessages.isNotEmpty()) {
            val id = "$taskId-$seq"
            result += TaskSubGroup(
                id = id,
                type = currentType,
                messages = currentMessages,
                isExpanded = expandedMap[id] ?: true
            )
        }
        return result
    }

    /**
     * 消息归属的二级片段类型：ASSISTANT 消息（含内嵌思考）统一归 REPLY。
     */
    fun AgentUIMessage.subGroupType(): TaskSubGroupType? = when {
        role == MessageRole.USER && !isBackgroundNotification -> TaskSubGroupType.USER
        role == MessageRole.ASSISTANT &&
            (reasoning?.hasVisibleContent() == true || content.hasVisibleContent()) -> TaskSubGroupType.REPLY
        role == MessageRole.TOOL -> TaskSubGroupType.TOOL
        else -> null
    }

    /**
     * 从用户消息内容派生任务标题：取首个非空行，截断到 24 字符。
     */
    fun deriveTaskTitle(content: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        return if (firstLine.length <= 24) firstLine else firstLine.take(24) + "…"
    }
}
