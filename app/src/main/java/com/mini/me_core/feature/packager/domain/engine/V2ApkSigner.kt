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
 * APK V2/V3 签名器（手动实现，基于 AOSP 公开标准）
 *
 * 完全不依赖 apksig 库，手动实现 APK Signature Scheme v2 + v3。
 * V2 签名从 Android 7.0（API 24）开始支持，Android 11（API 30）起强制要求。
 * V3 签名从 Android 9（API 28）开始支持，携带 minSdk/maxSdk 以支持密钥轮换演进。
 *
 * 实现参考：https://source.android.google.cn/docs/security/features/apksigning/v2
 *          https://source.android.google.cn/docs/security/features/apksigning/v3
 *
 * **双 scheme 输出策略**：每次签名同时写入 V2 与 V3 两个 ID-value pair。
 * - API 28+ 设备优先使用 V3（V3 解析失败时按 Android 规范回退 V2）；
 * - API 24-27 设备使用 V2；
 * - 两者覆盖同一 content digest，签名输入仅差 V3 尾部的 minSdk/maxSdk 8 字节。
 *
 * V3 未实现 proof-of-rotation（密钥轮换证明链）：本签名器面向「单签名者自签名」场景，
 * 无历史签名 lineage，proof-of-rotation 按规范为可选 attribute，留空合法。
 * 若未来需要轮换签名密钥，须在 V3 additional attributes 中补充 lineage 链。
 */
class V2ApkSigner {

    companion object {
        private const val TAG = "V2ApkSigner"

        /** APK Signing Block 魔数 */
        private const val APK_SIG_BLOCK_MAGIC = "APK Sig Block 42"

        /** V2 签名 ID */
        private const val V2_SIGNATURE_ID = 0x7109871a

        /**
         * V3 签名 ID（APK Signing Block ID 0xf05368c0）
         *
         * 0xf05368c0 = 4029293760 超出有符号 Int 正值范围，Kotlin 字面量会推断为 Long，
         * 而 writeIntLE / buildIdValuePair 需要 Int，故用 .toInt() 取得位模式 -265673536。
         * writeIntLE 写出的小端字节即 C0 68 53 F0，字节级与规范一致。
         */
        private const val V3_SIGNATURE_ID = 0xf05368c0.toInt()

        /** 签名算法：RSA PKCS#1 v1.5 with SHA256 */
        private const val SIG_ALG_RSA_PKCS1_V15_SHA256 = 0x0103

        /** 分块大小：1MB */
        private const val CHUNK_SIZE = 1048576

        /** ZIP EOCD 签名 */
        private const val EOCD_SIGNATURE = 0x06054b50

        /** 签名者声明的最低受支持 API（与项目 minSdk 26 一致；V2 已覆盖 API 24+）。 */
        const val DEFAULT_MIN_SDK = 26

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
         * 对 APK 进行 V2 + V3 签名
         *
         * @param minSdkVersion 写入 V3 signed_data 的最低受支持 API（默认 [DEFAULT_MIN_SDK]=26）。
         *   V2 scheme 无此字段，不受影响。
         */
        fun sign(
            unsignedApk: File,
            signedApk: File,
            privateKey: PrivateKey,
            certificate: X509Certificate,
            minSdkVersion: Int = DEFAULT_MIN_SDK
        ) {
            FileLogger.d(TAG, "开始 V2+V3 签名: ${unsignedApk.name} (${unsignedApk.length()} bytes), minSdk=$minSdkVersion")

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

            // 4. 分别构建 V2 / V3 的 signed-data 并签名
            //    V2 signed-data 与 V3 仅差尾部 8 字节（minSdk/maxSdk），签名输入不同、签名不同。
            val v2SignedData = buildSignedData(contentDigest, certBytes, minSdk = null, maxSdk = null)
            val v3SignedData = buildSignedData(contentDigest, certBytes, minSdk = minSdkVersion, maxSdk = Int.MAX_VALUE)
            FileLogger.d(TAG, "signed-data 构建完成: v2=${v2SignedData.size}B, v3=${v3SignedData.size}B")
            val v2Signature = signData(v2SignedData, privateKey)
            val v3Signature = signData(v3SignedData, privateKey)
            FileLogger.d(TAG, "数据签名完成: v2=${v2Signature.size}B, v3=${v3Signature.size}B")

            // 5. 构建 APK Signing Block：V2 与 V3 两个 ID-value pair 并存
            val v2Pair = buildIdValuePair(
                V2_SIGNATURE_ID,
                buildSignersSequence(v2SignedData, v2Signature, publicKeyBytes)
            )
            val v3Pair = buildIdValuePair(
                V3_SIGNATURE_ID,
                buildSignersSequence(
                    v3SignedData,
                    v3Signature,
                    publicKeyBytes,
                    minSdk = minSdkVersion,
                    maxSdk = Int.MAX_VALUE
                )
            )
            val signingBlock = buildSigningBlock(listOf(v2Pair, v3Pair))
            FileLogger.d(TAG, "签名块构建完成: v2Pair=${v2Pair.size}B, v3Pair=${v3Pair.size}B, block=${signingBlock.size}B")

            // 6. 更新 EOCD 的 central_dir_offset（指向签名块后的中央目录），写入签名后 APK
            val eocdBuffer = ByteBuffer.wrap(eocd).order(ByteOrder.LITTLE_ENDIAN)
            eocdBuffer.putInt(16, (centralDirOffset + signingBlock.size).toInt())

            FileOutputStream(signedApk).use { output ->
                output.write(apkContents)
                output.write(signingBlock)
                output.write(centralDir)
                output.write(eocd)
            }

            FileLogger.d(TAG, "V2+V3 签名完成: ${signedApk.name} (${signedApk.length()} bytes)")
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
         * 构建签名器的 signed-data（签名输入，必须与签名块中存储的 signed-data 完全一致）
         *
         * 结构: [digests_length(4)] [digests] [certificates_length(4)] [certificates] [minSDK(4)] [maxSDK(4)] [additional_attributes_length(4)] [additional_attributes]
         * digests: [digest_length(4)] [signature_algorithm_id(4)] [digest_data_length(4)] [digest_data]
         * certificates: [certificate_length(4)] [certificate_data]
         *
         * V2 与 V3 的结构差异：V3 在 certificates 之后、additional_attributes 之前追加
         * minSdk / maxSdk 两个 uint32（小端）。[minSdk] 为 null 时产出 V2 格式。
         * 注意 minSdk/maxSdk 不在 V2 格式中（V2 无 SDK 范围概念），且不能放在
         * additional_attributes 之后——验证器按 certificates → minSdk → maxSdk → attrs
         * 的顺序读取（AOSP V3SchemeSigner.encodeSignedData 反汇编确认）。
         *
         * 实现参考：AOSP `v2/V2SchemeSigner.generateSignedData` 与
         * `v3/V3SchemeSigner.generateSignedData`（后者比前者多 putInt(minSdk)/putInt(maxSdk)，
         * 且位于 additional attributes 之前）。
         */
        private fun buildSignedData(
            contentDigest: ByteArray,
            certBytes: ByteArray,
            minSdk: Int? = null,
            maxSdk: Int? = null
        ): ByteArray {
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
            // [digests_len(4)][digests][certs_len(4)][certs]
            // 注意：三个长度字段不能连续放在最前面，否则 apksigner 读完 digests_len
            // 后切出的区域会包含 certs_len/attrs_len，导致后续字段全部错位。
            val baseSize = 4 + digestsBlock.size + 4 + certSequence.size + 4
            val isV3 = minSdk != null
            val signerData = ByteArray(if (isV3) baseSize + 8 else baseSize)
            var offset = 0
            writeIntLE(signerData, offset, digestsBlock.size); offset += 4
            System.arraycopy(digestsBlock, 0, signerData, offset, digestsBlock.size); offset += digestsBlock.size
            writeIntLE(signerData, offset, certSequence.size); offset += 4
            System.arraycopy(certSequence, 0, signerData, offset, certSequence.size); offset += certSequence.size
            if (isV3) {
                // V3 专属：minSdk/maxSdk 位于 certificates 之后、additional_attributes 之前。
                // 字节序经 apksigner.jar 内 V3SchemeSigner.encodeSignedData 反汇编确认：
                // put(digests) → put(certificates) → putInt(minSdk) → putInt(maxSdk) → put(attrs)。
                // 若把 attrs_len 放在 minSdk/maxSdk 之前，验证器会把 attrs_len 与 minSdk
                // 误读为 SDK 参数（实测报 minSdkVersion 0 maxSdkVersion 26）。
                writeIntLE(signerData, offset, minSdk); offset += 4
                writeIntLE(signerData, offset, maxSdk ?: Int.MAX_VALUE); offset += 4
            }
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
         * 构建签名者序列（signers sequence）——ID-value pair 的 value 部分。
         *
         * 结构: [签名者序列长度(4)] [签名者长度(4)] [签名者]
         * 签名者: [签名者数据长度(4)] [签名者数据] [签名序列长度(4)] [签名序列] [公钥长度(4)] [公钥]
         * 签名者数据: [摘要算法序列长度(4)] [证书序列长度(4)] [额外属性序列长度(4)] [摘要算法序列] [证书序列] [额外属性序列]
         * 摘要算法序列: [算法ID(4)] [摘要长度(4)] [摘要(32)]
         * 签名序列: [算法ID(4)] [签名长度(4)] [签名数据]
         *
         * **V3 差异**（经 apksigner.jar 内 V3SchemeSigner.encodeSigner 反汇编确认）：
         * 签名者数据之后、签名序列之前插入 [minSdk(4)] [maxSdk(4)]，即
         * [签名者数据长度][签名者数据] [minSdk][maxSdk] [签名序列长度][签名序列] [公钥长度][公钥]。
         * 验证器从签名者缓冲区读取这两个字段做 SDK 范围校验（minSdk >= 0 且 minSdk <= maxSdk），
         * 并回读签名者数据内部的 minSdk/maxSdk 交叉比对（不一致报
         * V3_MIN/MAX_SDK_VERSION_MISMATCH_BETWEEN_SIGNER_AND_SIGNED_DATA_RECORD）。
         * 缺失这两个字段时，验证器会把签名序列长度误读为 minSdk（实测报 minSdkVersion 268）。
         *
         * @param minSdk V3 签名者级 minSdk；null 表示 V2（无此字段）。
         * @param maxSdk V3 签名者级 maxSdk；须与 signedData 内部一致。
         */
        private fun buildSignersSequence(
            signedData: ByteArray,
            signatureBytes: ByteArray,
            publicKeyBytes: ByteArray,
            minSdk: Int? = null,
            maxSdk: Int? = null
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
            //    V3 在签名者数据与签名区域之间插入 minSdk/maxSdk（见方法 KDoc）。
            val isV3 = minSdk != null
            val signerSize = 4 + signedData.size + (if (isV3) 8 else 0) +
                4 + signaturesBlock.size + 4 + publicKeyBytes.size
            val signer = ByteArray(signerSize)
            var offset = 0
            writeIntLE(signer, offset, signedData.size); offset += 4
            System.arraycopy(signedData, 0, signer, offset, signedData.size); offset += signedData.size
            if (isV3) {
                writeIntLE(signer, offset, minSdk); offset += 4
                writeIntLE(signer, offset, maxSdk ?: Int.MAX_VALUE); offset += 4
            }
            writeIntLE(signer, offset, signaturesBlock.size); offset += 4
            System.arraycopy(signaturesBlock, 0, signer, offset, signaturesBlock.size); offset += signaturesBlock.size
            writeIntLE(signer, offset, publicKeyBytes.size); offset += 4
            System.arraycopy(publicKeyBytes, 0, signer, offset, publicKeyBytes.size)

            // 3. 构建签名者序列: 序列长度(4) + 签名者长度(4) + 签名者
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
            return signersSequence
        }

        /**
         * 构建 ID-value pair: value_len(8) + ID(4) + value
         * 规范要求 length 字段包含 4 字节 ID（即 4 + value 长度），少算 4 字节会导致
         * 解析器读取 ID-value 时错位，apksigner 报 "Malformed list of signers"。
         */
        private fun buildIdValuePair(signatureId: Int, value: ByteArray): ByteArray {
            val idValuePair = ByteArray(8 + 4 + value.size)
            writeLongLE(idValuePair, 0, (value.size + 4).toLong())
            writeIntLE(idValuePair, 8, signatureId)
            System.arraycopy(value, 0, idValuePair, 12, value.size)
            return idValuePair
        }

        /**
         * 构建完整的 APK Signing Block: [size(8)] [ID-value pairs...] [size(8)] [magic(16)]
         * size 为「pairs + 8 + 16」的字节数（不含首尾两个 size 字段自身，与 AOSP 一致）。
         */
        private fun buildSigningBlock(pairs: List<ByteArray>): ByteArray {
            val magicBytes = APK_SIG_BLOCK_MAGIC.toByteArray(Charsets.UTF_8)
            var pairsSize = 0
            for (p in pairs) pairsSize += p.size
            val blockSize = pairsSize + 8 + 16
            val signingBlock = ByteArray(8 + pairsSize + 8 + 16)
            var offset = 0
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            for (p in pairs) {
                System.arraycopy(p, 0, signingBlock, offset, p.size); offset += p.size
            }
            writeLongLE(signingBlock, offset, blockSize.toLong()); offset += 8
            System.arraycopy(magicBytes, 0, signingBlock, offset, magicBytes.size)
            return signingBlock
        }
    }
}
