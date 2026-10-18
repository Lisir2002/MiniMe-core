package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.core.util.FileLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 单个 MCP server 的会话客户端：负责握手、列举工具、转发工具调用。
 *
 * 生命周期：[connect] 完成 initialize → notifications/initialized → tools/list 后，
 * [tools] 即为该 server 暴露的工具描述符；[callTool] 把一次调用转成 `tools/call`。
 * 任何一步失败都抛 [McpException]，由上层（McpManager）按 server 粒度兜底，不影响其它 server。
 */
class McpClient(
    val serverName: String,
    private val transport: McpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val TAG = "McpClient"
    }

    @Volatile
    var tools: List<McpToolDescriptor> = emptyList()
        private set

    /**
     * G3：`initialize` 响应里 server 声明的 `capabilities`。
     *
     * 只用于 prompts/resources 的**快速路径**，不作为最终依据——能力声明按规范是
     * 提示性的，`supportsPrompts()` 为 true 不保证 `prompts/list` 真能成功，
     * 反之亦然。故所有 list 方法都必须容忍「声明了但失败」与「没声明但能用」。
     */
    @Volatile
    var serverCapabilities: JsonObject? = null
        private set

    /** 完整握手：initialize → initialized 通知 → tools/list。返回拿到的工具数。 */
    suspend fun connect(): Int {
        initialize()
        transport.notify("notifications/initialized")
        val count = refreshTools()
        FileLogger.i(TAG, "[$serverName] 握手完成，发现 $count 个工具")
        return count
    }

    private suspend fun initialize() {
        val params = buildJsonObject {
            put("protocolVersion", McpProtocol.PROTOCOL_VERSION)
            putJsonObject("capabilities") { /* 客户端暂不声明额外能力 */ }
            putJsonObject("clientInfo") {
                put("name", McpProtocol.CLIENT_NAME)
                put("version", McpProtocol.CLIENT_VERSION)
            }
        }
        val response = transport.request("initialize", params)
        // G3：记录 server 声明的能力——prompts/resources 是**可选**能力，
        // 后续枚举前先看这里，避免对不支持的 server 发方法探测（那会得到
        // 一个错误码异常，而不是空列表）。解析失败按「无能力」处理，不影响握手。
        serverCapabilities = runCatching {
            val result = response.result
            result?.get("capabilities") as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
    }

    // ── G3：prompts / resources 消费侧 ──────────────────────────────

    /**
     * 该 server 是否声明了 `prompts` 能力。
     *
     * 只在 `listPrompts` / `getPrompt` 处用作**快速路径**；即使这里判为 false，
     * 调用方仍应处理 `listPrompts()` 返回空列表的情况——能力声明是提示性的，
     * 实际支持与否以服务端应答为准（与「MCP annotations 不可信」同一取向）。
     */
    fun supportsPrompts(): Boolean = serverCapabilities?.containsKey("prompts") == true

    /** 同 [supportsPrompts]，对应 `resources` 能力。 */
    fun supportsResources(): Boolean = serverCapabilities?.containsKey("resources") == true

    /**
     * 枚举该 server 暴露的 prompts。
     *
     * **不支持时返回空列表而非抛异常**——prompts 是可选能力，让它拖垮整个
     * `connect()` 显然不合理。这与 `tools/list` 不同：后者是协议核心能力，
     * 失败必须抛出让上层把该 server 标为 FAILED。
     *
     * 需要区分「不支持」与「请求失败」的调用方请用 [listPromptsDetailed]；
     * 本方法刻意丢掉这个区别，因为对带着过期 assumptions 的核心路径而言它就是「没有」。
     */
    suspend fun listPrompts(): List<McpPromptDescriptor> = listPromptsDetailed().itemsOrEmpty()

    /** 枚举该 server 暴露的 resources；语义与 [listPrompts] 相同。 */
    suspend fun listResources(): List<McpResourceDescriptor> = listResourcesDetailed().itemsOrEmpty()

    /**
     * 枚举 prompts 的完整结果。
     *
     * 与 [listPrompts] 的差别只在于**保留** 了「为什么没有」：后者把三种空原因
     * （未声明能力 / 请求失败 / 真的没有）压成同一个空列表。对核心路径来说这种压缩
     * 是对的（早退即可），但对 UI 是信息丢失——用户看到「没有 prompts」时无法判断
     * 该不该重试。故多给一层，让调用方按需取精度。
     */
    suspend fun listPromptsDetailed(): OptionalCapability<McpPromptDescriptor> {
        if (!supportsPrompts()) return OptionalCapability.Unsupported
        return runCatching {
            val result = transport.request("prompts/list").result
                ?: return@runCatching OptionalCapability.Listed(emptyList())
            val prompts = json.decodeFromJsonElement(McpPromptsListResult.serializer(), result).prompts
            OptionalCapability.Listed(prompts)
        }.getOrElse { e ->
            // 协议错误码（如 -32601 方法不存在）说明该 server 实际不支持 prompts，
            // 按「空」处理并记日志，不当作连接失败。
            FileLogger.w(TAG, "[$serverName] 枚举 prompts 失败，按不支持处理: ${e.message}")
            OptionalCapability.Failed(e.message.orEmpty())
        }
    }

    /** 枚举 resources 的完整结果；语义与 [listPromptsDetailed] 相同。 */
    suspend fun listResourcesDetailed(): OptionalCapability<McpResourceDescriptor> {
        if (!supportsResources()) return OptionalCapability.Unsupported
        return runCatching {
            val result = transport.request("resources/list").result
                ?: return@runCatching OptionalCapability.Listed(emptyList())
            val resources = json.decodeFromJsonElement(McpResourcesListResult.serializer(), result).resources
            OptionalCapability.Listed(resources)
        }.getOrElse { e ->
            FileLogger.w(TAG, "[$serverName] 枚举 resources 失败，按不支持处理: ${e.message}")
            OptionalCapability.Failed(e.message.orEmpty())
        }
    }

    /**
     * `prompts/get`：取一个 prompt 的 messages，返回**已拼好的纯文本**。
     *
     * 扁平化在此处完成（而不是把结构化 messages 抛给调用方）有两个理由：
     * 一是 [callTool] 已确立「`McpClient` 对外只给纯文本」的约定，保持一致；
     * 二是 messages 的 content 块形状随内容类型变化，让每个调用方各写一遍
     * 形状判断，等于把同一个坑复制 N 份。
     *
     * 文本经 [McpContentSanitizer] 中和：prompt 正文来自**外部 server**，
     * 与工具结果同属不可信数据，不做净化就等于允许外部内容注入指令。
     */
    suspend fun getPrompt(name: String, arguments: Map<String, String> = emptyMap()): String {
        val params = buildJsonObject {
            put("name", name)
            if (arguments.isNotEmpty()) {
                putJsonObject("arguments") { arguments.forEach { (k, v) -> put(k, v) } }
            }
        }
        val result = transport.request("prompts/get", params).result
            ?: throw McpException(message = "[$serverName] prompts/get 无 result")
        val messages = result["messages"] as? JsonArray
            ?: throw McpException(message = "[$serverName] prompts/get 响应缺 messages")
        val sb = StringBuilder()
        for (msg in messages) {
            val obj = msg as? JsonObject ?: continue
            sb.append(flattenContent(obj["content"]))
            sb.append("\n")
        }
        return McpContentSanitizer.sanitizeResult(sb.toString().trimEnd())
    }

    /**
     * `resources/read`：读一个 resource 的文本内容。
     *
     * 按 [uri] 寻址（规范里 resource 的主键是 URI，不是 name）。
     */
    suspend fun readResource(uri: String): String {
        val params = buildJsonObject { put("uri", uri) }
        val result = transport.request("resources/read", params).result
            ?: throw McpException(message = "[$serverName] resources/read 无 result")
        val contents = result["contents"] as? JsonArray
            ?: throw McpException(message = "[$serverName] resources/read 响应缺 contents")
        val sb = StringBuilder()
        for (item in contents) {
            val obj = item as? JsonObject ?: continue
            // 单条 contents 直接带 text；若走 blob 则是 base64，本仓不消费二进制。
            (obj["text"] as? JsonPrimitive)?.contentOrNull?.let {
                sb.append(it)
                sb.append("\n")
            }
        }
        return McpContentSanitizer.sanitizeResult(sb.toString().trimEnd())
    }

    /** 重新拉取工具列表（配置变更或手动刷新时调用）。 */
    suspend fun refreshTools(): Int {
        val response = transport.request("tools/list")
        val result = response.result ?: throw McpException(message = "[$serverName] tools/list 无 result")
        tools = runCatching {
            json.decodeFromJsonElement(McpToolsListResult.serializer(), result).tools
        }.getOrElse {
            throw McpException(message = "[$serverName] 解析工具列表失败: ${it.message}", cause = it)
        }
        return tools.size
    }

    /**
     * 执行一次工具调用，返回扁平化为文本的结果（喂回模型）。
     * MCP 的 `tools/call` 结果是 content 块数组；这里把 text 块拼接，其它类型给出占位描述。
     */
    suspend fun callTool(toolName: String, arguments: JsonObject): String {
        val params = buildJsonObject {
            put("name", toolName)
            put("arguments", arguments)
        }
        val response = transport.request("tools/call", params)
        val result = response.result ?: throw McpException(message = "[$serverName] tools/call 无 result")

        val isError = (result["isError"] as? JsonPrimitive)?.contentOrNull == "true"
        val rawText = flattenContent(result["content"])
        // MCP 返回文本为不可信外部数据：中和其中的指令模式并加围栏，防止被模型当作越权指令。
        val text = McpContentSanitizer.sanitizeResult(rawText)
        return if (isError) "Error: $text" else text
    }

    /**
     * 把 content 转成纯文本：text 块取 text；其它块给类型占位。
     *
     * 同时接受两种形状，因为规范里两者都合法：
     * - **数组**（`tools/call` 的 `content`）：多个内容块；
     * - **单个块对象**（`prompts/get` 的 `message.content`）：规范定义为单个 content block，
     *   不是数组。
     *
     * 曾只处理数组，导致 prompt 正文恒为空串——而症状是「点了 prompt 得到空白」，
     * 与「这个 prompt 本来就是空的」完全无法区分。
     */
    private fun flattenContent(content: kotlinx.serialization.json.JsonElement?): String {
        if (content is JsonObject) return flattenContentBlock(content)
        val array = content as? JsonArray ?: return ""
        val sb = StringBuilder()
        for (block in array) {
            val obj = block as? JsonObject ?: continue
            sb.append(flattenContentBlock(obj))
            sb.append("\n")
        }
        return sb.toString().trimEnd()
    }

    /** 单个 content 块的文本化。 */
    private fun flattenContentBlock(obj: JsonObject): String = when ((obj["type"] as? JsonPrimitive)?.contentOrNull) {
        "text" -> (obj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        "image" -> "[image content]"
        "resource" -> "[resource content]"
        else -> (obj["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    fun close() = transport.close()
}
