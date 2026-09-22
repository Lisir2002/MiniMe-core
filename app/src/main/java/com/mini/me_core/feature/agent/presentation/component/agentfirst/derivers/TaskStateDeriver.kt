package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.domain.tool.PendingToolPermission
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType

/**
 * TaskCard 的 7 种展示状态。
 *
 * 全部从现有数据推导，不引入新的持久化状态。
 * 状态推导优先级：WaitingApproval > Running > Planning > Failed > Cancelled > Completed > Idle。
 */
enum class TaskState {
    /** 空闲（无子分组或空任务）。 */
    IDLE,

    /** 规划中（有 REASONING 无 TOOL，正在思考）。 */
    PLANNING,

    /** 运行中（文本流式 或 工具执行中）。 */
    RUNNING,

    /** 等待审批（pendingToolPermission 存在）。 */
    WAITING_APPROVAL,

    /** 已完成（所有工具成功，有回复）。 */
    COMPLETED,

    /** 失败（最后一条工具消息 isError=true）。 */
    FAILED,

    /** 已取消（用户中断或异常终止）。 */
    CANCELLED
}

/**
 * 任务状态推导器。
 *
 * 从 TaskGroup、AgentUIState、runningTools、pendingPermission 推导 TaskCard 的展示状态。
 * 纯函数，可单元测试。
 *
 * 推导逻辑（按优先级从高到低）：
 * 1. WAITING_APPROVAL: pendingPermission != null
 * 2. RUNNING: group.isStreaming == true || 该 group 的 TOOL 消息有对应 runningTool
 *    （关键 P0 修复：isStreaming 仅在文本流式时为 true，工具执行期间 isStreaming=false，
 *     必须额外检查 runningTool 匹配，否则工具执行期间 TaskCard 不会显示 Running）
 * 3. PLANNING: !isStreaming && 有 REASONING 子分组 && 无 TOOL 子分组 && 无 REPLY 子分组
 * 4. FAILED: !isStreaming && 最后一条 TOOL 消息 isError=true（且非 Cancelled）
 * 5. CANCELLED: !isStreaming && 最后一条 TOOL 消息 content 含"已停止"标记
 * 6. COMPLETED: !isStreaming && 所有 TOOL isError=false && 有 REPLY 子分组
 * 7. IDLE: 无子分组
 *
 * 注意：
 * - PendingToolPermission 没有 taskId 字段（P1 差距），无法精确归属到具体 TaskCard。
 *   第一版：pendingPermission 存在时，所有 Running 状态的 TaskCard 都显示 WaitingApproval。
 *   长期方案：在 PendingToolPermission 增加 taskId 字段。
 * - "最后一条 TOOL 消息"的判定：按时间顺序取最后一个 TOOL 子分组中的最后一条消息。
 * - Failed vs Cancelled 的区分：先检查 Cancelled 标记（"已停止"），再检查 isError。
 */
object TaskStateDeriver {

    /**
     * 推导任务的展示状态。
     *
     * @param group 任务分组
     * @param agentState 全局 Agent 状态
     * @param runningTools 当前会话所有运行中工具
     * @param pendingPermission 待审批的工具权限（null 表示无待审批）
     * @return 推导的 TaskState
     */
    fun derive(
        group: TaskGroup,
        agentState: AgentUIState,
        runningTools: List<RunningToolOutput>,
        pendingPermission: PendingToolPermission?
    ): TaskState {
        // 无子分组 → IDLE
        if (group.subGroups.isEmpty()) {
            return TaskState.IDLE
        }

        // 1. WAITING_APPROVAL: pendingPermission != null
        // （P1 差距：pendingPermission 无 taskId，第一版所有 Running 任务都显示 WaitingApproval）
        if (pendingPermission != null) {
            return TaskState.WAITING_APPROVAL
        }

        // 收集该任务的所有 TOOL 消息
        val toolMessages = collectToolMessages(group)
        val hasTool = toolMessages.isNotEmpty()
        val hasReply = group.subGroups.any { it.type == TaskSubGroupType.REPLY }
        val hasReasoning = group.subGroups.any { it.type == TaskSubGroupType.REASONING }

        // 2. RUNNING: isStreaming || 该任务的 TOOL 消息有对应 runningTool
        // （P0 修复：工具执行期间 isStreaming=false，必须检查 runningTool）
        val hasRunningTool = toolMessages.any { msg ->
            runningTools.any { it.messageId == msg.id }
        }
        if (group.isStreaming || hasRunningTool) {
            return TaskState.RUNNING
        }

        // 3. PLANNING: 有 REASONING 无 TOOL 无 REPLY
        if (hasReasoning && !hasTool && !hasReply) {
            return TaskState.PLANNING
        }

        // 获取最后一条 TOOL 消息（按时间顺序）
        val lastToolMessage = toolMessages.lastOrNull()

        // 4. CANCELLED: 最后一条 TOOL 消息 content 含"已停止"标记
        // （先检查 Cancelled，因为被用户停止的工具 isError=true，与 Failed 结构相同）
        if (lastToolMessage != null && ToolStateDeriver.containsCancelledMarker(lastToolMessage.content)) {
            return TaskState.CANCELLED
        }

        // 5. FAILED: 最后一条 TOOL 消息 isError=true
        // （简化：不做"后续成功恢复"判断，最后一条工具失败即 Failed）
        if (lastToolMessage != null && lastToolMessage.isError) {
            return TaskState.FAILED
        }

        // 6. COMPLETED: 所有 TOOL isError=false && 有 REPLY
        if (hasReply && toolMessages.all { !it.isError }) {
            return TaskState.COMPLETED
        }

        // 7. 无 TOOL 无 REPLY 但有 USER → 视为已完成（纯对话）
        if (!hasTool && hasReply) {
            return TaskState.COMPLETED
        }

        // 兜底：有消息但不匹配以上状态 → COMPLETED
        return TaskState.COMPLETED
    }

    /**
     * 收集任务组内所有 TOOL 消息（按时间顺序）。
     *
     * 遍历所有子分组，筛选 type==TOOL 的子分组，收集其中所有 role==TOOL 的消息。
     * 保持子分组的时间顺序。
     */
    fun collectToolMessages(group: TaskGroup): List<AgentUIMessage> {
        return group.subGroups
            .filter { it.type == TaskSubGroupType.TOOL }
            .flatMap { subGroup ->
                subGroup.messages.filter { it.role == MessageRole.TOOL }
            }
    }

    /**
     * 检查任务是否有工具正在运行。
     *
     * 用于 TaskCard 的 Running 状态推导和进度条动画。
     */
    fun hasRunningTool(group: TaskGroup, runningTools: List<RunningToolOutput>): Boolean {
        val toolMessages = collectToolMessages(group)
        return toolMessages.any { msg ->
            runningTools.any { it.messageId == msg.id }
        }
    }
}
