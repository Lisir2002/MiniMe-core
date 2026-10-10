package com.mini.me_core.feature.agent.domain.execution.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回传给模型的异常文本必须去除宿主绝对路径 / SQLCipher/DEK/内部类名。
 */
class AgentErrorSanitizerTest {

    @Test
    fun stripsHostAbsPaths() {
        val raw = "failed to open /home/user/projects/MiniMe-core/app/data.db under /data/user/0/com.mini.me_core/files"
        val out = AgentErrorSanitizer.sanitize(raw)
        assertFalse(out.contains("/home/user/"))
        assertFalse(out.contains("/data/user/0/"))
        assertTrue(out.contains("<host-path>"))
    }

    @Test
    fun stripsCryptoAndInternalClass() {
        val raw = "net.sqlcipher.DatabaseError: file is encrypted: SQLCipher DEK passphrase mismatch in com.mini.me_core.datalayer.encryption.EncryptedDriverFactory"
        val out = AgentErrorSanitizer.sanitize(raw)
        assertFalse(out.contains("SQLCipher"))
        assertFalse(out.contains("DEK"))
        assertFalse(out.contains("net.sqlcipher"))
        assertFalse(out.contains("com.mini.me_core"))
        assertTrue(out.contains("<internal>"))
    }

    @Test
    fun keepsOperableCategory() {
        val raw = "HTTP 401 Unauthorized: invalid api key"
        val out = AgentErrorSanitizer.sanitize(raw)
        assertTrue(out.contains("401"))
        assertTrue(out.contains("Unauthorized"))
    }

    @Test
    fun nullOrBlank_passthrough() {
        assertTrue(AgentErrorSanitizer.sanitize(null).isEmpty())
        assertEquals("", AgentErrorSanitizer.sanitize(""))
    }
}
