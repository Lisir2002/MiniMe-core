package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.agent.domain.container.ConnectionState
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.RemoteSshConnection
import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.agent.domain.execution.mcp.McpToolDescriptor
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRule
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.settings.data.remote.ModelApiService
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.settings.data.remote.ModelTestResult
import com.mini.me_core.feature.settings.data.repository.AppThemeMode
import com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository
import com.mini.me_core.feature.settings.data.repository.DefaultPolicy
import com.mini.me_core.feature.settings.data.repository.ViewImageUnknownGuardPolicy
import com.mini.me_core.feature.settings.data.repository.ContainerSettingsRepository
import com.mini.me_core.feature.settings.data.repository.SecureScreenRepository
import com.mini.me_core.feature.settings.data.repository.SecureScreenScope
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder
import com.mini.me_core.feature.settings.data.repository.ExecutionModeRepository
import com.mini.me_core.feature.settings.data.repository.KeepaliveSettingsRepository
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import com.mini.me_core.feature.agent.domain.core.guard.GuardLogRepository
import com.mini.me_core.feature.agent.domain.knowledge.playbook.PlaybookExecutor
import com.mini.me_core.feature.agent.domain.core.prompt.NormFlowStatsRepository

import com.mini.me_core.feature.settings.data.repository.LogFilterSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import com.mini.me_core.feature.settings.data.repository.SettingsSearchHistoryManager
import com.mini.me_core.feature.settings.data.repository.ThemeSettingsRepository
import com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import com.mini.me_core.feature.workspace.domain.repository.RemoteRepository
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ModelMetadata
import com.mini.me_core.feature.settings.domain.model.ProviderType
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import com.mini.me_core.feature.settings.presentation.section.ContainerSection
import com.mini.me_core.feature.settings.presentation.section.LogViewerSection
import com.mini.me_core.feature.settings.presentation.section.McpSection
import com.mini.me_core.feature.settings.presentation.section.NormFlowSection
import com.mini.me_core.feature.settings.presentation.section.PermissionSection
import com.mini.me_core.feature.settings.presentation.section.ProviderSection
import com.mini.me_core.feature.settings.presentation.section.ThemeSection
import com.mini.me_core.feature.settings.presentation.section.VisionCompactionSection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mini.me_core.feature.settings.presentation.component.SettingsSection

/**
 * P2-17 行为保持重构：本类是设置页**唯一** @HiltViewModel 瘦门面。
 *
 * 按设置域内聚的状态与逻辑已原样搬迁到同包 section/ 下的普通 internal 协作者
 * （ProviderSection / VisionCompactionSection / LogViewerSection / NormFlowSection /
 * ThemeSection / ContainerSection / McpSection / PermissionSection），UI 注入点零改动。
 * 每个对外 StateFlow 与公开方法都与原类同名同类型并委托对应 section；
 * 所有协程仍由同一 viewModelScope 驱动（section 通过构造拿到该 scope）。
 */
@Suppress("TooManyFunctions", "ClassOrdering") // 瘦门面按设置域聚合委托，行为保持（ClassOrdering 原条目本在 baseline）
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AIProviderRepository,
    private val modelApiService: ModelApiService,
    private val modelMetadataService: ModelMetadataService,
    private val logSettingsRepository: LogSettingsRepository,
    private val logFilterSettingsRepository: LogFilterSettingsRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val keepaliveSettingsRepository: KeepaliveSettingsRepository,
    /** D1-7 规范流程统一开关（总开关 + step_inject/tool_guard 子开关，对齐 norm-chain §3.5）。 */
    private val normFlowSettingsRepository: NormFlowSettingsRepository,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val mcpServerManager: McpServerManager,
    private val permissionRulesRepository: PermissionRulesRepository,
    private val visionModelSettingsRepository: VisionModelSettingsRepository,
    private val compactionModelSettingsRepository: CompactionModelSettingsRepository,
    private val containerSettingsRepository: ContainerSettingsRepository,
    private val containerInstaller: ContainerInstaller,
    private val executionModeRepository: ExecutionModeRepository,
    private val executionModeHolder: ExecutionModeHolder,
    private val remoteSshConnection: RemoteSshConnection,
    private val remoteRepository: RemoteRepository,
    val auditLogRepository: RemoteAuditLogRepository,
    /** RC63 备选方案③：兼容端点全局策略（STRICT/HEURISTIC/LAX/MANUAL + 自动降级 + viewImage 守卫）。 */
    private val compatibilityPolicyRepository: CompatibilityPolicyRepository,
    /** 设置页搜索历史（KVStore 持久化，最近 10 条）。 */
    private val searchHistoryManager: SettingsSearchHistoryManager,
    /** 防截图录屏开关。 */
    private val secureScreenRepository: SecureScreenRepository,
    /** 仪表盘统计：护栏累计拦截次数（Block 级）。 */
    private val guardLogRepository: GuardLogRepository,
    /** 仪表盘统计：Playbook 跨会话空转轮数总和。 */
    private val playbookExecutor: PlaybookExecutor,
    /** 仪表盘统计：累计注入 token 估算。 */
    private val normFlowStatsRepository: NormFlowStatsRepository,
) : ViewModel() {

    // ── F5.5 变更生效即时反馈（嵌套类型保持原位，SettingsScreen 以 SettingsViewModel.SettingEffect 引用）──
    /** 设置变更效果事件：立即生效提示 / 需重启提示。 */
    sealed interface SettingEffect {
        /** 变更已即时生效（Snackbar 提示）。 */
        data object Applied : SettingEffect
        /** 变更需重启应用后生效（Snackbar 带「立即重启」）。 */
        data object NeedsRestart : SettingEffect
    }
    private val _settingEffect = kotlinx.coroutines.flow.MutableSharedFlow<SettingEffect>(extraBufferCapacity = 4)
    val settingEffect: kotlinx.coroutines.flow.SharedFlow<SettingEffect> = _settingEffect.asSharedFlow()

    // ── 各域 section 装配：构造顺序与原 init 块内 collector 启动顺序一致 ──
    private val providerSection = ProviderSection(
        scope = viewModelScope,
        repository = repository,
        modelApiService = modelApiService,
        modelMetadataService = modelMetadataService,
        compatibilityPolicyRepository = compatibilityPolicyRepository,
    )
    private val visionCompactionSection = VisionCompactionSection(
        scope = viewModelScope,
        visionModelSettingsRepository = visionModelSettingsRepository,
        compactionModelSettingsRepository = compactionModelSettingsRepository,
    )
    private val logViewerSection = LogViewerSection(
        scope = viewModelScope,
        logSettingsRepository = logSettingsRepository,
        logFilterSettingsRepository = logFilterSettingsRepository,
    )
    private val normFlowSection = NormFlowSection(
        scope = viewModelScope,
        normFlowSettingsRepository = normFlowSettingsRepository,
    )
    private val themeSection = ThemeSection(
        scope = viewModelScope,
        themeSettingsRepository = themeSettingsRepository,
    ) { effect ->
        // 语义等同原 _settingEffect.tryEmit(...)
        _settingEffect.tryEmit(effect)
    }
    private val containerSection = ContainerSection(
        scope = viewModelScope,
        containerSettingsRepository = containerSettingsRepository,
        containerInstaller = containerInstaller,
        executionModeRepository = executionModeRepository,
        executionModeHolder = executionModeHolder,
        remoteSshConnection = remoteSshConnection,
        remoteRepository = remoteRepository,
    )
    private val mcpSection = McpSection(
        scope = viewModelScope,
        mcpConfigRepository = mcpConfigRepository,
        mcpManager = mcpManager,
        mcpServerManager = mcpServerManager,
    )
    private val permissionSection = PermissionSection(
        scope = viewModelScope,
        permissionRulesRepository = permissionRulesRepository,
    )

    // RC62：跨屏幕设置页跳转的「待打开分区」信号。
    // - 背景：MainActivity 有两个地方会触发「跳转到 SSH 主机配置」：
    //     1) SettingsScreen 内部 onNavigateToSshHosts lambda（本就在 Settings 路由栈内）
    //     2) TerminalSettingsScreen 的 SSH 菜单项（从 terminal_settings 路由先 pop 回 settings，
    //        再把 SettingsScreen 内部的 section 切到 RemoteServers —— 但 SettingsScreen 内部
    //        section 是它自己的 remember mutableState，外部调不到）
    // - 解决方案：复用 Activity 级单例 SettingsViewModel（本来 MainActivity L388 就复用了），
    //   用 tick + lastRequestedSection 对比：外部 openSection(Section) 后 tick+1，
    //   SettingsScreen LaunchedEffect(pendingTick, consumedTick) 看到有新 tick 就跳 section 并标记 consumed。
    // - 为什么不用 SharedFlow replay=0：路由创建之前就发 openSection 的场景会丢事件，
    //   tick 对比是纯状态机，100% 不漏也不重入。
    private val _pendingOpenSectionTick = MutableStateFlow(0L)
    private val _lastConsumedSectionTick = MutableStateFlow(-1L)
    private val _lastRequestedSection: MutableStateFlow<SettingsSection?> = MutableStateFlow(null)
    val lastRequestedSection: StateFlow<SettingsSection?> = _lastRequestedSection.asStateFlow()
    val pendingOpenSectionTick: StateFlow<Long> = _pendingOpenSectionTick.asStateFlow()
    val lastConsumedSectionTick: StateFlow<Long> = _lastConsumedSectionTick.asStateFlow()

    /**
     * 请求 SettingsScreen 切到指定二级分区。
     * 通常用在：TerminalSettingsScreen 里点「管理 SSH 主机配置」时，需要 terminal_settings
     * 先 popBackStack 回 settings，然后 SettingsScreen 自己在进入时收到信号 → 内部 section = Section。
     */
    fun openSection(section: SettingsSection) {
        _lastRequestedSection.value = section
        _pendingOpenSectionTick.value = (_pendingOpenSectionTick.value + 1) and Long.MAX_VALUE
    }

    /** 消费一次 openSection 请求（由 SettingsScreen 调），避免 LaunchedEffect 重入时反复跳同一个 section。 */
    fun markPendingSectionConsumed(tick: Long) {
        _lastConsumedSectionTick.value = tick
    }

    // ── 设置页搜索历史（KVStore 持久化，最近 10 条）──
    val searchHistory: StateFlow<List<String>> = searchHistoryManager.history

    /** 记录一次搜索词（在 IME Search / 点击结果时调用）。 */
    fun recordSearch(query: String) {
        viewModelScope.launch { searchHistoryManager.recordSearch(query) }
    }

    /** 清空搜索历史。 */
    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryManager.clearHistory() }
    }

    /** 删除单条搜索历史（F5.1）。 */
    fun removeSearchHistoryItem(query: String) {
        viewModelScope.launch { searchHistoryManager.removeHistoryItem(query) }
    }

    // ── 防截图录屏开关（供 MainActivity / SettingsScreen 统一动态控制 FLAG_SECURE）──
    val secureScreenEnabled: StateFlow<Boolean> = secureScreenRepository.enabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 防截图录屏保护范围（仅供应商编辑页 / 所有敏感页 / 全局）。 */
    val secureScreenScope: StateFlow<SecureScreenScope> = secureScreenRepository.scopeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SecureScreenScope.DEFAULT)

    // ── Keepalive（仅持久化标志位——启停 Service 由 MiniMeCore 监听 enabledFlow 统一完成）──
    private val _keepaliveEnabled = MutableStateFlow(false)
    val keepaliveEnabled: StateFlow<Boolean> = _keepaliveEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            keepaliveSettingsRepository.enabledFlow.collectLatest {
                _keepaliveEnabled.value = it
            }
        }
    }

    fun setKeepaliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            keepaliveSettingsRepository.setEnabled(enabled)
            _settingEffect.tryEmit(SettingEffect.NeedsRestart)
        }
    }

    // ── 仪表盘真实统计（替代原硬编码 "--"/"0"/"0"）────────────────────────────
    /** 护栏累计拦截次数（Block 级，Advisory 不计）。 */
    val guardBlockCount: StateFlow<Int> = guardLogRepository.blockCount

    /** 累计注入 token 估算值（字符数 / 4）。 */
    val injectTokenCount: StateFlow<Int> = normFlowStatsRepository.injectTokenCount

    /**
     * 跨会话空转轮数总和：PlaybookExecutor.idleRounds 为非挂起内存快照，
     * 此处以 2s 轮询定时刷新（仪表盘前台展示足够，避免改造所有 idleRounds 变更点）。
     */
    val idleRoundCount: StateFlow<Int> = flow {
        while (true) {
            emit(playbookExecutor.getTotalIdleRounds())
            delay(IDLE_ROUND_POLL_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // 仪表盘「重置统计」：清空累计注入 token 与护栏拦截日志/计数（空转轮数为运行时快照，不在此清空）。
    fun resetNormFlowStats() {
        normFlowStatsRepository.clear()
        guardLogRepository.clear()
    }

    // ── 设置页面滚动位置持久化 ─────────────────────────────────────────────
    // 导航到子页面后 SettingsScreen composable 被移出组合，rememberScrollState() 丢失，
    // 返回时回到顶部。将偏移量保存在 Activity 级 ViewModel 中，跨导航保持存活。
    private val _settingsScrollOffset = MutableStateFlow(0)
    val settingsScrollOffset: StateFlow<Int> = _settingsScrollOffset.asStateFlow()

    fun setSettingsScrollOffset(offset: Int) {
        if (offset >= 0) _settingsScrollOffset.value = offset
    }

    // ── ProviderSection 委托面 ────────────────────────────────────────────
    val providers: StateFlow<List<AIProviderConfig>> = providerSection.providers
    val activeProvider: StateFlow<AIProviderConfig?> = providerSection.activeProvider
    val fetchState: StateFlow<FetchState> = providerSection.fetchState
    val builtInFetchState: StateFlow<FetchState> = providerSection.builtInFetchState
    val customFetchState: StateFlow<FetchState> = providerSection.customFetchState
    val saveError: StateFlow<String?> = providerSection.saveError
    val testResults: StateFlow<Map<String, ModelTestResult>> = providerSection.testResults
    val modelMetadata: StateFlow<Map<String, ModelMetadata>> = providerSection.modelMetadata
    val testing: StateFlow<Set<String>> = providerSection.testing
    val connectionTestState: StateFlow<ConnectionTestState> = providerSection.connectionTestState

    /** RC63 备选方案③：兼容端点「默认策略」流（保持原 get() 语义：每次访问新建 Eagerly stateIn）。 */
    val compatibilityDefaultPolicyFlow: StateFlow<DefaultPolicy>
        get() = providerSection.compatibilityDefaultPolicyFlow

    /** RC63 备选方案②「发送失败自动降级」总开关。 */
    val autoDowngradeOnSendFailureFlow: StateFlow<Boolean>
        get() = providerSection.autoDowngradeOnSendFailureFlow

    /** RC63 viewImage 守卫策略（FALLBACK_VISION_MODEL vs FAIL_FAST）。 */
    val viewImageUnknownGuardPolicyFlow: StateFlow<ViewImageUnknownGuardPolicy>
        get() = providerSection.viewImageUnknownGuardPolicyFlow

    fun consumeSaveError() = providerSection.consumeSaveError()
    fun setActiveProvider(id: String) = providerSection.setActiveProvider(id)
    fun setProviderEnabled(id: String, isEnabled: Boolean) = providerSection.setProviderEnabled(id, isEnabled)
    fun saveProvider(provider: AIProviderConfig) = providerSection.saveProvider(provider)
    fun deleteProvider(id: String) = providerSection.deleteProvider(id)
    fun testProviderConnection(provider: AIProviderConfig) = providerSection.testProviderConnection(provider)
    fun resetConnectionTest() = providerSection.resetConnectionTest()
    fun duplicateProvider(id: String) = providerSection.duplicateProvider(id)
    fun toggleFavoriteModel(providerId: String, modelId: String) = providerSection.toggleFavoriteModel(providerId, modelId)
    fun fetchModels(provider: AIProviderConfig) = providerSection.fetchModels(provider)
    fun resolveModelMetadata(type: ProviderType, modelIds: List<String>) = providerSection.resolveModelMetadata(type, modelIds)
    fun loadAllModelMetadata() = providerSection.loadAllModelMetadata()
    fun resetFetchState() = providerSection.resetFetchState()
    fun fetchBuiltInModels(baseUrl: String, apiKey: String, type: ProviderType) =
        providerSection.fetchBuiltInModels(baseUrl, apiKey, type)
    fun fetchCustomModels(baseUrl: String, apiKey: String, type: ProviderType) =
        providerSection.fetchCustomModels(baseUrl, apiKey, type)
    fun testModel(provider: AIProviderConfig, model: String) = providerSection.testModel(provider, model)
    fun clearTestResults() = providerSection.clearTestResults()
    fun selectModel(providerId: String, model: String) = providerSection.selectModel(providerId, model)
    fun applyModelGlobally(providerId: String, model: String) = providerSection.applyModelGlobally(providerId, model)
    fun setCompatibilityDefaultPolicy(policy: DefaultPolicy) = providerSection.setCompatibilityDefaultPolicy(policy)
    fun setAutoDowngradeOnSendFailure(enabled: Boolean) = providerSection.setAutoDowngradeOnSendFailure(enabled)
    fun setViewImageUnknownGuardPolicy(policy: ViewImageUnknownGuardPolicy) = providerSection.setViewImageUnknownGuardPolicy(policy)
    fun observeCapabilityOverride(type: ProviderType, modelId: String) = providerSection.observeCapabilityOverride(type, modelId)

    @Suppress("LongParameterList") // 签名保持：UI 接线签名不变
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
    ) = providerSection.saveCapabilityOverride(type, modelId, vision, tools, reasoning, video, audio, code, structuredOutput)

    fun clearCapabilityOverride(type: ProviderType, modelId: String) = providerSection.clearCapabilityOverride(type, modelId)
    fun observeModelCustomConfig(type: ProviderType, modelId: String) = providerSection.observeModelCustomConfig(type, modelId)
    fun saveModelCustomConfig(type: ProviderType, modelId: String, inputTokens: Int?, outputTokens: Int?) =
        providerSection.saveModelCustomConfig(type, modelId, inputTokens, outputTokens)
    fun clearModelCustomConfig(type: ProviderType, modelId: String) = providerSection.clearModelCustomConfig(type, modelId)
    fun observeModelSamplingConfig(type: ProviderType, modelId: String) = providerSection.observeModelSamplingConfig(type, modelId)
    fun saveModelSamplingConfig(type: ProviderType, modelId: String, temperature: Float?, topP: Float?, maxTokens: Int?) =
        providerSection.saveModelSamplingConfig(type, modelId, temperature, topP, maxTokens)
    fun clearModelSamplingConfig(type: ProviderType, modelId: String) = providerSection.clearModelSamplingConfig(type, modelId)

    // ── VisionCompactionSection 委托面 ────────────────────────────────────
    val visionProviderId: StateFlow<String> = visionCompactionSection.visionProviderId
    val visionModel: StateFlow<String> = visionCompactionSection.visionModel
    val compactionProviderId: StateFlow<String> = visionCompactionSection.compactionProviderId
    val compactionModel: StateFlow<String> = visionCompactionSection.compactionModel
    fun setVisionModel(providerId: String, model: String) = visionCompactionSection.setVisionModel(providerId, model)
    fun clearVisionModel() = visionCompactionSection.clearVisionModel()
    fun setCompactionModel(providerId: String, model: String) = visionCompactionSection.setCompactionModel(providerId, model)
    fun clearCompactionModel() = visionCompactionSection.clearCompactionModel()

    // ── LogViewerSection 委托面 ──────────────────────────────────────────
    val logLevel: StateFlow<com.mini.me_core.core.util.LogLevel> = logViewerSection.logLevel
    val logViewerState: StateFlow<LogViewerUiState> = logViewerSection.logViewerState
    fun setLogLevel(level: com.mini.me_core.core.util.LogLevel) = logViewerSection.setLogLevel(level)
    // 默认参数保持原调用点求值语义：无参调用时取当前日志状态的 filterServerName。
    fun refreshLogs(filterServerName: String? = logViewerSection.logViewerState.value.filterServerName) =
        logViewerSection.refreshLogs(filterServerName)
    fun selectLogFile(fileName: String) = logViewerSection.selectLogFile(fileName)
    fun toggleFilterPanel() = logViewerSection.toggleFilterPanel()
    fun closeFilterPanel() = logViewerSection.closeFilterPanel()
    fun setSelectedDates(dates: Set<String>) = logViewerSection.setSelectedDates(dates)
    fun setDateRangeMode(mode: DateRangeMode) = logViewerSection.setDateRangeMode(mode)
    fun selectSingleFile(fileName: String) = logViewerSection.selectSingleFile(fileName)
    fun setCustomDateRange(start: String?, end: String?) = logViewerSection.setCustomDateRange(start, end)
    fun setRecordLevel(level: com.mini.me_core.core.util.LogLevel) = logViewerSection.setRecordLevel(level)
    fun clearDisplayLevels() = logViewerSection.clearDisplayLevels()
    fun quickFilterLevel(level: com.mini.me_core.core.util.LogLevel) = logViewerSection.quickFilterLevel(level)
    fun toggleLevelCollapse(level: com.mini.me_core.core.util.LogLevel) = logViewerSection.toggleLevelCollapse(level)
    fun setSearchExpanded(expanded: Boolean) = logViewerSection.setSearchExpanded(expanded)
    fun prevMatch() = logViewerSection.prevMatch()
    fun nextMatch() = logViewerSection.nextMatch()
    fun toggleLevel(level: com.mini.me_core.core.util.LogLevel) = logViewerSection.toggleLevel(level)
    fun toggleTag(tag: String) = logViewerSection.toggleTag(tag)
    fun setSearchQuery(query: String) = logViewerSection.setSearchQuery(query)
    fun resetFilters() = logViewerSection.resetFilters()
    fun toggleLiveTail() = logViewerSection.toggleLiveTail()
    fun dismissNewLogs() = logViewerSection.dismissNewLogs()
    fun setAutoScrolling(autoScrolling: Boolean) = logViewerSection.setAutoScrolling(autoScrolling)

    // ── NormFlowSection 委托面 ───────────────────────────────────────────
    val normFlowEnabled: StateFlow<Boolean> = normFlowSection.normFlowEnabled
    val stepInjectEnabled: StateFlow<Boolean> = normFlowSection.stepInjectEnabled
    val toolGuardEnabled: StateFlow<Boolean> = normFlowSection.toolGuardEnabled
    val reasoningBudgetEnabled: StateFlow<Boolean> = normFlowSection.reasoningBudgetEnabled
    val usageCardEnabled: StateFlow<Boolean> = normFlowSection.usageCardEnabled
    val sopSummaryEnabled: StateFlow<Boolean> = normFlowSection.sopSummaryEnabled
    val playbookAutoEnabled: StateFlow<Boolean> = normFlowSection.playbookAutoEnabled
    val idleConvergeEnabled: StateFlow<Boolean> = normFlowSection.idleConvergeEnabled
    val stepInjectGoalEnabled: StateFlow<Boolean> = normFlowSection.stepInjectGoalEnabled
    val stepInjectStaticRulesEnabled: StateFlow<Boolean> = normFlowSection.stepInjectStaticRulesEnabled
    val stepInjectLayeredRulesEnabled: StateFlow<Boolean> = normFlowSection.stepInjectLayeredRulesEnabled
    val stepInjectProjectAgentsEnabled: StateFlow<Boolean> = normFlowSection.stepInjectProjectAgentsEnabled
    val fileObservationEnabled: StateFlow<Boolean> = normFlowSection.fileObservationEnabled
    val reasoningBudgetLevel: StateFlow<String> = normFlowSection.reasoningBudgetLevel
    val idleConvergeRounds: StateFlow<Int> = normFlowSection.idleConvergeRounds
    val activePreset: StateFlow<String> = normFlowSection.activePreset
    val guardDangerousCommandEnabled: StateFlow<Boolean> = normFlowSection.guardDangerousCommandEnabled
    val guardLargeFileEnabled: StateFlow<Boolean> = normFlowSection.guardLargeFileEnabled
    val guardPathBoundaryEnabled: StateFlow<Boolean> = normFlowSection.guardPathBoundaryEnabled
    val usageCardItems: StateFlow<Set<String>> = normFlowSection.usageCardItems

    fun setNormFlowEnabled(enabled: Boolean) = normFlowSection.setNormFlowEnabled(enabled)
    fun setStepInjectEnabled(enabled: Boolean) = normFlowSection.setStepInjectEnabled(enabled)
    fun setToolGuardEnabled(enabled: Boolean) = normFlowSection.setToolGuardEnabled(enabled)
    fun setReasoningBudgetEnabled(enabled: Boolean) = normFlowSection.setReasoningBudgetEnabled(enabled)
    fun setUsageCardEnabled(enabled: Boolean) = normFlowSection.setUsageCardEnabled(enabled)
    fun setSopSummaryEnabled(enabled: Boolean) = normFlowSection.setSopSummaryEnabled(enabled)
    fun setPlaybookAutoEnabled(enabled: Boolean) = normFlowSection.setPlaybookAutoEnabled(enabled)
    fun setIdleConvergeEnabled(enabled: Boolean) = normFlowSection.setIdleConvergeEnabled(enabled)
    fun setStepInjectGoalEnabled(enabled: Boolean) = normFlowSection.setStepInjectGoalEnabled(enabled)
    fun setStepInjectStaticRulesEnabled(enabled: Boolean) = normFlowSection.setStepInjectStaticRulesEnabled(enabled)
    fun setStepInjectLayeredRulesEnabled(enabled: Boolean) = normFlowSection.setStepInjectLayeredRulesEnabled(enabled)
    fun setStepInjectProjectAgentsEnabled(enabled: Boolean) = normFlowSection.setStepInjectProjectAgentsEnabled(enabled)
    fun setFileObservationEnabled(enabled: Boolean) = normFlowSection.setFileObservationEnabled(enabled)
    fun setReasoningBudgetLevel(level: String) = normFlowSection.setReasoningBudgetLevel(level)
    fun setIdleConvergeRounds(rounds: Int) = normFlowSection.setIdleConvergeRounds(rounds)
    fun applyPreset(preset: String) = normFlowSection.applyPreset(preset)
    fun setGuardDangerousCommandEnabled(enabled: Boolean) = normFlowSection.setGuardDangerousCommandEnabled(enabled)
    fun setGuardLargeFileEnabled(enabled: Boolean) = normFlowSection.setGuardLargeFileEnabled(enabled)
    // 安全相关：路径边界开关委托，默认值/键/时机全部原样搬迁。
    fun setGuardPathBoundaryEnabled(enabled: Boolean) = normFlowSection.setGuardPathBoundaryEnabled(enabled)
    fun toggleUsageCardItem(item: String) = normFlowSection.toggleUsageCardItem(item)
    fun exportConfig(): String = normFlowSection.exportConfig()
    fun importConfig(json: String): Boolean = normFlowSection.importConfig(json)

    // ── ThemeSection 委托面 ──────────────────────────────────────────────
    val themeMode: StateFlow<AppThemeMode> = themeSection.themeMode
    fun setThemeMode(mode: AppThemeMode) = themeSection.setThemeMode(mode)

    // ── ContainerSection 委托面 ──────────────────────────────────────────
    val activeProfileId: StateFlow<String> = containerSection.activeProfileId
    val storageShareEnabled: StateFlow<Boolean> = containerSection.storageShareEnabled
    val customProfiles: StateFlow<List<ContainerProfile>> = containerSection.customProfiles
    val profiles: StateFlow<List<ContainerProfile>> = containerSection.profiles
    val executionMode: StateFlow<ExecutionMode> = containerSection.executionMode
    val connectionState: StateFlow<ConnectionState> = containerSection.connectionState
    val remoteConnections: StateFlow<List<RemoteConnection>> = containerSection.remoteConnections
    fun setActiveContainerProfile(id: String) = containerSection.setActiveContainerProfile(id)
    fun setStorageShareEnabled(enabled: Boolean) = containerSection.setStorageShareEnabled(enabled)
    fun resetBuiltinContainer(profile: ContainerProfile) = containerSection.resetBuiltinContainer(profile)
    fun saveCustomContainerProfile(profile: ContainerProfile) = containerSection.saveCustomContainerProfile(profile)
    fun editCustomContainerProfile(profile: ContainerProfile) = containerSection.editCustomContainerProfile(profile)
    fun deleteCustomContainerProfile(profile: ContainerProfile) = containerSection.deleteCustomContainerProfile(profile)

    // ── McpSection 委托面 ────────────────────────────────────────────────
    val mcpServers: StateFlow<List<McpServerConfig>> = mcpSection.mcpServers
    val mcpStatuses: StateFlow<List<McpServerStatus>> = mcpSection.mcpStatuses
    val mcpReloading: StateFlow<Boolean> = mcpSection.mcpReloading
    val mcpServerIsRunning: StateFlow<Boolean> = mcpSection.mcpServerIsRunning
    val mcpServerPort: StateFlow<Int> = mcpSection.mcpServerPort
    val mcpServerToken: StateFlow<String> = mcpSection.mcpServerToken
    val mcpServerRequireApproval: StateFlow<Boolean> = mcpSection.mcpServerRequireApproval
    val mcpServerAutoStart: StateFlow<Boolean> = mcpSection.mcpServerAutoStart
    val mcpServerUrl: StateFlow<String> = mcpSection.mcpServerUrl
    val mcpServerError: StateFlow<String?> = mcpSection.mcpServerError
    fun toggleMcpServer() = mcpSection.toggleMcpServer()
    fun saveMcpServerConfig(port: Int, requireApproval: Boolean, autoStart: Boolean) =
        mcpSection.saveMcpServerConfig(port, requireApproval, autoStart)
    fun regenerateMcpServerToken() = mcpSection.regenerateMcpServerToken()
    fun upsertMcpServer(originalName: String?, config: McpServerConfig) =
        mcpSection.upsertMcpServer(originalName, config)
    fun deleteMcpServer(name: String) = mcpSection.deleteMcpServer(name)
    fun setMcpServerEnabled(name: String, enabled: Boolean) = mcpSection.setMcpServerEnabled(name, enabled)
    fun reloadMcp() = mcpSection.reloadMcp()
    fun getMcpServerTools(serverName: String?): List<McpToolDescriptor> = mcpSection.getMcpServerTools(serverName)

    // ── PermissionSection 委托面 ────────────────────────────────────────
    val globalRules: StateFlow<List<PermissionRule>> = permissionSection.globalRules
    val projectRules: StateFlow<List<PermissionRule>> = permissionSection.projectRules
    val currentProjectName: StateFlow<String?>
        get() = permissionSection.currentProjectName

    fun deleteGlobalRule(rule: PermissionRule) = permissionSection.deleteGlobalRule(rule)
    fun deleteProjectRule(rule: PermissionRule) = permissionSection.deleteProjectRule(rule)
    fun promoteRuleToGlobal(rule: PermissionRule) = permissionSection.promoteRuleToGlobal(rule)

    override fun onCleared() {
        super.onCleared()
        logViewerSection.onCleared()
    }

    private companion object {
        /** 仪表盘空转轮数轮询间隔（毫秒）：前台展示足够实时，避免高频刷新。 */
        const val IDLE_ROUND_POLL_MS = 2_000L
    }
}
