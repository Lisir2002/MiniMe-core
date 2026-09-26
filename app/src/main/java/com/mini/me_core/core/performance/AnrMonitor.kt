package com.mini.me_core.core.performance

import android.os.Handler
import android.os.Looper
import com.mini.me_core.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ANR 监控（仅 debug 构建采集）。
 *
 * 机制：主线程 Looper 心跳检测。
 * - 每隔 1s 向主线程 post 一个心跳 Runnable。
 * - 后台线程检测心跳是否按时执行，若主线程阻塞超过 [ANR_THRESHOLD_MS]，
 *   抓取主线程完整堆栈并记录为一条 ANR 事件。
 * - 记录保存在内存中（[records]），供开发者选项展示；不写磁盘。
 *
 * release 构建下为空操作。
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
        if (started || !BuildConfig.DEBUG) return
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
    }

    fun snapshot(): List<AnrRecord> = records.toList()

    fun clear() = records.clear()
}
