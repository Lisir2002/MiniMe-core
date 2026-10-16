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
import java.util.regex.Matcher

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
        // 压缩编码护栏：OkHttp 的透明 gzip 会剥掉 Content-Encoding 头；走到这里仍有
        // 非 identity 编码（br/deflate 等服务端强发），说明 body 是压缩字节。
        // 此时把压缩字节当 HTML 改写会产出乱码页面，且改写头仍带原编码——与「背景图
        // 不显示」同属「响应元数据与改写体不匹配」家族。放弃注入，走正常加载。
        val contentEncoding = response.header("Content-Encoding")
        if (contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)) {
            body.close()
            response.close()
            return null
        }

        val originalHtml = body.string()
        val script = FingerprintInjector.generateScript(profile)
        val injectTag = "<script>$script</script>"

        // 在 <head> 首行注入；若无 <head> 则在 <html> 后注入；都没有则在开头注入。
        // 指纹脚本含 `$` 字符，字符串替换形式会把替换内容里的 `$` 当作分组引用，
        // 抛 Illegal group reference / 静默串改内容，导致注入整体失败
        // （主框架回退为无指纹加载）。必须对注入内容做 quoteReplacement 转义。
        val headRegex = Regex("<head[^>]*>", RegexOption.IGNORE_CASE)
        val htmlRegex = Regex("<html[^>]*>", RegexOption.IGNORE_CASE)
        val safeInject = Matcher.quoteReplacement(injectTag)
        val modifiedHtml = when {
            originalHtml.contains("<head>", ignoreCase = true) ->
                originalHtml.replaceFirst(headRegex, "$0$safeInject")
            originalHtml.contains("<html>", ignoreCase = true) ->
                originalHtml.replaceFirst(htmlRegex, "$0$safeInject")
            else -> injectTag + originalHtml
        }

        // 构建修改后的响应
        val newResponse = android.webkit.WebResourceResponse(
            "text/html",
            response.header("Content-Encoding", "utf-8"),
            ByteArrayInputStream(modifiedHtml.toByteArray())
        )
        // 复制响应头（剔除 Content-Length：改写后长度必变，过期值会截断注入后的 HTML）
        val responseHeaders = mutableMapOf<String, String>()
        response.headers.names().forEach { name ->
            if (name.equals("Content-Length", ignoreCase = true)) return@forEach
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
