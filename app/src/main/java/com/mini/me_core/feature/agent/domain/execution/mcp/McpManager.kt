package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    @Named("direct") private val okHttpClient: OkHttpClient,
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository,
    /** F1：内置 server 工厂（三台进程内 MCP server）。 */
    private val builtinMcpServerFactory: BuiltinMcpServerFactory,
    /** F1：灰度开关（`builtin_as_mcp_enabled`，默认 false → 与存量行为完全一致）。 */
    private val normFlowSettingsRepository: NormFlowSettingsRepository
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
     *
     * F1：server 列表 = `mcp.json` 里的远端 server **+** 三台内置 server（代码内置、不落盘）。
     * 二者在后续步骤走**同一条装配路径**，仅 [createTransport] 按形态分叉。
     * 灰度开关 `builtin_as_mcp_enabled` 关闭时只装载远端 server，行为与存量完全一致。
     */
    suspend fun reload() = reloadMutex.withLock {
        val servers = configRepository.getServers() + builtinServers()
        FileLogger.i(
            TAG,
            "增量加载 MCP 配置，共 ${servers.size} 个 server（其中内置 ${servers.count { it.isBuiltin }}）"
        )

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

    /**
     * F1：内置 server 列表（灰度开关关闭时返回空列表）。
     *
     * 开关读取失败时**按关闭处理**（返回空列表），保证配置层异常不会把内置能力挂进模型路径。
     */
    private suspend fun builtinServers(): List<McpServerConfig> {
        if (!isBuiltinAsMcpActive()) return emptyList()
        return runCatching { builtinMcpServerFactory.builtinServers() }
            .getOrElse { e ->
                FileLogger.w(TAG, "装配内置 MCP server 失败，本轮跳过内置分组: ${e.message}")
                emptyList()
            }
    }

    /** F1：灰度开关读取；异常按关闭处理并记日志（取消异常原样重抛）。 */
    private suspend fun isBuiltinAsMcpActive(): Boolean = try {
        normFlowSettingsRepository.isBuiltinAsMcpActive()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        @Suppress("SwallowedException")
        FileLogger.w(TAG, "读取 builtin_as_mcp_enabled 失败，按关闭处理: ${e.message}")
        false
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

            // F3：策略在此处定一次，判定逻辑全在 ServerPolicy —— 注册过滤、审批标记、
            // UI 徽章读同一套规则，不可能各自判出不同结果。
            val policy = ServerPolicy.from(cfg)
            val tools = client.tools.map { McpTool(client, it, policy) }
            val enabledTools = tools.filter { policy.isToolEnabled(it.remoteName) }
            val registered = synchronized(activeClients) {
                activeClients[cfg.name] = client
                val names = mutableSetOf<String>()
                enabledTools.forEach { tool ->
                    // F1：内置 server **只枚举不注册**。
                    // 若把 43 个内置工具经 McpTool 包装后注册进 ToolRegistry，模型每轮会额外看到
                    // 43 个 `mcp__minime_core__*` 命名空间工具——既稀释注意力，又与批次 B 的
                    // CANONICAL_CORE_ORDER 冲突，且 flattenContent 会压平结构化结果。
                    // 内置能力的执行面仍是 ToolRegistry 直连（规划 §1），此处只保留 client
                    // 以支撑 tools/list 枚举与状态展示。
                    if (cfg.isBuiltin) return@forEach
                    toolRegistry.register(tool.name, tool)
                    names.add(tool.name)
                }
                registeredToolsByServer[cfg.name] = names
                names
            }

            FileLogger.i(
                TAG,
                if (cfg.isBuiltin) {
                    "[${cfg.name}] 连接成功，内置分组枚举 ${enabledTools.size}/${tools.size} 个工具（不进模型可见面）"
                } else {
                    "[${cfg.name}] 连接成功，注册 ${registered.size}/${tools.size} 个工具"
                }
            )
            McpServerStatus(
                cfg.name,
                McpServerStatus.State.CONNECTED,
                toolCount = enabledTools.size,
                totalToolCount = tools.size
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${cfg.name}] 连接失败", e)
            McpServerStatus(cfg.name, McpServerStatus.State.FAILED, error = e.message)
        }
    }

    /**
     * 创建传输层，按三种形态分叉（其余逻辑三者完全共用）：
     * - 内置 server（F1）：进程内 [BuiltinTransport] → [com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerSession]，
     *   无端口、无子进程、无自举时序；
     * - stdio：容器内子进程；
     * - 其余：Streamable HTTP。
     *
     * 从 connectOne() 和 testConnection() 中提取，消除重复逻辑。
     */
    private suspend fun createTransport(cfg: McpServerConfig): McpTransport {
        val builtinGroup = cfg.builtinGroup
        if (builtinGroup != null) {
            return builtinMcpServerFactory.createTransport(builtinGroup)
        }
        return if (cfg.isStdio) {
            containerEngine.notReadyHint()?.let {
                throw IllegalStateException(it)
            }
            StdioTransport(
                serverName = cfg.name,
                engine = containerEngine,
                program = cfg.command ?: error("stdio MCP server requires command: ${cfg.name}"),
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

    /** 某 server 经协议枚举到的工具描述（内置分组经 `tools/list` 取得，远端同理）。 */
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FileLogger.w(TAG, "[${cfg.name}] 连接测试失败: ${e.message}")
            e.message ?: "未知错误"
        }
    }
}
