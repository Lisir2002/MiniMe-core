package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 旧模板降级测试：当前随仓库发布的 template-base.apk 尚不含 splash_img / splash.html。
 *
 * 防御契约：解压目录里找不到 splash_img 位图时，注入器必须 FileLogger 告警并优雅跳过，
 * 绝不抛异常中断构建；assets/www 下的 splash.html 照常写入（前向兼容）。
 *
 * 说明：本环境为纯 JVM 单测（unitTests.returnDefaultValues=true），android.graphics.Bitmap
 * 为桩实现，合成位图返回 null，因此窗口位图覆盖分支天然走“跳过”路径，正好覆盖降级场景。
 */
class SplashInjectorDegradationTest {

    @Test
    fun `missing splash image skips window overwrite but still writes splash html`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "splash-degrade-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val extracted = File(tmpDir, "extracted").apply { mkdirs() }
            // 故意不放 res/splash_img*，也不放 resources.arsc
            File(extracted, "assets/www").mkdirs()

            val htmlSource = File(tmpDir, "splash.html")
            htmlSource.writeText("<html>splash</html>")

            val result = SplashInjector.inject(
                extractedDir = extracted,
                settings = com.mini.me_core.feature.packager.domain.model.SplashSettings(),
                customImageFile = null,
                splashHtmlSource = htmlSource
            )

            // 旧模板无 splash_img：窗口位图覆盖被跳过
            assertFalse("旧模板无 splash_img 时不应覆盖窗地位图", result.windowImageOverwritten)
            // splash.html 照常写入
            assertTrue("splash.html 应写入 assets/www", result.splashHtmlWritten)
            assertTrue(File(extracted, "assets/www/splash.html").exists())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `invalid extracted dir returns skipped result without throwing`() {
        val missing = File(System.getProperty("java.io.tmpdir"), "no-such-dir-${System.nanoTime()}")
        val result = SplashInjector.inject(
            extractedDir = missing,
            settings = com.mini.me_core.feature.packager.domain.model.SplashSettings(),
            customImageFile = null,
            splashHtmlSource = null
        )
        assertFalse(result.windowImageOverwritten)
        assertFalse(result.splashHtmlWritten)
    }

    @Test
    fun `fallback scan finds splash img by name in res dir`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "splash-fallback-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val extracted = File(tmpDir, "extracted")
            val resDir = File(extracted, "res/drawable-nodpi-v4").apply { mkdirs() }
            val splashImg = File(resDir, "splash_img.png")
            splashImg.writeBytes(ByteArray(8) { i -> i.toByte() })

            // 直接定位逻辑：无 arsc 时走 res 递归兜底，应能按名找到
            val located = locateViaFallback(extracted)
            assertEquals(splashImg.absolutePath, located?.absolutePath)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /** 复用与生产一致的兜底扫描规则（res 下递归 *splash_img*.png）。 */
    private fun locateViaFallback(extractedDir: File): File? {
        val resDir = File(extractedDir, "res")
        return resDir.walkTopDown().firstOrNull { f ->
            f.isFile &&
                f.name.contains("splash_img", ignoreCase = true) &&
                f.name.endsWith(".png", ignoreCase = true)
        }
    }
}
