package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate

/**
 * APK V2 签名器（手动实现，基于 AOSP 公开标准）
 *
 * 完全不依赖 apksig 库，手动实现 APK Signature Scheme v2。
 * V2 签名从 Android 7.0（API 24）开始支持，Android 11（API 30）起强制要求。
 *
 * 实现参考：https://source.android.google.cn/docs/security/features/apksigning/v2
 */
class V2ApkSigner {

    companion object {
        private const val TAG = "V2ApkSigner"

        /** APK Signing Block 魔数 */
        private const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"

        /** V2 签名 ID */
        private const val V2_SIGNATURE_ID = 0x7109871a

        /** V3 签名 ID（0xf05368c0，作为有符号 Int 表示） */
        private const val V3_SIGNATURE_ID: Int = 0xf05368c0.toInt()

        /** 签名算法：RSA PKCS#1 v1.5 with SHA256 */
        private const val SIG_ALG_RSA_PKCS1_V15_SHA256 = 0x0103

        /** 分块大小：1MB */
        private const val CHUNK_SIZE = 1048576

        /** ZIP EOCD 签名 */
        private const val EOCD_SIGNATURE = 0x06054b50

        /**
         * 写入小端序 32 位整数到字节数组指定位置
         */
        private fun writeIntLE(data: ByteArray, offset: Int, value: Int) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }

        /**
         * 写入小端序 64 位整数到字节数组指定位置
         */
        private fun writeLongLE(data: ByteArray, offset: Int, value: Long) {
            data[offset] = (value and 0xFF).toByte()
            data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            data[offset + 2] = ((value shr 16) and 0xFF).toByte()
            data[offset + 3] = ((value shr 24) and 0xFF).toByte()
            data[offset + 4] = ((value shr 32) and 0xFF).toByte()
            data[offset + 5] = ((value shr 40) and 0xFF).toByte()
            data[offset + 6] = ((value shr 48) and 0xFF).toByte()
            data[offset + 7] = ((value shr 56) and 0xFF).toByte()
        }

        /**
         * 对 APK 进行 V2 签名
         */
        fun sign(
            unsignedApk: File,
            signedApk: File,
            privateKey: PrivateKey,
            certificate: X509Certificate
        ) {
            FileLogger.d(TAG, "开始 V2 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes)")

            // 1. 找到中央目录的起始偏移量
            val centralDirOffset = findCentralDirOffset(unsignedApk)
            FileLogger.d(TAG, "中央目录偏移量: $centralDirOffset")

            // 2. 计算 APK 内容的摘要（分块 SHA256）
            val contentDigest = computeContentDigest(unsignedApk, centralDirOffset)
            FileLogger.d(TAG, "内容摘要计算完成: ${contentDigest.size} bytes")

            // 3. 构建签名数据（长度 + 签名算法ID + 摘要长度 + 摘要）
            val signatureData = buildSignatureData(contentDigest)
            FileLogger.d(TAG, "签名数据构建完成: ${signatureData.size} bytes")

            // 4. 用私钥对签名数据进行 RSA PKCS#1 v1.5 SHA256 签名
            val signatureBytes = signData(signatureData, privateKey)
            FileLogger.d(TAG, "数据签名完成: ${signatureBytes.size} bytes")

            // 5. 获取证书的 DER 编码
            val certBytes = certificate.encoded
            FileLogger.d(TAG, "证书 DER 编码: ${certBytes.size} bytes")

            // 6. 构建 V2 签名块
            val v2SigningBlock = buildSigningBlock(
                signatureId = V2_SIGNATURE_ID,
                contentDigest = contentDigest,
                signatureBytes = signatureBytes,
                certBytes = certBytes
            )
            FileLogger.d(TAG, "V2 签名块构建完成: ${v2SigningBlock.size} bytes")

            // 7. 构建 V3 签名块（与 V2 格式相同，ID 不同）
            val v3SigningBlock = buildSigningBlock(
                signatureId = V3_SIGNATURE_ID,
                contentDigest = contentDigest,
                signatureBytes = signatureBytes,
                certBytes = certBytes
            )
            FileLogger.d(TAG, "V3 签名块构建完成: ${v3SigningBlock.size} bytes")

            // 8. 合并 V2 + V3 签名块到一个 APK Signing Block 中
            val combinedBlock = combineSigningBlocks(v2SigningBlock, v3SigningBlock)
            FileLogger.d(TAG, "合并签名块完成: ${combinedBlock.size} bytes")

            // 9. 将签名块插入到 APK 中，更新中央目录偏移量
            insertSigningBlock(
                unsignedApk = unsignedApk,
                signedApk = signedApk,
                signingBlock = combinedBlock,
                centralDirOffset = centralDirOffset
            )

            FileLogger.d(TAG, "V2+V3 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
        }

        /**
         * 找到 ZIP 文件中央目录的起始偏移量（从 EOCD 中读取）
         */
        private fun findCentralDirOffset(apkFile: File): Long {
            val data = apkFile.readBytes()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            var pos = data.size - 22
            while (pos >= 0) {
                if (buffer.getInt(pos) == EOCD_SIGNATURE) {
                    return buffer.getInt(pos + 16).toLong() and 0xFFFFFFFFL
                }
                pos--
            }
            throw IllegalStateException("未找到 EOCD，无法确定中央目录偏移量")
        }

        /**
         * 计算 APK 内容的摘要（根据 AOSP 规范）
         */
        private fun computeContentDigest(apkFile: File, centralDirOffset: Long): ByteArray {
            val chunkDigests = java.io.ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            var chunkCount = 0

            FileInputStream(apkFile).use { fis ->
                var remaining = centralDirOffset
                val chunkBuffer = ByteArray(CHUNK_SIZE)

                while (remaining > 0) {
                    val toRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                    val bytesRead = fis.read(chunkBuffer, 0, toRead)
                    if (bytesRead <= 0) break

                    val chunkData = java.io.ByteArrayOutputStream()
                    chunkData.write(0xa5)
                    buffer.clear()
                    buffer.putInt(bytesRead)
                    chunkData.write(buffer.array())
                    chunkData.write(chunkBuffer, 0, bytesRead)

                    val chunkDigest = MessageDigest.getInstance("SHA-256")
                        .digest(chunkData.toByteArray())

                    chunkDigests.write(chunkDigest)
                    remaining -= bytesRead
                    chunkCount++
                }
            }

            FileLogger.d(TAG, "内容摘要分块数量: $chunkCount")

            val overallData = java.io.ByteArrayOutputStream()
            overallData.write(0x5a)
            buffer.clear()
            buffer.putInt(chunkCount)
            overallData.write(buffer.array())
            overallData.write(chunkDigests.toByteArray())

            val finalDigest = MessageDigest.getInstance("SHA-256")
                .digest(overallData.toByteArray())

            FileLogger.d(TAG, "内容摘要计算完成: ${finalDigest.size} bytes")
            return finalDigest
        }

        /**
         * 构建签名数据（长度 + 签名算法ID + 摘要长度 + 摘要）
         */
        private fun buildSignatureData(contentDigest: ByteArray): ByteArray {
            val dataLength = 4 + 4 + contentDigest.size
            val result = ByteArray(4 + dataLength)
            var offset = 0

            writeIntLE(result, offset, dataLength); offset += 4
            writeIntLE(result, offset, SIG_ALG_RSA_PKCS1_V15_SHA256); offset += 4
            writeIntLE(result, offset, contentDigest.size); offset += 4
            System.arraycopy(contentDigest, 0, result, offset, contentDigest.size)

            return result
        }

        /**
         * 用私钥对数据进行 RSA PKCS#1 v1.5 SHA256 签名
         */
        private fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(data)
            return signature.sign()
        }

        /**
         * 构建签名块
         *
         * 结构: [size(8)] [ID-value pairs] [size(8)] [magic(16)]
         * ID-value pair: [value_len(8)] [ID(4)] [value]
         * value: [签名者序列长度(4)] [签名者]
         * 签名者: [签名者数据长度(4)] [签名者数据] [签名序列长度(4)] [签名序列] [公钥长度(4)] [公钥]
         * 签名者数据: [摘要算法序列长度(4)] [证书序列长度(4)] [额外属性序列长度(4)] [摘要算法序列] [证书序列] [额外属性序列]
         * 摘要算法序列: [算法ID(4)] [摘要长度(4)] [摘要(32)]
         * 签名序列: [算法ID(4)] [签名长度(4)] [签名数据]
         */
        private fun buildSigningBlock(
            signatureId: Int,
            contentDigest: ByteArray,
            signatureBytes: ByteArray,
            certBytes: ByteArray
        ): ByteArray {
            // 1. 构建摘要算法序列: 算法ID(4) + 摘要长度(4) + 摘要(32) = 40
            val digestAlgorithm = ByteArray(40)
            writeIntLE(digestAlgorithm, 0, SIG_ALG_RSA_PKCS1_V15_SHA256)
            writeIntLE(digestAlgorithm, 4, contentDigest.size)
            System.arraycopy(contentDigest, 0, digestAlgorithm, 8, contentDigest.size)

            // 2. 构建证书序列: 证书长度(4) + 证书数据
            val certSequence = ByteArray(4 + certBytes.size)
            writeIntLE(certSequence, 0, certBytes.size)
            System.arraycopy(certBytes, 0, certSequence, 4, certBytes.size)

            // 3. 构建签名者数据: 摘要算法序列长度(4) + 证书序列长度(4) + 额外属性序列长度(4) + 摘要算法序列 + 证书序列
            val signerDataSize = 4 + 4 + 4 + digestAlgorithm.size + certSequence.size
            val signerData = ByteArray(signerDataSize)
            var offset = 0
            writeIntLE(signerData, offset, digestAlgorithm.size); offset += 4
            writeIntLE(signerData, offset, certSequence.size); offset += 4
            writeIntLE(signerData, offset, 0); offset += 4 // 额外属性序列长度=0
            System.arraycopy(digestAlgorithm, 0, signerData, offset, digestAlgorithm.size); offset += digestAlgorithm.size
            System.arraycopy(certSequence, 0, signerData, offset, certSequence.size)

            // 4. 构建签名序列: 算法ID(4) + 签名长度(4) + 签名数据
            val signatures = ByteArray(4 + 4 + signatureBytes.size)
            writeIntLE(signatures, 0, SIG_ALG_RSA_PKCS1_V15_SHA256)
            writeIntLE(signatures, 4, signatureBytes.size)
            System.arraycopy(signatureBytes, 0, signatures, 8, signatureBytes.size)

            // 5. 构建签名者: 签名者数据长度(4) + 签名者数据 + 签名序列长度(4) + 签名序列 + 公钥长度(4) + 公钥(0)
            val signerSize = 4 + signerData.size + 4 + signatures.size + 4
            val signer = ByteArray(signerSize)
            offset = 0
            writeIntLE(signer, offset, signerData.size); offset += 4
            System.arraycopy(signerData, 0, signer, offset, signerData.size); offset += signerData.size
            writeIntLE(signer, offset, signatures.size); offset += 4
            System.arraycopy(signatures, 0, signer, offset, signatures.size); offset += signatures.size
            writeIntLE(signer, offset, 0) // 公钥长度=0

            // 6. 构建签名者序列: 长度(4) + 签名者
            val signersSequence = ByteArray(4 + signer.size)
            writeIntLE(signersSequence, 0, signer.size)
            System.arraycopy(signer, 0, signersSequence, 4, signer.size)

            // 7. 构建 ID-value pair: value_len(8) + ID(4) + value(签名者序列)
            val idValuePair = ByteArray(8 + 4 + signersSequence.size)
            writeLongLE(idValuePair, 0, signersSequence.size.toLong())
            writeIntLE(idValuePair, 8, signatureId)
            System.arraycopy(signersSequence, 0, idValuePair, 12, signersSequence.size)

            // 8. 构建完整的 APK Signing Block: [size(8)] [ID-value pairs] [size(8)] [magic(16)]
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.UTF_8)
            val blockSize = idValuePair.size + 8 + 16
            val signingBlock = ByteArray(8 + idValuePair.size + 8 + 16)
            offset = 0
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            System.arraycopy(idValuePair, 0, signingBlock, offset, idValuePair.size); offset += idValuePair.size
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            System.arraycopy(magicBytes, 0, signingBlock, offset, magicBytes.size)

            return signingBlock
        }

        /**
         * 合并多个签名块到一个 APK Signing Block 中
         */
        private fun combineSigningBlocks(vararg blocks: ByteArray): ByteArray {
            // 提取所有 ID-value pairs
            var totalIdValuePairsSize = 0
            for (block in blocks) {
                // block 结构: [size(8)] [ID-value pairs] [size(8)] [magic(16)]
                totalIdValuePairsSize += block.size - 8 - 8 - 16
            }

            val idValuePairs = ByteArray(totalIdValuePairsSize)
            var offset = 0
            for (block in blocks) {
                val pairSize = block.size - 8 - 8 - 16
                System.arraycopy(block, 8, idValuePairs, offset, pairSize)
                offset += pairSize
            }

            // 构建新的 APK Signing Block
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.UTF_8)
            val blockSize = idValuePairs.size + 8 + 16
            val signingBlock = ByteArray(8 + idValuePairs.size + 8 + 16)
            offset = 0
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            System.arraycopy(idValuePairs, 0, signingBlock, offset, idValuePairs.size); offset += idValuePairs.size
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            System.arraycopy(magicBytes, 0, signingBlock, offset, magicBytes.size)

            return signingBlock
        }

        /**
         * 将签名块插入到 APK 中，更新中央目录和 EOCD 的偏移量
         */
        private fun insertSigningBlock(
            unsignedApk: File,
            signedApk: File,
            signingBlock: ByteArray,
            centralDirOffset: Long
        ) {
            val data = unsignedApk.readBytes()

            FileOutputStream(signedApk).use { output ->
                // 1. 写入内容（从开始到中央目录之前）
                output.write(data, 0, centralDirOffset.toInt())

                // 2. 写入签名块
                output.write(signingBlock)

                // 3. 写入中央目录和 EOCD（需要更新 EOCD 中的中央目录偏移量）
                val centralDirAndEocd = data.copyOfRange(centralDirOffset.toInt(), data.size)

                val eocdBuffer = ByteBuffer.wrap(centralDirAndEocd).order(ByteOrder.LITTLE_ENDIAN)
                var eocdPos = -1
                for (i in (centralDirAndEocd.size - 22) downTo 0) {
                    if (eocdBuffer.getInt(i) == EOCD_SIGNATURE) {
                        eocdPos = i
                        break
                    }
                }
                if (eocdPos >= 0) {
                    val newOffset = centralDirOffset + signingBlock.size
                    eocdBuffer.putInt(eocdPos + 16, newOffset.toInt())
                }

                output.write(centralDirAndEocd)
            }

            FileLogger.d(TAG, "签名块插入完成，新中央目录偏移量: ${centralDirOffset + signingBlock.size}")
        }
    }
}
