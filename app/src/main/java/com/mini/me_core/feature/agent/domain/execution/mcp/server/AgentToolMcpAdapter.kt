package com.mini.me_core.feature.agent.domain.execution.mcp.server

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.mcp.McpToolDescriptor
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionChoice
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * AgentTool ↔ MCP tool 双向映射（服务端侧）。
 *
 * - [listTools]：把 `ToolRegistry` 中**允许暴露**的工具转成 MCP tool 描述
 *   （复用 [AgentTool.toJsonSchema] 生成 inputSchema）。
 * - [callTool]：执行一次远程调用，走权限审批（复用 [ToolPermissionManager]），
 *   结果封装为 MCP `content` 块。
 *
 * 安全模型：
 * - 逐工具远程黑名单不暴露（[McpServerSecurity.isExposed]），仅当 [respectRemoteBlacklist] 为真时生效；
 * - `requireApproval=true`（默认）时，即使工具 AUTO_APPROVE 也进入审批弹窗；
 *   关闭时仅对 ASK 策略工具弹窗（与本地行为一致）。
 *
 * F1：内置 server 复用本适配器，但**不复用远程黑名单**（[respectRemoteBlacklist] = false）。
 * 黑名单的语义是「不得被**远程客户端**触达」，而内置 server 的 `tools/list` 服务于
 * App 内的分组枚举与卡片展示（`terminal` / `browser` / `askUserQuestion` 都要正常显示）。
 * 若沿用黑名单，内置分组会出现「13/19」这种工具凭空消失的现象。
 * 外部可达性改由「分组级 [BuiltinGroup.remotelyExposedByDefault]」在 HostServer 侧统一裁决，
 * 两道防线职责分离。
 */
class AgentToolMcpAdapter(
    private val toolRegistry: ToolRegistry,
    private val permissionManager: ToolPermissionManager,
    private val requireApproval: Boolean,
    /** F1：只列出该分组内的工具；null = 全量（既有 HostServer 行为）。 */
    private val builtinGroup: BuiltinGroup? = null,
    /** F1：是否套用远程暴露黑名单。内置 server 传 false（见类注释）。 */
    private val respectRemoteBlacklist: Boolean = true
) {
    private companion object {
        const val TAG = "McpServerAdapter"
        const val CALL_ID_PREFIX = "mcp-remote"
    }

    /** 对外暴露的工具描述列表（内置 server 按分组过滤；远端 server 剔除黑名单）。 */
    fun listTools(): List<McpToolDescriptor> {
        val candidates = if (builtinGroup != null) {
            // 内置 server：按分组取，绕过远程黑名单（黑名单只约束远程客户端，不约束 App 内分组展示）
            toolRegistry.getToolsByBuiltinGroup(builtinGroup)
        } else {
            toolRegistry.getAvailableTools()
        }
        return candidates
            .filter { !respectRemoteBlacklist || McpServerSecurity.isExposed(it.name) }
            .map { tool ->
                McpToolDescriptor(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = toJsonElement(tool.toJsonSchema()) as? JsonObject
                )
            }
    }

    /**
     * 执行一次 `tools/call`，返回 MCP 结果对象（含 `content` 与 `isError`）。
     *
     * @param arguments MCP 客户端传入的参数（JSON-RPC params.arguments）。
     */
    suspend fun callTool(name: String, arguments: JsonObject): JsonObject {
        val tool = toolRegistry.getTool(name)
            ?: return errorResult("TOOL_NOT_FOUND", "未知工具: $name")
        // 分组隔离：内置 server 只服务本分组的工具，跨组调用一律拒绝
        if (builtinGroup != null && toolRegistry.getBuiltinGroup(name) != builtinGroup) {
            return errorResult("TOOL_NOT_IN_GROUP", "工具 $name 不属于内置分组 ${builtinGroup.serverName}")
        }
        if (respectRemoteBlacklist && !McpServerSecurity.isExposed(name)) {
            return errorResult("TOOL_NOT_EXPOSED", "工具 $name 未开放给远程 MCP 客户端")
        }

        val choice = approve(tool, arguments)
        if (choice == PermissionChoice.REJECT) {
            FileLogger.w(TAG, "调用 $name 被用户拒绝")
            return errorResult("PERMISSION_DENIED", "用户拒绝调用工具 $name")
        }

        val result = runCatching { tool.execute(arguments) }
            .getOrElse { e ->
                FileLogger.e(TAG, "调用 $name 执行异常", e)
                return errorResult("TOOL_EXECUTION_FAILED", e.message ?: "工具执行异常")
            }
        return wrapResult(name, result)
    }

    /** 权限审批：requireApproval 且工具非 AUTO_APPROVE 时弹窗；否则按策略放行。 */
    private suspend fun approve(tool: AgentTool, args: JsonObject): PermissionChoice {
        val autoApprove = tool.permissionPolicy == ToolPermissionPolicy.AUTO_APPROVE
        if (autoApprove && !requireApproval) return PermissionChoice.ONCE

        val preview = args.toString().take(500)
        val isRemote = respectRemoteBlacklist
        val request = tool.buildPermissionRequest(
            callId = "$CALL_ID_PREFIX-${System.currentTimeMillis()}",
            args = args,
            argsPreview = preview
        ).copy(
            title = if (isRemote) "外部 MCP 调用 · ${tool.name}" else "内置 MCP 调用 · ${tool.name}",
            summary = if (isRemote) {
                "外部 MCP 客户端请求执行 ${tool.name}"
            } else {
                "内置 MCP server 请求执行 ${tool.name}"
            },
            // sessionId=null：审批卡对所有会话可见（AI 聊天面板会展示），
            // 避免请求因「会话不匹配」被 UI 过滤掉导致永远无法审批。
            sessionId = null
        )
        return permissionManager.awaitApproval(sessionId = null, request = request)
    }

    private fun wrapResult(toolName: String, result: ToolResult): JsonObject = when (result) {
        is ToolResult.Success -> successContent(result.data.toString())
        is ToolResult.Partial -> successContent(
            buildString {
                if (result.message.isNotBlank()) {
                    append(result.message)
                    append('\n')
                }
                append(result.data.toString())
            }
        )
        is ToolResult.Error -> {
            FileLogger.w(TAG, "调用 $toolName 返回错误 [${result.code}] ${result.message}")
            errorResult(result.code, result.message)
        }
    }

    private fun successContent(text: String): JsonObject = buildJsonObject {
        put("isError", JsonPrimitive(false))
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
    }

    private fun errorResult(code: String, message: String): JsonObject = buildJsonObject {
        put("isError", JsonPrimitive(true))
        put("code", code)
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", "Error[$code]: $message")
            }
        }
    }

    /** 把 AgentTool.toJsonSchema() 的 Map<String, Any> 转成 kotlinx JsonElement。 */
    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJsonElement(v) })
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }
}
