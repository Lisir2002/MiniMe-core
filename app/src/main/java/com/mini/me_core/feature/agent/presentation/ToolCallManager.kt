package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.feature.agent.domain.core.model.CodeChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具调用状态管理器：管理运行中工具、代码变更、上下文压缩状态。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - 运行中工具跟踪（支持多工具并行）
 * - 代码变更跟踪
 * - 上下文压缩状态管理
 */
@Singleton
class ToolCallManager @Inject constructor() {

    private val _runningTools = MutableStateFlow<Map<String, Map<String, RunningToolOutput>>>(emptyMap())
    val runningTools: StateFlow<Map<String, Map<String, RunningToolOutput>>> = _runningTools.asStateFlow()

    private val _changesMap = MutableStateFlow<Map<String, List<CodeChange>>>(emptyMap())
    val changesMap: StateFlow<Map<String, List<CodeChange>>> = _changesMap.asStateFlow()

    private val _compactingSessions = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val compactingSessions: StateFlow<Map<String, Boolean>> = _compactingSessions.asStateFlow()

    /**
     * 添加/更新一个运行中工具（按 msgId 定位，支持多个工具并行）。
     */
    fun setRunningTool(sessionId: String, msgId: String, tool: RunningToolOutput) {
        val sessionTools = _runningTools.value[sessionId] ?: emptyMap()
        _runningTools.value = _runningTools.value + (sessionId to (sessionTools + (msgId to tool)))
    }

    /**
     * 移除一个运行中工具；会话无剩余运行工具时清除该会话条目。
     */
    fun removeRunningTool(sessionId: String, msgId: String) {
        val sessionTools = _runningTools.value[sessionId] ?: return
        val updated = sessionTools - msgId
        _runningTools.value = if (updated.isEmpty()) {
            _runningTools.value - sessionId
        } else {
            _runningTools.value + (sessionId to updated)
        }
    }

    /**
     * 获取指定会话的运行中工具列表。
     */
    fun getRunningTools(sessionId: String): List<RunningToolOutput> =
        _runningTools.value[sessionId]?.values?.toList() ?: emptyList()

    /**
     * 设置代码变更列表；空列表时清除该会话条目。
     */
    fun setChanges(sessionId: String, changes: List<CodeChange>) {
        _changesMap.value = if (changes.isEmpty()) _changesMap.value - sessionId else _changesMap.value + (sessionId to changes)
    }

    /**
     * 获取指定会话的代码变更列表。
     */
    fun getChanges(sessionId: String): List<CodeChange> =
        _changesMap.value[sessionId] ?: emptyList()

    /**
     * 设置上下文压缩状态。
     */
    fun setCompacting(sessionId: String, compacting: Boolean) {
        _compactingSessions.value = if (compacting) {
            _compactingSessions.value + (sessionId to true)
        } else {
            _compactingSessions.value - sessionId
        }
    }

    /**
     * 检查指定会话是否正在压缩上下文。
     */
    fun isCompacting(sessionId: String): Boolean =
        _compactingSessions.value[sessionId] == true

    /**
     * 清除指定会话的所有状态。
     */
    fun clearSessionState(sessionId: String) {
        _runningTools.value = _runningTools.value - sessionId
        _changesMap.value = _changesMap.value - sessionId
        _compactingSessions.value = _compactingSessions.value - sessionId
    }
}
