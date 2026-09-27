package com.mini.me_core.feature.qqbot.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lifecycle of the bundled LLBot (OneBot v11) process running inside the
 * terminal Linux container.
 *
 * Responsibilities:
 *  - generate the json5 reverse-Websocket config pointing at
 *    `ws://127.0.0.1:<port>/onebot/v11/ws`;
 *  - launch / stop the daemon through [ContainerBridge] (user-opaque, no PTY);
 *  - wait for the OneBot port to become ready (bounded poll);
 *  - stream the process log into [QBotLogManager];
 *  - watchdog the process and auto-restart on unexpected death with the bounded
 *    exponential backoff defined in [QBotConstants.RESTART_BACKOFF_MS].
 *
 * The observable [state] drives the UI; [start]/[stop]/[restart] are serialized through
 * a [Mutex] so concurrent taps cannot fork two daemons.
 */
@Singleton
class LLBotProcessManager @Inject constructor(
    private val containerBridge: ContainerBridge,
    private val logManager: QBotLogManager,
    private val qrCodeManager: QrCodeManager,
) {

    private companion object {
        const val TAG = "LLBotProcessManager"
        /** Working directory inside the container for the LLBot binary, config and logs. */
        const val WORKDIR = "/root/.minime/llbot"
        const val BINARY = "$WORKDIR/llbot"
        const val CONFIG_PATH = "$WORKDIR/${QBotConstants.LL_BOT_CONFIG_FILE}"
        const val LOG_PATH = "$WORKDIR/${QBotConstants.LL_BOT_LOG_FILE}"
        /** Max time to wait for the OneBot port after launch. */
        const val PORT_READY_TIMEOUT_MS = 30_000L
        const val PORT_POLL_INTERVAL_MS = 1_000L
        /** Grace period after SIGTERM before escalating to SIGKILL. */
        const val STOP_GRACE_MS = 5_000L
        const val PID_RESOLVE_DELAY_MS = 800L
    }

    private val _state = MutableStateFlow(QBotState.STOPPED)

    /** Observable lifecycle state for the UI. */
    val state: StateFlow<QBotState> = _state.asStateFlow()

    @Volatile
    private var pid: Int = -1

    @Volatile
    private var restartAttempts: Int = 0

    /**
     * 最近一次由 UI/服务层下发的配置。[start] 在生成配置文件时读取它，从而把
     * botQq / loginType / 密码等账号信息写进 LLBot 的 account 段。
     * ViewModel 在启动前通过 [configure] 注入；watchdog 自动重启时沿用同一份配置。
     */
    @Volatile
    var currentConfig: QBotConfig = QBotConfig()
        private set

    /** 启动前注入最新配置（幂等，可重复调用）。 */
    fun configure(config: QBotConfig) {
        currentConfig = config
    }

    /** Byte offset used to tail [LOG_PATH] incrementally. */
    @Volatile
    private var logOffset: Long = 0L

    private val lifecycleMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var watchdogJob: Job? = null

    /** @return current lifecycle state. */
    fun getState(): QBotState = _state.value

    /** @return LLBot process id, or -1 when not known. */
    fun getPid(): Int = pid

    /** @return true while the process is expected to be alive (starting or running). */
    fun isAlive(): Boolean = _state.value.isActive

    /**
     * Start LLBot: write config, launch the detached daemon, wait for the OneBot port.
     * Safe to call repeatedly; no-op when already starting/running.
     */
    suspend fun start() = lifecycleMutex.withLock {
        when (_state.value) {
            QBotState.STARTING, QBotState.RUNNING -> {
                logManager.log(QBotLogLevel.INFO, TAG, "start ignored, state=${_state.value}")
                return@withLock
            }
            else -> Unit
        }
        _state.value = QBotState.STARTING
        logManager.log(QBotLogLevel.INFO, TAG, "starting LLBot (port ${QBotConstants.DEFAULT_WS_PORT})")

        // Already running (e.g. a previous daemon survived a process restart)? Adopt it.
        if (containerBridge.isProcessRunning(QBotConstants.LL_BOT_PROCESS_NAME) ||
            containerBridge.checkPort(QBotConstants.DEFAULT_WS_PORT)
        ) {
            logManager.log(QBotLogLevel.INFO, TAG, "detected existing LLBot instance, adopting")
            pid = resolvePid()
            restartAttempts = 0
            _state.value = QBotState.RUNNING
            startWatchdog()
            return@withLock
        }

        // 1. Generate config.
        val configWritten = containerBridge.writeFile(CONFIG_PATH, buildConfigJson())
        if (!configWritten) {
            failAndScheduleRestart("failed to write config file")
            return@withLock
        }

        // 全新启动：清掉上一次残留的二维码，等待新一轮日志解析。
        qrCodeManager.clearQrCode()

        // 2. Launch the detached daemon (setsid decouples it from the short-lived exec shell).
        val launchCommand = buildString {
            append("mkdir -p $WORKDIR && cd $WORKDIR && ")
            append("setsid ./llbot -c $CONFIG_PATH >> $LOG_PATH 2>&1 < /dev/null &")
        }
        containerBridge.executeCommand(launchCommand, timeoutMs = 5_000L)
        delay(PID_RESOLVE_DELAY_MS)
        pid = resolvePid()
        logManager.log(QBotLogLevel.INFO, TAG, "llbot launched, pid=$pid")

        // 3. Wait for the OneBot reverse-WS listening port.
        if (awaitPortReady(QBotConstants.DEFAULT_WS_PORT, PORT_READY_TIMEOUT_MS)) {
            restartAttempts = 0
            _state.value = QBotState.RUNNING
            logManager.log(QBotLogLevel.INFO, TAG, "LLBot is running")
            startWatchdog()
        } else {
            val tail = containerBridge.readFile(LOG_PATH)?.takeLast(600).orEmpty()
            failAndScheduleRestart("port ${QBotConstants.DEFAULT_WS_PORT} not ready within ${PORT_READY_TIMEOUT_MS}ms; tail=$tail")
        }
    }

    /**
     * Stop LLBot: SIGTERM, wait grace, escalate to SIGKILL, then flip state to STOPPED.
     */
    suspend fun stop() = lifecycleMutex.withLock {
        if (_state.value == QBotState.STOPPED) return@withLock
        watchdogJob?.cancel()
        watchdogJob = null
        _state.value = QBotState.STOPPING
        logManager.log(QBotLogLevel.INFO, TAG, "stopping LLBot")

        val targetPid = pid.takeIf { it > 0 } ?: resolvePid()
        if (targetPid > 0) {
            containerBridge.executeCommand("kill $targetPid 2>/dev/null", timeoutMs = 3_000L)
            val deadline = System.currentTimeMillis() + STOP_GRACE_MS
            while (System.currentTimeMillis() < deadline) {
                if (!containerBridge.isProcessRunning(QBotConstants.LL_BOT_PROCESS_NAME)) break
                delay(300L)
            }
            if (containerBridge.isProcessRunning(QBotConstants.LL_BOT_PROCESS_NAME)) {
                logManager.log(QBotLogLevel.WARN, TAG, "SIGTERM timed out, escalating to SIGKILL")
                containerBridge.executeCommand("kill -9 $targetPid 2>/dev/null", timeoutMs = 3_000L)
            }
        } else {
            // No pid known: best-effort pkill.
            containerBridge.executeCommand("pkill -f '${QBotConstants.LL_BOT_PROCESS_NAME}' 2>/dev/null", timeoutMs = 3_000L)
        }

        pid = -1
        restartAttempts = 0
        _state.value = QBotState.STOPPED
        logOffset = 0L
        logManager.log(QBotLogLevel.INFO, TAG, "LLBot stopped")
    }

    /** Stop then start again. */
    suspend fun restart() {
        stop()
        start()
    }

    // -----------------------------------------------------------------------------------
    // internals
    // -----------------------------------------------------------------------------------

    private fun buildConfigJson(): String {
        val cfg = currentConfig
        val loginType = cfg.loginType.ifBlank { "qrcode" }
        // 已设置 QQ 号则写入 uin；否则留空字符串，由 LLBot 扫码成功后自动回填。
        val uinValue = if (cfg.botQq != 0L) cfg.botQq.toString() else "\"\""
        // 密码为轻量 Base64 编码存储；写配置时原样透传，第一阶段可留空走扫码。
        val passwordValue = cfg.passwordEncrypted.ifBlank { "" }
        return buildString {
            appendLine("{")
            appendLine("  // generated by MiniMe QQ bot manager")
            appendLine("  \"message_post_format\": \"array\",")
            appendLine("  \"report_self_message\": false,")
            appendLine("  \"http\": { \"enabled\": false },")
            appendLine("  \"ws\": [],")
            appendLine("  \"account\": {")
            appendLine("    \"uin\": $uinValue,")
            appendLine("    \"login_type\": \"$loginType\",")
            appendLine("    \"password\": \"$passwordValue\"")
            appendLine("  },")
            appendLine("  \"reverse_ws\": [")
            appendLine("    {")
            appendLine("      \"name\": \"llbot\",")
            appendLine("      \"enabled\": true,")
            appendLine("      \"host\": \"${QBotConstants.WS_HOST}\",")
            appendLine("      \"port\": ${QBotConstants.DEFAULT_WS_PORT},")
            appendLine("      \"path\": \"${QBotConstants.WS_PATH}\",")
            appendLine("      \"use_wss\": false,")
            appendLine("      \"report_self_message\": false,")
            appendLine("      \"token\": \"\"")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
        }
    }

    private suspend fun resolvePid(): Int {
        val result = containerBridge.executeCommand(
            "pgrep -f '$BINARY -c' 2>/dev/null | head -n 1",
            timeoutMs = 3_000L
        )
        return result.output.trim().toIntOrNull() ?: -1
    }

    private suspend fun awaitPortReady(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (containerBridge.checkPort(port)) return true
            delay(PORT_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(QBotConstants.WATCHDOG_INTERVAL_MS)
                if (_state.value != QBotState.RUNNING) continue
                drainLogFile()
                val alive = containerBridge.isProcessRunning(QBotConstants.LL_BOT_PROCESS_NAME) ||
                    containerBridge.checkPort(QBotConstants.DEFAULT_WS_PORT)
                if (!alive) {
                    logManager.log(QBotLogLevel.ERROR, TAG, "LLBot process lost")
                    // 进程异常退出且此前已登录 → 标记掉线，提示用户重新登录。
                    if (qrCodeManager.loginState.value == QBotLoginState.LOGGED_IN) {
                        qrCodeManager.setLoginState(QBotLoginState.OFFLINE)
                    }
                    watchdogJob?.cancel()
                    watchdogJob = null
                    scope.launch { scheduleRestart("process exited unexpectedly") }
                    break
                }
            }
        }
    }

    /** Tail new bytes from the LLBot log and mirror them into [logManager]. */
    private suspend fun drainLogFile() {
        val sizeResult = containerBridge.executeCommand(
            "wc -c < $LOG_PATH 2>/dev/null",
            timeoutMs = 3_000L
        )
        val size = sizeResult.output.trim().toLongOrNull() ?: return
        if (size < logOffset) logOffset = 0L // file rotated/truncated
        if (size == logOffset) return
        val readResult = containerBridge.executeCommand(
            "tail -c ${logOffset + 1} $LOG_PATH 2>/dev/null",
            timeoutMs = 3_000L
        )
        readResult.output.lineSequence()
            .filter { it.isNotBlank() }
            .forEach { line ->
                logManager.log(QBotLogLevel.INFO, "LLBot", line)
                // 解析二维码 / 登录状态；内部已做容错，不会因格式不符崩溃。
                qrCodeManager.parseLogLine(line)
            }
        logOffset = size
    }

    /**
     * Move to ERROR and retry [start] after the configured backoff, up to
     * [QBotConstants.MAX_RESTART_RETRIES]. Runs on [scope] so it does not hold the
     * lifecycle mutex during the backoff delay.
     */
    private suspend fun scheduleRestart(reason: String) {
        if (_state.value == QBotState.STOPPING || _state.value == QBotState.STOPPED) return
        if (restartAttempts >= QBotConstants.MAX_RESTART_RETRIES) {
            _state.value = QBotState.ERROR
            logManager.log(QBotLogLevel.ERROR, TAG, "max restart retries exhausted ($reason)")
            return
        }
        _state.value = QBotState.ERROR
        val backoff = QBotConstants.RESTART_BACKOFF_MS[
            restartAttempts.coerceAtMost(QBotConstants.RESTART_BACKOFF_MS.size - 1)
        ]
        restartAttempts++
        logManager.log(
            QBotLogLevel.WARN, TAG,
            "auto-restart in ${backoff}ms [$restartAttempts/${QBotConstants.MAX_RESTART_RETRIES}]: $reason"
        )
        delay(backoff)
        if (_state.value == QBotState.STOPPING || _state.value == QBotState.STOPPED) return
        start()
    }

    private suspend fun failAndScheduleRestart(reason: String) {
        logManager.log(QBotLogLevel.ERROR, TAG, reason)
        scope.launch { scheduleRestart(reason) }
    }
}
