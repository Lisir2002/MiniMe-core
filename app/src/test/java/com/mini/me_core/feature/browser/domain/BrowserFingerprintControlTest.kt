package com.mini.me_core.feature.browser.domain

import android.webkit.WebResourceRequest
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintManager
import com.mini.me_core.feature.browser.domain.fingerprint.FingerprintProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 指纹注入拦截护栏回归。
 *
 * 背景：shouldInterceptRequest 对主框架 HTML 用 OkHttp 重拉后注入伪装脚本，
 * 若服务端强发非 gzip 压缩（br/deflate），压缩字节被当 HTML 改写且响应头仍带
 * 原编码，WebView 解压乱码 → 整页损坏。与「背景图不显示」同属
 * 「响应元数据与改写体不匹配」家族：改写体必须搭配正确的响应头。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserFingerprintControlTest {

    private fun profile() = FingerprintProfile(id = "spoof", name = "伪装配置")

    private fun okHttpReturning(response: Response): OkHttpClient {
        val call = mockk<Call>()
        every { call.execute() } returns response
        val client = mockk<OkHttpClient>()
        every { client.newCall(any()) } returns call
        return client
    }

    private fun response(
        contentType: String?,
        contentEncoding: String?,
        body: ResponseBody?,
        headers: Map<String, String> = emptyMap()
    ): Response = mockk<Response>(relaxed = true).also { r ->
        every { r.header("Content-Type", "") } returns (contentType ?: "")
        every { r.header("Content-Encoding") } returns contentEncoding
        headers.forEach { (name, value) -> every { r.header(name) } returns value }
        every { r.body } returns body
        every { r.headers } returns Headers.Builder().apply {
            headers.forEach { (name, value) -> add(name, value) }
        }.build()
    }

    private fun request() = mockk<WebResourceRequest>(relaxed = true)

    @Test
    fun compressedResponse_skipsInjectionInsteadOfCorruptingPage() {
        val manager = mockk<FingerprintManager>()
        every { manager.getCurrent() } returns profile()
        val body = mockk<ResponseBody>(relaxed = true)
        val resp = response(contentType = "text/html", contentEncoding = "br", body = body)

        val result = BrowserFingerprintControl(manager, okHttpReturning(resp))
            .injectFingerprintIntoHtml("https://example.com", request())

        assertNull(result)
        // 放弃注入时必须释放资源，避免连接泄漏
        verify { body.close() }
        verify { resp.close() }
    }

    @Test
    fun nonHtmlResponse_skipsInjection() {
        val manager = mockk<FingerprintManager>()
        every { manager.getCurrent() } returns profile()
        val body = ResponseBody.create(null, "{\"ok\":true}")
        val resp = response(contentType = "application/json", contentEncoding = null, body = body)

        val result = BrowserFingerprintControl(manager, okHttpReturning(resp))
            .injectFingerprintIntoHtml("https://example.com/api", request())

        assertNull(result)
    }

    @Test
    fun noActiveProfile_skipsInjection() {
        val manager = mockk<FingerprintManager>()
        every { manager.getCurrent() } returns null

        val result = BrowserFingerprintControl(manager, okHttpReturning(response("text/html", null, null)))
            .injectFingerprintIntoHtml("https://example.com", request())

        assertNull(result)
    }

    @Test
    fun htmlResponse_injectsAndStripsStaleContentLength() {
        val manager = mockk<FingerprintManager>()
        every { manager.getCurrent() } returns profile()
        val body = ResponseBody.create(null, "<html><head></head><body>hi</body></html>")
        val headers = mapOf(
            "Content-Length" to "9999", // 改写后长度必变，过期值会截断注入后的 HTML
            "X-Test" to "1"
        )
        val resp = response(contentType = "text/html; charset=utf-8", contentEncoding = null, body = body, headers = headers)

        val result = BrowserFingerprintControl(manager, okHttpReturning(resp))
            .injectFingerprintIntoHtml("https://example.com", request())

        assertNotNull(result)
        val responseHeaders = requireNotNull(result).responseHeaders
        assertFalse(responseHeaders.keys.any { it.equals("Content-Length", ignoreCase = true) })
        assertEquals("1", responseHeaders["X-Test"])
    }
}
