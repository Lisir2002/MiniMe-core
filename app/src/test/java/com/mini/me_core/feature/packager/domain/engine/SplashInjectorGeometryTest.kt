package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * centerCrop 纯几何函数测试：竖图/横图/方图/极端宽高比。
 *
 * 不变量：裁剪矩形落在源图范围内、居中、裁剪区域宽高比等于目标画布宽高比（cover）。
 */
class SplashInjectorGeometryTest {

    private val canvasW = 1080
    private val canvasH = 1920
    private val targetRatio = canvasW.toDouble() / canvasH

    private fun assertInvariant(srcW: Int, srcH: Int) {
        val c = SplashInjector.centerCrop(srcW, srcH, canvasW, canvasH)
        assertTrue("left>=0", c.srcLeft >= 0)
        assertTrue("top>=0", c.srcTop >= 0)
        assertTrue("right<=srcW", c.srcRight <= srcW)
        assertTrue("bottom<=srcH", c.srcBottom <= srcH)
        assertTrue("width>0", c.width > 0)
        assertTrue("height>0", c.height > 0)
        // 居中（允许 1px 取整误差）
        assertTrue("水平居中", kotlin.math.abs(c.srcLeft - (srcW - c.width) / 2) <= 1)
        assertTrue("垂直居中", kotlin.math.abs(c.srcTop - (srcH - c.height) / 2) <= 1)
        // 裁剪宽高比 ≈ 目标宽高比（cover）
        val ratio = c.width.toDouble() / c.height
        assertEquals("裁剪比应≈cover目标比", targetRatio, ratio, 0.02)
    }

    @Test
    fun `square source`() = assertInvariant(1000, 1000)

    @Test
    fun `portrait source`() = assertInvariant(500, 1000)

    @Test
    fun `landscape source`() = assertInvariant(2000, 1000)

    @Test
    fun `extreme wide source`() = assertInvariant(4000, 100)

    @Test
    fun `extreme tall source`() = assertInvariant(100, 4000)

    @Test
    fun `tiny source does not underflow`() {
        val c = SplashInjector.centerCrop(1, 1, canvasW, canvasH)
        assertEquals(0, c.srcLeft)
        assertEquals(0, c.srcTop)
        assertEquals(1, c.srcRight)
        assertEquals(1, c.srcBottom)
    }

    @Test
    fun `exact canvas aspect uses whole source`() {
        // 源图本身就是 1080x1920 比例，应整图采用
        val c = SplashInjector.centerCrop(1080, 1920, canvasW, canvasH)
        assertEquals(0, c.srcLeft)
        assertEquals(0, c.srcTop)
        assertEquals(1080, c.srcRight)
        assertEquals(1920, c.srcBottom)
    }
}
