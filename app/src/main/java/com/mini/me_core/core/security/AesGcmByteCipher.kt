package com.mini.me_core.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 可单测的 AES-GCM 字节加解密引擎（不依赖 AndroidKeyStore）。
 *
 * 设计目的：把「随机 IV + AES/GCM/NoPadding + 128bit tag」的纯密码学核心从
 * AndroidKeyStore / EncryptedSharedPreferences 中抽离出来，密钥以 [SecretKey] 注入：
 *  - Android 层用 AndroidKeyStore 派生/包裹密钥后注入；
 *  - JVM 单测用随机生成的 AES 密钥直接注入，验证 roundtrip / IV 不重复 / 篡改即失败。
 *
 * 线格式（与 [UnifiedKeyManager] 既有 V2 口径一致）：
 *   `IV(12B) || ciphertext || 16B GCM tag`
 * - IV 每次加密用 [SecureRandom] 随机生成，绝不复用；
 * - tag 校验由 [Cipher.doFinal] 在解密末尾常量时间完成，失败抛 [AuthTagMismatchException]。
 */
class AesGcmByteCipher(
    private val secretKey: SecretKey,
    private val random: SecureRandom = SecureRandom(),
) {

    /** 加密：返回 `IV || ciphertext || tag`。 */
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plaintext)
        val out = ByteArray(iv.size + body.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(body, 0, out, iv.size, body.size)
        return out
    }

    /**
     * 解密：输入 `IV || ciphertext || tag`，返回明文。
     * @throws AuthTagMismatchException 密文被篡改 / tag 校验失败 / 格式非法。
     */
    fun decrypt(wire: ByteArray): ByteArray {
        if (wire.size < MIN_WIRE_LEN) {
            throw AuthTagMismatchException("密文长度不足（${wire.size}B < 最小 $MIN_WIRE_LEN B）")
        }
        val iv = wire.copyOfRange(0, IV_LEN)
        val body = wire.copyOfRange(IV_LEN, wire.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            throw AuthTagMismatchException("GCM tag 校验失败或密文被篡改", e)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_LEN = 16
        const val IV_LEN = 12

        /** 线格式最小长度 = IV(12) + tag(16)（空明文也至少有 tag）。 */
        const val MIN_WIRE_LEN = IV_LEN + GCM_TAG_LEN
    }
}

/** GCM 认证 tag 校验失败 / 密文被篡改时抛出的类型化异常（fail-closed，绝不回吐密文）。 */
class AuthTagMismatchException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)
