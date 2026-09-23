package com.mini.logs

import com.mini.me_core.core.util.LogSanitizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LogSanitizer] 单元测试。
 */
class LogSanitizerTest {

    @Test
    fun `json apiKey value is redacted`() {
        val input = """{"apiKey": "secret123"}"""
        val out = LogSanitizer.sanitize(input)
        assertFalse("secret123 should be removed", out.contains("secret123"))
        assertTrue(out.contains("[REDACTED"))
        assertTrue("key name preserved", out.contains("apiKey"))
    }

    @Test
    fun `authorization bearer header is redacted`() {
        val input = "Authorization: Bearer token123"
        val out = LogSanitizer.sanitize(input)
        assertFalse(out.contains("token123"))
        assertTrue(out.contains("Authorization: Bearer [REDACTED]"))
    }

    @Test
    fun `url query parameter is redacted`() {
        val input = "https://api.com?apiKey=secret&page=1"
        val out = LogSanitizer.sanitize(input)
        assertFalse("secret should be removed", out.contains("secret"))
        assertTrue(out.contains("[REDACTED]"))
        // 非敏感参数 page 保留
        assertTrue(out.contains("page=1"))
    }

    @Test
    fun `inline key value is redacted`() {
        val input = "config: apiKey=secret123 ready"
        val out = LogSanitizer.sanitize(input)
        assertFalse(out.contains("secret123"))
        assertTrue(out.contains("apiKey=[REDACTED]"))
    }

    @Test
    fun `base64 media data uri is redacted`() {
        val b64 = "A".repeat(600)
        val input = "data:image/png;base64,$b64"
        val out = LogSanitizer.sanitize(input)
        assertFalse("long base64 should be stripped", out.contains(b64))
        assertTrue(out.contains("base64 omitted"))
    }

    @Test
    fun `ordinary log is not over-redacted`() {
        val input = "2026-09-23 09:23:45.123 INFO [Container] PRoot started on port 8080"
        val out = LogSanitizer.sanitize(input)
        assertEqualsString(input, out)
    }

    private fun assertEqualsString(expected: String, actual: String) {
        assertTrue("expected [$expected] but was [$actual]", expected == actual)
    }
}
