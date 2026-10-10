package com.mini.me_core.feature.workspace.domain.remote.ftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 明文 FTP 风险策略单测：默认拒绝静默明文登录；有安全通道时绝不退化为明文。
 */
class FtpCleartextPolicyTest {

    @Test
    fun cleartextDeniedByDefault() {
        // 未获用户显式确认、无安全通道 → 拒绝（fail-closed）
        assertFalse(FtpCleartextPolicy.allowCleartext(userConfirmedCleartext = false, secureChannelAvailable = false))
    }

    @Test
    fun cleartextAllowedOnlyAfterExplicitConfirmation() {
        assertTrue(FtpCleartextPolicy.allowCleartext(userConfirmedCleartext = true, secureChannelAvailable = false))
    }

    @Test
    fun secureChannelAvailableNeverDowngradesToCleartext() {
        // 即便用户确认过，只要存在 SFTP/FTPS 安全通道可用，也不允许明文
        assertFalse(FtpCleartextPolicy.allowCleartext(userConfirmedCleartext = true, secureChannelAvailable = true))
        assertFalse(FtpCleartextPolicy.allowCleartext(userConfirmedCleartext = false, secureChannelAvailable = true))
    }

    @Test
    fun notConfirmedExceptionIsSecurityException() {
        // 拒绝时抛出的是 SecurityException 体系
        val e = CleartextFtpNotConfirmedException("x")
        assertTrue(e is SecurityException)
    }
}
