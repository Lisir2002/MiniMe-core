package com.mini.me_core.feature.browser.domain

import android.content.Intent
import android.net.http.SslError
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * 内置浏览器宿主能力加固回归（与 `<select>` 弹层同类的「宿主侧静默失效」家族）。
 *
 * 背景：WebView 的默认实现会静默丢弃一批浏览器标准能力——prompt() 输入框、
 * window.open 新窗口、网页全屏、权限/定位请求、渲染进程崩溃恢复。
 * 页面侧表现为「点了没反应」，而根因全在宿主没有显式接管。
 * 本测试锁定每一条接管契约：
 *  1. prompt：登记待处理对话框（带 defaultValue），确认时把用户输入回传 JS；
 *  2. 全屏：onShowCustomView 暴露 View，exitFullscreen 回执页面；
 *  3. 权限：受保护媒体资源授予、其余拒绝；定位按「仅本次允许」；
 *  4. 新窗口：onCreateWindow 开新标签并回传 transport，无用户手势且开启
 *     阻止弹窗时拦截；
 *  5. window.close：关对应标签但至少保留一个；
 *  6. 渲染进程崩溃：onRenderProcessGone 返回 true（否则系统杀整个 App），
 *     并原地重建标签 WebView。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserControllerWebCapabilitiesTest {

    private lateinit var controller: BrowserController
    private lateinit var adBlocker: AdBlocker

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        adBlocker = mockk(relaxed = true)
        controller = BrowserController(
            appContext,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            adBlocker,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        controller.destroy()
        unmockkAll()
    }

    private fun activeWebView(): WebView {
        // getActiveWebView 不负责建标签：先确保存在激活标签，再取（与 UI  bind() 同序）。
        controller.ensureActiveTab()
        return requireNotNull(controller.getActiveWebView())
    }

    private fun chromeClient(): WebChromeClient = shadowOf(activeWebView()).webChromeClient

    /** 对话框结果由 IO 协程确认后再 post 回主线程，轮询 idle 等待其落地。 */
    private fun flushMainLooper() {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(30) {
            looper.idle()
            Thread.sleep(10)
        }
    }

    /**
     * 确认/取消对话框并等待结果落地，返回 [BrowserController.handleDialog] 的执行结果。
     *
     * handleDialog 内部经 Dispatchers.Main 做 50ms 沉降等待：Robolectric 主 looper
     * 处于 PAUSED 模式、时钟冻结，测试线程直接 runBlocking 会死等那条 postDelayed
     * 消息。因此把调用放到后台协程，由测试线程持续推进主 looper（含延时任务），
     * 每轮之间让出真实时间供 Dispatchers.Default 上的续体完成收尾。
     */
    private fun resolveDialogAsync(accept: Boolean): Deferred<Boolean> =
        CoroutineScope(Dispatchers.Default).async { controller.handleDialog(accept) }

    private fun awaitDialogResolution(job: Deferred<Boolean>): Boolean = runBlocking {
        withTimeout(5_000) {
            val looper = shadowOf(Looper.getMainLooper())
            while (job.isActive) {
                looper.idleFor(Duration.ofMillis(25))
                delay(10)
            }
            job.await()
        }
    }

    @Test
    fun `prompt registers dialog with default value and confirms user input to js`() {
        activeWebView() // 确保标签存在
        val result = mockk<JsPromptResult>(relaxed = true)

        chromeClient().onJsPrompt(activeWebView(), "https://example.com", "请输入名字", "默认名", result)

        val pending = controller.pendingDialog.value
        assertEquals("prompt", pending?.type)
        assertEquals("默认名", pending?.defaultValue)

        controller.setPromptInput("张三")
        assertTrue(awaitDialogResolution(resolveDialogAsync(true)))
        flushMainLooper()

        // prompt 必须把输入框内容回传给 JS，否则页面拿到 null（点了没反应的根因之一）
        verify { result.confirm("张三") }
        assertNull(controller.pendingDialog.value)
    }

    @Test
    fun `prompt cancel forwards js cancel`() {
        activeWebView()
        val result = mockk<JsPromptResult>(relaxed = true)

        chromeClient().onJsPrompt(activeWebView(), "https://example.com", "请输入", null, result)

        assertTrue(awaitDialogResolution(resolveDialogAsync(false)))
        flushMainLooper()

        verify { result.cancel() }
    }

    @Test
    fun `alert and confirm dialogs are registered`() {
        activeWebView()
        val alert = mockk<JsResult>(relaxed = true)
        chromeClient().onJsAlert(activeWebView(), "https://example.com", "提示内容", alert)
        assertEquals("alert", controller.pendingDialog.value?.type)

        val confirm = mockk<JsResult>(relaxed = true)
        chromeClient().onJsConfirm(activeWebView(), "https://example.com", "确认？", confirm)
        assertEquals("confirm", controller.pendingDialog.value?.type)

        assertTrue(awaitDialogResolution(resolveDialogAsync(true)))
        flushMainLooper()
        verify { confirm.confirm() }
    }

    @Test
    fun `onShowCustomView exposes fullscreen view and exitFullscreen notifies page`() {
        activeWebView()
        val callback = mockk<WebChromeClient.CustomViewCallback>(relaxed = true)
        val fsView = View(ApplicationProvider.getApplicationContext())
        var entered: Boolean? = null
        controller.onFullscreenChange = { entered = it }

        chromeClient().onShowCustomView(fsView, callback)

        assertSame(fsView, controller.fullscreenView)
        assertTrue(entered == true)

        // 返回键 / 用户点击退出：必须回执页面，否则网页永远停在「全屏中」
        assertTrue(controller.exitFullscreen())
        verify { callback.onCustomViewHidden() }
        assertNull(controller.fullscreenView)
        assertTrue(entered == false)
        // 无全屏时再退一次返回 false（供返回键判断是否消费事件）
        assertFalse(controller.exitFullscreen())
    }

    @Test
    fun `onHideCustomView clears fullscreen state`() {
        activeWebView()
        val callback = mockk<WebChromeClient.CustomViewCallback>(relaxed = true)
        chromeClient().onShowCustomView(View(ApplicationProvider.getApplicationContext()), callback)
        assertNotNull(controller.fullscreenView)

        chromeClient().onHideCustomView()

        assertNull(controller.fullscreenView)
        verify { callback.onCustomViewHidden() }
    }

    @Test
    fun `permission request grants protected media only`() {
        activeWebView()
        val grantable = mockk<PermissionRequest>(relaxed = true)
        every { grantable.resources } returns arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)

        chromeClient().onPermissionRequest(grantable)

        verify {
            grantable.grant(match { it.contentEquals(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) })
        }
    }

    @Test
    fun `permission request denies camera and microphone`() {
        activeWebView()
        val denied = mockk<PermissionRequest>(relaxed = true)
        every { denied.resources } returns arrayOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE
        )

        chromeClient().onPermissionRequest(denied)

        verify { denied.deny() }
    }

    @Test
    fun `geolocation prompt allows once without persisting`() {
        activeWebView()
        val callback = mockk<GeolocationPermissions.Callback>(relaxed = true)

        chromeClient().onGeolocationPermissionsShowPrompt("https://example.com", callback)

        // allow = true, retain = false：仅本次允许，不持久化（对齐 Chrome 临时授权）
        verify { callback.invoke("https://example.com", true, false) }
    }

    @Test
    fun `onCreateWindow opens new tab and transports its webview`() {
        activeWebView()
        val message = obtainMessageWithTarget()
        val transport = activeWebView().WebViewTransport()
        message.obj = transport

        val handled = chromeClient().onCreateWindow(activeWebView(), false, true, message)

        assertTrue(handled)
        assertEquals(2, controller.tabsState.value.size)
        assertNotNull(transport.webView)
        // 新标签应被切换为激活标签（用户点「在新标签打开」后期望跳过去）
        assertEquals(transport.webView, controller.getActiveWebView())
    }

    @Test
    fun `onCreateWindow blocked without user gesture when popup block enabled`() {
        activeWebView()
        every { adBlocker.popupBlockEnabled } returns true
        val message = obtainMessageWithTarget()
        val transport = activeWebView().WebViewTransport()
        message.obj = transport

        val handled = chromeClient().onCreateWindow(activeWebView(), false, false, message)

        assertTrue(handled)
        assertNull(transport.webView)
        assertEquals(1, controller.tabsState.value.size)
    }

    @Test
    fun `onCloseWindow closes matching tab but keeps at least one`() {
        activeWebView()
        // 先经 onCreateWindow 开出第二个标签
        val message = obtainMessageWithTarget()
        val transport = activeWebView().WebViewTransport()
        message.obj = transport
        chromeClient().onCreateWindow(activeWebView(), false, true, message)
        assertEquals(2, controller.tabsState.value.size)

        // window.close() 关掉第二个标签
        chromeClient().onCloseWindow(transport.webView)
        assertEquals(1, controller.tabsState.value.size)

        // 最后一个标签的 window.close() 必须被忽略（浏览器永远至少留一个标签）
        chromeClient().onCloseWindow(controller.getActiveWebView())
        assertEquals(1, controller.tabsState.value.size)
    }

    @Test
    fun `render process gone rebuilds tab webview and returns true`() {
        activeWebView()
        val oldWebView = controller.getActiveWebView()
        val detail = mockk<android.webkit.RenderProcessGoneDetail>(relaxed = true)

        val handled = shadowOf(oldWebView).webViewClient.onRenderProcessGone(oldWebView, detail)

        // 必须返回 true：返回 false 系统会连带杀掉整个 App 进程
        assertTrue(handled)
        val newWebView = controller.getActiveWebView()
        assertNotNull(newWebView)
        assertFalse("渲染进程崩溃后必须重建 WebView，不能复用已崩溃实例", newWebView === oldWebView)
        assertEquals(1, controller.tabsState.value.size)
    }

    private fun obtainMessageWithTarget(): Message =
        Message.obtain().apply { target = Handler(Looper.getMainLooper()) }

    private fun webViewClient(): android.webkit.WebViewClient = shadowOf(activeWebView()).webViewClient

    @Test
    fun `tel link is dispatched to system app instead of failing inside webview`() {
        activeWebView()
        val request = mockk<WebResourceRequest>(relaxed = true)
        every { request.url } returns Uri.parse("tel:10086")
        every { request.hasGesture() } returns true

        val handled = webViewClient().shouldOverrideUrlLoading(activeWebView(), request)

        // WebView 直接 loadUrl("tel:…") 只会得到 ERR_UNKNOWN_URL_SCHEME（点了没反应），
        // 必须转交系统拨号应用。
        assertTrue(handled)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("tel:10086", started.data.toString())
    }

    @Test
    fun `mailto link is dispatched with sendto action`() {
        activeWebView()
        val request = mockk<WebResourceRequest>(relaxed = true)
        every { request.url } returns Uri.parse("mailto:support@example.com")
        every { request.hasGesture() } returns true

        val handled = webViewClient().shouldOverrideUrlLoading(activeWebView(), request)

        assertTrue(handled)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val started = shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_SENDTO, started.action)
    }

    @Test
    fun `intent scheme without user gesture is not dispatched`() {
        activeWebView()
        val request = mockk<WebResourceRequest>(relaxed = true)
        every { request.url } returns
            Uri.parse("intent://example.com/#Intent;scheme=https;package=com.example;end")
        every { request.hasGesture() } returns false

        webViewClient().shouldOverrideUrlLoading(activeWebView(), request)

        // intent:// 可拉起任意 Activity，无用户手势不得放行（对齐 Chrome）
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `http auth request registers pending dialog and proceeds with credentials`() {
        activeWebView()
        val handler = mockk<HttpAuthHandler>(relaxed = true)

        webViewClient().onReceivedHttpAuthRequest(activeWebView(), handler, "example.com", "Basic realm")

        val pending = controller.pendingHttpAuth.value
        assertEquals("example.com", pending?.host)
        assertEquals("Basic realm", pending?.realm)

        assertTrue(controller.resolveHttpAuth(true, "alice", "s3cret"))
        flushMainLooper()
        verify { handler.proceed("alice", "s3cret") }
        assertNull(controller.pendingHttpAuth.value)
    }

    @Test
    fun `http auth cancel cancels handler`() {
        activeWebView()
        val handler = mockk<HttpAuthHandler>(relaxed = true)

        webViewClient().onReceivedHttpAuthRequest(activeWebView(), handler, "example.com", null)

        assertTrue(controller.resolveHttpAuth(false))
        flushMainLooper()
        verify { handler.cancel() }
    }

    @Test
    fun `ssl error cancels loading instead of failing silently`() {
        activeWebView()
        val handler = mockk<SslErrorHandler>(relaxed = true)
        val error = mockk<SslError>(relaxed = true)
        every { error.url } returns "https://expired.example.com"
        every { error.primaryError } returns SslError.SSL_EXPIRED

        webViewClient().onReceivedSslError(activeWebView(), handler, error)

        // 安全上不提供绕过入口，但必须显式取消并记录（默认实现的静默取消不可排障）
        verify { handler.cancel() }
    }

    @Test
    fun `main frame load failure surfaces error state and retry clears it`() {
        activeWebView()
        val mainRequest = mockk<WebResourceRequest>(relaxed = true)
        every { mainRequest.isForMainFrame } returns true
        val error = mockk<WebResourceError>(relaxed = true)
        every { error.description } returns "net::ERR_NAME_NOT_RESOLVED"
        every { error.errorCode } returns -2

        webViewClient().onReceivedError(activeWebView(), mainRequest, error)

        // 主框架失败必须升级为可见错误态（默认只有日志 → 用户看到永久白屏）
        assertEquals("net::ERR_NAME_NOT_RESOLVED", controller.uiState.value.loadError)

        // 子资源失败不应把整页标记为错误页
        val subRequest = mockk<WebResourceRequest>(relaxed = true)
        every { subRequest.isForMainFrame } returns false
        webViewClient().onReceivedError(activeWebView(), subRequest, error)
        assertEquals("net::ERR_NAME_NOT_RESOLVED", controller.uiState.value.loadError)

        // 重新加载（onPageStarted）应清除错误态
        webViewClient().onPageStarted(activeWebView(), "https://example.com", null)
        assertNull(controller.uiState.value.loadError)
    }
}
