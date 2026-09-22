package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.RunningToolOutput

/**
 * ToolCallCard 的 6 种展示状态。
 *
 * 全部从现有数据推导，不引入新的持久化状态。
 * 状态推导优先级：Running > Cancelled(冷启动中断) > TimedOut > Error > Success。
 *
 * 注意：Pending 状态在现有数据中无法可靠推导（ToolCallStarted 同时触发落库和
 * setRunningTool，两者之间无间隙），第一版不实现，归为 Running。
 */
enum class ToolCallState {
    /** 工具已请求，等待执行（排队/等审批）。第一版不实现，归为 Running。 */
    PENDING,

    /** 执行中，实时输出流式显示。 */
    RUNNING,

    /** 执行成功。 */
    SUCCESS,

    /** 执行失败（含普通错误）。 */
    ERROR,

    /** 用户中断或冷启动异常终止。 */
    CANCELLED,

    /** 执行超时（Error 的视觉子状态，橙色而非红色）。 */
    TIMED_OUT
}

/**
 * 工具调用状态推导器。
 *
 * 从 AgentUIMessage、RunningToolOutput、AgentUIState 推导 ToolCallCard 的展示状态。
 * 纯函数，可单元测试。
 *
 * 推导逻辑（按优先级从高到低）：
 * 1. RUNNING: liveOutput != null（runningTool 中存在对应 messageId）
 * 2. CANCELLED: message.content 以 PENDING_TOOL_MARKER 开头 && liveOutput == null &&
 *    agentState is Idle（冷启动异常终止，工具未正常结束）
 * 3. TIMED_OUT: message.isError == true && 输出文本匹配超时关键词
 * 4. ERROR: message.isError == true
 * 5. SUCCESS: 以上都不匹配（非运行中、无错误、有内容）
 *
 * 关键设计决策（修复 P0 漏洞 V1-01）：
 * - 不依赖 TaskGroup.isStreaming（工具执行期间 isStreaming=false）
 * - 直接从 liveOutput 是否为 null 推导 Running 状态
 * - 冷启动时 PENDING_TOOL_MARKER 残留 + liveOutput=null + agentState=Idle → Cancelled
 *   （而非误判为 Success）
 */
object ToolStateDeriver {

    /** 超时关键词列表（小写匹配）。 */
    private val TIMEOUT_KEYWORDS = listOf(
        "命令执行超时",
        "已强制终止",
        "timed out",
        "timeout",
        "tool_timeout",
        "execution timed out"
    )

    /** 用户中断关键词列表（小写匹配）。 */
    private val CANCELLED_KEYWORDS = listOf(
        "已停止",
        "agent_stopped_by_user",
        "stopped by user",
        "cancelled by user",
        "用户已停止"
    )

    /**
     * 推导工具调用的展示状态。
     *
     * @param message TOOL 类型的 AgentUIMessage
     * @param liveOutput 匹配 message.id 的实时输出，null 表示非运行中
     * @param agentState 全局 Agent 状态
     * @return 推导的 ToolCallState
     */
    fun derive(
        message: AgentUIMessage,
        liveOutput: RunningToolOutput?,
        agentState: AgentUIState
    ): ToolCallState {
        // 1. RUNNING: runningTool 中存在对应 messageId
        if (liveOutput != null) {
            return ToolCallState.RUNNING
        }

        val content = message.content

        // 2. CANCELLED: 冷启动异常终止
        // PENDING_TOOL_MARKER 残留 + liveOutput=null + agentState=Idle
        // 说明工具未正常结束（App 被杀或异常终止），不应误判为 Success
        val hasPendingMarker = content.startsWith(SessionUseCase.PENDING_TOOL_MARKER) ||
            content.startsWith(SessionUseCase.LEGACY_PENDING_TOOL_MARKER)
        if (hasPendingMarker && agentState is AgentUIState.Idle) {
            return ToolCallState.CANCELLED
        }

        // 3. TIMED_OUT: isError=true && 输出文本匹配超时关键词
        if (message.isError && containsTimeoutMarker(content)) {
            return ToolCallState.TIMED_OUT
        }

        // 4. CANCELLED: isError=true && 输出文本匹配用户中断关键词
        // （被用户停止的工具消息 isError=true，与执行失败的工具消息结构相同，
        //  唯一区别是 content 末尾有"已停止"文本）
        if (message.isError && containsCancelledMarker(content)) {
            return ToolCallState.CANCELLED
        }

        // 5. ERROR: isError=true（普通错误）
        if (message.isError) {
            return ToolCallState.ERROR
        }

        // 6. SUCCESS: 以上都不匹配
        return ToolCallState.SUCCESS
    }

    /**
     * 判断输出文本是否包含超时标记。
     *
     * 纯函数，可单元测试。匹配常见的超时提示文本。
     * 注意：AgentUIMessage 没有 errorCode 字段（P1 差距），超时只能从输出文本推断，
     * 这是不可靠的降级方案。长期方案需在 AgentUIMessage 增加 errorCode 字段。
     */
    fun containsTimeoutMarker(content: String): Boolean {
        if (content.isBlank()) return false
        val lower = content.lowercase()
        return TIMEOUT_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * 判断输出文本是否包含用户中断标记。
     *
     * 纯函数，可单元测试。匹配常见的用户中断提示文本。
     */
    fun containsCancelledMarker(content: String): Boolean {
        if (content.isBlank()) return false
        val lower = content.lowercase()
        return CANCELLED_KEYWORDS.any { lower.contains(it) }
    }
}
