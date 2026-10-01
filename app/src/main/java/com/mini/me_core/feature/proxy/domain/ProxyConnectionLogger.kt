package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.ProxyConnectionEntry
import com.mini.me_core.datalayer.store.ProxyConnectionLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P3-19：代理连接审计日志采集器。
 *
 * 默认关闭。开启后每 10s 调 mihomo `/connections` 取活跃连接快照：
 * 上次在、这次不在的连接视为「已结束」，批量落库（host/ip/port/protocol/上下行/时长）。
 * 过期记录按保留天数清理。性能：10s 一次、批量事务写库。
 */
@Singleton
class ProxyConnectionLogger @Inject constructor(
    private val manager: ClashProxyManager,
    private val repo: ProxyConnectionLogRepository,
) {
    private companion object {
        const val TAG = "ProxyConnLog"
        const val INTERVAL_MS = 10_000L
    }

    /** 采集开关（默认关，隐私优先）。 */
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 保留天数：1/7/30/0(永久)。 */
    @Volatile var retentionDays: Int = 7

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    /** 上次快照：connId -> (host,ip,port,proto,startMs,up,down)。 */
    private var prev = mutableMapOf<String, ConnSnap>()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        if (on) start() else stop()
    }

    private fun start() {
        if (job?.isActive == true) return
        prev.clear()
        job = scope.launch {
            while (true) {
                delay(INTERVAL_MS)
                runCatching { sampleOnce() }.onFailure { FileLogger.w(TAG, "连接采样异常: ${it.message}") }
            }
        }
        FileLogger.i(TAG, "连接审计日志已开启（每 10s）")
    }

    private fun stop() {
        job?.cancel(); job = null
        prev.clear()
    }

    private suspend fun sampleOnce() {
        if (!manager.isKernelAlive()) return
        val resp = manager.controllerRequestPublic("GET", "/connections") ?: return
        val root = Json.parseToJsonElement(resp).jsonObject
        val conns = root["connections"]?.jsonArray ?: return

        val current = mutableMapOf<String, ConnSnap>()
        conns.forEach { c ->
            val o = c.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val meta = o["metadata"]?.jsonObject
            current[id] = ConnSnap(
                host = meta?.get("host")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                ip = meta?.get("destinationIP")?.jsonPrimitive?.contentOrNull,
                port = meta?.get("destinationPort")?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                proto = meta?.get("network")?.jsonPrimitive?.contentOrNull,
                startMs = meta?.get("start")?.jsonPrimitive?.contentOrNull?.let { parseTime(it) } ?: 0L,
                up = o["upload"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                down = o["download"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
            )
        }

        // 上次有、这次没了 → 结束连接，落库
        val closed = prev.keys.filterNot { current.containsKey(it) }.mapNotNull { id ->
            val s = prev[id] ?: return@mapNotNull null
            val now = System.currentTimeMillis()
            val dur = if (s.startMs > 0) now - s.startMs else 0L
            ProxyConnectionEntry(
                id = 0, timestamp = now, host = s.host, ip = s.ip, port = s.port,
                protocol = s.proto, upBytes = s.up, downBytes = s.down,
                durationMs = dur, status = "closed"
            )
        }
        if (closed.isNotEmpty()) repo.insertBatch(closed)
        prev = current

        // 过期清理（每轮顺手做，开销极小）
        if (retentionDays > 0) {
            repo.purgeOlderThan(System.currentTimeMillis() - retentionDays * 24 * 3600_000L)
        }
    }

    /** mihomo start 形如 "2024-01-01T12:00:00.123..."；粗略解析为 epoch ms。解析失败返回 0。 */
    private fun parseTime(s: String): Long = runCatching {
        // 取毫秒部分近似（不要求精确时区，仅用于时长统计）
        java.time.Instant.parse(s).toEpochMilli()
    }.getOrDefault(0L)

    private data class ConnSnap(
        val host: String?, val ip: String?, val port: Long?, val proto: String?,
        val startMs: Long, val up: Long, val down: Long,
    )
}
