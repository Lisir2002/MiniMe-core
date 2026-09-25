package com.mini.me_core.feature.browser.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F4.5 广告拦截与隐私保护。
 *
 * 内置常见广告 / 跟踪器域名黑名单，配合用户自定义黑白名单，在
 * [android.webkit.WebViewClient.shouldInterceptRequest] 阶段拦截请求。
 * 拦截计数按页面累计，导航到新页面时由 [BrowserController] 调用 [reset] 清零。
 */
@Singleton
class AdBlocker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("browser_privacy_prefs", Context.MODE_PRIVATE)

    // ── 开关（持久化） ───────────────────────────
    var adBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_ADBLOCK, true)
        set(v) = prefs.edit().putBoolean(KEY_ADBLOCK, v).apply()

    var trackerBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_TRACKER, true)
        set(v) = prefs.edit().putBoolean(KEY_TRACKER, v).apply()

    var popupBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_POPUP, true)
        set(v) = prefs.edit().putBoolean(KEY_POPUP, v).apply()

    var doNotTrack: Boolean
        get() = prefs.getBoolean(KEY_DNT, true)
        set(v) = prefs.edit().putBoolean(KEY_DNT, v).apply()

    var blockThirdPartyCookies: Boolean
        get() = prefs.getBoolean(KEY_3P_COOKIE, true)
        set(v) = prefs.edit().putBoolean(KEY_3P_COOKIE, v).apply()

    // ── 拦截计数 ───────────────────────────
    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun reset() { _blockedCount.value = 0 }

    // ── 用户自定义规则 ───────────────────────────
    var customBlacklist: Set<String>
        get() = prefs.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_BLACKLIST, v).apply()

    var customWhitelist: Set<String>
        get() = prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_WHITELIST, v).apply()

    fun addBlacklist(domain: String) {
        customBlacklist = customBlacklist + domain.trim().lowercase()
    }

    fun addWhitelist(domain: String) {
        customWhitelist = customWhitelist + domain.trim().lowercase()
    }

    /**
     * 判断请求 URL 是否应被拦截。在 shouldInterceptRequest 后台线程调用。
     * 白名单优先；自定义黑名单优先于内置规则。
     */
    fun shouldBlock(url: String): Boolean {
        val host = runCatching {
            android.net.Uri.parse(url).host?.lowercase()
        }.getOrNull() ?: return false

        // 白名单放行
        if (customWhitelist.any { host == it || host.endsWith(".$it") }) return false

        // 自定义黑名单
        if (customBlacklist.any { host == it || host.endsWith(".$it") }) {
            increment()
            return true
        }

        if (adBlockEnabled && AD_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            increment()
            return true
        }
        if (trackerBlockEnabled && TRACKER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            increment()
            return true
        }
        return false
    }

    @Synchronized
    private fun increment() {
        _blockedCount.value = _blockedCount.value + 1
    }

    companion object {
        private const val KEY_ADBLOCK = "adblock_on"
        private const val KEY_TRACKER = "tracker_on"
        private const val KEY_POPUP = "popup_on"
        private const val KEY_DNT = "dnt"
        private const val KEY_3P_COOKIE = "third_party_cookie"
        private const val KEY_BLACKLIST = "custom_blacklist"
        private const val KEY_WHITELIST = "custom_whitelist"

        /** 常见广告域名（精选）。 */
        val AD_DOMAINS = setOf(
            "doubleclick.net", "ads.google.com", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "adroll.com", "adservice.google.com", "admob.com", "imasdk.googleapis.com",
            "amazon-adsystem.com", "adsrvr.org", "advertising.com", "adtech.com", "mathtag.com",
            "criteo.com", "criteo.net", "outbrain.com", "taboola.com", "mgid.com", "zergnet.com",
            "popads.net", "popcash.net", "propellerads.com", "propellerclick.com", "onclkds.com",
            "adsafeprotected.com", "moatads.com", "openx.net", "pubmatic.com", "rubiconproject.com",
            "casalemedia.com", "contextweb.com", "gumgum.com", "playground.zemanta.com", "yieldmo.com",
            "33across.com", "33across-digital.com", "liveintent.com", "bidswitch.net", "smartadserver.com",
            "smartadserver.com", "adform.net", "media.net", "rlcdn.com", "fastlane.rubiconproject.com",
            "adformdsp.net", "teads.tv", "teads.com", "quantserve.com", "chartbeat.com",
            "crwdcntrl.net", "g.doubleclick.net", "securepubads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "s.amazon-adsystem.com", "ics.cdn.adtile.me", "ads.yieldmo.com", "ads.tremorhub.com"
        )

        /** 常见跟踪器 / 分析域名（精选）。 */
        val TRACKER_DOMAINS = setOf(
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "analytics.twitter.com", "stats.g.doubleclick.net", "connect.facebook.net",
            "pixel.facebook.com", "graph.facebook.com", "analytics.tiktok.com",
            "hotjar.com", "hotjar.io", "mixpanel.com", "segment.io", "segment.com",
            "amplitude.com", "sentry.io", "bugsnag.com", "fullstory.com", "mouseflow.com",
            "plausible.io", "fathom.site", "umami.is", "count.ly", "matomo.cloud",
            "newrelic.com", "nr-data.net", "branch.io", "app.link", "adjust.com",
            "kochava.com", "appsflyer.com", "onesignal.com", "clarity.ms", "bing.com/clarity",
            "scorecardresearch.com", "quantserve.com", "agkn.com", "navegg.com", "rfihub.com",
            "tapad.com", "mediavine.com", "evgnet.com", "dpm.demdex.net", "demdex.net",
            "id5-sync.com", "adserver.adtechus.com", "eu1.adserver.adtechus.com"
        )
    }
}
