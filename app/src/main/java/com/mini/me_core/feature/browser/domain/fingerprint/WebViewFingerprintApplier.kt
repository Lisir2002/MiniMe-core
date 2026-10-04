package com.mini.me_core.feature.browser.domain.fingerprint

import android.webkit.WebSettings
import android.webkit.WebView

/**
 * WebView 指纹应用层。
 *
 * 把 [FingerprintProfile] 的配置实际落到 [WebView] 实例上，包括：
 * - UserAgent 字符串（[applyUserAgent]）
 * - WebView 行为设置（[applyWebSettings]）
 * - JS 伪装脚本注入（[injectScript]）
 *
 * 所有公开方法均必须在主线程调用（WebView 本身要求主线程访问）。
 *
 * 使用方式：
 * - 新创建 WebView 后调用 [apply]，一次性下发 UA / WebSettings / JS 脚本；
 * - 切换指纹时先 [apply] 新配置，再 reload 页面使 document-start 注入生效；
 * - 传入 null 调用 [apply] 可清除伪装、恢复浏览器真实 UA。
 */
object WebViewFingerprintApplier {

    /**
     * 将指纹配置完整应用到 WebView。
     *
     * @param webView 目标 WebView
     * @param profile 指纹配置；为 null 时清除所有伪装，恢复真实 UA 且不注入脚本
     */
    fun apply(webView: WebView, profile: FingerprintProfile?) {
        if (profile == null) {
            clear(webView)
            return
        }
        applyWebSettings(webView, profile)
        applyUserAgent(webView, profile)
        injectScript(webView, profile)
    }

    /**
     * 仅应用 UserAgent（轻量，不需要重载页面即可影响后续请求）。
     *
     * 优先使用 [FingerprintProfile.userAgent]；未设置时按 browser + os + version 生成。
     */
    fun applyUserAgent(webView: WebView, profile: FingerprintProfile) {
        val ua = profile.userAgent ?: buildUserAgent(profile)
        webView.settings.userAgentString = ua
    }

    /**
     * 注入 JS 伪装脚本。
     *
     * 通过 [WebView.evaluateJavascript] 执行；在 WebViewClient.onPageStarted 或
     * document-start 时机调用可保证脚本早于页面业务脚本执行。
     */
    fun injectScript(webView: WebView, profile: FingerprintProfile) {
        val script = FingerprintInjector.generateScript(profile)
        // 包裹 try/catch，避免在特殊页面（about:blank / file://）抛错影响浏览
        webView.evaluateJavascript("(function(){try{$script}catch(e){}})();", null)
    }

    /**
     * 应用 WebSettings 相关配置（开启 JS、DOM 存储、默认缓存模式）。
     */
    fun applyWebSettings(webView: WebView, profile: FingerprintProfile) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    /**
     * 清除伪装：恢复默认 UA（置 null 由系统决定），不主动注入脚本。
     * 注意：已注入到页面上下文的 JS 无法从外部移除，需要 reload 才能真正恢复。
     */
    fun clear(webView: WebView) {
        webView.settings.userAgentString = null
    }

    /**
     * 根据 browser / os / version 生成标准桌面/移动 UA。
     */
    internal fun buildUserAgent(profile: FingerprintProfile): String {
        val chrome = profile.browserVersion
        return when (profile.os.lowercase()) {
            "windows" ->
                "Mozilla/5.0 (Windows NT ${profile.osVersion}; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
            "macos" ->
                "Mozilla/5.0 (Macintosh; Intel Mac OS X ${macOsVersion(profile.osVersion)}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
            "android" ->
                "Mozilla/5.0 (Linux; Android ${profile.osVersion}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36"
            "ios" ->
                "Mozilla/5.0 (iPhone; CPU iPhone OS ${iosVersion(profile.osVersion)} like Mac OS X) " +
                    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
            "linux" ->
                "Mozilla/5.0 (X11; Linux x86_64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
            else ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
        }
    }

    /** 把 "14.0" 转成 macOS UA 用的 "10_15_7" 风格（简化映射）。 */
    private fun macOsVersion(version: String): String {
        val parts = version.split(".")
        val major = parts.firstOrNull()?.toIntOrNull() ?: 10
        // macOS 11+ 在 UA 中仍写作 10_15_x 的历史兼容写法，这里做一个保守映射
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 15
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 7
        return "10_${minor.coerceIn(0, 15)}_$patch".let { if (major >= 11) "10_15_7" else it }
    }

    /** 把 "16.0" 转成 iOS UA 用的 "16_0" 风格。 */
    private fun iosVersion(version: String): String =
        version.split(".").joinToString("_") { it }
}
