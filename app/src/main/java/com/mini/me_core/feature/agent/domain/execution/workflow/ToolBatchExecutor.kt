package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.core.guard.FileObservationGuard
import com.mini.me_core.feature.agent.domain.core.guard.GuardLogRepository
import com.mini.me_core.feature.agent.domain.core.guard.ToolGuard
import com.mini.me_core.feature.agent.domain.core.guard.ToolGuardContext
import com.mini.me_core.feature.agent.domain.core.guard.ToolGuardResult
import com.mini.me_core.feature.agent.domain.core.model.AgentContext
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.StreamingAgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import com.mini.me_core.feature.agent.domain.execution.tool.ToolOutputStore
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResultCache
import com.mini.me_core.feature.agent.domain.execution.tool.ToolStreamEvent
import com.mini.me_core.feature.agent.domain.execution.tool.classifyError
import com.mini.me_core.feature.agent.domain.execution.tool.toTransportString
import com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ViewImageUnknownGuardPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 工具批量执行叶子（ 从 StatefulAgentWorkflow 整体搬迁的叶子协作者）。
 *
 * 职责：单个工具的六段式执行流水线（pre-execute 缓存 → guard 链 → execute → post-execute 媒体剥离
 * /文件观察 → finalizeContent 定型 → 只读副作用）、流式工具的聚合进度发布、D1-3 护栏链遍历短路。
 *
 * 边界（行为保持）：批级并发骨架（dependencyScheduler.buildSchedule / 拓扑分层 / awaitAll /
 * 耗时统计 / 结果按序回收）仍留在主类编排；本类只负责「单工具怎么执行出 [ToolRunResult]」。
 * 权限判定（policyEngine / permissionManager）仍由主类 requestPermissionIfNeeded 完成，本类不触碰。
 *
 * @Suppress LongParameterList：聚合单工具执行所需的多数据源（缓存/输出仓/设置/护栏链），
 *   由主类已注入对象经构造传入，属装配签名保持，不拆分额外间接层。
 * @Suppress ClassOrdering：协作者按「执行流水线方法分组」组织，成员顺序调整不影响行为（行为保持）。
 * @Suppress CognitiveComplexMethod, LongMethod, ReturnCount：单工具六段流水线含多个早退分支，
 *   为行为保持整体搬迁（控制流逐字等价），不重排分支顺序以免改变返回/异常语义。
 * @Suppress CyclomaticComplexMethod：同上，分支密度随六段流水线整体搬迁，不拆私有方法以免改变失败结论。
 */
@Suppress("LongParameterList", "ClassOrdering")
internal class ToolBatchExecutor(
    private val toolResultCache: ToolResultCache,
    private val compatibilityPolicyRepository: CompatibilityPolicyRepository,
    private val normFlowSettingsRepository: NormFlowSettingsRepository,
    private val toolOutputStore: ToolOutputStore,
    private val fileObservationGuard: FileObservationGuard,
    private val toolGuards: Set<@JvmSuppressWildcards ToolGuard>,
    private val guardLogRepository: GuardLogRepository,
    private val modelCallRouter: ModelCallRouter,
    private val resultSanitizer: ResultSanitizer,
    private val eventTracer: EventTracer
) {

    private companion object {
        // 与搬迁前主类日志 tag 保持一致，避免日志归类漂移。
        const val TAG = "StatefulAgentWorkflow"

        /** D1-4 文件观察纪律：仅 agent 文件工具链（readFile/writeFile/editFile）生效。 */
        val FILE_OBSERVED_TOOLS = setOf("readFile", "writeFile", "editFile")

        const val LIVE_TAIL_CHARS = 4_000
        const val PROGRESS_INTERVAL_MS = 250L
    }

    // TooGenericExceptionCaught：六段流水线任意异常都需统一收口为 TOOL_EXECUTION_FAILED 结论（含 e.message），
    //   与搬迁前失败结论逐字一致；CancellationException 已先行 rethrow 不被吞。
    @Suppress("CognitiveComplexMethod", "LongMethod", "ReturnCount", "CyclomaticComplexMethod", "TooGenericExceptionCaught")
    suspend fun runSync(tool: AgentTool?, toolCall: ToolCall, context: AgentContext): StatefulAgentWorkflow.ToolRunResult {
        val name = toolCall.name
        if (tool == null) {
            val error = ToolResult.Error("工具 $name 不存在", "TOOL_NOT_FOUND")
            eventTracer.recordSessionOutput(context, toolCall.id, name, error)
            return StatefulAgentWorkflow.ToolRunResult(error.toTransportString(), true, errorClass = classifyError(error.code))
        }
        return try {
            // ── pre-execute 段（D1-5，六段式流水线契约：pre-execute → guard → execute →
            //    post-execute → finalizeContent → result）：门——执行前可短路/变换的入口。
            //    ① L5 结果缓存：纯读工具（readFile/search/list）按 (toolName, argsHash) 键控，
            //    命中则直接复用结果（短路返回）；文件类工具由 mtime + TTL 双机制失效。
            //    ② viewImage 守卫：当前模型不支持多模态时自动降级识图模型 / FAIL_FAST。
            val cacheKey = if (name in ToolResultCache.FILE_TOOLS) {
                context.sessionId?.let { sid -> toolResultCache.buildKey(name, toolCall.arguments, sid) }
            } else {
                null
            }
            if (cacheKey != null) {
                val cached = toolResultCache.get(cacheKey)
                if (cached != null) {
                    val cachedTransport = cached.toTransportString()
                    eventTracer.recordSessionOutput(context, toolCall.id, name, cached)
                    eventTracer.recordIncrementalAction(name, cachedTransport, context)
                    val errorClass = (cached as? ToolResult.Error)?.let { classifyError(it.code) }
                    return StatefulAgentWorkflow.ToolRunResult(cachedTransport, cached is ToolResult.Error, errorClass = errorClass)
                }
            }
            if (name == "viewImage" && !modelCallRouter.activeModelSupportsVision(context.sessionId)) {
                // 备选方案③：viewImage 守卫策略（默认自动回退识图模型，FAIL_FAST 则直接报错提示用户）
                val guardPolicy = compatibilityPolicyRepository.getViewImageUnknownGuardPolicy()
                val fallbackReady = modelCallRouter.visionFallbackReady()
                if (guardPolicy == ViewImageUnknownGuardPolicy.FAIL_FAST || !fallbackReady) {
                    val error = ToolResult.Error(
                        "当前聊天模型不支持「多模态识图（Vision）」能力，且未配置专用的「多模态识图（Vision）」兜底模型。请在【设置 → 默认模型 → 识图模型】中指定一个支持「多模态识图（Vision）」能力的模型后再查看图片。\n" +
                            "💡 你也可以在【设置 → AI 提供商 → 该模型 → 能力覆盖】中手动开启「多模态识图（Vision）」复选框，一步修复。",
                        "MODEL_VISION_UNSUPPORTED"
                    )
                    eventTracer.recordSessionOutput(context, toolCall.id, name, error)
                    return StatefulAgentWorkflow.ToolRunResult(
                        error.toTransportString(),
                        true,
                        errorClass = classifyError(error.code)
                    )
                }
                // 非多模态模型：同步用识图模型理解图片，文本结果直接作为工具返回
                val result = tool.executeWithContext(toolCall.arguments, context)
                val userPrompt = (toolCall.arguments["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
                val textResult = resultSanitizer.runVisionFallback(result, context.sessionId, userPrompt)
                val processed = toolOutputStore.process(name, toolCall.id, ToolResult.Success(JsonObject(mapOf(
                    "content" to JsonPrimitive(textResult),
                    "model" to JsonPrimitive("vision-fallback")
                ))))
                eventTracer.recordSessionOutput(context, toolCall.id, name, processed)
                return StatefulAgentWorkflow.ToolRunResult(processed.toTransportString(), processed is ToolResult.Error, emptyList())
            }
            // ── guard 段（D1-3，六段式流水线契约）：execute 前遍历护栏链，首个 BLOCK 短路 ──
            // 护栏链：权限检查（workflow 层既有）→ 文件观察（本批新增）→ 危险命令/超时钳制
            // （ExecuteCommandTool 工具层既有，保持原位，经本判定契约对齐）。
            // D1-7 统一开关：总开关 norm_flow_enabled 或子开关 tool_guard 关闭时跳过护栏链
            // （对齐 norm-chain §3.5，默认开启；关闭即 guard 链 + 文件观察不生效）。
            if (normFlowSettingsRepository.isToolGuardActive()) {
                runToolGuards(name, toolCall, context)?.let { blocked ->
                    eventTracer.recordSessionOutput(context, toolCall.id, name, blocked)
                    eventTracer.recordIncrementalAction(name, blocked.toTransportString(), context)
                    val errorClass = classifyError(blocked.code)
                    return StatefulAgentWorkflow.ToolRunResult(blocked.toTransportString(), true, errorClass = errorClass)
                }
            }
            // ── execute 段：执行工具（含流式工具的聚合兜底） ──
            val result = tool.executeWithContext(toolCall.arguments, context)
            // ── post-execute 段：可改写结果——媒体（图片/附件）从正文剥离走独立通道，
            //    以 text 形态进入结果定型；文件观察版本更新（markObserved）亦在本段（见下）。
            val images = if (name == "viewImage") resultSanitizer.extractInlineImages(result) else emptyList()
            val attachments = if (name == "sendFile") resultSanitizer.extractAttachments(result) else emptyList()
            val transportResult = when {
                images.isNotEmpty() -> resultSanitizer.stripInlineImages(result)
                attachments.isNotEmpty() -> resultSanitizer.stripAttachments(result)
                else -> result
            }
            // ── finalizeContent 段：结果定型——toolOutputStore.process 统一为 canonical 形态
            //    （对齐 RunCodeTool 的 canonical JSON 输出语义），供下游只读消费。
            val processed = toolOutputStore.process(name, toolCall.id, transportResult)
            eventTracer.recordSessionOutput(context, toolCall.id, name, processed)
            // L5 写缓存：仅成功结果入缓存，失败不缓存（避免缓存错误）。
            if (cacheKey != null && processed is ToolResult.Success) {
                toolResultCache.put(cacheKey, processed)
            }
            // L7 事件发布：工具成功后广播对应事件，驱动缓存失效、上下文增量刷新等联动。
            // ── post-execute 段：文件观察版本更新（D1-4，readFile/writeFile/editFile 成功后记录当前 mtime，
            //    使「写入即已知」成立、避免「自己刚改过又被拦」；失败静默降级不阻断主流程）。
            if (processed is ToolResult.Success) {
                if (name in FILE_OBSERVED_TOOLS) {
                    (toolCall.arguments["path"] as? JsonPrimitive)?.contentOrNull?.let { fileObservationGuard.markObserved(it) }
                }
                eventTracer.publishToolEvent(name, toolCall, processed, context)
            }
            // ── result 段：只读观测——写结果缓存（仅成功）、发布工具事件、记录增量索引，
            //    均为不改变结果本体的只读副作用（side-effect），六段契约到此结束。
            // L6 增量索引：记录工具动作摘要（成功与失败均记录，作为状态变化）。
            // 复用下方 transportString，避免对完整结果重复序列化。
            val transportString = processed.toTransportString()
            eventTracer.recordIncrementalAction(name, transportString, context)
            val errorClass = (processed as? ToolResult.Error)?.let { classifyError(it.code) }
            StatefulAgentWorkflow.ToolRunResult(transportString, processed is ToolResult.Error, images, attachments, errorClass)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = ToolResult.Error("工具执行失败: ${e.message}", "TOOL_EXECUTION_FAILED")
            eventTracer.recordSessionOutput(context, toolCall.id, name, error)
            StatefulAgentWorkflow.ToolRunResult(error.toTransportString(), true, errorClass = classifyError(error.code))
        }
    }

    /**
     * D1-3 guard 段：遍历护栏链，首个 [ToolGuardResult.Block] 短路（工具不执行）；
     * [ToolGuardResult.Advisory] 不阻断，记录日志（提醒队列，供上层注入给模型）。
     * 单个护栏异常静默按放行处理，不阻断主流程。
     * @return 非 null = 被某护栏拦截，调用方直接以该错误返回模型。
     *
     * @Suppress TooGenericExceptionCaught：单个护栏异常按放行处理（搬迁前语义），cause 已由
     *   FileLogger.w 记录不丢失；LoopWithTooManyJumpStatements：护栏链需顺序短路/跳过，提前终止是契约。
     */
    @Suppress("TooGenericExceptionCaught", "LoopWithTooManyJumpStatements")
    private suspend fun runToolGuards(
        name: String,
        toolCall: ToolCall,
        context: AgentContext
    ): ToolResult.Error? {
        val fileObservationActive = normFlowSettingsRepository.isFileObservationActive()
        for (guard in toolGuards) {
            // 文件观察护栏独立开关关闭时跳过该护栏
            if (guard.id == "file-observation" && !fileObservationActive) continue
            val verdict = try {
                guard.guard(
                    ToolGuardContext(
                        toolName = name,
                        args = toolCall.arguments,
                        sessionId = context.sessionId,
                        projectRoot = context.projectRoot
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FileLogger.w(TAG, "护栏 ${guard.id} 判定异常，按放行处理", e)
                continue
            }
            when (verdict) {
                is ToolGuardResult.Block -> {
                    FileLogger.w(TAG, "护栏 ${guard.id} 拦截 $name: ${verdict.code} ${verdict.message}")
                    guardLogRepository.log(
                        com.mini.me_core.feature.agent.domain.core.guard.GuardLogEntry(
                            timestamp = System.currentTimeMillis(),
                            toolName = name,
                            guardId = guard.id,
                            code = verdict.code,
                            message = verdict.message
                        ),
                        isBlock = true
                    )
                    return ToolResult.Error(verdict.message, verdict.code)
                }
                is ToolGuardResult.Advisory -> {
                    FileLogger.w(TAG, "护栏 ${guard.id} 提醒 $name: ${verdict.code} ${verdict.message}")
                    guardLogRepository.log(
                        com.mini.me_core.feature.agent.domain.core.guard.GuardLogEntry(
                            timestamp = System.currentTimeMillis(),
                            toolName = name,
                            guardId = guard.id,
                            code = verdict.code,
                            message = verdict.message
                        )
                    )
                }
                ToolGuardResult.Pass -> Unit
            }
        }
        return null
    }

    // TooGenericExceptionCaught：流式聚合任意异常统一收口为 TOOL_EXECUTION_FAILED（含 e.message），
    //   与搬迁前失败结论逐字一致；CancellationException 已先行 rethrow。
    @Suppress("TooGenericExceptionCaught")
    suspend fun runStream(
        tool: StreamingAgentTool,
        toolCall: ToolCall,
        context: AgentContext,
        onEvent: suspend (AgentEvent) -> Unit
    ): StatefulAgentWorkflow.ToolRunResult {
        val live = StringBuilder()
        var lastEmitMs = 0L
        var finalResult: ToolResult? = null
        try {
            tool.executeStream(toolCall.arguments, context).collect { ev ->
                when (ev) {
                    is ToolStreamEvent.Progress -> {
                        live.append(ev.chunk).append('\n')
                        if (live.length > LIVE_TAIL_CHARS) {
                            live.delete(0, live.length - LIVE_TAIL_CHARS)
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= PROGRESS_INTERVAL_MS) {
                            lastEmitMs = now
                            onEvent(AgentEvent.ToolCallProgress(toolCall.id, toolCall.name, live.toString()))
                        }
                    }
                    is ToolStreamEvent.Completed -> finalResult = ev.result
                }
            }
            val result = finalResult ?: ToolResult.Error("流式工具未返回结果", "MISSING_STREAM_RESULT")
            val processed = toolOutputStore.process(toolCall.name, toolCall.id, result)
            // L7 事件发布：流式工具（如 Bash）成功后同样广播事件，驱动缓存失效等联动。
            if (processed is ToolResult.Success) {
                eventTracer.publishToolEvent(toolCall.name, toolCall, processed, context)
            }
            val errorClass = (processed as? ToolResult.Error)?.let { classifyError(it.code) }
            return StatefulAgentWorkflow.ToolRunResult(processed.toTransportString(), processed is ToolResult.Error, errorClass = errorClass)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = ToolResult.Error("工具执行失败: ${e.message}", "TOOL_EXECUTION_FAILED")
            return StatefulAgentWorkflow.ToolRunResult(error.toTransportString(), true, errorClass = classifyError(error.code))
        }
    }
}
