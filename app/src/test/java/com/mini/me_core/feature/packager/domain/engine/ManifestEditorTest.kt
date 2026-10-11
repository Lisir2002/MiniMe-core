package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ManifestEditor 回归测试。
 *
 * 守护「改包名时必须同步修改权限名和 authority」这条底线：
 * 仅改 package 属性而不改 permission/authority，会导致多个打包生成的应用
 * 声明同名权限而冲突，安装时报「软件包与现有软件包存在冲突」。
 */
class ManifestEditorTest {

    private fun extractManifestFromTemplate(dest: File) {
        dest.parentFile?.mkdirs()
        val template = File("/home/user/projects/MiniMe-core/app/src/main/assets/packager/template-base.apk")
        if (!template.exists()) return
        ProcessBuilder(
            "unzip", "-o", template.absolutePath, "AndroidManifest.xml", "-d", dest.parentFile!!.absolutePath
        ).redirectErrorStream(true).start().waitFor()
    }

    /** 解析二进制 AXML 的字符串池，返回所有字符串 */
    private fun parseAxmlStrings(manifestFile: File): List<String> {
        val data = manifestFile.readBytes()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // String Pool chunk 在文件头(8字节)之后
        val spStart = 8
        buffer.position(spStart)
        val spType = buffer.short.toInt() and 0xFFFF
        if (spType != 0x0001) return emptyList()

        val spHeaderSize = buffer.short.toInt() and 0xFFFF
        val spSize = buffer.int
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        val isUtf8 = (flags and 0x100) != 0

        // 读取字符串偏移
        val offsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            offsets[i] = buffer.int
        }

        // 读取所有字符串
        val strings = mutableListOf<String>()
        for (i in 0 until stringCount) {
            val pos = spStart + stringsStart + offsets[i]
            buffer.position(pos)
            if (isUtf8) {
                var charLen = buffer.get().toInt() and 0xFF
                if (charLen and 0x80 != 0) {
                    charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                }
                var byteLen = buffer.get().toInt() and 0xFF
                if (byteLen and 0x80 != 0) {
                    byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
                }
                val bytes = ByteArray(byteLen)
                buffer.get(bytes)
                strings.add(String(bytes, Charsets.UTF_8))
            } else {
                var charLen = buffer.short.toInt() and 0xFFFF
                if (charLen and 0x8000 != 0) {
                    charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
                }
                val chars = CharArray(charLen)
                for (j in 0 until charLen) {
                    chars[j] = buffer.short.toChar()
                }
                strings.add(String(chars))
            }
        }
        return strings
    }

    @Test
    fun modifyManifest_updatesPackagePermissionAndAuthority() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "manifest-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val manifestFile = File(tmpDir, "AndroidManifest.xml")
            extractManifestFromTemplate(manifestFile)
            if (!manifestFile.exists()) {
                println("[跳过] 模板 AndroidManifest.xml 不存在")
                return
            }

            val result = ManifestEditor.modifyManifest(
                manifestFile = manifestFile,
                newPackageName = "com.example.testapp",
                newAppName = "测试应用",
                newVersionName = "1.0.0",
                newVersionCode = 10000100
            )
            assertTrue("modifyManifest 应返回 true", result)

            val strings = parseAxmlStrings(manifestFile)

            // 验证旧包名相关的权限/authority 已不存在
            assertFalse("权限名应已更新", strings.contains("com.minime.template.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
            assertFalse("authority 应已更新", strings.contains("com.minime.template.androidx-startup"))
            assertFalse("package 应已更新", strings.contains("com.minime.template"))

            // 验证新包名相关的权限/authority 已存在
            assertTrue("新 package 应存在", strings.contains("com.example.testapp"))
            assertTrue("新权限名应存在", strings.contains("com.example.testapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
            assertTrue("新 authority 应存在", strings.contains("com.example.testapp.androidx-startup"))

            // 验证类名保持不变（不应该被替换）
            assertTrue("Activity 类名应保持不变", strings.contains("com.minime.template.MainActivity"))
            assertTrue("Application 类名应保持不变", strings.contains("com.minime.template.TemplateApp"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun modifyManifest_preservesClassNames() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "manifest-test2-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val manifestFile = File(tmpDir, "AndroidManifest.xml")
            extractManifestFromTemplate(manifestFile)
            if (!manifestFile.exists()) {
                println("[跳过] 模板 AndroidManifest.xml 不存在")
                return
            }

            ManifestEditor.modifyManifest(
                manifestFile = manifestFile,
                newPackageName = "com.example.another",
                newAppName = "另一个应用"
            )

            val strings = parseAxmlStrings(manifestFile)

            // 类名必须保持不变，否则运行时找不到类
            assertTrue("MainActivity 类名应保持不变", strings.contains("com.minime.template.MainActivity"))
            assertTrue("TemplateApp 类名应保持不变", strings.contains("com.minime.template.TemplateApp"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
