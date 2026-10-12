package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * arsc 主题注入纯函数测试：直接以模板 resources.arsc 二进制为夹具，验证
 *  1. findSplashImgResId 能解析出 splash_img 位图资源 ID；
 *  2. patchWindowBackground 能在原地把 Theme.AppTemplate 的 windowBackground
 *     从原引用（layer-list @drawable/splash_bg）改写为 splash_img 位图引用，
 *     且不改 arsc 文件大小、不破坏其它 style 映射。
 *
 * 夹具 template.arsc 与随仓库发布的 template-base.apk 内 resources.arsc 一致。
 */
class SplashInjectorArscThemeTest {

    private fun loadFixture(): ByteArray =
        SplashInjectorArscThemeTest::class.java.classLoader
            .getResourceAsStream("splash/template.arsc")!!
            .readBytes()

    @Test
    fun `findSplashImgResId resolves drawable resource id`() {
        val data = loadFixture()
        val id = SplashInjector.findSplashImgResId(data)
        assertNotNull("splash_img 资源 ID 应可解析", id)
        // 0x7f(package) 04(drawable) 0015(entry 21)
        assertEquals(0x7f040015, id)
    }

    @Test
    fun `patchWindowBackground rewrites windowBackground in place`() {
        val data = loadFixture()
        val originalSize = data.size
        val splashId = SplashInjector.findSplashImgResId(data)!!

        // 记录 patch 前 windowBackground 的取值（通过反解确认是 layer-list）
        val before = readWindowBackground(data)
        // 模板中 windowBackground 原本指向 splash_bg layer-list (0x7f040014)
        assertEquals(0x7f040014, before)

        val patched = SplashInjector.patchWindowBackground(data, splashId)
        assertTrue("应命中并改写 windowBackground", patched)
        assertEquals("原地改写不应改变 arsc 大小", originalSize, data.size)

        val after = readWindowBackground(data)
        assertEquals("windowBackground 应指向 splash_img 位图", splashId, after)
        assertNotEquals(before, after)
    }

    @Test
    fun `patchWindowBackground does not touch other style entries`() {
        val data = loadFixture()
        val splashId = SplashInjector.findSplashImgResId(data)!!
        // 仅对比 windowBackground(0x01010054) 之外的映射，确认原地改写不波及其它条目
        val othersBefore = readThemeMapValues(data).filterKeys { it != 0x01010054 }
        SplashInjector.patchWindowBackground(data, splashId)
        val othersAfter = readThemeMapValues(data).filterKeys { it != 0x01010054 }
        assertEquals("除 windowBackground 外的映射应保持不变", othersBefore, othersAfter)
    }

    @Test
    fun `findSplashImgResId returns null for corrupted arsc`() {
        val garbage = ByteArray(64) { 0x77 }
        assertEquals(null, SplashInjector.findSplashImgResId(garbage))
    }

    @Test
    fun `findResIdByKey resolves splash_background color id`() {
        val data = loadFixture()
        val id = SplashInjector.findResIdByKey(data, "color", "splash_background")
        assertNotNull("splash_background 颜色资源 ID 应可解析", id)
        // color 类型 idx=2，splash_background 为其首个非空 entry
        assertEquals(0x7f02000a, id)
    }

    @Test
    fun `patchColorValue rewrites splash_background color in place`() {
        val data = loadFixture()
        val originalSize = data.size
        val newColor = 0xFF112233.toInt()
        val patched = SplashInjector.patchColorValue(data, "splash_background", newColor)
        assertTrue("应命中并改写 splash_background 颜色", patched)
        assertEquals("原地改写不应改变 arsc 大小", originalSize, data.size)
        assertEquals("颜色值应被改写为新 ARGB", newColor, readColorValue(data, "splash_background"))
    }

    // === 以下为测试侧最小反解：仅用于断言，与生产解析逻辑独立 ===

    private fun u16(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)

    private fun u32(d: ByteArray, o: Int): Int =
        (d[o].toInt() and 0xFF) or
            ((d[o + 1].toInt() and 0xFF) shl 8) or
            ((d[o + 2].toInt() and 0xFF) shl 16) or
            ((d[o + 3].toInt() and 0xFF) shl 24)

    private fun parseSp(d: ByteArray, start: Int): List<String> {
        val hs = u16(d, start + 2)
        val cnt = u32(d, start + 8)
        val flags = u32(d, start + 16)
        val ss = u32(d, start + 20)
        val is8 = (flags and 0x100) != 0
        val out = ArrayList<String>()
        for (i in 0 until cnt) {
            var pos = start + ss + u32(d, start + hs + i * 4)
            if (is8) {
                var n = d[pos].toInt() and 0xFF; pos++
                if (n and 0x80 != 0) { n = ((n and 0x7F) shl 8) or (d[pos].toInt() and 0xFF); pos++ }
                var b = d[pos].toInt() and 0xFF; pos++
                if (b and 0x80 != 0) { b = ((b and 0x7F) shl 8) or (d[pos].toInt() and 0xFF); pos++ }
                out.add(String(d, pos, b, Charsets.UTF_8))
            } else {
                var n = u16(d, pos); pos += 2
                if (n and 0x8000 != 0) { n = ((n and 0x7FFF) shl 16) or u16(d, pos); pos += 2 }
                out.add(String(d, pos, n * 2, Charsets.UTF_16LE))
            }
        }
        return out
    }

    /** 返回 Theme.AppTemplate 中 windowBackground(0x01010054) 的 data 值。 */
    private fun readWindowBackground(d: ByteArray): Int = readThemeMapValues(d)[0x01010054]!!

    /** 返回 Theme.AppTemplate 全部 map key -> data。 */
    private fun readThemeMapValues(d: ByteArray): Map<Int, Int> {
        val pkg = 12 + u32(d, 12 + 4)
        val tsOff = u32(d, pkg + 12 + 256)
        val ksOff = u32(d, pkg + 12 + 256 + 8)
        val typeStrs = parseSp(d, pkg + tsOff)
        val keyStrs = parseSp(d, pkg + ksOff)
        val styleIdx = typeStrs.indexOf("style") + 1
        var cur = pkg + u16(d, pkg + 2)
        val end = pkg + u32(d, pkg + 4)
        while (cur + 8 <= end) {
            val ct = u16(d, cur)
            val chs = u16(d, cur + 2)
            val csz = u32(d, cur + 4)
            if (ct == 0x0201 && (d[cur + 8].toInt() and 0xFF) == styleIdx) {
                val ec = u32(d, cur + 12)
                val es = u32(d, cur + 16)
                val ob = cur + chs
                for (i in 0 until ec) {
                    val eoff = u32(d, ob + i * 4)
                    if (eoff == 0 || eoff == -1) continue
                    val ep = cur + es + eoff
                    val ef = u16(d, ep + 2)
                    if ((ef and 0x1) == 0) continue
                    val ek = u32(d, ep + 4)
                    if (keyStrs[ek] != "Theme.AppTemplate") continue
                    val cnt = u32(d, ep + 12)
                    val map = HashMap<Int, Int>()
                    var bp = ep + 16
                    repeat(cnt) {
                        val mk = u32(d, bp)
                        val mdata = u32(d, bp + 8)
                        map[mk] = mdata
                        bp += 12
                    }
                    return map
                }
            }
            cur += csz
        }
        throw IllegalStateException("Theme.AppTemplate not found")
    }

    /** 读取 color 类型下指定 key 的 Res_value.data（测试侧反解）。 */
    private fun readColorValue(d: ByteArray, key: String): Int {
        val pkg = 12 + u32(d, 12 + 4)
        val tsOff = u32(d, pkg + 12 + 256)
        val ksOff = u32(d, pkg + 12 + 256 + 8)
        val typeStrs = parseSp(d, pkg + tsOff)
        val keyStrs = parseSp(d, pkg + ksOff)
        val colorIdx = typeStrs.indexOf("color") + 1
        var cur = pkg + u16(d, pkg + 2)
        val end = pkg + u32(d, pkg + 4)
        while (cur + 8 <= end) {
            val ct = u16(d, cur)
            val chs = u16(d, cur + 2)
            val csz = u32(d, cur + 4)
            if (ct == 0x0201 && (d[cur + 8].toInt() and 0xFF) == colorIdx) {
                val ec = u32(d, cur + 12)
                val es = u32(d, cur + 16)
                val ob = cur + chs
                for (i in 0 until ec) {
                    val eoff = u32(d, ob + i * 4)
                    if (eoff == 0 || eoff == -1) continue
                    val ep = cur + es + eoff
                    val ef = u16(d, ep + 2)
                    if ((ef and 0x1) != 0) continue
                    val ek = u32(d, ep + 4)
                    if (keyStrs[ek] != key) continue
                    // ResTable_entry(8) 后紧跟 Res_value: size(2)@+8,res0@+10,dataType@+11,data(4)@+12
                    return u32(d, ep + 12)
                }
            }
            cur += csz
        }
        throw IllegalStateException("$key color not found")
    }
}
