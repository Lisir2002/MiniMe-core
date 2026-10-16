package com.mini.me_core.feature.browser.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [AdBlocker.shouldBlock] 本地豁免优先级单测（Robolectric：需要真实的
 * android.net.Uri.parse 与 SharedPreferences）。
 *
 * 核心回归点：本地/私有 host 即使被用户加入自定义黑名单，也不进入广告名单
 * 裁决——这是「本地部署页面背景图被误杀」修复的行为契约；同时公网 host 的
 * 黑名单 / 内置广告名单必须照常生效，豁免不得扩大化。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AdBlockerShouldBlockTest {

    private lateinit var adBlocker: AdBlocker

    @Before
    fun setUp() {
        adBlocker = AdBlocker(ApplicationProvider.getApplicationContext<Context>())
        adBlocker.reset()
        adBlocker.adBlockEnabled = true
        adBlocker.trackerBlockEnabled = true
        adBlocker.customBlacklist = emptySet()
        adBlocker.customWhitelist = emptySet()
    }

    @Test
    fun `private IPv4 host in custom blacklist is still exempt`() {
        adBlocker.addBlacklist("192.168.1.5")
        assertFalse(adBlocker.shouldBlock("http://192.168.1.5:8080/assets/bg.png"))
    }

    @Test
    fun `loopback IPv4 host in custom blacklist is still exempt`() {
        adBlocker.addBlacklist("127.0.0.1")
        assertFalse(adBlocker.shouldBlock("http://127.0.0.1:3000/bg.png"))
    }

    @Test
    fun `localhost in custom blacklist is still exempt`() {
        adBlocker.addBlacklist("localhost")
        assertFalse(adBlocker.shouldBlock("http://localhost/bg.png"))
    }

    @Test
    fun `mDNS dot-local host in custom blacklist is still exempt`() {
        adBlocker.addBlacklist("nas.local")
        assertFalse(adBlocker.shouldBlock("https://nas.local/img/bg.jpg"))
    }

    @Test
    fun `public host in custom blacklist still blocks`() {
        adBlocker.addBlacklist("evil-ad.example")
        assertTrue(adBlocker.shouldBlock("https://evil-ad.example/ad.js"))
    }

    @Test
    fun `built-in ad domain still blocks`() {
        assertTrue(adBlocker.shouldBlock("https://doubleclick.net/x.gif"))
    }

    @Test
    fun `built-in tracker subdomain still blocks`() {
        assertTrue(adBlocker.shouldBlock("https://www.google-analytics.com/collect"))
    }

    @Test
    fun `normal public resource passes`() {
        assertFalse(adBlocker.shouldBlock("https://example.com/bg.png"))
    }

    @Test
    fun `unparseable url is not blocked`() {
        assertFalse(adBlocker.shouldBlock(""))
    }
}
