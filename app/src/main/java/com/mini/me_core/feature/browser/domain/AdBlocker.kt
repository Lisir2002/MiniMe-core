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
     *
     * 本地/私有部署地址（localhost、.local、回环与私网段）一律放行：
     * 用户在自己部署的页面上引用任何资源（背景图、字体、接口）都不应被
     * 公共广告名单裁决——这类误杀表现为「其他浏览器显示正常、内置浏览器
     * 资源缺失」，且本地页面本无广告投放动机。
     */
    fun shouldBlock(url: String): Boolean {
        val host = runCatching {
            android.net.Uri.parse(url).host?.lowercase()
        }.getOrNull() ?: return false

        // 本地/私有部署地址免拦截
        if (isLocalOrPrivateHost(host)) return false

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
            "kochava.com", "appsflyer.com", "onesignal.com", "clarity.ms",
            "scorecardresearch.com", "quantserve.com", "agkn.com", "navegg.com", "rfihub.com",
            "tapad.com", "mediavine.com", "evgnet.com", "dpm.demdex.net", "demdex.net",
            "id5-sync.com", "adserver.adtechus.com", "eu1.adserver.adtechus.com"
        )

        /**
         * 判断 host 是否为本地/私有部署地址（放行，不进广告名单裁决）：
         * - `localhost` / `*.localhost` / `*.local`（mDNS）
         * - IPv4 回环 127.0.0.0/8、私网 10.0.0.0/8 与 172.16.0.0/12、
         *   192.168.0.0/16、链路本地 169.254.0.0/16、未指定 0.0.0.0
         * - IPv6 回环 `::1`/`::`、链路本地 `fe80::/10`、唯一本地地址 `fc00::/7`（ULA）、
         *   IPv4-mapped `::ffff:a.b.c.d`（按内嵌 IPv4 判定）
         *
         * 纯字符串函数（不依赖 android.net.Uri），可在 JVM 单测直接验证。
         */
        fun isLocalOrPrivateHost(host: String): Boolean {
            val h = host.trim().lowercase()
            if (h.isEmpty()) return false
            return isLocalHostname(h) || isLocalIpv6(h) || isLocalIpv4(h)
        }

        private fun isLocalHostname(h: String): Boolean =
            h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")

        /** IPv6 字面量（可带方括号，如 `[::1]`）中的回环 / 链路本地 / 私网地址。 */
        private fun isLocalIpv6(h: String): Boolean {
            val v6 = if (h.startsWith("[") && h.endsWith("]")) h.substring(1, h.length - 1) else h
            if (v6.isEmpty()) return false
            if (isLoopbackV6(v6) || isLinkLocalV6(v6)) return true
            return isUniqueLocalV6(v6) || isIpv4MappedLocalV6(v6)
        }

        /** 回环 `::1`（含全写形式）与未指定 `::`。 */
        private fun isLoopbackV6(v6: String): Boolean =
            v6 == "::1" || v6 == "0:0:0:0:0:0:0:1" || v6 == "::"

        /** 链路本地 `fe80::/10`。 */
        private fun isLinkLocalV6(v6: String): Boolean = v6.startsWith("fe80:")

        /**
         * 唯一本地地址 `fc00::/7`（ULA，IPv6 私网）。
         * 必须含冒号，防止 "fcbook.com" 这类以 fc/fd 开头的公网域名被误判。
         */
        private fun isUniqueLocalV6(v6: String): Boolean {
            val firstGroup = v6.substringBefore(':')
            return v6.contains(':') && (firstGroup.startsWith("fc") || firstGroup.startsWith("fd"))
        }

        /** IPv4-mapped（`::ffff:a.b.c.d`）按内嵌 IPv4 判定。 */
        private fun isIpv4MappedLocalV6(v6: String): Boolean {
            val mapped = v6.removePrefix("::ffff:")
            return mapped != v6 && isLocalIpv4(mapped)
        }

        /** IPv4 字面量中的回环 / 私网 / 链路本地段（四段全数字才判定，域名不漏判）。 */
        private fun isLocalIpv4(h: String): Boolean {
            val parts = h.split(".")
            if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) {
                return false
            }
            val a = parts[0].toIntOrNull() ?: return false
            val b = parts[1].toIntOrNull() ?: return false
            return when (a) {
                0, 127 -> true // 未指定 / 回环 127.0.0.0/8
                10 -> true // 10.0.0.0/8
                172 -> b in 16..31 // 172.16.0.0/12
                192 -> b == 168 // 192.168.0.0/16
                169 -> b == 254 // 169.254.0.0/16
                else -> false
            }
        }
    }
}
