package com.mini.me_core.feature.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行内代码反引号区域扫描算法（[InlineCodeRegionScanner]）的 JVM 单元测试。
 *
 * 覆盖单反引号、双反引号、未闭合、无反引号、嵌套长度不匹配等场景。
 */
class MarkdownAnalyzerTest {

    @Test
    fun singleBacktick_oneRegion() {
        // `hello` → 内容区间 [1, 6)
        assertEquals(
            listOf(1 to 6),
            InlineCodeRegionScanner.scan("`hello`"),
        )
    }

    @Test
    fun twoSingleBackticks_twoRegions() {
        // `a` and `b` → [(1,2), (9,10)]
        assertEquals(
            listOf(1 to 2, 9 to 10),
            InlineCodeRegionScanner.scan("`a` and `b`"),
        )
    }

    @Test
    fun doubleBacktick_oneRegion() {
        // ``code`` → 内容区间 [2, 6)
        assertEquals(
            listOf(2 to 6),
            InlineCodeRegionScanner.scan("``code``"),
        )
    }

    @Test
    fun unclosedBacktick_noRegion() {
        // `unclosed → 无闭合反引号，不产出区域
        assertTrue(
            "未闭合反引号应无区域",
            InlineCodeRegionScanner.scan("`unclosed").isEmpty(),
        )
    }

    @Test
    fun noBacktick_empty() {
        assertTrue(
            "无反引号的行应返回空列表",
            InlineCodeRegionScanner.scan("plain text without code").isEmpty(),
        )
    }

    @Test
    fun mismatchedBacktickLength_treatedAsLiteral() {
        // `code with ` nested` → 第一个闭合在第 11 列（内容 "code with "），
        // 末尾的单个反引号为未闭合开符号，丢弃
        assertEquals(
            listOf(1 to 11),
            InlineCodeRegionScanner.scan("`code with ` nested`"),
        )
    }
}
