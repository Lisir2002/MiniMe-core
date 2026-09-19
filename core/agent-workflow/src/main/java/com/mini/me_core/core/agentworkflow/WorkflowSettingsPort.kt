package com.mini.me_core.core.agentworkflow

/**
 * 工作流对 settings 层的反向端口（架构规则 #2 的落地）。
 *
 * 反转前：StatefulAgentWorkflow 直接依赖 feature.settings.data.repository.*（6 个具体仓储）
 * 与 feature.settings.domain.model.*（AIProviderConfig / ModelMetadata / ProviderType）。
 * 这让「纯 Kotlin 工作流内核」无法脱离 settings 模块独立成 :core:agent-workflow。
 *
 * 反转后：:core:agent-workflow 只依赖这个窄端口 + [com.mini.me_core.core.model] 的纯数据视图；
 * 具体仓储由 :app（或未来的 :feature:agent）实现并注入。查询结果用纯数据视图返回，
 * 不携带 settings 模块的具体类型，业务语义不变。
 */
interface WorkflowSettingsPort {

    // ── norm flow 规范流程开关（原 NormFlowSettingsRepository）──
    suspend fun isIdleConvergeActive(): Boolean
    suspend fun isStepInjectActive(): Boolean
    suspend fun isReasoningBudgetActive(): Boolean
    suspend fun isUsageCardActive(): Boolean
    suspend fun isToolGuardActive(): Boolean

    /**
     * F5：一次性批量读取全部「规范流程」子开关 + 总开关 AND 叠加，返回只读快照。
     *
     * 工作流每轮 CallLlm 前调用一次，后续消费点只读 [NormFlowSnapshot] 字段，
     * 不再逐个调用 [isStepInjectActive] 等单点方法（避免一轮内多次 KVStore 往返）。
     * 适配器实现：读总开关 [NormFlowSnapshot.stepInject] 等已含总开关 AND 叠加结果。
     */
    suspend fun loadNormFlowSnapshot(): NormFlowSnapshot

    // ── 兼容端点 / 自动降级（原 CompatibilityPolicyRepository）──
    suspend fun isAutoDowngradeOnSendFailure(): Boolean

    /**
     * viewImage 未收录模型的守卫策略。
     * @return "FALLBACK_VISION_MODEL" 或 "FAIL_FAST"（对齐原 ViewImageUnknownGuardPolicy 枚举名）。
     */
    suspend fun getViewImageUnknownGuardPolicyRaw(): String

    // ── 识图 / 压缩备用模型（原 VisionModelSettingsRepository / CompactionModelSettingsRepository）──
    suspend fun getVisionProviderId(): String
    suspend fun getVisionModel(): String
    suspend fun getCompactionProviderId(): String
    suspend fun getCompactionModel(): String

    /**
     * 按 provider 类型 + 模型名解析模型元数据，只取工作流需要的纯视图。
     * @param providerTypeRaw ProviderType 的 name()（OPENAI / ANTHROPIC / GEMINI …）。
     */
    suspend fun resolveModelMetadata(providerTypeRaw: String, model: String): ModelMetadataView?
}

/**
 * 模型元数据的纯视图（对应 feature.settings.domain.model.ModelMetadata 的 supportsVision 等）。
 */
data class ModelMetadataView(
    val supportsVision: Boolean,
)
