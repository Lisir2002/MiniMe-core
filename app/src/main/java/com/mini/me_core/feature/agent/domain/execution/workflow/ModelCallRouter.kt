package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.core.network.ModelEndpointRouter
import com.mini.me_core.feature.agent.data.remote.anthropic.AnthropicApi
import com.mini.me_core.feature.agent.data.remote.gemini.GeminiApi
import com.mini.me_core.feature.agent.data.remote.openai.OpenAIApi
import com.mini.me_core.feature.agent.domain.core.provider.AIProvider
import com.mini.me_core.feature.agent.domain.core.provider.AnthropicAdapter
import com.mini.me_core.feature.agent.domain.core.provider.GeminiAdapter
import com.mini.me_core.feature.agent.domain.core.provider.OpenAIAdapter
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient

/**
 * 模型调用路由（ 从 StatefulAgentWorkflow 整体搬迁的叶子协作者）。
 *
 * 职责：解析当前生效/备用/识图/压缩专用 provider 配置、按 needsProxy 装配独立 [AIProvider] 实例、
 * 查询模型视觉能力与识图兜底可用性。纯路由叶子——不触碰状态机、不发事件、不执行工具，
 * 所有判定入参与结论处理与搬迁前逐字一致（行为保持搬迁）。
 *
 * @Suppress LongParameterList：聚合模型路由所需的多数据源（仓库/API 客户端/设置），
 *   由主类已注入对象经构造传入，属装配签名保持，不拆分为额外间接层。
 * @Suppress ClassOrdering：协作者按「路由方法分组」组织，成员顺序调整不影响行为（行为保持）。
 */
@Suppress("LongParameterList", "ClassOrdering")
internal class ModelCallRouter(
    private val aiProviderRepository: AIProviderRepository,
    private val sessionUseCase: SessionUseCase,
    private val openAIApi: OpenAIApi,
    private val anthropicApi: AnthropicApi,
    private val geminiApi: GeminiApi,
    private val okHttpClient: OkHttpClient,
    private val proxyOkHttpClient: OkHttpClient,
    private val modelMetadataService: ModelMetadataService,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
    /** 内置多线路选路：为用户配置 URL 排序候选端点（无健康数据时原序=用户配置，存量行为不变）。 */
    private val endpointRouter: ModelEndpointRouter
) {

    private companion object {
        // 与搬迁前主类日志 tag 保持一致，避免日志归类漂移。
        const val TAG = "StatefulAgentWorkflow"
    }

    suspend fun getActiveProvider(sessionId: String?): AIProvider {
        return getActiveProviderWithConfig(sessionId).first
    }

    /**
     * 同 [getActiveProvider]，但同时返回 [AIProviderConfig]，供工作流读取 fallbackProviderId 等配置字段。
     *
     * @Suppress UseCheckOrError：搬迁来的 `throw IllegalStateException(...)` 写法，改为 check/error 会改变
     *   异常类型或文案，属行为保持搬迁（异常类型/文案逐字保留）。
     */
    @Suppress("UseCheckOrError")
    suspend fun getActiveProviderWithConfig(sessionId: String?): Pair<AIProvider, AIProviderConfig> {
        val config = resolveProviderConfig(sessionId)
            ?: throw IllegalStateException("尚未配置 AI 提供商，请到设置中添加并选择一个")
        if (config.apiKey.isBlank()) throw IllegalStateException("「${config.name}」未填写 API Key")
        if (config.effectiveModel.isBlank()) throw IllegalStateException("「${config.name}」未选择模型")
        return createStandaloneProvider(config, sessionId) to config
    }

    /**
     * 供应商故障转移：根据 [fallbackProviderId] 创建备用 provider 实例。
     * 备用 provider 的配置独立从数据库读取，不继承主 provider 的运行时状态。
     * 返回 null 表示备用 provider 不存在、被禁用或无 API Key，不应切换。
     */
    suspend fun resolveFallbackProvider(fallbackId: String, sessionId: String?): AIProvider? {
        if (fallbackId.isBlank()) return null
        val fallbackConfig = aiProviderRepository.getProviderById(fallbackId) ?: return null
        // 等价合并原「禁用 / 无 apiKey / 无有效模型」三道早退：任一不满足即不切换，结论不变。
        if (!fallbackConfig.isEnabled || fallbackConfig.apiKey.isBlank() || fallbackConfig.effectiveModel.isBlank()) {
            return null
        }
        return runCatching { createStandaloneProvider(fallbackConfig, sessionId) }.getOrNull()
    }

    /**
     * 解析当前生效的 provider 配置：优先用 session 绑定的 providerId/model，回退全局 active provider。
     * session 绑定的 provider 不存在或已禁用时回退全局，保证老会话与异常数据不中断。
     */
    suspend fun resolveProviderConfig(sessionId: String?): AIProviderConfig? {
        if (sessionId != null) {
            val session = sessionUseCase.getSessionById(sessionId)
            val boundProviderId = session?.provider_id
            val boundModel = session?.model
            if (!boundProviderId.isNullOrBlank()) {
                val config = aiProviderRepository.getProviderById(boundProviderId)
                if (config != null && config.isEnabled && config.apiKey.isNotBlank()) {
                    return if (!boundModel.isNullOrBlank()) config.copy(selectedModel = boundModel) else config
                }
            }
        }
        return aiProviderRepository.getActiveProviderSync()
    }

    /**
     * 根据 [config] 创建一个全新的、独立的 [AIProvider] 实例。
     * 用于识图回退和上下文压缩等独立请求场景，完全不占用或修改主对话所用的 Provider 单例。
     *
     * 代理隔离：根据 [AIProviderConfig.needsProxy] 选择网络客户端。
     * - needsProxy=true → proxyOkHttpClient（走 mihomo 代理，含健康降级与熔断）
     * - needsProxy=false → okHttpClient（强制直连，不受系统代理污染）
     */
    suspend fun createStandaloneProvider(config: AIProviderConfig, sessionId: String?): AIProvider {
        // 代理路由选择：needsProxy 开关真正生效，决定模型请求走直连还是代理。
        val useProxy = config.needsProxy
        val clientForProvider = if (useProxy) proxyOkHttpClient else okHttpClient
        // 端点选路（内置多线路）：无健康数据时返回用户配置 URL（原序，存量行为零变化）；
        // 用户配置端点熔断中才改选同类型官方端点（同一把 Key，不引入第三方线路）。
        // useFullUrl=true 时用户填的是完整请求地址，选路直通不换（见 ModelEndpointRouter）。
        val chosenBaseUrl = endpointRouter.selectEndpoint(config)
        // 代理路由日志：记录每个模型的网络出口，便于排查"代理污染模型接口"类问题。
        FileLogger.i(
            TAG,
            "模型网络路由: name=${config.name} model=${config.effectiveModel} " +
                "baseUrl=${config.baseUrl} endpoint=$chosenBaseUrl needsProxy=$useProxy → ${if (useProxy) "代理出口(mihomo)" else "直连(NO_PROXY)"}"
        )
        val provider: AIProvider = when (config.type) {
            ProviderType.ANTHROPIC -> AnthropicAdapter(anthropicApi, clientForProvider)
            ProviderType.GEMINI -> GeminiAdapter(geminiApi, clientForProvider)
            else -> OpenAIAdapter(openAIApi, clientForProvider)
        }
        provider.apiKey = config.apiKey
        provider.baseUrl = chosenBaseUrl
        provider.model = config.effectiveModel
        provider.useFullUrl = config.useFullUrl
        provider.useResponseApi = config.useResponseApi
        // 模型级采样参数覆盖：严格优先级 模型级(非null) > 供应商级默认。
        // 只读 providerConfig，绝不修改；解析结果只设置到当前 provider 实例。
        val samplingConfig = runCatching {
            modelMetadataService.observeSamplingConfig(config.type, config.effectiveModel).firstOrNull()
        }.getOrNull()
        val resolved = modelMetadataService.resolveSamplingParams(config, samplingConfig)
        provider.temperature = resolved.temperature
        provider.topP = resolved.topP
        provider.maxTokens = resolved.maxTokens
        provider.apiPath = config.apiPath
        provider.requestTimeout = config.requestTimeout
        provider.retryCount = config.retryCount
        provider.logSessionId = sessionId
        return provider
    }

    /**
     * 当前聊天模型是否「有原生能力支持多模态」。
     *
     * 影响两条关键链路：
     *  (1) `runToolSync(name=="viewImage")` 的守卫：
     *      if (!activeModelSupportsVision(...)) → 直接抛「当前聊天模型不支持图片输入」
     *  (2) `pendingVisionRound` 是否启用独立识图模型 fallback 的判定入口。
     *
     *  判定链优先级（从高到低）：
     *  - ④ 单模型复选框手动覆盖（ModelCapabilityOverrideDao，在 resolve 内处理）；
     *  - catalog 明确 supportsVision=true → 支持；
     *  - catalog 明确 supportsVision=false（MODELS_DEV 收录的纯文本模型）→ 不支持；
     *  - ③ 兼容端点默认策略 Repository（STRICT/HEURISTIC/LAX/MANUAL，resolve 内处理）；
     *  - probablyVision 启发式（step- 家族白名单在 ModelMetadataService.default() 命中即为 true）。
     */
    suspend fun activeModelSupportsVision(sessionId: String?): Boolean {
        val config = resolveProviderConfig(sessionId) ?: return false
        val metadata = modelMetadataService.resolve(config.type, config.effectiveModel)
        return metadata.supportsVision
    }

    /**
     * 发送前是否应该把图片带给模型。
     * 判定链与 [activeModelSupportsVision] 严格一致；
     * 2026-08-10 按用户要求**撤销 的 `source==INFERRED → 一律 true` 全局放宽**，
     * 恢复 之前的严格语义。未收录兼容端点模型仅当 probablyVision 启发式
     * （或③兼容端点策略 / ④单模型覆盖）命中时才会带图发送，避免全局放宽的副作用。
     */
    suspend fun shouldSendImages(sessionId: String?): Boolean {
        val config = resolveProviderConfig(sessionId) ?: return false
        val metadata = modelMetadataService.resolve(config.type, config.effectiveModel)
        return metadata.supportsVision
    }

    /**
     * 识图专用兜底模型是否可用：已配置 providerId 且指向的 provider/model 存在、有 apiKey、且其
     * ModelMetadata.supportsVision 为真。识图轮仅当 [activeModelSupportsVision] 为 false 时才回退到它。
     * 未配置（providerId 空）即视为「跟随聊天模型」，不构成兜底 → 返回 false。
     */
    suspend fun visionFallbackReady(): Boolean {
        val providerId = visionModelSettingsRepository.getVisionProviderId().trim()
        val model = visionModelSettingsRepository.getVisionModel().trim()
        // 等价合并原「providerId 空 / model 空」两道早退：任一未配置即无兜底，结论不变。
        if (providerId.isEmpty() || model.isEmpty()) return false
        val config = aiProviderRepository.getProviderById(providerId) ?: return false
        // 等价合并原「未启用 / 无 apiKey」两道早退：任一不满足即不可用，结论不变。
        if (!config.isEnabled || config.apiKey.isBlank()) return false
        val metadata = modelMetadataService.resolve(config.type, model)
        return metadata.supportsVision
    }

    /**
     * 识图轮专用 provider 解析。仅当当前聊天模型不支持 vision、且专用模型已配置且可用时返回
     * 全新的独立 AIProvider 实例；否则返回 null（表示无需切换、沿用 aiProvider）。
     *
     * @Suppress UseCheckOrError：搬迁来的 `error(...)` 写法，改为 check/error 会改变异常类型或文案，
     *   属行为保持（异常类型/文案逐字保留）。
     */
    @Suppress("UseCheckOrError")
    suspend fun resolveVisionFallbackProvider(sessionId: String?): AIProvider? {
        if (activeModelSupportsVision(sessionId)) return null // 当前聊天模型就有原生能力，直接用之
        if (!visionFallbackReady()) return null       // 无可用兜底，仍沿用 aiProvider（守卫已先行拦截并报错）
        val providerId = visionModelSettingsRepository.getVisionProviderId().trim()
        val model = visionModelSettingsRepository.getVisionModel().trim()
        val config = aiProviderRepository.getProviderById(providerId)
            ?: error("识图专用模型配置丢失")
        if (config.apiKey.isBlank()) error("识图专用模型「${config.name}」未填写 API Key")
        if (model.isBlank()) error("识图专用模型未指定模型")
        return createStandaloneProvider(config.copy(selectedModel = model), sessionId)
    }

    /**
     * 压缩轮专用 provider 解析。若用户配置了压缩专用模型且 provider 存在、已启用、有 apiKey，
     * 则返回全新的独立 AIProvider 实例；否则返回 null（沿用当前聊天模型）。
     */
    suspend fun resolveCompactionFallbackProvider(sessionId: String? = null): AIProvider? {
        val providerId = compactionModelSettingsRepository.getCompactionProviderId().trim()
        val model = compactionModelSettingsRepository.getCompactionModel().trim()
        // 等价合并原「providerId 空 / model 空」两道早退：任一未配置即沿用聊天模型，结论不变。
        if (providerId.isEmpty() || model.isEmpty()) return null
        val config = aiProviderRepository.getProviderById(providerId) ?: return null
        // 等价合并原「未启用 / 无 apiKey」两道早退：任一不满足即沿用聊天模型，结论不变。
        if (!config.isEnabled || config.apiKey.isBlank()) return null
        return createStandaloneProvider(config.copy(selectedModel = model), sessionId)
    }
}
