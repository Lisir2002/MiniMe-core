package com.mini.me_core.core.performance

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ANR 监控（**全构建生效**，含 release）。
 *
 * 机制：主线程 Looper 心跳检测。
 * - 每隔 1s 向主线程 post 一个心跳 Runnable。
 * - 后台线程检测心跳是否按时执行，若主线程阻塞超过 [ANR_THRESHOLD_MS]，
 *   抓取主线程完整堆栈并记录为一条 ANR 事件。
 * - 记录既保存在内存中（[records]，供开发者选项展示），也写一条结构化日志进入可导出链路。
 *
 * 为什么 release 也要开：ANR 达到 5s 后系统要么弹「无响应」对话框、要么直接杀进程，
 * 属于「闪退却查不到原因」的典型路径之一。旧实现用 `!BuildConfig.DEBUG` 直接 return，
 * 等于正式版完全没有可观测性。心跳本身每秒只有一次 `Handler.post`，开销可忽略。
 */
object AnrMonitor {

    private const val ANR_THRESHOLD_MS = 5_000L
    private const val HEARTBEAT_INTERVAL_MS = 1_000L
    private const val MAX_RECORDS = 20

    data class AnrRecord(
        val timestamp: Long,
        val blockDurationMs: Long,
        val mainThreadStack: String,
    ) {
        fun formattedTime(): String =
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }

    private val records = CopyOnWriteArrayList<AnrRecord>()

    @Volatile
    private var started = false

    @Volatile
    private var heartbeatReceived = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private var watcherThread: Thread? = null

    private val heartbeat = Runnable { heartbeatReceived = true }

    fun start() {
        // release 同样启用：ANR 是「闪退无记录」的主要路径之一，正式版必须有可观测性。
        if (started) return
        started = true
        heartbeatReceived = true
        watcherThread = Thread({
            while (started) {
                heartbeatReceived = false
                mainHandler.post(heartbeat)
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (!heartbeatReceived) {
                    val blockStart = System.currentTimeMillis()
                    try {
                        Thread.sleep(HEARTBEAT_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                    if (!heartbeatReceived) {
                        val blockDuration = System.currentTimeMillis() - blockStart + HEARTBEAT_INTERVAL_MS
                        val stack = Looper.getMainLooper().thread.stackTrace
                            .joinToString("\n") { "    at $it" }
                        addRecord(AnrRecord(
                            timestamp = System.currentTimeMillis(),
                            blockDurationMs = blockDuration,
                            mainThreadStack = stack,
                        ))
                    }
                }
            }
        }, "anr-watcher").apply { isDaemon = true; start() }
    }

    fun stop() {
        started = false
        watcherThread?.interrupt()
        watcherThread = null
    }

    private fun addRecord(record: AnrRecord) {
        records.add(0, record)
        while (records.size > MAX_RECORDS) records.removeAt(records.size - 1)
        // 审计 K：ANR 不能只留内存——进程被杀即永久丢失。落一条结构化日志进入可导出链路。
        // 级别用 ERROR（主线程阻塞 5s 已属严重事件），便于在日志中一眼识别。
        com.mini.me_core.core.util.FileLogger.e(
            "DIAG-ANR",
            "[DIAG-ANR] 主线程阻塞 ${record.blockDurationMs}ms @ ${record.formattedTime()}\n${record.mainThreadStack}",
        )
    }

    fun snapshot(): List<AnrRecord> = records.toList()

    fun clear() = records.clear()
}
