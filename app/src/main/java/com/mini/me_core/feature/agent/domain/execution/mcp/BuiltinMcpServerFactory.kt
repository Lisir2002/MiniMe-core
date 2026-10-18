package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.feature.agent.domain.execution.mcp.server.AgentToolMcpAdapter
import com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerSession
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F1 内置 MCP server 工厂：把 [BuiltinGroup] 三台内置 server 装配成**进程内 MCP server**。
 *
 * 装配链路与远端完全同构：
 * ```
 * McpClient ──McpTransport──▶ BuiltinTransport ──▶ McpServerSession ──▶ AgentToolMcpAdapter ──▶ ToolRegistry
 *   （同一）        （进程内）        （同一）        （同一，加分组过滤）      （同一）
 * ```
 * 因此 `McpClient` / `McpTool` / `McpManager` 无需为本地能力写任何分支之外的特例。
 *
 * 与远端的两处**有意**差异（均为设计决策，非妥协）：
 * 1. `respectRemoteBlacklist = false`：逐工具黑名单约束的是「远程客户端不得触达」，
 * 而内置 `tools/list` 服务于 App 内分组展示，不能让 `terminal` / `browser` 凭空消失。
 * 外部可达性改由 [BuiltinGroup.remotelyExposedByDefault] 在 HostServer 侧裁决。
 * 2. `requireApproval = false`：内置 server 不新增审批弹窗。审批仍由本地 Agent 流程的
 * [ToolPermissionPolicy] 与（批次 F3 接通的）`requireApprovalTools` 负责，避免同一动作弹两次。
 */
@Singleton
class BuiltinMcpServerFactory @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val permissionManager: ToolPermissionManager
) {
    /**
     * 三台内置 server 的配置（代码内置，**不落盘**）。
     *
     * 由 [McpManager] 与 `mcp.json` 里的远端 server 并列装载；因 [McpServerConfig.builtinGroup]
     * 非空，二者在 `McpManager` 内的走同一条装配路径，仅传输层不同。
     */
    fun builtinServers(): List<McpServerConfig> = BuiltinGroup.entries.map { group ->
        McpServerConfig(
            name = group.serverName,
            builtinGroup = group,
            remotelyExposed = group.remotelyExposedByDefault
        )
    }

    /** 该分组下经远程黑名单裁决后实际对外可见的工具名（HostServer 分组级收敛用）。 */
    fun externallyExposedGroups(): Set<BuiltinGroup> =
        BuiltinGroup.entries.filter { it.remotelyExposedByDefault }.toSet()

    /** 构造某分组的进程内传输层（含服务端会话 → 适配器 → ToolRegistry 全链）。 */
    fun createTransport(group: BuiltinGroup): BuiltinTransport {
        val adapter = AgentToolMcpAdapter(
            toolRegistry = toolRegistry,
            permissionManager = permissionManager,
            requireApproval = false,
            builtinGroup = group,
            respectRemoteBlacklist = false
        )
        val session = McpServerSession(
            adapter = adapter,
            serverName = group.serverName,
            builtinGroup = group
        )
        return BuiltinTransport(
            serverName = group.serverName,
            session = BuiltinMcpSession { text -> session.handle(text)?.toString() }
        )
    }
}
