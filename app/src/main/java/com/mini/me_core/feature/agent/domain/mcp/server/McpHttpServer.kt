package com.mini.me_core.feature.agent.domain.mcp.server

import com.mini.me_core.core.util.FileLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 内置 MCP 服务器的 HTTP 传输层（Ktor CIO，Streamable HTTP 形态）。
 *
 * 端点形态（对齐客户端 [com.mini.me_core.feature.agent.domain.mcp.StreamableHttpTransport]）：
 * - `POST /mcp`：接收 JSON-RPC，回 `application/json`（单条响应）或
 *   `text/event-stream`（请求 Accept 含 SSE 时按 `event: message` 帧包装）；首个响应带 Mcp-Session-Id。
 * - `GET /mcp`：SSE 保活长连接（客户端拉取服务端推送，首期仅空流保活）。
 * - `DELETE /mcp`：结束会话（返回 200，便于客户端优雅关闭）。
 *
 * 鉴权：每个请求校验 `Authorization: Bearer <token>`（常量时间比较），失败回 401。
 * 配置变更走「重建服务器」生效（见 [McpServerManager]），本类持有一份启动快照。
 */
class McpHttpServer(
    private val settings: McpServerSettings,
    private val session: McpServerSession,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
) {
    private companion object {
        const val TAG = "McpHttpServer"
        const val ENDPOINT = "/mcp"
        const val SESSION_HEADER = "Mcp-Session-Id"
        val SSE_CONTENT_TYPE = ContentType.parse("text/event-stream")
        const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L
        const val MAX_SESSIONS = 100
        const val CLEANUP_INTERVAL_MS = 60 * 1000L
    }

    private var engine: ApplicationEngine? = null

    /** sessionId -> 最后活跃时间戳（ms），支持超时清理和上限管理。 */
    private val sessions = ConcurrentHashMap<String, Long>()
    private val sessionCounter = AtomicLong(0)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val port: Int get() = settings.port

    /** 启动 Ktor 服务器。返回是否成功（失败原因记入日志）。 */
    suspend fun start(): Boolean = try {
        stop()
        val server = embeddedServer(CIO, port = settings.port, host = "0.0.0.0") {
            serverModule()
        }
        server.start(wait = false)
        engine = server
        startCleanupLoop()
        FileLogger.i(TAG, "MCP 服务器已监听 0.0.0.0:${settings.port}$ENDPOINT")
        true
    } catch (e: Exception) {
        FileLogger.e(TAG, "启动 MCP 服务器失败: ${e.message}", e)
        false
    }

    /** 停止 Ktor 服务器。 */
    suspend fun stop() {
        try {
            engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        } catch (e: Exception) {
            FileLogger.w(TAG, "停止 MCP 服务器时出错（忽略）: ${e.message}", e)
        } finally {
            engine = null
            sessions.clear()
        }
    }

    /** 定期清理超时会话：30 分钟无活动移除，上限 100 个。 */
    private fun startCleanupLoop() {
        cleanupScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL_MS)
                val now = System.currentTimeMillis()
                // 移除超时会话
                val expired = sessions.entries.filter { now - it.value > SESSION_TIMEOUT_MS }
                expired.forEach { sessions.remove(it.key) }
                if (expired.isNotEmpty()) {
                    FileLogger.i(TAG, "清理 ${expired.size} 个超时会话，剩余 ${sessions.size}")
                }
                // 超过上限时移除最旧的
                while (sessions.size > MAX_SESSIONS) {
                    val oldest = sessions.minByOrNull { it.value } ?: break
                    sessions.remove(oldest.key)
                    FileLogger.i(TAG, "会话数超上限，移除最旧会话: ${oldest.key}")
                }
            }
        }
    }

    private fun Application.serverModule() {
        routing {
            route(ENDPOINT) {
                post {
                    if (!authenticate(call)) {
                        call.respondText("Unauthorized", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    handlePost(call)
                }
                get {
                    if (!authenticate(call)) {
                        call.respondText("Unauthorized", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        return@get
                    }
                    handleGet(call)
                }
                delete {
                    call.respondText("OK", ContentType.Text.Plain, HttpStatusCode.OK)
                }
            }
        }
    }

    // ── POST：JSON-RPC 处理 ──────────────────────────────────────

    private suspend fun handlePost(call: ApplicationCall) {
        val body = call.receiveText()
        FileLogger.d(TAG, "POST body: ${body.take(200)}")
        val response = try {
            session.handle(body)
        } catch (e: Exception) {
            FileLogger.e(TAG, "会话处理异常: ${e.message}", e)
            null
        }

        // 通知类无应答 → 202 Accepted（Streamable HTTP 规范）。
        if (response == null) {
            call.respondText("Accepted", ContentType.Text.Plain, HttpStatusCode.Accepted)
            return
        }

        // 会话管理：优先复用客户端传来的 session id，否则新建。
        val now = System.currentTimeMillis()
        val incomingSessionId = call.request.headers[SESSION_HEADER]
        val sessionId = when {
            incomingSessionId != null && sessions.containsKey(incomingSessionId) -> {
                sessions[incomingSessionId] = now
                incomingSessionId
            }
            else -> {
                val newId = "minime-${sessionCounter.incrementAndGet()}"
                sessions[newId] = now
                newId
            }
        }
        call.response.headers.append(SESSION_HEADER, sessionId)

        val responseText = response.toString()
        val accept = call.request.headers[HttpHeaders.Accept].orEmpty()
        if (accept.contains("text/event-stream", ignoreCase = true)) {
            call.respondText(
                "event: message\ndata: $responseText\n\n",
                SSE_CONTENT_TYPE,
                HttpStatusCode.OK
            )
        } else {
            call.respondText(responseText, ContentType.Application.Json, HttpStatusCode.OK)
        }
    }

    // ── GET：SSE 保活长连接 ──────────────────────────────────────

    private suspend fun handleGet(call: ApplicationCall) {
        call.respondTextWriter(contentType = SSE_CONTENT_TYPE, status = HttpStatusCode.OK) {
            try {
                write(": keepalive\n\n")
                flush()
                while (true) {
                    delay(15_000)
                    write(": keepalive\n\n")
                    flush()
                }
            } catch (_: IOException) {
                // 客户端断开，正常结束。
            } catch (_: kotlinx.coroutines.CancellationException) {
                // 服务器停止，正常结束。
            }
        }
    }

    private fun authenticate(call: ApplicationCall): Boolean {
        val header = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val presented = header.removePrefix("Bearer").trim()
        return McpServerSecurity.isValidToken(settings.token, presented)
    }
}
