package com.mini.me_core.feature.qqbot.domain

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneBot 11 reverse WebSocket server.
 *
 * Listens on [QBotConstants.WS_HOST]:[QBotConstants.DEFAULT_WS_PORT] at path
 * [QBotConstants.WS_PATH]. LLBot connects to this server as a WebSocket client.
 *
 * Responsibilities:
 * - Start / stop the Ktor CIO WebSocket endpoint.
 * - Parse incoming OneBot 11 event JSON into [OneBotMessageEvent].
 * - Track the active [DefaultWebSocketSession] and expose [wsState] as [StateFlow].
 * - Send API requests via [sendApiRequest] and correlate responses by `echo`.
 */
@Singleton
class OneBot11Server @Inject constructor(
    private val logManager: QBotLogManager
) {

    companion object {
        private const val TAG = "OneBot11Server"
        private const val RESPONSE_TIMEOUT_MS = 10_000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var engine: ApplicationEngine? = null

    private val _wsState = MutableStateFlow(QBotWsState.DISCONNECTED)

    /** Reactive WebSocket connection state. */
    val wsState: StateFlow<QBotWsState> = _wsState.asStateFlow()

    @Volatile
    private var currentSession: DefaultWebSocketSession? = null

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<OneBotApiResponse>>()

    /** Invoked when a OneBot 11 message event is received. */
    var onMessage: ((OneBotMessageEvent) -> Unit)? = null

    /** Invoked when the WebSocket connection state changes. */
    var onConnectionStateChange: ((QBotWsState) -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start the WebSocket server on [port].
     * Safe to call multiple times; subsequent calls are ignored if already running.
     */
    fun start(port: Int = QBotConstants.DEFAULT_WS_PORT) {
        if (engine != null) {
            logManager.warn(TAG, "Server already running, ignore start request")
            return
        }
        _wsState.value = QBotWsState.CONNECTING
        onConnectionStateChange?.invoke(QBotWsState.CONNECTING)
        logManager.info(TAG, "Starting WebSocket server on ${QBotConstants.WS_HOST}:$port")

        try {
            val server = embeddedServer(CIO, host = QBotConstants.WS_HOST, port = port) {
                install(WebSockets)
                routing {
                    webSocket(QBotConstants.WS_PATH) {
                        handleConnection(this)
                    }
                }
            }
            server.start(wait = false)
            engine = server
            logManager.info(TAG, "WebSocket server started successfully")
        } catch (e: Exception) {
            logManager.error(TAG, "Failed to start WebSocket server", e)
            _wsState.value = QBotWsState.DISCONNECTED
            onConnectionStateChange?.invoke(QBotWsState.DISCONNECTED)
        }
    }

    /**
     * Stop the WebSocket server, close any active session, and clear pending requests.
     */
    fun stop() {
        logManager.info(TAG, "Stopping WebSocket server")
        currentSession = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        try {
            engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        } catch (e: Exception) {
            logManager.error(TAG, "Error stopping WebSocket server", e)
        } finally {
            engine = null
            _wsState.value = QBotWsState.DISCONNECTED
            onConnectionStateChange?.invoke(QBotWsState.DISCONNECTED)
            logManager.info(TAG, "WebSocket server stopped")
        }
    }

    /**
     * @return true if the underlying Ktor engine is running.
     */
    fun isRunning(): Boolean = engine != null

    /**
     * Send a OneBot 11 API request to the connected LLBot.
     *
     * The request body is `{"action": action, "params": params, "echo": <uuid>}`.
     * The method suspends until the matching response arrives or [RESPONSE_TIMEOUT_MS] elapses.
     *
     * @return the parsed [OneBotApiResponse], or null if no connection / timeout / error.
     */
    suspend fun sendApiRequest(action: String, params: JsonObject): OneBotApiResponse? {
        val session = currentSession ?: run {
            logManager.warn(TAG, "Cannot send API request: no active LLBot connection")
            return null
        }

        val echo = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<OneBotApiResponse>()
        pendingRequests[echo] = deferred

        val request = buildJsonObject {
            put("action", action)
            put("params", params)
            put("echo", echo)
        }

        return try {
            session.send(Frame.Text(request.toString()))
            logManager.debug(TAG, "Sent API request action=$action echo=$echo")

            val response = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) {
                deferred.await()
            }
            if (response == null) {
                logManager.warn(TAG, "API request timeout action=$action echo=$echo")
            }
            pendingRequests.remove(echo)
            response
        } catch (e: Exception) {
            logManager.error(TAG, "Failed to send API request action=$action", e)
            pendingRequests.remove(echo)
            null
        }
    }

    /**
     * Handle a single incoming WebSocket connection from LLBot.
     * Runs until the connection is closed.
     */
    private suspend fun handleConnection(session: DefaultWebSocketSession) {
        currentSession = session
        _wsState.value = QBotWsState.CONNECTED
        onConnectionStateChange?.invoke(QBotWsState.CONNECTED)
        logManager.info(TAG, "LLBot client connected")

        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> handleTextFrame(frame.readText())
                    else -> { /* ignore binary / close frames */ }
                }
            }
        } catch (e: Exception) {
            logManager.error(TAG, "Connection error while reading frames", e)
        } finally {
            if (currentSession == session) {
                currentSession = null
            }
            _wsState.value = QBotWsState.DISCONNECTED
            onConnectionStateChange?.invoke(QBotWsState.DISCONNECTED)
            logManager.info(TAG, "LLBot client disconnected, waiting for reconnect")
        }
    }

    /**
     * Parse an incoming text frame.
     *
     * If the frame contains an `echo` field matching a pending request, it is treated as
     * an API response. Otherwise, it is treated as a OneBot 11 event (`post_type`).
     */
    private fun handleTextFrame(text: String) {
        try {
            val root = json.parseToJsonElement(text).jsonObject

            // API response: has echo field and a pending request waiting for it.
            val echo = root["echo"]?.jsonPrimitive?.content
            if (echo != null && pendingRequests.containsKey(echo)) {
                val deferred = pendingRequests.remove(echo)
                val response = json.decodeFromJsonElement(OneBotApiResponse.serializer(), root)
                deferred?.complete(response)
                return
            }

            // Event: must have post_type.
            val postType = root["post_type"]?.jsonPrimitive?.content ?: run {
                logManager.debug(TAG, "Frame without post_type and no matching echo, ignored")
                return
            }

            when (postType) {
                "message" -> {
                    val event = json.decodeFromJsonElement(OneBotMessageEvent.serializer(), root)
                    logManager.debug(
                        TAG,
                        "Received message type=${event.messageType} userId=${event.userId} groupId=${event.groupId}"
                    )
                    onMessage?.invoke(event)
                }
                "meta_event" -> {
                    // Heartbeat / lifecycle event: no action needed in phase 1.
                    logManager.debug(TAG, "Received meta_event (heartbeat / lifecycle)")
                }
                else -> {
                    logManager.debug(TAG, "Unknown post_type: $postType")
                }
            }
        } catch (e: Exception) {
            logManager.error(TAG, "Failed to handle incoming text frame", e)
        }
    }
}
