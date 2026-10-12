package com.mini.me_core.feature.packager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动页设置 → config.json splash 段契约映射（纯函数）测试。
 *
 * 守护模板侧契约：duration 钳制在 500..5000、过渡效果白名单、背景色规整、
 * 有无自定义图决定 image 字段（"splash.png" 或 null）。
 */
class SplashConfigMapperTest {

    @Test
    fun `duration is clamped into 500 to 5000`() {
        assertEquals(500, SplashSettings(delayMs = 10).toSplashConfigPayload(false).durationMs)
        assertEquals(5000, SplashSettings(delayMs = 99999).toSplashConfigPayload(false).durationMs)
        assertEquals(1500, SplashSettings(delayMs = 1500).toSplashConfigPayload(false).durationMs)
    }

    @Test
    fun `image is splash png only when custom image present`() {
        assertEquals(
            "splash.png",
            SplashSettings(customImagePath = "/x/splash.png").toSplashConfigPayload(true).image
        )
        assertNull(SplashSettings(customImagePath = null).toSplashConfigPayload(false).image)
        assertNull(SplashSettings().toSplashConfigPayload(false).image)
    }

    @Test
    fun `invalid transition falls back to fade`() {
        assertEquals("fade", SplashSettings(transition = "weird").toSplashConfigPayload(false).transition)
        assertEquals("fade", SplashSettings(transition = "").toSplashConfigPayload(false).transition)
    }

    @Test
    fun `supported transitions pass through lowercased`() {
        listOf("fade", "zoom", "slide", "dissolve").forEach { t ->
            assertEquals(t, SplashSettings(transition = t).toSplashConfigPayload(false).transition)
        }
        assertEquals("zoom", SplashSettings(transition = "ZOOM").toSplashConfigPayload(false).transition)
    }

    @Test
    fun `background color is normalized`() {
        assertEquals("#FFFFFF", SplashSettings(backgroundColor = "#ffffff").toSplashConfigPayload(false).backgroundColor)
        assertEquals("#AABBCC", SplashSettings(backgroundColor = "aabbcc").toSplashConfigPayload(false).backgroundColor)
        assertEquals("#FFFFFF", SplashSettings(backgroundColor = "#FFF").toSplashConfigPayload(false).backgroundColor)
        // 非法色回退白色
        assertEquals("#FFFFFF", SplashSettings(backgroundColor = "not-a-color").toSplashConfigPayload(false).backgroundColor)
    }

    @Test
    fun `autoHide is always true and skipOnTap passthrough`() {
        assertTrue(SplashSettings(skipOnTap = true).toSplashConfigPayload(false).autoHide)
        assertTrue(SplashSettings(skipOnTap = true).toSplashConfigPayload(false).skipOnTap)
        assertFalse(SplashSettings(skipOnTap = false).toSplashConfigPayload(false).skipOnTap)
    }
}
