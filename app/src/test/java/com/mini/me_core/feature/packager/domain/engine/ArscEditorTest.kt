package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * resources.arsc 编辑器回归测试。
 *
 * 守护「改包名时必须同步修改 resources.arsc 的 package name」这条底线：
 * 仅改 AndroidManifest.xml 的 package 而不改 resources.arsc，会导致系统安装时
 * 报「安装应用与当前系统有兼容性问题」。
 */
class ArscEditorTest {

    private fun copyTemplateArsc(dest: File) {
        dest.parentFile?.mkdirs()
        val template = File("/home/user/projects/MiniMe-core/app/src/main/assets/packager/template-base.apk")
        if (!template.exists()) {
            // CI 环境可能无 assets，跳过
            return
        }
        // 从模板 APK 中提取 resources.arsc
        val process = ProcessBuilder(
            "unzip", "-o", template.absolutePath, "resources.arsc", "-d", dest.parentFile!!.absolutePath
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText()
        process.waitFor()
    }

    @Test
    fun modifyPackageName_updatesArscPackageName() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "arsc-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val arscFile = File(tmpDir, "resources.arsc")
            copyTemplateArsc(arscFile)
            if (!arscFile.exists()) {
                println("[跳过] 模板 resources.arsc 不存在")
                return
            }

            val originalSize = arscFile.length()
            val result = ArscEditor.modifyPackageName(
                arscFile = arscFile,
                newPackageName = "com.example.testapp"
            )

            assertTrue("modifyPackageName 应返回 true", result)
            assertEquals("文件大小应不变（原地替换）", originalSize, arscFile.length())

            // 用 aapt 验证包名已改变
            val aapt = findAapt()
            if (aapt != null) {
                // 构造一个最小 APK 来验证 arsc（aapt 不能直接读 arsc）
                // 改为直接用 Python 解析验证
            }

            // 直接解析 arsc 验证包名
            val data = arscFile.readBytes()
            val spSize = byteArrayToInt(data, 16)  // stringPool size @ offset 12+4
            val pkgOffset = 12 + spSize
            val nameBytes = data.copyOfRange(pkgOffset + 12, pkgOffset + 12 + 256)
            val packageName = nameBytes.toString(Charsets.UTF_16LE).trimEnd('\u0000')
            assertEquals("resources.arsc 包名应已更新", "com.example.testapp", packageName)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun modifyPackageName_rejectsTooLongName() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "arsc-test-long-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val arscFile = File(tmpDir, "resources.arsc")
            copyTemplateArsc(arscFile)
            if (!arscFile.exists()) {
                println("[跳过] 模板 resources.arsc 不存在")
                return
            }

            val longName = "a".repeat(130)  // 130 字符 > 127 上限（UTF-16LE 260 字节 > 256）
            val result = ArscEditor.modifyPackageName(arscFile, longName)
            assertFalse("超长包名应返回 false", result)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun findAapt(): String? {
        val androidHome = System.getenv("ANDROID_HOME") ?: return null
        val buildTools = File(androidHome, "build-tools")
        if (!buildTools.exists()) return null
        return buildTools.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { File(it, "aapt").takeIf { f -> f.exists() }?.absolutePath }
            ?.firstOrNull()
    }

    private fun byteArrayToInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }
}
