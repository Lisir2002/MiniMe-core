package com.mini.me_core.feature.terminal.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.core.theme.components.AppSectionGroup
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.feature.agent.domain.container.ContainerArch
import com.mini.me_core.feature.agent.domain.container.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.terminal.data.repository.SshHeartbeatSeconds
import com.mini.me_core.feature.terminal.data.repository.TerminalFontSizes
import com.mini.me_core.feature.terminal.data.repository.TerminalTheme
import com.mini.me_core.feature.terminal.presentation.TerminalSettingsViewModel
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 合并后的「终端与容器」Tab 页面。
 *
 * 顶部胶囊式 Tab 切换 [容器] / [终端]：
 * - 容器 Tab：状态卡片 + 存储共享 + Profile 列表 + 添加镜像
 * - 终端 Tab：外观 / 键盘交互 / 行为 / SSH 四分组设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalContainerScreen(
    viewModel: TerminalSettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSshHosts: () -> Unit,
    onNavigateToBundleManager: () -> Unit = {}
) {
    val context = LocalContext.current
    // ── 收集 ViewModel 状态 ──
    val containerInit by viewModel.containerInit.collectAsStateWithLifecycle()
    val containerInstalled by viewModel.containerInstalled.collectAsStateWithLifecycle()
    val storageUsedMb by viewModel.storageUsedMb.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val storageShareEnabled by viewModel.storageShareEnabled.collectAsStateWithLifecycle()
    val remoteConnections by viewModel.remoteConnections.collectAsStateWithLifecycle()
    val runningSessionCount by viewModel.runningSessionCount.collectAsStateWithLifecycle()
    val isRemoteMode by viewModel.isRemoteMode.collectAsStateWithLifecycle()

    val fontSizeSp by viewModel.fontSizeSp.collectAsStateWithLifecycle()
    val theme by viewModel.terminalTheme.collectAsStateWithLifecycle()
    val showTabBar by viewModel.showTabBar.collectAsStateWithLifecycle()
    val fullExtraKeys by viewModel.fullExtraKeys.collectAsStateWithLifecycle()
    val scalePersists by viewModel.scaleGesturePersists.collectAsStateWithLifecycle()
    val autoPopIme by viewModel.autoPopImeOnSwitch.collectAsStateWithLifecycle()
    val newOutputIndicator by viewModel.newOutputIndicator.collectAsStateWithLifecycle()
    val autoNewTabOnCloseLast by viewModel.autoNewTabOnCloseLast.collectAsStateWithLifecycle()
    val keepSession by viewModel.keepSessionWhenLeave.collectAsStateWithLifecycle()
    val pasteAsPlain by viewModel.pasteAsPlainText.collectAsStateWithLifecycle()
    val sshAutoReconnect by viewModel.sshAutoReconnect.collectAsStateWithLifecycle()
    val sshHeartbeat by viewModel.sshHeartbeatSeconds.collectAsStateWithLifecycle()
    val sshKeepalive by viewModel.sshKeepalive.collectAsStateWithLifecycle()

    val errorToast by viewModel.errorToast.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(errorToast) {
        errorToast?.let {
            scope.launch { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
            viewModel.consumeErrorToast()
        }
    }

    // ── UI 状态 ──
    var selectedTab by remember { mutableIntStateOf(0) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showMirrorPicker by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }
    var showHeartbeatPicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var pendingSwitchProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var pendingResetBuiltin by remember { mutableStateOf<ContainerProfile?>(null) }
    var highlightedProfileId by remember { mutableStateOf<String?>(null) }

    // 每个 Tab 独立的滚动状态
    val containerScrollState = rememberScrollState()
    val terminalScrollState = rememberScrollState()
    // F3.1：容器 Tab 内子切换（0=状态/镜像 1=文件管理器）
    var containerSubTab by remember { mutableIntStateOf(0) }

    // 新添加 profile 后高亮闪烁
    LaunchedEffect(highlightedProfileId) {
        if (highlightedProfileId != null) {
            delay(2000)
            highlightedProfileId = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.settings_terminal_container),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 胶囊式 Tab 切换 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(LocalCornerRadius.current.xl)
                    )
                    .padding(PrimitiveSpacing.Xs),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tabs = listOf(
                    stringResource(R.string.tc_tab_container),
                    stringResource(R.string.tc_tab_terminal)
                )
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(LocalCornerRadius.current.lg))
                            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Tab 内容 ──
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> Column(modifier = Modifier.fillMaxSize()) {
                        // F3.1：容器子切换 [状态] [文件]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            listOf(
                                stringResource(R.string.tc_subtab_status) to 0,
                                stringResource(R.string.tc_subtab_files) to 1
                            ).forEach { (label, idx) ->
                                val isSel = containerSubTab == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
                                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { containerSubTab = idx }
                                        .padding(horizontal = Spacing.md, vertical = 4.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        when (containerSubTab) {
                            1 -> ContainerFileManager(
                                access = viewModel.fileAccess,
                                modifier = Modifier.weight(1f)
                            )
                            else -> ContainerTabContent(
                                scrollState = containerScrollState,
                                viewModel = viewModel,
                                containerInit = containerInit,
                                containerInstalled = containerInstalled,
                                storageUsedMb = storageUsedMb,
                                profiles = profiles,
                                activeProfile = activeProfile,
                                activeProfileId = activeProfileId,
                                storageShareEnabled = storageShareEnabled,
                                remoteConnections = remoteConnections,
                                isRemoteMode = isRemoteMode,
                                highlightedProfileId = highlightedProfileId,
                                onInit = viewModel::ensureContainerInstalled,
                                onRestart = viewModel::restartContainer,
                                onReset = { showResetConfirm = true },
                                onPickMirror = { showMirrorPicker = true },
                                onSwitchImage = { showImagePicker = true },
                                onStorageShareChange = viewModel::setStorageShareEnabled,
                                onSelectProfile = { profile ->
                                    if (runningSessionCount > 0 && profile.id != activeProfileId) {
                                        pendingSwitchProfile = profile
                                    } else {
                                        viewModel.setActiveContainerProfile(profile.id)
                                    }
                                },
                                onEditProfile = { editingProfile = it },
                                onDeleteProfile = { deletingProfile = it },
                                onResetBuiltin = { pendingResetBuiltin = it },
                                onAddProfile = { showProfileSheet = true },
                            )
                        }
                    }
                    1 -> TerminalTabContent(
                        scrollState = terminalScrollState,
                        fontSizeSp = fontSizeSp,
                        theme = theme,
                        showTabBar = showTabBar,
                        fullExtraKeys = fullExtraKeys,
                        scalePersists = scalePersists,
                        autoPopIme = autoPopIme,
                        newOutputIndicator = newOutputIndicator,
                        autoNewTabOnCloseLast = autoNewTabOnCloseLast,
                        keepSession = keepSession,
                        pasteAsPlain = pasteAsPlain,
                        sshAutoReconnect = sshAutoReconnect,
                        sshHeartbeat = sshHeartbeat,
                        sshKeepalive = sshKeepalive,
                        onFontSizeDecrease = {
                            val steps = TerminalFontSizes.STEPS
                            val idx = steps.binarySearch(fontSizeSp).let { if (it < 0) -it - 1 else it }
                            if (idx - 1 >= 0) viewModel.setFontSizeSp(steps[idx - 1])
                        },
                        onFontSizeIncrease = {
                            val steps = TerminalFontSizes.STEPS
                            val idx = steps.binarySearch(fontSizeSp).let { if (it < 0) -it - 1 else it }
                            if (idx + 1 <= steps.lastIndex) viewModel.setFontSizeSp(steps[idx + 1])
                        },
                        onThemeClick = { showThemePicker = true },
                        onShowTabBarChange = viewModel::setShowTabBar,
                        onFullExtraKeysChange = viewModel::setFullExtraKeys,
                        onScalePersistsChange = viewModel::setScaleGesturePersists,
                        onAutoPopImeChange = viewModel::setAutoPopImeOnSwitch,
                        onResetCtrlHint = viewModel::resetCtrlHint,
                        onNewOutputIndicatorChange = viewModel::setNewOutputIndicator,
                        onAutoNewTabChange = viewModel::setAutoNewTabOnCloseLast,
                        onKeepSessionChange = viewModel::setKeepSessionWhenLeave,
                        onPasteAsPlainChange = viewModel::setPasteAsPlainText,
                        onSshAutoReconnectChange = viewModel::setSshAutoReconnect,
                        onSshKeepaliveChange = viewModel::setSshKeepalive,
                        onHeartbeatClick = { showHeartbeatPicker = true },
                        onSshHostsClick = onNavigateToSshHosts,
                    )
                }
            }
        }
    }

    // ── Dialogs ──

    // 切换 Profile 安全检查
    pendingSwitchProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitchProfile = null },
            title = { Text(stringResource(R.string.tc_switch_warning_title)) },
            text = { Text(stringResource(R.string.tc_switch_warning_message, runningSessionCount)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setActiveContainerProfile(target.id)
                    pendingSwitchProfile = null
                }) {
                    Text(stringResource(R.string.tc_force_switch), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitchProfile = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 删除 Profile 确认
    deletingProfile?.let { deleting ->
        val isActiveDeleting = deleting.id == activeProfileId
        val freeSpaceText = if (deleting.mode == ExecutionMode.LOCAL_PROOT && !deleting.isBuiltin) {
            stringResource(R.string.tc_delete_free_space, storageUsedMb.toInt())
        } else ""
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text(stringResource(R.string.container_delete_config)) },
            text = {
                Column {
                    Text(stringResource(R.string.container_delete_confirm, deleting.name, ""))
                    if (isActiveDeleting) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.tc_delete_active_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (freeSpaceText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(freeSpaceText, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomContainerProfile(deleting)
                    deletingProfile = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 重置容器确认
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.ui______8415b836)) },
            text = {
                Text(stringResource(R.string.tc_reset_confirm_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetContainer { showResetConfirm = false }
                }) {
                    Text(stringResource(R.string.tc_reset_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.ui____625fb26b_3))
                }
            }
        )
    }

    // 重置内置 Profile 确认
    pendingResetBuiltin?.let { resetting ->
        AlertDialog(
            onDismissRequest = { pendingResetBuiltin = null },
            title = { Text(stringResource(R.string.container_reset_builtin)) },
            text = { Text(stringResource(R.string.container_reset_confirm, resetting.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetBuiltinContainer(resetting)
                    pendingResetBuiltin = null
                }) {
                    Text(stringResource(R.string.container_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetBuiltin = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 镜像源选择
    if (showMirrorPicker) {
        MirrorPickerDialogInternal(
            current = ContainerInstaller.ALPINE_MIRROR,
            onDismiss = { showMirrorPicker = false },
            onConfirm = { mirror ->
                viewModel.setMirrorAndRefresh(mirror)
                showMirrorPicker = false
            }
        )
    }

    // 切换镜像选择
    if (showImagePicker) {
        ImagePickerDialogInternal(
            profiles = profiles,
            activeProfileId = activeProfileId,
            onDismiss = { showImagePicker = false },
            onPick = { profile ->
                showImagePicker = false
                when {
                    profile.id == activeProfileId -> {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.tc_current_image_using),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    profiles.size <= 1 -> {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.tc_no_other_images),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                    runningSessionCount > 0 -> pendingSwitchProfile = profile
                    else -> viewModel.setActiveContainerProfile(profile.id)
                }
            }
        )
    }

    // 主题选择
    if (showThemePicker) {
        ThemePickerDialogInternal(
            current = theme,
            onDismiss = { showThemePicker = false },
            onConfirm = { t ->
                viewModel.setTheme(t)
                showThemePicker = false
            }
        )
    }

    // 心跳间隔选择
    if (showHeartbeatPicker) {
        HeartbeatPickerDialogInternal(
            current = sshHeartbeat,
            onDismiss = { showHeartbeatPicker = false },
            onConfirm = { s ->
                viewModel.setSshHeartbeatSeconds(s)
                showHeartbeatPicker = false
            }
        )
    }

    // 添加/编辑镜像 BottomSheet
    if (showProfileSheet) {
        ProfileEditSheet(
            initial = null,
            remoteConnections = remoteConnections,
            onDismiss = { showProfileSheet = false },
            onConfirm = { profile ->
                val id = "custom-${System.currentTimeMillis()}"
                val finalProfile = if (profile.mode == ExecutionMode.REMOTE_SSH) {
                    profile.copy(id = id, name = profile.name.ifBlank { "远程 SSH" })
                } else {
                    profile.copy(id = id, name = profile.name.ifBlank { "自定义镜像" })
                }
                viewModel.saveCustomContainerProfile(finalProfile)
                highlightedProfileId = id
                showProfileSheet = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.tc_new_profile_hint),
                        duration = SnackbarDuration.Long
                    )
                }
            }
        )
    }

    editingProfile?.let { editing ->
        ProfileEditSheet(
            initial = editing,
            remoteConnections = remoteConnections,
            onDismiss = { editingProfile = null },
            onConfirm = { newProfile ->
                viewModel.editCustomContainerProfile(editing, newProfile.copy(id = editing.id))
                editingProfile = null
            }
        )
    }
}

// ================================================================
// 容器 Tab 内容
// ================================================================
@Composable
private fun ContainerTabContent(
    scrollState: androidx.compose.foundation.ScrollState,
    viewModel: TerminalSettingsViewModel,
    containerInit: com.mini.me_core.feature.agent.domain.container.ContainerInitState,
    containerInstalled: Boolean,
    storageUsedMb: Long,
    profiles: List<ContainerProfile>,
    activeProfile: ContainerProfile?,
    activeProfileId: String,
    storageShareEnabled: Boolean,
    remoteConnections: List<RemoteConnection>,
    isRemoteMode: Boolean,
    highlightedProfileId: String?,
    onInit: () -> Unit,
    onRestart: () -> Unit,
    onReset: () -> Unit,
    onPickMirror: () -> Unit,
    onSwitchImage: () -> Unit,
    onStorageShareChange: (Boolean) -> Unit,
    onSelectProfile: (ContainerProfile) -> Unit,
    onEditProfile: (ContainerProfile) -> Unit,
    onDeleteProfile: (ContainerProfile) -> Unit,
    onResetBuiltin: (ContainerProfile) -> Unit,
    onAddProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 1. 状态卡片
        ContainerStatusCard(
            containerInstalled = containerInstalled,
            initProgress = containerInit,
            storageUsedMb = storageUsedMb,
            activeProfile = activeProfile,
            remoteConnections = remoteConnections,
            onInit = onInit,
            onRestart = onRestart,
            onReset = onReset,
            onSwitchImage = onSwitchImage,
            onPickMirror = onPickMirror,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )

        // 2. 存储与共享
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            shape = RoundedCornerShape(LocalCornerRadius.current.lg),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.container_share_storage),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRemoteMode)
                            stringResource(R.string.tc_remote_mode_not_applicable)
                        else
                            stringResource(R.string.container_share_storage_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
                Switch(
                    checked = storageShareEnabled && !isRemoteMode,
                    onCheckedChange = onStorageShareChange,
                    enabled = !isRemoteMode
                )
            }
        }

        // 3. 镜像 Profile 列表
        profiles.forEach { profile ->
            ContainerProfileCard(
                profile = profile,
                isActive = profile.id == activeProfileId,
                remoteConnections = remoteConnections,
                highlight = profile.id == highlightedProfileId,
                onSelect = { onSelectProfile(profile) },
                onEdit = { onEditProfile(profile) },
                onDelete = { onDeleteProfile(profile) },
                onReset = { onResetBuiltin(profile) },
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
        }

        // 添加镜像按钮
        OutlinedButton(
            onClick = onAddProfile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(stringResource(R.string.tc_add_image))
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

// ================================================================
// 终端 Tab 内容（迁移自 TerminalSettingsScreen）
// ================================================================
@Composable
private fun TerminalTabContent(
    scrollState: androidx.compose.foundation.ScrollState,
    fontSizeSp: Int,
    theme: TerminalTheme,
    showTabBar: Boolean,
    fullExtraKeys: Boolean,
    scalePersists: Boolean,
    autoPopIme: Boolean,
    newOutputIndicator: Boolean,
    autoNewTabOnCloseLast: Boolean,
    keepSession: Boolean,
    pasteAsPlain: Boolean,
    sshAutoReconnect: Boolean,
    sshHeartbeat: Int,
    sshKeepalive: Boolean,
    onFontSizeDecrease: () -> Unit,
    onFontSizeIncrease: () -> Unit,
    onThemeClick: () -> Unit,
    onShowTabBarChange: (Boolean) -> Unit,
    onFullExtraKeysChange: (Boolean) -> Unit,
    onScalePersistsChange: (Boolean) -> Unit,
    onAutoPopImeChange: (Boolean) -> Unit,
    onResetCtrlHint: () -> Unit,
    onNewOutputIndicatorChange: (Boolean) -> Unit,
    onAutoNewTabChange: (Boolean) -> Unit,
    onKeepSessionChange: (Boolean) -> Unit,
    onPasteAsPlainChange: (Boolean) -> Unit,
    onSshAutoReconnectChange: (Boolean) -> Unit,
    onSshKeepaliveChange: (Boolean) -> Unit,
    onHeartbeatClick: () -> Unit,
    onSshHostsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // G1 外观
        AppSectionHeader(title = stringResource(R.string.settings_category_appearance))
        AppSectionGroup {
            StepperRow(
                icon = Icons.Rounded.TextFields,
                title = stringResource(R.string.ui______58a3ff82),
                subtitle = "${fontSizeSp} sp（推荐 11-14 sp）",
                onDecrease = onFontSizeDecrease,
                onIncrease = onFontSizeIncrease,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.ui________48cee970),
                subtitle = when (theme) {
                    TerminalTheme.FOLLOW_APP -> stringResource(R.string.ui______40b081ab)
                    TerminalTheme.PURE_BLACK -> stringResource(R.string.ui______f5242d83)
                    TerminalTheme.PURE_WHITE -> stringResource(R.string.ui______c68108f0)
                    else -> theme.stableKey
                },
                onViewClick = onThemeClick,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.ViewColumn,
                title = stringResource(R.string.ui____caa23c17),
                subtitle = stringResource(R.string.ui______________aeee5eb0),
                checked = showTabBar,
                onCheckedChange = onShowTabBarChange,
                showDivider = false
            )
        }

        // G2 键盘 & 交互
        AppSectionHeader(title = stringResource(R.string.ui____e1dd53a4))
        AppSectionGroup {
            AppListItem(
                icon = Icons.Rounded.Dashboard,
                title = stringResource(R.string.ui________bd4b33a0),
                subtitle = "关闭为精简布局（仅 Ctrl/Alt/Fn/方向）",
                checked = fullExtraKeys,
                onCheckedChange = onFullExtraKeysChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.ZoomIn,
                title = stringResource(R.string.ui___________19a36c2d),
                subtitle = stringResource(R.string.ui______________82f9dac5),
                checked = scalePersists,
                onCheckedChange = onScalePersistsChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.Terminal,
                title = stringResource(R.string.ui__________a6cbf757),
                subtitle = stringResource(R.string.ui______________05546ade),
                checked = autoPopIme,
                onCheckedChange = onAutoPopImeChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.ui____2959acd6),
                subtitle = stringResource(R.string.ui_____18a3cbe4),
                onClick = onResetCtrlHint,
                showDivider = false
            )
        }

        // G3 行为
        AppSectionHeader(title = stringResource(R.string.ui____a0496123))
        AppSectionGroup {
            AppListItem(
                icon = Icons.Rounded.Notifications,
                title = stringResource(R.string.ui_____________996140c5),
                subtitle = stringResource(R.string.ui_____46f74d41),
                checked = newOutputIndicator,
                onCheckedChange = onNewOutputIndicatorChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Default.Add,
                title = stringResource(R.string.ui_____________0184da1b),
                subtitle = stringResource(R.string.ui_____________73b957c1),
                checked = autoNewTabOnCloseLast,
                onCheckedChange = onAutoNewTabChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.Archive,
                title = stringResource(R.string.ui___________37172e85),
                subtitle = stringResource(R.string.ui______________427e9b33),
                checked = keepSession,
                onCheckedChange = onKeepSessionChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.ContentPaste,
                title = stringResource(R.string.ui________52007b14),
                subtitle = stringResource(R.string.ui______________eb6dcb95),
                checked = pasteAsPlain,
                onCheckedChange = onPasteAsPlainChange,
                showDivider = false
            )
        }

        // G4 SSH 常用
        AppSectionHeader(title = stringResource(R.string.ui_ssh_ac7515bd))
        AppSectionGroup {
            AppListItem(
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.ui________66d1f9aa),
                subtitle = stringResource(R.string.ui______f096b834),
                checked = sshAutoReconnect,
                onCheckedChange = onSshAutoReconnectChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.Dns,
                title = "TCP KeepAlive",
                subtitle = stringResource(R.string.ui______________8a0ba94d),
                checked = sshKeepalive,
                onCheckedChange = onSshKeepaliveChange,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.MonitorHeart,
                title = stringResource(R.string.ui______9901e9b6),
                subtitle = SshHeartbeatSeconds.fromSeconds(sshHeartbeat).display,
                onViewClick = onHeartbeatClick,
                showDivider = true
            )
            AppListItem(
                icon = Icons.Rounded.Dns,
                title = stringResource(R.string.ui____9c828e97),
                subtitle = "新增/编辑/删除 SSH 主机和密钥",
                onViewClick = onSshHostsClick,
                showDivider = false
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

// ================================================================
// Stepper Row (internal, reused from TerminalSettingsScreen)
// ================================================================
@Composable
internal fun StepperRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PrimitiveSpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(PrimitiveSpacing.Md))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onIncrease, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ================================================================
// Dialogs (internal, copied from TerminalSettingsScreen)
// ================================================================

@Composable
internal fun ImagePickerDialogInternal(
    profiles: List<ContainerProfile>,
    activeProfileId: String,
    onDismiss: () -> Unit,
    onPick: (ContainerProfile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tc_pick_image_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                profiles.forEach { profile ->
                    val isActive = profile.id == activeProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                            .clickable { onPick(profile) }
                            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (profile.arch) {
                                    ContainerArch.X86_64 -> "x86_64"
                                    ContainerArch.ARM64 -> "aarch64"
                                } + if (profile.mode == ExecutionMode.REMOTE_SSH)
                                    " · " + stringResource(R.string.tc_profile_ssh)
                                else " · " + stringResource(R.string.tc_profile_local),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isActive) {
                            Text(
                                text = stringResource(R.string.tc_image_picker_active_tag),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
internal fun MirrorPickerDialogInternal(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 预设镜像：label 走 strings.xml，url 为固定值。
    val presets = listOf(
        stringResource(R.string.tc_mirror_aliyun) to "https://mirrors.aliyun.com/alpine",
        stringResource(R.string.tc_mirror_tuna) to "https://mirrors.tuna.tsinghua.edu.cn/alpine",
        stringResource(R.string.tc_mirror_ustc) to "https://mirrors.ustc.edu.cn/alpine",
        stringResource(R.string.tc_mirror_official) to "https://dl-cdn.alpinelinux.org/alpine",
    )
    val customLabel = stringResource(R.string.tc_mirror_custom_option)
    // 选中项：要么是某个预设 url，要么是 CUSTOM 标记。
    val customTag = "__custom__"
    val initialSelection = presets.firstOrNull { it.second == current }?.second ?: customTag
    var selected by remember { mutableStateOf(initialSelection) }
    var customUrl by remember { mutableStateOf(current) }
    val customFocusRequester = remember { FocusRequester() }

    // 选中"自定义"时自动聚焦输入框。
    LaunchedEffect(selected) {
        if (selected == customTag) {
            runCatching { customFocusRequester.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui____1d3311a8)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                presets.forEach { (label, url) ->
                    FilterChip(
                        selected = selected == url,
                        onClick = { selected = url },
                        label = {
                            Column {
                                Text(label)
                                Text(
                                    url,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
                // 自定义选项
                FilterChip(
                    selected = selected == customTag,
                    onClick = { selected = customTag },
                    label = { Text(customLabel) }
                )
                // 自定义输入框：仅在选中"自定义"时展示。
                if (selected == customTag) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text(stringResource(R.string.tc_mirror_custom_label)) },
                        placeholder = { Text(stringResource(R.string.tc_mirror_custom_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val value = customUrl.trim()
                                if (value.isNotBlank()) onConfirm(value)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(customFocusRequester)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = if (selected == customTag) customUrl.trim() else selected
                if (value.isNotBlank()) onConfirm(value)
            }) {
                Text(stringResource(R.string.ui____e83a256e))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui____625fb26b_4)) } }
    )
}

@Composable
internal fun ThemePickerDialogInternal(
    current: TerminalTheme,
    onDismiss: () -> Unit,
    onConfirm: (TerminalTheme) -> Unit
) {
    val options = listOf(
        TerminalTheme.FOLLOW_APP to stringResource(R.string.ui______40b081ab_2),
        TerminalTheme.PURE_BLACK to stringResource(R.string.ui______27771d06),
        TerminalTheme.PURE_WHITE to stringResource(R.string.ui______909e0e94)
    )
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui________48cee970_2)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { (v, label) ->
                    FilterChip(
                        selected = selected == v,
                        onClick = { selected = v },
                        label = { Text(label) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.ui____e83a256e_2)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui____625fb26b_5)) } }
    )
}

@Composable
internal fun HeartbeatPickerDialogInternal(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = SshHeartbeatSeconds.entries
    var selected by remember { mutableStateOf(SshHeartbeatSeconds.fromSeconds(current)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_ssh_1fd07b48)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.forEach { o ->
                    FilterChip(
                        selected = selected == o,
                        onClick = { selected = o },
                        label = { Text(o.display) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected.seconds) }) { Text(stringResource(R.string.ui____e83a256e_3)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui____625fb26b_6)) } }
    )
}
