package com.mini.me_core.feature.agent.presentation.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 浏览器操作工具调用卡片的解析回归。
 *
 * 覆盖参数解析（action/url/element_id/message/脏数据）与结果解析
 * （envelope 快照字段、错误 envelope、非 JSON 历史数据兜底）。
 */
class BrowserToolCallCardParseTest {

    @Test
    fun parseArgs_fullTakeoverCall() {
        val args = parseBrowserToolArgs(
            """{"action":"takeover","message":"请帮我完成支付验证码"}"""
        )
        assertEquals("takeover", args.action)
        assertEquals("请帮我完成支付验证码", args.message)
        assertTrue(args.target.isEmpty())
    }

    @Test
    fun parseArgs_navigatePrefersUrl() {
        val args = parseBrowserToolArgs(
            """{"action":"navigate","url":"https://example.com/login"}"""
        )
        assertEquals("navigate", args.action)
        assertEquals("https://example.com/login", args.target)
    }

    @Test
    fun parseArgs_clickFallsBackToElementId() {
        val args = parseBrowserToolArgs("""{"action":"click","element_id":"e42"}""")
        assertEquals("click", args.action)
        assertEquals("#e42", args.target)
    }

    @Test
    fun parseArgs_typeFallsBackToText() {
        val args = parseBrowserToolArgs("""{"action":"type","text":"hello"}""")
        assertEquals("hello", args.target)
    }

    @Test
    fun parseArgs_dirtyInput_returnsEmptyNotCrash() {
        assertTrue(parseBrowserToolArgs(null).action.isEmpty())
        assertTrue(parseBrowserToolArgs("").action.isEmpty())
        assertTrue(parseBrowserToolArgs("not-json").action.isEmpty())
        assertTrue(parseBrowserToolArgs("""{"action":123}""").action.isEmpty())
        assertTrue(parseBrowserToolArgs("""{"other":"x"}""").target.isEmpty())
    }

    @Test
    fun parseArgs_nonStringPrimitive_treatedAsMissing() {
        // contentOrNull 只过滤 JsonNull，数字/布尔需显式收窄为「未提供」，
        // 否则 {"action":123} 会把 "123" 当动作名渲染出来。
        assertTrue(parseBrowserToolArgs("""{"action":123,"url":456}""").action.isEmpty())
        assertTrue(parseBrowserToolArgs("""{"action":123,"url":456}""").target.isEmpty())
        assertTrue(parseBrowserToolArgs("""{"message":true}""").message.isEmpty())
        assertTrue(parseBrowserToolArgs("""{"action":null}""").action.isEmpty())
    }

    @Test
    fun parseResult_successEnvelopeWithSnapshot() {
        val content = """
            {"status":"success","data":{"ok":true,"action":"navigate","changed":true,
            "summary":"页面「登录页」 URL=https://example.com/login",
            "snapshot":{"title":"登录页","url":"https://example.com/login"}}}
        """.trimIndent()
        val result = parseBrowserToolResult(content)
        assertEquals(true, result?.ok)
        assertEquals("navigate", result?.action)
        assertEquals("页面「登录页」 URL=https://example.com/login", result?.summary)
        assertEquals("登录页", result?.pageTitle)
        assertEquals("https://example.com/login", result?.pageUrl)
    }

    @Test
    fun parseResult_errorEnvelope_okFalseWithError() {
        val content = """
            {"status":"success","data":{"ok":false,"action":"click","changed":false,
            "summary":"点击失败","error":"元素 #e9 不可见或已失效"}}
        """.trimIndent()
        val result = parseBrowserToolResult(content)
        assertEquals(false, result?.ok)
        assertEquals("元素 #e9 不可见或已失效", result?.error)
    }

    @Test
    fun parseResult_pendingMarker_isStripped() {
        val content = "[running]" + """{"status":"success","data":{"ok":true,"action":"reload","summary":"已刷新"}}"""
        val result = parseBrowserToolResult(content)
        assertEquals(true, result?.ok)
        assertEquals("已刷新", result?.summary)
    }

    @Test
    fun parseResult_legacyToStringForm_returnsNull() {
        // 历史数据可能是 ToolResult.toString() 形态（Success(data=...)），解析不出 envelope
        assertNull(parseBrowserToolResult("Success(data={\"ok\":true})"))
    }

    @Test
    fun parseResult_blankInput_returnsNull() {
        assertNull(parseBrowserToolResult(null))
        assertNull(parseBrowserToolResult(""))
        assertNull(parseBrowserToolResult("garbage"))
    }

    @Test
    fun parseResult_missingSnapshot_fieldsEmpty() {
        val content = """{"status":"success","data":{"ok":true,"action":"screenshot","summary":"已截图"}}"""
        val result = parseBrowserToolResult(content)
        assertTrue(result?.pageTitle?.isEmpty() == true)
        assertTrue(result?.pageUrl?.isEmpty() == true)
        assertFalse(result?.ok == false)
    }
}
