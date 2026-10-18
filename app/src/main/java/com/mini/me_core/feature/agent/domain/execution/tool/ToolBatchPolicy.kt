package com.mini.me_core.feature.agent.domain.execution.tool

/**
 * C2 批量工具执行策略（批次 C：并行调用强化，方案见 docs/tool-orchestration-optimization.md）。
 *
 * 三条执行纪律（业界生产实践：只并行真正独立的调用 + batch 上限 + 每个 call ID 都有结果）：
 * 1. **batch 上限**：模型单回合返回超过 [MAX_CALLS_PER_BATCH] 个调用时按 chunk 分批执行，
 *    防一次几十个调用打爆下游（容器 CPU / 网络 / 权限弹窗队列）。
 * 2. **写操作同层串行**：拓扑分层后，若某层任一调用具备写操作能力（WRITE_WORKSPACE /
 *    EXECUTE_COMMANDS），该层降为串行执行，保写顺序与文件系统一致性。
 * 3. **partial failure**：每个 call ID 都必须有结果——单调用抛异常不连坐同层其余调用；
 *    依赖调用已失败的后续调用标记跳过（DEPENDENCY_FAILED），不盲跑。
 *
 * 纯函数组件，零 Android/协程依赖，可 JVM 单测；执行编排在 StatefulAgentWorkflow。
 */
object ToolBatchPolicy {

    /** 单回合执行上限：超出按 chunk 分批（每 chunk 独立走依赖调度）。 */
    const val MAX_CALLS_PER_BATCH: Int = 8

    /** 写操作能力集：命中任一的调度层降为串行执行。 */
    private val WRITE_CAPABILITIES = setOf(
        ToolCapability.WRITE_WORKSPACE,
        ToolCapability.EXECUTE_COMMANDS
    )

    /** 依赖调用失败时后续调用的跳过错误码（partial failure 语义）。 */
    const val DEPENDENCY_FAILED_CODE = "DEPENDENCY_FAILED"

    /** 把单回合调用切分为不超过 [MAX_CALLS_PER_BATCH] 的 chunk；空列表返回空。 */
    fun chunkCalls(calls: List<ToolCall>): List<List<ToolCall>> {
        if (calls.isEmpty()) return emptyList()
        return calls.chunked(MAX_CALLS_PER_BATCH)
    }

    /** 工具是否具备写操作能力（未注册工具视为只读，不影响层串行判定）。 */
    fun hasWriteCapability(tool: AgentTool?): Boolean {
        return tool != null && tool.capabilities.any { it in WRITE_CAPABILITIES }
    }

    /** 层内任一调用具备写操作能力 → 该层须串行执行。 */
    fun layerRequiresSerialization(layer: List<ToolCall>, registry: ToolRegistry): Boolean {
        return layer.any { hasWriteCapability(registry.getTool(it.name)) }
    }

    /**
     * partial failure：调用的任一依赖已产生错误结果 → 应跳过（返回失败依赖的 callId），否则返回 null。
     *
     * 依赖图由拓扑分层保证：依赖必在更早层且已执行完毕，故 [isFailed] 读取结果无竞态。
     *
     * @param dependencies callId -> 依赖的 callId 集合（调度计划提供）
     * @param isFailed 判定某 callId 的结果是否为错误
     */
    fun failedDependency(
        call: ToolCall,
        dependencies: Map<String, Set<String>>,
        isFailed: (String) -> Boolean
    ): String? {
        val depIds = dependencies[call.id] ?: return null
        return depIds.firstOrNull { isFailed(it) }
    }
}
