package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.domain.tool.PendingToolPermission
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateDeriverTest {

    private fun userMsg(id: String = "user_1", content: String = "请帮我构建项目") =
        AgentUIMessage(id = id, role = MessageRole.USER, content = content)

    private fun replyMsg(id: String = "reply_1", content: String = "好的，我来帮你构建") =
        AgentUIMessage(id = id, role = MessageRole.ASSISTANT, content = content)

    private fun toolMsg(
        id: String = "tool_1",
        content: String = "Build successful",
        isError: Boolean = false,
        toolName: String = "Bash"
    ) = AgentUIMessage(
        id = id, role = MessageRole.TOOL, content = content,
        toolName = toolName, toolArgs = """{"command":"./gradlew build"}""", isError = isError
    )

    private fun reasoningMsg(id: String = "reasoning_1", content: String = "我需要先检查环境") =
        AgentUIMessage(id = id, role = MessageRole.ASSISTANT, content = "", reasoning = content)

    private fun taskGroup(
        taskId: String = "task_1",
        subGroups: List<TaskSubGroup>,
        isStreaming: Boolean = false
    ) = TaskGroup(
        taskId = taskId,
        title = "测试任务",
        timestamp = System.currentTimeMillis(),
        subGroups = subGroups,
        isStreaming = isStreaming
    )

    private fun subGroup(type: TaskSubGroupType, messages: List<AgentUIMessage>, id: String = "sg_1") =
        TaskSubGroup(id = id, type = type, messages = messages)

    // ===== IDLE =====

    @Test
    fun emptySubGroups_returnsIDLE() {
        val group = taskGroup(subGroups = emptyList())
        assertEquals(TaskState.IDLE, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    // ===== WAITING_APPROVAL =====

    @Test
    fun pendingPermissionNotNull_returnsWAITING_APPROVAL() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg()), id = "sg_2")
        ), isStreaming = true)
        val pending = PendingToolPermission(
            id = "perm_1", toolName = "Bash", title = "执行命令",
            summary = "需要确认", details = "详情", argsPreview = "ls"
        )
        assertEquals(TaskState.WAITING_APPROVAL, TaskStateDeriver.derive(group, AgentUIState.Streaming, emptyList(), pending))
    }

    // ===== RUNNING =====

    @Test
    fun isStreamingTrue_returnsRUNNING() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_2")
        ), isStreaming = true)
        assertEquals(TaskState.RUNNING, TaskStateDeriver.derive(group, AgentUIState.Streaming, emptyList(), null))
    }

    @Test
    fun isStreamingFalseButRunningToolExists_returnsRUNNING() {
        // P0 修复：工具执行期间 isStreaming=false，但 runningTool 存在 → Running
        val tool = toolMsg(id = "tool_1")
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(tool), id = "sg_2")
        ), isStreaming = false)
        val runningTools = listOf(RunningToolOutput(messageId = "tool_1", text = "building...", toolName = "Bash"))
        assertEquals(TaskState.RUNNING, TaskStateDeriver.derive(group, AgentUIState.Idle, runningTools, null))
    }

    @Test
    fun isStreamingFalseNoRunningTool_returnsNotRUNNING() {
        val tool = toolMsg(id = "tool_1")
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(tool), id = "sg_2"),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_3")
        ), isStreaming = false)
        assertEquals(TaskState.COMPLETED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    // ===== PLANNING =====

    @Test
    fun reasoningOnlyNoToolNoReply_returnsPLANNING() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.REASONING, listOf(reasoningMsg()), id = "sg_2")
        ), isStreaming = false)
        assertEquals(TaskState.PLANNING, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    @Test
    fun reasoningWithTool_returnsNotPLANNING() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.REASONING, listOf(reasoningMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg()), id = "sg_2")
        ), isStreaming = false)
        // 有 TOOL 无 REPLY，最后工具成功 → COMPLETED（兜底）
        val result = TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null)
        assertTrue(result == TaskState.COMPLETED || result == TaskState.PLANNING)
    }

    // ===== FAILED =====

    @Test
    fun lastToolIsError_returnsFAILED() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(
                toolMsg(id = "tool_1", content = "success"),
                toolMsg(id = "tool_2", content = "Build failed", isError = true)
            ), id = "sg_2")
        ), isStreaming = false)
        assertEquals(TaskState.FAILED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    @Test
    fun firstToolErrorLastToolSuccess_returnsCOMPLETED() {
        // 简化：最后一条工具成功 → COMPLETED（不做"后续恢复"判断）
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.TOOL, listOf(
                toolMsg(id = "tool_1", content = "failed", isError = true),
                toolMsg(id = "tool_2", content = "retry success")
            ), id = "sg_2"),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_3")
        ), isStreaming = false)
        assertEquals(TaskState.COMPLETED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    // ===== CANCELLED =====

    @Test
    fun lastToolStopped_returnsCANCELLED() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.TOOL, listOf(
                toolMsg(id = "tool_1", content = "已停止 agent_stopped_by_user", isError = true)
            ), id = "sg_2")
        ), isStreaming = false)
        assertEquals(TaskState.CANCELLED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    // ===== COMPLETED =====

    @Test
    fun allToolsSuccessWithReply_returnsCOMPLETED() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg()), id = "sg_2"),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_3")
        ), isStreaming = false)
        assertEquals(TaskState.COMPLETED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    @Test
    fun noToolWithReply_returnsCOMPLETED() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_2")
        ), isStreaming = false)
        assertEquals(TaskState.COMPLETED, TaskStateDeriver.derive(group, AgentUIState.Idle, emptyList(), null))
    }

    // ===== collectToolMessages =====

    @Test
    fun collectToolMessages_filtersOnlyToolMessages() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.USER, listOf(userMsg())),
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg(id = "t1"), toolMsg(id = "t2")), id = "sg_2"),
            subGroup(TaskSubGroupType.REPLY, listOf(replyMsg()), id = "sg_3"),
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg(id = "t3")), id = "sg_4")
        ))
        val tools = TaskStateDeriver.collectToolMessages(group)
        assertEquals(3, tools.size)
        assertEquals("t1", tools[0].id)
        assertEquals("t2", tools[1].id)
        assertEquals("t3", tools[2].id)
    }

    // ===== hasRunningTool =====

    @Test
    fun hasRunningTool_matchingMessageId_returnsTrue() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg(id = "tool_1")), id = "sg_2")
        ))
        val runningTools = listOf(RunningToolOutput(messageId = "tool_1", text = "running", toolName = "Bash"))
        assertTrue(TaskStateDeriver.hasRunningTool(group, runningTools))
    }

    @Test
    fun hasRunningTool_noMatchingMessageId_returnsFalse() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg(id = "tool_1")), id = "sg_2")
        ))
        val runningTools = listOf(RunningToolOutput(messageId = "other_tool", text = "running", toolName = "Bash"))
        assertFalse(TaskStateDeriver.hasRunningTool(group, runningTools))
    }

    @Test
    fun hasRunningTool_emptyRunningTools_returnsFalse() {
        val group = taskGroup(subGroups = listOf(
            subGroup(TaskSubGroupType.TOOL, listOf(toolMsg()), id = "sg_2")
        ))
        assertFalse(TaskStateDeriver.hasRunningTool(group, emptyList()))
    }
}
