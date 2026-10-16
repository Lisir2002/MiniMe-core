package com.mini.me_core.feature.browser.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AdBlocker.isLocalOrPrivateHost] 纯函数单测（JVM，无 Android 依赖）。
 *
 * 背景：用户本地部署的页面（localhost / 私网 IP / .local mDNS）引用任何资源
 * （背景图、字体、接口）都不应被公共广告名单裁决——这类误杀表现为「其他
 * 浏览器显示正常、内置浏览器资源缺失」。该函数是 [AdBlocker.shouldBlock]
 * 本地豁免的判定核心，边界按 RFC1918 / RFC3927 / RFC4193 链路本地语义覆盖。
 */
class AdBlockerLocalHostTest {

    // ── 主机名 ───────────────────────────

    @Test
    fun `localhost variants are local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("localhost"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("LOCALHOST"))
        assertTrue(AdBlocker.isLocalOrPrivateHost(" localhost "))
        assertTrue(AdBlocker.isLocalOrPrivateHost("app.localhost"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("a.b.localhost"))
    }

    @Test
    fun `mDNS dot-local hosts are local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("nas.local"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("my-printer.local"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("NAS.LOCAL"))
    }

    @Test
    fun `public domains are not local`() {
        assertFalse(AdBlocker.isLocalOrPrivateHost("example.com"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("localhost.com")) // 后缀不是精确 .localhost
        assertFalse(AdBlocker.isLocalOrPrivateHost("notlocalhost"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("x.local.com")) // .local 后还有公网后缀
        assertFalse(AdBlocker.isLocalOrPrivateHost("evil.localhost.attacker.com"))
        assertFalse(AdBlocker.isLocalOrPrivateHost(""))
        assertFalse(AdBlocker.isLocalOrPrivateHost("   "))
    }

    // ── IPv4 私网 / 回环 / 链路本地 ───────────────────────────

    @Test
    fun `IPv4 loopback range is local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("127.0.0.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("127.1.2.3"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("127.255.255.255"))
    }

    @Test
    fun `IPv4 private ranges are local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("10.0.0.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("10.255.255.255"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("172.16.0.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("172.31.255.255"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("192.168.0.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("192.168.1.5"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("169.254.1.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("0.0.0.0"))
    }

    @Test
    fun `IPv4 addresses outside private ranges are not local`() {
        assertFalse(AdBlocker.isLocalOrPrivateHost("8.8.8.8"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("1.1.1.1"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("100.64.0.1")) // CGNAT，非私网
        assertFalse(AdBlocker.isLocalOrPrivateHost("172.15.0.1")) // 172.16/12 下界外
        assertFalse(AdBlocker.isLocalOrPrivateHost("172.32.0.1")) // 上界外
        assertFalse(AdBlocker.isLocalOrPrivateHost("11.0.0.1"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("192.169.0.1"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("169.253.0.1"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("126.0.0.1"))
    }

    @Test
    fun `malformed IPv4-like hosts are not local`() {
        assertFalse(AdBlocker.isLocalOrPrivateHost("127.0.0")) // 三段
        assertFalse(AdBlocker.isLocalOrPrivateHost("127.0.0.1.5")) // 五段
        assertFalse(AdBlocker.isLocalOrPrivateHost("127.0.0.a")) // 非数字段
        assertFalse(AdBlocker.isLocalOrPrivateHost("127.0.0.")) // 尾点
        assertFalse(AdBlocker.isLocalOrPrivateHost("1.2.3.4.example.com")) // 域名以数字段开头
    }

    // ── IPv6 回环 / 链路本地 ───────────────────────────

    @Test
    fun `IPv6 loopback and link-local are local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("::1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("[::1]"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("0:0:0:0:0:0:0:1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("fe80::1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("[fe80::1]"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("FE80::AB"))
    }

    @Test
    fun `IPv6 unique local addresses are local`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("fc00::1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("fd00::1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("fd12:3456:789a::1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("[fdff::abcd]"))
    }

    @Test
    fun `IPv4-mapped IPv6 follows the embedded IPv4`() {
        assertTrue(AdBlocker.isLocalOrPrivateHost("::ffff:127.0.0.1"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("::ffff:10.1.2.3"))
        assertTrue(AdBlocker.isLocalOrPrivateHost("[::ffff:192.168.1.1]"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("::ffff:8.8.8.8"))
    }

    @Test
    fun `IPv6 unspecified address is local`() {
        // 与 0.0.0.0 同语义：未指定地址按本地放行
        assertTrue(AdBlocker.isLocalOrPrivateHost("::"))
    }

    @Test
    fun `domains with IPv6 prefix lookalikes are not local`() {
        // 防误判：ULA/链路本地判定要求含冒号，"fc"/"fd"/"fe80" 开头的公网域名必须放行裁决
        assertFalse(AdBlocker.isLocalOrPrivateHost("fcbook.com"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("fdhost.org"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("fe80.example.com"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("fc00.cn")) // 无冒号，不是 IPv6 字面量
    }

    @Test
    fun `public IPv6 addresses are not local`() {
        assertFalse(AdBlocker.isLocalOrPrivateHost("2001:db8::1"))
        assertFalse(AdBlocker.isLocalOrPrivateHost("[2001:db8::1]"))
    }
}
