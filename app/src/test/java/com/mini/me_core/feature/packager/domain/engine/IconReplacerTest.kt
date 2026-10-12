package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * 图标替换器 arsc 字符串池重定向回归测试。
 *
 * 守护「矢量前景图标必须通过 arsc 路径重定向到 .png，而非用 PNG 字节覆盖 .xml」
 * 这条底线：Android 按扩展名解析资源，覆盖 .xml 会导致解析失败回退默认图标。
 */
class IconReplacerTest {

    private val templateApk =
        File("/home/user/projects/MiniMe-core/app/src/main/assets/packager/template-base.apk")

    /** 从模版 APK 提取 resources.arsc 到临时文件，模版缺失时返回 null。 */
    private fun extractTemplateArsc(tmpDir: File): File? {
        if (!templateApk.exists()) return null
        val arscFile = File(tmpDir, "resources.arsc")
        ZipFile(templateApk).use { zip ->
            val entry = zip.getEntry("resources.arsc") ?: return null
            zip.getInputStream(entry).use { input ->
                arscFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return arscFile.takeIf { it.exists() && it.length() > 0 }
    }

    @Test
    fun redirectArscPath_xmlToPng_equalLengthInPlaceReplace() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "icon-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val arscFile = extractTemplateArsc(tmpDir)
            if (arscFile == null) {
                println("[跳过] 模版 APK 不存在")
                return
            }

            val originalBytes = arscFile.readBytes()
            val originalSize = originalBytes.size

            val ok = IconReplacer.redirectArscPath(arscFile, "res/Qr.xml", "res/Qr.png")
            assertTrue("arsc 重定向应成功", ok)

            val patched = arscFile.readBytes()
            // 文件大小不变（等长原地替换）
            assertEquals("替换后文件大小必须不变", originalSize.toLong(), arscFile.length())
            assertEquals("字节数组长度必须不变", originalSize, patched.size)
            // 新路径存在、旧路径消失
            assertTrue("应包含 res/Qr.png", patched.contains("res/Qr.png".toByteArray()))
            assertFalse("不应再包含 res/Qr.xml", patched.contains("res/Qr.xml".toByteArray()))
            // 除替换的 10 字节外，其余字节完全一致
            val expected = originalBytes.copyOf()
            val idx = expected.indexOf("res/Qr.xml".toByteArray())
            System.arraycopy("res/Qr.png".toByteArray(), 0, expected, idx, 10)
            assertArrayEquals("仅路径字符串被改写，其余字节必须一致", expected, patched)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun redirectArscPath_nonExistentString_returnsFalse() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "icon-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val arscFile = extractTemplateArsc(tmpDir)
            if (arscFile == null) {
                println("[跳过] 模版 APK 不存在")
                return
            }
            val before = arscFile.readBytes()
            val ok = IconReplacer.redirectArscPath(arscFile, "res/NotExist.xml", "res/NotExist.png")
            assertFalse("不存在的路径应返回 false", ok)
            assertArrayEquals("失败时文件内容不得改变", before, arscFile.readBytes())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun redirectArscPath_lengthMismatch_returnsFalse() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "icon-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val arscFile = extractTemplateArsc(tmpDir)
            if (arscFile == null) {
                println("[跳过] 模版 APK 不存在")
                return
            }
            val before = arscFile.readBytes()
            // res/Qr.xml(10) -> res/Qr.jpeg(11) 长度不一致
            val ok = IconReplacer.redirectArscPath(arscFile, "res/Qr.xml", "res/Qr.jpeg")
            assertFalse("长度不一致应返回 false", ok)
            assertArrayEquals("失败时文件内容不得改变", before, arscFile.readBytes())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun redirectArscPath_missingFile_returnsFalse() {
        val missing = File(System.getProperty("java.io.tmpdir"), "no-such-arsc-${System.nanoTime()}")
        val ok = IconReplacer.redirectArscPath(missing, "res/Qr.xml", "res/Qr.png")
        assertFalse("文件不存在应返回 false", ok)
    }

    private fun ByteArray.contains(other: ByteArray): Boolean {
        if (other.isEmpty()) return true
        outer@ for (i in 0..size - other.size) {
            for (j in other.indices) {
                if (this[i + j] != other[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun ByteArray.indexOf(other: ByteArray): Int {
        outer@ for (i in 0..size - other.size) {
            for (j in other.indices) {
                if (this[i + j] != other[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
