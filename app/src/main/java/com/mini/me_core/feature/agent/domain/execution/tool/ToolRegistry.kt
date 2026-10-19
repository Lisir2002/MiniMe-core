package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    /**
     * 别名 -> 正式名 的映射，用于历史工具名迁移期兼容（见 [AgentTool.aliases]）。
     * 仅参与查找回退，不作为工具定义暴露给 AI。
     */
    private val aliasMap = ConcurrentHashMap<String, String>()

    fun register(name: String, tool: AgentTool) {
        tools[name] = tool
        tool.aliases.forEach { alias ->
            // 别名不得与既有正式名冲突（防遮蔽），也不得抢占已被其他工具登记的别名（防劫持）
            if (!tools.containsKey(alias) && !aliasMap.containsKey(alias)) {
                aliasMap[alias] = name
            }
        }
    }

    fun getTool(name: String): AgentTool? {
        return tools[name] ?: aliasMap[name]?.let { canonical -> tools[canonical] }
    }

    fun unregister(name: String) {
        val removed = tools.remove(name) ?: aliasMap.remove(name)?.let { canonical -> tools.remove(canonical) }
        if (removed != null) {
            removed.aliases.forEach { aliasMap.remove(it) }
        }
    }

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
