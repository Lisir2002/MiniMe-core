package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.ByteArrayOutputStream
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
 *
 * V2 签名原理：
 * 1. 将 APK 内容分为 1MB 块，逐块计算 SHA256 摘要
 * 2. 将所有块摘要拼接后再计算一次 SHA256，得到内容摘要
 * 3. 构建签名数据（长度+算法ID+摘要），用私钥 RSA PKCS#1 v1.5 签名
 * 4. 构建 APK Signing Block（签名者数据+签名序列+证书链）
 * 5. 将签名块插入到 ZIP 内容和中央目录之间，更新偏移量
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

        /** SHA256 摘要标记 */
        private const val DIGEST_ID_SHA256: Byte = 0x5a

        /**
         * 对 APK 进行 V2 签名
         *
         * @param unsignedApk 未签名的 APK（已包含 V1 签名）
         * @param signedApk 签名后的 APK
         * @param privateKey 签名私钥
         * @param certificate 签名证书
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

            // 从文件末尾向前搜索 EOCD 签名
            var pos = data.size - 22 // EOCD 最小长度
            while (pos >= 0) {
                if (buffer.getInt(pos) == EOCD_SIGNATURE) {
                    // 中央目录偏移量在 EOCD 偏移 16 处（uint32）
                    return buffer.getInt(pos + 16).toLong() and 0xFFFFFFFFL
                }
                pos--
            }
            throw IllegalStateException("未找到 EOCD，无法确定中央目录偏移量")
        }

        /**
         * 计算 APK 内容的摘要（分块 SHA256）
         *
         * V2 签名的摘要计算：
         * 1. 将内容分为 1MB 块
         * 2. 对每个块计算 SHA256
         * 3. 构建摘要序列（块大小 + 每个块的摘要标记+长度+摘要）
         * 4. 对摘要序列计算 SHA256
         */
        private fun computeContentDigest(apkFile: File, centralDirOffset: Long): ByteArray {
            val digestStream = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // 写入块大小（uint32）
            buffer.putInt(CHUNK_SIZE)
            digestStream.write(buffer.array())

            // 分块读取内容并计算 SHA256
            FileInputStream(apkFile).use { fis ->
                var remaining = centralDirOffset
                val chunkBuffer = ByteArray(CHUNK_SIZE)

                while (remaining > 0) {
                    val toRead = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                    val bytesRead = fis.read(chunkBuffer, 0, toRead)
                    if (bytesRead <= 0) break

                    // 计算这个块的 SHA256
                    val chunkDigest = MessageDigest.getInstance("SHA-256")
                        .digest(chunkBuffer.copyOf(bytesRead))

                    // 写入摘要标记（0x5a 表示 SHA256）
                    digestStream.write(DIGEST_ID_SHA256.toInt())
                    // 写入摘要长度（uint32）
                    buffer.clear()
                    buffer.putInt(chunkDigest.size)
                    digestStream.write(buffer.array())
                    // 写入摘要数据
                    digestStream.write(chunkDigest)

                    remaining -= bytesRead
                }
            }

            // 对摘要序列计算 SHA256
            val finalDigest = MessageDigest.getInstance("SHA-256")
                .digest(digestStream.toByteArray())

            // 根据 AOSP 规范，最终内容摘要需要加上摘要标记和长度前缀
            // 格式: [摘要标记(1, 0x5a)] [摘要长度(4)] [SHA256摘要(32)]
            val result = ByteArrayOutputStream()
            result.write(DIGEST_ID_SHA256.toInt())
            val lenBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            lenBuffer.putInt(finalDigest.size)
            result.write(lenBuffer.array())
            result.write(finalDigest)

            return result.toByteArray()
        }

        /**
         * 构建签名数据（长度 + 签名算法ID + 摘要长度 + 摘要）
         *
         * 注意：长度字段表示长度字段之后的数据长度，即 4(算法ID) + 4(摘要长度) + 摘要数据长度
         */
        private fun buildSignatureData(contentDigest: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // 长度字段之后的数据长度：签名算法ID(4) + 摘要长度(4) + 摘要数据
            val dataLength = 4 + 4 + contentDigest.size

            // 写入长度（uint32）- 表示长度字段之后的数据长度
            buffer.putInt(dataLength)
            out.write(buffer.array())

            // 写入签名算法ID（uint32）
            buffer.clear()
            buffer.putInt(SIG_ALG_RSA_PKCS1_V15_SHA256)
            out.write(buffer.array())

            // 写入摘要长度（uint32）
            buffer.clear()
            buffer.putInt(contentDigest.size)
            out.write(buffer.array())

            // 写入摘要数据
            out.write(contentDigest)

            return out.toByteArray()
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
         * 构建单个签名方案的 APK Signing Block
         *
         * APK Signing Block 结构：
         * - 签名块大小（8字节，uint64）
         * - 魔数（8字节，"APK Sig Block 42"）
         * - ID-value pairs（一个或多个）
         * - 签名块大小（8字节，重复）
         * - 魔数（8字节，重复）
         *
         * 每个 ID-value pair：
         * - 值长度（8字节，uint64）
         * - ID（4字节，uint32）
         * - 值（可变长度）
         *
         * V2/V3 签名值结构：
         * - 签名者序列长度（4字节，uint32）
         * - 签名者序列
         *   - 签名者数据长度（4字节）+ 签名者数据
         *   - 签名序列长度（4字节）+ 签名序列
         *   - 公钥长度（4字节）+ 公钥数据（可为空）
         */
        private fun buildSigningBlock(
            signatureId: Int,
            contentDigest: ByteArray,
            signatureBytes: ByteArray,
            certBytes: ByteArray
        ): ByteArray {
            // 构建签名者数据（签名算法序列 + 证书序列 + 额外属性序列）
            val signerData = buildSignerData(contentDigest, certBytes)

            // 构建签名序列（算法ID + 签名长度 + 签名数据）
            val signatures = buildSignatures(signatureBytes)

            // 构建签名者（签名者数据 + 签名序列 + 公钥）
            val signer = ByteArrayOutputStream()
            val buffer4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // 签名者数据长度 + 数据
            buffer4.putInt(signerData.size)
            signer.write(buffer4.array())
            signer.write(signerData)

            // 签名序列长度 + 序列
            buffer4.clear()
            buffer4.putInt(signatures.size)
            signer.write(buffer4.array())
            signer.write(signatures)

            // 公钥长度 + 公钥（V2 签名中公钥可选，这里写入空）
            buffer4.clear()
            buffer4.putInt(0)
            signer.write(buffer4.array())

            val signerBytes = signer.toByteArray()

            // 构建签名者序列（长度 + 签名者）
            val signersSequence = ByteArrayOutputStream()
            buffer4.clear()
            buffer4.putInt(signerBytes.size)
            signersSequence.write(buffer4.array())
            signersSequence.write(signerBytes)

            // 构建 ID-value pair（值长度 + ID + 值）
            val idValuePair = ByteArrayOutputStream()
            val buffer8 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)

            // 值长度（uint64）= 签名者序列大小（不包括 value_len 和 ID 字段）
            val valueLength = signersSequence.size().toLong()
            buffer8.putLong(valueLength)
            idValuePair.write(buffer8.array())

            // ID（uint32）
            buffer4.clear()
            buffer4.putInt(signatureId)
            idValuePair.write(buffer4.array())

            // 值（签名者序列）
            idValuePair.write(signersSequence.toByteArray())

            val idValuePairBytes = idValuePair.toByteArray()

            // 构建完整的 APK Signing Block
            // 正确结构: [size(8)] [ID-value pairs] [size(8)] [magic(16)]
            val signingBlock = ByteArrayOutputStream()
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.UTF_8)

            // 签名块大小（不包括前8字节）= ID-value pairs + 末尾size(8) + magic(16)
            val blockSize = idValuePairBytes.size + 8 + 16

            // 写入签名块大小（uint64）
            buffer8.clear()
            buffer8.putLong(blockSize.toLong())
            signingBlock.write(buffer8.array())

            // 写入 ID-value pairs
            signingBlock.write(idValuePairBytes)

            // 写入签名块大小（重复）
            buffer8.clear()
            buffer8.putLong(blockSize.toLong())
            signingBlock.write(buffer8.array())

            // 写入魔数
            signingBlock.write(magicBytes)

            return signingBlock.toByteArray()
        }

        /**
         * 构建签名者数据（签名算法序列 + 证书序列 + 额外属性序列）
         *
         * 签名算法序列：每个算法包含 ID(4) + 摘要长度(4) + 摘要数据
         * 注意：这里的摘要是内容摘要（已带0x5a标记和长度前缀），不是签名数据
         */
        private fun buildSignerData(
            contentDigest: ByteArray,
            certBytes: ByteArray
        ): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // 直接使用内容摘要（已带0x5a标记和长度前缀，共37字节）
            val digest = contentDigest
            FileLogger.d(TAG, "签名者数据使用内容摘要: ${digest.size} bytes")

            // 签名算法序列（算法ID + 摘要长度 + 摘要）
            val digestAlgorithm = ByteArrayOutputStream()
            buffer.putInt(SIG_ALG_RSA_PKCS1_V15_SHA256)
            digestAlgorithm.write(buffer.array())
            buffer.clear()
            buffer.putInt(digest.size)
            digestAlgorithm.write(buffer.array())
            digestAlgorithm.write(digest)

            // 写入签名算法序列长度 + 序列
            buffer.clear()
            buffer.putInt(digestAlgorithm.size())
            out.write(buffer.array())
            out.write(digestAlgorithm.toByteArray())

            // 证书序列（证书长度 + 证书数据）
            val certSequence = ByteArrayOutputStream()
            buffer.clear()
            buffer.putInt(certBytes.size)
            certSequence.write(buffer.array())
            certSequence.write(certBytes)

            // 写入证书序列长度 + 序列
            buffer.clear()
            buffer.putInt(certSequence.size())
            out.write(buffer.array())
            out.write(certSequence.toByteArray())

            // 额外属性序列（空）
            buffer.clear()
            buffer.putInt(0)
            out.write(buffer.array())

            return out.toByteArray()
        }

        /**
         * 构建签名序列（算法ID + 签名长度 + 签名数据）
         */
        private fun buildSignatures(signatureBytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // 签名算法ID
            buffer.putInt(SIG_ALG_RSA_PKCS1_V15_SHA256)
            out.write(buffer.array())

            // 签名长度
            buffer.clear()
            buffer.putInt(signatureBytes.size)
            out.write(buffer.array())

            // 签名数据
            out.write(signatureBytes)

            return out.toByteArray()
        }

        /**
         * 合并多个签名方案的 ID-value pairs 到一个 APK Signing Block 中
         *
         * 输入的每个 block 都是完整的 APK Signing Block，结构为:
         * [size(8)] [ID-value pairs] [size(8)] [magic(16)]
         * 需要提取其中的 ID-value pair，然后重新构建一个包含所有 ID-value pairs 的 APK Signing Block。
         */
        private fun combineSigningBlocks(vararg blocks: ByteArray): ByteArray {
            val allIdValuePairs = ByteArrayOutputStream()

            for (block in blocks) {
                // 每个 block 的结构：大小(8) + ID-value pairs + 大小(8) + 魔数(16)
                // 提取 ID-value pairs（跳过前 8 字节 size，减去后 24 字节 size(8)+magic(16)）
                val idValuePairs = block.copyOfRange(8, block.size - 24)
                allIdValuePairs.write(idValuePairs)
            }

            val idValuePairsBytes = allIdValuePairs.toByteArray()

            // 构建新的 APK Signing Block
            // 正确结构: [size(8)] [ID-value pairs] [size(8)] [magic(16)]
            val signingBlock = ByteArrayOutputStream()
            val buffer8 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.UTF_8)

            // 签名块大小（不包括前8字节）= ID-value pairs + 末尾size(8) + magic(16)
            val blockSize = idValuePairsBytes.size + 8 + 16

            // 写入签名块大小
            buffer8.putLong(blockSize.toLong())
            signingBlock.write(buffer8.array())

            // 写入所有 ID-value pairs
            signingBlock.write(idValuePairsBytes)

            // 写入签名块大小（重复）
            buffer8.clear()
            buffer8.putLong(blockSize.toLong())
            signingBlock.write(buffer8.array())

            // 写入魔数
            signingBlock.write(magicBytes)

            return signingBlock.toByteArray()
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

                // 找到 EOCD 并更新中央目录偏移量
                val eocdBuffer = ByteBuffer.wrap(centralDirAndEocd).order(ByteOrder.LITTLE_ENDIAN)
                var eocdPos = -1
                for (i in (centralDirAndEocd.size - 22) downTo 0) {
                    if (eocdBuffer.getInt(i) == EOCD_SIGNATURE) {
                        eocdPos = i
                        break
                    }
                }
                if (eocdPos >= 0) {
                    // 更新中央目录偏移量（EOCD 偏移 16，uint32）
                    val newOffset = centralDirOffset + signingBlock.size
                    eocdBuffer.putInt(eocdPos + 16, newOffset.toInt())
                }

                output.write(centralDirAndEocd)
            }

            FileLogger.d(TAG, "签名块插入完成，新中央目录偏移量: ${centralDirOffset + signingBlock.size}")
        }
    }
}
