package com.mini.me_core.feature.agent.presentation

import android.content.Context
import com.mini.me_core.R
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.me_core.feature.agent.domain.execution.workflow.AgentEvent
import com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 用量统计聚合器（ 从 AIAgentViewModel 提取，行为保持搬迁）。
 *
 * 仅负责 token 用量的持久化与「回合用量卡片」落库：
 * - AssistantText 到达后异步写 token 用量（落库到 v2Agent，不阻塞循环）；
 * - TurnUsage 到达时把本回合增量 + 会话累计渲染为轻量工具卡片（toolName=usage）落库，
 *   由 Room Flow 自动驱动 UI 渲染。
 *
 * 不判定任何阈值/成本；仅把原 [AIAgentViewModel] 事件分支中的落库语句逐字搬入。
 * 异步写入复用 ViewModel 传入的同一 [scope]（即 viewModelScope）。
 */
internal class UsageStatsCollector(
    private val scope: CoroutineScope,
    private val context: Context,
    private val v2Agent: V2AgentRepository,
    private val messagePersistenceUseCase: MessagePersistenceUseCase,
) {

    /**
     * AssistantText 到达后异步记录 token 用量。
     * 与原实现逐字一致：inputTokens/outputTokens 任一 >0 时异步落库；inputTokens>0 时额外更新最近输入 tokens。
     */
    fun recordAssistantTokens(sessionId: String, inputTokens: Int, outputTokens: Int) {
        if (inputTokens > 0 || outputTokens > 0) {
            scope.launch {
                runCatching {
                    v2Agent.addTokenUsage(sessionId, inputTokens.toLong(), outputTokens.toLong())
                    if (inputTokens > 0) {
                        v2Agent.updateLastInputTokens(sessionId, inputTokens.toLong())
                    }
                }
            }
        }
    }

    /**
     * TurnUsage：回合结束展示本回合增量 + 会话累计，仅 token 不估成本。
     * 落库为轻量工具卡片（toolName=usage），Room Flow 自动驱动 UI 渲染。
     * 调用方需在调用本方法前后保持原分支对流式状态的清理顺序（本类不触碰流式 StateFlow）。
     */
    suspend fun persistTurnUsageCard(sessionId: String, event: AgentEvent.TurnUsage) {
        val msgId = "usage_${event.taskId}_${System.nanoTime()}"
        val turnText = context.getString(
            R.string.agent_usage_turn,
            event.turn.tokensIn,
            event.turn.tokensOut,
            event.turn.totalTokens,
            event.turn.toolCalls,
            event.turn.durationMs
        )
        val sessionText = context.getString(
            R.string.agent_usage_session,
            event.session.tokensIn + event.session.tokensOut,
            event.session.count
        )
        messagePersistenceUseCase.persist(
            sessionId,
            MessageRole.TOOL,
            turnText,
            id = msgId,
            taskId = event.taskId,
            toolName = "usage",
            toolArgs = sessionText,
            isError = false
        )
    }
}
