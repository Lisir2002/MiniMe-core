package com.mini.me_core.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LogSanitizer] 增强规则单测：Bearer、URL 内嵌 user:token@、命令凭据参数、
 * 已知前缀令牌（sk-/gh*_/xox*-）、PEM 私钥块必须打码；正常路径/命令不被误杀。
 */
class LogSanitizerSensitiveTest {

    private fun redacted(text: String): Boolean = text.contains("[REDACTED") || text.contains("sk-")

    @Test
    fun `bearer token redacted`() {
        val out = LogSanitizer.sanitize("Authorization: Bearer abcdef123456token")
        assertFalse(out.contains("abcdef123456token"))
        assertTrue(out.contains("[REDACTED"))
    }

    @Test
    fun `url embedded credentials redacted`() {
        val out = LogSanitizer.sanitize("fetch https://admin:hunter2pass@example.com/path")
        assertFalse(out.contains("hunter2pass"))
        assertTrue(out.contains("[REDACTED]@example.com"))
    }

    @Test
    fun `cli credential flag redacted`() {
        val out = LogSanitizer.sanitize("curl --api-key sk-secretvalue1234 https://x")
        assertFalse(out.contains("sk-secretvalue1234"))
    }

    @Test
    fun `openai style sk token redacted`() {
        val secret = "sk-" + "A1b2C3d4E5f6G7h8I9j0K1l2"
        val out = LogSanitizer.sanitize("key=$secret")
        assertFalse(out.contains(secret))
    }

    @Test
    fun `github style token redacted`() {
        val secret = "ghp_" + "a".repeat(30)
        val out = LogSanitizer.sanitize("token $secret")
        assertFalse(out.contains(secret))
    }

    @Test
    fun `pem private key block redacted`() {
        val block = "-----BEGIN RSA PRIVATE KEY-----\nMIIBo...secret...\n-----END RSA PRIVATE KEY-----"
        val out = LogSanitizer.sanitize("config: $block")
        assertFalse(out.contains("MIIBo"))
        assertTrue(out.contains("REDACTED PRIVATE KEY BLOCK"))
    }

    @Test
    fun `normal command path kept`() {
        val out = LogSanitizer.sanitize("execute ls -la /root/workspace/src")
        assertTrue(out.contains("/root/workspace/src"))
    }

    @Test
    fun `json apiKey field redacted`() {
        val out = LogSanitizer.sanitize("""{"apiKey": "xyz-secret-123"}""")
        assertFalse(out.contains("xyz-secret-123"))
    }
}
