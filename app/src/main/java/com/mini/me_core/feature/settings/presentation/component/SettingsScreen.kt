package com.mini.me_core.feature.settings.presentation.component
import com.mini.me_core.core.theme.tokens.LocalComponentTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import com.mini.me_core.core.theme.AppTopAppBar
import com.mini.me_core.core.theme.AppSectionHeader
import com.mini.me_core.core.theme.AppSectionGroup
import com.mini.me_core.core.theme.CyberColors
import com.mini.me_core.core.theme.CyberCard
import com.mini.me_core.core.theme.CyberSectionHeader
import com.mini.me_core.core.theme.CyberMenuRow
import com.mini.me_core.core.theme.CyberSearchBar
import com.mini.me_core.core.theme.LocalAppDarkMode
import com.mini.me_core.core.theme.cyberColor
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.R
import com.mini.me_core.feature.agent.domain.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.mcp.McpServerStatus
import com.mini.me_core.feature.backup.presentation.BackupSection
import com.mini.me_core.feature.settings.data.repository.AppThemeMode
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.model.ModelMetadata
import com.mini.me_core.feature.settings.presentation.SecuritySettingsViewModel
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import com.mini.me_core.feature.settings.presentation.components.RemoteAuditLogsScreen
import com.mini.me_core.feature.settings.presentation.components.SecuritySettingsScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal

/** 设置页内部二级菜单分区。Menu 为首页菜单，其余为各自的二级页。 */
enum class SettingsSection(@param:StringRes val titleRes: Int) {
    Menu(R.string.settings_title),
    Providers(R.string.settings_providers),
    ProviderEditor(R.string.settings_provider_editor),
    McpCenter(R.string.settings_mcp),
    Container(R.string.settings_container),
    Logs(R.string.settings_logs),
    Permissions(R.string.settings_permissions),
    NormFlow(R.string.settings_norm_flow),
    RemoteServers(R.string.settings_remote_servers),
    Backup(R.string.settings_backup),
    Security(R.string.settings_security),
    RemoteAuditLogs(R.string.settings_remote_audit_logs),
    About(R.string.settings_about),
    Theme(R.string.settings_theme_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTerminalSettings: () -> Unit = {},
    onNavigateToSshHosts: () -> Unit = {},
    onStopAllAndCloseTerminal: () -> Unit = {},
    onNavigateToNetProxy: () -> Unit = {},
    onNavigateToCapabilityCenter: () -> Unit = {}
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val mcpStatuses by viewModel.mcpStatuses.collectAsStateWithLifecycle()
    val mcpReloading by viewModel.mcpReloading.collectAsStateWithLifecycle()
    val mcpServerIsRunning by viewModel.mcpServerIsRunning.collectAsStateWithLifecycle()
    val mcpServerPort by viewModel.mcpServerPort.collectAsStateWithLifecycle()
    val mcpServerToken by viewModel.mcpServerToken.collectAsStateWithLifecycle()
    val mcpServerRequireApproval by viewModel.mcpServerRequireApproval.collectAsStateWithLifecycle()
    val mcpServerAutoStart by viewModel.mcpServerAutoStart.collectAsStateWithLifecycle()
    val mcpServerUrl by viewModel.mcpServerUrl.collectAsStateWithLifecycle()
    val mcpServerError by viewModel.mcpServerError.collectAsStateWithLifecycle()
    val globalRules by viewModel.globalRules.collectAsStateWithLifecycle()
    val projectRules by viewModel.projectRules.collectAsStateWithLifecycle()
    val currentProjectName by viewModel.currentProjectName.collectAsStateWithLifecycle()
    val keepaliveEnabled by viewModel.keepaliveEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val visionProviderId by viewModel.visionProviderId.collectAsStateWithLifecycle()
    val visionModel by viewModel.visionModel.collectAsStateWithLifecycle()
    val compactionProviderId by viewModel.compactionProviderId.collectAsStateWithLifecycle()
    val compactionModel by viewModel.compactionModel.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val containerProfiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val remoteConnections by viewModel.remoteConnections.collectAsStateWithLifecycle()
    val storageShareEnabled by viewModel.storageShareEnabled.collectAsStateWithLifecycle()
    // D1-7 规范流程统一开关（对齐 norm-chain §3.5：总开关 + step_inject/tool_guard 子开关）。
    val normFlowEnabled by viewModel.normFlowEnabled.collectAsStateWithLifecycle()
    val stepInjectEnabled by viewModel.stepInjectEnabled.collectAsStateWithLifecycle()
    val toolGuardEnabled by viewModel.toolGuardEnabled.collectAsStateWithLifecycle()
    // D2-2/D2-4 规范流程子开关：推理预算 / 用量卡片。
    val reasoningBudgetEnabled by viewModel.reasoningBudgetEnabled.collectAsStateWithLifecycle()
    val usageCardEnabled by viewModel.usageCardEnabled.collectAsStateWithLifecycle()
    val sopSummaryEnabled by viewModel.sopSummaryEnabled.collectAsStateWithLifecycle()
    // D5-pa Playbook 自动触发子开关（对齐 §3.5）。
    val playbookAutoEnabled by viewModel.playbookAutoEnabled.collectAsStateWithLifecycle()
    // D2-1 空转软收敛子开关（默认关）。
    val idleConvergeEnabled by viewModel.idleConvergeEnabled.collectAsStateWithLifecycle()
    // P0 step 前注入子开关
    val stepInjectGoalEnabled by viewModel.stepInjectGoalEnabled.collectAsStateWithLifecycle()
    val stepInjectStaticRulesEnabled by viewModel.stepInjectStaticRulesEnabled.collectAsStateWithLifecycle()
    val stepInjectLayeredRulesEnabled by viewModel.stepInjectLayeredRulesEnabled.collectAsStateWithLifecycle()
    val stepInjectProjectAgentsEnabled by viewModel.stepInjectProjectAgentsEnabled.collectAsStateWithLifecycle()
    val fileObservationEnabled by viewModel.fileObservationEnabled.collectAsStateWithLifecycle()
    // P1：推理强度 / 空转阈值 / 预设方案
    val reasoningBudgetLevel by viewModel.reasoningBudgetLevel.collectAsStateWithLifecycle()
    val idleConvergeRounds by viewModel.idleConvergeRounds.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    // P2：新增护栏 + 用量卡片项
    val dangerousCommandEnabled by viewModel.guardDangerousCommandEnabled.collectAsStateWithLifecycle()
    val largeFileEnabled by viewModel.guardLargeFileEnabled.collectAsStateWithLifecycle()
    val pathBoundaryEnabled by viewModel.guardPathBoundaryEnabled.collectAsStateWithLifecycle()
    val usageCardItems by viewModel.usageCardItems.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf(SettingsSection.Menu) }
    var logReturnSection by remember { mutableStateOf(SettingsSection.Menu) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerConfig?>(null) }
    var showContainerAddSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    // P1：规范查看器导航（0=静态规则, 1=SOP, null=关闭）
    var assetViewerTab by remember { mutableStateOf<Int?>(null) }
    // P2：注入诊断面板 / 护栏日志导航
    var showDiagnosis by remember { mutableStateOf(false) }
    var showGuardLogs by remember { mutableStateOf(false) }
    // P3：规则管理 / 导入导出
    var showRuleManager by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<String?>(null) }
    val clipboardContext = androidx.compose.ui.platform.LocalContext.current
    // 问题6：搜索模式状态（顶栏按钮触发，替代常驻搜索框）
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 搜索历史（KVStore 持久化，由 ViewModel 暴露）
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    // RC62：跨屏跳转（terminal_settings → settings → RemoteServers）：接收来自 SettingsViewModel
    //   的 openSection 请求，切到 SettingsScreen 内部的 section。
    // 注意：这段必须写在 `var section` remember 之后，否则会引用 section 报 Unresolved。
    val pendingTick by viewModel.pendingOpenSectionTick.collectAsStateWithLifecycle()
    val consumedTick by viewModel.lastConsumedSectionTick.collectAsStateWithLifecycle()
    val lastRequestedSection by viewModel.lastRequestedSection.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTick, consumedTick) {
        if (pendingTick > consumedTick) {
            val sec = lastRequestedSection
            if (sec != null && section != sec) {
                section = sec
            }
            viewModel.markPendingSectionConsumed(pendingTick)
        }
    }

    // 处于二级页时，系统返回键先回到上一层；首页时交还给上层导航。
    BackHandler(enabled = section != SettingsSection.Menu) {
        when (section) {
            SettingsSection.ProviderEditor -> section = SettingsSection.Providers
            SettingsSection.Logs -> section = logReturnSection
            else -> section = SettingsSection.Menu
        }
    }

    // 提供商编辑为独立全屏页，直接渲染（不嵌套 Scaffold）
    if (section == SettingsSection.ProviderEditor) {
        ProviderEditorScreen(
            viewModel = viewModel,
            initialProvider = editingProvider,
            onNavigateBack = { section = SettingsSection.Providers },
            onSave = { provider ->
                viewModel.saveProvider(provider)
            },
            onDelete = { id ->
                viewModel.deleteProvider(id)
                section = SettingsSection.Providers
            }
        )
        return
    }

    if (section == SettingsSection.RemoteServers) {
        com.mini.me_core.feature.workspace.presentation.remote.RemoteServerScreen(
            onNavigateBack = { section = SettingsSection.Menu }
        )
        return
    }

    // 运行日志查看器为独立全屏页（自带顶栏：返回/搜索/筛选/导出）
    if (section == SettingsSection.Logs) {
        LogViewerScreen(
            onNavigateBack = { section = logReturnSection }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 模型管理页（Providers section）自带 Scaffold 顶栏，外层不显示顶栏以避免双重顶栏
            if (section != SettingsSection.Providers) {
            // 问题6：Menu 主页且搜索模式开启时，顶栏显示搜索输入框；带 Crossfade 平滑切换
            val animScale = com.mini.me_core.core.theme.LocalAnimationScale.current
            val barAnimDuration = (220L * animScale).toInt().coerceAtLeast(0)
            Crossfade(
                targetState = section == SettingsSection.Menu && isSearchMode,
                animationSpec = tween(barAnimDuration),
                label = "searchTopBar",
            ) { searchMode ->
                if (searchMode) {
                    SearchTopBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isSearchMode = false
                            searchQuery = ""
                        },
                        onSearch = { q ->
                            if (q.isNotBlank()) viewModel.recordSearch(q)
                        },
                        placeholder = stringResource(R.string.settings_search_hint)
                    )
                } else {
                    AppTopAppBar(
                        title = stringResource(section.titleRes),
                        onNavigateBack = {
                            if (section == SettingsSection.Menu) {
                                onNavigateBack()
                            } else if (section == SettingsSection.Logs) {
                                section = logReturnSection
                            } else {
                                section = SettingsSection.Menu
                            }
                        },
                        navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                        navigationContentDescription = stringResource(R.string.common_back)
                    ) {
                        when (section) {
                            // 问题6：Menu 主页顶栏右侧显示搜索按钮
                            SettingsSection.Menu -> IconButton(
                                onClick = { isSearchMode = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.settings_search_hint), modifier = Modifier.size(20.dp))
                            }
                            SettingsSection.McpCenter -> {
                                IconButton(onClick = { viewModel.reloadMcp() }, modifier = Modifier.size(40.dp)) {
                                    if (mcpReloading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.settings_reconnect), modifier = Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = { editingMcp = null; showMcpDialog = true }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.settings_add_mcp_server), modifier = Modifier.size(20.dp))
                                }
                            }
                            SettingsSection.Container -> IconButton(onClick = { showContainerAddSheet = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.container_add_image), modifier = Modifier.size(20.dp))
                            }
                            SettingsSection.Logs -> {
                                IconButton(onClick = { viewModel.refreshLogs() }, modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.settings_refresh_logs), modifier = Modifier.size(20.dp))
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            }
        }
) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when (section) {
                SettingsSection.Logs -> Unit // Logs 已在 Scaffold 前 early return 为独立全屏页
                SettingsSection.Menu -> SettingsMenu(
                    providerCount = providers.size,
                    activeProviderName = activeProvider?.name,
                    activeContainerProfileName = containerProfiles.firstOrNull { it.id == activeProfileId }?.name,
                    visionProviderName = providers.firstOrNull { it.id == visionProviderId }?.name,
                    visionModel = visionModel,
                    compactionProviderName = providers.firstOrNull { it.id == compactionProviderId }?.name,
                    compactionModel = compactionModel,
                    mcpCount = mcpServers.size,
                    mcpConnected = mcpStatuses.count { it.state == McpServerStatus.State.CONNECTED },
                    mcpServerRunning = mcpServerIsRunning,
                    logLevel = logLevel,
                    permissionRuleCount = projectRules.size + globalRules.size,
                    themeMode = themeMode,
                    onOpenThemeSheet = { showThemeSheet = true },
                    keepaliveEnabled = keepaliveEnabled,
                    onToggleKeepalive = { viewModel.setKeepaliveEnabled(it) },
                    onOpen = {
                        if (it == SettingsSection.Logs) {
                            logReturnSection = SettingsSection.Menu
                            viewModel.refreshLogs(filterServerName = null)
                        }
                        section = it
                    },
                    onNavigateToTerminalSettings = onNavigateToTerminalSettings,
                    onNavigateToNetProxy = onNavigateToNetProxy,
                    onNavigateToCapabilityCenter = onNavigateToCapabilityCenter,
                    // 问题6：传入搜索状态（由顶栏搜索框驱动）
                    searchQuery = searchQuery,
                    isSearchMode = isSearchMode,
                    searchHistory = searchHistory,
                    onHistoryClick = { word ->
                        searchQuery = word
                        viewModel.recordSearch(word)
                    },
                    onClearSearchHistory = { viewModel.clearSearchHistory() },
                    onExitSearchMode = {
                        isSearchMode = false
                        searchQuery = ""
                    },
                )
                SettingsSection.Providers -> ModelManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { section = SettingsSection.Menu },
                    onEditProvider = {
                        editingProvider = it
                        section = SettingsSection.ProviderEditor
                    }
                )
                SettingsSection.McpCenter -> McpCenterScreen(
                    servers = mcpServers,
                    statuses = mcpStatuses,
                    reloading = mcpReloading,
                    onReload = { viewModel.reloadMcp() },
                    onToggleServerEnabled = { name, enabled -> viewModel.setMcpServerEnabled(name, enabled) },
                    onEditServer = {
                        editingMcp = it
                        showMcpDialog = true
                    },
                    onDeleteServer = { viewModel.deleteMcpServer(it) },
                    onAddServer = { editingMcp = null; showMcpDialog = true },
                    isRunning = mcpServerIsRunning,
                    port = mcpServerPort,
                    token = mcpServerToken,
                    requireApproval = mcpServerRequireApproval,
                    autoStart = mcpServerAutoStart,
                    serverUrl = mcpServerUrl,
                    errorMessage = mcpServerError,
                    onToggleHostServer = { viewModel.toggleMcpServer() },
                    onSaveHostConfig = { p, r, a -> viewModel.saveMcpServerConfig(p, r, a) },
                    onRegenerateToken = { viewModel.regenerateMcpServerToken() }
                )
                SettingsSection.Container -> ContainerSection(
                    profiles = containerProfiles,
                    activeProfileId = activeProfileId,
                    showAddSheetExternal = showContainerAddSheet,
                    onDismissAddSheet = { showContainerAddSheet = false },
                    onSelect = { viewModel.setActiveContainerProfile(it) },
                    onSaveCustom = { viewModel.saveCustomContainerProfile(it) },
                    onEditCustom = { viewModel.editCustomContainerProfile(it) },
                    onDeleteCustom = { viewModel.deleteCustomContainerProfile(it) },
                    onSwitchConfirmed = onStopAllAndCloseTerminal,
                    onResetBuiltin = { viewModel.resetBuiltinContainer(it) },
                    remoteConnections = remoteConnections,
                    storageShareEnabled = storageShareEnabled,
                    onStorageShareChange = { viewModel.setStorageShareEnabled(it) }
                )
                SettingsSection.Permissions -> PermissionsSection(
                    projectName = currentProjectName,
                    projectRules = projectRules,
                    globalRules = globalRules,
                    onDeleteProject = { viewModel.deleteProjectRule(it) },
                    onPromote = { viewModel.promoteRuleToGlobal(it) },
                    onDeleteGlobal = { viewModel.deleteGlobalRule(it) }
                )
                SettingsSection.NormFlow -> {
                    when {
                        showDiagnosis -> NormFlowDiagnosisScreen(onBack = { showDiagnosis = false })
                        showGuardLogs -> GuardLogsScreen(onBack = { showGuardLogs = false })
                        showRuleManager -> RuleManagerScreen(projectRoot = "", onBack = { showRuleManager = false })
                        assetViewerTab != null -> NormFlowAssetViewerScreen(
                            initialTab = assetViewerTab!!,
                            onBack = { assetViewerTab = null }
                        )
                        else -> NormFlowSection(
                            normFlowEnabled = normFlowEnabled,
                            stepInjectEnabled = stepInjectEnabled,
                            toolGuardEnabled = toolGuardEnabled,
                            fileObservationEnabled = fileObservationEnabled,
                            reasoningBudgetEnabled = reasoningBudgetEnabled,
                            usageCardEnabled = usageCardEnabled,
                            sopSummaryEnabled = sopSummaryEnabled,
                            playbookAutoEnabled = playbookAutoEnabled,
                            idleConvergeEnabled = idleConvergeEnabled,
                            stepInjectGoalEnabled = stepInjectGoalEnabled,
                            stepInjectStaticRulesEnabled = stepInjectStaticRulesEnabled,
                            stepInjectLayeredRulesEnabled = stepInjectLayeredRulesEnabled,
                            stepInjectProjectAgentsEnabled = stepInjectProjectAgentsEnabled,
                            reasoningBudgetLevel = reasoningBudgetLevel,
                            idleConvergeRounds = idleConvergeRounds,
                            activePreset = activePreset,
                            dangerousCommandEnabled = dangerousCommandEnabled,
                            largeFileEnabled = largeFileEnabled,
                            pathBoundaryEnabled = pathBoundaryEnabled,
                            usageCardItems = usageCardItems,
                            onToggleNormFlow = { viewModel.setNormFlowEnabled(it) },
                            onToggleStepInject = { viewModel.setStepInjectEnabled(it) },
                            onToggleToolGuard = { viewModel.setToolGuardEnabled(it) },
                            onToggleFileObservation = { viewModel.setFileObservationEnabled(it) },
                            onToggleReasoningBudget = { viewModel.setReasoningBudgetEnabled(it) },
                            onToggleUsageCard = { viewModel.setUsageCardEnabled(it) },
                            onToggleSopSummary = { viewModel.setSopSummaryEnabled(it) },
                            onTogglePlaybookAuto = { viewModel.setPlaybookAutoEnabled(it) },
                            onToggleIdleConverge = { viewModel.setIdleConvergeEnabled(it) },
                            onToggleStepInjectGoal = { viewModel.setStepInjectGoalEnabled(it) },
                            onToggleStepInjectStaticRules = { viewModel.setStepInjectStaticRulesEnabled(it) },
                            onToggleStepInjectLayeredRules = { viewModel.setStepInjectLayeredRulesEnabled(it) },
                            onToggleStepInjectProjectAgents = { viewModel.setStepInjectProjectAgentsEnabled(it) },
                            onSetReasoningBudgetLevel = { viewModel.setReasoningBudgetLevel(it) },
                            onSetIdleConvergeRounds = { viewModel.setIdleConvergeRounds(it) },
                            onApplyPreset = { viewModel.applyPreset(it) },
                            onViewStaticRules = { assetViewerTab = 0 },
                            onViewSop = { assetViewerTab = 1 },
                            onToggleDangerousCommand = { viewModel.setGuardDangerousCommandEnabled(it) },
                            onToggleLargeFile = { viewModel.setGuardLargeFileEnabled(it) },
                            onTogglePathBoundary = { viewModel.setGuardPathBoundaryEnabled(it) },
                            onToggleUsageCardItem = { viewModel.toggleUsageCardItem(it) },
                            onOpenDiagnosis = { showDiagnosis = true },
                            onOpenGuardLogs = { showGuardLogs = true },
                            onManageRules = { showRuleManager = true },
                            onExportConfig = {
                                val json = viewModel.exportConfig()
                                val cm = clipboardContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("norm_flow_config", json))
                            },
                            onImportConfig = { showImportDialog = true }
                        )
                    }
                }
                SettingsSection.Backup -> {
                    val backupViewModel: com.mini.me_core.feature.backup.presentation.BackupViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    BackupSection(viewModel = backupViewModel)
                }
                SettingsSection.Security -> {
                    val securityViewModel: SecuritySettingsViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    SecuritySettingsScreen(viewModel = securityViewModel)
                }
                SettingsSection.RemoteAuditLogs -> {
                    RemoteAuditLogsScreen(auditLogRepo = viewModel.auditLogRepository)
                }
                SettingsSection.ProviderEditor -> {} // 已在上方 early return 处理
                SettingsSection.RemoteServers -> {} // 已在上方 early return 处理
                SettingsSection.About -> AboutSection()
                SettingsSection.Theme -> {
                    com.mini.me_core.feature.settings.presentation.ThemeSettingsScreen(
                        onNavigateBack = { section = SettingsSection.Menu }
                    )
                }
            }
        }
    }

    // P3：导入配置对话框
    if (showImportDialog) {
        val importSuccessStr = stringResource(R.string.norm_flow_import_success)
        val importFailedStr = stringResource(R.string.norm_flow_import_failed)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportDialog = false; importText = ""; importResult = null },
            title = { Text(stringResource(R.string.norm_flow_import_config)) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(stringResource(R.string.norm_flow_import_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    importResult?.let {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val ok = viewModel.importConfig(importText)
                    importResult = if (ok) importSuccessStr else importFailedStr
                    if (ok) { showImportDialog = false; importText = "" }
                }) { Text(stringResource(R.string.norm_flow_import_config)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showImportDialog = false; importText = ""; importResult = null }) {
                    Text(stringResource(R.string.norm_flow_asset_back))
                }
            }
        )
    }


    if (showMcpDialog) {
        McpServerEditDialog(
            initial = editingMcp,
            tools = viewModel.getMcpServerTools(editingMcp?.name),
            onRefreshTools = { viewModel.reloadMcp() },
            onOpenLogs = editingMcp?.let { existing ->
                {
                    showMcpDialog = false
                    logReturnSection = SettingsSection.McpCenter
                    viewModel.refreshLogs(filterServerName = existing.name)
                    section = SettingsSection.Logs
                }
            },
            onDismiss = { showMcpDialog = false },
            onSave = { config ->
                viewModel.upsertMcpServer(editingMcp?.name, config)
                showMcpDialog = false
            },
            onDelete = editingMcp?.let { existing ->
                {
                    viewModel.deleteMcpServer(existing.name)
                    showMcpDialog = false
                }
            }
        )
    }

    if (showThemeSheet) {
        ThemeSelectionSheet(
            selected = themeMode,
            onSelected = { viewModel.setThemeMode(it) },
            onDismiss = { showThemeSheet = false }
        )
    }
}

internal data class MenuItem(
    val section: SettingsSection?,
    val group: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    /** 彩色图标块背景：日间亮色 / 夜间深色，随主题切换；Color.Unspecified 表示沿用灰色默认。 */
    val iconBgLight: Color = Color.Unspecified,
    val iconBgDark: Color = Color.Unspecified,
    val keywords: List<String>,
    val action: () -> Unit,
    val trailing: @Composable (() -> Unit)? = null
)



/** 设置首页：每个分区一个可点击的二级菜单入口。 */
@Composable
internal fun SettingsMenu(
    providerCount: Int,
    activeProviderName: String?,
    activeContainerProfileName: String?,
    visionProviderName: String?,
    visionModel: String,
    compactionProviderName: String?,
    compactionModel: String,
    mcpCount: Int,
    mcpConnected: Int,
    mcpServerRunning: Boolean,
    logLevel: LogLevel,
    permissionRuleCount: Int,
    themeMode: AppThemeMode,
    onOpenThemeSheet: () -> Unit,
    keepaliveEnabled: Boolean,
    onToggleKeepalive: (Boolean) -> Unit,
    onOpen: (SettingsSection) -> Unit,
    onNavigateToTerminalSettings: () -> Unit = {},
    onNavigateToNetProxy: () -> Unit = {},
    onNavigateToCapabilityCenter: () -> Unit = {},
    // 问题6：搜索词由顶栏搜索框提供，不再内部管理
    searchQuery: String = "",
    isSearchMode: Boolean = false,
    searchHistory: List<String> = emptyList(),
    onHistoryClick: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    onExitSearchMode: () -> Unit = {},
) {
    val themeLabel = stringResource(themeMode.labelRes)

    // 多语言分组名（全部走 strings.xml i18n）
    val groupAI = stringResource(R.string.settings_category_ai_agent)
    val groupEnv = stringResource(R.string.settings_category_environment)
    val groupData = stringResource(R.string.settings_category_data_security)
    val groupSystem = stringResource(R.string.settings_category_system_app)
    // 固定分组顺序（必须用上面 i18n 后的 group key，与 filteredGroups 对齐）
    val groupOrder = listOf(groupAI, groupEnv, groupData, groupSystem)
    val logLevelLabel = stringResource(
        when (logLevel) {
            LogLevel.VERBOSE -> R.string.log_level_verbose
            LogLevel.DEBUG -> R.string.log_level_debug
            LogLevel.INFO -> R.string.log_level_info
            LogLevel.WARN -> R.string.log_level_warn
            LogLevel.ERROR, LogLevel.FATAL -> R.string.log_level_error
            LogLevel.NONE -> R.string.log_level_none
        }
    )

    // section=null 的菜单项，title 用独立的 i18n 资源
    val menuItems: List<MenuItem> = listOf(
        MenuItem(
            section = SettingsSection.Providers,
            group = groupAI,
            title = "模型管理",
            subtitle = run {
                val parts = mutableListOf<String>()
                // 供应商信息：数量 + 活跃供应商
                if (providerCount == 0) {
                    parts.add(stringResource(R.string.settings_providers_empty))
                } else {
                    var providerInfo = stringResource(R.string.settings_providers_count, providerCount)
                    activeProviderName?.let { providerInfo += stringResource(R.string.settings_providers_active, it) }
                    parts.add(providerInfo)
                }
                // 默认模型信息：识图模型 + 压缩模型
                if (!visionProviderName.isNullOrBlank() && visionModel.isNotBlank()) {
                    parts.add(stringResource(R.string.settings_default_models_vision_dedicated, visionProviderName, visionModel))
                }
                if (!compactionProviderName.isNullOrBlank() && compactionModel.isNotBlank()) {
                    parts.add(stringResource(R.string.settings_default_models_compaction_dedicated, compactionProviderName, compactionModel))
                }
                parts.joinToString("\n")
            },
            icon = Icons.Rounded.Cloud,
            iconBgLight = Color(0xFF4C8DFF),
            iconBgDark = Color(0xFF2B4E9E),
            keywords = listOf(
                "provider", stringResource(R.string.ui____8000f187), "api", "key", "providers", stringResource(R.string.ui_____8da5f75a),
                "model", "default", stringResource(R.string.ui____18c63459), stringResource(R.string.ui____faa3c555), "vision", stringResource(R.string.ui____6612548a), "compaction", stringResource(R.string.ui____8000f187_2), stringResource(R.string.ui_____37acc94c)
            ),
            action = { onOpen(SettingsSection.Providers) }
        ),
        MenuItem(
            section = SettingsSection.McpCenter,
            group = groupAI,
            title = stringResource(SettingsSection.McpCenter.titleRes),
            subtitle = buildString {
                if (mcpCount == 0) {
                    append(stringResource(R.string.settings_mcp_empty))
                } else {
                    append(stringResource(R.string.settings_mcp_count_connected, mcpCount, mcpConnected))
                }
                append(" · ")
                append(stringResource(
                    if (mcpServerRunning) R.string.settings_mcp_server_running
                    else R.string.settings_mcp_server_stopped
                ))
            },
            icon = Icons.Rounded.Extension,
            iconBgLight = Color(0xFF00B4A8),
            iconBgDark = Color(0xFF0E6E68),
            keywords = listOf("mcp", "server", stringResource(R.string.ui____20dce2c6), "function", stringResource(R.string.ui____faa1ad5e), stringResource(R.string.ui_____c566ca59)),
            action = { onOpen(SettingsSection.McpCenter) }
        ),
        MenuItem(
            section = SettingsSection.Permissions,
            group = groupAI,
            title = stringResource(SettingsSection.Permissions.titleRes),
            subtitle = if (permissionRuleCount == 0)
                stringResource(R.string.settings_permissions_empty)
            else
                stringResource(R.string.settings_permissions_count, permissionRuleCount),
            icon = Icons.Rounded.Lock,
            iconBgLight = Color(0xFFFF9F43),
            iconBgDark = Color(0xFF9A5B1E),
            keywords = listOf("perm", stringResource(R.string.ui____98a315c0), stringResource(R.string.ui____b0fae043), "permission", "allow", stringResource(R.string.ui____20dce2c6_2), "tool"),
            action = { onOpen(SettingsSection.Permissions) }
        ),
        // D1-7 规范流程统一开关（对齐 norm-chain §3.5）：总开关 + step_inject/tool_guard 子开关。
        MenuItem(
            section = SettingsSection.NormFlow,
            group = groupAI,
            title = stringResource(SettingsSection.NormFlow.titleRes),
            subtitle = stringResource(R.string.settings_norm_flow_subtitle),
            icon = Icons.Rounded.FactCheck,
            iconBgLight = Color(0xFF06B6D4),
            iconBgDark = Color(0xFF155E75),
            keywords = listOf("norm", "flow", "step", "inject", "guard", stringResource(R.string.settings_norm_flow)),
            action = { onOpen(SettingsSection.NormFlow) }
        ),
        // 能力中心入口（自侧边栏移入设置）：技能管理、MCP 工具服务等，独立路由跳转，不走 section 切换。
        MenuItem(
            section = null,
            group = groupAI,
            title = stringResource(R.string.capability_center_title),
            subtitle = stringResource(R.string.capability_center_subtitle),
            icon = Icons.Rounded.Dashboard,
            iconBgLight = Color(0xFF8B5CF6),
            iconBgDark = Color(0xFF4C1D95),
            keywords = listOf(
                "skill", "skills", "capability",
                stringResource(R.string.capability_tab_skills),
                stringResource(R.string.capability_tab_tools),
                "mcp"
            ),
            action = onNavigateToCapabilityCenter
        ),
        MenuItem(
            section = null,
            group = groupEnv,
            title = stringResource(R.string.settings_terminal),
            subtitle = stringResource(R.string.settings_terminal_subtitle),
            icon = Icons.Rounded.Terminal,
            iconBgLight = Color(0xFF22C55E),
            iconBgDark = Color(0xFF14693A),
            keywords = listOf("terminal", stringResource(R.string.ui____4722bc0c), "ssh", "shell", "bash", stringResource(R.string.ui____ddf7d2a5)),
            action = onNavigateToTerminalSettings
        ),
        MenuItem(
            section = null,
            group = groupEnv,
            title = stringResource(R.string.ui______d3ec5010),
            subtitle = stringResource(R.string.ui_mihomo_8fdcce67),
            icon = Icons.Rounded.Public,
            iconBgLight = Color(0xFF6366F1),
            iconBgDark = Color(0xFF4338CA),
            keywords = listOf("proxy", stringResource(R.string.ui____fc954d25), "vpn", "clash", "mihomo", stringResource(R.string.ui____02daf71f), "network"),
            action = onNavigateToNetProxy
        ),
        MenuItem(
            section = SettingsSection.Container,
            group = groupEnv,
            title = stringResource(SettingsSection.Container.titleRes),
            subtitle = stringResource(
                R.string.settings_container_current,
                activeContainerProfileName ?: stringResource(R.string.settings_container_builtin_alpine)
            ),
            icon = Icons.Rounded.Storage,
            iconBgLight = Color(0xFF14B8A6),
            iconBgDark = Color(0xFF0F766E),
            keywords = listOf("container", "docker", stringResource(R.string.ui____34772285), "alpine", stringResource(R.string.ui____22c79904), "proot", stringResource(R.string.ui____fa405f59)),
            action = { onOpen(SettingsSection.Container) }
        ),
        MenuItem(
            section = SettingsSection.RemoteServers,
            group = groupEnv,
            title = stringResource(SettingsSection.RemoteServers.titleRes),
            subtitle = stringResource(R.string.settings_remote_subtitle),
            icon = Icons.Rounded.Lan,
            iconBgLight = Color(0xFF3B82F6),
            iconBgDark = Color(0xFF1E40AF),
            keywords = listOf("remote", "ssh", "sftp", stringResource(R.string.ui_____c566ca59_3), stringResource(R.string.ui_____4fa8c1a3), stringResource(R.string.ui____6a620e3c), stringResource(R.string.ui____a7d3091f)),
            action = { onOpen(SettingsSection.RemoteServers) }
        ),
        MenuItem(
            section = SettingsSection.Backup,
            group = groupData,
            title = stringResource(SettingsSection.Backup.titleRes),
            subtitle = stringResource(R.string.settings_backup_subtitle),
            icon = Icons.Rounded.Archive,
            iconBgLight = Color(0xFFF59E0B),
            iconBgDark = Color(0xFF92400E),
            keywords = listOf("backup", stringResource(R.string.ui____664b37da), stringResource(R.string.ui____69de8d7f), "export", stringResource(R.string.ui____55405ea6), stringResource(R.string.ui____8d9a071e), stringResource(R.string.ui____56563edf)),
            action = { onOpen(SettingsSection.Backup) }
        ),
        MenuItem(
            section = SettingsSection.Security,
            group = groupData,
            title = stringResource(SettingsSection.Security.titleRes),
            subtitle = stringResource(R.string.settings_security_subtitle),
            icon = Icons.Rounded.Security,
            iconBgLight = Color(0xFFEF4444),
            iconBgDark = Color(0xFFB91C1C),
            keywords = listOf("security", stringResource(R.string.ui____56563edf_2), stringResource(R.string.ui______05ad4f31), stringResource(R.string.ui____5f811dd8), "password", stringResource(R.string.ui____fdbc77bd), "pin"),
            action = { onOpen(SettingsSection.Security) }
        ),
        MenuItem(
            section = SettingsSection.RemoteAuditLogs,
            group = groupData,
            title = stringResource(SettingsSection.RemoteAuditLogs.titleRes),
            subtitle = stringResource(R.string.settings_remote_audit_subtitle),
            icon = Icons.Rounded.Description,
            iconBgLight = Color(0xFF64748B),
            iconBgDark = Color(0xFF475569),
            keywords = listOf("audit", stringResource(R.string.ui____771dc11a), "log", stringResource(R.string.ui____30f7dd4e), stringResource(R.string.ui____10b2761d), "ssh", stringResource(R.string.ui____664b37da_2)),
            action = { onOpen(SettingsSection.RemoteAuditLogs) }
        ),
        MenuItem(
            section = SettingsSection.Logs,
            group = groupData,
            title = stringResource(SettingsSection.Logs.titleRes),
            subtitle = stringResource(R.string.settings_log_subtitle, logLevelLabel),
            icon = Icons.Rounded.Notes,
            iconBgLight = Color(0xFFF97316),
            iconBgDark = Color(0xFF9A3412),
            keywords = listOf("log", stringResource(R.string.ui____456d29ef), "debug", "trace", stringResource(R.string.ui____7030ff64), "bug", "filter"),
            action = { onOpen(SettingsSection.Logs) }
        ),
        MenuItem(
            section = SettingsSection.Theme,
            group = groupSystem,
            title = stringResource(R.string.settings_theme_title),
            subtitle = stringResource(R.string.settings_log_current, themeLabel),
            icon = Icons.Rounded.DarkMode,
            iconBgLight = Color(0xFF8B5CF6),
            iconBgDark = Color(0xFF4C1D95),
            keywords = listOf("theme", "appearance", stringResource(R.string.ui____afcde261), stringResource(R.string.ui____41e8e8b9), stringResource(R.string.ui____48d0a09b), stringResource(R.string.ui____f0789e79), stringResource(R.string.ui____9970ad07)),
            action = { onOpen(SettingsSection.Theme) }
        ),
        MenuItem(
            section = null,
            group = groupSystem,
            title = stringResource(R.string.settings_keepalive_title),
            subtitle = stringResource(R.string.settings_keepalive_subtitle),
            icon = Icons.Rounded.Favorite,
            iconBgLight = Color(0xFF2DD4BF),
            iconBgDark = Color(0xFF115E59),
            keywords = listOf("keepalive", stringResource(R.string.ui____bc28072c), stringResource(R.string.ui____066ae8d7), "foreground", stringResource(R.string.ui____5660bcd2), stringResource(R.string.ui____f0d6210d), stringResource(R.string.ui____f88522cf)),
            action = { onToggleKeepalive(!keepaliveEnabled) },
            trailing = {
                Switch(
                    checked = keepaliveEnabled,
                    onCheckedChange = onToggleKeepalive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFFFFF),
                        checkedTrackColor = Color(0xFF0984E3).copy(alpha = 0.55f)
                    )
                )
            }
        ),
        MenuItem(
            section = SettingsSection.About,
            group = groupSystem,
            title = stringResource(SettingsSection.About.titleRes),
            subtitle = stringResource(R.string.settings_about_subtitle),
            icon = Icons.Rounded.Info,
            iconBgLight = Color(0xFF0EA5E9),
            iconBgDark = Color(0xFF0369A1),
            keywords = listOf("about", stringResource(R.string.ui____81d9f505), "version", "release", stringResource(R.string.ui____32ac152b), stringResource(R.string.ui_____20a28457), "license", stringResource(R.string.ui____62cea749)),
            action = { onOpen(SettingsSection.About) }
        )
    )

    val filteredGroups = remember(searchQuery, menuItems) {
        val query = searchQuery.trim()
        val filtered = if (query.isEmpty()) {
            menuItems
        } else {
            // 使用 SearchUtils 打分排序
            menuItems.map { item ->
                item to SearchUtils.score(item, query)
            }
                .filter { (_, score) -> score > 0.0 }
                .sortedByDescending { (_, score) -> score }
                .map { (item, _) -> item }
        }
        filtered.groupBy { it.group }.filter { it.value.isNotEmpty() }
    }

    val searchResultCount = remember(searchQuery, filteredGroups) {
        if (searchQuery.isBlank()) null else filteredGroups.values.sumOf { it.size }
    }

    val hasSearchQuery = searchQuery.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 问题6：移除常驻 CyberSearchBar，搜索改为顶栏按钮触发模式

        // 搜索结果区域（可滚动）
        // 问题5修复：添加水平 padding（Spacing.lg=16dp），让菜单卡片不贴屏幕边缘
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            when {
                // 搜索模式 + 空输入：展示搜索历史
                isSearchMode && searchQuery.isBlank() -> {
                    SearchHistorySection(
                        history = searchHistory,
                        onHistoryClick = onHistoryClick,
                        onClearHistory = onClearSearchHistory,
                    )
                }
                // 搜索模式 + 有输入 + 无结果：空状态
                hasSearchQuery && searchResultCount == 0 -> {
                    EmptySearchResult(query = searchQuery)
                }
                // 搜索模式 + 有输入 + 有结果：计数行 + 分组结果
                hasSearchQuery -> {
                    SearchResultCountRow(count = searchResultCount ?: 0)
                    for (groupName in groupOrder) {
                        val items = filteredGroups[groupName] ?: continue
                        CyberSectionHeader(text = groupName)
                        CyberCard {
                            Column {
                                items.forEachIndexed { index, item ->
                                    CyberMenuRow(
                                        icon = item.icon,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        onClick = {
                                            // 点击结果：先退出搜索模式，再执行跳转
                                            if (isSearchMode) onExitSearchMode()
                                            item.action()
                                        },
                                        showDivider = index < items.size - 1,
                                        highlightQuery = searchQuery,
                                        iconBg = if (item.iconBgLight != Color.Unspecified || item.iconBgDark != Color.Unspecified) {
                                            if (LocalAppDarkMode.current) item.iconBgDark else item.iconBgLight
                                        } else null,
                                        trailing = item.trailing ?: {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // 非搜索模式：展示全部分组
                else -> {
                    for (groupName in groupOrder) {
                        val items = filteredGroups[groupName] ?: continue
                        CyberSectionHeader(text = groupName)
                        CyberCard {
                            Column {
                                items.forEachIndexed { index, item ->
                                    CyberMenuRow(
                                        icon = item.icon,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        onClick = item.action,
                                        showDivider = index < items.size - 1,
                                        highlightQuery = "",
                                        iconBg = if (item.iconBgLight != Color.Unspecified || item.iconBgDark != Color.Unspecified) {
                                            if (LocalAppDarkMode.current) item.iconBgDark else item.iconBgLight
                                        } else null,
                                        trailing = item.trailing ?: {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/**
 * 空搜索结果状态：图标 + 标题 + 副标题。
 * 视觉走 ComponentTokens.emptyState 令牌。
 */
@Composable
private fun EmptySearchResult(query: String) {
    val tokens = LocalComponentTokens.current
    val es = tokens.emptyState
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = PrimitiveSpacing.Xxxxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = es.iconTintColor,
            modifier = Modifier.size(es.iconSize)
        )
        Spacer(Modifier.height(es.spacingAfterIcon))
        Text(
            text = stringResource(R.string.settings_search_empty_title),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = es.titleFontWeight),
            color = es.titleColor
        )
        Spacer(Modifier.height(es.spacingAfterTitle))
        Text(
            text = stringResource(R.string.settings_search_empty_subtitle, query),
            style = MaterialTheme.typography.bodyMedium,
            color = es.subtitleColor
        )
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/** 分组内菜单行：无边框、无 Card，带可选底部分割线。 */
@Composable
internal fun GroupMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/** 分组内开关行：无边框、无 Card，带 Switch。可选 onViewClick 在 Switch 旁显示「查看」按钮。 */
@Composable
internal fun GroupSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onViewClick: (() -> Unit)? = null,
    isChild: Boolean = false,
    valueSummary: String? = null
) {
    val startPadding = if (isChild) Spacing.lg + 54.dp else Spacing.lg
    Column(
        modifier = Modifier.then(
            if (!enabled) Modifier.alpha(0.4f) else Modifier
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPadding, end = Spacing.lg)
                .padding(top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconSize = if (isChild) 18.dp else 22.dp
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = if (isChild) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = if (isChild) MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (valueSummary != null) {
                    Text(
                        text = valueSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (onViewClick != null) {
                IconButton(
                    onClick = onViewClick,
                    enabled = enabled
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.norm_flow_asset_view),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/** 二级菜单入口行：图标 + 标题 + 摘要 + 右箭头。 */
@Composable
internal fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(LocalCornerRadius.current.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
