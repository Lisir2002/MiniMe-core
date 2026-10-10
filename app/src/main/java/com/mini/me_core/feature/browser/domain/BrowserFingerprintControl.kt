package com.mini.me_core.feature.browser.domain

import android.webkit.WebResourceRequest
import android.webkit.WebView
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintInjector
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintProfile
import com.mini.me_core.feature.browser.domain.fingerprint.WebViewFingerprintApplier
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream

/**
 * 指纹反检测注入（行为保持自 [BrowserController] 原样搬迁）。
 *
 * 只负责两件事，判定逻辑与注入时机/内容一字未改：
 *  - [injectFingerprintIntoHtml]：拦截主框架 HTML 请求，在 <head> 首行注入指纹伪装脚本；
 *  - [injectFingerprintScript]：onPageStarted 时通过 evaluateJavascript 兜底注入。
 * 指纹切换/应用到激活 WebView 的流程仍留在 [BrowserController]（依赖 UI 状态与主线程）。
 */
@Suppress("ClassOrdering") // 行为保持搬迁：成员按两个注入职责分组
internal class BrowserFingerprintControl(
    private val fingerprintManager: FingerprintManager,
    private val okHttp: OkHttpClient
) {

    /**
     * 拦截主框架 HTML 请求，在 <head> 首行注入指纹伪装脚本。
     *
     * 通过 OkHttp 拉取原始 HTML，注入 `<script>` 标签后返回修改后的响应。
     * 注入位置在 `<head>` 标签之后（若无 head 则在 `<html>` 之后），
     * 确保伪装脚本在页面自身任何脚本之前执行。
     *
     * 注入失败时返回 null，WebView 正常加载原页面（不影响浏览）。
     */
    @Suppress("ReturnCount") // 行为保持搬迁：注入各路径早退返回 null/修改响应，资源关闭顺序与注入时机逐字不变
    fun injectFingerprintIntoHtml(
        url: String,
        request: WebResourceRequest
    ): android.webkit.WebResourceResponse? {
        val profile = fingerprintManager.getCurrent() ?: return null
        // 真实指纹（无伪装配置）不注入脚本
        if (profile.id == FingerprintProfile.DEFAULT_ID && !profile.webglSpoofEnabled) return null

        // 用 OkHttp 拉取原始 HTML（复用 WebView 的请求头，保持 Cookie/Session 一致）
        val builder = Request.Builder().url(url).get()
        request.requestHeaders?.forEach { (key, value) ->
            if (key !in setOf("host", "content-length", "accept-encoding")) {
                builder.addHeader(key, value)
            }
        }
        val response: Response = okHttp.newCall(builder.build()).execute()
        val body = response.body ?: return null
        val contentType = response.header("Content-Type", "") ?: ""
        // 仅处理 HTML 响应
        if (!contentType.contains("text/html", ignoreCase = true)) {
            body.close()
            response.close()
            return null
        }

        val originalHtml = body.string()
        val script = FingerprintInjector.generateScript(profile)
        val injectTag = "<script>$script</script>"

        // 在 <head> 首行注入；若无 <head> 则在 <html> 后注入；都没有则在开头注入
        val modifiedHtml = when {
            originalHtml.contains("<head>", ignoreCase = true) ->
                originalHtml.replaceFirst(Regex("<head[^>]*>", RegexOption.IGNORE_CASE), "$0$injectTag")
            originalHtml.contains("<html>", ignoreCase = true) ->
                originalHtml.replaceFirst(Regex("<html[^>]*>", RegexOption.IGNORE_CASE), "$0$injectTag")
            else -> injectTag + originalHtml
        }

        // 构建修改后的响应
        val newResponse = android.webkit.WebResourceResponse(
            "text/html",
            response.header("Content-Encoding", "utf-8"),
            ByteArrayInputStream(modifiedHtml.toByteArray())
        )
        // 复制响应头
        val responseHeaders = mutableMapOf<String, String>()
        response.headers.names().forEach { name ->
            response.header(name)?.let { responseHeaders[name] = it }
        }
        newResponse.responseHeaders = responseHeaders
        response.close()
        return newResponse
    }

    /** 通过 evaluateJavascript 注入指纹伪装脚本（onPageStarted 兜底用）。 */
    fun injectFingerprintScript(webView: WebView) {
        val profile = fingerprintManager.getCurrent() ?: return
        if (profile.id == FingerprintProfile.DEFAULT_ID) return
        WebViewFingerprintApplier.injectScript(webView, profile)
    }
}
