package com.mini.me_core.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UserPasswordBackupCrypto] 纯 JVM 单测：PBKDF2 + AES-128-GCM + HMAC 尾的加解密闭环。
 * 覆盖：round-trip / 错误密码 / 密文篡改 / 非备份文件 / 头嗅探。
 */
class UserPasswordBackupCryptoTest {

    private val crypto = UserPasswordBackupCrypto()

    @Test
    fun encrypt_decrypt_roundTrip() {
        val plain = "MiniMe-core 用户密码加密备份 payload（中文 + emoji 🚀）".toByteArray(Charsets.UTF_8)
        val password = "correct horse battery staple".toCharArray()

        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write(plain) }
        val encrypted = out.toByteArray()

        // 文件结构：64B header + ciphertext(+16B GCM tag) + 32B HMAC 尾
        assertTrue(encrypted.size > 64 + 16 + 32)

        val decrypted = crypto.decryptingInputStream(ByteArrayInputStream(encrypted), password)
            .use { it.readBytes() }
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun encrypt_emptyPayload_roundTrip() {
        val password = "pw".toCharArray()
        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { /* 空 payload */ }
        val decrypted = crypto.decryptingInputStream(ByteArrayInputStream(out.toByteArray()), password)
            .use { it.readBytes() }
        assertTrue(decrypted.isEmpty())
    }

    @Test
    fun decrypt_wrongPassword_throws() {
        val plain = "encrypted payload".toByteArray(Charsets.UTF_8)
        val password = "right-password".toCharArray()

        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write(plain) }

        assertThrows(UserPasswordBackupCrypto.BackupWrongPasswordException::class.java) {
            crypto.decryptingInputStream(
                ByteArrayInputStream(out.toByteArray()),
                "wrong-password".toCharArray()
            ).use { it.readBytes() }
        }
    }

    @Test
    fun decrypt_tamperedCiphertext_throws() {
        val password = "pw".toCharArray()
        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write("payload".toByteArray(Charsets.UTF_8)) }

        val bytes = out.toByteArray()
        // 翻转 64B header 之后的 payload 区一个字节 → HMAC 校验必然失败
        bytes[64] = (bytes[64].toInt() xor 0x01).toByte()

        assertThrows(UserPasswordBackupCrypto.BackupWrongPasswordException::class.java) {
            crypto.decryptingInputStream(ByteArrayInputStream(bytes), password).use { it.readBytes() }
        }
    }

    @Test
    fun decrypt_tamperedHmacTail_throws() {
        val password = "pw".toCharArray()
        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write("payload".toByteArray(Charsets.UTF_8)) }

        val bytes = out.toByteArray()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()

        assertThrows(UserPasswordBackupCrypto.BackupWrongPasswordException::class.java) {
            crypto.decryptingInputStream(ByteArrayInputStream(bytes), password).use { it.readBytes() }
        }
    }

    @Test
    fun decrypt_notBackupFile_throwsTampered() {
        val garbage = ByteArray(64) { 0x42 }
        assertThrows(UserPasswordBackupCrypto.BackupTamperedException::class.java) {
            crypto.decryptingInputStream(ByteArrayInputStream(garbage), "pw".toCharArray())
        }
    }

    @Test
    fun decrypt_tooShort_throwsTampered() {
        // 不足 64B header
        assertThrows(UserPasswordBackupCrypto.BackupTamperedException::class.java) {
            crypto.decryptingInputStream(ByteArrayInputStream(ByteArray(10)), "pw".toCharArray())
        }
    }

    @Test
    fun sniffHeader_validAndInvalid() {
        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, "pw".toCharArray()).use { it.write("x".toByteArray(Charsets.UTF_8)) }

        val info = crypto.sniffHeader(ByteArrayInputStream(out.toByteArray()))
        assertNotNull(info)
        assertEquals(1, info!!.version.toInt())
        assertFalse(info.iteration <= 0)
        assertEquals(16, info.salt.size)
        assertEquals(12, info.iv.size)

        assertNull(crypto.sniffHeader(ByteArrayInputStream(ByteArray(64) { 0x42 })))
        // 输入不足 64B 也不抛，返回 null
        assertNull(crypto.sniffHeader(ByteArrayInputStream(ByteArray(3))))
    }

    // ============== 迭代数钳制 ==============

    @Test
    fun encrypt_iterBelowMin_clampedToMin() {
        val out = ByteArrayOutputStream()
        // 显式传过低迭代数：应被钳到下限 120_000。
        crypto.encryptingOutputStream(out, "pw".toCharArray(), explicitIter = 1_000).use {
            it.write("x".toByteArray())
        }
        val info = crypto.sniffHeader(ByteArrayInputStream(out.toByteArray()))
        assertNotNull(info)
        assertEquals(120_000, info!!.iteration)
    }

    @Test
    fun encrypt_iterAboveMax_clampedToMax() {
        val out = ByteArrayOutputStream()
        // 显式传过高迭代数：应被钳到上限 2_000_000（防 DoS）。
        crypto.encryptingOutputStream(out, "pw".toCharArray(), explicitIter = 50_000_000).use {
            it.write("x".toByteArray())
        }
        val info = crypto.sniffHeader(ByteArrayInputStream(out.toByteArray()))
        assertNotNull(info)
        assertEquals(2_000_000, info!!.iteration)
    }

    @Test
    fun decrypt_iterBelowMinHeader_throwsTampered() {
        // 手工构造一个迭代数过低（1_000）的 64B 头：解密应在派生密钥前拒绝。
        val header = buildFakeHeader(iter = 1_000)
        val stream = ByteArrayInputStream(header + ByteArray(48))
        assertThrows(UserPasswordBackupCrypto.BackupTamperedException::class.java) {
            crypto.decryptingInputStream(stream, "pw".toCharArray()).use { it.readBytes() }
        }
    }

    @Test
    fun decrypt_iterAboveMaxHeader_throwsTampered() {
        // 手工构造一个迭代数过高（Int.MAX_VALUE）的 64B 头：解密应在派生密钥前拒绝（防 DoS）。
        val header = buildFakeHeader(iter = Int.MAX_VALUE)
        val stream = ByteArrayInputStream(header + ByteArray(48))
        assertThrows(UserPasswordBackupCrypto.BackupTamperedException::class.java) {
            crypto.decryptingInputStream(stream, "pw".toCharArray()).use { it.readBytes() }
        }
    }

    // ============== 流式分块加解密（>chunk） ==============

    @Test
    fun encrypt_largePayload_streamingRoundTrip() {
        // 256KB payload，远大于内部 64KB 分块，验证 hold-back 48B(tag+HMAC) 跨多块正确。
        val plain = ByteArray(256 * 1024) { (it % 251).toByte() }
        val password = "streaming-pw".toCharArray()

        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write(plain) }
        val encrypted = out.toByteArray()

        val decrypted = crypto.decryptingInputStream(ByteArrayInputStream(encrypted), password)
            .use { it.readBytes() }
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun decrypt_largePayload_tamperedThrows() {
        val plain = ByteArray(200 * 1024) { (it % 131).toByte() }
        val password = "pw".toCharArray()
        val out = ByteArrayOutputStream()
        crypto.encryptingOutputStream(out, password).use { it.write(plain) }
        val bytes = out.toByteArray()

        // 翻转中间密文一个字节 → GCM tag / HMAC 必然失败。
        bytes[64 + 40_000] = (bytes[64 + 40_000].toInt() xor 0x01).toByte()

        assertThrows(UserPasswordBackupCrypto.BackupWrongPasswordException::class.java) {
            crypto.decryptingInputStream(ByteArrayInputStream(bytes), password).use { it.readBytes() }
        }
    }

    private fun buildFakeHeader(iter: Int): ByteArray {
        val header = ByteArray(64)
        ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).apply {
            putShort(0x5243.toShort()) // 'RC'
            put(0x01) // version
            put(ByteArray(16)) // salt
            putInt(iter)
            put(ByteArray(12)) // iv
        }
        return header
    }
}
