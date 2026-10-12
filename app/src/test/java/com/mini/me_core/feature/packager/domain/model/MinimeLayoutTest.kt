package com.mini.me_core.feature.packager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `.minime/` 布局与旧目录迁移纯逻辑测试。
 */
class MinimeLayoutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `new layout has no pending migration`() {
        val root = tmp.newFolder()
        MinimeLayout.minimeDir(root).mkdirs()
        MinimeLayout.configFile(root).writeText("{}")
        assertTrue("已是 .minime 布局时不应有待迁移", MinimeLayout.pendingMigrations(root).isEmpty())
    }

    @Test
    fun `legacy root config json is detected`() {
        val root = tmp.newFolder()
        File(root, "config.json").writeText("{}")
        File(root, "icon.png").writeBytes(byteArrayOf(1, 2, 3))
        val pending = MinimeLayout.pendingMigrations(root)
        assertEquals("应列出 config.json 与 icon.png 两个旧文件", 2, pending.size)
    }

    @Test
    fun `migrate moves legacy files into minime dir`() {
        val root = tmp.newFolder()
        File(root, "config.json").writeText("{}")
        File(root, "splash.png").writeBytes(byteArrayOf(9))
        val moved = MinimeLayout.migrate(root)
        assertEquals(2, moved)
        assertTrue(MinimeLayout.configFile(root).isFile)
        assertTrue(MinimeLayout.splashImageFile(root).isFile)
        assertTrue(MinimeLayout.buildDir(root).isDirectory)
    }

    @Test
    fun `hidden detection`() {
        assertTrue(MinimeLayout.isHidden(".minime"))
        assertFalse(MinimeLayout.isHidden("www"))
    }
}
