package com.mini.me_core.feature.workspace.presentation.remote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteMount
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.ui.rememberPersistentLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerScreen(
    viewModel: RemoteServerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var showAddMountDialog by remember { mutableStateOf(false) }
    var connectionToEdit by remember { mutableStateOf<RemoteConnection?>(null) }
    var mountToEdit by remember { mutableStateOf<RemoteMount?>(null) }

    val syncIgnoredPatterns by viewModel.syncIgnoredPatterns.collectAsStateWithLifecycle()
    val syncUseGitIgnore by viewModel.syncUseGitIgnore.collectAsStateWithLifecycle()
    val maxSyncBatchSize by viewModel.maxSyncBatchSize.collectAsStateWithLifecycle()
    val conflictStrategy by viewModel.conflictStrategy.collectAsStateWithLifecycle()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsStateWithLifecycle()
    val autoSyncIntervalMinutes by viewModel.autoSyncIntervalMinutes.collectAsStateWithLifecycle()
    val testingConnectionIds by viewModel.testingConnectionIds.collectAsStateWithLifecycle()
    val mountSyncDirections by viewModel.mountSyncDirections.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppTopAppBar(
                    title = stringResource(R.string.remote_workspace_title),
                    onNavigateBack = onNavigateBack,
                    navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                    navigationContentDescription = stringResource(R.string.common_back),
                )
                AppSegmentedControl(
                    tabs = listOf(
                        stringResource(R.string.remote_tab_connections),
                        stringResource(R.string.common_workspace),
                        stringResource(R.string.remote_tab_ftp),
                        stringResource(R.string.remote_tab_sync)
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(onClick = {
                    if (selectedTab == 0) {
                        connectionToEdit = null
                        showAddConnectionDialog = true
                    } else {
                        mountToEdit = null
                        showAddMountDialog = true
                    }
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.common_add))
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (selectedTab == 0) {
                if (uiState.connections.isEmpty()) {
                    com.mini.me_core.core.theme.components.AppEmptyState(
                        title = stringResource(R.string.remote_empty_connections_title),
                        subtitle = stringResource(R.string.remote_empty_connections_subtitle),
                        icon = androidx.compose.material.icons.Icons.Rounded.Dns,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val connectionsListState = rememberPersistentLazyListState("settings_remote_connections")
                    LazyColumn(
                        state = connectionsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.connections) { conn ->
                            RemoteConnectionCard(
                                conn = conn,
                                testing = conn.id in testingConnectionIds,
                                onTest = { c ->
                                    viewModel.testConnection(c.id) { success, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                onEdit = {
                                    connectionToEdit = it
                                    showAddConnectionDialog = true
                                },
                                onDelete = { viewModel.deleteConnection(it.id) }
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                if (uiState.mounts.isEmpty()) {
                    com.mini.me_core.core.theme.components.AppEmptyState(
                        title = stringResource(R.string.remote_empty_mounts_title),
                        subtitle = stringResource(R.string.remote_empty_mounts_subtitle),
                        icon = androidx.compose.material.icons.Icons.Rounded.Storage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val mountsListState = rememberPersistentLazyListState("settings_remote_mounts")
                    LazyColumn(
                        state = mountsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.mounts) { mount ->
                            RemoteMountCard(
                                mount = mount,
                                isFailed = mount.id in uiState.failedMountIds,
                                direction = mountSyncDirections[mount.id] ?: mount.syncDirection,
                                onDirectionChange = { viewModel.setSyncDirection(mount.id, it) },
                                onEdit = {
                                    mountToEdit = it
                                    showAddMountDialog = true
                                },
                                onDelete = { viewModel.deleteMount(it.id) },
                                onUpload = { viewModel.forceUploadMount(it.id) },
                                onDownload = { viewModel.forceDownloadMount(it.id) },
                                onConnect = { viewModel.connectMount(it.id) },
                                onDisconnect = { viewModel.disconnectMount(it.id) }
                            )
                        }
                    }
                }
            }

            if (selectedTab == 2) {
                WiFiFtpServerSection(viewModel)
            } else if (selectedTab == 3) {
                SyncSettingsSection(
                    ignoredPatterns = syncIgnoredPatterns,
                    useGitIgnore = syncUseGitIgnore,
                    maxSyncBatchSize = maxSyncBatchSize,
                    conflictStrategy = conflictStrategy,
                    autoSyncEnabled = autoSyncEnabled,
                    autoSyncIntervalMinutes = autoSyncIntervalMinutes,
                    onPatternsChange = { viewModel.setSyncIgnoredPatterns(it) },
                    onUseGitIgnoreChange = { viewModel.setSyncUseGitIgnore(it) },
                    onMaxSyncBatchSizeChange = { viewModel.setMaxSyncBatchSize(it) },
                    onDirectionChange = { direction ->
                        uiState.mounts.forEach { viewModel.setSyncDirection(it.id, direction) }
                    },
                    onConflictStrategyChange = { viewModel.setConflictStrategy(it) },
                    onAutoSyncChange = { enabled, interval -> viewModel.setAutoSync(enabled, interval) }
                )
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Lg),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    if (showAddConnectionDialog) {
        AddRemoteConnectionDialog(
            initialConnection = connectionToEdit,
            onDismiss = { showAddConnectionDialog = false },
            onAdd = { name, host, port, username, password, protocol ->
                val editing = connectionToEdit
                if (editing != null) {
                    viewModel.updateConnection(editing.id, name, host, port, username, password, protocol)
                } else {
                    viewModel.addConnection(name, host, port, username, password, protocol)
                }
                showAddConnectionDialog = false
            },
            onTestConnection = { host, port, username, password, protocol, onResult ->
                viewModel.testConnection(host, port, username, password, protocol, onResult)
            }
        )
    }

    if (showAddMountDialog) {
        if (uiState.connections.isEmpty()) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                onDismissRequest = { showAddMountDialog = false },
                title = { Text(stringResource(R.string.remote_hint_title)) },
                text = { Text(stringResource(R.string.remote_add_channel_first)) },
                confirmButton = {
                    TextButton(onClick = { showAddMountDialog = false; selectedTab = 0 }) {
                        Text(stringResource(R.string.remote_go_add))
                    }
                }
            )
        } else {
            AddRemoteMountDialog(
                initialMount = mountToEdit,
                connections = uiState.connections,
                workspaces = uiState.workspaces,
                onDismiss = { showAddMountDialog = false },
                onAdd = { connectionId, remotePath, localWorkspacePath, autoConnect ->
                    val editing = mountToEdit
                    if (editing != null) {
                        viewModel.updateMount(editing.id, connectionId, remotePath, localWorkspacePath, autoConnect)
                    } else {
                        viewModel.addMount(connectionId, remotePath, localWorkspacePath, autoConnect)
                    }
                    showAddMountDialog = false
                },
                onListDirectories = { connectionId, path, onResult ->
                    viewModel.listRemoteDirectories(connectionId, path, onResult)
                }
            )
        }
    }

    // ── 通用下载确认弹窗（远程工作区全量拉取） ──
    uiState.pendingDownloadMount?.let { mount ->
        com.mini.me_core.core.download.DownloadConfirmDialog(
            fileName = mount.remotePath.substringAfterLast('/').ifEmpty { mount.remotePath },
            fileSizeText = null,
            sourceUrl = "remote:${mount.remotePath}",
            onConfirm = { viewModel.confirmDownloadMount() },
            onDismiss = { viewModel.dismissDownloadConfirm() },
        )
    }

    // ── 通用下载进度弹窗（不确定进度，远程同步无逐文件回调） ──
    if (uiState.isDownloading) {
        com.mini.me_core.core.download.DownloadProgressDialog(
            task = com.mini.me_core.core.download.DownloadTask(
                id = "remote-sync",
                title = uiState.pendingDownloadMount?.remotePath?.substringAfterLast('/') ?: "Remote Sync",
                url = "",
                totalBytes = -1,
                status = com.mini.me_core.core.download.DownloadStatus.DOWNLOADING,
            ),
            onCancel = { viewModel.dismissDownloadResult() },
            onDismiss = { viewModel.dismissDownloadResult() },
        )
    }

    // ── 下载完成/失败结果 ──
    uiState.downloadResult?.let { result ->
        com.mini.me_core.core.download.DownloadProgressDialog(
            task = com.mini.me_core.core.download.DownloadTask(
                id = "remote-sync",
                title = "Remote Sync",
                url = "",
                status = if (result.contains("成功"))
                    com.mini.me_core.core.download.DownloadStatus.COMPLETED
                else
                    com.mini.me_core.core.download.DownloadStatus.FAILED,
                errorMessage = result,
            ),
            onCancel = {},
            onDismiss = { viewModel.dismissDownloadResult() },
        )
    }

}
