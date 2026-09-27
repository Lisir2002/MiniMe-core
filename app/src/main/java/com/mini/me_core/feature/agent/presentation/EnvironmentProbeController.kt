package com.mini.me_core.feature.agent.presentation

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.tool.container.CheckEnvironmentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.domain.execution.tool.toTransportString
import com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.agent.presentation.component.parseEnvironmentComponents
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 环境探测控制器：管理容器环境状态探测、快照缓存与节流。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - 主动刷新环境（落库，进模型上下文）
 * - 旁路环境探测（不落库，仅UI展示）
 * - 环境快照持久化与恢复
 * - 探测节流控制
 */
@Singleton
class EnvironmentProbeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val containerEngine: LinuxContainerEngine,
    private val checkEnvironmentTool: CheckEnvironmentTool,
    private val messagePersistenceUseCase: MessagePersistenceUseCase
) {
    companion object {
        private const val TAG = "EnvironmentProbe"
        const val PROBE_THROTTLE_MS = 30_000L
    }

    private val _environmentSnapshots = MutableStateFlow<Map<String, EnvironmentSnapshot>>(emptyMap())
    val environmentSnapshots: StateFlow<Map<String, EnvironmentSnapshot>> = _environmentSnapshots.asStateFlow()

    private val envSnapshotStore by lazy { EnvironmentSnapshotStore(context) }
    private val lastProbeAt = mutableMapOf<String, Long>()

    /**
     * 主动刷新环境：结果落库，进入模型上下文。
     */
    fun refreshEnvironment(
        sessionId: String?,
        taskId: String? = null,
        currentTaskId: String? = null,
        components: List<String>? = null
    ) {
        val sid = sessionId ?: return
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (!containerEngine.isProvisioned()) return@launch
            val args = if (components.isNullOrEmpty()) emptyMap() else mapOf(
                "components" to kotlinx.serialization.json.JsonArray(components.map { kotlinx.serialization.json.JsonPrimitive(it) })
            )
            val result = runCatching { checkEnvironmentTool.execute(args) }.getOrNull()
            if (result is ToolResult.Success) {
                messagePersistenceUseCase.persist(
                    sid,
                    MessageRole.TOOL,
                    result.toTransportString(),
                    taskId = taskId ?: currentTaskId ?: "",
                    toolName = "check_environment",
                    toolArgs = null,
                    isError = false
                )
            }
        }
    }

    /**
     * 旁路环境探测：不落库、不进模型上下文，仅UI展示。
     * 带节流：同一触发消息在 PROBE_THROTTLE_MS 内不重复探测。
     */
    fun probeEnvironment(
        sessionId: String?,
        taskId: String,
        triggerMsgId: String,
        components: List<String>?
    ) {
        val sid = sessionId ?: return
        // 明确传入空集合时不做任何探测
        if (components != null && components.isEmpty()) {
            _environmentSnapshots.value = _environmentSnapshots.value - triggerMsgId
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastProbeAt[triggerMsgId] ?: 0L
        if (now - last < PROBE_THROTTLE_MS) return
        lastProbeAt[triggerMsgId] = now
        // 先标记「探测中」
        _environmentSnapshots.value = _environmentSnapshots.value + (
            triggerMsgId to EnvironmentSnapshot(key = triggerMsgId, components = emptyList(), probedAt = now, probing = true)
        )
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (!containerEngine.isProvisioned()) {
                _environmentSnapshots.value = _environmentSnapshots.value - triggerMsgId
                return@launch
            }
            val args = if (components.isNullOrEmpty()) emptyMap() else mapOf(
                "components" to kotlinx.serialization.json.JsonArray(components.map { kotlinx.serialization.json.JsonPrimitive(it) })
            )
            val result = runCatching { checkEnvironmentTool.execute(args) }.getOrNull()
            val snapshot = if (result is ToolResult.Success) {
                EnvironmentSnapshot(
                    key = triggerMsgId,
                    components = parseEnvironmentComponents(result.toTransportString()),
                    probedAt = android.os.SystemClock.elapsedRealtime(),
                    probing = false
                )
            } else {
                _environmentSnapshots.value = _environmentSnapshots.value - triggerMsgId
                return@launch
            }
            _environmentSnapshots.value = _environmentSnapshots.value + (triggerMsgId to snapshot)
            // 持久化到磁盘
            if (sid != null) {
                envSnapshotStore.save(sid, _environmentSnapshots.value)
            }
        }
    }

    /**
     * 从磁盘恢复环境快照。
     */
    fun restoreSnapshots(sessionId: String) {
        val restored = envSnapshotStore.load(sessionId)
        if (restored.isNotEmpty()) {
            _environmentSnapshots.value = restored
            FileLogger.d(TAG, "Restored ${restored.size} environment snapshots for session $sessionId")
        }
    }

    /**
     * 清除指定会话的环境快照。
     */
    fun clearSnapshots(sessionId: String) {
        envSnapshotStore.clear(sessionId)
        _environmentSnapshots.value = emptyMap()
        lastProbeAt.clear()
    }
}
