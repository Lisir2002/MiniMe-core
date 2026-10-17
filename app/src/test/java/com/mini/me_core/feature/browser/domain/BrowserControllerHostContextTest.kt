package com.mini.me_core.feature.browser.domain

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WebView 宿主 context 切换回归（内置浏览器 `<select>` 弹层不显示的根因修复）。
 *
 * 背景：Chromium 的原生弹层（`<select>` 下拉、日期选择器等）是用 WebView 自身 context
 * 创建的系统对话框。本门面注入的是 Application context——没有窗口令牌、没有 Activity
 * 主题，弹层静默失败，表现为「点 <select> 毫无反应」，而其他浏览器正常（根因在宿主）。
 * 修复：WebView 一律包 [MutableContextWrapper]，浏览器页可见时 base 指向宿主 Activity，
 * 卸载时回退 Application context（Agent 后台浏览不需要弹层，且避免持有已销毁 Activity）。
 *
 * 本测试锁定三条契约，任何一条被破坏都会让 `<select>` 弹层重新失效：
 *  1. WebView 的 context 必须是 MutableContextWrapper（而非裸 Application context）；
 *  2. attachHost 后 base 指向 Activity，且对已存在的 WebView 同样生效；
 *  3. detachHost 后 base 回退 Application context。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserControllerHostContextTest {

    private lateinit var controller: BrowserController
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        // 其余协作者与本测试无关，全部 relaxed mock（init 中的两个 Flow collect
        // 不会发射，协程立即完成）。
        controller = BrowserController(
            appContext,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
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

    @Test
    fun `webView context is a mutable wrapper even without host activity`() {
        // Agent 后台浏览场景：没有宿主 Activity，也必须包 wrapper（否则之后 attachHost
        // 无法把 base 换成 Activity）。此时 base 退化为 Application context。
        controller.ensureActiveTab()
        val webView = requireNotNull(controller.getActiveWebView())
        val wrapper = webView.context
        assertTrue(
            "WebView context 必须是 MutableContextWrapper，实际为 ${wrapper::class.java.name}",
            wrapper is MutableContextWrapper
        )
        assertSame(appContext, (wrapper as MutableContextWrapper).baseContext)
    }

    @Test
    fun `attachHost swaps existing webView context base to activity`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        // 先无宿主创建（模拟后台标签），再附着宿主：已存在的 WebView 也必须换 base，
        // 否则历史标签的 <select> 依旧不弹。
        controller.ensureActiveTab()
        val webView = requireNotNull(controller.getActiveWebView())

        controller.attachHost(activity)

        val wrapper = webView.context as MutableContextWrapper
        assertSame(activity, wrapper.baseContext)
    }

    @Test
    fun `webView created after attachHost holds activity context`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        controller.attachHost(activity)

        controller.ensureActiveTab()

        val wrapper = requireNotNull(controller.getActiveWebView()).context as MutableContextWrapper
        assertSame(activity, wrapper.baseContext)
    }

    @Test
    fun `detachHost reverts context base to application`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        controller.attachHost(activity)
        controller.ensureActiveTab()
        val webView = requireNotNull(controller.getActiveWebView())

        controller.detachHost()

        // 离开浏览器页后必须回退，避免 wrapper 持有已销毁的 Activity（泄漏）。
        val wrapper = webView.context as MutableContextWrapper
        assertSame(appContext, wrapper.baseContext)
    }
}
