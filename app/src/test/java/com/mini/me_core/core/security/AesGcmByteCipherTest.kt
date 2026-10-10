package com.mini.me_core.core.security

import java.util.Arrays
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AesGcmByteCipher] 纯 JVM 单测：密钥以随机 AES 密钥注入，不碰 AndroidKeyStore。
 *
 * 覆盖：roundtrip / 两次加密 IV 与密文均不重复 / 篡改密文抛异常且不回吐明文 /
 * 过短线格式拒绝。
 */
class AesGcmByteCipherTest {

    private fun newCipher(): AesGcmByteCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return AesGcmByteCipher(key)
    }

    @Test
    fun encrypt_decrypt_roundTrip() {
        val cipher = newCipher()
        val plain = "供应商 API Key / git token 明文 🚀".toByteArray(Charsets.UTF_8)
        val wire = cipher.encrypt(plain)
        assertArrayEquals(plain, cipher.decrypt(wire))
    }

    @Test
    fun encrypt_emptyPlaintext_roundTrip() {
        val cipher = newCipher()
        val wire = cipher.encrypt(ByteArray(0))
        assertArrayEquals(ByteArray(0), cipher.decrypt(wire))
    }

    @Test
    fun encrypt_twice_ivAndCiphertextDiffer() {
        val cipher = newCipher()
        val plain = "same-input".toByteArray(Charsets.UTF_8)
        val w1 = cipher.encrypt(plain)
        val w2 = cipher.encrypt(plain)
        // IV（前 12B）必须不同
        assertFalse(Arrays.equals(w1.copyOfRange(0, 12), w2.copyOfRange(0, 12)))
        // 整体密文（含 tag）也必须不同
        assertFalse(Arrays.equals(w1, w2))
        // 但都能解回同一明文
        assertArrayEquals(cipher.decrypt(w1), cipher.decrypt(w2))
    }

    @Test
    fun decrypt_tamperedCiphertext_throwsAndNeverEchoes() {
        val cipher = newCipher()
        val plain = "secret-value".toByteArray(Charsets.UTF_8)
        val wire = cipher.encrypt(plain)

        // 翻转密文中的一个字节（不在 IV 段）
        val tampered = wire.copyOf()
        tampered[wire.size - 3] = (tampered[wire.size - 3].toInt() xor 0x01).toByte()

        val ex = assertThrows(AuthTagMismatchException::class.java) {
            cipher.decrypt(tampered)
        }
        // 失败时绝不回吐密文当明文：异常消息不含原始 wire 字节
        assertFalse(ex.message?.contains(String(plain)) == true)
    }

    @Test
    fun decrypt_tamperedIv_throws() {
        val cipher = newCipher()
        val wire = cipher.encrypt("data".toByteArray())
        val tampered = wire.copyOf()
        tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
        assertThrows(AuthTagMismatchException::class.java) { cipher.decrypt(tampered) }
    }

    @Test
    fun decrypt_tooShort_throws() {
        val cipher = newCipher()
        assertThrows(AuthTagMismatchException::class.java) { cipher.decrypt(ByteArray(8)) }
    }

    @Test
    fun wire_format_containsRandomIvAndTag() {
        val cipher = newCipher()
        val plain = "x".toByteArray()
        val wire = cipher.encrypt(plain)
        // IV(12) + tag(16) 起步
        assertTrue(wire.size >= AesGcmByteCipher.MIN_WIRE_LEN)
    }
}
