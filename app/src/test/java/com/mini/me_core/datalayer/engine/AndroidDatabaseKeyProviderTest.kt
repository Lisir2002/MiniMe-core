package com.mini.me_core.datalayer.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [AndroidDatabaseKeyProvider] 单元测试（设计文档 db-encryption-migration-design.md §4 / §10.2）。
 *
 * 测试覆盖：
 * - 密钥首次生成与持久化
 * - 密钥解包一致性（多次调用返回同一 DEK）
 * - 每库独立 DEK
 * - isInitialized 状态
 * - emergencyReset 清除
 * - encodePassphrase 编码正确性
 * - fail-close 行为（异常不静默）
 *
 * 注意：本测试使用 Robolectric 模拟 Android Keystore 与 SharedPreferences。
 * Robolectric 的 Keystore 为软件模拟，加密算法与真机一致，但不涉及硬件 TEE。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AndroidDatabaseKeyProviderTest {

    private lateinit var context: Context
    private lateinit var keyProvider: AndroidDatabaseKeyProvider
    private lateinit var testMasterKey: SecretKey

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每个测试前清除 SharedPreferences，避免测试间污染
        context.getSharedPreferences("minime_db_keys", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // 生成软件 AES-256 密钥作为测试用 MasterKey。
        // 原因：Robolectric 不支持 KeyGenerator.getInstance("AES", "AndroidKeyStore")，
        // 通过子类覆盖 getMasterKey() 注入软件密钥，wrapDek/unwrapDek 使用
        // Cipher.getInstance("AES/GCM/NoPadding")（默认软件 provider），加密算法与真机一致。
        testMasterKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keyProvider = TestAndroidDatabaseKeyProvider(context, testMasterKey)
    }

    @After
    fun tearDown() {
        // 清除 SharedPreferences
        context.getSharedPreferences("minime_db_keys", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    /**
     * 测试用子类：覆盖 getMasterKey() 返回软件生成的 AES 密钥，
     * 绕过 Robolectric 对 AndroidKeyStore 的限制。
     */
    private class TestAndroidDatabaseKeyProvider(
        context: Context,
        private val testKey: SecretKey,
    ) : AndroidDatabaseKeyProvider(context) {
        override fun obtainMasterKey(): SecretKey = testKey
    }

    // ── isInitialized 状态测试 ──

    @Test
    fun `isInitialized returns false before first getPassphrase`() = runBlocking {
        assertFalse(keyProvider.isInitialized(LibName.AGENT))
        assertFalse(keyProvider.isInitialized(LibName.CREDENTIALS))
        assertFalse(keyProvider.isInitialized(LibName.SETTINGS))
    }

    @Test
    fun `isInitialized returns true after first getPassphrase`() = runBlocking {
        val dek = keyProvider.getPassphrase(LibName.AGENT)
        try {
            assertTrue(keyProvider.isInitialized(LibName.AGENT))
            // 其他库仍未初始化
            assertFalse(keyProvider.isInitialized(LibName.CREDENTIALS))
        } finally {
            dek.fill(0)
        }
    }

    // ── DEK 生成与解包一致性测试 ──

    @Test
    fun `getPassphrase first call generates DEK of 32 bytes`() = runBlocking {
        val dek = keyProvider.getPassphrase(LibName.AGENT)
        try {
            assertEquals(32, dek.size)
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun `getPassphrase second call returns same DEK (unwraps correctly)`() = runBlocking {
        val dek1 = keyProvider.getPassphrase(LibName.AGENT)
        val dek2 = keyProvider.getPassphrase(LibName.AGENT)
        try {
            assertArrayEquals(dek1, dek2)
        } finally {
            dek1.fill(0)
            dek2.fill(0)
        }
    }

    @Test
    fun `getPassphrase persists across provider instances (wrapped DEK survives)`() =
        runBlocking {
            val dek1 = keyProvider.getPassphrase(LibName.AGENT)
            // 创建新的 provider 实例（模拟 App 重启），使用相同的测试 MasterKey
            val newProvider = TestAndroidDatabaseKeyProvider(context, testMasterKey)
            val dek2 = newProvider.getPassphrase(LibName.AGENT)
            try {
                assertArrayEquals(dek1, dek2)
            } finally {
                dek1.fill(0)
                dek2.fill(0)
            }
        }

    // ── 每库独立 DEK 测试 ──

    @Test
    fun `different libraries have different DEKs`() = runBlocking {
        val deks = mutableMapOf<LibName, ByteArray>()
        try {
            for (lib in LibName.entries) {
                deks[lib] = keyProvider.getPassphrase(lib)
            }
            // 所有库都已初始化
            for (lib in LibName.entries) {
                assertTrue(keyProvider.isInitialized(lib))
            }
            // 两两比较，确保不同
            val libs = LibName.entries
            for (i in libs.indices) {
                for (j in i + 1 until libs.size) {
                    assertNotEquals(
                        "DEK for ${libs[i]} should differ from ${libs[j]}",
                        deks[libs[i]]!!.contentToString(),
                        deks[libs[j]]!!.contentToString(),
                    )
                }
            }
        } finally {
            deks.values.forEach { it.fill(0) }
        }
    }

    // ── emergencyReset 测试 ──

    @Test
    fun `emergencyReset clears all DEK wrapped keys`() = runBlocking {
        // 先初始化所有库
        val deks = mutableListOf<ByteArray>()
        try {
            for (lib in LibName.entries) {
                deks.add(keyProvider.getPassphrase(lib))
            }
            for (lib in LibName.entries) {
                assertTrue(keyProvider.isInitialized(lib))
            }
            // 执行紧急清除
            keyProvider.emergencyReset()
            // 所有库都不再初始化
            for (lib in LibName.entries) {
                assertFalse(keyProvider.isInitialized(lib))
            }
        } finally {
            deks.forEach { it.fill(0) }
        }
    }

    @Test
    fun `after emergencyReset new getPassphrase generates fresh DEK`() = runBlocking {
        val oldDek = keyProvider.getPassphrase(LibName.AGENT)
        keyProvider.emergencyReset()
        val newDek = keyProvider.getPassphrase(LibName.AGENT)
        try {
            // 新 DEK 不应与旧 DEK 相同（随机生成，概率上不可能相同）
            assertNotEquals(oldDek.contentToString(), newDek.contentToString())
            assertTrue(keyProvider.isInitialized(LibName.AGENT))
        } finally {
            oldDek.fill(0)
            newDek.fill(0)
        }
    }

    // ── encodePassphrase 测试 ──

    @Test
    fun `encodePassphrase returns non-empty Base64 string`() {
        val dek = ByteArray(32) { it.toByte() }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek)
        assertTrue(passphrase.isNotEmpty())
        // Base64 编码 32 字节 = 44 字符（含 padding）
        assertEquals(44, passphrase.length)
        // 不含换行（NO_WRAP）
        assertFalse(passphrase.contains("\n"))
        assertFalse(passphrase.contains("\r"))
    }

    @Test
    fun `encodePassphrase different DEKs produce different passphrases`() {
        val dek1 = ByteArray(32) { 1 }
        val dek2 = ByteArray(32) { 2 }
        val p1 = AndroidDatabaseKeyProvider.encodePassphrase(dek1)
        val p2 = AndroidDatabaseKeyProvider.encodePassphrase(dek2)
        assertNotEquals(p1, p2)
    }

    @Test
    fun `encodePassphrase same DEK produces same passphrase (deterministic)`() {
        val dek = ByteArray(32) { 42 }
        val p1 = AndroidDatabaseKeyProvider.encodePassphrase(dek)
        val p2 = AndroidDatabaseKeyProvider.encodePassphrase(dek)
        assertEquals(p1, p2)
    }

    // ── fail-close 行为测试 ──

    @Test(expected = DatabaseEncryptionException::class)
    fun `unwrapDek with corrupted ciphertext throws DatabaseEncryptionException`() {
        runBlocking {
            // 先正常初始化
            val dek = keyProvider.getPassphrase(LibName.AGENT)
            dek.fill(0)
            // 手动篡改 SharedPreferences 中的包裹密文
            val prefs = context.getSharedPreferences("minime_db_keys", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("dek_wrapped_AGENT", "invalid_base64_ciphertext!!!")
                .commit()
            // 新 provider 实例解包篡改后的密文，应抛异常
            val newProvider = TestAndroidDatabaseKeyProvider(context, testMasterKey)
            newProvider.getPassphrase(LibName.AGENT)
        }
    }

    @Test
    fun `getPassphrase does not return empty or null array`() = runBlocking {
        val dek = keyProvider.getPassphrase(LibName.AGENT)
        try {
            assertTrue(dek.isNotEmpty())
            assertEquals(32, dek.size)
        } finally {
            dek.fill(0)
        }
    }

    // ── 密钥不进日志测试（验证异常消息不含密钥内容）──

    @Test
    fun `exception messages do not contain key material`() {
        runBlocking {
            // 篡改密文触发异常
            val prefs = context.getSharedPreferences("minime_db_keys", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("dek_wrapped_AGENT", "corrupted!!!")
                .commit()
            val newProvider = TestAndroidDatabaseKeyProvider(context, testMasterKey)
            try {
                newProvider.getPassphrase(LibName.AGENT)
            } catch (e: DatabaseEncryptionException) {
                // 异常消息不应包含密钥相关内容
                val msg = e.message ?: ""
                assertFalse(msg.contains("passphrase", ignoreCase = true))
                assertFalse(msg.contains("dek", ignoreCase = true))
                assertFalse(msg.contains("key=", ignoreCase = true))
                // 应包含库名（便于定位问题）
                assertTrue(msg.contains("AGENT"))
            }
        }
    }
}
