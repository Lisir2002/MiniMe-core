package com.mini.me_core.feature.agent.domain.session.trajectory

import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_trajectories as V2Trajectory
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResultTypeRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class TrajectoryAggregate(
    val tokensIn: Long,
    val tokensOut: Long,
    val count: Long,
)

/**
 * 运行轨迹服务（D2-3/D2-5，对齐 norm-chain-design.md §3.8）。
 *
 * 承载 [TrajectoryEntity] 的写入（workflow 追加 tool 轨迹与 turn/compaction/inject/error/timeout
 * 轻量标记）、用量聚合（每回合增量 + 会话累计，D2-4 数据源）与轨迹消费（已做动作摘要，D2-5）。
 * append-only：只插入不更新，删除会话时由 [com.mini.me_core.feature.agent.domain.session.SessionUseCase]
 * 级联清理。
 */
@Singleton
class TrajectoryService @Inject constructor(
    private val toolResultTypeRegistry: ToolResultTypeRegistry,
    private val v2Agent: V2AgentRepository,
) {
    private companion object {
        /** 轨迹 kind 常量（与 Entity 注释一致）。 */
        const val KIND_TOOL = "tool"
        const val KIND_TURN = "turn"
        const val KIND_COMPACTION = "compaction"
        const val KIND_INJECT = "inject"
        const val KIND_ERROR = "error"
        const val KIND_TIMEOUT = "timeout"

        /** 已做动作摘要默认条数上限（D2-5，收敛/阶段总结消费）。 */
        const val ACTION_SUMMARY_MAX_ITEMS = 12
        const val ACTION_SUMMARY_CHARS = 80
    }

    /** 轨迹摘要提取：成功走注册表定制/通用截断；失败带错误码前缀。 */
    private fun buildSummary(toolName: String, args: Map<String, JsonElement>, result: ToolResult): String {
        return when (result) {
            is ToolResult.Success -> toolResultTypeRegistry.summarize(toolName, args, result.data)
            is ToolResult.Partial -> toolResultTypeRegistry.summarize(toolName, args, result.data)
            is ToolResult.Error ->
                "❌[${result.code}] ${result.message.take(ACTION_SUMMARY_CHARS)}"
        }
    }

    private fun argsHash(args: Map<String, JsonElement>): String =
        args.toString().hashCode().toString()

    /**
     * 追加一条 tool 轨迹（workflow 每次工具执行完成后调用）。
     * 轨迹 id 用 UUID，避免跨批冲突；sessionId 为空（无会话）时跳过。
     */
    suspend fun recordTool(
        sessionId: String?,
        taskId: String?,
        turnIndex: Int,
        toolName: String,
        args: Map<String, JsonElement>,
        result: ToolResult,
        isError: Boolean,
        durationMs: Long,
        tokensIn: Int,
        tokensOut: Int
    ) {
        if (sessionId == null) return
        v2Agent.insertTrajectory(
            trajectoryId = "trj_${UUID.randomUUID().toString().replace("-", "")}",
            sessionId = sessionId,
            taskId = taskId.orEmpty(),
            turnIndex = turnIndex.toLong(),
            kind = KIND_TOOL,
            toolName = toolName,
            argsHash = argsHash(args),
            resultSummary = buildSummary(toolName, args, result),
            isError = if (isError) 1L else 0L,
            durationMs = durationMs.coerceAtLeast(0),
            tokensIn = tokensIn.toLong(),
            tokensOut = tokensOut.toLong(),
            ts = System.currentTimeMillis()
        )
    }

    /** 追加一条轻量标记（turn 边界 / 压缩 / 注入 / 错误 / 超时）。 */
    suspend fun recordMark(
        sessionId: String?,
        taskId: String?,
        turnIndex: Int,
        kind: String,
        summary: String,
        tokensIn: Int = 0,
        tokensOut: Int = 0
    ) {
        if (sessionId == null) return
        v2Agent.insertTrajectory(
            trajectoryId = "trj_${UUID.randomUUID().toString().replace("-", "")}",
            sessionId = sessionId,
            taskId = taskId.orEmpty(),
            turnIndex = turnIndex.toLong(),
            kind = kind,
            toolName = "",
            argsHash = "",
            resultSummary = summary,
            isError = 0L,
            durationMs = 0L,
            tokensIn = tokensIn.toLong(),
            tokensOut = tokensOut.toLong(),
            ts = System.currentTimeMillis()
        )
    }

    /** 本回合（taskId 分组）用量：主显每回合增量（D2-4 数据源）。 */
    suspend fun turnUsage(sessionId: String?, taskId: String?): TurnUsage {
        if (sessionId == null || taskId.isNullOrBlank()) return TurnUsage()
        val entries = v2Agent.listTrajectoriesByTask(taskId)
        if (entries.isEmpty()) return TurnUsage()
        return entries.aggregateUsage()
    }

    /** 会话累计用量：附一行累计（D2-4 数据源）。 */
    suspend fun sessionUsage(sessionId: String?): SessionUsage {
        if (sessionId == null) return SessionUsage()
        val v2 = v2Agent.getTrajectoryAggregate(sessionId)
        val agg = TrajectoryAggregate(tokensIn = v2.tokens_in, tokensOut = v2.tokens_out, count = v2.count)
        return SessionUsage(tokensIn = agg.tokensIn, tokensOut = agg.tokensOut, count = agg.count)
    }

    /** 已做动作摘要（D2-5：3.7 强制收敛返回 / Playbook 阶段总结 / 审计）。 */
    suspend fun buildActionSummary(sessionId: String?, maxItems: Int = ACTION_SUMMARY_MAX_ITEMS): String {
        if (sessionId == null) return ""
        val tools = v2Agent.listTrajectories(sessionId).filter { it.kind == KIND_TOOL }.takeLast(maxItems)
        if (tools.isEmpty()) return ""
        return tools.joinToString("\n") { t ->
            val marker = if (t.is_error != 0L) "❌" else "•"
            val dur = if (t.duration_ms > 0) " (${t.duration_ms}ms)" else ""
            "$marker ${t.tool_name}${if (t.tool_name.isNotEmpty()) ": " else ""}${t.result_summary}$dur"
        }
    }

    /** 审计回放：按会话查完整轨迹（时间升序）。 */
    suspend fun getTrajectory(sessionId: String?): List<V2Trajectory> {
        if (sessionId == null) return emptyList()
        return v2Agent.listTrajectories(sessionId)
    }

    /** 会话最近一个回合（taskId + turnIndex），供 UI 用量卡片定位。 */
    suspend fun latestTurn(sessionId: String?): Pair<String, Int>? {
        if (sessionId == null) return null
        val taskId = v2Agent.getLatestTaskId(sessionId) ?: return null
        val turnIndex = v2Agent.getMaxTurnIndex(sessionId, taskId) ?: 0
        return taskId to turnIndex
    }

    private fun List<V2Trajectory>.aggregateUsage(): TurnUsage {
        var tokensIn = 0L
        var tokensOut = 0L
        var durationMs = 0L
        var toolCalls = 0
        for (e in this) {
            tokensIn += e.tokens_in
            tokensOut += e.tokens_out
            durationMs += e.duration_ms
            if (e.kind == KIND_TOOL) toolCalls++
        }
        return TurnUsage(
            tokensIn = tokensIn,
            tokensOut = tokensOut,
            totalTokens = tokensIn + tokensOut,
            durationMs = durationMs,
            toolCalls = toolCalls
        )
    }
}

/**
 * 本回合用量（主显）：输入/输出/总 token、耗时、工具调用数。
 */
data class TurnUsage(
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val totalTokens: Long = 0,
    val durationMs: Long = 0,
    val toolCalls: Int = 0
)

/**
 * 会话累计用量（附一行累计，仅 token 不估成本）。
 */
data class SessionUsage(
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val count: Long = 0
)