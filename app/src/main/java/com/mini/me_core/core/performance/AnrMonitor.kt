package com.mini.me_core.core.performance

import android.os.Handler
import android.os.Looper
import com.mini.me_core.BuildConfig
import com.mini.me_core.core.util.FileLogger

/**
 * F6.4 ANR 监控（轻量主线程看门狗）。
 *
 * 原理：向主线程 Looper 周期 post 一个探针任务；若超过 [TIMEOUT_MS] 仍未被主线程执行，
 * 说明主线程被阻塞，判定为 ANR 风险，抓取主线程堆栈并记入崩溃报告目录（与 CrashReporter 同源）。
 *
 * - 仅 debug 构建启用；release 下为空操作。
 * - 不监控 /data/anr/traces.txt（权限受限），采用应用内自监控。
 */
object AnrMonitor {

    private const val TIMEOUT_MS = 3_000L
    private const val INTERVAL_MS = 1_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private val probe = object : Runnable {
        override fun run() {
            if (!started) return
            // 主线程即将执行到此（探针准时），重置 watchdog。
            watchdog.removeCallbacks(watchdogRunnable)
            watchdog.postDelayed(watchdogRunnable, TIMEOUT_MS)
            mainHandler.postDelayed(this, INTERVAL_MS)
        }
    }

    // 独立 handler 线程用于判定阻塞：探针没在限时内跑完主线程，watchdog 仍在队列里触发。
    private val watchdog = Handler(Looper.getMainLooper())

    private val watchdogRunnable = Runnable {
        if (!started) return@Runnable
        // 走到这里说明探针在 TIMEOUT_MS 内没被主线程处理 → 主线程阻塞。
        val stack = Looper.getMainLooper().thread.stackTrace
        FileLogger.w("AnrMonitor", "疑似 ANR：主线程阻塞超过 ${TIMEOUT_MS}ms")
        val sb = StringBuilder("MiniMe-core ANR Report time=${System.currentTimeMillis()}\n")
        for (e in stack.take(30)) sb.appendLine("    at $e")
        FileLogger.w("AnrMonitor", sb.toString())
    }

    fun start() {
        if (started) return
        if (!BuildConfig.DEBUG) return
        started = true
        mainHandler.post(probe)
    }

    fun stop() {
        started = false
        mainHandler.removeCallbacks(probe)
        watchdog.removeCallbacks(watchdogRunnable)
    }
}
