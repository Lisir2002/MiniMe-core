package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.core.container.api.ConnectionState
import com.mini.me_core.core.container.api.ContainerArch
import com.mini.me_core.feature.agent.domain.container.ContainerInstaller
import com.mini.me_core.core.container.api.ContainerProfile
import com.mini.me_core.feature.agent.domain.container.RemoteSshConnection
import com.mini.me_core.core.container.api.RootfsSource
import com.mini.me_core.feature.settings.data.repository.ContainerSettingsRepository
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder
import com.mini.me_core.feature.settings.data.repository.ExecutionModeRepository
import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.repository.RemoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：容器域（内置/自定义 profile、共享设备存储、本地 PRoot ↔ 远程 SSH 执行模式切换）。
 * 逻辑原属 SettingsViewModel，逐字搬迁；协程仍由同一 viewModelScope 驱动。
 */
internal class ContainerSection(
    private val scope: CoroutineScope,
    private val containerSettingsRepository: ContainerSettingsRepository,
    private val containerInstaller: ContainerInstaller,
    private val executionModeRepository: ExecutionModeRepository,
    private val executionModeHolder: ExecutionModeHolder,
    private val remoteSshConnection: RemoteSshConnection,
    private val remoteRepository: RemoteRepository,
) {
    private val _activeProfileId = MutableStateFlow(ContainerProfile.BUILTIN_ID)
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    /** 「共享设备存储」：把设备外存绑定进容器 /root/storage/shared（默认关）。 */
    private val _storageShareEnabled = MutableStateFlow(false)
    val storageShareEnabled: StateFlow<Boolean> = _storageShareEnabled.asStateFlow()

    private val _customProfiles = MutableStateFlow<List<ContainerProfile>>(emptyList())
    val customProfiles: StateFlow<List<ContainerProfile>> = _customProfiles.asStateFlow()

    /** 全部 profile（内置 arm64 + 内置 x86_64 + 自定义），供 UI 列出。 */
    val profiles: StateFlow<List<ContainerProfile>> = customProfiles
        .map { listOf(ContainerProfile.BUILTIN_ALPINE, ContainerProfile.BUILTIN_ALPINE_X86) + it }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            listOf(ContainerProfile.BUILTIN_ALPINE, ContainerProfile.BUILTIN_ALPINE_X86)
        )

    /** 当前执行模式（本地 PRoot / 远程 SSH），供 UI 判断是否显示远程连接指示器。 */
    val executionMode: StateFlow<ExecutionMode> = executionModeHolder.mode

    /** 远程 SSH 连接状态，供 UI 显示指示器。 */
    val connectionState: StateFlow<ConnectionState> = remoteSshConnection.connectionState

    /** 工作区已配置的远程连接通道，供容器镜像 SSH 模式下拉复用。 */
    val remoteConnections: StateFlow<List<RemoteConnection>> = remoteRepository.getConnections()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            containerSettingsRepository.activeProfileIdFlow.collectLatest {
                _activeProfileId.value = it
            }
        }

        scope.launch {
            containerSettingsRepository.customProfilesFlow.collectLatest {
                _customProfiles.value = it
            }
        }

        scope.launch {
            containerSettingsRepository.storageShareEnabledFlow.collectLatest {
                _storageShareEnabled.value = it
            }
        }
    }

    /**
     * 切换当前选中的容器 profile，并按其 [ContainerProfile.mode] 同步切全局执行模式。
     *
     * 本地镜像 → [ExecutionMode.LOCAL_PROOT]；远程 SSH 镜像 → [ExecutionMode.REMOTE_SSH]，
     * 并据其 [RootfsSource.RemoteSsh] 绑定的工作区通道构造 [RemoteConnectionSettings] 持久化 + 触发 SSH 连接。
     * 委托层每次调用读 holder，切换即时生效，无需重启。
     */
    fun setActiveContainerProfile(id: String) {
        scope.launch {
            val profile = _customProfiles.value.firstOrNull { it.id == id }
                ?: when (id) {
                    ContainerProfile.BUILTIN_ID -> ContainerProfile.BUILTIN_ALPINE
                    ContainerProfile.BUILTIN_X86_ID -> ContainerProfile.BUILTIN_ALPINE_X86
                    else -> null
                } ?: return@launch
            containerSettingsRepository.setActiveProfile(id)
            when (profile.mode) {
                ExecutionMode.LOCAL_PROOT -> {
                    executionModeRepository.setExecutionMode(ExecutionMode.LOCAL_PROOT)
                    executionModeHolder.setMode(ExecutionMode.LOCAL_PROOT)
                }

                ExecutionMode.REMOTE_SSH -> {
                    val ssh = profile.rootfsSource as? RootfsSource.RemoteSsh ?: return@launch
                    val conn = remoteConnections.value.firstOrNull { it.id == ssh.connectionId }
                        ?: return@launch
                    val auth = runCatching { remoteRepository.getAuthById(ssh.connectionId) }.getOrNull()
                        ?: com.mini.me_core.feature.workspace.domain.remote.RemoteAuth.Password(conn.password)
                    val workspacePath = ssh.remoteWorkspacePath.ifBlank { "/home/${conn.username}/workspace" }
                    // v2 统一数据源：只存 activeConnectionId + workspacePath + profileId，
                    // 不再重复 host/port/username/password 到 DataStore，避免与 Room 漂移。
                    val settings = com.mini.me_core.feature.settings.data.repository.RemoteConnectionSettings(
                        host = "",
                        port = 22,
                        username = "",
                        password = "",
                        remoteWorkspacePath = workspacePath,
                        activeConnectionId = ssh.connectionId,
                    )
                    executionModeRepository.setRemoteConnection(settings, activeProfileId = id)
                    executionModeRepository.setExecutionMode(ExecutionMode.REMOTE_SSH)
                    executionModeHolder.setMode(ExecutionMode.REMOTE_SSH)
                    // 运行时切换需主动连接（启动时由 MiniMeCore 连）；复用 RemoteSshConnection.connect
                    runCatching {
                        remoteSshConnection.connect(
                            com.mini.me_core.core.container.api.RemoteConnectionConfig(
                                host = conn.host,
                                port = conn.port,
                                username = conn.username,
                                auth = auth,
                                remoteWorkspacePath = workspacePath
                            )
                        )
                    }.onFailure { FileLogger.w("SettingsViewModel", "切换到远程镜像时 SSH 连接失败", it) }
                }
            }
        }
    }

    /** 切换「共享设备存储」开关。生效对象为后续启动的容器进程，已运行的 shell 需重开。 */
    fun setStorageShareEnabled(enabled: Boolean) {
        scope.launch { containerSettingsRepository.setStorageShareEnabled(enabled) }
    }

    /** 重置内置容器（arm64 或 x86_64）：删除对应架构 rootfs，下次初始化重新解压 + provision。 */
    fun resetBuiltinContainer(profile: ContainerProfile) {
        scope.launch {
            if (profile.arch == ContainerArch.X86_64) {
                containerInstaller.resetBuiltinX86Rootfs()
            } else {
                containerInstaller.resetBuiltinRootfs()
            }
        }
    }

    /** 保存（新增/覆盖）自定义容器 profile。 */
    fun saveCustomContainerProfile(profile: ContainerProfile) {
        scope.launch {
            containerSettingsRepository.upsertCustomProfile(profile)
        }
    }

    /** 编辑自定义 profile：覆盖配置；若镜像来源变了则删旧 rootfs 触发重新解压。 */
    fun editCustomContainerProfile(profile: ContainerProfile) {
        scope.launch {
            val old = _customProfiles.value.firstOrNull { it.id == profile.id }
            val oldUri = (old?.rootfsSource as? RootfsSource.LocalFile)?.uri
            val newUri = (profile.rootfsSource as? RootfsSource.LocalFile)?.uri
            if (old != null && oldUri != newUri) {
                containerInstaller.deleteCustomRootfs(profile)
            }
            containerSettingsRepository.upsertCustomProfile(profile)
        }
    }

    /** 删除自定义 profile，连带清理其 rootfs 目录。 */
    fun deleteCustomContainerProfile(profile: ContainerProfile) {
        scope.launch {
            containerSettingsRepository.deleteCustomProfile(profile.id)
            containerInstaller.deleteCustomRootfs(profile)
            if (_activeProfileId.value == profile.id) {
                containerSettingsRepository.setActiveProfile(ContainerProfile.BUILTIN_ID)
                // 删的是当前激活的远程镜像：回退到内置本地镜像，同步切回本地模式
                if (profile.mode == ExecutionMode.REMOTE_SSH) {
                    executionModeRepository.setExecutionMode(ExecutionMode.LOCAL_PROOT)
                    executionModeHolder.setMode(ExecutionMode.LOCAL_PROOT)
                }
            }
        }
    }
}
