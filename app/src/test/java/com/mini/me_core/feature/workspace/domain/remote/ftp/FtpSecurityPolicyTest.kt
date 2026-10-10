package com.mini.me_core.feature.workspace.domain.remote.ftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FTP 安全默认策略单测：默认绑回环、拒绝弱默认口令、匿名只读由调用方保证权限。
 */
class FtpSecurityPolicyTest {

    @Test
    fun defaultBindIsLoopback() {
        // 局域网开关默认关 → 必须绑回环
        assertEquals(FtpSecurityPolicy.BIND_LOOPBACK, FtpSecurityPolicy.bindAddress(lanExposed = false))
        assertEquals("127.0.0.1", FtpSecurityPolicy.bindAddress(lanExposed = false))
    }

    @Test
    fun bindAllOnlyWhenLanExplicitlyEnabled() {
        assertEquals(FtpSecurityPolicy.BIND_ALL, FtpSecurityPolicy.bindAddress(lanExposed = true))
        assertEquals("0.0.0.0", FtpSecurityPolicy.bindAddress(lanExposed = true))
    }

    @Test
    fun legacyWeakDefaultPasswordRejected() {
        // 旧版默认口令 123456 必须被判定为弱口令
        assertTrue(FtpSecurityPolicy.isWeakPassword("123456"))
        assertTrue(FtpSecurityPolicy.isWeakPassword(" 123456 "))
        assertTrue(FtpSecurityPolicy.isWeakPassword("password"))
        assertTrue(FtpSecurityPolicy.isWeakPassword("admin"))
    }

    @Test
    fun shortOrAllDigitPasswordRejected() {
        assertTrue(FtpSecurityPolicy.isWeakPassword("abc"))
        assertTrue(FtpSecurityPolicy.isWeakPassword("1234567")) // 7 位
        assertTrue(FtpSecurityPolicy.isWeakPassword("123456789012")) // 纯数字
    }

    @Test
    fun strongPasswordAccepted() {
        assertFalse(FtpSecurityPolicy.isWeakPassword("Xk9#mP2vLq8!wZ3e"))
        assertFalse(FtpSecurityPolicy.isWeakPassword("correct horse battery staple"))
    }

    @Test
    fun validateStartBlocksWeakOrBlankUser() {
        // 未设置用户名 → 阻断
        assertTrue(FtpSecurityPolicy.validateStart("", "Xk9#mP2vLq8!") != null)
        // 弱口令 → 阻断
        assertTrue(FtpSecurityPolicy.validateStart("minime", "123456") != null)
        // 强口令 + 非空用户 → 放行
        assertNull(FtpSecurityPolicy.validateStart("minime", "Xk9#mP2vLq8!"))
    }

    @Test
    fun generatedPasswordIsStrongAndRandom() {
        val p1 = FtpSecurityPolicy.generateRandomPassword()
        val p2 = FtpSecurityPolicy.generateRandomPassword()
        assertEquals(20, p1.length)
        assertFalse(FtpSecurityPolicy.isWeakPassword(p1))
        assertNotEquals(p1, p2)
    }
}
