package com.mini.me_core.core.network

import com.mini.me_core.core.util.FileLogger
import java.util.concurrent.ConcurrentHashMap

/**
 * 端点健康注册表（进程内单例，纯内存态）：「内置优化网络线路」的状态底座。
 *
 * 背景（「好网络仍报网络波动」优化）：模型端点（baseUrl）的可用性会随时间波动
 * （中转站限流、官方域名区域性抽风、连接半死等）。没有健康状态时，每次请求都
 * 无脑打用户配置的首选端点，失败后只能在同一条连接上反复重试，用户对着
 * 「正在重试」气泡干等。本注册表按端点记录：
 * - 连续失败数（达到阈值即熔断，期间不再作为首选）；
 * - EWMA 延迟（同分候选按延迟排序，快的优先）；
 * - 熔断截止时间（到期自动半开，允许一次探测）。
 *
 * 线程安全：模型请求来自不同会话/工具的协程，全部走 [ConcurrentHashMap] +
 * 同步块，无锁外副作用。进程重启即重置——健康状态是易失的运行时数据，
 * 持久化反而会把「几分钟前的抽风」变成永久的错误决策。
 */
class EndpointHealthRegistry {

    private class Entry {
        /** 连续失败次数（成功即清零）。 */
        @Volatile var consecutiveFailures: Int = 0

        /** 熔断开启截止时间（ms epoch）；0 表示未熔断。 */
        @Volatile var circuitOpenUntilMs: Long = 0L

        /** EWMA 延迟（ms）；0 表示无样本。 */
        @Volatile var ewmaLatencyMs: Long = 0L

        /** 最近一次成功/失败时间（ms epoch），仅用于日志。 */
        @Volatile var lastEventMs: Long = 0L
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * 记录一次成功：清零连续失败、解除熔断、更新 EWMA 延迟。
     *
     * @param latencyMs 本次成功请求的观测延迟（用于排序参考）
     */
    fun recordSuccess(key: String, latencyMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val entry = entries.getOrPut(key) { Entry() }
        synchronized(entry) {
            val wasOpen = entry.circuitOpenUntilMs > 0L
            entry.consecutiveFailures = 0
            entry.circuitOpenUntilMs = 0L
            entry.ewmaLatencyMs = if (entry.ewmaLatencyMs <= 0L) {
                latencyMs
            } else {
                (entry.ewmaLatencyMs * 3 + latencyMs) / 4
            }
            entry.lastEventMs = nowMs
            if (wasOpen) {
                FileLogger.i(TAG, "端点恢复健康（半开探测成功）: $key ewma=${entry.ewmaLatencyMs}ms")
            }
        }
    }

    /**
     * 记录一次失败：连续失败数 +1，达到 [FAILURE_THRESHOLD] 即熔断 [CIRCUIT_OPEN_MS]。
     * 熔断期间该端点不再作为首选，避免把请求持续打向已知不可用的线路。
     */
    fun recordFailure(key: String, nowMs: Long = System.currentTimeMillis()) {
        val entry = entries.getOrPut(key) { Entry() }
        synchronized(entry) {
            entry.consecutiveFailures++
            entry.lastEventMs = nowMs
            if (entry.consecutiveFailures >= FAILURE_THRESHOLD && entry.circuitOpenUntilMs <= nowMs) {
                entry.circuitOpenUntilMs = nowMs + CIRCUIT_OPEN_MS
                FileLogger.w(TAG, "端点熔断开启（连续 ${entry.consecutiveFailures} 次失败，${CIRCUIT_OPEN_MS}ms 内不再首选）: $key")
            }
        }
    }

    /** 端点当前是否处于熔断期。 */
    fun isCircuitOpen(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val entry = entries[key] ?: return false
        return entry.circuitOpenUntilMs > nowMs
    }

    /**
     * 按健康度排序候选端点：健康的在前，同分按 EWMA 延迟快的在前。
     *
     * 排序分 = 基础分 - 熔断罚分 - 连续失败罚分 - 延迟罚分（见 [healthScore]）。
     * 全部未知（无样本）时保持调用方给的原始顺序——用户配置的首选永远第一，
     * 不会因为「没数据」就被重排。
     */
    fun rankEndpoints(candidates: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> {
        if (candidates.isEmpty()) return emptyList()
        // 无任何样本 → 原序返回（渐进式：不改变存量选路行为）
        if (candidates.none { entries.containsKey(it) }) return candidates
        return candidates.sortedByDescending { healthScore(it, nowMs) }
    }

    /**
     * 端点健康分（越高越优先）。熔断中 = 0；否则 1000 起评，
     * 每次连续失败 -100，EWMA 延迟每 100ms -1（封顶 -300）。
     */
    fun healthScore(key: String, nowMs: Long = System.currentTimeMillis()): Int {
        val entry = entries[key] ?: return DEFAULT_SCORE
        if (entry.circuitOpenUntilMs > nowMs) return 0
        var score = DEFAULT_SCORE
        score -= entry.consecutiveFailures * FAILURE_PENALTY
        if (entry.ewmaLatencyMs > 0L) {
            score -= minOf((entry.ewmaLatencyMs / 100L).toInt(), LATENCY_PENALTY_CAP)
        }
        return score
    }

    companion object {
        private const val TAG = "EndpointHealth"

        /** 连续失败达到此次数即熔断。 */
        const val FAILURE_THRESHOLD = 3

        /** 熔断时长：到期自动半开，允许一次探测请求。 */
        const val CIRCUIT_OPEN_MS = 60_000L

        /** 无样本端点的基础分（与健康端点同分，靠稳定排序保原序）。 */
        const val DEFAULT_SCORE = 1000

        private const val FAILURE_PENALTY = 100
        private const val LATENCY_PENALTY_CAP = 300
    }
}
