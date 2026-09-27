package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.agent.domain.container.ContainerInitState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 容器协调器：管理容器初始化进度和当前工作区。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - 容器初始化进度状态
 * - 当前工作区路径管理
 *
 * 注意：会话列表（sessions/sessionsWithCount）依赖 v2Agent，
 * 仍由 AIAgentViewModel 管理，本类只负责容器和工作区。
 */
@Singleton
class ContainerCoordinator @Inject constructor(
    private val containerEngine: LinuxContainerEngine
) {
    /** 容器初始化实时进度（解压/部署/装包）。 */
    val containerInit: StateFlow<ContainerInitState> = containerEngine.initProgress

    private val _currentWorkspace = MutableStateFlow<String>("")
    val currentWorkspace: StateFlow<String> = _currentWorkspace.asStateFlow()

    /**
     * 设置当前工作区路径。
     * 空路径或相同路径忽略。
     */
    fun setWorkspace(path: String) {
        if (path.isBlank() || _currentWorkspace.value == path) return
        _currentWorkspace.value = path
    }

    /**
     * 获取当前工作区路径。
     */
    fun getWorkspace(): String = _currentWorkspace.value

    /**
     * 检查容器是否已就绪。
     */
    fun isContainerReady(): Boolean = containerEngine.isProvisioned()
}
