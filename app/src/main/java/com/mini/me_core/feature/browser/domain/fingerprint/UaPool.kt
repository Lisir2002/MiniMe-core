package com.mini.me_core.feature.browser.domain.fingerprint

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * User-Agent 池。
 *
 * 维护主流浏览器/OS/设备的真实 UA 列表，支持随机选择、按条件筛选、
 * 按市场份额加权随机。所有 UA 均来自真实浏览器，非伪造。
 */
object UaPool {

    /**
     * UA 条目。
     */
    @Serializable
    data class UaEntry(
        /** 完整 UA 字符串 */
        val ua: String,
        /** 浏览器类型：chrome / safari / firefox / edge */
        val browser: String,
        /** 浏览器主版本号 */
        val majorVersion: Int,
        /** 操作系统：windows / macos / linux / android / ios */
        val os: String,
        /** 设备类型：desktop / mobile / tablet */
        val deviceType: String,
        /** 市场份额（0-1），用于加权随机 */
        val marketShare: Float = 0.05f,
    )

    /**
     * 主流桌面 Chrome UA（Windows/macOS/Linux）。
     * 版本覆盖 Chrome 115-126，均为真实 UA。
     */
    private val CHROME_DESKTOP = listOf(
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36", "chrome", 126, "windows", "desktop", 0.08f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "chrome", 125, "windows", "desktop", 0.07f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36", "chrome", 124, "windows", "desktop", 0.06f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36", "chrome", 126, "macos", "desktop", 0.05f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "chrome", 125, "macos", "desktop", 0.04f),
        UaEntry("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36", "chrome", 126, "linux", "desktop", 0.02f),
        UaEntry("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "chrome", 125, "linux", "desktop", 0.02f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", "chrome", 120, "windows", "desktop", 0.03f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36", "chrome", 118, "windows", "desktop", 0.02f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", "chrome", 120, "macos", "desktop", 0.02f),
    )

    /**
     * 主流桌面 Safari UA（macOS）。
     */
    private val SAFARI_DESKTOP = listOf(
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15", "safari", 17, "macos", "desktop", 0.06f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15", "safari", 17, "macos", "desktop", 0.05f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Safari/605.1.15", "safari", 16, "macos", "desktop", 0.03f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15", "safari", 16, "macos", "desktop", 0.02f),
    )

    /**
     * 主流桌面 Firefox UA（Windows/macOS/Linux）。
     */
    private val FIREFOX_DESKTOP = listOf(
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0", "firefox", 127, "windows", "desktop", 0.03f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0", "firefox", 126, "windows", "desktop", 0.02f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:127.0) Gecko/20100101 Firefox/127.0", "firefox", 127, "macos", "desktop", 0.02f),
        UaEntry("Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0", "firefox", 127, "linux", "desktop", 0.01f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:115.0) Gecko/20100101 Firefox/115.0", "firefox", 115, "windows", "desktop", 0.02f),
    )

    /**
     * 主流桌面 Edge UA（Windows/macOS）。
     */
    private val EDGE_DESKTOP = listOf(
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0", "edge", 126, "windows", "desktop", 0.04f),
        UaEntry("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0", "edge", 125, "windows", "desktop", 0.03f),
        UaEntry("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0", "edge", 126, "macos", "desktop", 0.01f),
    )

    /**
     * 移动端 Chrome UA（Android）。
     */
    private val CHROME_MOBILE = listOf(
        UaEntry("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36", "chrome", 126, "android", "mobile", 0.03f),
        UaEntry("Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.71 Mobile Safari/537.36", "chrome", 126, "android", "mobile", 0.03f),
        UaEntry("Mozilla/5.0 (Linux; Android 13; Mi 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.6422.113 Mobile Safari/537.36", "chrome", 125, "android", "mobile", 0.02f),
        UaEntry("Mozilla/5.0 (Linux; Android 12; OPPO Find X5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.230 Mobile Safari/537.36", "chrome", 120, "android", "mobile", 0.02f),
    )

    /**
     * 移动端 Safari UA（iOS iPhone）。
     */
    private val SAFARI_MOBILE = listOf(
        UaEntry("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", "safari", 17, "ios", "mobile", 0.04f),
        UaEntry("Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1", "safari", 17, "ios", "mobile", 0.03f),
        UaEntry("Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1", "safari", 16, "ios", "mobile", 0.02f),
    )

    /** 全部 UA 列表（合并所有分类） */
    val all: List<UaEntry> = CHROME_DESKTOP + SAFARI_DESKTOP + FIREFOX_DESKTOP + EDGE_DESKTOP + CHROME_MOBILE + SAFARI_MOBILE

    /** 桌面端 UA */
    val desktop: List<UaEntry> = all.filter { it.deviceType == "desktop" }

    /** 移动端 UA */
    val mobile: List<UaEntry> = all.filter { it.deviceType == "mobile" }

    /**
     * 按条件筛选 UA。
     *
     * @param browser 浏览器类型（null=不限）
     * @param os 操作系统（null=不限）
     * @param deviceType 设备类型（null=不限）
     * @return 符合条件的 UA 列表
     */
    fun filter(
        browser: String? = null,
        os: String? = null,
        deviceType: String? = null,
    ): List<UaEntry> = all.filter { entry ->
        (browser == null || entry.browser == browser) &&
        (os == null || entry.os == os) &&
        (deviceType == null || entry.deviceType == deviceType)
    }

    /**
     * 随机选择一个 UA（均匀随机）。
     *
     * @param browser 浏览器类型（null=不限）
     * @param os 操作系统（null=不限）
     * @param deviceType 设备类型（null=不限）
     * @return 随机选中的 UA，如无符合条件的返回 null
     */
    fun random(
        browser: String? = null,
        os: String? = null,
        deviceType: String? = null,
    ): UaEntry? {
        val candidates = filter(browser, os, deviceType)
        return if (candidates.isEmpty()) null else candidates.random()
    }

    /**
     * 按市场份额加权随机选择 UA（市场份额越高被选中概率越大）。
     *
     * @param browser 浏览器类型（null=不限）
     * @param os 操作系统（null=不限）
     * @param deviceType 设备类型（null=不限）
     * @return 加权随机选中的 UA，如无符合条件的返回 null
     */
    fun weightedRandom(
        browser: String? = null,
        os: String? = null,
        deviceType: String? = null,
    ): UaEntry? {
        val candidates = filter(browser, os, deviceType)
        if (candidates.isEmpty()) return null

        val totalWeight = candidates.sumOf { it.marketShare.toDouble() }
        if (totalWeight <= 0) return candidates.random()

        var r = Random.nextDouble() * totalWeight
        for (entry in candidates) {
            r -= entry.marketShare
            if (r <= 0) return entry
        }
        return candidates.last()
    }

    /**
     * 根据 UA 字符串推断浏览器类型。
     */
    fun inferBrowser(ua: String): String = when {
        ua.contains("Edg/") -> "edge"
        ua.contains("Firefox/") -> "firefox"
        ua.contains("Safari/") && ua.contains("Version/") -> "safari"
        ua.contains("Chrome/") -> "chrome"
        else -> "unknown"
    }

    /**
     * 根据 UA 字符串推断操作系统。
     */
    fun inferOs(ua: String): String = when {
        ua.contains("Windows NT") -> "windows"
        ua.contains("Mac OS X") || ua.contains("Macintosh") -> "macos"
        ua.contains("Android") -> "android"
        ua.contains("iPhone") || ua.contains("iPad") -> "ios"
        ua.contains("Linux") -> "linux"
        else -> "unknown"
    }

    /**
     * 根据 UA 推断 navigator.platform 值。
     */
    fun inferPlatform(ua: String): String = when (inferOs(ua)) {
        "windows" -> "Win32"
        "macos" -> "MacIntel"
        "linux" -> "Linux x86_64"
        "android" -> "Linux armv8l"
        "ios" -> if (ua.contains("iPad")) "iPad" else "iPhone"
        else -> "Win32"
    }
}
