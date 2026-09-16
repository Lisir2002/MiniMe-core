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

    // ===== 新增能力①：多级嵌套列表 =====

    @Test fun `无序列表支持二级缩进`() {
        val segs = parseSegments("- 一级A\n  - 二级A1\n  - 二级A2\n- 一级B")
        val list = segs.filterIsInstance<MdSegment.MarkdownList>().single()
        assertEquals(4, list.items.size)
        assertEquals(0, list.items[0].level)
        assertEquals("一级A", list.items[0].text)
        assertEquals(1, list.items[1].level)
        assertEquals("二级A1", list.items[1].text)
        assertEquals(1, list.items[2].level)
        assertEquals("一级B", list.items[3].text)
        assertEquals(0, list.items[3].level)
    }

    @Test fun `有序列表保留序号并支持嵌套`() {
        val segs = parseSegments("1. 第一步\n   1. 子步骤\n2. 第二步")
        val list = segs.filterIsInstance<MdSegment.MarkdownList>().single()
        assertEquals(listOf("1", "1", "2"), list.items.map { it.marker })
        assertEquals(0, list.items[0].level)
        assertEquals(1, list.items[1].level)
        assertEquals("子步骤", list.items[1].text)
        assertEquals(0, list.items[2].level)
    }

    // ===== 新增能力②：行内转义 =====

    @Test fun `转义星号与下划线还原为字面字符`() {
        assertEquals("a*b", unescapeInline("a\\*b"))
        assertEquals("_italic_", unescapeInline("\\_italic\\_"))
    }

    @Test fun `转义方括号不被当作链接`() {
        assertEquals("[不是链接]", unescapeInline("\\[不是链接\\]"))
        // 未转义的反斜杠原样保留
        assertEquals("a\\b", unescapeInline("a\\b"))
    }

    // ===== 新增能力③：图片占位 =====

    @Test fun `整行图片解析为 Image 段`() {
        val segs = parseSegments("![示意图](https://example.com/a.png)")
        val img = segs.filterIsInstance<MdSegment.Image>().single()
        assertEquals("示意图", img.alt)
        assertEquals("https://example.com/a.png", img.url)
    }

    @Test fun `图片与普通段落穿插`() {
        val segs = parseSegments("前言段落\n\n![alt 文本](u)\n\n尾段")
        assertEquals(3, segs.size)
        assertTrue(segs[0] is MdSegment.Paragraph)
        val img = segs[1] as MdSegment.Image
        assertEquals("alt 文本", img.alt)
        assertEquals("u", img.url)
        assertTrue(segs[2] is MdSegment.Paragraph)
    }
}
