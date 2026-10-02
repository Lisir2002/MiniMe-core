package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * 代理会话生命周期管理器：实现「随用随开，用完随关」。
 *
 * 核心机制：
 *  - 活动追踪：任何代理流量 / 工具调用 / 节点切换都视为活动，重置空闲计时器。
 *  - 空闲超时：默认 5 分钟无活动自动关闭代理（可配置 1-30 分钟）。
 *  - 会话级：模型调用 use 开启会话，release 标记结束并启动倒计时；
 *    会话结束（Agent 会话销毁）时自动关闭。
 *  - 预警通知：关闭前 30 秒通过 [autoCloseIn] 暴露剩余秒数，模型可调用 keepalive 续命。
 *
 * 与 [ClashProxyManager] 的关系：本管理器只负责「什么时候该关」，
 * 实际开关动作委托给 manager.on() / manager.off()。
 */
@Singleton
class ProxySessionManager @Inject constructor(
    private val manager: ClashProxyManager,
    private val repository: ProxySettingsRepository,
) {
    private companion object {
        const val TAG = "ProxySessionManager"
        const val DEFAULT_IDLE_TIMEOUT_SEC = 5 * 60L  // 5 分钟
        const val MIN_IDLE_TIMEOUT_SEC = 60L          // 1 分钟
        const val MAX_IDLE_TIMEOUT_SEC = 30 * 60L     // 30 分钟
        const val TICK_INTERVAL_SEC = 1L              // 每秒 tick
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    /** 距离自动关闭的剩余秒数；-1 表示无自动关闭计划（手动模式或未开启）。 */
    private val _autoCloseIn = MutableStateFlow(-1L)
    val autoCloseIn: StateFlow<Long> = _autoCloseIn.asStateFlow()

    private var idleTimeoutSec = DEFAULT_IDLE_TIMEOUT_SEC
    private var lastActivityMs = 0L
    private var tickerJob: Job? = null
    private var autoCloseEnabled = true  // 默认开启自动关闭；手动 on/off 时暂停

    init {
        // 读取用户配置的超时时间
        scope.launch {
            repository.idleTimeoutSecFlow.collect { configured ->
                if (configured in MIN_IDLE_TIMEOUT_SEC..MAX_IDLE_TIMEOUT_SEC) {
                    idleTimeoutSec = configured
                    FileLogger.i(TAG, "空闲超时配置: ${idleTimeoutSec}s")
                }
            }
        }
    }

    /** 模型调用 use：开启会话，重置计时器。 */
    suspend fun startSession(profileId: String? = null, inlineYaml: String? = null): String {
        val result = if (!manager.state.value.enabled) {
            manager.on(profileId, inlineYaml)
        } else "ok"
        if (result == "ok") {
            _sessionActive.value = true
            autoCloseEnabled = true
            markActivity()
            startTicker()
            FileLogger.i(TAG, "会话开启 (profile=$profileId, idleTimeout=${idleTimeoutSec}s)")
        }
        return result
    }

    /** 模型调用 release：标记本次使用结束，立即启动空闲倒计时。 */
    fun endSession() {
        _sessionActive.value = false
        markActivity()  // 从 release 时刻开始计时
        FileLogger.i(TAG, "会话结束，启动 ${idleTimeoutSec}s 空闲倒计时")
    }

    /** 模型调用 keepalive：重置空闲计时器，续命。 */
    fun keepAlive() {
        markActivity()
        FileLogger.i(TAG, "keepalive: 重置空闲计时器")
    }

    /** 记录一次活动（流量 / 工具调用 / 节点切换），重置空闲计时器。 */
    fun markActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    /** 手动开启时调用：暂停自动关闭（用户明确要一直开着）。 */
    fun onManualOn() {
        autoCloseEnabled = false
        _autoCloseIn.value = -1
        stopTicker()
        FileLogger.i(TAG, "手动开启，暂停自动关闭")
    }

    /** 手动关闭时调用：清理状态。 */
    fun onManualOff() {
        _sessionActive.value = false
        autoCloseEnabled = true
        _autoCloseIn.value = -1
        stopTicker()
        FileLogger.i(TAG, "手动关闭，清理会话状态")
    }

    /** 代理是否由会话管理（自动关闭模式）。 */
    fun isAutoCloseEnabled(): Boolean = autoCloseEnabled && manager.state.value.enabled

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_INTERVAL_SEC * 1000)
                val elapsedSec = (System.currentTimeMillis() - lastActivityMs) / 1000
                val remaining = idleTimeoutSec - elapsedSec
                if (remaining <= 0) {
                    if (autoCloseEnabled && manager.state.value.enabled) {
                        FileLogger.i(TAG, "空闲超时 ${idleTimeoutSec}s，自动关闭代理")
                        manager.off()
                        _sessionActive.value = false
                        _autoCloseIn.value = -1
                    }
                    stopTicker()
                    break
                }
                _autoCloseIn.value = remaining
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** 获取当前空闲超时配置（秒）。 */
    fun getIdleTimeoutSec(): Long = idleTimeoutSec

    /** 设置空闲超时（秒），范围 1-30 分钟。 */
    suspend fun setIdleTimeoutSec(seconds: Long) {
        val clamped = max(MIN_IDLE_TIMEOUT_SEC, min(MAX_IDLE_TIMEOUT_SEC, seconds))
        idleTimeoutSec = clamped
        repository.updateIdleTimeout(clamped)
        FileLogger.i(TAG, "空闲超时设置为 ${clamped}s")
    }
}
