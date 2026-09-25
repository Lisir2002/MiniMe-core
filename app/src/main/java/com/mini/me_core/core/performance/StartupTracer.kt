package com.mini.me_core.core.performance

import com.mini.me_core.BuildConfig

/**
 * F6.1 冷启动耗时追踪器（仅 debug 构建输出日志）。
 *
 * 在 Application.attachBaseContext / onCreate、Hilt 注入、数据库初始化、主题加载、
 * MainActivity.onCreate、首帧绘制等关键节点打点（System.nanoTime），汇总各阶段
 * 耗时与总耗时，供性能优化与开发者选项可视化。
 *
 * 设计要点：
 *  - 纯 object 单例，不依赖 Hilt（T0 必须在任何 DI 之前由 attachBaseContext 打点）。
 *  - 时间基准 T0 = attachBaseContext 开始时刻；所有阶段以「距 T0 的毫秒」记录。
 *  - release 构建下 [mark] 为空操作，无额外开销；[snapshot] 返回空列表。
 */
object StartupTracer {

    /** 单个启动阶段记录。 */
    data class Stage(
        val key: String,
        val label: String,
        /** 距 T0 的累计毫秒。 */
        val elapsedMs: Long,
        /** 相对上一阶段的增量毫秒（阶段耗时）。 */
        val deltaMs: Long,
    )

    private val enabled: Boolean get() = BuildConfig.DEBUG

    @Volatile
    private var t0Ns: Long = 0L

    private val stages = linkedMapOf<String, Stage>()

    /** 在 attachBaseContext 最早期调用，锚定 T0。 */
    fun onAttachBaseContext() {
        t0Ns = System.nanoTime()
        stages.clear()
        mark("attach_base", "attachBaseContext")
    }

    /**
     * 打一个阶段点。key 用于去重（重复打点以后一次为准），label 用于展示。
     * release 构建下为空操作。
     */
    fun mark(key: String, label: String) {
        if (!enabled || t0Ns == 0L) return
        val now = System.nanoTime()
        val elapsedMs = (now - t0Ns) / 1_000_000L
        val prevElapsed = stages.values.lastOrNull()?.elapsedMs ?: 0L
        stages[key] = Stage(key, label, elapsedMs, elapsedMs - prevElapsed)
        if (enabled) {
            com.mini.me_core.core.util.FileLogger.d(
                "StartupTracer",
                "[启动] $label 累计=${elapsedMs}ms 阶段=${elapsedMs - prevElapsed}ms",
            )
        }
    }

    /** 启动完成后输出汇总日志（各阶段占比 + 总耗时）。仅 debug。 */
    fun report(reason: String) {
        if (!enabled || stages.isEmpty()) return
        val total = stages.values.lastOrNull()?.elapsedMs ?: 0L
        val sb = StringBuilder("[启动汇总] reason=$reason total=${total}ms\n")
        stages.values.forEach { s ->
            val pct = if (total > 0) s.deltaMs * 100 / total else 0
            sb.append("  - %-22s 累计=%5dms 阶段=%5dms 占比=%3d%%\n"
                .format(s.label, s.elapsedMs, s.deltaMs, pct))
        }
        com.mini.me_core.core.util.FileLogger.i("StartupTracer", sb.toString())
    }

    /** 供开发者选项可视化：返回按时间顺序排列的阶段快照。release 下为空。 */
    fun snapshot(): List<Stage> = if (enabled) stages.values.toList() else emptyList()

    /** 冷启动总耗时（距 T0，首帧后调用）；release 下返回 -1。 */
    fun totalMs(): Long = if (enabled && t0Ns != 0L) (System.nanoTime() - t0Ns) / 1_000_000L else -1L
}
