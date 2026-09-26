package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.proxy.domain.ProxyRuntimeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

internal data class UsageStats(
    val totalSessions: Int,
    val totalMessages: Int,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val firstUsedMs: Long,
    val activeDays: Int
)

@HiltViewModel
internal class AboutStatsViewModel @Inject constructor(
    private val v2Agent: V2AgentRepository,
    private val proxyManager: ClashProxyManager,
    private val containerInstaller: ContainerInstaller,
    private val kv: KVStore,
) : ViewModel() {

    private companion object {
        const val NS = "about"
        const val KEY_STATS_RESET_EPOCH = "stats_reset_epoch_ms"
    }

    private val _stats = MutableStateFlow(
        UsageStats(
            totalSessions = 0,
            totalMessages = 0,
            totalInputTokens = 0L,
            totalOutputTokens = 0L,
            firstUsedMs = 0L,
            activeDays = 0
        )
    )
    val stats: StateFlow<UsageStats> = _stats.asStateFlow()

    /** mihomo 代理内核运行态（来自 [ClashProxyManager]）。 */
    val proxyState: StateFlow<ProxyRuntimeState> = proxyManager.state

    private val _terminalReady = MutableStateFlow(false)
    val terminalReady: StateFlow<Boolean> = _terminalReady.asStateFlow()

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            _terminalReady.value =
                containerInstaller.isInstalled() || containerInstaller.isInstalledX86()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = load()
        }
    }

    /**
     * 重置使用统计：记录当前时间为重置纪元。重置后统计仅统计该时间之后创建的会话。
     * 不删除任何业务数据。
     */
    fun resetStats() {
        viewModelScope.launch(Dispatchers.IO) {
            kv.putInt(NS, KEY_STATS_RESET_EPOCH, System.currentTimeMillis())
            refresh()
        }
    }

    private suspend fun load(): UsageStats = withContext(Dispatchers.IO) {
        val resetEpoch = kv.getInt(NS, KEY_STATS_RESET_EPOCH) ?: 0L
        val sessions = v2Agent.getAllOnce()
            .map { it.toEntity() }
            .filter { it.createdAtMs >= resetEpoch }
        val messageCount = v2Agent.getAllMessagesOnce().size

        computeUsageStats(
            sessions = sessions.map {
                SessionCountInput(it.createdAtMs, it.totalInputTokens.toLong(), it.totalOutputTokens.toLong())
            },
            messageCount = messageCount,
            resetEpochMs = resetEpoch,
        )
    }

    // ── V2 映射 ──────────────────────────────────────────────────────

    private fun V2AgentSession.toEntity() = com.mini.me_core.feature.agent.data.local.entity.ChatSessionEntity(
        id = id,
        title = title ?: "",
        createdAtMs = created_at,
        updatedAtMs = updated_at,
        workspacePath = workspace_path,
        mode = mode,
        reasoningEffort = reasoning_effort,
        providerId = provider_id,
        model = model,
        totalInputTokens = total_input_tokens.toInt(),
        totalOutputTokens = total_output_tokens.toInt(),
        lastInputTokens = last_input_tokens.toInt(),
    )

    private fun com.mini.mecore.datalayer.sqldelight.agent.Agent_message.toEntity() = com.mini.me_core.feature.agent.data.local.entity.AgentMessageEntity(
        id = id,
        sessionId = session_id,
        taskId = task_id,
        role = role,
        content = content,
        timestamp = seq,
        toolCallsJson = tool_calls_json,
        toolCallId = tool_call_id,
        toolName = tool_name,
        toolArgs = tool_args,
        isError = is_error == 1L,
        reasoning = reasoning,
        signature = signature,
        attachmentsJson = attachments_json,
        isCompacted = is_compacted == 1L,
        isContextSummary = is_context_summary == 1L,
        isCompactionMarker = is_compaction_marker == 1L,
        inputTokens = input_tokens.toInt(),
        outputTokens = output_tokens.toInt(),
        chunkGroupId = chunk_group_id,
        chunkIndex = chunk_index.toInt(),
    )

    private fun utcDayBucket(ms: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

/** 统计计算的纯输入（与 DB 实体解耦，便于单测）。 */
internal data class SessionCountInput(
    val createdAtMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
)

/**
 * 由已过滤的会话列表计算使用统计。纯函数，便于单元测试。
 *
 * @param sessions 已按 resetEpoch 过滤的会话列表
 * @param messageCount 消息总数
 * @param resetEpochMs 统计重置纪元（早于此时间的会话应已在上层过滤）
 */
internal fun computeUsageStats(
    sessions: List<SessionCountInput>,
    messageCount: Int,
    resetEpochMs: Long,
): UsageStats {
    val totalSessions = sessions.size
    val totalInputTokens = sessions.sumOf { it.inputTokens }
    val totalOutputTokens = sessions.sumOf { it.outputTokens }
    val firstUsedMs = sessions.minOfOrNull { it.createdAtMs } ?: 0L

    val dayBuckets = sessions.map { utcDayBucket(it.createdAtMs) }.toSet()
    val activeDays = dayBuckets.size

    return UsageStats(
        totalSessions = totalSessions,
        totalMessages = messageCount,
        totalInputTokens = totalInputTokens,
        totalOutputTokens = totalOutputTokens,
        firstUsedMs = firstUsedMs,
        activeDays = activeDays,
    )
}

private fun utcDayBucket(ms: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
