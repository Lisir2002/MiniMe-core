package com.mini.me_core.feature.agent.domain.mcp

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.agent.domain.tool.ToolRegistry
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class McpServerStatus(
    val name: String,
    val state: State,
    val toolCount: Int = 0,
    val totalToolCount: Int = 0,
    val error: String? = null
) {
    enum class State { CONNECTING, CONNECTED, FAILED, DISABLED }
}

// reloadMutex 串行化重连，避免设置页连点导致并发注册/反注册竞态。
@Singleton
class McpManager @Inject constructor(
    private val configRepository: McpConfigRepository,
    private val toolRegistry: ToolRegistry,
    private val okHttpClient: OkHttpClient,
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "McpManager"
        const val MAX_RETRY_ATTEMPTS = 3
        val RETRY_DELAYS_MS = listOf(2000L, 4000L, 8000L)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reloadMutex = Mutex()

    private val activeClients = mutableMapOf<String, McpClient>()
    /** serverName -> 该服务器注册的工具名集合，按服务器分组避免同名工具误删。 */
    private val registeredToolsByServer = mutableMapOf<String, MutableSet<String>>()

    private val _statuses = MutableStateFlow<List<McpServerStatus>>(emptyList())
    val statuses: StateFlow<List<McpServerStatus>> = _statuses.asStateFlow()

    fun start() {
        scope.launch { reload() }
    }

    /**
     * 增量重载：只断开被删除/禁用的服务器，只连接新增/启用的服务器，已连接且配置不变的保持连接。
     */
    suspend fun reload() = reloadMutex.withLock {
        val servers = configRepository.getServers()
        FileLogger.i(TAG, "增量加载 MCP 配置，共 ${servers.size} 个 server")

        val newNames = servers.map { it.name }.toSet()
        val newEnabledNames = servers.filter { it.enabled }.map { it.name }.toSet()

        // 1. 断开：已删除或被禁用的服务器
        val toDisconnect = synchronized(activeClients) {
            activeClients.keys.filter { it !in newNames || it !in newEnabledNames }
        }
        toDisconnect.forEach { name ->
            FileLogger.i(TAG, "断开不再需要的 server: $name")
            disconnectServer(name)
        }

        // 2. 待连接：新增或新启用的服务器（已连接的跳过）
        val toConnect = servers.filter { it.enabled && it.name !in activeClients }

        // 3. 更新状态：已连接的保留，禁用的置 DISABLED，待连接的先置 CONNECTING
        val existingStatuses = _statuses.value.associateBy { it.name }
        _statuses.value = servers.map { cfg ->
            if (!cfg.enabled) {
                McpServerStatus(cfg.name, McpServerStatus.State.DISABLED)
            } else if (cfg.name in activeClients) {
                existingStatuses[cfg.name] ?: McpServerStatus(cfg.name, McpServerStatus.State.CONNECTED)
            } else {
                McpServerStatus(cfg.name, McpServerStatus.State.CONNECTING)
            }
        }

        if (toConnect.isEmpty()) {
            FileLogger.i(TAG, "无需新连接，增量重载完成")
            return@withLock
        }

        // 4. 并行连接新增/启用的 server；各自独立失败。
        FileLogger.i(TAG, "需要连接 ${toConnect.size} 个新 server")
        val results = withContext(Dispatchers.IO) {
            toConnect.map { cfg ->
                async { connectOneWithRetry(cfg) }
            }.awaitAll()
        }

        // 5. 合并结果：保持原有顺序，已连接的不变。
        val resultByMap = results.associateBy { it.name }
        _statuses.value = servers.map { cfg ->
            if (!cfg.enabled) {
                McpServerStatus(cfg.name, McpServerStatus.State.DISABLED)
            } else if (cfg.name in activeClients && cfg.name !in resultByMap) {
                existingStatuses[cfg.name] ?: McpServerStatus(cfg.name, McpServerStatus.State.CONNECTED)
            } else {
                resultByMap[cfg.name] ?: McpServerStatus(cfg.name, McpServerStatus.State.DISABLED)
            }
        }
    }

    /** 连接一个 server，失败后指数退避重试最多 [MAX_RETRY_ATTEMPTS] 次。 */
    private suspend fun connectOneWithRetry(cfg: McpServerConfig): McpServerStatus {
        var lastError: String? = null
        for (attempt in 0..MAX_RETRY_ATTEMPTS) {
            val result = connectOne(cfg)
            if (result.state == McpServerStatus.State.CONNECTED) {
                return result
            }
            lastError = result.error
            if (attempt < MAX_RETRY_ATTEMPTS) {
                val delayMs = RETRY_DELAYS_MS[attempt]
                FileLogger.i(TAG, "[${cfg.name}] 第 ${attempt + 1} 次连接失败，${delayMs}ms 后重试")
                delay(delayMs)
            }
        }
        FileLogger.e(TAG, "[${cfg.name}] 重试 $MAX_RETRY_ATTEMPTS 次后仍失败: $lastError")
        return McpServerStatus(cfg.name, McpServerStatus.State.FAILED, error = lastError)
    }

    private suspend fun connectOne(cfg: McpServerConfig): McpServerStatus {
        return try {
            val transport = createTransport(cfg)
            val client = McpClient(serverName = cfg.name, transport = transport)
            client.connect()

            val tools = client.tools.map { McpTool(client, it) }
            val enabledTools = tools.filter { it.remoteName !in cfg.disabledTools }
            synchronized(activeClients) {
                activeClients[cfg.name] = client
                val registered = mutableSetOf<String>()
                enabledTools.forEach { tool ->
                    toolRegistry.register(tool.name, tool)
                    registered.add(tool.name)
                }
                registeredToolsByServer[cfg.name] = registered
            }
            FileLogger.i(TAG, "[${cfg.name}] 连接成功，注册 ${enabledTools.size}/${tools.size} 个工具")
            McpServerStatus(cfg.name, McpServerStatus.State.CONNECTED, toolCount = enabledTools.size, totalToolCount = tools.size)
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${cfg.name}] 连接失败", e)
            McpServerStatus(cfg.name, McpServerStatus.State.FAILED, error = e.message)
        }
    }

    /**
     * 创建传输层：stdio 走容器，HTTP 走 OkHttp。
     * 从 connectOne() 和 testConnection() 中提取，消除重复逻辑。
     */
    private suspend fun createTransport(cfg: McpServerConfig): McpTransport {
        return if (cfg.isStdio) {
            containerEngine.notReadyHint()?.let {
                throw IllegalStateException(it)
            }
            StdioTransport(
                serverName = cfg.name,
                engine = containerEngine,
                program = cfg.command!!,
                programArgs = cfg.args,
                projectPath = workspaceRepository.currentPath(),
                extraEnv = cfg.env
            )
        } else {
            StreamableHttpTransport(
                endpoint = cfg.url.orEmpty(),
                client = okHttpClient,
                extraHeaders = cfg.headers
            )
        }
    }

    fun getServerTools(serverName: String): List<McpToolDescriptor> {
        return synchronized(activeClients) {
            activeClients[serverName]?.tools ?: emptyList()
        }
    }

    /** 断开单个服务器：关闭连接并只移除该服务器注册的工具。 */
    private fun disconnectServer(name: String) {
        synchronized(activeClients) {
            registeredToolsByServer.remove(name)?.forEach { toolRegistry.unregister(it) }
            activeClients.remove(name)?.let { runCatching { it.close() } }
        }
    }

    /**
     * S-2：测试 MCP server 连通性。
     *
     * 用传入的配置创建一个临时 client，完成 initialize → tools/list 握手后立即关闭，不注册工具。
     * 用于 [ManageMcpTool] 在 add 后立即验证配置正确性，避免等到下次会话才暴露连接错误。
     *
     * @return 成功返回空字符串；失败返回错误消息。
     */
    suspend fun testConnection(cfg: McpServerConfig): String = withContext(Dispatchers.IO) {
        try {
            val transport = createTransport(cfg)
            val client = McpClient(serverName = cfg.name, transport = transport)
            val toolCount = client.connect()
            client.close()
            FileLogger.i(TAG, "[${cfg.name}] 连接测试成功，发现 $toolCount 个工具")
            ""
        } catch (e: Exception) {
            FileLogger.w(TAG, "[${cfg.name}] 连接测试失败: ${e.message}")
            e.message ?: "未知错误"
        }
    }
}
