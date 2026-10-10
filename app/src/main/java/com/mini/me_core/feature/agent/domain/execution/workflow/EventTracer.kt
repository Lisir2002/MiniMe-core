package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.feature.agent.domain.core.model.AgentContext
import com.mini.me_core.feature.agent.domain.execution.tool.ActionIndex
import com.mini.me_core.feature.agent.domain.execution.tool.IncrementalIndexStore
import com.mini.me_core.feature.agent.domain.execution.tool.RoundSnapshot
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import com.mini.me_core.feature.agent.domain.execution.tool.ToolEventBus
import com.mini.me_core.feature.agent.domain.execution.tool.ToolOutputRecord
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.domain.execution.tool.classifyError
import kotlinx.serialization.json.JsonPrimitive

/**
 * 事件落库/索引/时间线记录（ 从 StatefulAgentWorkflow 整体搬迁的叶子协作者）。
 *
 * 职责：L6 增量索引（动作摘要/轮次快照）、L2 会话共享产物记录（callId 索引，供后续工具直引）、
 * L7 工具成功后的事件总线发布（驱动缓存失效/增量刷新联动）。均为只读副作用叶子，
 * 截断长度、字段、调用形态与搬迁前逐字一致（行为保持搬迁）。
 *
 * @Suppress ClassOrdering：协作者按「落库方法分组」组织，成员顺序调整不影响行为（行为保持）。
 */
@Suppress("ClassOrdering")
internal class EventTracer(
    private val incrementalIndexStore: IncrementalIndexStore,
    private val toolRegistry: ToolRegistry,
    private val toolEventBus: ToolEventBus
) {

    private companion object {
        /** L6 增量索引：动作摘要截断长度，避免大结果全量写入内存索引。 */
        const val SUMMARY_CHARS = 512
    }

    /**
     * L6 增量索引：记录工具动作摘要。
     *
     * 性能关键：接收已序列化的 transportString（调用方已序列化一次，
     * 此处复用避免对完整结果重复 toTransportString）；data 只存截断摘要，避免内存膨胀。
     */
    suspend fun recordIncrementalAction(name: String, transportString: String, context: AgentContext) {
        val sessionId = context.sessionId ?: return
        val summary = transportString.take(SUMMARY_CHARS)
        incrementalIndexStore.recordAction(
            sessionId,
            ActionIndex(
                actionType = name,
                data = JsonPrimitive(summary),
                dataHash = transportString.hashCode().toString()
            )
        )
    }

    /**
     * L6 增量索引：记录本批轮次快照并触发持久化（轮次级索引）。
     */
    suspend fun recordIncrementalRound(context: AgentContext, batchResults: List<StatefulAgentWorkflow.ToolBatchResult>) {
        val sessionId = context.sessionId ?: return
        if (batchResults.isEmpty()) return
        val actions = batchResults.map { br ->
            ActionIndex(
                actionType = br.toolName,
                data = JsonPrimitive(br.result.take(SUMMARY_CHARS)),
                dataHash = br.result.hashCode().toString()
            )
        }
        // O(1) 读取输出计数作为轮次号，避免每次全量遍历 outputs。
        val round = context.sessionState?.outputCount ?: 0
        incrementalIndexStore.recordRound(
            sessionId,
            RoundSnapshot(round, actions, "本批 ${actions.size} 个工具动作")
        )
        incrementalIndexStore.persist(sessionId)
    }

    /**
     * L2 共享会话状态：把单次工具执行结果写入 [ToolSessionState.outputs]（callId 索引），
     * 供后续工具按 callId 直连引用前序产物。成功记录 errorClass=null，失败按 [classifyError] 分类。
     */
    // 非 suspend：与搬迁前一致，纯内存写入，不切线程。
    fun recordSessionOutput(context: AgentContext, callId: String, toolName: String, result: ToolResult) {
        val state = context.sessionState ?: return
        val errorClass = if (result is ToolResult.Error) classifyError(result.code) else null
        state.recordOutput(
            ToolOutputRecord(
                callId = callId,
                toolName = toolName,
                result = result,
                errorClass = errorClass
            )
        )
    }

    /**
     * L7 事件发布（事件解耦 M1）：工具成功后广播对应事件，驱动缓存失效、上下文增量刷新等联动。
     *
     * 事件全部由工具自身经 [ToolRegistry] 注册工具的 buildPostExecutionEvent 钩子声明，
     * 本方法只解析工具实例、统一查询钩子并发布，不再感知具体工具名；hash/diff 等字段级详情由工具侧组装。
     */
    suspend fun publishToolEvent(name: String, toolCall: ToolCall, result: ToolResult, context: AgentContext) {
        val tool = toolRegistry.getTool(name) ?: return
        tool.buildPostExecutionEvent(toolCall, result, context)?.let { toolEventBus.publish(it) }
    }
}
