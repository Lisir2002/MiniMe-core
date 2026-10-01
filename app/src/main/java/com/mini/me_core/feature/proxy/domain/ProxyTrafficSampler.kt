package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.ProxyTrafficRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P1-8：代理流量采样器。
 *
 * 跟随 [ClashProxyManager.state] 的 enabled：代理开启时每 60s 拉一次 mihomo /connections 累计计数，
 * 与上一次基线做差，把「这 60s 的增量 up/down」写入 proxy_traffic 表；关闭时停止采样并重置基线，
 * 避免内核重启后计数归零造成负增量。
 */
@Singleton
class ProxyTrafficSampler @Inject constructor(
    private val manager: ClashProxyManager,
    private val repo: ProxyTrafficRepository,
) {
    private companion object {
        const val TAG = "ProxyTrafficSampler"
        const val SAMPLE_INTERVAL_MS = 60_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var sampleJob: Job? = null
    private var lastUp: Long = -1L
    private var lastDown: Long = -1L

    init {
        // 跟随代理启用态自动启停采样。
        scope.launch {
            manager.state.collectLatest { s ->
                if (s.enabled && manager.isKernelAlive()) {
                    startSampling()
                } else {
                    stopSampling()
                }
            }
        }
    }

    private fun startSampling() {
        if (sampleJob?.isActive == true) return
        // 重置基线：新一次启用先读一次当前计数作为起点（不计入增量）。
        lastUp = -1L
        lastDown = -1L
        sampleJob = scope.launch {
            while (true) {
                delay(SAMPLE_INTERVAL_MS)
                runCatching { sampleOnce() }
                    .onFailure { FileLogger.w(TAG, "采样异常: ${it.message}") }
            }
        }
        FileLogger.i(TAG, "流量采样已启动（每 60s）")
    }

    private fun stopSampling() {
        sampleJob?.cancel()
        sampleJob = null
        lastUp = -1L
        lastDown = -1L
    }

    private suspend fun sampleOnce() {
        val counters = manager.readTrafficCounters() ?: return
        val (up, down) = counters
        if (lastUp < 0 || lastDown < 0) {
            // 首次采样：只记基线，不写库。
            lastUp = up; lastDown = down
            return
        }
        // 内核重启会让计数回零：检测到回退则重置基线，跳过本次（避免负/暴涨）。
        if (up < lastUp || down < lastDown) {
            FileLogger.w(TAG, "检测到计数回退（内核重启？），重置基线")
            lastUp = up; lastDown = down
            return
        }
        val deltaUp = up - lastUp
        val deltaDown = down - lastDown
        lastUp = up; lastDown = down
        repo.insertSample(System.currentTimeMillis(), deltaUp, deltaDown, SAMPLE_INTERVAL_MS)
    }
}
