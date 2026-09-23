package com.mini.me_core.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    private fun redacted(s: String) = LogSanitizer.sanitize(s)

    // ── 1. JSON 字段值 ──

    @Test
    fun jsonApiKey_isRedacted() {
        val input = """{"model":"gpt-4","apiKey":"sk-abc123secret","n":1}"""
        val out = redacted(input)
        assertFalse("apiKey 真实值不应残留: $out", out.contains("sk-abc123secret"))
        assertTrue("应保留 key 名: $out", out.contains("\"apiKey\""))
        assertTrue("应打码: $out", out.contains("[REDACTED"))
    }

    @Test
    fun jsonPassword_caseInsensitive_isRedacted() {
        val input = """{"USER":"alice","Password":"hunter2"}"""
        val out = redacted(input)
        assertFalse("Password 值应被打码: $out", out.contains("hunter2"))
        assertTrue(out.contains("[REDACTED"))
    }

    @Test
    fun jsonTokenAccessTokenRefreshToken_allRedacted() {
        val input = """{"token":"t1","access_token":"a2","refresh_token":"r3"}"""
        val out = redacted(input)
        assertFalse(out.contains("\"t1\""))
        assertFalse(out.contains("\"a2\""))
        assertFalse(out.contains("\"r3\""))
        assertEquals(3, out.windowed(12).count { it.startsWith("[REDACTED:") })
    }

    @Test
    fun jsonPrivateKeyAndClientSecret_redacted() {
        val input = """{"private_key":"-----BEGIN KEY-----","client_secret":"cs-99"}"""
        val out = redacted(input)
        assertFalse(out.contains("-----BEGIN KEY-----"))
        assertFalse(out.contains("cs-99"))
    }

    @Test
    fun jsonNonSensitiveField_valuePreserved() {
        val input = """{"model":"gpt-4","temperature":0.7,"count":5}"""
        val out = redacted(input)
        assertTrue("普通字段值应保留: $out", out.contains("gpt-4"))
        assertTrue(out.contains("0.7"))
    }

    // ── 2. HTTP Authorization 头 ──

    @Test
    fun bearerToken_header_isRedacted() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"
        val out = redacted(input)
        assertFalse("Bearer 凭据应被打码: $out", out.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue("应保留 scheme: $out", out.contains("Bearer"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun basicAuth_header_isRedacted() {
        val input = "Authorization: Basic dXNlcjpwYXNz"
        val out = redacted(input)
        assertFalse(out.contains("dXNlcjpwYXNz"))
        assertTrue(out.contains("[REDACTED]"))
    }

    // ── 3. URL query 参数 ──

    @Test
    fun urlQueryParams_areRedacted() {
        val input = "https://api.example.com/v1/chat?apiKey=sk-xyz&model=gpt4&token=abc"
        val out = redacted(input)
        assertFalse("apiKey 值应打码: $out", out.contains("sk-xyz"))
        assertFalse("token 值应打码: $out", out.contains("token=abc"))
        assertTrue("普通参数保留: $out", out.contains("model=gpt4"))
    }

    // ── 4. 内联 key=value ──

    @Test
    fun inlineApiKey_isRedacted() {
        val input = "发送请求失败 apiKey=sk-abc123 已重试"
        val out = redacted(input)
        assertFalse(out.contains("sk-abc123"))
        assertTrue(out.contains("apiKey=[REDACTED]"))
    }

    @Test
    fun inlinePassword_isRedacted() {
        val input = "credential check pwd=hunter2 failed"
        val out = redacted(input)
        assertFalse(out.contains("hunter2"))
    }

    // ── 5. base64 媒体（迁移自 AILogger） ──

    @Test
    fun dataUrlBase64Image_isRedacted() {
        val b64 = "A".repeat(1000)
        val input = "data:image/png;base64,$b64"
        val out = redacted(input)
        assertFalse("base64 原始字节不应残留: $out", out.contains(b64))
        assertTrue(out.contains("base64 omitted"))
        assertTrue("应保留 mime: $out", out.contains("image/png"))
    }

    @Test
    fun jsonBase64DataField_isRedacted() {
        val b64 = "Q".repeat(800)
        val input = """{"data":"$b64","model":"x"}"""
        val out = redacted(input)
        assertFalse(out.contains(b64))
        assertTrue(out.contains("base64 omitted"))
    }

    @Test
    fun shortBase64_notRedacted() {
        // 不足 512 字符的短 base64 不打码，避免误伤普通短串
        val b64 = "YWJj"
        val input = """{"data":"$b64"}"""
        val out = redacted(input)
        assertTrue("短 base64 应保留: $out", out.contains(b64))
    }

    // ── 6. 不误伤 / 边界 ──

    @Test
    fun plainText_unchanged() {
        val input = "工作区初始化完成，日志目录: /data/data/xxx/files/logs"
        assertEquals(input, redacted(input))
    }

    @Test
    fun emptyString_returnedAsIs() {
        assertEquals("", redacted(""))
    }

    @Test
    fun normalLogLine_noFalsePositive() {
        // 普通业务日志里出现 "token count=5" 这类正常描述不应被误打码
        val input = "RESPONSE #3 [openai / stream] 收到 12 个 token"
        assertEquals(input, redacted(input))
    }

    @Test
    fun combined_jsonAndBearer_inOneLine() {
        val input = """request header Authorization: Bearer abc.def, body {"password":"p1"}"""
        val out = redacted(input)
        assertFalse(out.contains("abc.def"))
        assertFalse(out.contains("\"p1\""))
    }
}
