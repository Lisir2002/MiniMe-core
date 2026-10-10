package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.agent.domain.execution.mcp.McpToolDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：MCP 服务器域（外部 MCP server 列表管理与重载 / 内置 MCP server 开关）。
 * 逻辑原属 SettingsViewModel，逐字搬迁；协程仍由同一 viewModelScope 驱动。
 */
internal class McpSection(
    private val scope: CoroutineScope,
    private val mcpConfigRepository: McpConfigRepository,
    private val mcpManager: McpManager,
    private val mcpServerManager: McpServerManager,
) {
    private val _mcpServers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val mcpServers: StateFlow<List<McpServerConfig>> = _mcpServers.asStateFlow()

    val mcpStatuses: StateFlow<List<McpServerStatus>> = mcpManager.statuses

    private val _mcpReloading = MutableStateFlow(false)
    val mcpReloading: StateFlow<Boolean> = _mcpReloading.asStateFlow()

    // ── 内置 MCP 服务器（把自己开放出去） ──
    val mcpServerIsRunning: StateFlow<Boolean> = mcpServerManager.isRunning
    val mcpServerPort: StateFlow<Int> = mcpServerManager.port
    val mcpServerToken: StateFlow<String> = mcpServerManager.token
    val mcpServerRequireApproval: StateFlow<Boolean> = mcpServerManager.requireApproval
    val mcpServerAutoStart: StateFlow<Boolean> = mcpServerManager.autoStart
    val mcpServerUrl: StateFlow<String> = mcpServerManager.serverUrl
    val mcpServerError: StateFlow<String?> = mcpServerManager.errorMessage

    init {
        scope.launch {
            mcpConfigRepository.serversFlow.collectLatest {
                _mcpServers.value = it
            }
        }
    }

    fun toggleMcpServer() = scope.launch { mcpServerManager.toggleServer() }

    fun saveMcpServerConfig(port: Int, requireApproval: Boolean, autoStart: Boolean) =
        scope.launch { mcpServerManager.saveConfig(port, requireApproval, autoStart) }

    fun regenerateMcpServerToken() = scope.launch { mcpServerManager.regenerateToken() }

    fun upsertMcpServer(originalName: String?, config: McpServerConfig) {
        scope.launch {
            val ordered = LinkedHashMap<String, McpServerConfig>()
            _mcpServers.value.forEach { ordered[it.name] = it }
            if (originalName != null && originalName != config.name) {
                ordered.remove(originalName)
            }
            ordered[config.name] = config
            persistMcpServers(ordered.values.toList())
        }
    }

    fun deleteMcpServer(name: String) {
        scope.launch {
            persistMcpServers(_mcpServers.value.filterNot { it.name == name })
        }
    }

    fun setMcpServerEnabled(name: String, enabled: Boolean) {
        scope.launch {
            persistMcpServers(_mcpServers.value.map { if (it.name == name) it.copy(enabled = enabled) else it })
        }
    }

    fun reloadMcp() {
        scope.launch {
            _mcpReloading.value = true
            try {
                mcpManager.reload()
            } finally {
                _mcpReloading.value = false
            }
        }
    }

    fun getMcpServerTools(serverName: String?): List<McpToolDescriptor> {
        if (serverName.isNullOrBlank()) return emptyList()
        return mcpManager.getServerTools(serverName)
    }

    private suspend fun persistMcpServers(servers: List<McpServerConfig>) {
        mcpConfigRepository.setServers(servers)
        _mcpReloading.value = true
        try {
            mcpManager.reload()
        } finally {
            _mcpReloading.value = false
        }
    }
}
