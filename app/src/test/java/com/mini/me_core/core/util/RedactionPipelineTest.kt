package com.mini.me_core.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionPipelineTest {

    @Test
    fun redactsUrlCredentials() {
        val out = RedactionPipeline.redact("curl https://root:hunter2@10.0.0.1/uptime")
        assertFalse(out.contains("hunter2"))
        assertTrue(out.contains("[redacted]@"))
    }

    @Test
    fun redactsBearerAndApiKeyHeaders() {
        val out = RedactionPipeline.redact("Authorization: Bearer abcdef1234567890xyz\nX-API-Key: secret-key")
        assertFalse(out.contains("abcdef1234567890xyz"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun redactsQueryToken() {
        val out = RedactionPipeline.redact("GET https://x.io/callback?token=abcdef123456&next=1")
        assertFalse(out.contains("abcdef123456"))
        assertTrue(out.contains("next=1")) // 非敏感参数保留
    }

    @Test
    fun redactsPrefixSecret() {
        val out = RedactionPipeline.redact("key sk-abcdefghijklmnopqrstuvwxyz done")
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(out.contains("[redacted]"))
    }

    @Test
    fun redactsPemPrivateKey() {
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\nbase64\n-----END OPENSSH PRIVATE KEY-----"
        val out = RedactionPipeline.redact("pk=$pem")
        assertFalse(out.contains("base64"))
        assertTrue(out.contains("[ssh key redacted]"))
    }

    @Test
    fun keepsPlainTextUntouched() {
        val out = RedactionPipeline.redact("normal info no secrets")
        assertTrue(out.contains("normal info no secrets"))
    }
}
