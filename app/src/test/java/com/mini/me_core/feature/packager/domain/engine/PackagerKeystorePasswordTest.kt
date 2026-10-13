package com.mini.me_core.feature.packager.domain.engine

import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PackagerKeystorePassword] 纯 JVM 单测。
 *
 * 用内存假 [UnifiedKeyManager]（真实 AES-256-GCM 加解密，仅存储换成 Map）替代
 * Android Keystore；口令文件与密钥库均为真实文件操作（临时目录）。
 * 测试密钥库由 JDK keytool 生成，不依赖 BouncyCastle。
 *
 * 覆盖：新库随机口令 / 二次获取一致 / 口令文件为密文 / 旧库自动迁移 /
 * 失配 fail-close / Keystore 故障时新库抛错而旧库降级不阻塞签名 /
 * 半迁移状态（失配包裹口令）自愈。
 */
class PackagerKeystorePasswordTest {

    /** 内存假 UnifiedKeyManager：per-purpose AES-256-GCM，行为与真实实现一致 */
    private class FakeKeyManager(
        private val failOnEncrypt: Boolean = false,
        private val failOnDecrypt: Boolean = false,
    ) : UnifiedKeyManager {
        private val deks = mutableMapOf<String, SecretKey>()

        private fun dekFor(purpose: String): SecretKey = deks.getOrPut(purpose) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }

        override suspend fun getOrCreateDek(purpose: String): ByteArray = dekFor(purpose).encoded

        override suspend fun getDek(purpose: String): ByteArray? =
            deks[purpose]?.encoded ?: throw IllegalStateException("DEK 不存在")

        override suspend fun rotateDek(purpose: String): ByteArray =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                deks[purpose] = it
            }.encoded

        override fun isDekInitialized(purpose: String): Boolean = deks.containsKey(purpose)

        override suspend fun encrypt(purpose: String, plaintext: ByteArray): String {
            if (failOnEncrypt) throw IllegalStateException("模拟 Keystore 故障")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, dekFor(purpose))
            val combined = cipher.iv + cipher.doFinal(plaintext)
            return Base64.getEncoder().encodeToString(combined)
        }

        override suspend fun decrypt(purpose: String, ciphertextB64: String): ByteArray {
            if (failOnDecrypt) throw IllegalStateException("模拟 Keystore 故障")
            val combined = Base64.getDecoder().decode(ciphertextB64)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                dekFor(purpose),
                GCMParameterSpec(128, combined, 0, 12),
            )
            return cipher.doFinal(combined, 12, combined.size - 12)
        }

        override fun isMasterKeyAvailable(): Boolean = true

        override suspend fun emergencyReset() {
            deks.clear()
        }

        override suspend fun importDek(purpose: String, dek: ByteArray) {
            deks[purpose] = SecretKeySpec(dek, "AES")
        }
    }

    private val tmpDir: File =
        File(System.getProperty("java.io.tmpdir"), "pkgpw-${System.nanoTime()}")

    private fun keystoreFile(): File =
        File(tmpDir, "default.keystore").apply { parentFile?.mkdirs() }

    /** 以指定口令创建 PKCS12 密钥库（模拟历史版本产物或第三方工具产物） */
    private fun createKeystoreWith(file: File, alias: String, password: String) {
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
            .takeIf { it.exists() }?.absolutePath ?: "keytool"
        val proc = ProcessBuilder(
            keytool, "-genkeypair",
            "-alias", alias,
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=MiniMe Packager Test, OU=Test, O=MiniMe, C=CN",
            "-keystore", file.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", password,
            "-keypass", password,
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        assertTrue("keytool 应成功生成测试密钥库:\n$out", proc.waitFor() == 0)
    }

    private fun canOpen(file: File, password: String): Boolean = try {
        val keystore = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { keystore.load(it, password.toCharArray()) }
        keystore.containsAlias("minime_packager")
    } catch (e: Exception) {
        false
    }

    @Before
    fun setUp() {
        tmpDir.mkdirs()
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun newKeystore_returnsRandomPassword_persistedAsCiphertext() = runBlocking {
        val file = keystoreFile()
        val provider = PackagerKeystorePassword(FakeKeyManager())

        val pw = provider.obtain(file).concatToString()
        // 模拟 ApkSigner 用该口令创建密钥库（obtain 之后由调用方落盘）
        createKeystoreWith(file, "minime_packager", pw)
        val pw2 = provider.obtain(file).concatToString()

        assertNotEquals("minime_packager_2026", pw, "新库不得使用旧版硬编码口令")
        assertEquals("密钥库创建后二次获取应返回同一随机口令", pw, pw2)
        assertTrue("随机口令应具备足够熵，实际长度 ${pw.length}", pw.length >= 32)

        // 口令文件必须是密文：既不含本次明文口令，也不含旧版硬编码口令
        val pwFile = File(file.parentFile, "${file.name}.pw")
        assertTrue("应生成包裹口令文件", pwFile.exists())
        val stored = pwFile.readText()
        assertFalse("口令文件不得包含明文口令", stored.contains(pw))
        assertFalse("口令文件不得包含旧版硬编码口令", stored.contains("minime_packager_2026"))
    }

    @Test
    fun legacyKeystore_migratesToRandomPassword() = runBlocking {
        val file = keystoreFile()
        // 模拟历史版本：以旧版硬编码口令创建的密钥库
        createKeystoreWith(file, "minime_packager", "minime_packager_2026")

        val provider = PackagerKeystorePassword(FakeKeyManager())
        val pw = provider.obtain(file).concatToString()

        assertNotEquals("minime_packager_2026", pw, "迁移后不得仍是旧版硬编码口令")
        assertTrue("迁移后密钥库应可用新口令打开", canOpen(file, pw))
        assertFalse("迁移后旧口令应失效", canOpen(file, "minime_packager_2026"))
    }

    @Test
    fun corruptKeystore_throwsFailClosed() {
        runBlocking {
            val file = keystoreFile()
            // 第三方工具/损坏产物：口令与历史硬编码口令均不匹配
            createKeystoreWith(file, "minime_packager", "some-other-password")

            val provider = PackagerKeystorePassword(FakeKeyManager())
            assertThrows(IllegalStateException::class.java) {
                runBlocking { provider.obtain(file) }
            }
        }
    }

    @Test
    fun keyManagerFailure_newKeystore_failsClosed() {
        runBlocking {
            val file = keystoreFile()
            val provider = PackagerKeystorePassword(FakeKeyManager(failOnEncrypt = true))

            // Keystore 不可用时拒绝创建新库（不得静默降级为硬编码口令）
            assertThrows(IllegalStateException::class.java) {
                runBlocking { provider.obtain(file) }
            }
        }
    }

    @Test
    fun keyManagerFailure_legacyKeystore_stillSignable() = runBlocking {
        val file = keystoreFile()
        createKeystoreWith(file, "minime_packager", "minime_packager_2026")

        val provider = PackagerKeystorePassword(
            FakeKeyManager(failOnEncrypt = true, failOnDecrypt = true),
        )
        val pw = provider.obtain(file).concatToString()

        assertEquals("Keystore 故障时旧库应降级维持可签名，不阻塞用户", "minime_packager_2026", pw)
        assertTrue("降级口令应能打开历史密钥库", canOpen(file, pw))
    }

    @Test
    fun staleWrappedPassword_selfHealsViaLegacyFallback() = runBlocking {
        val file = keystoreFile()
        createKeystoreWith(file, "minime_packager", "minime_packager_2026")
        // 模拟半迁移状态：存在一个失配的包裹口令文件（上次迁移重存失败的自愈场景）
        File(file.parentFile, "${file.name}.pw").writeText("stale-ciphertext")

        val provider = PackagerKeystorePassword(FakeKeyManager())
        val pw = provider.obtain(file).concatToString()

        assertNotEquals("minime_packager_2026", pw, "应经旧口令回退完成迁移")
        assertTrue("自愈后密钥库应可用新口令打开", canOpen(file, pw))
    }
}
