package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent UI状态管理器：管理Agent执行状态、流式文本、流式思考、重试状态。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - AgentUIState 状态管理
 * - 流式正文/思考文本管理（含内存护栏截断）
 * - 流式任务指示同步
 * - 重试状态管理
 *
 * 注意：currentSessionId 和 currentTaskIdBySession 由 AIAgentViewModel 提供，
 * 本类通过回调获取。
 */
@Singleton
class AgentStateManager @Inject constructor() {

    companion object {
        private const val TAG = "AgentStateManager"
        const val STREAMING_TEXT_MAX_CHARS = 50_000
        const val STREAMING_REASONING_MAX_CHARS = 30_000
    }

    private val _agentStates = MutableStateFlow<Map<String, AgentUIState>>(emptyMap())
    val agentStates: StateFlow<Map<String, AgentUIState>> = _agentStates.asStateFlow()

    private val _streamingTexts = MutableStateFlow<Map<String, String?>>(emptyMap())
    val streamingTexts: StateFlow<Map<String, String?>> = _streamingTexts.asStateFlow()

    private val _streamingReasonings = MutableStateFlow<Map<String, String?>>(emptyMap())
    val streamingReasonings: StateFlow<Map<String, String?>> = _streamingReasonings.asStateFlow()

    private val _streamingTaskBySession = MutableStateFlow<Map<String, String>>(emptyMap())
    val streamingTaskBySession: StateFlow<Map<String, String>> = _streamingTaskBySession.asStateFlow()

    private val _retryStates = MutableStateFlow<Map<String, RetryState?>>(emptyMap())
    val retryStates: StateFlow<Map<String, RetryState?>> = _retryStates.asStateFlow()

    /**
     * 设置Agent UI状态。
     */
    fun setAgentState(sessionId: String, state: AgentUIState) {
        _agentStates.value = _agentStates.value + (sessionId to state)
    }

    /**
     * 获取指定会话的Agent状态。
     */
    fun getAgentState(sessionId: String): AgentUIState =
        _agentStates.value[sessionId] ?: AgentUIState.Idle

    /**
     * 流式正文/思考的内存护栏：单条流式文本显示上限。
     * 只截断流式显示，最终落库仍由 MessagePersistenceUseCase.MAX_CONTENT_CHARS 兜底。
     */
    fun capStreamingText(text: String, isReasoning: Boolean): String {
        val max = if (isReasoning) STREAMING_REASONING_MAX_CHARS else STREAMING_TEXT_MAX_CHARS
        if (text.length <= max) return text
        FileLogger.w(
            TAG,
            "流式${if (isReasoning) "思考" else "正文"}超长已截断显示: len=${text.length} > $max（仅截断流式显示，落库走 MAX_CONTENT_CHARS 兜底）"
        )
        return text.take(max)
    }

    /**
     * 设置流式正文文本。
     */
    fun setStreamingText(sessionId: String, text: String?, currentTaskId: String?) {
        val capped = text?.let { capStreamingText(it, isReasoning = false) }
        _streamingTexts.value = if (capped == null) _streamingTexts.value - sessionId else _streamingTexts.value + (sessionId to capped)
        updateStreamingTask(sessionId, currentTaskId)
    }

    /**
     * 设置流式思考文本。
     */
    fun setStreamingReasoning(sessionId: String, text: String?, currentTaskId: String?) {
        val capped = text?.let { capStreamingText(it, isReasoning = true) }
        _streamingReasonings.value = if (capped == null) _streamingReasonings.value - sessionId else _streamingReasonings.value + (sessionId to capped)
        updateStreamingTask(sessionId, currentTaskId)
    }

    /**
     * 同步流式任务指示：会话正在流式（正文或思考）时标记当前 taskId，否则清除。
     */
    private fun updateStreamingTask(sessionId: String, currentTaskId: String?) {
        val isStreaming = _streamingTexts.value[sessionId] != null || _streamingReasonings.value[sessionId] != null
        _streamingTaskBySession.value = if (isStreaming && currentTaskId != null) {
            _streamingTaskBySession.value + (sessionId to currentTaskId)
        } else {
            _streamingTaskBySession.value - sessionId
        }
    }

    /**
     * 设置重试状态。
     */
    fun setRetryState(sessionId: String, state: RetryState?) {
        _retryStates.value = if (state == null) _retryStates.value - sessionId else _retryStates.value + (sessionId to state)
    }

    /**
     * 获取指定会话的重试状态。
     */
    fun getRetryState(sessionId: String): RetryState? = _retryStates.value[sessionId]

    /**
     * 清除指定会话的所有状态。
     */
    fun clearSessionState(sessionId: String) {
        _agentStates.value = _agentStates.value - sessionId
        _streamingTexts.value = _streamingTexts.value - sessionId
        _streamingReasonings.value = _streamingReasonings.value - sessionId
        _streamingTaskBySession.value = _streamingTaskBySession.value - sessionId
        _retryStates.value = _retryStates.value - sessionId
    }
}
