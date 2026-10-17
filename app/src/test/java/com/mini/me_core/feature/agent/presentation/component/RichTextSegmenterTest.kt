package com.mini.me_core.feature.agent.presentation.component

import com.mini.me_core.feature.agent.presentation.component.richsegment.Inline
import com.mini.me_core.feature.agent.presentation.component.richsegment.RichSegment
import com.mini.me_core.feature.agent.presentation.component.richsegment.RichTextSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextSegmenterTest {

    private fun flatPlain(seg: RichSegment.Paragraph): String {
        return seg.inlines.joinToString("") { when (it) {
            is Inline.Plain -> it.text
            is Inline.Url -> it.label ?: it.url
            is Inline.FilePath -> it.path
            is Inline.Code -> it.code
            is Inline.Bold -> it.text
            is Inline.Italic -> it.text
            is Inline.BoldItalic -> it.text
        } }
    }

    // ----- URL 中文尾随剥离（本次教训核心） -----
    @Test
    fun urlTrailingChinesePunctuationIsStripped() {
        val input =
            "阿里云镜像地址可能是：https://mirrors.aliyun.com/android/repository/。我们可以设置环境变量REPO_OS_URL。"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as? RichSegment.Paragraph
        assertNotNull(p)
        val urlIndex = p!!.inlines.indexOfFirst { it is Inline.Url }
        assertTrue("url must be found", urlIndex >= 0)
        val url = p.inlines[urlIndex] as Inline.Url
        assertEquals("https://mirrors.aliyun.com/android/repository/", url.url)
        val after = p.inlines.getOrNull(urlIndex + 1)
        assertTrue("after url should be plain punctuation", after is Inline.Plain)
        assertTrue((after as Inline.Plain).text.startsWith("。"))
    }

    @Test
    fun urlTrailingMultipleUrlsAreStrippedAndSeparated() {
        val input =
            "方案 A：https://mirrors.aliyun.com/android/repository/。方案 B：https://mirrors.cloud.tencent.com/AndroidSDK/。两个都试试。"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val urls = p.inlines.filterIsInstance<Inline.Url>()
        assertEquals(2, urls.size)
        assertEquals("https://mirrors.aliyun.com/android/repository/", urls[0].url)
        assertEquals("https://mirrors.cloud.tencent.com/AndroidSDK/", urls[1].url)
    }

    @Test
    fun urlInMarkdownLinkIsNotReSegmented() {
        // 对于 [text](url) 这种，我们的块级 segmenter 不做 md 链接识别，只有行内级
        // InlineTokenizer 只识别裸 URL（含前缀排除）。这里先校验：url 被识别为裸 URL
        // 且没有和 () 边界混淆。
        val input = "点这里下载：[gradle](https://mirrors.aliyun.com/gradle/distributions/v9.4.1/gradle-9.4.1-bin.zip)"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val urls = p.inlines.filterIsInstance<Inline.Url>()
        // 裸 URL regex 要求 ?<!\]\(?<!\( 所以不会把 ]() 里的 URL 误识别为裸 URL；
        // 这里断言数量为 0 即可
        assertEquals(0, urls.size)
    }

    @Test
    fun urlInAngleBracketsIsIgnored() {
        val input = "文档：<https://docs.example.com/zh-cn/latest。。>"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        assertEquals(0, p.inlines.filterIsInstance<Inline.Url>().size)
    }

    @Test
    fun wikipediaParenthesesUrlIsPreserved() {
        val input = "维基百科：https://en.wikipedia.org/wiki/Android_(operating_system) 介绍得很全。"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val url = p.inlines.filterIsInstance<Inline.Url>().single()
        assertEquals("https://en.wikipedia.org/wiki/Android_(operating_system)", url.url)
    }

    @Test
    fun urlUnmatchedTrailingParenIsCut() {
        val input = "请先看（参考文档：https://docs.example.com/page）然后继续。"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val url = p.inlines.filterIsInstance<Inline.Url>().single()
        assertEquals("https://docs.example.com/page", url.url)
    }

    // ----- 文件路径 -----
    @Test
    fun absoluteFilePathIsDetected() {
        val input = "请打开 /workspace/app/src/main/java/Main.kt 修改入口。"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val fp = p.inlines.filterIsInstance<Inline.FilePath>().single()
        assertEquals("/workspace/app/src/main/java/Main.kt", fp.path)
    }

    @Test
    fun relativeTildeAndDotPathDetected() {
        val inputs = listOf(
            "修改 ~/.minime/env.sh",
            "用 ./gradlew assemble",
            "改 ../app/src/build.gradle.kts",
            "用 app/src/main/AndroidManifest.xml 改一下权限"
        )
        for (inp in inputs) {
            val segs = RichTextSegmenter.segment(inp)
            val p = segs.singleOrNull() as RichSegment.Paragraph
            val fps = p.inlines.filterIsInstance<Inline.FilePath>()
            assertTrue("expected at least 1 filePath in: $inp", fps.size >= 1)
        }
    }

    // ----- 命令 -----
    @Test
    fun commandLineDollarPrefix() {
        val input = """
            构建步骤：
            ${'$'} ./gradlew assembleRelease
            ${'$'} adb install app/build/outputs/apk/release/app-release.apk
        """.trimIndent()
        val segs = RichTextSegmenter.segment(input)
        val commands = segs.filterIsInstance<RichSegment.Command>()
        assertEquals(2, commands.size)
        assertEquals("./gradlew assembleRelease", commands[0].command)
    }

    // ----- 围栏代码块 -----
    @Test
    fun codeFenceIsIsolated() {
        val input = """
            ```kotlin
            // 看下面这行 URL，应该不被当做裸链接
            val x = "https://a.b/c。。。"
            **not bold**
            ```
            正文继续。
        """.trimIndent()
        val segs = RichTextSegmenter.segment(input)
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals("kotlin", block.language)
        assertTrue(block.code.contains("https://a.b/c。。。"))
        assertTrue(block.code.contains("**not bold**"))

        // 代码块内部的星号和 URL 绝不进入 InlineTokenizer：
        // 校验在段外（正文）段落内不含 URL 类型
        val paras = segs.filterIsInstance<RichSegment.Paragraph>()
        for (p in paras) {
            assertEquals(0, p.inlines.filterIsInstance<Inline.Url>().size)
        }
    }

    // ----- 标题 / 引用 / 列表 -----
    @Test
    fun headingLevelsAndLists() {
        val input = """
            # h1
            ## h2
            - item **bold** one
            - item two
            1. first
            2. second
            3. third
        """.trimIndent()
        val segs = RichTextSegmenter.segment(input)
        val h1s = segs.filterIsInstance<RichSegment.Heading>().filter { it.level == 1 }
        val h2s = segs.filterIsInstance<RichSegment.Heading>().filter { it.level == 2 }
        val bullets = segs.filterIsInstance<RichSegment.BulletList>().single()
        val ordered = segs.filterIsInstance<RichSegment.OrderedList>().single()
        assertEquals(1, h1s.size)
        assertEquals(1, h2s.size)
        assertEquals(2, bullets.items.size)
        assertEquals(3, ordered.items.size)
        assertEquals(1, ordered.start)
        // 第一个 bullet 含 **bold**
        val firstBulletInlines = bullets.items[0]
        val bolds = firstBulletInlines.filterIsInstance<Inline.Bold>()
        assertEquals(1, bolds.size)
        assertEquals("bold", bolds[0].text)
    }

    @Test
    fun orderedListStartNumberIsRespected() {
        val input = "3. alpha\n4. beta"
        val segs = RichTextSegmenter.segment(input)
        val ordered = segs.filterIsInstance<RichSegment.OrderedList>().single()
        assertEquals(3, ordered.start)
        assertEquals(2, ordered.items.size)
    }

    @Test
    fun quoteBlockStripsPrefix() {
        val input = "> 第一段引文\n> 第二段引文\n普通段落。"
        val segs = RichTextSegmenter.segment(input)
        val q = segs.filterIsInstance<RichSegment.Quote>().single()
        assertEquals(2, q.lines.size)
        assertEquals("第一段引文", q.lines[0])
    }

    // ----- 表格 -----
    @Test
    fun tableHasHeaderAndRows() {
        val input = """
            | Name | Value |
            |---|---|
            | Alpha | 1 |
            | Beta | 2 |
        """.trimIndent()
        val segs = RichTextSegmenter.segment(input)
        val t = segs.filterIsInstance<RichSegment.Table>().single()
        assertEquals(2, t.header.size)
        assertEquals(2, t.rows.size)
        assertEquals(2, t.rows[0].size)
    }

    // ----- 行内：粗体 / 斜体 / 粗斜体 / 行内代码 -----
    @Test
    fun inlineMarkStyles() {
        val input = "行内样式 **bold** 和 *italic* 和 ***bolditalic*** 还有 `code` 结束"
        val segs = RichTextSegmenter.segment(input)
        val p = segs.singleOrNull() as RichSegment.Paragraph
        val bolds = p.inlines.filterIsInstance<Inline.Bold>()
        val italics = p.inlines.filterIsInstance<Inline.Italic>()
        val boldItalics = p.inlines.filterIsInstance<Inline.BoldItalic>()
        val codes = p.inlines.filterIsInstance<Inline.Code>()
        assertEquals(1, bolds.size)
        assertEquals(1, italics.size)
        assertEquals(1, boldItalics.size)
        assertEquals(1, codes.size)
        assertEquals("bold", bolds[0].text)
        assertEquals("italic", italics[0].text)
        assertEquals("bolditalic", boldItalics[0].text)
        assertEquals("code", codes[0].code)
    }

    // ----- 空白输入 -----
    @Test
    fun blankInputReturnsEmpty() {
        assertEquals(emptyList<RichSegment>(), RichTextSegmenter.segment(""))
        assertEquals(emptyList<RichSegment>(), RichTextSegmenter.segment("   \n\n   "))
    }

    // ----- 组合：长文真实样例 -----
    @Test
    fun longComposeSample() {
        val input = """
            # 构建指南

            > 本文是 Android 端构建的快速指南。

            ## 环境准备

            请确保安装了 **JDK 17** 和 [Android Command line tools](https://developer.android.com/studio)。
            若访问慢，可用国内镜像：
            阿里镜像：https://mirrors.aliyun.com/android/repository/。

            常用命令：

            ${'$'} ./gradlew assembleRelease
            ${'$'} ls app/build/outputs/apk/release/*.apk

            ## 修改清单

            1. 打开 /workspace/app/src/main/AndroidManifest.xml
            2. 修改 `package` 字段

            ```xml
            <manifest package="com.mini.me_core">
              <application>
                <activity android:name=".MainActivity"/>
              </application>
            </manifest>
            ```

            支持矩阵：

            | 构建变体 | 签名 | 体积 |
            |---|---|---|
            | release | keystore | 22MB |
            | debug | debug | 28MB |
        """.trimIndent()
        val segs = RichTextSegmenter.segment(input)
        assertTrue("must have headings", segs.filterIsInstance<RichSegment.Heading>().size >= 2)
        assertTrue("must have quote", segs.filterIsInstance<RichSegment.Quote>().isNotEmpty())
        assertTrue("must have ordered list", segs.filterIsInstance<RichSegment.OrderedList>().isNotEmpty())
        assertTrue("must have commands", segs.filterIsInstance<RichSegment.Command>().size == 2)
        val codeBlocks = segs.filterIsInstance<RichSegment.CodeBlock>()
        assertEquals(1, codeBlocks.size)
        assertEquals("xml", codeBlocks[0].language)
        val tables = segs.filterIsInstance<RichSegment.Table>()
        assertEquals(1, tables.size)
        // URL 剥离中文句号：长文中 developer.android.com 位于 [Android Command line tools](url)
        // 按 urlInMarkdownLinkIsNotReSegmented 设计，MD 链接内部 URL 不被当作裸 URL 识别，
        // 因此唯一裸 URL 是「阿里镜像：https://...」那一行。
        val paras = segs.filterIsInstance<RichSegment.Paragraph>()
        val urlsInText = paras.flatMap { it.inlines.filterIsInstance<Inline.Url>() }
        assertEquals(1, urlsInText.size)
        assertEquals("https://mirrors.aliyun.com/android/repository/", urlsInText[0].url)
        // FilePath：长文中的路径在有序列表里（「1. 打开 /workspace/...」是 RichSegment.OrderedList，
        // 不是 Paragraph），所以需要从所有含 inlines 的段类型中汇总查找。
        val fps = buildList<List<Inline>> {
            for (seg in segs) {
                when (seg) {
                    is RichSegment.Paragraph -> add(seg.inlines)
                    is RichSegment.Heading -> add(seg.inlines)
                    is RichSegment.BulletList -> addAll(seg.items)
                    is RichSegment.OrderedList -> addAll(seg.items)
                    is RichSegment.Table -> { add(seg.header.flatten()); addAll(seg.rows.map { it.flatten() }) }
                    else -> {}
                }
            }
        }.flatten().filterIsInstance<Inline.FilePath>()
        assertEquals(1, fps.size)
        assertEquals("/workspace/app/src/main/AndroidManifest.xml", fps[0].path)
    }

    // ---- 围栏状态机（fence state machine）----

    /** 流式中：未闭合围栏的块必须标记 closed=false，供渲染侧只高亮稳定行。 */
    @Test
    fun unclosedFenceIsMarkedNotClosed() {
        val segs = RichTextSegmenter.segment("```kotlin\nval x = 1")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals(false, block.closed)
        assertEquals("kotlin", block.language)
        assertEquals("val x = 1", block.code)
    }

    /** 流式中：尾随换行产生的空行不得进入代码内容（否则闭合时该行消失造成跳变）。 */
    @Test
    fun trailingNewlineIsNotCodeContentWhileStreaming() {
        val segs = RichTextSegmenter.segment("```kotlin\nval x = 1\n")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals("val x = 1", block.code)
        assertEquals(false, block.closed)
    }

    /** 流式中：正在写入的半截闭合围栏（`` ）不是代码内容，须剔除。 */
    @Test
    fun partialClosingFenceIsStrippedWhileStreaming() {
        val segs = RichTextSegmenter.segment("```kotlin\nval x = 1\n``")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals("val x = 1", block.code)
        assertEquals(false, block.closed)
    }

    /** 闭合瞬间：内容与流式期间的稳定区一致，仅 closed 翻转，不引入内容跳变。 */
    @Test
    fun closingFenceFlipsClosedWithoutChangingStableCode() {
        val streaming = RichTextSegmenter.segment("```kotlin\nval x = 1")
            .filterIsInstance<RichSegment.CodeBlock>().single()
        val closed = RichTextSegmenter.segment("```kotlin\nval x = 1\n```")
            .filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals(streaming.code, closed.code)
        assertEquals(true, closed.closed)
    }

    /** 代码中间的空行是真实内容，必须保留（只剔除「末行」的半截围栏/空行）。 */
    @Test
    fun innerBlankLineInsideCodeIsPreserved() {
        val segs = RichTextSegmenter.segment("```\na\n\nb")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals("a\n\nb", block.code)
    }

    /** 未闭合围栏内的闭合字符若不属于围栏字符（如 ~~~ 块里的 ```），不得误判为闭合。 */
    @Test
    fun mismatchedFenceCharDoesNotCloseBlock() {
        val segs = RichTextSegmenter.segment("~~~\nval x = 1\n```")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals(false, block.closed)
        assertEquals("val x = 1\n```", block.code)
    }

    // ---- 半截 URL 终止符判定（P2-12）----

    /** 流式中：URL 顶到文本末尾（无终止符）→ 按纯文本，不当链接。 */
    @Test
    fun unterminatedTrailingUrlIsPlainWhileStreaming() {
        val segs = RichTextSegmenter.segment("详见 https://developer.android.com/guide", streaming = true)
        val urls = segs.filterIsInstance<RichSegment.Paragraph>()
            .flatMap { it.inlines.filterIsInstance<Inline.Url>() }
        assertTrue("流式半截 URL 不应被判定为链接", urls.isEmpty())
    }

    /** 流式中：URL 后出现终止符（空格+正文）→ 正常升级为链接。 */
    @Test
    fun terminatedUrlBecomesLinkWhileStreaming() {
        val segs = RichTextSegmenter.segment("详见 https://a.com 后续内容", streaming = true)
        val urls = segs.filterIsInstance<RichSegment.Paragraph>()
            .flatMap { it.inlines.filterIsInstance<Inline.Url>() }
        assertEquals(1, urls.size)
        assertEquals("https://a.com", urls[0].url)
    }

    /** 非流式：结尾 URL 依然是合法链接（不因本次改动回归）。 */
    @Test
    fun trailingUrlIsLinkWhenNotStreaming() {
        val segs = RichTextSegmenter.segment("详见 https://developer.android.com/guide")
        val urls = segs.filterIsInstance<RichSegment.Paragraph>()
            .flatMap { it.inlines.filterIsInstance<Inline.Url>() }
        assertEquals(1, urls.size)
    }

    /** 流式中：URL 后紧跟换行也构成终止（下一行已有内容）。 */
    @Test
    fun urlFollowedByNewlineIsLinkWhileStreaming() {
        val segs = RichTextSegmenter.segment("https://a.com\n第二行", streaming = true)
        val urls = segs.filterIsInstance<RichSegment.Paragraph>()
            .flatMap { it.inlines.filterIsInstance<Inline.Url>() }
        assertEquals(1, urls.size)
        assertEquals("https://a.com", urls[0].url)
    }

    // ---- HTML 实体解码（P2-14）----

    @Test
    fun htmlEntitiesInPlainTextAreDecoded() {
        val segs = RichTextSegmenter.segment("参数 a=1 &amp; b=2 就绪")
        val para = segs.filterIsInstance<RichSegment.Paragraph>().single()
        assertEquals("参数 a=1 & b=2 就绪", para.inlines.joinToString("") {
            if (it is Inline.Plain) it.text else ""
        })
    }

    /** 代码块内的实体必须原样保留（代码是逐字内容）。 */
    @Test
    fun htmlEntitiesInsideCodeBlockAreNotDecoded() {
        val segs = RichTextSegmenter.segment("```xml\n<a> &amp; </a>\n```")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals("<a> &amp; </a>", block.code)
    }

    /** 行内代码同样原样保留。 */
    @Test
    fun htmlEntitiesInsideInlineCodeAreNotDecoded() {
        val segs = RichTextSegmenter.segment("用 `&amp;` 转义")
        val para = segs.filterIsInstance<RichSegment.Paragraph>().single()
        val codes = para.inlines.filterIsInstance<Inline.Code>()
        assertEquals(1, codes.size)
        assertEquals("&amp;", codes[0].code)
    }

    // ---- 块级解析进度护栏（P2 分段管线加固）----

    /**
     * 单行表格（无分隔行）不得让分段器原地打转。
     *
     * `| a |` 是 table start，但因缺少 `| --- |` 分隔行而不走表格分支，
     * 落到段落兜底；而 isPlainContentLine 又排除 table start，
     * 若兜底不强行吃掉这一行，外层 while 下标不动 → 主线程死循环（ANR）。
     */
    @Test
    fun loneTableLineDoesNotHangSegmenter() {
        val segs = RichTextSegmenter.segment("| a |\n正文")
        assertTrue("必须产出段落而不是空转", segs.any { it is RichSegment.Paragraph })
        assertTrue("原样保留该行文本", segs.filterIsInstance<RichSegment.Paragraph>()
            .any { it.inlines.joinToString("").contains("| a |") })
    }

    /** 有分隔行的表格仍按表格解析，不能被上面的兜底吃掉。 */
    @Test
    fun validTableStillParsedAsTable() {
        val segs = RichTextSegmenter.segment("| h1 | h2 |\n| --- | --- |\n| c1 | c2 |")
        assertEquals(1, segs.filterIsInstance<RichSegment.Table>().size)
    }

    /**
     * 未闭合围栏的末行若是「半截闭合围栏」，必须剔除——
     * 否则闭合围栏一到，这行会从代码区凭空消失，造成整块重排跳变。
     */
    @Test
    fun incompleteClosingFenceIsNotRenderedAsCode() {
        val segs = RichTextSegmenter.segment("```kt\nval a = 1\n``")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals(false, block.closed)
        assertEquals("val a = 1", block.code)
    }

    /** 完整的闭合围栏：闭合到达后内容不变，closed=true。 */
    @Test
    fun completeFenceKeepsCodeAndMarksClosed() {
        val segs = RichTextSegmenter.segment("```kt\nval a = 1\n```")
        val block = segs.filterIsInstance<RichSegment.CodeBlock>().single()
        assertEquals(true, block.closed)
        assertEquals("val a = 1", block.code)
    }
}
