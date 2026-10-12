package com.minime.template

import android.os.SystemClock
import android.util.Log

/**
 * 启动与首屏性能埋点
 *
 * 以进程 Application 初始化时刻为零点，记录容器启动关键环节相对耗时，便于在真机上用
 * `adb logcat -s TplStart` 量化冷启动、WebView 初始化、首帧与可交互时间，优化前后可对比。
 *
 * 仅输出 Log，不做任何磁盘/网络操作，不参与业务逻辑；release 包保留这些轻量日志，
 * 因为现场定位启动问题依赖它们，单行输出、常量级开销。
 */
object StartUpTracer {

    private const val TAG = "TplStart"

    private var t0 = 0L
    private var webViewCtorStart = 0L
    private var loadUrlAt = 0L
    private val marks = LinkedHashMap<String, Long>()

    /**
     * 在 Application.onCreate 最前面调用，重置零点。
     */
    fun markProcessStart() {
        t0 = SystemClock.elapsedRealtime()
        webViewCtorStart = 0L
        loadUrlAt = 0L
        marks.clear()
        log("进程起点 Application.onCreate")
    }

    /**
     * 记录一个节点，输出相对进程起点的毫秒数。
     */
    fun mark(stage: String) {
        if (t0 == 0L) t0 = SystemClock.elapsedRealtime()
        val elapsed = SystemClock.elapsedRealtime() - t0
        marks[stage] = elapsed
        log("$stage  进程启动后=${elapsed}ms")
    }

    fun beginWebViewCtor() {
        webViewCtorStart = SystemClock.elapsedRealtime()
        mark("WebView 构造开始")
    }

    fun endWebViewCtor() {
        if (webViewCtorStart > 0L) {
            val cost = SystemClock.elapsedRealtime() - webViewCtorStart
            log("WebView 构造耗时=${cost}ms")
        }
        mark("WebView 构造完成")
    }

    fun markLoadUrl() {
        loadUrlAt = SystemClock.elapsedRealtime()
        mark("loadUrl 已调用")
    }

    /**
     * 首帧真正绘制完成时调用，输出端到端汇总。
     */
    fun markFirstFrame(stage: String) {
        val now = SystemClock.elapsedRealtime()
        val total = now - t0
        val afterLoadUrl = if (loadUrlAt > 0L) now - loadUrlAt else -1L
        log("──── 首帧到达($stage) 进程启动后=${total}ms；loadUrl→首帧=${afterLoadUrl}ms ────")
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }
}
