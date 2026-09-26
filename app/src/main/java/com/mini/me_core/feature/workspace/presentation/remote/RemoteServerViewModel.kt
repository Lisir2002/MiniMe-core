package com.mini.me_core.feature.workspace.presentation.remote

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteMount
import com.mini.me_core.feature.workspace.domain.model.RemoteProtocol
import com.mini.me_core.feature.workspace.domain.remote.RemoteAuth
import com.mini.me_core.feature.workspace.domain.repository.RemoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mini.me_core.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.mini.me_core.feature.workspace.domain.model.Workspace
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import com.mini.me_core.feature.settings.data.repository.SyncSettingsRepository
import com.mini.me_core.feature.workspace.domain.remote.ftp.FtpServerManager
import com.mini.me_core.datalayer.store.KVStore

@HiltViewModel
class RemoteServerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: RemoteRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val syncSettingsRepository: SyncSettingsRepository,
    private val kv: KVStore,
    val ftpServerManager: FtpServerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemoteServerUiState())
    val uiState: StateFlow<RemoteServerUiState> = _uiState.asStateFlow()

    val syncIgnoredPatterns = syncSettingsRepository.ignoredPatterns
    val syncUseGitIgnore = syncSettingsRepository.useGitIgnore
    val maxSyncBatchSize = syncSettingsRepository.maxSyncBatchSize
    val conflictStrategy = syncSettingsRepository.conflictStrategy
    val autoSyncEnabled = syncSettingsRepository.autoSyncEnabled
    val autoSyncIntervalMinutes = syncSettingsRepository.autoSyncIntervalMinutes

    /** 每个挂载点的同步方向（bidirectional/upload_only/download_only），按 mountId 持久化在 KVStore。 */
    private val _mountSyncDirections = MutableStateFlow<Map<String, String>>(emptyMap())
    val mountSyncDirections: StateFlow<Map<String, String>> = _mountSyncDirections.asStateFlow()

    /** 正在测试连通性的连接 id 集合（用于卡片 loading 态）。 */
    private val _testingConnectionIds = MutableStateFlow<Set<String>>(emptySet())
    val testingConnectionIds: StateFlow<Set<String>> = _testingConnectionIds.asStateFlow()

    init {
        loadData()
        loadMountSyncDirections()
    }

    private fun loadMountSyncDirections() {
        viewModelScope.launch {
            val entries = kv.getAll(REMOTE_MOUNT_PREFS_NS).associate { it.key to (it.stringVal ?: "bidirectional") }
            _mountSyncDirections.value = entries
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            launch {
                repository.getConnections()
                    .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                    .collect { connections ->
                        _uiState.value = _uiState.value.copy(connections = connections)
                    }
            }
            launch {
                repository.getMounts()
                    .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                    .collect { mounts ->
                        val wasEmpty = _uiState.value.mounts.isEmpty()
                        _uiState.value = _uiState.value.copy(mounts = mounts)
                        
                        // Auto-connect mounts on load
                        if (wasEmpty) {
                            mounts.filter { it.autoConnect && !it.isActive }.forEach {
                                connectMount(it.id)
                            }
                        }
                    }
            }
            launch {
                workspaceRepository.workspaces.collect { workspaces ->
                    _uiState.value = _uiState.value.copy(workspaces = workspaces)
                }
            }
        }
    }

    fun connectMount(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.connectMount(id)
            val updatedFailed = if (result.isFailure) _uiState.value.failedMountIds + id else _uiState.value.failedMountIds - id
            _uiState.value = _uiState.value.copy(isLoading = false, failedMountIds = updatedFailed)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Connection failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun disconnectMount(id: String) {
        viewModelScope.launch {
            repository.disconnectMount(id)
            _uiState.value = _uiState.value.copy(failedMountIds = _uiState.value.failedMountIds - id)
        }
    }

    fun forceUploadMount(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.forceUploadMount(id)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.remote_upload_all_failed, result.exceptionOrNull()?.message))
            } else {
                _uiState.value = _uiState.value.copy(error = context.getString(R.string.remote_upload_all_success)) // 暂时复用 error 展示成功消息，或稍后单独做 toast
            }
        }
    }

    fun forceDownloadMount(id: String) {
        // 先弹出确认弹窗，不直接开始下载
        val mount = _uiState.value.mounts.firstOrNull { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(pendingDownloadMount = mount)
    }

    fun confirmDownloadMount() {
        val mount = _uiState.value.pendingDownloadMount ?: return
        _uiState.value = _uiState.value.copy(pendingDownloadMount = null, isDownloading = true, downloadResult = null)
        viewModelScope.launch {
            val result = repository.forceDownloadMount(mount.id)
            _uiState.value = _uiState.value.copy(isDownloading = false)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    downloadResult = context.getString(R.string.remote_download_all_failed, result.exceptionOrNull()?.message)
                )
            } else {
                _uiState.value = _uiState.value.copy(downloadResult = context.getString(R.string.remote_download_all_success))
            }
        }
    }

    fun dismissDownloadConfirm() {
        _uiState.value = _uiState.value.copy(pendingDownloadMount = null)
    }

    fun dismissDownloadResult() {
        _uiState.value = _uiState.value.copy(downloadResult = null)
    }

    fun deleteConnection(id: String) {
        viewModelScope.launch {
            repository.deleteConnection(id)
        }
    }
    
    fun deleteMount(id: String) {
        viewModelScope.launch {
            repository.deleteMount(id)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun addConnection(
        name: String,
        host: String,
        port: String,
        username: String,
        password: String, 
        protocol: RemoteProtocol
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val conn = RemoteConnection(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = protocol,
                host = host,
                port = p,
                username = username.ifBlank { "local" }
            )
            repository.addConnection(conn, RemoteAuth.Password(password))
        }
    }

    fun updateConnection(
        id: String,
        name: String,
        host: String,
        port: String,
        username: String,
        password: String,
        protocol: RemoteProtocol
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val conn = RemoteConnection(
                id = id,
                name = name,
                protocol = protocol,
                host = host,
                port = p,
                username = username.ifBlank { "local" }
            )
            repository.updateConnection(conn, RemoteAuth.Password(password))
        }
    }

    fun addMount(connectionId: String, remotePath: String, localWorkspacePath: String, autoConnect: Boolean) {
        viewModelScope.launch {
            val mount = RemoteMount(
                id = UUID.randomUUID().toString(),
                connectionId = connectionId,
                remotePath = remotePath,
                localMountPath = localWorkspacePath,
                autoConnect = autoConnect
            )
            repository.addMount(mount)
            if (autoConnect) {
                connectMount(mount.id)
            }
        }
    }

    fun updateMount(id: String, connectionId: String, remotePath: String, localWorkspacePath: String, autoConnect: Boolean) {
        viewModelScope.launch {
            val mount = RemoteMount(
                id = id,
                connectionId = connectionId,
                remotePath = remotePath,
                localMountPath = localWorkspacePath,
                autoConnect = autoConnect
            )
            repository.updateMount(mount)
            if (autoConnect) {
                connectMount(mount.id)
            } else {
                disconnectMount(mount.id)
            }
        }
    }

    fun testConnection(
        host: String,
        port: String,
        username: String,
        password: String,
        protocol: RemoteProtocol,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val p = port.toIntOrNull() ?: defaultPort(protocol)
            val result = repository.testConnection(host, p, username, RemoteAuth.Password(password), protocol)
            if (result.isSuccess) {
                onResult(true, context.getString(R.string.remote_connect_success))
            } else {
                onResult(false, context.getString(R.string.remote_connect_failed, result.exceptionOrNull()?.message))
            }
        }
    }

    fun listRemoteDirectories(
        connectionId: String,
        path: String,
        onResult: (Boolean, List<String>, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.listRemoteDirectories(connectionId, path)
            if (result.isSuccess) {
                onResult(true, result.getOrNull() ?: emptyList(), "")
            } else {
                onResult(false, emptyList(), result.exceptionOrNull()?.message ?: context.getString(R.string.remote_unknown_error))
            }
        }
    }

    /** 测试单个已保存连接的连通性（从卡片触发）。 */
    fun testConnection(id: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _testingConnectionIds.value = _testingConnectionIds.value + id
            val result = repository.testConnectionById(id)
            _testingConnectionIds.value = _testingConnectionIds.value - id
            if (result.isSuccess) {
                onResult(true, context.getString(R.string.remote_connect_success))
            } else {
                onResult(false, context.getString(R.string.remote_connect_failed, result.exceptionOrNull()?.message))
            }
        }
    }

    /** 批量连接所有未连接的挂载点。 */
    fun connectAll() {
        viewModelScope.launch {
            _uiState.value.mounts.filter { !it.isActive }.forEach { connectMount(it.id) }
        }
    }

    /** 批量断开所有已连接的挂载点。 */
    fun disconnectAll() {
        viewModelScope.launch {
            _uiState.value.mounts.filter { it.isActive }.forEach { disconnectMount(it.id) }
        }
    }

    /** 设置单个挂载点的同步方向并持久化。 */
    fun setSyncDirection(mountId: String, direction: String) {
        viewModelScope.launch {
            kv.putString(REMOTE_MOUNT_PREFS_NS, "dir_$mountId", direction)
            _mountSyncDirections.value = _mountSyncDirections.value + (mountId to direction)
            // TODO: 将 direction 传入 SyncEngine，按方向限制 upload/download/watch 行为
        }
    }

    /** 设置全局冲突处理策略并持久化。 */
    fun setConflictStrategy(strategy: String) {
        syncSettingsRepository.setConflictStrategy(strategy)
        // TODO: 将 strategy 传入 SyncEngine，解决冲突时按策略执行
    }

    /** 设置自动同步开关与间隔（分钟，0 表示仅手动）并持久化。 */
    fun setAutoSync(enabled: Boolean, intervalMinutes: Int) {
        syncSettingsRepository.setAutoSync(enabled, intervalMinutes)
        // TODO: 按 intervalMinutes 启动/取消周期同步协程
    }

    fun setSyncIgnoredPatterns(patterns: String) {
        syncSettingsRepository.setIgnoredPatterns(patterns)
    }

    fun setSyncUseGitIgnore(use: Boolean) {
        syncSettingsRepository.setUseGitIgnore(use)
    }

    fun setMaxSyncBatchSize(size: Int) {
        syncSettingsRepository.setMaxSyncBatchSize(size)
    }

    fun toggleFtpServer() {
        viewModelScope.launch {
            ftpServerManager.toggleServer()
        }
    }

    fun saveFtpServerConfig(port: Int, username: String, password: String, isAnonymous: Boolean, autoStart: Boolean) {
        viewModelScope.launch {
            ftpServerManager.saveConfig(port, username, password, isAnonymous, autoStart)
        }
    }

    private fun defaultPort(protocol: RemoteProtocol): Int = when (protocol) {
        RemoteProtocol.SFTP -> 22
        RemoteProtocol.FTP -> 21
        RemoteProtocol.LOCAL -> 0
    }
}

data class RemoteServerUiState(
    val connections: List<RemoteConnection> = emptyList(),
    val mounts: List<RemoteMount> = emptyList(),
    val workspaces: List<Workspace> = emptyList(),
    val failedMountIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** 待确认下载的挂载点（非空时弹出确认弹窗）。 */
    val pendingDownloadMount: RemoteMount? = null,
    /** 是否正在下载远程工作区（显示不确定进度弹窗）。 */
    val isDownloading: Boolean = false,
    /** 下载完成/失败结果消息（非空时由进度弹窗显示）。 */
    val downloadResult: String? = null,
)

/** KVStore namespace：按 mountId 保存每挂载点的同步方向等偏好。 */
private const val REMOTE_MOUNT_PREFS_NS = "remote_mount_prefs"
