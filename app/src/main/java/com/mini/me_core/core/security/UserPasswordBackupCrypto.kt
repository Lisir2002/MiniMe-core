package com.mini.me_core.core.security

import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 用户密码加密备份：PBKDF2-HMAC-SHA256 + AES-128-GCM + HMAC-SHA256 完整性校验。
 *
 * 文件格式：
 *   - 头 64B：magic(2) = 0x5243 'RC' | version(1)=0x01 | salt(16) | iter(4, BE) | iv(12) | reserved(29)
 *   - 中间：AES-128-GCM(dataKey, payload tar.gz bytes) + 16B GCM tag
 *   - 尾 32B：HMAC-SHA256(hmacKey, header || entireCiphertextWithTag)
 *
 * 密钥派生：password → PBKDF2-HMAC-SHA256(iter) → 256-bit keyMaterial
 *    → split: dataKey(前 128bit) + hmacKey(后 128bit)
 *
 * 跨设备可用：不依赖 Android Keystore。
 *
 * 安全约定：
 *  - 迭代数钳制在 [MIN_ITER, MAX_ITER]：低于下限视为弱派生拒绝，高于上限拒绝以防 DoS
 *    （文件头里的 iter 是攻击者可控的，直接信任会被诱导做海量 PBKDF2 计算）；
 *  - 解密流式分块（[CHUNK_SIZE]），绝不整包 readBytes 进内存；
 *  - GCM tag 在流末尾 doFinal 校验（不依赖 CipherInputStream 吞 doFinal 的已知坑）。
 */
class UserPasswordBackupCrypto {

    private companion object {
        const val MAGIC: Short = 0x5243.toShort() // 'RC'
        const val VERSION: Byte = 0x01
        const val HEADER_SIZE = 64
        const val TAG_LEN_BITS = 128
        const val HMAC_LEN = 32
        const val KEY_LEN_BITS = 256
        const val DATA_KEY_LEN = 16 // 128 bit
        const val HMAC_KEY_LEN = 16 // 128 bit
        const val IV_LEN = 12
        const val GCM_TAG_LEN = 16

        /** PBKDF2 迭代数安全下限（低于此视为弱派生，拒绝）。 */
        const val MIN_ITER = 120_000

        /** PBKDF2 迭代数安全上限（高于此拒绝，防 DoS）。 */
        const val MAX_ITER = 2_000_000

        /** 默认迭代数（在安全区间内）。 */
        const val DEFAULT_ITER = MIN_ITER

        /** 解密/加密流式分块大小。 */
        const val CHUNK_SIZE = 64 * 1024
    }

    /**
     * 密码错误的异常。
     */
    class BackupWrongPasswordException(message: String? = null, cause: Throwable? = null) :
        Exception(message ?: "密码不正确或备份文件已损坏", cause)
    class BackupTamperedException(message: String? = null, cause: Throwable? = null) :
        Exception(message ?: "不是有效的 MiniMe-core 加密备份文件", cause)

    /**
     * 加密写入：返回的 OutputStream 关流时自动写 GCM tag + HMAC 尾。
     * 使用时需要确保外层 OutputStream 在 finally 中 close。
     */
    fun encryptingOutputStream(
        rawOut: OutputStream,
        password: CharArray,
        explicitIter: Int = DEFAULT_ITER,
    ): OutputStream {
        // 迭代数钳制到安全区间：过低提至下限（不存过弱参数），过高压到上限（防 DoS）。
        val iter = explicitIter.coerceIn(MIN_ITER, MAX_ITER)

        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LEN).apply { SecureRandom().nextBytes(this) }
        val (dataKey, hmacKey) = deriveKeys(password, salt, iter)

        // 写 64B header
        val header = buildHeader(salt, iter, iv)
        rawOut.write(header)

        // AES-128-GCM 加密层
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(TAG_LEN_BITS, iv))

        // 流式 tee：边写 rawOut 边对 (header || ciphertext || gcmTag) 累加 HMAC，
        // 不把整个密文缓冲在内存（备份包可能很大）。
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(header)
        val teeOut = object : OutputStream() {
            override fun write(b: Int) {
                mac.update(b.toByte())
                rawOut.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                mac.update(b, off, len)
                rawOut.write(b, off, len)
            }
        }

        val cipherOut = CipherOutputStream(teeOut, cipher)
        return object : FilterOutputStream(cipherOut) {
            override fun close() {
                // CipherOutputStream.close() 会写出 GCM tag（tee 已同步累加进 HMAC）
                cipherOut.close()
                // 写 32B HMAC 尾：HMAC(header || ciphertext || gcmTag)
                rawOut.write(mac.doFinal())
                rawOut.flush()
            }
        }
    }

    /**
     * 解密读取：返回的 InputStream 流式产出明文，自动验证 HMAC + GCM tag。
     *
     * 流式实现：分块读取密文，末尾 32B 暂存为 HMAC 尾（不喂给 GCM），其余喂给
     * Cipher.update()；流结束时 doFinal() 校验 GCM tag，并比对暂存的 HMAC。
     *
     * @throws BackupWrongPasswordException 密码错或文件损坏
     * @throws BackupTamperedException 文件头不是 RC v1 格式 / 迭代数越界
     */
    fun decryptingInputStream(rawIn: InputStream, password: CharArray): InputStream {
        val header = ByteArray(HEADER_SIZE).also { readFully(rawIn, it) }
        val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = buf.short
        if (magic != MAGIC) throw BackupTamperedException()
        val version = buf.get()
        if (version != VERSION) throw BackupTamperedException()
        val salt = ByteArray(16).also { buf.get(it) }
        val iter = buf.int
        val iv = ByteArray(IV_LEN).also { buf.get(it) }

        // 迭代数越界直接拒绝：过低=弱派生，过高=潜在 DoS。
        if (iter < MIN_ITER || iter > MAX_ITER) {
            throw BackupTamperedException("PBKDF2 迭代数越界: $iter（应在[$MIN_ITER, $MAX_ITER]）")
        }

        val (dataKey, hmacKey) = deriveKeys(password, salt, iter)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(TAG_LEN_BITS, iv))

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        mac.update(header)

        return GcmHmacDecryptStream(rawIn, cipher, mac)
    }

    /**
     * 嗅探备份文件头：从 [rawIn] 前 64B 解析元信息（不消费 payload）。
     * 返回 null 说明不是 RC v1 文件。
     */
    fun sniffHeader(rawIn: InputStream): BackupHeaderInfo? {
        return try {
            rawIn.mark(HEADER_SIZE)
            val header = ByteArray(HEADER_SIZE).also { readFully(rawIn, it) }
            rawIn.reset()
            val buf = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            if (buf.short != MAGIC) return null
            val version = buf.get()
            if (version != VERSION) return null
            val salt = ByteArray(16).also { buf.get(it) }
            val iter = buf.int
            val iv = ByteArray(IV_LEN).also { buf.get(it) }
            BackupHeaderInfo(version, iter, salt, iv)
        } catch (e: Exception) {
            null
        }
    }

    data class BackupHeaderInfo(
        val version: Byte,
        val iteration: Int,
        val salt: ByteArray,
        val iv: ByteArray
    )

    // ============== 内部辅助 ==============

    /**
     * 流式 GCM + HMAC 解密输入流。
     *
     * 线格式：ciphertext || 16B GCM tag || 32B HMAC。
     *
     * 关键不变量（fail-closed）：JCE GCM 在 doFinal() 校验 tag 前不会放行明文，
     * 因此本类**在 finish() 之前不向调用方吐任何明文**。实现上分块从 rawIn 读取：
     *  - 末尾 48B（16B tag + 32B HMAC）全程 hold-back，不喂给 cipher.update()；
     *  - 其余 ciphertext 分块喂给 cipher.update()（其输出先累积，不直接外放）；
     *  - EOF 时先常量时间比对 HMAC，再 cipher.doFinal(tag) 校验 GCM tag，
     *    通过后才把累积明文一次性放出。任一不符抛 [BackupWrongPasswordException]。
     */
    private class GcmHmacDecryptStream(
        private val rawIn: InputStream,
        private val cipher: Cipher,
        private val mac: Mac,
    ) : InputStream() {

        /** 末尾暂存：[0,16)=GCM tag，[16,48)=HMAC 尾。 */
        private val hold = ByteArray(GCM_TAG_LEN + HMAC_LEN)
        private var holdLen = 0
        private val holdTarget = GCM_TAG_LEN + HMAC_LEN

        /** 已解密出的明文（finish 前不外放）。 */
        private var plain: ByteArray = ByteArray(0)
        private var plainOff = 0
        private var plainLen = 0

        private var finished = false
        private var closed = false

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (plainLen == 0 && !finished) {
                runAllDecryption()
            }
            if (plainLen == 0) return -1
            val n = minOf(plainLen, len)
            System.arraycopy(plain, plainOff, b, off, n)
            plainOff += n
            plainLen -= n
            return n
        }

        /**
         * 分块读取并解密，直到流结束；末尾统一做 HMAC + GCM tag 校验。
         * 校验通过前 plain 不会被 read() 外放。
         */
        private fun runAllDecryption() {
            val buf = ByteArray(CHUNK_SIZE)
            val out = ByteArrayOutputStream()
            while (true) {
                val n = rawIn.read(buf)
                if (n < 0) break

                // 合并 hold + 新块，末尾 holdTarget 字节暂存为 (tag + HMAC 尾)，其余作为纯 ciphertext。
                val combined = ByteArray(holdLen + n)
                System.arraycopy(hold, 0, combined, 0, holdLen)
                System.arraycopy(buf, 0, combined, holdLen, n)

                val total = combined.size
                val bodyLen = (total - holdTarget).coerceAtLeast(0)
                val newHoldLen = total - bodyLen
                System.arraycopy(combined, bodyLen, hold, 0, newHoldLen)
                holdLen = newHoldLen

                if (bodyLen > 0) {
                    val body = combined.copyOfRange(0, bodyLen)
                    // HMAC 覆盖 header || ciphertext（tag 在 finish 时补喂）。
                    mac.update(body)
                    // GCM 在 doFinal 前通常不放行明文；update 若产出则先累积，不直接外放。
                    out.write(cipher.update(body))
                }
            }

            // EOF：尾部长必须恰好 48B（16 tag + 32 HMAC）。
            if (holdLen != holdTarget) {
                throw UserPasswordBackupCrypto.BackupWrongPasswordException("备份尾部被截断")
            }
            // HMAC 覆盖 header || ciphertextWithTag：补上暂存的 16B tag。
            mac.update(hold, 0, GCM_TAG_LEN)
            val computed = mac.doFinal()
            if (!MessageDigest.isEqual(computed, hold.copyOfRange(GCM_TAG_LEN, holdTarget))) {
                throw UserPasswordBackupCrypto.BackupWrongPasswordException("HMAC 校验失败，备份已损坏")
            }
            // 再 doFinal 校验 GCM tag。解密必须 fail-closed：任何底层异常（tag 不匹配、
            // bad padding 等）一律判为密码错/篡改；但不得吞因——把 cause 透传出去。
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            val finalPlain = try {
                cipher.doFinal(hold, 0, GCM_TAG_LEN)
            } catch (e: Exception) {
                throw UserPasswordBackupCrypto.BackupWrongPasswordException(
                    "GCM tag 校验失败或密文损坏",
                    e,
                )
            }
            out.write(finalPlain)
            plain = out.toByteArray()
            plainOff = 0
            plainLen = plain.size
            finished = true
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { rawIn.close() }
        }
    }

    private fun deriveKeys(password: CharArray, salt: ByteArray, iteration: Int): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iteration, KEY_LEN_BITS)
        val keyMaterial = factory.generateSecret(spec).encoded
        return keyMaterial.copyOf(DATA_KEY_LEN) to keyMaterial.copyOfRange(DATA_KEY_LEN, DATA_KEY_LEN + HMAC_KEY_LEN)
    }

    private fun buildHeader(salt: ByteArray, iter: Int, iv: ByteArray): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(MAGIC)
            put(VERSION)
            put(salt)
            putInt(iter)
            put(iv)
            // 剩余 29 bytes 保留为 0
        }
        return header
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) throw BackupTamperedException()
            offset += read
        }
    }
}
