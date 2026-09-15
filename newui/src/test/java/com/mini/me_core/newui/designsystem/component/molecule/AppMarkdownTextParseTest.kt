package com.mini.me_core.newui.designsystem.component.molecule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMarkdownTextParseTest {

    @Test fun `段落按空行分段`() {
        val segs = parseSegments("第一段\n连续两行\n\n第二段")
        assertEquals(2, segs.size)
        assertTrue(segs[0] is MdSegment.Paragraph)
        assertEquals("第二段", (segs[1] as MdSegment.Paragraph).content)
    }

    @Test fun `代码块被完整捕获`() {
        val segs = parseSegments("前言\n\n```kotlin\nval a = 1\nval b = 2\n```\n尾注")
        val code = segs.filterIsInstance<MdSegment.CodeBlock>().single()
        assertEquals("val a = 1\nval b = 2", code.code)
    }

    @Test fun `标题按 # 数量分级`() {
        val segs = parseSegments("# H1\n## H2\n### H3")
        val heads = segs.filterIsInstance<MdSegment.Heading>()
        assertEquals(3, heads.size)
        assertEquals(1, heads[0].level)
        assertEquals("H1", heads[0].content)
        assertEquals(2, heads[1].level)
        assertEquals(3, heads[2].level)
    }

    @Test fun `引用块合并连续行`() {
        val segs = parseSegments("> 第一行\n> 第二行\n普通段落")
        val quote = segs.filterIsInstance<MdSegment.Blockquote>().single()
        assertEquals("第一行\n第二行", quote.content)
    }

    @Test fun `表格识别表头分隔行与数据行`() {
        val md = "| A | B |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |"
        val table = parseSegments(md).filterIsInstance<MdSegment.Table>().single()
        assertEquals(listOf("A", "B"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("1", table.rows[0][0])
    }

    @Test fun `不含分隔行的 | 不算表格`() {
        val segs = parseSegments("这是一段含 | 竖线 的普通文字")
        assertTrue(segs.none { it is MdSegment.Table })
    }

    @Test fun `空输入返回空`() {
        assertTrue(parseSegments("").isEmpty())
    }
}
