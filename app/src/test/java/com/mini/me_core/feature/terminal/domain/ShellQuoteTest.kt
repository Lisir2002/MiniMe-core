package com.mini.me_core.feature.terminal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * shellQuote 注入反例单测：外部路径（含单引号/分号/命令替换/空格/反引号）经转义后
 * 必须始终是被单引号包裹的字面量，无法逃逸出引号闭合新命令。
 */
class ShellQuoteTest {

    @Test
    fun plainPathWrappedInSingleQuotes() {
        assertEquals("'/data/projects/test'", ShellQuote.quote("/data/projects/test"))
    }

    @Test
    fun singleQuoteEscaped() {
        // 路径中的单引号被转成 '\'' 序列
        assertEquals("'a'\\''b'", ShellQuote.quote("a'b"))
    }

    @Test
    fun semicolonInjectionNeutralized() {
        val out = ShellQuote.quote("x;rm -rf /")
        // 分号被包在引号内成为字面量，输出整体是一个引号对
        assertEquals("'x;rm -rf /'", out)
    }

    @Test
    fun commandSubstitutionNeutralized() {
        assertEquals("'$(id)'", ShellQuote.quote("\$(id)"))
        assertEquals("'`id`'", ShellQuote.quote("`id`"))
    }

    @Test
    fun spaceAndDollarNeutralized() {
        assertEquals("'a b\$HOME'", ShellQuote.quote("a b\$HOME"))
    }

    @Test
    fun everyPayloadStartsAndEndsWithSingleQuoteAndIsLiteral() {
        val payloads = listOf(
            "p'a;reboot",
            "';cat /etc/passwd #",
            "\$(touch /tmp/pwned)",
            "`wget http://x`",
            "dir with space",
            "a'b\"c\$d`e"
        )
        for (p in payloads) {
            val out = ShellQuote.quote(p)
            // 必须以单引号包裹；中间唯一合法的单引号出现形式是转义序列 '\''
            assertTrue(out.startsWith("'") && out.endsWith("'"))
            // 去掉所有 '\'' 转义段后，剩余字符串内应只剩首尾两个单引号（引号未被提前闭合）
            val stripped = out.replace("'\\''", "")
            assertEquals(2, stripped.count { it == '\'' })
            assertTrue(stripped.startsWith("'") && stripped.endsWith("'"))
        }
    }
}
