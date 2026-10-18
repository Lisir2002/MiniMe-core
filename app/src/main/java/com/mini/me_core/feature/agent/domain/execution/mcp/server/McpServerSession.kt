package com.mini.me_core.feature.agent.domain.execution.mcp.server

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.mcp.McpProtocol
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * MCP server 协议会话层：解析 JSON-RPC 报文并产出响应（纯 JVM 逻辑，可单测）。
 *
 * 实现 [builtin-mcp-server-design.md] §5.2 的四种方法：
 * - `initialize`：校验 protocolVersion，回 serverInfo + capabilities；
 * - `tools/list`：经 [AgentToolMcpAdapter] 拉取暴露工具；
 * - `tools/call`：执行远程调用（含权限审批），回 content 块；
 * - `ping`：回 `{}` 保活。
 * notifications/initialized 等通知无 id，返回 null → HTTP 层回 202 空响应。
 *
 * 响应以 [JsonObject] 直出（完整 JSON-RPC 报文），id 原样回显（支持数字/字符串），
 * 便于与外部客户端（Claude Desktop / Trae / Cursor）互通。
 *
 * F1：本类同时被两条链路复用：
 * - 对外服务：[McpHttpServer] 经 HTTP 暴露（单台，server 名取 [McpProtocol.SERVER_NAME]）；
 * - 进程内：[com.mini.me_core.feature.agent.domain.execution.mcp.BuiltinTransport] 经
 *   [BuiltinMcpSession] 直调同一 [handle]，使本地能力在协议层成为真正的 MCP server。
 * 因此 [serverName] 改为构造参数，内置三分组各报自己的名字。
 */
class McpServerSession(
    private val adapter: AgentToolMcpAdapter,
    /** F1：本会话对外声明的 server 名（内置分组各一台，如 MiniMe-Core）。 */
    private val serverName: String = McpProtocol.SERVER_NAME,
    /** F1：本会话服务的能力分组（null = 全量，即 HostServer 的既有行为）。 */
    private val builtinGroup: BuiltinGroup? = null,
    /**
     * F4：技能适配器（prompts / resources）。
     *
     * 默认传 [EmptySkillMcpAdapter] 而非 `null`：技能是**用户维度**的能力，
     * 内置三分组 server 服务的是工具维度，故刻意不接技能。给默认值而非要求显式传参，
     * 是为了让「哪台 server 暴露技能」在装配处一目了然，又不必改所有既有构造点。
     */
    private val skillAdapter: SkillMcpAdapter = EmptySkillMcpAdapter,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val TAG = "McpServerSession"
    }

    /**
     * 处理一条请求/通知文本。
     *
     * @return 需要回给客户端的 JSON-RPC 响应（[JsonObject]）；通知类返回 null。
     */
    suspend fun handle(jsonText: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(jsonText) as JsonObject }
            .getOrElse { e ->
                FileLogger.w(TAG, "JSON-RPC 解析失败: ${e.message}")
                return errorResponse(JsonNull, -32700, "Parse error")
            }

        val id: JsonElement = root["id"] ?: JsonNull
        val method = (root["method"] as? JsonPrimitive)?.contentOrNull

        if (method == null) {
            return errorResponse(id, -32600, "Invalid Request")
        }
        // 通知：无 id、服务端不应答。
        if (method.startsWith("notifications/")) {
            FileLogger.d(TAG, "收到通知 $method（不应答）")
            return null
        }

        return when (method) {
            "initialize" -> resultResponse(id, initializeResult())
            "ping" -> resultResponse(id, buildJsonObject { })
            "tools/list" -> handleListTools(id)
            "tools/call" -> handleCallTool(id, root)
            // F4：技能经 MCP 原生协议暴露（PROMPT 型 → prompts，SCRIPT 型 → resources）
            "prompts/list" -> handleListPrompts(id)
            "prompts/get" -> handleGetPrompt(id, root)
            "resources/list" -> handleListResources(id)
            "resources/read" -> handleReadResource(id, root)
            else -> {
                FileLogger.w(TAG, "未知方法 $method")
                errorResponse(id, -32601, "Method not found: $method")
            }
        }
    }

    /**
     * F4：当前会话可暴露的 prompt / resource 数量（仅用于启动日志观测）。
     *
     * 刻意不缓存：技能列表会被用户随时增删，缓存必然过期。
     */
    fun promptCount(): Int = skillAdapter.listPrompts().size

    fun resourceCount(): Int = skillAdapter.listResources().size

    // ── 各方法实现 ──────────────────────────────────────────────

    private fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", McpProtocol.PROTOCOL_VERSION)
        putJsonObject("capabilities") {
            putJsonObject("tools") { }
            // F4：prompts / resources 的能力**按实际是否接入技能声明**。
            // 规范要求「支持 prompts MUST 声明 prompts capability」——反之也成立：
            // 没接技能却声明能力，等于对外承诺一个只会返回空列表的方法。
            // listChanged 本实现暂不声明：技能列表变化由宿主 App 内部消化，
            // 不经本 server 推送（尚无 `notifications/prompts/list_changed` 的触发点）。
            if (skillAdapter.hasSkills()) {
                putJsonObject("prompts") { }
                putJsonObject("resources") { }
            }
        }
        putJsonObject("serverInfo") {
            put("name", serverName)
            put("version", McpProtocol.SERVER_VERSION)
        }
        put(
            "instructions",
            if (builtinGroup != null) {
                "MiniMe-core 内置 MCP 服务器（${builtinGroup.displayName}）：本组能力在协议层可用，可被任意 MCP 客户端发现与调用。"
            } else {
                "MiniMe-core 内置 MCP 服务器：把设备的 Linux 编码后端（容器/文件/git/搜索/AI 工具）开放给外部 MCP 客户端。"
            }
        )
    }

    private fun handleListTools(id: JsonElement): JsonObject {
        val tools = adapter.listTools()
        val result = buildJsonObject {
            putJsonArray("tools") {
                tools.forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        tool.description?.let { put("description", it) }
                        tool.inputSchema?.let { put("inputSchema", it) }
                    })
                }
            }
        }
        return resultResponse(id, result)
    }

    private suspend fun handleCallTool(id: JsonElement, root: JsonObject): JsonObject {
        val params = root["params"] as? JsonObject
            ?: return errorResponse(id, -32602, "Invalid params")
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
            ?: return errorResponse(id, -32602, "Missing tool name")
        val arguments = params["arguments"] as? JsonObject ?: buildJsonObject { }

        val callResult = adapter.callTool(name, arguments)
        return resultResponse(id, callResult)
    }

    // ── F4：prompts / resources ───────────────────────────────────

    private fun handleListPrompts(id: JsonElement): JsonObject {
        val prompts = skillAdapter.listPrompts()
        val result = buildJsonObject {
            putJsonArray("prompts") { prompts.forEach { add(it.toPromptDescriptor()) } }
            // 分页游标：本实现一次性返回全量，故不带 nextCursor。
            // 规范把 nextCursor 设为可选，客户端见到缺失即视为单页。
        }
        return resultResponse(id, result)
    }

    private fun handleGetPrompt(id: JsonElement, root: JsonObject): JsonObject {
        val params = root["params"] as? JsonObject
            ?: return errorResponse(id, -32602, "Invalid params")
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull
            ?: return errorResponse(id, -32602, "Missing prompt name")
        // 未知 prompt 名按规范返回 -32602（Invalid params），不是 -32601：
        // 方法本身存在，只是参数值无效。
        val skill = skillAdapter.getPrompt(name)
            ?: return errorResponse(id, -32602, "Unknown prompt: $name")
        return resultResponse(id, skill.toPromptResult())
    }

    private fun handleListResources(id: JsonElement): JsonObject {
        val resources = skillAdapter.listResources()
        val result = buildJsonObject {
            putJsonArray("resources") { resources.forEach { add(it.toResourceDescriptor()) } }
        }
        return resultResponse(id, result)
    }

    private fun handleReadResource(id: JsonElement, root: JsonObject): JsonObject {
        val params = root["params"] as? JsonObject
            ?: return errorResponse(id, -32602, "Invalid params")
        val uri = (params["uri"] as? JsonPrimitive)?.contentOrNull
            ?: return errorResponse(id, -32602, "Missing resource uri")
        // URI 来自外部客户端（不可信输入）：畸形 URI 走「资源不存在」，不抛异常。
        val skill = skillAdapter.getResource(uri)
            ?: return errorResponse(id, -32602, "Unknown resource: $uri")
        return resultResponse(id, skill.toResourceResult())
    }

    // ── 响应构造 ────────────────────────────────────────────────

    private fun resultResponse(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    private fun errorResponse(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}
