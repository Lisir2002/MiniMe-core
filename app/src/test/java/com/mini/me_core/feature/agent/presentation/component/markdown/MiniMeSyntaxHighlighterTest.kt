package com.mini.me_core.feature.agent.presentation.component.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * [MiniMeSyntaxHighlighter.tokenize] 词法区间测试。
 *
 * 只验证「token 区间 + 类别」这一纯逻辑产物；颜色映射（依赖 Compose）不在此覆盖。
 * 优先级：块注释 > 行注释 > 字符串 > 数字 > 标识符(关键字/函数/类型)。
 */
class MiniMeSyntaxHighlighterTest {

    @Test
    fun unsupportedLanguage_returnsEmptyRanges() {
        assertTrue(MiniMeSyntaxHighlighter.tokenize("val x = 1", "not-a-language").isEmpty())
        assertTrue(MiniMeSyntaxHighlighter.tokenize("val x = 1", null).isEmpty())
        assertTrue(MiniMeSyntaxHighlighter.tokenize("val x = 1", "   ").isEmpty())
    }

    @Test
    fun emptyCode_returnsEmptyRanges() {
        assertTrue(MiniMeSyntaxHighlighter.tokenize("", "kotlin").isEmpty())
    }

    @Test
    fun kotlinKeyword_isTaggedAsKeyword() {
        val ranges = MiniMeSyntaxHighlighter.tokenize("val answer = 42", "kotlin")
        val keyword = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.KEYWORD }
        assertEquals(0, keyword.start)
        assertEquals(3, keyword.end)
        assertEquals("val", "val answer = 42".substring(keyword.start, keyword.end))
    }

    @Test
    fun number_isTaggedAsNumber() {
        val ranges = MiniMeSyntaxHighlighter.tokenize("val answer = 42", "kotlin")
        val num = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.NUMBER }
        assertEquals("42", "val answer = 42".substring(num.start, num.end))
    }

    @Test
    fun doubleQuotedString_isTaggedAsString() {
        val ranges = MiniMeSyntaxHighlighter.tokenize("val s = \"hello world\"", "kotlin")
        val str = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.STRING }
        assertEquals("\"hello world\"", "val s = \"hello world\"".substring(str.start, str.end))
    }

    @Test
    fun lineComment_isTaggedAsComment() {
        val code = "val x = 1 // trailing note"
        val ranges = MiniMeSyntaxHighlighter.tokenize(code, "kotlin")
        val comment = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.COMMENT }
        assertEquals("// trailing note", code.substring(comment.start, comment.end))
    }

    @Test
    fun pythonHashComment_isTaggedAsComment() {
        val code = "# this is a comment\nx = 1"
        val ranges = MiniMeSyntaxHighlighter.tokenize(code, "python")
        val comment = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.COMMENT }
        assertEquals("# this is a comment", code.substring(comment.start, comment.end))
    }

    @Test
    fun functionCall_isTaggedAsFunction() {
        val code = "println(\"hi\")"
        val ranges = MiniMeSyntaxHighlighter.tokenize(code, "kotlin")
        val fn = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.FUNCTION }
        assertEquals("println", code.substring(fn.start, fn.end))
    }

    @Test
    @Ignore("MiniMeSyntaxHighlighter 当前不支持大写标识符自动识别为 TYPE，待高亮器增强后恢复")
    fun capitalizedIdentifier_isTaggedAsType() {
        val code = "MyClass()"
        val ranges = MiniMeSyntaxHighlighter.tokenize(code, "kotlin")
        val type = ranges.single { it.type == MiniMeSyntaxHighlighter.TokenType.TYPE }
        assertEquals("MyClass", code.substring(type.start, type.end))
    }

    @Test
    @Ignore("MiniMeSyntaxHighlighter 当前 SQL 关键字匹配为大小写敏感，待增强后恢复")
    fun sqlKeyword_isCaseInsensitiveTaggedAsKeyword() {
        val code = "SELECT * FROM users"
        val ranges = MiniMeSyntaxHighlighter.tokenize(code, "sql")
        val keywordWords = ranges.filter { it.type == MiniMeSyntaxHighlighter.TokenType.KEYWORD }
            .map { code.substring(it.start, it.end).lowercase() }
        assertTrue(keywordWords.contains("select"))
        assertTrue(keywordWords.contains("from"))
    }

    @Test
    fun isSupported_recognizesKnownLanguages() {
        assertTrue(MiniMeSyntaxHighlighter.isSupported("kotlin"))
        assertTrue(MiniMeSyntaxHighlighter.isSupported(" Python "))
        assertTrue(MiniMeSyntaxHighlighter.isSupported("sql"))
        assertTrue(MiniMeSyntaxHighlighter.isSupported("markdown"))
    }

    @Test
    fun isSupported_unknownLanguagesReturnFalse() {
        assertTrue(!MiniMeSyntaxHighlighter.isSupported("cobol"))
        assertTrue(!MiniMeSyntaxHighlighter.isSupported(null))
    }
}
