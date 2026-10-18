package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * F1 内置能力分组（D1：静态声明于 `AgentModule.provideToolRegistry` 的注册处）。
 *
 * 分组是**用户心智**语义（用户找工具时先想「我要操作文件还是操作任务」），
 * 与 [ToolCapability] 的**安全**语义（能不能改网络）正交，两套轴不混用。
 *
 * 每组对应一台内置 MCP server（见 `BuiltinMcpServerFactory`），本地能力在协议层
 * 由此成为真正的 MCP server，与远端 server 同款治理、同样可被外部客户端发现。
 */
@Serializable
enum class BuiltinGroup(
    /** 内置 MCP server 名（协议层对外标识）。 */
    val serverName: String,
    /** UI 展示名（工具分组标题下的能力描述）。 */
    val displayName: String,
    /**
     * 默认是否允许对外暴露。
     *
     * CORE 组只含工作区/执行类工具，默认允许；CHAT 组含 askUserQuestion 等强依赖
     * App 内 UI 的工具，DATA 组含 browser / device_storage / network_proxy 等依赖
     * 本机登录态与系统权限的工具，两者默认仅内部使用。
     */
    val remotelyExposedByDefault: Boolean
) {
    CORE("MiniMe-Core", "工作区与执行", true),
    CHAT("MiniMe-Chat", "会话协作与编排", false),
    DATA("MiniMe-Data", "外部世界与工程", false);

    companion object {
        /** 按 server 名反查分组；非内置 server（含远端）返回 null。 */
        fun byServerName(name: String): BuiltinGroup? = entries.firstOrNull { it.serverName == name }
    }
}

@Singleton
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    /**
     * 工具名 -> 内置分组。未登记（如远端 MCP 工具）的工具不出现在此表。
     *
     * 与 [tools] 并行维护，`unregister` 时同步清理，避免留下悬空分组。
     */
    private val builtinGroups = ConcurrentHashMap<String, BuiltinGroup>()

    /**
     * 别名 -> 正式名 的映射，用于历史工具名迁移期兼容（见 [AgentTool.aliases]）。
     * 仅参与查找回退，不作为工具定义暴露给 AI。
     */
    private val aliasMap = ConcurrentHashMap<String, String>()

    /**
     * F2：注册表变更版本号。
     *
     * [tools] 是 `ConcurrentHashMap`，本身**没有变更通知**——而远端 MCP 工具是运行期
     * 动态注册/注销的（`McpManager.connectOne` / `disconnectOne`），旧聚合层因此只能在
     * 构造期做一次性快照：进入页面后连接或断开任一 server，工具列表都不再变化。
     *
     * 这里用单调递增版本号把变更**转成响应式信号**，而不去动`tools` 的数据结构或给
     * 每个 `AgentTool` 加 Flow（后者会把工具数量级的开销摊到每次工具定义序列化上）。
     * 消费者 `combine(version) { registry 快照 }` 即可获得正确的刷新时机。
     *
     * 只增不减、不可重置：消费者据此判断「是否需要重新读快照」。
     */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    /**
     * 注册工具。
     *
     * @param builtinGroup 内置能力分组；本地内置工具须显式声明，远端 MCP 工具传 null
     *   （不参与内置分组，也不计入分组覆盖率统计）。
     */
    fun register(name: String, tool: AgentTool, builtinGroup: BuiltinGroup? = null) {
        val existed = tools.containsKey(name)
        val groupChanged = builtinGroup != null && builtinGroups[name] != builtinGroup
        tools[name] = tool
        if (builtinGroup != null) {
            builtinGroups[name] = builtinGroup
        }
        tool.aliases.forEach { alias ->
            // 别名不得与既有正式名冲突（防遮蔽），也不得抢占已被其他工具登记的别名（防劫持）
            if (!tools.containsKey(alias) && !aliasMap.containsKey(alias)) {
                aliasMap[alias] = name
            }
        }
        // 远端 server 重连会重复注册同名同实例工具（McpManager 的幂等路径），内容未变则不发信号，
        // 否则每次重连都会让所有订阅方重建快照并重组，而 UI 看起来毫无变化。
        if (!existed || groupChanged) {
            bumpVersion()
        }
    }

    fun getTool(name: String): AgentTool? {
        return tools[name] ?: aliasMap[name]?.let { canonical -> tools[canonical] }
    }

    fun unregister(name: String) {
        val removed = tools.remove(name) ?: aliasMap.remove(name)?.let { canonical -> tools.remove(canonical) }
        // 分组随工具一同清理：远端 server 断开时不得残留分组登记。
        // 工具本就不存在时不得推进版本号——否则一次无意义的注销会让所有订阅方
        // 重建快照并重组，而 UI 看起来毫无变化。
        val groupRemoved = builtinGroups.remove(name) != null
        if (removed != null) {
            removed.aliases.forEach { aliasMap.remove(it) }
            bumpVersion()
        } else if (groupRemoved) {
            // 工具已不在表中但分组登记残留（如历史版本的清理遗漏），仍属状态变化
            bumpVersion()
        }
    }

    /**
     * 推进注册表版本号（仅在内容真正变化时由 [register] / [unregister] 调用）。
     */
    private fun bumpVersion() {
        _version.value = _version.value + 1
    }

    /** 查询内置分组；非内置工具（含远端 MCP 工具）返回 null。 */
    fun getBuiltinGroup(name: String): BuiltinGroup? = builtinGroups[name]

    /** 某分组下的全部工具（按名称排序）；远端工具不在任何分组内，故不出现在结果中。 */
    fun getToolsByBuiltinGroup(group: BuiltinGroup): List<AgentTool> =
        builtinGroups.entries
            .filter { it.value == group }
            .mapNotNull { tools[it.key] }
            .sortedBy { it.name }

    /** 已声明分组的工具数量（分组覆盖率统计用）。 */
    fun getGroupedToolCount(): Int = builtinGroups.size

    fun getToolNames(): Set<String> {
        return tools.keys.toSet()
    }

    fun getAvailableTools(): List<AgentTool> {
        return tools.values.toList()
    }

    /**
     * 根据当前模式返回可用工具列表。
     * PLAN 模式下仍返回全部工具定义（让 AI 知道这些工具存在，可在计划中引用），
     * 写操作工具由 [ToolPermissionPolicyEngine] 在运行时拦截并返回 PLAN_MODE_REJECTED。
     */
    fun getAvailableTools(mode: AgentMode): List<AgentTool> {
        return tools.values.toList()
    }

    fun hasTool(name: String): Boolean {
        return tools.containsKey(name) || aliasMap.containsKey(name)
    }

    fun getAllToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.toToolDefinition() }
    }
}
