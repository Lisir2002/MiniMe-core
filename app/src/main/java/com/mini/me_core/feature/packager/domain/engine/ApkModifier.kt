package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * APK 修改器
 *
 * 负责 APK 的解压、资源替换、重新打包。
 * APK 本质上是 ZIP 格式，可以用标准 ZIP 工具处理。
 */
class ApkModifier {

    companion object {
        private const val TAG = "ApkModifier"

        /** 网页资源在 APK 中的路径前缀 */
        private const val WWW_ASSETS_PREFIX = "assets/www/"

        /** META-INF 目录（签名文件，重打包时需要删除，重新签名） */
        private const val META_INF_PREFIX = "META-INF/"

        /**
         * 解压 APK 到指定目录
         *
         * @param apkFile APK 文件
         * @param outputDir 输出目录
         */
        fun extractApk(apkFile: File, outputDir: File) {
            FileLogger.d(TAG, "解压 APK: ${apkFile.name} (${apkFile.length()} bytes) -> ${outputDir.name}")
            if (outputDir.exists()) {
                outputDir.deleteRecursively()
            }
            outputDir.mkdirs()

            var fileCount = 0
            ZipInputStream(FileInputStream(apkFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val entryFile = File(outputDir, entry!!.name)

                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        // 保留文件时间
                        entry!!.time?.let { entryFile.setLastModified(it) }
                        fileCount++
                    }
                    zis.closeEntry()
                }
            }
            FileLogger.d(TAG, "解压完成，共 $fileCount 个文件")
        }

        /**
         * 替换 assets/www/ 目录下的所有网页资源
         *
         * @param extractedDir 已解压的 APK 目录
         * @param wwwSourceDir 源 www 目录（包含 index.html 等）
         */
        fun replaceWwwAssets(extractedDir: File, wwwSourceDir: File) {
            val targetWwwDir = File(extractedDir, WWW_ASSETS_PREFIX)
            FileLogger.d(TAG, "替换网页资源: ${wwwSourceDir.absolutePath} -> $WWW_ASSETS_PREFIX")

            // 删除旧的 www 目录
            if (targetWwwDir.exists()) {
                targetWwwDir.deleteRecursively()
            }
            targetWwwDir.mkdirs()

            // 复制新的 www 目录
            if (wwwSourceDir.exists()) {
                val fileCount = copyDirectory(wwwSourceDir, targetWwwDir)
                FileLogger.d(TAG, "网页资源替换完成，共 $fileCount 个文件")
            } else {
                FileLogger.w(TAG, "源 www 目录不存在: ${wwwSourceDir.absolutePath}")
            }
        }

        /**
         * 从解压目录重新打包为 APK（未签名）
         *
         * @param extractedDir 已解压的 APK 目录
         * @param outputApk 输出的未签名 APK 文件
         * @param noCompressExtensions 不需要压缩的文件扩展名（如 .png, .jpg）
         */
        fun repackApk(
            extractedDir: File,
            outputApk: File,
            noCompressExtensions: Set<String> = setOf("png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "wav", "ogg", "arsc")
        ) {
            FileLogger.d(TAG, "重新打包 APK: ${extractedDir.name} -> ${outputApk.name}")
            outputApk.parentFile?.mkdirs()
            if (outputApk.exists()) outputApk.delete()

            var entryCount = 0
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputApk))).use { zos ->
                // 收集所有文件，按路径排序（保证确定性）
                val files = mutableListOf<File>()
                collectFiles(extractedDir, files)
                files.sortBy { it.relativeTo(extractedDir).path.replace('\\', '/') }
                FileLogger.d(TAG, "收集到 ${files.size} 个文件待打包")

                for (file in files) {
                    val relativePath = file.relativeTo(extractedDir).path.replace('\\', '/')

                    // 跳过 META-INF（签名文件，重新签名时会生成新的）
                    if (relativePath.startsWith(META_INF_PREFIX)) continue

                    val entry = ZipEntry(relativePath)
                    entry.time = file.lastModified()

                    // 判断是否需要压缩
                    val extension = file.extension.lowercase()
                    if (extension in noCompressExtensions) {
                        entry.method = ZipEntry.STORED
                        entry.size = file.length()
                        entry.crc = computeCrc32(file)
                    } else {
                        entry.method = ZipEntry.DEFLATED
                    }

                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                    entryCount++
                }
            }
            FileLogger.d(TAG, "重新打包完成，共 $entryCount 个条目，输出大小: ${outputApk.length()} bytes")
        }

        /**
         * 递归收集目录下所有文件
         */
        private fun collectFiles(dir: File, files: MutableList<File>) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    collectFiles(file, files)
                } else {
                    files.add(file)
                }
            }
        }

        /**
         * 复制目录（递归），返回复制的文件数
         */
        private fun copyDirectory(src: File, dest: File): Int {
            dest.mkdirs()
            var count = 0
            src.listFiles()?.forEach { file ->
                val destFile = File(dest, file.name)
                if (file.isDirectory) {
                    count += copyDirectory(file, destFile)
                } else {
                    file.copyTo(destFile, overwrite = true)
                    count++
                }
            }
            return count
        }

        /**
         * 计算文件的 CRC32 校验值（用于 STORED 方式的 ZIP 条目）
         */
        private fun computeCrc32(file: File): Long {
            val crc = java.util.zip.CRC32()
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    crc.update(buffer, 0, bytesRead)
                }
            }
            return crc.value
        }
    }
}
