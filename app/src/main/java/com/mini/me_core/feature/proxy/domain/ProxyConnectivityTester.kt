package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 诊断交通灯：绿=四项全通；黄=部分通；红=进程/控制面不通。 */
enum class TrafficLight { GREEN, YELLOW, RED }

/**
 * P1-9：一次代理连接诊断的结果（四项检查）。
 *
 *  - [processAlive]：mihomo 子进程是否存活；
 *  - [controllerReachable]：127.0.0.1:9090 控制面 GET /configs 是否可达；
 *  - [outboundOk]：经内核真实出口访问 generate_204 是否通（含 [outboundLatencyMs]）；
 *  - [dnsOk]：经内核解析一个域名是否通（用第二个域名延迟测试验证 DNS 链路）。
 */
data class ProxyDiagnosticResult(
    val processAlive: Boolean = false,
    val controllerReachable: Boolean = false,
    val outboundOk: Boolean = false,
    val outboundLatencyMs: Long? = null,
    val dnsOk: Boolean = false,
    val checkedAt: Long = 0L,
) {
    val light: TrafficLight
        get() = when {
            !processAlive || !controllerReachable -> TrafficLight.RED
            outboundOk && dnsOk -> TrafficLight.GREEN
            else -> TrafficLight.YELLOW
        }
}

/**
 * P1-9：代理连接诊断器。启用代理后自动跑一次，UI 用交通灯展示，可手动重测。
 * 纯读、无副作用；任何单项失败不影响其他项。
 */
@Singleton
class ProxyConnectivityTester @Inject constructor(
    private val manager: ClashProxyManager,
) {
    private companion object {
        const val TAG = "ProxyDiag"
    }

    private val _result = MutableStateFlow<ProxyDiagnosticResult?>(null)
    val result: StateFlow<ProxyDiagnosticResult?> = _result.asStateFlow()

    /** 跑四项诊断并更新 [result]。 */
    suspend fun runDiagnostics() = withContext(Dispatchers.IO) {
        val processAlive = manager.isKernelAlive()
        val controllerReachable = manager.controllerRequest("GET", "/configs") != null
        // 出口测试：generate_204
        val outboundLatency = if (controllerReachable) manager.testOutboundLatency() else null
        val outboundOk = outboundLatency != null
        // DNS 测试：换一个域名再测一次延迟（能解析并连通即 DNS 正常；generate_204 走 IP 可能不测 DNS）
        val dnsOk = if (controllerReachable) {
            runCatching {
                val resp = manager.controllerRequest(
                    "GET",
                    "/proxies/GLOBAL/delay?url=https://www.google.com/generate_204&timeout=5000"
                )
                resp != null
            }.getOrDefault(false)
        } else false

        val r = ProxyDiagnosticResult(
            processAlive = processAlive,
            controllerReachable = controllerReachable,
            outboundOk = outboundOk,
            outboundLatencyMs = outboundLatency,
            dnsOk = dnsOk,
            checkedAt = System.currentTimeMillis(),
        )
        _result.value = r
        FileLogger.i(
            TAG,
            "诊断完成: light=${r.light} alive=$processAlive ctrl=$controllerReachable " +
                "outbound=${outboundLatency}ms dns=$dnsOk"
        )
    }
}
