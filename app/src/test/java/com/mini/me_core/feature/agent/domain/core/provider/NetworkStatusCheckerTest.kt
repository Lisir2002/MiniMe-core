package com.mini.me_core.feature.agent.domain.core.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线预检纯判定测试（顶层函数 [isOnlineFromCaps]）。
 *
 * 判定规则（宁漏勿误）：有 INTERNET 能力且至少一种 transport 才判在线；
 * NET_CAPABILITY_VALIDATED 不参与硬判（国内设备 Google 校验不可达常为 false 但网络可用）。
 */
class NetworkStatusCheckerTest {

    @Test
    fun wifiWithInternet_isOnline() {
        assertTrue(isOnlineFromCaps(hasInternet = true, wifi = true, cellular = false, ethernet = false, vpn = false))
    }

    @Test
    fun cellularWithInternet_isOnline() {
        assertTrue(isOnlineFromCaps(hasInternet = true, wifi = false, cellular = true, ethernet = false, vpn = false))
    }

    @Test
    fun ethernetWithInternet_isOnline() {
        assertTrue(isOnlineFromCaps(hasInternet = true, wifi = false, cellular = false, ethernet = true, vpn = false))
    }

    @Test
    fun vpnWithInternet_isOnline() {
        assertTrue(isOnlineFromCaps(hasInternet = true, wifi = false, cellular = false, ethernet = false, vpn = true))
    }

    @Test
    fun noTransport_isOffline() {
        // 飞行模式 / 无 WiFi 无数据：无任何 transport → 离线
        assertFalse(isOnlineFromCaps(hasInternet = true, wifi = false, cellular = false, ethernet = false, vpn = false))
    }

    @Test
    fun noInternetCapability_isOffline() {
        // 有 transport 但无 INTERNET 能力（罕见：仅限局域网热点等）→ 离线
        assertFalse(isOnlineFromCaps(hasInternet = false, wifi = true, cellular = false, ethernet = false, vpn = false))
    }

    @Test
    fun nothingAtAll_isOffline() {
        assertFalse(isOnlineFromCaps(hasInternet = false, wifi = false, cellular = false, ethernet = false, vpn = false))
    }

    @Test
    fun validatedFalse_butTransportPresent_isStillOnline() {
        // 回归钉住：VALIDATED=false 的国内 WiFi 不得被误判离线——
        // 纯函数签名里没有 validated 参数，本条仅文档化该决策；
        // 真实 caps 路径见 NetworkStatusChecker.isOnline 的 KDoc。
        assertTrue(isOnlineFromCaps(hasInternet = true, wifi = true, cellular = false, ethernet = false, vpn = false))
    }
}
