package com.mini.me_core.core.performance

import android.app.ActivityManager
import android.content.Context
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * F6.2 内存监控：周期性采样 Java 堆使用情况，超过阈值(80%)记录 GC 建议日志。
 *
 * - 仅在 debug 构建下采样并输出历史曲线；release 下 [start] 为空操作（零开销）。
 * - 当前快照通过 [snapshot] 暴露给开发者选项（复用 DevOptionsScreen）。
 * - 不持有任何 Activity Context，仅使用 @ApplicationContext，避免 Context 泄漏。
 */
@Singleton
class MemoryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class MemorySnapshot(
        /** Java 堆已用 MB。 */
        val usedHeapMb: Long,
        /** Java 堆最大可用 MB。 */
        val maxHeapMb: Long,
        /** 系统可用内存 MB。 */
        val availMemMb: Long,
        /** 系统总内存 MB。 */
        val totalMemMb: Long,
        /** 已用堆占最大堆百分比 0..100。 */
        val heapPercent: Int,
        /** 是否处于高内存压力（>=80%）。 */
        val highPressure: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    /** 最近一次采样快照（供 UI 读取）。 */
    @Volatile
    var latest: MemorySnapshot = sample()
        private set

    /** 启动周期采样。幂等；release 下为空操作。 */
    fun start() {
        if (started) return
        started = true
        if (!com.mini.me_core.BuildConfig.DEBUG) return
        scope.launch {
            while (isActive) {
                val s = sample()
                latest = s
                if (s.highPressure) {
                    FileLogger.w(
                        "MemoryMonitor",
                        "内存压力高：heap=${s.usedHeapMb}/${s.maxHeapMb}MB (${s.heapPercent}%)，" +
                            "建议触发 GC 并释放非关键缓存",
                    )
                    // 主动提示一次 GC（非强制，仅建议；真正回收由 VM 决定）
                    System.gc()
                }
                delay(2_000L)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        scope.cancel()
    }

    /** 同步采样一次当前内存快照。 */
    fun sample(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val max = runtime.maxMemory() / (1024 * 1024)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val avail = info.availMem / (1024 * 1024)
        val total = info.totalMem / (1024 * 1024)
        val percent = if (max > 0) max(0, (used * 100 / max)).toInt() else 0
        return MemorySnapshot(
            usedHeapMb = used,
            maxHeapMb = max,
            availMemMb = avail,
            totalMemMb = total,
            heapPercent = percent,
            highPressure = percent >= 80,
        )
    }
}
