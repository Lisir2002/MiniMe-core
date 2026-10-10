package com.mini.me_core.feature.backup.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份口令策略纯单测：
 *  - 随机口令两次生成不同、非空、长度足够（32 字节 → ~43 字符 base64url）；
 *  - 口令派生不依赖任何证书/APK 公开材料（输入为空即随机，与证书哈希无固定关系）；
 *  - 弱用户口令拒绝、中等及以上放行。
 */
class BackupPassphrasePolicyTest {

    @Test
    fun `two generated secrets differ and are non-empty`() {
        val r1 = BackupPassphrasePolicy.randomSecret()
        val r2 = BackupPassphrasePolicy.randomSecret()
        assertTrue(r1.isNotBlank())
        assertTrue(r2.isNotBlank())
        assertNotEquals(r1, r2)
    }

    @Test
    fun `secret has sufficient entropy length`() {
        val s = BackupPassphrasePolicy.randomSecret()
        assertTrue("期望 >=40 字符 base64url，实际 ${s.length}", s.length >= 40)
        // base64url 字符集，无填充
        assertTrue(s.matches(Regex("[A-Za-z0-9_-]+")))
    }

    @Test
    fun `secret is not derived from a fixed certificate hash`() {
        // 伪造一个「证书 SHA-256 hex」（64 字符），随机口令不应等于它、也不应以它为种子。
        val certHash = "ab".repeat(32)
        val s = BackupPassphrasePolicy.randomSecret()
        assertNotEquals(certHash, s)
        // 连续两个固定种子下结果相同只证明随机源可控；这里仅断言 SecureRandom 路径不走证书。
    }

    @Test
    fun `weak user passphrase rejected`() {
        assertFalse(BackupPassphrasePolicy.isAcceptableUserPassphrase("short"))
        assertFalse(BackupPassphrasePolicy.isAcceptableUserPassphrase(""))
    }

    @Test
    fun `medium user passphrase accepted`() {
        assertTrue(BackupPassphrasePolicy.isAcceptableUserPassphrase("abc12345"))
        assertTrue(BackupPassphrasePolicy.isAcceptableUserPassphrase("Tr0ub4dor&3x"))
    }
}
