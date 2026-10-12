package com.minime.template

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import com.minime.template.bridge.AppBridge
import com.minime.template.bridge.EventBridge
import java.util.HashMap

/**
 * 模版应用主 Activity（本地 H5 容器）
 *
 * 零白屏启动衔接：
 * 1. 冷启动第一帧由主题 windowBackground（@drawable/splash_bg，构建期可注入用户启动图）绘制；
 * 2. setContentView 仅挂载一个背景透明的 WebView，windowBackground 在 WebView 首帧绘制前自然透出；
 * 3. 启用启动动画时先加载与 windowBackground 首帧像素一致的 splash.html（同图、cover、同背景色），
 *    其播放过渡并预载主页后再 location.replace 到入口页；进入入口页前把 WebView 背景设为启动背景色，
 *    保证任何切换间隙都不露白底；
 * 4. 原生看门狗兜底：splash.html 异常未跳转时强制进入入口页。
 *
 * 其他：WebViewAssetLoader 虚拟 HTTPS 域加载 assets；file:// 全禁用、危险接口移除、安全浏览、
 * 默认禁明文 HTTP、CSP 注入、渲染进程崩溃自动重建、全链路启动埋点。
 */
class MainActivity : Activity() {

    private var webView: WebView? = null
    private var assetLoader: WebViewAssetLoader? = null
    private var appBridge: AppBridge? = null
    private lateinit var configManager: ConfigManager
    private lateinit var config: AppConfig
    private lateinit var rootLayout: FrameLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private val splashWatchdog = Runnable { forceEnterEntry("看门狗：启动页未按时跳转") }

    private var useSplashPage = false
    private var watchdogScheduled = false

    private val baseUrl = "https://appassets.androidplatform.net/assets/www/"
    private val splashUrl get() = baseUrl + SPLASH_PAGE
    private val entryUrl get() = baseUrl + config.webview.entry

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartUpTracer.mark("Activity onCreate 进入")

        configManager = ConfigManager.getInstance(this)
        config = configManager.config

        window.statusBarColor = Color.parseColor(config.webview.statusBarColor)
        window.navigationBarColor = Color.parseColor(config.webview.navigationBarColor)

        rootLayout = FrameLayout(this)
        setContentView(rootLayout)
        StartUpTracer.mark("setContentView 完成（透明容器，透出 windowBackground）")

        useSplashPage = config.splash.enabled && assetExists("www/$SPLASH_PAGE")

        // 等 windowBackground 首帧上屏后再创建 WebView，与 Application 中发起的后台内核预热并行，
        // 避免 WebView 重初始化挡在第一帧之前。
        rootLayout.viewTreeObserver.addOnPreDrawListener(object :
            android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                rootLayout.viewTreeObserver.removeOnPreDrawListener(this)
                rootLayout.post { buildWebView() }
                return true
            }
        })
    }

    private fun assetExists(assetPath: String): Boolean = try {
        assets.open(assetPath).use { it.read() != -1 }
    } catch (_: Exception) {
        false
    }

    /**
     * 创建并装配 WebView；渲染进程崩溃后复用该流程重建实例（旧实例必须先销毁，不可复用）。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView() {
        webView?.let { old ->
            (old.parent as? ViewGroup)?.removeView(old)
            old.destroy()
            webView = null
        }

        StartUpTracer.beginWebViewCtor()
        val webView = WebView(this)
        this.webView = webView
        StartUpTracer.endWebViewCtor()

        // 透明背景：在 splash.html 首帧绘制前透出原生 windowBackground，实现无缝衔接、不露白。
        webView.setBackgroundColor(Color.TRANSPARENT)

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        this.assetLoader = assetLoader

        val appBridge = AppBridge(this, webView)
        this.appBridge = appBridge
        appBridge.uiBridge.attach(webView, window)

        configureWebViewSettings(config.webview.debuggable)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val localResponse = assetLoader.shouldInterceptRequest(request.url)?.let {
                    injectCspIfHtml(it, request.url?.path)
                }
                if (localResponse != null) return localResponse

                // SPA history fallback：仅对无扩展名的路由返回 index.html；显式 .html/静态资源缺失不兜底。
                if (config.webview.spaFallback &&
                    request.url.host == "appassets.androidplatform.net" &&
                    request.method == "GET"
                ) {
                    val path = request.url.path ?: ""
                    val isStaticResource = path.contains(".")
                    if (!isStaticResource && path != "/assets/www/index.html" &&
                        !path.endsWith("/$SPLASH_PAGE")
                    ) {
                        return try {
                            assets.open("www/index.html").use { stream ->
                                WebResourceResponse("text/html", "UTF-8", stream).also {
                                    injectCspIfHtml(it, path)
                                }
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                return null
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { appBridge.updateUrl(it) }
                if (isSplashUrl(url)) {
                    // 启动页期间保持透明，透出 windowBackground
                    view?.setBackgroundColor(Color.TRANSPARENT)
                    StartUpTracer.mark("splash.html 开始加载")
                } else {
                    // 进入主页：用启动背景色承接，避免页面首帧前出现系统默认白底
                    view?.setBackgroundColor(resolveSplashBackgroundColor())
                    cancelSplashWatchdog()
                    StartUpTracer.mark("入口页开始加载")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { appBridge.updateUrl(it) }
                if (!isSplashUrl(url)) {
                    webView.evaluateJavascript(
                        "window.APP_VERSION='${BuildConfig.VERSION_NAME}';" +
                            "if(document.getElementById('app-version')){" +
                            "document.getElementById('app-version').textContent='v${BuildConfig.VERSION_NAME}';}",
                        null
                    )
                    cancelSplashWatchdog()
                    StartUpTracer.markFirstFrame("入口页 onPageFinished")
                    appBridge.emitEvent(EventBridge.EVENT_ON_RESUME)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame != true) return
                Log.e(TAG, "主框架加载失败 code=${error?.errorCode} desc=${error?.description} url=${request.url}")
                if (isSplashUrl(request.url?.toString())) {
                    // 启动页本身加载失败，直接进入入口页，避免停在静态背景
                    forceEnterEntry("启动页加载失败")
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                Log.e(TAG, "SSL 错误，取消加载: ${error?.primaryError}")
                handler?.cancel()
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                Log.e(TAG, "WebView 渲染进程终止(crash=${detail?.didCrash()})，重建实例")
                StartUpTracer.mark("onRenderProcessGone 重建")
                cancelSplashWatchdog()
                mainHandler.post { buildWebView() }
                return true
            }
        }

        webView.addJavascriptInterface(appBridge, AppBridge.BRIDGE_INTERFACE_NAME)
        removeDangerousInterfaces(webView)

        rootLayout.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val startUrl = if (useSplashPage) splashUrl else entryUrl
        StartUpTracer.markLoadUrl()
        webView.loadUrl(startUrl)
        if (useSplashPage) scheduleSplashWatchdog()

        // 首帧埋点（最贴近真正可见的时机）
        webView.postVisualStateCallback(
            VISUAL_STATE_REQUEST_ID,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    StartUpTracer.markFirstFrame(if (useSplashPage) "splash 首帧" else "入口首帧")
                }
            }
        )
    }

    private fun isSplashUrl(url: String?): Boolean =
        url != null && url.contains("/$SPLASH_PAGE")

    private fun resolveSplashBackgroundColor(): Int = try {
        Color.parseColor(config.splash.backgroundColor)
    } catch (_: Exception) {
        Color.WHITE
    }

    private fun scheduleSplashWatchdog() {
        if (watchdogScheduled) return
        watchdogScheduled = true
        // 启动页动画时长 + 跳转/预载缓冲，封顶 12 秒，仅在异常时兜底
        val waitMs = (config.splash.duration.coerceIn(500, 5000) + SPLASH_GRACE_MS)
            .toLong()
            .coerceAtMost(SPLASH_MAX_WAIT_MS)
        mainHandler.postDelayed(splashWatchdog, waitMs)
    }

    private fun cancelSplashWatchdog() {
        watchdogScheduled = false
        mainHandler.removeCallbacks(splashWatchdog)
    }

    private fun forceEnterEntry(reason: String) {
        cancelSplashWatchdog()
        Log.w(TAG, "$reason，强制进入入口页")
        StartUpTracer.mark("强制进入入口页($reason)")
        val webView = webView ?: return
        webView.setBackgroundColor(resolveSplashBackgroundColor())
        webView.loadUrl(entryUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSettings(debuggable: Boolean) {
        val webView = webView ?: return
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = resolveCacheMode(config.webview.cacheMode)

        settings.javaScriptCanOpenWindowsAutomatically = false
        @Suppress("DEPRECATION")
        settings.saveFormData = false

        // 纯本地应用（未声明联网）在内核层阻断网络：消除网络栈/Safe Browsing 首启等待并收敛外联面；
        // 本地资源由 shouldInterceptRequest 直接供给，不受影响。
        settings.blockNetworkLoads = !config.permissions.internet

        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        if (debuggable) WebView.setWebContentsDebuggingEnabled(true)
    }

    private fun resolveCacheMode(mode: String): Int = when (mode.lowercase()) {
        "no_cache", "nocache" -> WebSettings.LOAD_NO_CACHE
        "cache_only", "cacheonly" -> WebSettings.LOAD_CACHE_ONLY
        "cache_else_network", "cacheelsenetwork" -> WebSettings.LOAD_CACHE_ELSE_NETWORK
        else -> WebSettings.LOAD_DEFAULT
    }

    /**
     * 为 HTML 响应注入 CSP。容器页面普遍使用内联样式/脚本但不用 eval，
     * 故放行 'unsafe-inline' 而不放行 'unsafe-eval'；联网能力随 INTERNET 权限变化。
     */
    private fun injectCspIfHtml(response: WebResourceResponse, path: String?): WebResourceResponse {
        val csp = resolveCsp() ?: return response
        val mime = response.mimeType ?: ""
        val isHtml = mime.contains("text/html") ||
            path?.endsWith(".html") == true ||
            path?.endsWith(".htm") == true
        if (isHtml) {
            val merged = HashMap<String, String>(response.responseHeaders ?: emptyMap())
            merged["Content-Security-Policy"] = csp
            response.responseHeaders = merged
        }
        return response
    }

    private fun resolveCsp(): String? {
        val configured = config.webview.csp
        if (configured.isNullOrBlank()) return buildDefaultCsp()
        if (configured.trim().equals("off", ignoreCase = true)) return null
        return configured.trim()
    }

    private fun buildDefaultCsp(): String {
        val connect = if (config.permissions.internet) {
            "connect-src 'self' https: wss:; "
        } else {
            "connect-src 'self'; "
        }
        return "default-src 'self' data: blob:; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data:; " +
            "media-src 'self' data: blob:; " +
            connect +
            "object-src 'none'; base-uri 'self'; frame-src 'self'"
    }

    private fun removeDangerousInterfaces(webView: WebView) {
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
        webView.removeJavascriptInterface("accessibility")
        webView.removeJavascriptInterface("accessibilityTraversal")
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        appBridge?.emitEvent(EventBridge.EVENT_ON_PAUSE)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
        appBridge?.emitEvent(EventBridge.EVENT_ON_RESUME)
    }

    override fun onDestroy() {
        appBridge?.emitEvent(EventBridge.EVENT_ON_DESTROY)
        cancelSplashWatchdog()
        webView?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
            webView = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        appBridge?.onPermissionResult(permissions, grantResults)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val webView = webView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            appBridge?.emitEvent(EventBridge.EVENT_BACK_PRESSED)
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val VISUAL_STATE_REQUEST_ID = 1L
        private const val SPLASH_PAGE = "splash.html"
        private const val SPLASH_GRACE_MS = 3500
        private const val SPLASH_MAX_WAIT_MS = 12000L
    }
}
