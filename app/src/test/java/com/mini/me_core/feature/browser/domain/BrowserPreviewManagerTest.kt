package com.mini.me_core.feature.browser.domain

import android.webkit.WebView
import com.mini.me_core.core.util.FileLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 预览截图管理器回归。
 *
 * 背景：AI 接管浏览器后右下角悬浮预览面板「每次空白」——根因是
 * [BrowserPreviewManager.captureNow] 历史上直接在 IO 协程调用 WebView.draw，
 * 违反 [BrowserController]「所有 WebView 访问统一调度主线程」的纪律，
 * 跨线程 draw 抛异常被 catch 静默吞掉 → previewState 永远 null → 面板只剩空底色。
 * 本测试锁定：captureNow 走 suspend + Main dispatcher 且正常产出帧；
 * 宽高为 0 与 draw 失败均有确定行为（跳过/吞掉不崩）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserPreviewManagerTest {

    private lateinit var controller: BrowserController
    private lateinit var manager: BrowserPreviewManager

    @Before
    fun setUp() {
        controller = mockk(relaxed = true)
        every { controller.agentStatus } returns MutableStateFlow(AgentBrowserStatus(active = true))
        manager = BrowserPreviewManager(controller)
    }

    private fun webView(
        width: Int = 800,
        height: Int = 600,
        url: String = "https://example.com/page",
        title: String = "示例页面",
        drawThrows: Boolean = false
    ): WebView = mockk<WebView>(relaxed = true).also { wv ->
        every { wv.width } returns width
        every { wv.height } returns height
        every { wv.url } returns url
        every { wv.title } returns title
        every { wv.background } returns null
        if (drawThrows) {
            every { wv.draw(any()) } throws RuntimeException("Only the original thread that created a view hierarchy can touch its views.")
        } else {
            every { wv.draw(any()) } returns Unit
        }
    }

    @Test
    fun captureNow_producesFrameWithPageMeta() = runBlocking {
        val wv = webView()

        manager.captureNow(wv)

        val frame = manager.previewState.value
        assertNotNull("截图成功后必须产出预览帧", frame)
        assertTrue("JPEG 字节不应为空", frame!!.jpegBytes.isNotEmpty())
        assertTrue("URL 应随帧带上", frame.url == "https://example.com/page")
        assertTrue("标题应随帧带上", frame.title == "示例页面")
        verify { wv.draw(any()) }
    }

    @Test
    fun captureNow_zeroSizeWebView_skipsWithoutDrawing() = runBlocking {
        val wv = webView(width = 0, height = 0)

        manager.captureNow(wv)

        assertNull("宽高为 0 的 WebView 不应产出帧", manager.previewState.value)
        verify(exactly = 0) { wv.draw(any()) }
    }

    @Test
    fun captureNow_drawFailure_isSwallowedAndLeavesNoFrame() = runBlocking {
        // 跨线程 draw 失败的形态：异常必须被吞掉不崩，且不留下半成品帧
        val wv = webView(drawThrows = true)

        manager.captureNow(wv)

        assertNull("draw 失败时不应产出帧", manager.previewState.value)
    }

    @Test
    fun stopCapturing_clearsFrame() = runBlocking {
        val wv = webView()
        manager.captureNow(wv)
        assertNotNull(manager.previewState.value)

        manager.stopCapturing()

        assertNull("停止捕获后必须清空帧，避免陈旧画面常驻", manager.previewState.value)
    }

    @Test
    fun captureNow_secondFrame_replacesFirst() = runBlocking {
        manager.captureNow(webView(url = "https://a.example.com", title = "A"))
        manager.captureNow(webView(url = "https://b.example.com", title = "B"))

        assertTrue(manager.previewState.value?.url == "https://b.example.com")
    }
}
