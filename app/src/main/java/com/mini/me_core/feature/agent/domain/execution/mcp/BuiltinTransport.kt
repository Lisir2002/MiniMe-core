package com.mini.me_core.feature.agent.domain.execution.mcp

import com.mini.me_core.core.util.FileLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * F1 内置传输层：在**同一进程内**把 MCP 客户端接到内置 MCP 服务端。
 *
 * 为什么不监听端口、也不起子进程：
 * - 内置服务端 [com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerSession]
 *   是纯 JVM 文本入口（`handle(jsonText)`），不依赖 Ktor、不持有端口与生命周期；
 * - 编解码成本与 HTTP / stdio 传输完全一致（本就要做 [JsonRpcRequest] 序列化与
 *   [JsonRpcResponse] 反序列化），故「进程内」并不比「跨网络」更省任何一步；
 * - 免去端口占用、自举时序、后台存活与 token 鉴权四类问题，行为完全确定。
 *
 * 至此本地能力在**协议层**就是一台真正的 MCP server：走完整的
 * `initialize` → `notifications/initialized` → `tools/list` → `tools/call` 握手与调用，
 * 可被任意 MCP 客户端按标准协议发现和调用；`McpClient` 与 [McpTool] 无需任何改动。
 *
 * 注意执行面仍走 `ToolRegistry` 直连（见规划 §1）：若让内置工具的 Agent 调用也经
 * [McpTool] 包装，`buildNamespacedName` 会把 `readFile` 改成 `mcp__minime_core__readFile`，
 * 且 `flattenContent` 会把结构化结果压平成文本，破坏工具名与
 * [com.mini.me_core.feature.agent.domain.execution.tool.ToolResultTypeRegistry] 的按类型消费。
 * 本传输用于**协议面与枚举面**（`tools/list` → UI / CapabilityRegistry）。
 */
class BuiltinTransport(
    private val serverName: String,
    /** 进程内服务端会话；由 [BuiltinMcpServerFactory] 构造并注入。 */
    private val session: BuiltinMcpSession,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) : McpTransport {

    /** 请求 id 递增（与 [StreamableHttpTransport] 同惯例，服务端原样回显配对）。 */
    private val idCounter = AtomicLong(0)

    @Volatile
    private var closed = false

    /** server 标识（供日志与排查使用）。 */
    val name: String get() = serverName

    override suspend fun request(method: String, params: JsonObject?): JsonRpcResponse {
        check(!closed) { "[$serverName] 传输已关闭，无法发起 $method" }
        val id = idCounter.incrementAndGet()
        val payload = JsonRpcRequest(id = id, method = method, params = params)
        val body = json.encodeToString(JsonRpcRequest.serializer(), payload)
        FileLogger.d(TAG, "→ [$serverName] $method id=$id")

        // 通知类无应答；被当成请求发出属协议误用，直接失败而非静默返回空结果
        val responseText = session.handle(body)
            ?: throw McpException(message = "[$serverName] $method 无响应（服务端把它当作通知处理了）")

        val response = runCatching {
            json.decodeFromString(JsonRpcResponse.serializer(), responseText)
        }.getOrElse {
            throw McpException(message = "[$serverName] $method 响应解析失败: ${it.message}", cause = it)
        }

        // id 必须原样回显，否则无法把响应配对到请求
        if (response.id != null && response.id != id) {
            throw McpException(message = "[$serverName] $method 响应 id 不匹配：期望 $id，实得 ${response.id}")
        }

        response.error?.let { err ->
            throw McpException(rpcCode = err.code, message = "[$serverName] $method 失败: ${err.message}")
        }
        return response
    }

    /**
     * 通知（无 id，服务端不应答）。
     *
     * 进程内没有需要送达的通道，方法只需送达服务端；服务端按规范返回 null。
     * 这里**吞掉返回的 null**（它正是「通知已处理」的信号），异常则记日志而不外抛。
     */
    override suspend fun notify(method: String, params: JsonObject?) {
        check(!closed) { "[$serverName] 传输已关闭，无法发送 $method" }
        val payload = JsonRpcNotification(method = method, params = params)
        val body = json.encodeToString(JsonRpcNotification.serializer(), payload)
        FileLogger.d(TAG, "→ [$serverName] $method（通知）")
        runCatching { session.handle(body) }
            .onFailure { FileLogger.w(TAG, "[$serverName] $method 通知处理异常: ${it.message}") }
    }

    /** 进程内传输无资源可释放，置位关闭标记后可重复调用。 */
    override fun close() {
        closed = true
    }

    private companion object {
        const val TAG = "BuiltinTransport"
    }
}

/**
 * 进程内服务端会话端口（函数式）。
 *
 * 抽出接口而非直接依赖 [com.mini.me_core.feature.agent.domain.execution.mcp.server.McpServerSession]
 * 具体类，既避免传输层 → 服务端包的反向依赖，也便于单测注入桩实现。
 */
fun interface BuiltinMcpSession {
    /** 处理一条 JSON-RPC 文本，请求返回响应报文，通知返回 null。 */
    suspend fun handle(jsonText: String): String?
}
