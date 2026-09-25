package com.mini.me_core.feature.terminal.domain

import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** 一次资源采样。 */
data class ResourceSnapshot(
    val cpuPercent: Float,
    val memUsedMb: Long,
    val memTotalMb: Long,
    val storageUsedPercent: Float,
    val processCount: Int,
)

/**
 * F3.4 容器资源采集器：通过 cat /proc/meminfo、df、ps 在容器内采集，2 秒一次。
 */
@Singleton
class ResourceMonitor @Inject constructor(
    private val engine: LinuxContainerEngine,
) {
    fun sampleFlow(intervalMs: Long = 2000L): Flow<ResourceSnapshot> = flow {
        var prevIdle: Long = 0L
        var prevTotal: Long = 0L
        while (true) {
            val cpu = runCatching { readCpuPercent(prevIdle, prevTotal) }
            val cpuData = cpu.getOrNull()
            if (cpuData != null) {
                prevIdle = cpuData.idle
                prevTotal = cpuData.total
            }
            val cpuPct = cpuData?.percent ?: 0f
            val mem = runCatching { readMem() }.getOrDefault(0L to 0L)
            val disk = runCatching { readDiskPercent() }.getOrDefault(0f)
            val procs = runCatching { readProcessCount() }.getOrDefault(0)
            emit(ResourceSnapshot(cpuPct, mem.first, mem.second, disk, procs))
            delay(intervalMs)
        }
    }

    private data class CpuSample(val idle: Long, val total: Long, val percent: Float)

    private suspend fun readCpuPercent(prevIdle: Long, prevTotal: Long): CpuSample? {
        val out = runCatching {
            engine.runCommandSync("cat /proc/stat | head -1", null, 5000L)
        }.getOrDefault("")
        val parts = out.trim().split(Regex("\\s+"))
        if (parts.size < 5 || parts[0] != "cpu") return null
        val vals = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (vals.size < 4) return null
        val idle = vals[3]
        val total = vals.sum()
        val idleDelta = idle - prevIdle
        val totalDelta = total - prevTotal
        val pct = if (totalDelta > 0) 100f * (1f - idleDelta.toFloat() / totalDelta) else 0f
        return CpuSample(idle, total, pct.coerceIn(0f, 100f))
    }

    private suspend fun readMem(): Pair<Long, Long> {
        val out = runCatching {
            engine.runCommandSync("cat /proc/meminfo", null, 5000L)
        }.getOrDefault("")
        var total = 0L
        var available = 0L
        out.lines().forEach { line ->
            when {
                line.startsWith("MemTotal:") -> total = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
                line.startsWith("MemAvailable:") -> available = line.split(Regex("\\s+"))[1].toLongOrNull() ?: 0L
            }
        }
        val usedKb = (total - available).coerceAtLeast(0)
        return (usedKb / 1024) to (total / 1024)
    }

    private suspend fun readDiskPercent(): Float {
        val out = runCatching {
            engine.runCommandSync("df -P / 2>/dev/null | tail -1", null, 5000L)
        }.getOrDefault("")
        val pct = out.split(Regex("\\s+")).getOrNull(4)?.replace("%", "")?.toFloatOrNull() ?: 0f
        return pct.coerceIn(0f, 100f)
    }

    private suspend fun readProcessCount(): Int {
        val out = runCatching {
            engine.runCommandSync("ps -e 2>/dev/null | wc -l", null, 5000L)
        }.getOrDefault("")
        return out.trim().toIntOrNull() ?: 0
    }
}
