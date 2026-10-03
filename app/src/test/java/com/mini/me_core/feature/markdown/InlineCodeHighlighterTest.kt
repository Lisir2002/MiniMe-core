package com.mini.me_core.feature.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行内代码语言自动检测与高亮管线（缓存/阈值/高负载）的 JVM 单元测试。
 *
 * 语言检测逻辑在 [InlineCodeLanguageDetector] 中为纯函数，grammar 可用性由 `{ true }` 注入，
 * 因此无需 Android / TextMate 运行时即可运行。
 */
class InlineCodeHighlighterTest {

    /** grammar 全部视为可用，隔离检测逻辑本身。 */
    private val alwaysAvailable: (String) -> Boolean = { true }

    // ------------------------------------------------------------------
    // 语言自动检测
    // ------------------------------------------------------------------

    @Test
    fun detect_python() {
        assertEquals(
            "def hello(): pass",
            "source.python",
            InlineCodeLanguageDetector.detect("def hello(): pass", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_javascript() {
        assertEquals(
            "source.js",
            InlineCodeLanguageDetector.detect("const x = () => {}", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_rust() {
        assertEquals(
            "source.rust",
            InlineCodeLanguageDetector.detect("fn main() { let mut x = 1; }", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_go() {
        assertEquals(
            "source.go",
            InlineCodeLanguageDetector.detect("func main() { fmt.Println(\"hi\") }", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_java() {
        assertEquals(
            "source.java",
            InlineCodeLanguageDetector.detect(
                "public class Main { public static void main(String[] args) {} }",
                null,
                alwaysAvailable,
            ),
        )
    }

    @Test
    fun detect_php() {
        assertEquals(
            "source.php",
            InlineCodeLanguageDetector.detect("<?php echo \"hello\"; ?>", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_html() {
        assertEquals(
            "text.html.basic",
            InlineCodeLanguageDetector.detect("<html><body></body></html>", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_sql() {
        assertEquals(
            "source.sql",
            InlineCodeLanguageDetector.detect("SELECT * FROM users WHERE id = 1", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_shell() {
        assertEquals(
            "source.shell",
            InlineCodeLanguageDetector.detect("#!/bin/bash\necho hello", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_cpp() {
        assertEquals(
            "source.cpp",
            InlineCodeLanguageDetector.detect(
                "#include <stdio.h>\nint main() { printf(\"hi\"); }",
                null,
                alwaysAvailable,
            ),
        )
    }

    @Test
    fun detect_plainText_returnsNull() {
        assertEquals(
            "纯文本不应识别为任何语言",
            null,
            InlineCodeLanguageDetector.detect("hello world", null, alwaysAvailable),
        )
    }

    @Test
    fun detect_contextLanguage_takesPrecedence_whenGrammarAvailable() {
        // 上下文给出 Python，但内容本身像 JS：应优先采用上下文语言
        assertEquals(
            "source.python",
            InlineCodeLanguageDetector.detect("const x = 1", "source.python", alwaysAvailable),
        )
    }

    @Test
    fun detect_contextLanguage_markdownSelf_isIgnored() {
        // 上下文为 markdown 自身时不应被当作代码语言
        assertEquals(
            "source.js",
            InlineCodeLanguageDetector.detect(
                "const x = () => {}",
                "text.html.markdown.enhanced",
                alwaysAvailable,
            ),
        )
    }

    // ------------------------------------------------------------------
    // LRU 缓存
    // ------------------------------------------------------------------

    @Test
    fun pipeline_lruCache_secondHit_skipsTokenizer() {
        var tokenizeCalls = 0
        val fakeTokenize: (String, String) -> List<MarkdownSpan> = { code, scope ->
            tokenizeCalls++
            listOf(MarkdownSpan(0, code.length, 0xFF00FF00.toInt(), scope))
        }
        val pipeline = InlineCodePipeline(fakeTokenize, alwaysAvailable)

        val code = "const x = () => {}"
        val first = pipeline.highlight(code)
        val second = pipeline.highlight(code)

        assertEquals("首次应调用 tokenizer", 1, tokenizeCalls)
        assertEquals("第二次命中缓存，tokenizer 不再执行", 1, tokenizeCalls)
        assertEquals("两次结果应一致", first, second)
        assertEquals("缓存命中计数应为 1", 1, pipeline.cacheHitCount)
    }

    @Test
    fun pipeline_differentContent_doesNotHitCache() {
        var tokenizeCalls = 0
        val fakeTokenize: (String, String) -> List<MarkdownSpan> = { code, scope ->
            tokenizeCalls++
            listOf(MarkdownSpan(0, code.length, 0, scope))
        }
        val pipeline = InlineCodePipeline(fakeTokenize, alwaysAvailable)

        pipeline.highlight("const a = 1")
        pipeline.highlight("const b = 2")

        assertEquals("不同内容应分别 tokenize", 2, tokenizeCalls)
        assertEquals(0, pipeline.cacheHitCount)
    }

    // ------------------------------------------------------------------
    // 长度阈值
    // ------------------------------------------------------------------

    @Test
    fun pipeline_overLengthThreshold_returnsEmptyWithoutTokenize() {
        var tokenizeCalls = 0
        val fakeTokenize: (String, String) -> List<MarkdownSpan> = { _, _ ->
            tokenizeCalls++
            emptyList()
        }
        val pipeline = InlineCodePipeline(fakeTokenize, alwaysAvailable)

        // 501 字符，超过 500 阈值
        val longCode = "x = 1;".repeat(84) // 504 字符
        val result = pipeline.highlight(longCode)

        assertTrue("超长代码应返回空列表", result.isEmpty())
        assertEquals("超长代码不应进入 tokenizer", 0, tokenizeCalls)
    }

    // ------------------------------------------------------------------
    // 高负载模式
    // ------------------------------------------------------------------

    @Test
    fun pipeline_highLoadMode_returnsEmptyWithoutTokenize() {
        var tokenizeCalls = 0
        val fakeTokenize: (String, String) -> List<MarkdownSpan> = { _, _ ->
            tokenizeCalls++
            listOf(MarkdownSpan(0, 1, 0, "x"))
        }
        val pipeline = InlineCodePipeline(fakeTokenize, alwaysAvailable)
        pipeline.highLoadMode = true

        val result = pipeline.highlight("const x = 1")

        assertTrue("高负载下应返回空列表", result.isEmpty())
        assertEquals("高负载下不应进入 tokenizer", 0, tokenizeCalls)
    }

    @Test
    fun pipeline_emptyCode_returnsEmpty() {
        val pipeline = InlineCodePipeline({ _, _ -> emptyList() }, alwaysAvailable)
        assertTrue("空代码应返回空列表", pipeline.highlight("").isEmpty())
    }
}
