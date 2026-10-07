package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * APK 分析器
 *
 * 分析 APK 文件的基本信息、权限、组件、签名等。
 */
class ApkAnalyzer {

    /**
     * APK 分析结果
     */
    data class ApkAnalysis(
        val packageName: String = "",
        val appName: String = "",
        val versionName: String = "",
        val versionCode: Int = 0,
        val minSdkVersion: Int = 0,
        val targetSdkVersion: Int = 0,
        val fileSize: Long = 0,
        val permissions: List<String> = emptyList(),
        val activityCount: Int = 0,
        val serviceCount: Int = 0,
        val receiverCount: Int = 0,
        val providerCount: Int = 0,
        val signatureAlgorithm: String = "",
        val certificateSubject: String = "",
        val certificateIssuer: String = "",
        val certificateValidFrom: String = "",
        val certificateValidTo: String = "",
        val isSigned: Boolean = false,
        val dexCount: Int = 0,
        val nativeLibCount: Int = 0,
        val assetCount: Int = 0
    )

    companion object {
        private const val TAG = "ApkAnalyzer"

        /** AXML 文件魔数 */
        private const val AXML_MAGIC = 0x00080003

        /** String Pool chunk 类型 */
        private const val CHUNK_STRING_POOL = 0x0001

        /** String Pool 标志：UTF-8 编码 */
        private const val FLAG_UTF8 = 0x00000100

        /** XML chunk 类型 */
        private const val CHUNK_XML = 0x0003
        private const val CHUNK_START_ELEMENT = 0x0102
        private const val CHUNK_END_ELEMENT = 0x0103

        /**
         * 分析 APK 文件
         */
        fun analyze(apkFile: File): ApkAnalysis {
            FileLogger.d(TAG, "开始分析 APK: ${apkFile.name} (${apkFile.length()} bytes)")
            val analysis = ApkAnalysis(fileSize = apkFile.length())

            // 解析 AndroidManifest.xml
            val manifestData = readEntryFromZip(apkFile, "AndroidManifest.xml")
            if (manifestData != null) {
                val manifestInfo = parseBinaryManifest(manifestData)
                analysis.copy(
                    packageName = manifestInfo.packageName,
                    appName = manifestInfo.appName,
                    versionName = manifestInfo.versionName,
                    versionCode = manifestInfo.versionCode,
                    minSdkVersion = manifestInfo.minSdkVersion,
                    targetSdkVersion = manifestInfo.targetSdkVersion,
                    permissions = manifestInfo.permissions,
                    activityCount = manifestInfo.activityCount,
                    serviceCount = manifestInfo.serviceCount,
                    receiverCount = manifestInfo.receiverCount,
                    providerCount = manifestInfo.providerCount
                )
            }

            // 统计文件类型
            val fileCounts = countFileTypes(apkFile)
            analysis.copy(
                dexCount = fileCounts.dexCount,
                nativeLibCount = fileCounts.nativeLibCount,
                assetCount = fileCounts.assetCount
            )

            // 解析签名信息
            val signatureInfo = parseSignature(apkFile)
            analysis.copy(
                isSigned = signatureInfo.isSigned,
                signatureAlgorithm = signatureInfo.algorithm,
                certificateSubject = signatureInfo.subject,
                certificateIssuer = signatureInfo.issuer,
                certificateValidFrom = signatureInfo.validFrom,
                certificateValidTo = signatureInfo.validTo
            )

            FileLogger.d(TAG, "APK 分析完成: 包名=${analysis.packageName}, 权限=${analysis.permissions.size}, 已签名=${analysis.isSigned}")
            return analysis
        }

        /**
         * 从 ZIP 文件中读取指定条目
         */
        private fun readEntryFromZip(zipFile: File, entryName: String): ByteArray? {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    if (entry!!.name == entryName) {
                        return zis.readBytes()
                    }
                    zis.closeEntry()
                }
            }
            return null
        }

        /**
         * 二进制 Manifest 解析结果
         */
        private data class ManifestInfo(
            val packageName: String = "",
            val appName: String = "",
            val versionName: String = "",
            val versionCode: Int = 0,
            val minSdkVersion: Int = 0,
            val targetSdkVersion: Int = 0,
            val permissions: List<String> = emptyList(),
            val activityCount: Int = 0,
            val serviceCount: Int = 0,
            val receiverCount: Int = 0,
            val providerCount: Int = 0
        )

        /**
         * 解析二进制 AndroidManifest.xml
         */
        private fun parseBinaryManifest(data: ByteArray): ManifestInfo {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // 验证 AXML 魔数
            val magic = buffer.int
            if (magic != AXML_MAGIC) return ManifestInfo()

            val fileSize = buffer.int

            // 解析 String Pool
            val stringPoolStart = buffer.position()
            val spChunkType = buffer.short.toInt() and 0xFFFF
            if (spChunkType != CHUNK_STRING_POOL) return ManifestInfo()

            val spHeaderSize = buffer.short.toInt() and 0xFFFF
            val spChunkSize = buffer.int
            val stringCount = buffer.int
            val styleCount = buffer.int
            val flags = buffer.int
            val stringsStart = buffer.int
            val stylesStart = buffer.int

            // 读取字符串偏移量
            val stringOffsets = IntArray(stringCount)
            for (i in 0 until stringCount) {
                stringOffsets[i] = buffer.int
            }

            // 读取字符串
            val isUtf8 = (flags and FLAG_UTF8) != 0
            val strings = Array(stringCount) { "" }
            for (i in 0 until stringCount) {
                val stringStart = stringPoolStart + stringsStart + stringOffsets[i]
                strings[i] = readString(data, stringStart, isUtf8)
            }

            // 跳过 String Pool 剩余部分
            buffer.position(stringPoolStart + spChunkSize)

            // 解析 XML 内容
            var packageName = ""
            var appName = ""
            var versionName = ""
            var versionCode = 0
            var minSdkVersion = 0
            var targetSdkVersion = 0
            val permissions = mutableListOf<String>()
            var activityCount = 0
            var serviceCount = 0
            var receiverCount = 0
            var providerCount = 0

            while (buffer.position() < data.size) {
                val chunkStart = buffer.position()
                val chunkType = buffer.short.toInt() and 0xFFFF
                val chunkHeaderSize = buffer.short.toInt() and 0xFFFF
                val chunkSize = buffer.int

                when (chunkType) {
                    CHUNK_START_ELEMENT -> {
                        // 跳过 lineNumber 和 commentIndex
                        buffer.int
                        buffer.int
                        // 读取元素名索引
                        val nsIndex = buffer.int
                        val nameIndex = buffer.int
                        val elementName = if (nameIndex >= 0 && nameIndex < strings.size) strings[nameIndex] else ""

                        // 跳过 attributeStart、attributeSize、attributeCount
                        buffer.short
                        buffer.short
                        val attrCount = buffer.short.toInt() and 0xFFFF
                        // 跳过 idIndex、classIndex、styleIndex
                        buffer.int
                        buffer.int
                        buffer.int

                        // 读取属性
                        for (j in 0 until attrCount) {
                            val attrNsIndex = buffer.int
                            val attrNameIndex = buffer.int
                            val attrRawValueIndex = buffer.int
                            val attrValueType = buffer.int
                            val attrValueData = buffer.int

                            val attrName = if (attrNameIndex >= 0 && attrNameIndex < strings.size) strings[attrNameIndex] else ""

                            when (elementName) {
                                "manifest" -> {
                                    when (attrName) {
                                        "package" -> packageName = if (attrRawValueIndex >= 0 && attrRawValueIndex < strings.size) strings[attrRawValueIndex] else ""
                                        "versionName" -> versionName = if (attrRawValueIndex >= 0 && attrRawValueIndex < strings.size) strings[attrRawValueIndex] else ""
                                        "versionCode" -> versionCode = attrValueData
                                    }
                                }
                                "uses-sdk" -> {
                                    when (attrName) {
                                        "minSdkVersion" -> minSdkVersion = attrValueData
                                        "targetSdkVersion" -> targetSdkVersion = attrValueData
                                    }
                                }
                                "application" -> {
                                    if (attrName == "label") {
                                        appName = if (attrRawValueIndex >= 0 && attrRawValueIndex < strings.size) strings[attrRawValueIndex] else ""
                                    }
                                }
                                "uses-permission" -> {
                                    if (attrName == "name") {
                                        val perm = if (attrRawValueIndex >= 0 && attrRawValueIndex < strings.size) strings[attrRawValueIndex] else ""
                                        if (perm.isNotEmpty()) permissions.add(perm)
                                    }
                                }
                                "activity" -> activityCount++
                                "service" -> serviceCount++
                                "receiver" -> receiverCount++
                                "provider" -> providerCount++
                            }
                        }
                    }
                    CHUNK_END_ELEMENT -> {
                        // 跳过 lineNumber、commentIndex、nsIndex、nameIndex
                        buffer.int
                        buffer.int
                        buffer.int
                        buffer.int
                    }
                    else -> {
                        // 其他 chunk 直接跳过
                    }
                }

                // 移动到下一个 chunk
                if (chunkSize > 0) {
                    buffer.position(chunkStart + chunkSize)
                } else {
                    break
                }
            }

            return ManifestInfo(
                packageName = packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                minSdkVersion = minSdkVersion,
                targetSdkVersion = targetSdkVersion,
                permissions = permissions.distinct(),
                activityCount = activityCount,
                serviceCount = serviceCount,
                receiverCount = receiverCount,
                providerCount = providerCount
            )
        }

        /**
         * 从 AXML 数据中读取字符串
         */
        private fun readString(data: ByteArray, offset: Int, isUtf8: Boolean): String {
            return try {
                if (isUtf8) {
                    // UTF-8 编码
                    var pos = offset
                    // 字符长度
                    val charLen = data[pos].toInt() and 0xFF
                    pos++
                    if (charLen and 0x80 != 0) {
                        pos++
                    }
                    // 字节长度
                    val byteLen = data[pos].toInt() and 0xFF
                    pos++
                    if (byteLen and 0x80 != 0) {
                        val high = (byteLen and 0x7F) shl 8
                        val low = data[pos].toInt() and 0xFF
                        pos++
                        val len = high or low
                        String(data, pos, len, Charsets.UTF_8)
                    } else {
                        String(data, pos, byteLen, Charsets.UTF_8)
                    }
                } else {
                    // UTF-16 编码
                    var pos = offset
                    val len = data[pos].toInt() and 0xFF or ((data[pos + 1].toInt() and 0xFF) shl 8)
                    pos += 2
                    if (len and 0x8000 != 0) {
                        // 4字节长度
                        val high = (len and 0x7FFF) shl 16
                        val low = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8)
                        pos += 2
                        val charLen = high or low
                        String(data, pos, charLen * 2, Charsets.UTF_16LE)
                    } else {
                        String(data, pos, len * 2, Charsets.UTF_16LE)
                    }
                }
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * 文件类型统计结果
         */
        private data class FileTypeCounts(
            val dexCount: Int = 0,
            val nativeLibCount: Int = 0,
            val assetCount: Int = 0
        )

        /**
         * 统计 APK 中的文件类型
         */
        private fun countFileTypes(apkFile: File): FileTypeCounts {
            var dexCount = 0
            var nativeLibCount = 0
            var assetCount = 0

            ZipInputStream(FileInputStream(apkFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    when {
                        name.endsWith(".dex") -> dexCount++
                        name.endsWith(".so") -> nativeLibCount++
                        name.startsWith("assets/") && !entry!!.isDirectory -> assetCount++
                    }
                    zis.closeEntry()
                }
            }

            return FileTypeCounts(dexCount, nativeLibCount, assetCount)
        }

        /**
         * 签名信息
         */
        private data class SignatureInfo(
            val isSigned: Boolean = false,
            val algorithm: String = "",
            val subject: String = "",
            val issuer: String = "",
            val validFrom: String = "",
            val validTo: String = ""
        )

        /**
         * 解析 APK 签名信息
         */
        private fun parseSignature(apkFile: File): SignatureInfo {
            return try {
                JarFile(apkFile).use { jarFile ->
                    // 查找签名文件
                    val certEntry = jarFile.getEntry("META-INF/CERT.RSA")
                        ?: jarFile.getEntry("META-INF/CERT.DSA")
                        ?: jarFile.getEntry("META-INF/CERT.EC")

                    if (certEntry != null) {
                        jarFile.getInputStream(certEntry).use { input ->
                            // 读取证书
                            val certificateFactory = java.security.cert.CertificateFactory.getInstance("X.509")
                            val cert = certificateFactory.generateCertificate(input) as X509Certificate

                            SignatureInfo(
                                isSigned = true,
                                algorithm = cert.sigAlgName,
                                subject = cert.subjectX500Principal.name,
                                issuer = cert.issuerX500Principal.name,
                                validFrom = cert.notBefore.toString(),
                                validTo = cert.notAfter.toString()
                            )
                        }
                    } else {
                        SignatureInfo(isSigned = false)
                    }
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "解析签名信息失败: ${e.message}")
                SignatureInfo(isSigned = false)
            }
        }
    }
}
