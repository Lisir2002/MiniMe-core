package com.mini.me_core.feature.packager.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 对齐器回归测试。
 *
 * 守护「打包出的 APK 必须 4 字节对齐」这条底线：未对齐的 APK（特别是
 * resources.arsc 未对齐）在部分设备（如 vivo）上安装时会报
 * 「安装应用与当前系统有兼容性问题」。
 */
class ZipAlignerTest {

    private fun createTestApk(dest: File, storedFiles: Map<String, ByteArray>) {
        dest.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for ((name, data) in storedFiles) {
                val entry = ZipEntry(name)
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.crc = java.util.zip.CRC32().apply { update(data) }.value
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    @Test
    fun align_produces4ByteAlignedStoredEntries() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "zipalign-test-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            // 创建一个未对齐的测试 APK（多个 STORED 文件，大小不一）
            val input = File(tmpDir, "input.apk")
            createTestApk(
                input, mapOf(
                    "file1.txt" to ByteArray(10) { it.toByte() },
                    "file2.bin" to ByteArray(100) { (it % 256).toByte() },
                    "resources.arsc" to ByteArray(11488) { (it % 256).toByte() }
                )
            )

            val output = File(tmpDir, "aligned.apk")
            ZipAligner.align(input, output)

            assertTrue("对齐后的 APK 应存在", output.exists())
            assertTrue("对齐后的 APK 应非空", output.length() > 0)

            // 验证对齐：解析 ZIP，检查每个 STORED 条目的数据偏移是否 4 字节对齐
            val data = output.readBytes()
            var pos = 0
            var alignedCount = 0
            var totalStored = 0
            while (pos < data.size - 4) {
                val magic = (data[pos].toInt() and 0xFF) or
                        ((data[pos + 1].toInt() and 0xFF) shl 8) or
                        ((data[pos + 2].toInt() and 0xFF) shl 16) or
                        ((data[pos + 3].toInt() and 0xFF) shl 24)
                if (magic != 0x04034b50) break // LFH magic

                val method = (data[pos + 8].toInt() and 0xFF) or
                        ((data[pos + 9].toInt() and 0xFF) shl 8)
                val nameLen = (data[pos + 26].toInt() and 0xFF) or
                        ((data[pos + 27].toInt() and 0xFF) shl 8)
                val extraLen = (data[pos + 28].toInt() and 0xFF) or
                        ((data[pos + 29].toInt() and 0xFF) shl 8)
                val compSize = (data[pos + 18].toInt() and 0xFF) or
                        ((data[pos + 19].toInt() and 0xFF) shl 8) or
                        ((data[pos + 20].toInt() and 0xFF) shl 16) or
                        ((data[pos + 21].toInt() and 0xFF) shl 24)

                val dataStart = pos + 30 + nameLen + extraLen
                if (method == ZipEntry.STORED) {
                    totalStored++
                    if (dataStart % 4 == 0) alignedCount++
                }
                pos = dataStart + compSize
            }

            assertEquals("所有 STORED 条目都应对齐", totalStored, alignedCount)
            assertTrue("至少有一个 STORED 条目", totalStored > 0)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun align_preservesFileContents() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "zipalign-test2-${System.nanoTime()}")
        tmpDir.mkdirs()
        try {
            val content1 = "Hello World".toByteArray()
            val content2 = ByteArray(5000) { (it % 256).toByte() }
            val input = File(tmpDir, "input.apk")
            createTestApk(input, mapOf("a.txt" to content1, "b.bin" to content2))

            val output = File(tmpDir, "aligned.apk")
            ZipAligner.align(input, output)

            // 解压验证内容
            java.util.zip.ZipFile(output).use { zip ->
                zip.getInputStream(zip.getEntry("a.txt")).use {
                    assertEquals(content1.contentToString(), it.readBytes().contentToString())
                }
                zip.getInputStream(zip.getEntry("b.bin")).use {
                    assertEquals(content2.contentToString(), it.readBytes().contentToString())
                }
            }
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
