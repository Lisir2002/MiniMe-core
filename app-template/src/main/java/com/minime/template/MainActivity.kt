package com.minime.template

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import com.minime.template.bridge.AppBridge
import com.minime.template.bridge.EventBridge
import com.minime.template.bridge.UiBridge

/**
 * 模版应用主 Activity
 *
 * 核心设计：
 * 1. WebViewAssetLoader：通过虚拟 HTTPS 域名（https://appassets.androidplatform.net/）加载本地 assets，
 *    替代 file:// 协议，解决安全风险、跨域问题、history 路由支持
 * 2. 安全加固：禁用所有 file:// 访问、移除系统默认危险接口、开启安全浏览、默认禁止明文HTTP
 * 3. SPA fallback：history 路由模式下，非静态资源请求统一返回 index.html
 * 4. 启动屏：WebView 加载完成前显示启动屏，加载完成后淡出
 * 5. 全量 JS Bridge：来源校验 + 能力分级 + 异步回调
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var appBridge: AppBridge
    private lateinit var configManager: ConfigManager
    private lateinit var splashView: View

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configManager = ConfigManager.getInstance(this)
        val config = configManager.config

        // 设置状态栏/导航栏颜色
        window.statusBarColor = Color.parseColor(config.webview.statusBarColor)
        window.navigationBarColor = Color.parseColor(config.webview.navigationBarColor)

        // 创建启动屏
        splashView = View(this).apply {
            setBackgroundColor(Color.parseColor(config.splash.backgroundColor))
        }
        setContentView(splashView)

        // 创建 WebView
        webView = WebView(this)

        // 配置 WebViewAssetLoader：虚拟 HTTPS 域名映射到 assets/www/
        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        // 初始化 AppBridge
        appBridge = AppBridge(this, webView)

        // 配置 WebView 设置
        configureWebViewSettings(config.webview.debuggable)

        // 设置 WebViewClient（资源拦截 + SPA fallback + 加载回调）
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                // 先交给 WebViewAssetLoader 处理本地资源
                val localResponse = assetLoader.shouldInterceptRequest(request.url)
                if (localResponse != null) {
                    return localResponse
                }

                // SPA fallback：如果启用了 SPA fallback 且请求是本地虚拟域名的导航请求，
                // 且不是静态资源文件，则返回 index.html
                if (config.webview.spaFallback &&
                    request.url.host == "appassets.androidplatform.net" &&
                    request.method == "GET"
                ) {
                    val path = request.url.path ?: ""
                    val isStaticResource = path.contains(".") &&
                        !path.endsWith(".html") &&
                        !path.endsWith(".htm")
                    if (!isStaticResource && path != "/assets/www/index.html") {
                        return try {
                            val indexStream = assets.open("www/index.html")
                            WebResourceResponse("text/html", "UTF-8", indexStream)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后隐藏启动屏
                hideSplash()
                // 发送 app:resume 事件
                appBridge.emitEvent(EventBridge.EVENT_ON_RESUME)
            }
        }

        // 注入 JS Bridge
        webView.addJavascriptInterface(appBridge, AppBridge.BRIDGE_INTERFACE_NAME)

        // 移除系统默认暴露的危险接口
        removeDangerousInterfaces()

        // 加载入口页面
        val entry = config.webview.entry
        val entryUrl = "https://appassets.androidplatform.net/assets/www/$entry"
        webView.loadUrl(entryUrl)

        // 显示 WebView（启动屏在上面，加载完成后淡出）
        val contentView = android.widget.FrameLayout(this)
        contentView.addView(webView)
        contentView.addView(splashView)
        setContentView(contentView)
    }

    /**
     * 配置 WebView 设置
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebViewSettings(debuggable: Boolean) {
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
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 安全加固：禁用所有 file:// 访问
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false

        // 开启安全浏览
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        // 调试模式
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * 移除系统默认暴露的危险 Java 接口
     */
    private fun removeDangerousInterfaces() {
        webView.removeJavascriptInterface("searchBoxJavaBridge_")
        webView.removeJavascriptInterface("accessibility")
        webView.removeJavascriptInterface("accessibilityTraversal")
    }

    /**
     * 隐藏启动屏（淡出动画）
     */
    private fun hideSplash() {
        if (splashView.visibility != View.VISIBLE) return
        splashView.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                splashView.visibility = View.GONE
            }
            .start()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        appBridge.emitEvent(EventBridge.EVENT_ON_PAUSE)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        appBridge.emitEvent(EventBridge.EVENT_ON_RESUME)
    }

    override fun onDestroy() {
        appBridge.emitEvent(EventBridge.EVENT_ON_DESTROY)
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            appBridge.emitEvent(EventBridge.EVENT_BACK_PRESSED)
            super.onBackPressed()
        }
    }
}
