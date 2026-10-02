package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 代理出口健康检测器（阶段3新增）。
 *
 * 职责：主动探测整个代理链路（mihomo → 节点 → 外网）是否可用，而非被动等待模型请求失败。
 *
 * 与 [ProxyNodeHealthMonitor] 的区别：
 * - [ProxyNodeHealthMonitor]：监控 mihomo 内部各节点延迟，用于节点切换 UI；10 分钟一次。
 * - [ProxyHealthMonitor]：监控整个代理出口是否能通外网（gstatic generate_204），用于 OkHttp 自动降级；30 秒一次。
 *
 * 工作方式：
 * 1. 后台协程每 [CHECK_INTERVAL_MS] 发一次 HTTP HEAD 到 http://www.gstatic.com/generate_204，
 *    超时 [TIMEOUT_MS]；204/200 视为健康，超时/IOException 视为不健康。
 * 2. 健康结果写入 [proxyHealthy] StateFlow，供 UI 观察 + [ProxyRouteHolder] 降级决策。
 * 3. 状态翻转（健康→不健康 / 不健康→健康）时记录日志。
 * 4. [forceCheck] 供手动触发（如模型请求失败时立即诊断）。
 *
 * 注意：检测请求本身走代理（通过 [proxyClient]），直接打到 mihomo mixed-port 出口，
 * 这样测的是「代理链路是否通」，而不是直连是否通。
 */
@Singleton
class ProxyHealthMonitor @Inject constructor(
    private val routeHolder: ProxyRouteHolder,
) {
    private companion object {
        const val TAG = "ProxyHealthMonitor"
        /** 检测间隔：30 秒。 */
        const val CHECK_INTERVAL_MS = 30_000L
        /** 单次检测超时：5 秒。 */
        const val TIMEOUT_MS = 5_000L
        /** 探测目标：Google 的 204 端点，轻量、可靠。
         *  使用 https：经 HTTP 代理出口时走 CONNECT 隧道完成 TLS 握手，应用层无明文，
         *  与默认禁止 cleartext 的网络安全策略保持一致（避免被当作明文 http 探测而拦截）。 */
        const val PROBE_URL = "https://www.gstatic.com/generate_204"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    /** 代理是否健康（true=代理出口可用，false=代理不通/未启用）。 */
    private val _proxyHealthy = MutableStateFlow(false)
    val proxyHealthy: StateFlow<Boolean> = _proxyHealthy.asStateFlow()

    /** 上一次状态，用于检测翻转时打日志。 */
    @Volatile
    private var lastState: Boolean? = null

    /** 专用检测 client：短超时，走本机代理。 */
    private val probeClient: OkHttpClient by lazy {
        // 直接构造一个短超时 client，通过 mihomo mixed-port 出口探测。
        val proxyAddr = routeHolder.proxyAddress
        val host = proxyAddr.substringBeforeLast(':')
        val port = proxyAddr.substringAfterLast(':').toIntOrNull() ?: ClashProxyManager.MIXED_PORT
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
            .build()
    }

    init {
        // 启动后台轮询。
        startMonitoring()
        // 阶段4：健康状态变化时反向写入 ProxyRouteHolder，触发自动降级/恢复。
        scope.launch {
            proxyHealthy.collect { healthy ->
                routeHolder.setHealthDegraded(!healthy)
            }
        }
    }

    /** 启动后台健康检测协程。 */
    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            // 启动后稍等 3 秒再首轮，避免和 App 启动抢占资源。
            delay(3_000L)
            while (true) {
                doCheck()
                delay(CHECK_INTERVAL_MS)
            }
        }
        FileLogger.i(TAG, "代理健康监控已启动，间隔 ${CHECK_INTERVAL_MS / 1000}s")
    }

    /**
     * 手动触发一次健康检测（供模型请求失败时立即诊断）。
     * 不阻塞调用方，检测结果异步写入 [proxyHealthy]。
     */
    fun forceCheck() {
        scope.launch {
            FileLogger.i(TAG, "手动触发代理健康检测")
            doCheck()
        }
    }

    /**
     * 执行一次健康检测：走代理请求 gstatic generate_204。
     * 结果写入 [_proxyHealthy]，状态翻转时打日志。
     */
    private suspend fun doCheck() {
        // 代理未启用时直接标记为不健康（或视为「不需要代理」？）。
        // 语义：proxyHealthy = 代理链路是否通。代理没开 = 不通。
        if (!routeHolder.enabled) {
            updateState(false, reason = "代理未启用")
            return
        }

        try {
            val request = Request.Builder().url(PROBE_URL).head().build()
            probeClient.newCall(request).execute().use { response ->
                // 204 或 200 都算通（有些代理会返回 200）。
                val healthy = response.isSuccessful || response.code == 204
                updateState(healthy, reason = "HTTP ${response.code}")
            }
        } catch (e: IOException) {
            updateState(false, reason = e.message ?: "IOException")
        } catch (e: Exception) {
            updateState(false, reason = e.message ?: "Unknown error")
        }
    }

    /** 更新健康状态，状态翻转时记录日志。 */
    private fun updateState(healthy: Boolean, reason: String) {
        _proxyHealthy.value = healthy
        val prev = lastState
        if (prev != healthy) {
            if (healthy) {
                FileLogger.i(TAG, "代理恢复健康 ✅ ($reason)")
            } else {
                FileLogger.w(TAG, "代理不可用 ❌ ($reason)")
            }
            lastState = healthy
        }
    }
}
