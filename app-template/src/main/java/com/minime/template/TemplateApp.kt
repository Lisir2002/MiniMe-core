package com.minime.template

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors

/**
 * 模版应用 Application 类
 *
 * 负责全局初始化：崩溃日志捕获，以及把 WebView 的繁重冷启动负载提前到进程启动期在后台线程预热。
 *
 * 启动性能要点：
 * 1. WebView 首次初始化（加载系统 WebView 内核、浏览器/渲染进程、网络栈、Safe Browsing 数据库）非常重，
 *    默认在主线程首次触碰 webkit API 时整块执行。这里在 Application 中通过 startUpWebView
 *    把可在后台完成的启动任务提前并行执行；MainActivity 真正创建 WebView 时只剩少量主线程任务。
 * 2. 预热只跑后台任务（shouldRunUiThreadStartUpTasks=false），不抢占首帧；剩余 UI 线程任务在 Activity
 *    创建 WebView 时分块完成。
 * 3. Safe Browsing 提前在后台预热，避免其初始化（含联网更新）串行卡在首页加载关键路径上。
 */
class TemplateApp : Application() {

    private val webViewStartExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "webview-startup").apply { isDaemon = true }
    }

    override fun onCreate() {
        StartUpTracer.markProcessStart()
        // 初始化崩溃日志捕获（必须在最前面，确保能捕获到后续所有初始化环节的崩溃）
        CrashHandler.init(this)

        warmUpWebView()
        warmUpSafeBrowsing()
    }

    /**
     * 在后台线程预热 WebView。任何异常都静默降级：旧系统/旧 WebView 内核不支持时，
     * MainActivity 仍会按常规路径隐式初始化，功能不受影响。
     */
    private fun warmUpWebView() {
        try {
            StartUpTracer.mark("startUpWebView 发起(后台)")
            val config = WebViewStartUpConfig.Builder(webViewStartExecutor)
                // 仅执行可在后台完成的启动任务，UI 线程任务留给 Activity，避免与首帧竞争。
                .setShouldRunUiThreadStartUpTasks(false)
                .build()
            WebViewCompat.startUpWebView(
                applicationContext,
                config,
                object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                    override fun onResult(result: WebViewStartUpResult) {
                        StartUpTracer.mark("startUpWebView 后台预热完成")
                        shutdownExecutor()
                    }

                    override fun onError(error: WebViewStartupException) {
                        Log.w(TAG, "WebView 后台预热失败，将在 Activity 内常规初始化", error)
                        shutdownExecutor()
                    }
                }
            )
        } catch (t: Throwable) {
            // 该能力依赖较新的 androidx.webkit / 内核；不可用时直接退回隐式初始化。
            Log.w(TAG, "startUpWebView 不可用，跳过后台预热", t)
            shutdownExecutor()
        }
    }

    /**
     * 提前在后台初始化 Safe Browsing。WebView 内核版本较新（约 122+）时该初始化已自动完成，
     * 回调会立即返回；旧版本则借此把可能的联网初始化从首页关键路径移走。
     */
    private fun warmUpSafeBrowsing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        try {
            WebView.startSafeBrowsing(applicationContext) { success ->
                Log.d(TAG, "Safe Browsing 预热结果: $success")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Safe Browsing 预热不可用，跳过", t)
        }
    }

    private fun shutdownExecutor() {
        try {
            webViewStartExecutor.shutdown()
        } catch (_: Exception) {
            // 忽略关闭异常
        }
    }

    companion object {
        private const val TAG = "TemplateApp"
    }
}
