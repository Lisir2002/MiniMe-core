package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P2-13：节点健康监控。
 *
 * 代理启用后每 10 分钟对当前 select 分组内所有节点测一次延迟：
 *  - 单次 >5000ms 或失败记一次超时，**连续 2 次**超时标记为不可用（内存态，不持久化）；
 *  - 任一节点恢复即重置失败计数；
 *  - [autoSwitchFastest] 默认关；开启时测完自动切到延迟最低的可用节点。
 * 代理关闭时停止采样并清空状态。健康结果经 [health] 暴露给 UI 置灰不可用节点。
 */
@Singleton
class ProxyNodeHealthMonitor @Inject constructor(
    private val manager: ClashProxyManager,
) {
    private companion object {
        const val TAG = "ProxyNodeHealth"
        const val INTERVAL_MS = 10 * 60_000L
        const val FAIL_LIMIT = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null

    /** nodeName -> 是否健康（false=连续超时不可用）。 */
    private val _health = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val health: StateFlow<Map<String, Boolean>> = _health.asStateFlow()

    /** 自动切换到最快可用节点（默认关，UI 设置开关控制）。 */
    @Volatile
    var autoSwitchFastest: Boolean = false

    private val failCount = HashMap<String, Int>()

    init {
        scope.launch {
            manager.state.collectLatest { s ->
                if (s.enabled && manager.isKernelAlive()) start() else stop()
            }
        }
    }

    private fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            // 启动后稍等内核就绪再首轮
            delay(5_000L)
            while (true) {
                runCatching { monitorOnce() }
                    .onFailure { FileLogger.w(TAG, "健康监控异常: ${it.message}") }
                delay(INTERVAL_MS)
            }
        }
        FileLogger.i(TAG, "节点健康监控已启动（每 10 分钟）")
    }

    private fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        failCount.clear()
        _health.value = emptyMap()
    }

    private suspend fun monitorOnce() {
        val snap = manager.fetchProxiesSnapshot() ?: return
        // 取第一个 select 型分组作为当前活跃分组（其 all 为可选节点名列表）。
        val group = snap.groups.firstOrNull { it.type.equals("select", ignoreCase = true) }
            ?: return
        if (group.all.isEmpty()) return

        val results = HashMap<String, Long?>() // nodeName -> latency(null=失败)
        for (member in group.all) {
            val info = ProxyNodeInfo(name = member, type = "", server = "", port = 0)
            val delay = runCatching { manager.testNodeLatency(info) }.getOrNull()
            results[member] = delay
        }

        // 更新失败计数与健康表
        val healthMap = HashMap<String, Boolean>()
        var fastestName: String? = null
        var fastestDelay = Long.MAX_VALUE
        results.forEach { (name, d) ->
            val ok = d != null && d > 0
            if (ok) {
                failCount[name] = 0
                healthMap[name] = true
                if (d!! < fastestDelay) { fastestDelay = d; fastestName = name }
            } else {
                val n = (failCount[name] ?: 0) + 1
                failCount[name] = n
                healthMap[name] = n < FAIL_LIMIT // 未达连续2次仍视为可用
            }
        }
        _health.value = healthMap

        // 可选：自动切到最快可用节点
        if (autoSwitchFastest && fastestName != null && group.now != fastestName) {
            manager.selectProxyNode(group.name, fastestName)
            FileLogger.i(TAG, "自动切换到最快节点: $fastestName (${fastestDelay}ms)")
        }
    }
}
