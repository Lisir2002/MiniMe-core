package com.mini.me_core.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [DEKManager] 单元测试。
 *
 * 注意：Robolectric 不支持 AndroidKeyStore 的 KeyGenerator，
 * 因此本测试聚焦于不依赖 Keystore IO 的纯逻辑验证。
 * 完整的 Keystore 集成测试由 AndroidDatabaseKeyProviderTest 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DEKManagerTest {

    @Test
    fun `singleton instance returns same object`() {
        val instance1 = DEKManager.getInstance()
        val instance2 = DEKManager.getInstance()
        assertEquals("单例应返回同一对象", instance1, instance2)
    }

    @Test
    fun `generateDek produces 256 bit key`() {
        val dek = DEKManager.getInstance().generateDek()
        assertNotNull("生成的 DEK 不应为 null", dek)
        assertTrue("DEK 算法应为 AES", dek.algorithm == "AES")
        assertEquals("DEK 应为 256 位（32 字节）", 32, dek.encoded.size)
    }

    @Test
    fun `generateDek produces unique keys`() {
        val dek1 = DEKManager.getInstance().generateDek()
        val dek2 = DEKManager.getInstance().generateDek()
        assertTrue("每次生成的 DEK 应不同", !dek1.encoded.contentEquals(dek2.encoded))
    }

    @Test
    fun `wrapDek and unwrapDek roundtrip with software key`() {
        val masterKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dek = DEKManager.getInstance().generateDek()

        val wrapped = DEKManager.getInstance().wrapDek(masterKey, dek)
        assertNotNull("包装结果不应为 null", wrapped)
        assertTrue("包装结果应为非空字符串", wrapped.isNotEmpty())

        val unwrapped = DEKManager.getInstance().unwrapDek(masterKey, wrapped)
        assertNotNull("解包结果不应为 null", unwrapped)
        assertEquals("解包后 DEK 长度应与原 DEK 一致", dek.encoded.size, unwrapped.encoded.size)
    }

    @Test
    fun `clearDekCache clears cached dek`() {
        val manager = DEKManager.getInstance()
        manager.clearDekCache()
        assertTrue("清除缓存后应返回 null", manager.getCachedDek() == null)
    }

    @Test
    fun `getMasterKeyFingerprint returns non-empty for valid key`() {
        val masterKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val fingerprint = DEKManager.getInstance().getMasterKeyFingerprint(masterKey)
        assertNotNull("指纹不应为 null", fingerprint)
        assertTrue("指纹应为非空字符串", fingerprint.isNotEmpty())
    }
}
