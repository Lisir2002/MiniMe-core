package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.settings.data.remote.ModelApiService
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.settings.data.remote.ModelTestResult
import com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository
import com.mini.me_core.feature.settings.data.repository.DefaultPolicy
import com.mini.me_core.feature.settings.data.repository.ViewImageUnknownGuardPolicy
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ModelMetadata
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import com.mini.me_core.feature.settings.presentation.ConnectionTestState
import com.mini.me_core.feature.settings.presentation.FetchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：AI 供应商域（供应商 CRUD / 模型拉取与测试 / 模型元数据覆盖 /
 * 兼容端点全局策略 / 供应商级连通性测试）。逻辑原属 SettingsViewModel，逐字搬迁。
 */
@Suppress("TooManyFunctions", "ClassOrdering") // 行为保持搬迁：供应商域方法全部原样保留（ClassOrdering 原条目本在 baseline）
internal class ProviderSection(
    private val scope: CoroutineScope,
    private val repository: AIProviderRepository,
    private val modelApiService: ModelApiService,
    private val modelMetadataService: ModelMetadataService,
    private val compatibilityPolicyRepository: CompatibilityPolicyRepository,
) {
    private val _providers = MutableStateFlow<List<AIProviderConfig>>(emptyList())
    val providers: StateFlow<List<AIProviderConfig>> = _providers.asStateFlow()

    private val _activeProvider = MutableStateFlow<AIProviderConfig?>(null)
    val activeProvider: StateFlow<AIProviderConfig?> = _activeProvider.asStateFlow()

    private val _fetchState = MutableStateFlow<FetchState>(
        FetchState.Idle
    )
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    /** 内置供应商向导「选择模型」步骤的拉取状态（与编辑页 [_fetchState] 隔离，避免互相干扰）。 */
    private val _builtInFetchState = MutableStateFlow<FetchState>(
        FetchState.Idle
    )
    val builtInFetchState: StateFlow<FetchState> = _builtInFetchState.asStateFlow()

    /** 自定义供应商向导「选择模型」步骤的拉取状态（同样与编辑页 [_fetchState] 隔离）。 */
    private val _customFetchState = MutableStateFlow<FetchState>(
        FetchState.Idle
    )
    val customFetchState: StateFlow<FetchState> = _customFetchState.asStateFlow()

    /**
     * RC72：保存提供商时的错误提示通道（一次性，消费后自动清空）。
     *
     * 背景：RC71 把 AIProviderRepositoryImpl.toEntity 的「加密失败」从静默落空串改成抛异常，
     * 以杜绝密钥被空串覆盖。但 ViewModel 的 saveProvider 用 viewModelScope.launch 无 try-catch，
     * 未捕获的协程异常会直接导致 App 崩溃（闪退）。这里接管异常：不崩溃、不静默丢密钥，
     * 而是把错误暴露给 UI 弹提示。
     */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    /** 消费一次保存错误提示（UI 展示后调用），避免重复弹。 */
    fun consumeSaveError() {
        _saveError.value = null
    }

    private val _testResults = MutableStateFlow<Map<String, ModelTestResult>>(emptyMap())
    val testResults: StateFlow<Map<String, ModelTestResult>> = _testResults.asStateFlow()

    private val _modelMetadata = MutableStateFlow<Map<String, ModelMetadata>>(emptyMap())
    val modelMetadata: StateFlow<Map<String, ModelMetadata>> = _modelMetadata.asStateFlow()

    /** RC63 备选方案③：兼容端点「默认策略」流 + 快照（ProviderEditorScreen Tab0 下拉选择 + 选项卡小角标用）。 */
    val compatibilityDefaultPolicyFlow: StateFlow<DefaultPolicy>
        get() = compatibilityPolicyRepository.defaultPolicyFlow.stateIn(
            scope, SharingStarted.Eagerly, DefaultPolicy.STRICT
        )

    /** RC63 备选方案②「发送失败自动降级」总开关。 */
    val autoDowngradeOnSendFailureFlow: StateFlow<Boolean>
        get() = compatibilityPolicyRepository.autoDowngradeOnSendFailureFlow.stateIn(
            scope, SharingStarted.Eagerly, true
        )

    /** RC63 viewImage 守卫策略（FALLBACK_VISION_MODEL vs FAIL_FAST）。 */
    val viewImageUnknownGuardPolicyFlow: StateFlow<ViewImageUnknownGuardPolicy>
        get() = compatibilityPolicyRepository.viewImageUnknownGuardPolicyFlow.stateIn(
            scope, SharingStarted.Eagerly,
            ViewImageUnknownGuardPolicy.FALLBACK_VISION_MODEL
        )

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    // ── 供应商级连通性测试（GET /v1/models） ──
    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(
        ConnectionTestState.Idle
    )
    val connectionTestState: StateFlow<ConnectionTestState> =
        _connectionTestState.asStateFlow()

    init {
        scope.launch {
            // 启动即保证有激活提供商（若库中存在却无激活项），避免主页模型胶囊因 activeProvider=null 消失。
            repository.ensureActiveProvider()

            launch {
                repository.getAllProviders().collectLatest {
                    _providers.value = it
                    // 运行期兜底：提供商列表变化后若仍无激活项且有提供商，自动激活首个。
                    if (_activeProvider.value == null && it.isNotEmpty()) {
                        repository.ensureActiveProvider()
                    }
                }
            }

            launch {
                repository.getActiveProvider().collectLatest {
                    _activeProvider.value = it
                }
            }
        }
    }

    fun setActiveProvider(id: String) {
        scope.launch {
            repository.setActiveProvider(id)
        }
    }

    fun setProviderEnabled(id: String, isEnabled: Boolean) {
        scope.launch {
            repository.setProviderEnabled(id, isEnabled)
        }
    }

    fun saveProvider(provider: AIProviderConfig) {
        scope.launch {
            // RC72：接管保存异常，避免未捕获协程异常导致崩溃（闪退）。
            // 底层 toEntity 在加密失败时会抛异常（防止密钥被空串覆盖），必须在这里捕获，
            // 否则 viewModelScope.launch 的未捕获异常会直接闪退。
            runCatching { repository.saveProvider(provider) }
                .onFailure { e ->
                    FileLogger.e("SettingsVM", "保存提供商失败: ${e.message}", e)
                    _saveError.value = e.message ?: "保存失败，请重试"
                }
        }
    }

    fun deleteProvider(id: String) {
        scope.launch {
            val wasActive = repository.getActiveProviderSync()?.id == id
            val providerToDelete = repository.getProviderById(id)
            val providerType = providerToDelete?.type
            repository.deleteProvider(id)
            // 级联清理：删除供应商时清理其下所有模型级配置（能力覆盖/上下文长度/采样参数三张独立表）。
            if (providerType != null) {
                modelMetadataService.clearOverrideByProvider(providerType)
                modelMetadataService.clearCustomConfigByProvider(providerType)
                modelMetadataService.clearSamplingConfigByProvider(providerType)
            }
            if (wasActive) repository.ensureActiveProvider()
        }
    }

    fun testProviderConnection(provider: AIProviderConfig) {
        scope.launch {
            _connectionTestState.value = ConnectionTestState.Loading
            val result = modelApiService.fetchModels(provider.baseUrl, provider.apiKey, provider.type)
            _connectionTestState.value = result.fold(
                onSuccess = { models -> ConnectionTestState.Success("连接成功，发现 ${models.size} 个模型") },
                onFailure = { e -> ConnectionTestState.Error(e.message ?: "连接失败") }
            )
        }
    }

    fun resetConnectionTest() {
        _connectionTestState.value = ConnectionTestState.Idle
    }

    // ── 复制供应商（保留全部字段，重置 id / 名称加「（副本）」/ 不激活） ──
    fun duplicateProvider(id: String) {
        scope.launch {
            val original = repository.getProviderById(id) ?: return@launch
            val copy = original.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${original.name}（副本）",
                isActive = false
            )
            runCatching { repository.saveProvider(copy) }
                .onFailure { e ->
                    FileLogger.e("SettingsVM", "复制供应商失败: ${e.message}", e)
                    _saveError.value = e.message ?: "复制失败，请重试"
                }
        }
    }

    // ── 模型收藏：收藏即置顶。切换 favoriteModels 中 modelId 的存在并持久化。 ──
    fun toggleFavoriteModel(providerId: String, modelId: String) {
        scope.launch {
            val current = repository.getProviderById(providerId) ?: return@launch
            val newFavorites = if (modelId in current.favoriteModels) {
                current.favoriteModels - modelId
            } else {
                current.favoriteModels + modelId
            }
            runCatching { repository.saveProvider(current.copy(favoriteModels = newFavorites)) }
                .onFailure { e ->
                    FileLogger.e("SettingsVM", "更新收藏失败: ${e.message}", e)
                    _saveError.value = e.message ?: "操作失败"
                }
        }
    }

    fun fetchModels(provider: AIProviderConfig) {
        scope.launch {
            _fetchState.value = FetchState.Loading
            modelApiService.fetchModels(provider.baseUrl, provider.apiKey, provider.type)
                .onSuccess {
                    _fetchState.value = FetchState.Success(it)
                    resolveModelMetadata(provider.type, it)
                }
                .onFailure { _fetchState.value = FetchState.Error(it.message ?: "拉取失败") }
        }
    }

    fun resolveModelMetadata(type: ProviderType, modelIds: List<String>) {
        val normalizedIds = modelIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (normalizedIds.isEmpty()) return
        scope.launch {
            val metadata = modelMetadataService.resolveAll(type, normalizedIds)
            _modelMetadata.update { current -> current + metadata }
        }
    }

    /**
     * 加载所有已启用 provider 的全部模型元数据，合并进 [modelMetadata]。
     * 供「识图模型」等需要展示跨 provider 模型能力标签的页面在进入时调用--
     * 这些页面不像 ProviderEditor 那样会在编辑单个 provider 时顺带 resolve，
     * 不主动加载则 map 为空、所有模型都被误判为不支持图片。
     *
     * 实现要点（避免设置页卡顿）：
     * - 单协程顺序处理各 provider：首个 resolveAll 触发 catalog 加载（内存/磁盘 24h 缓存/内置 assets）并写入内存缓存，
     *   后续 provider 命中缓存。resolve 链路不发网络请求，models.dev 刷新统一由 App 启动时异步触发。
     * - 全部解析完一次性 update，避免多次 emit 导致设置页反复重组。
     */
    fun loadAllModelMetadata() {
        val enabled = _providers.value.filter { it.isEnabled }
        if (enabled.isEmpty()) return
        scope.launch {
            val resolved = mutableMapOf<String, ModelMetadata>()
            for (provider in enabled) {
                val ids = provider.models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (ids.isEmpty()) continue
                resolved += modelMetadataService.resolveAll(provider.type, ids)
            }
            if (resolved.isNotEmpty()) {
                _modelMetadata.update { it + resolved }
            }
        }
    }

    fun resetFetchState() {
        _fetchState.value = FetchState.Idle
    }

    /**
     * 内置供应商向导：实时拉取模型列表（步骤 3 使用）。
     *
     * 复用 [ModelApiService.fetchModels] 的接口探测与 T2I Failover；状态独立于编辑页，
     * 避免在向导里拉取模型时污染编辑页的 [_fetchState]。
     */
    fun fetchBuiltInModels(baseUrl: String, apiKey: String, type: ProviderType) {
        scope.launch {
            _builtInFetchState.value = FetchState.Loading
            modelApiService.fetchModels(baseUrl, apiKey, type)
                .onSuccess {
                    _builtInFetchState.value = FetchState.Success(it)
                    // 与编辑页一致：解析模型元数据，向导第 3 步才能展示真实能力标签（识图/工具/思考）
                    resolveModelMetadata(type, it)
                }
                .onFailure { _builtInFetchState.value = FetchState.Error(it.message ?: "拉取失败") }
        }
    }

    /**
     * 自定义供应商向导：拉取模型列表（步骤 2 使用）。
     * 复用 [ModelApiService.fetchModels] 的接口探测与 T2I Failover；状态独立于编辑页与内置向导。
     */
    fun fetchCustomModels(baseUrl: String, apiKey: String, type: ProviderType) {
        scope.launch {
            _customFetchState.value = FetchState.Loading
            modelApiService.fetchModels(baseUrl, apiKey, type)
                .onSuccess {
                    _customFetchState.value = FetchState.Success(it)
                    resolveModelMetadata(type, it)
                }
                .onFailure { _customFetchState.value = FetchState.Error(it.message ?: "拉取失败") }
        }
    }

    fun testModel(provider: AIProviderConfig, model: String) {
        scope.launch {
            _testing.update { it + model }
            val result = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                modelApiService.testModel(provider.baseUrl, provider.apiKey, provider.type, provider.useFullUrl, provider.useResponseApi, model)
            } ?: ModelTestResult(success = false, latencyMs = 30_000, message = "连接超时（30秒）")
            _testResults.update { it + (model to result) }
            _testing.update { it - model }
        }
    }

    fun clearTestResults() {
        _testResults.value = emptyMap()
        _testing.value = emptySet()
    }

    fun selectModel(providerId: String, model: String) {
        scope.launch {
            repository.setSelectedModel(providerId, model)
        }
    }

    // 主页模型选择：同步更新全局 active provider 的选中模型，使新建会话回退全局时落到用户最近选的模型。
    fun applyModelGlobally(providerId: String, model: String) {
        scope.launch {
            val activeId = repository.getActiveProviderSync()?.id
            if (activeId != providerId) {
                repository.setActiveProvider(providerId)
            }
            repository.setSelectedModel(providerId, model)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // RC63 备选方案③：兼容端点全局策略设置（ProviderEditorScreen Tab0 调用）
    // ────────────────────────────────────────────────────────────────

    fun setCompatibilityDefaultPolicy(policy: DefaultPolicy) {
        scope.launch { compatibilityPolicyRepository.setDefaultPolicy(policy) }
    }

    fun setAutoDowngradeOnSendFailure(enabled: Boolean) {
        scope.launch { compatibilityPolicyRepository.setAutoDowngradeOnSendFailure(enabled) }
    }

    fun setViewImageUnknownGuardPolicy(policy: ViewImageUnknownGuardPolicy) {
        scope.launch { compatibilityPolicyRepository.setViewImageUnknownGuardPolicy(policy) }
    }

    // ────────────────────────────────────────────────────────────────
    // RC63 备选方案④：单模型能力复选框覆盖（ProviderEditorScreen Tab1 调用）
    // ────────────────────────────────────────────────────────────────

    /** 观察某模型的手动覆盖（用于 ModelSettingsSheet 实时回显勾选状态）。 */
    fun observeCapabilityOverride(type: ProviderType, modelId: String) =
        modelMetadataService.observeOverride(type, modelId)

    /** 保存某模型的手动覆盖；不想覆盖的字段传 null（保持自动决策）。 */
    @Suppress("LongParameterList") // 签名保持：UI 接线签名不变，行为保持搬迁
    fun saveCapabilityOverride(
        type: ProviderType,
        modelId: String,
        vision: Boolean?,
        tools: Boolean?,
        reasoning: Boolean?,
        video: Boolean?,
        audio: Boolean?,
        code: Boolean?,
        structuredOutput: Boolean?
    ) {
        scope.launch {
            modelMetadataService.saveOverride(type, modelId, vision, tools, reasoning, video, audio, code, structuredOutput)
            // 立即重算该 provider 全部模型元数据（以便 ModelSettingsSheet 返回后
            // ProviderModelRow 的能力徽章与上下文标签立即刷新，用户看不到过期值）。
            val typeProviderModels = _providers.value
                .filter { it.type == type && it.models.isNotEmpty() }
                .flatMap { it.models.map(String::trim).filter(String::isNotEmpty).distinct() }
                .plus(modelId)
                .distinct()
            if (typeProviderModels.isEmpty()) return@launch
            val refreshed = modelMetadataService.resolveAll(type, typeProviderModels)
            _modelMetadata.update { it + refreshed }
        }
    }

    /** 清除某模型覆盖（恢复自动检测）。 */
    fun clearCapabilityOverride(type: ProviderType, modelId: String) {
        scope.launch {
            modelMetadataService.clearOverride(type, modelId)
            val refreshed = modelMetadataService.resolveAll(type, listOf(modelId))
            _modelMetadata.update { it + refreshed }
        }
    }

    // ── 模型自定义配置（输入/输出 token 上限覆盖）──────────────────────

    /** 观察某模型的自定义配置（用于 ModelSettingsSheet 回显输入框）。 */
    fun observeModelCustomConfig(type: ProviderType, modelId: String) =
        modelMetadataService.observeCustomConfig(type, modelId)

    /** 保存模型自定义输入/输出 token 上限；传 null 表示不覆盖该字段。保存后立即刷新元数据。 */
    fun saveModelCustomConfig(type: ProviderType, modelId: String, inputTokens: Int?, outputTokens: Int?) {
        scope.launch {
            modelMetadataService.saveCustomConfig(type, modelId, inputTokens, outputTokens)
            val refreshed = modelMetadataService.resolveAll(type, listOf(modelId))
            _modelMetadata.update { it + refreshed }
        }
    }

    /** 清除模型自定义配置（恢复自动检测的上下文长度）。 */
    fun clearModelCustomConfig(type: ProviderType, modelId: String) {
        scope.launch {
            modelMetadataService.clearCustomConfig(type, modelId)
            val refreshed = modelMetadataService.resolveAll(type, listOf(modelId))
            _modelMetadata.update { it + refreshed }
        }
    }

    // ── 模型级采样参数覆盖（temperature/topP/maxTokens）────────────────────

    /** 观察某模型的采样参数覆盖（用于 ModelSettingsSheet 回显滑块/输入框）。 */
    fun observeModelSamplingConfig(type: ProviderType, modelId: String) =
        modelMetadataService.observeSamplingConfig(type, modelId)

    /** 保存模型级采样参数覆盖；传 null 表示不覆盖该字段（继承供应商级默认）。 */
    fun saveModelSamplingConfig(type: ProviderType, modelId: String, temperature: Float?, topP: Float?, maxTokens: Int?) {
        scope.launch {
            modelMetadataService.saveSamplingConfig(type, modelId, temperature, topP, maxTokens)
        }
    }

    /** 清除模型级采样参数覆盖（恢复全部继承供应商级默认值）。 */
    fun clearModelSamplingConfig(type: ProviderType, modelId: String) {
        scope.launch {
            modelMetadataService.clearSamplingConfig(type, modelId)
        }
    }
}
