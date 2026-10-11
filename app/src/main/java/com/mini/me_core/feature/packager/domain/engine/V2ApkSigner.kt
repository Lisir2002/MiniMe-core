package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
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

            // 1. 找到中央目录的起始偏移量与 EOCD 位置
            val centralDirOffset = findCentralDirOffset(unsignedApk)
            val apkData = unsignedApk.readBytes()
            val eocdOffset = findEocdOffset(apkData)
            FileLogger.d(TAG, "中央目录偏移量: $centralDirOffset, EOCD偏移量: $eocdOffset")

            // 2. 计算 content digest：apksigner 验证时将 APK 拆为三个 DataSource 独立分块
            //    [apkContents=0..centralDirOffset] + [centralDir=centralDirOffset..eocdOffset] + [eocd]
            //    EOCD 使用原始 unsigned APK 的值（验证时 verifyIntegrity 会将 central_dir_offset
            //    改回 apkContents.size()），而非签名后更新的值。每个 DataSource 独立按 1MB 分块，
            //    overall = 0x5a + total_chunk_count(LE) + 所有 chunk digest 拼接。
            val apkContents = apkData.copyOfRange(0, centralDirOffset.toInt())
            val centralDir = apkData.copyOfRange(centralDirOffset.toInt(), eocdOffset)
            val eocd = apkData.copyOfRange(eocdOffset, apkData.size)
            val contentDigest = computeContentDigest(apkContents, centralDir, eocd)
            FileLogger.d(TAG, "内容摘要计算完成: ${contentDigest.size} bytes")

            // 3. 获取证书的 DER 编码与公钥
            val certBytes = certificate.encoded
            val publicKeyBytes = certificate.publicKey.encoded
            FileLogger.d(TAG, "证书 DER 编码: ${certBytes.size} bytes, 公钥: ${publicKeyBytes.size} bytes")

            // 4. 构建完整的 signed-data 并用私钥签名
            val signedData = buildSignedData(contentDigest, certBytes)
            FileLogger.d(TAG, "signed-data 构建完成: ${signedData.size} bytes")
            val signatureBytes = signData(signedData, privateKey)
            FileLogger.d(TAG, "数据签名完成: ${signatureBytes.size} bytes")

            // 5. 构建 V2 签名块
            val v2SigningBlock = buildSigningBlock(
                signatureId = V2_SIGNATURE_ID,
                signedData = signedData,
                signatureBytes = signatureBytes,
                publicKeyBytes = publicKeyBytes
            )
            FileLogger.d(TAG, "V2 签名块构建完成: ${v2SigningBlock.size} bytes")

            // 6. V3 签名方案暂不启用：V3 的 signed_data 含 minSdk/maxSdk/proof-of-rotation，
            //    与 V2 格式不同，复用 V2 格式会被 apksigner 判定为 Malformed list of signers。
            //    自签名打包器无需密钥轮换，且 V2 已覆盖 minSdk 26+，只输出 V2 即可。
            FileLogger.d(TAG, "V3 签名块跳过（待正确实现 V3 signed_data 格式）")

            // 7. 更新 EOCD 的 central_dir_offset（指向签名块后的中央目录），写入签名后 APK
            val eocdBuffer = ByteBuffer.wrap(eocd).order(ByteOrder.LITTLE_ENDIAN)
            eocdBuffer.putInt(16, (centralDirOffset + v2SigningBlock.size).toInt())

            FileOutputStream(signedApk).use { output ->
                output.write(apkContents)
                output.write(v2SigningBlock)
                output.write(centralDir)
                output.write(eocd)
            }

            FileLogger.d(TAG, "V2 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
        }

        /**
         * 找到 ZIP 文件中央目录的起始偏移量（从 EOCD 中读取）
         */
        private fun findCentralDirOffset(apkFile: File): Long {
            val data = apkFile.readBytes()
            return findCentralDirOffset(data)
        }

        private fun findCentralDirOffset(data: ByteArray): Long {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            var pos = data.size - 22
            while (pos >= 0) {
                if (buffer.getInt(pos) == EOCD_SIGNATURE) {
                    return buffer.getInt(pos + 16).toLong() and 0xFFFFFFFFL
                }
                pos--
            }
            error("未找到 EOCD，无法确定中央目录偏移量")
        }

        /**
         * 找到 EOCD 在 ZIP 文件中的起始偏移量
         */
        private fun findEocdOffset(data: ByteArray): Int {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            var pos = data.size - 22
            while (pos >= 0) {
                if (buffer.getInt(pos) == EOCD_SIGNATURE) {
                    return pos
                }
                pos--
            }
            error("未找到 EOCD")
        }

        /**
         * 计算 APK 内容的 CHUNKED_SHA256 摘要（与 apksigner/AOSP 完全一致）
         *
         * apksigner 验证时将 APK 拆为三个 DataSource 独立按 1MB 分块：
         * [apkContents] + [centralDir] + [eocd]，每个 DataSource 的最后一个 chunk
         * 可以小于 1MB。overall digest = SHA256(0x5a | total_chunk_count_LE |
         * 所有 chunk digest 按顺序拼接)。EOCD 使用原始 unsigned APK 的值（验证时
         * verifyIntegrity 会将 central_dir_offset 改回 apkContents.size()）。
         */
        private fun computeContentDigest(
            apkContents: ByteArray,
            centralDir: ByteArray,
            eocd: ByteArray
        ): ByteArray {
            val chunkDigests = java.io.ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            var chunkCount = 0

            for (source in arrayOf(apkContents, centralDir, eocd)) {
                var offset = 0
                while (offset < source.size) {
                    val toRead = minOf(CHUNK_SIZE, source.size - offset)
                    val chunkData = java.io.ByteArrayOutputStream()
                    chunkData.write(0xa5)
                    buffer.clear()
                    buffer.putInt(toRead)
                    chunkData.write(buffer.array())
                    chunkData.write(source, offset, toRead)

                    val chunkDigest = MessageDigest.getInstance("SHA-256")
                        .digest(chunkData.toByteArray())

                    chunkDigests.write(chunkDigest)
                    offset += toRead
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
        /**
         * 构建 V2 签名的 signed-data（签名输入，必须与签名块中存储的 signed-data 完全一致）
         *
         * 结构: [digests_length(4)] [digests] [certificates_length(4)] [certificates] [additional_attributes_length(4)] [additional_attributes]
         * digests: [digest_length(4)] [signature_algorithm_id(4)] [digest_data_length(4)] [digest_data]
         * certificates: [certificate_length(4)] [certificate_data]
         */
        private fun buildSignedData(contentDigest: ByteArray, certBytes: ByteArray): ByteArray {
            // digest 记录内容: 算法ID(4) + 摘要长度(4) + 摘要(32) = 40
            val digestRecord = ByteArray(40)
            writeIntLE(digestRecord, 0, SIG_ALG_RSA_PKCS1_V15_SHA256)
            writeIntLE(digestRecord, 4, contentDigest.size)
            System.arraycopy(contentDigest, 0, digestRecord, 8, contentDigest.size)
            // digests 区域 = 记录长度(4) + 记录内容
            val digestsBlock = ByteArray(4 + digestRecord.size)
            writeIntLE(digestsBlock, 0, digestRecord.size)
            System.arraycopy(digestRecord, 0, digestsBlock, 4, digestRecord.size)
            // certificates 区域 = 证书长度(4) + 证书数据
            val certSequence = ByteArray(4 + certBytes.size)
            writeIntLE(certSequence, 0, certBytes.size)
            System.arraycopy(certBytes, 0, certSequence, 4, certBytes.size)
            // signed-data 本体：每个长度前缀紧跟其对应数据
            // [digests_len(4)][digests][certs_len(4)][certs][attrs_len(4)][attrs]
            // 注意：三个长度字段不能连续放在最前面，否则 apksigner 读完 digests_len
            // 后切出的区域会包含 certs_len/attrs_len，导致后续字段全部错位。
            val signerData = ByteArray(4 + digestsBlock.size + 4 + certSequence.size + 4)
            var offset = 0
            writeIntLE(signerData, offset, digestsBlock.size); offset += 4
            System.arraycopy(digestsBlock, 0, signerData, offset, digestsBlock.size); offset += digestsBlock.size
            writeIntLE(signerData, offset, certSequence.size); offset += 4
            System.arraycopy(certSequence, 0, signerData, offset, certSequence.size); offset += certSequence.size
            writeIntLE(signerData, offset, 0); offset += 4 // additional_attributes 长度=0
            return signerData
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
         * value: [签名者序列长度(4)] [签名者长度(4)] [签名者]
         * 签名者: [签名者数据长度(4)] [签名者数据] [签名序列长度(4)] [签名序列] [公钥长度(4)] [公钥]
         * 签名者数据: [摘要算法序列长度(4)] [证书序列长度(4)] [额外属性序列长度(4)] [摘要算法序列] [证书序列] [额外属性序列]
         * 摘要算法序列: [算法ID(4)] [摘要长度(4)] [摘要(32)]
         * 签名序列: [算法ID(4)] [签名长度(4)] [签名数据]
         */
        private fun buildSigningBlock(
            signatureId: Int,
            signedData: ByteArray,
            signatureBytes: ByteArray,
            publicKeyBytes: ByteArray
        ): ByteArray {
            // 1. 构建签名记录内容: 算法ID(4) + 签名长度(4) + 签名数据
            val signatureRecord = ByteArray(4 + 4 + signatureBytes.size)
            writeIntLE(signatureRecord, 0, SIG_ALG_RSA_PKCS1_V15_SHA256)
            writeIntLE(signatureRecord, 4, signatureBytes.size)
            System.arraycopy(signatureBytes, 0, signatureRecord, 8, signatureBytes.size)
            // signatures 区域 = 记录长度(4) + 记录内容
            val signaturesBlock = ByteArray(4 + signatureRecord.size)
            writeIntLE(signaturesBlock, 0, signatureRecord.size)
            System.arraycopy(signatureRecord, 0, signaturesBlock, 4, signatureRecord.size)

            // 2. 构建签名者: 签名者数据长度(4) + 签名者数据 + 签名区域长度(4) + 签名区域 + 公钥长度(4) + 公钥
            //    公钥必须写入：apksigner 验证器在 parseSigner 中要求 signer 携带公钥，
            //    公钥长度置 0 会被判定为 Malformed signer block。
            val signerSize = 4 + signedData.size + 4 + signaturesBlock.size + 4 + publicKeyBytes.size
            val signer = ByteArray(signerSize)
            var offset = 0
            writeIntLE(signer, offset, signedData.size); offset += 4
            System.arraycopy(signedData, 0, signer, offset, signedData.size); offset += signedData.size
            writeIntLE(signer, offset, signaturesBlock.size); offset += 4
            System.arraycopy(signaturesBlock, 0, signer, offset, signaturesBlock.size); offset += signaturesBlock.size
            writeIntLE(signer, offset, publicKeyBytes.size); offset += 4
            System.arraycopy(publicKeyBytes, 0, signer, offset, publicKeyBytes.size)

            // 6. 构建签名者序列: 序列长度(4) + 签名者长度(4) + 签名者
            //    AOSP V2 规范要求 signers 是 "length-prefixed sequence of length-prefixed
            //    signer"：每个 signer 前必须有独立的 signer_length 前缀。缺少此前缀时，
            //    apksigner 会把 signed_data_length 误读为 signer_length，切出错误的
            //    signer buffer，后续字段全部错位，最终在 parseSigner 读 signatures 时
            //    抛出 Negative length。
            val signerWithLength = ByteArray(4 + signer.size)
            writeIntLE(signerWithLength, 0, signer.size)
            System.arraycopy(signer, 0, signerWithLength, 4, signer.size)
            val signersSequence = ByteArray(4 + signerWithLength.size)
            writeIntLE(signersSequence, 0, signerWithLength.size)
            System.arraycopy(signerWithLength, 0, signersSequence, 4, signerWithLength.size)

            // 7. ID-value pair: value_len(8) + ID(4) + value(签名者序列)
            //    规范要求 length 字段包含 4 字节 ID（即 4 + value 长度），少算 4 字节会导致
            //    解析器读取 ID-value 时错位，apksigner 报 "Malformed list of signers"。
            val idValuePair = ByteArray(8 + 4 + signersSequence.size)
            writeLongLE(idValuePair, 0, (signersSequence.size + 4).toLong())
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

    }
}
