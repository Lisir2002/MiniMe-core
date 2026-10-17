package com.mini.me_core.feature.agent.presentation.component.richsegment

/**
 * 自研富文本分段器：把一段原始文本按「块级类型」切成 [RichSegment] 列表，
 * 段落/标题/列表项/表格单元格内部再做「行内元素」切分（[Inline]）。
 *
 * 设计要点：
 *  - 用显式边界替代通用 Markdown 解析器的「贪心边界」，URL 只吃到合法 URL 字符为止，
 *    后续中文标点 / 正文不再被吞进链接。
 *  - 代码块（``` ）内的内容在块级阶段即被整段隔离，绝不进入行内解析，内部 URL/星号不误判。
 *  - 所有逻辑为纯函数、无 Android 依赖，可独立单测。
 */
object RichTextSegmenter {

    /** 块级解析结果：一段 segment（为空表示「本行已消费但不产出内容」）+ 下一行起始下标。 */
    private data class BlockResult(val segment: RichSegment?, val nextIndex: Int)

    /**
     * 对整段文本做块级分段。
     *
     * @param streaming 文本是否仍在流式增长。为 true 时，行内分词器对「位于文本末尾
     *   且尚未出现终止符的 URL」不做链接判定（见 [InlineTokenizer.parse]），
     *   避免半截 URL 每个 delta 都重排一次。
     */
    fun segment(text: String, streaming: Boolean = false): List<RichSegment> {
        if (text.isBlank()) return emptyList()
        val lines = text.lines()
        val result = mutableListOf<RichSegment>()
        var i = 0

        while (i < lines.size) {
            val parsed = parseBlockAt(lines, i, streaming)
            if (parsed.segment != null) result.add(parsed.segment)
            // 进度保证：parseBlockAt 契约要求 nextIndex > i，否则这里会原地打转死循环。
            i = parsed.nextIndex
        }
        // 问题7c修复：去掉开头的空行 segment（流式文本常以 \n 开头，导致首行永远空行）
        while (result.isNotEmpty() && result.first() is RichSegment.Blank) {
            result.removeAt(0)
        }
        return result
    }

    // ---- 块级判定 ----

    private fun isCodeFence(line: String): Boolean {
        val t = line.trim()
        return t.startsWith("```") || t.startsWith("~~~")
    }

    private fun isHeading(line: String): Boolean {
        val t = line.trim()
        if (!t.startsWith("#")) return false
        val level = t.takeWhile { it == '#' }.length
        return level in 1..6 && t.length > level && t[level] == ' '
    }

    private fun isQuote(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith(">") && (t.length == 1 || t[1] == ' ')
    }

    private fun isCommand(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("$ ") && t.length > 2
    }

    private fun isBulletItem(line: String): Boolean {
        val t = line.trimStart()
        return (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ")) && t.length > 2
    }

    private fun isOrderedItem(line: String): Boolean {
        val t = line.trimStart()
        val numLen = t.takeWhile { it.isDigit() }.length
        if (numLen == 0) return false
        if (t.length <= numLen) return false
        val sep = t[numLen]
        return (sep == '.' || sep == ')') && t.length > numLen + 1 && t[numLen + 1] == ' '
    }

    private fun isTableStart(line: String): Boolean = line.trim().startsWith("|")

    private fun hasTableContinuation(lines: List<String>, start: Int): Boolean {
        // 第二行必须是分隔行（| --- | --- |）
        if (start + 1 >= lines.size) return false
        val sep = lines[start + 1].trim()
        return sep.startsWith("|") && sep.contains("---")
    }

    private fun isPlainContentLine(line: String): Boolean {
        return line.isNotBlank() &&
            !isCodeFence(line) &&
            !isHeading(line) &&
            !isQuote(line) &&
            !isCommand(line) &&
            !isBulletItem(line) &&
            !isOrderedItem(line) &&
            !isTableStart(line)
    }

    // ---- 块级解析（各自负责「吃掉」自己那段行并返回下一行下标） ----

    /**
     * 按起始行类型分发到对应块解析器（顺序即优先级，不可随意调整：
     * 表格判断依赖「下一行是分隔行」，必须排在段落兜底之前）。
     *
     * 契约：返回的 [BlockResult.nextIndex] 必须大于 [start]，保证调用方必定前进。
     */
    private fun parseBlockAt(lines: List<String>, start: Int, streaming: Boolean): BlockResult {
        val line = lines[start]
        return when {
            // 围栏代码块：``` 或 ~~~
            isCodeFence(line) -> parseCodeBlock(lines, start)
            // 表格：连续 | 开头的行（至少表头 + 分隔行）
            isTableStart(line) && hasTableContinuation(lines, start) -> parseTable(lines, start, streaming)
            // 标题：# 空格
            isHeading(line) -> BlockResult(parseHeading(line, streaming), start + 1)
            // 引用块：连续 > 行
            isQuote(line) -> parseQuote(lines, start)
            // 命令：$ 前缀
            isCommand(line) -> BlockResult(
                RichSegment.Command(HtmlEntities.decode(line.trimStart().removePrefix("$").trim())),
                start + 1
            )
            // 无序列表：- / * / + （连续）
            isBulletItem(line) -> parseBulletList(lines, start, streaming)
            // 有序列表：数字. / 数字) （连续）
            isOrderedItem(line) -> parseOrderedList(lines, start, streaming)
            // 空行
            line.isBlank() -> BlockResult(RichSegment.Blank, start + 1)
            // 段落：连续非空普通行（合并，段内换行保留）
            else -> parseParagraph(lines, start, streaming)
        }
    }

    /**
     * 解析围栏代码块（``` / ~~~）。
     *
     * 围栏状态机关键一步：未闭合时，末行若只是「半截闭合围栏」（流式中正在写入的 ``` ，
     * 或尾随换行产生的空行），它不是代码内容，必须剔除——否则闭合围栏一到，
     * 这行会从代码区凭空消失，造成整块重排跳变。
     */
    private fun parseCodeBlock(lines: List<String>, start: Int): BlockResult {
        val line = lines[start]
        val fenceChar = line.trimStart().take(1)
        val fenceToken = fenceChar.repeat(3)
        val language = line.trim().removePrefix(fenceToken).trim().takeIf { it.isNotEmpty() }
        val codeLines = mutableListOf<String>()
        var i = start + 1
        var closed = false
        while (i < lines.size) {
            val l = lines[i]
            if (l.trim().startsWith(fenceToken)) {
                closed = true
                i++
                break
            }
            codeLines.add(l)
            i++
        }
        if (!closed && codeLines.isNotEmpty()) {
            val last = codeLines.last()
            // last 为空行，或 last 是 fenceToken 的前缀（正在逐字符写入的 ``` ）
            if (last.isEmpty() || fenceToken.startsWith(last)) {
                codeLines.removeAt(codeLines.size - 1)
            }
        }
        return BlockResult(
            RichSegment.CodeBlock(language, codeLines.joinToString("\n"), closed),
            i
        )
    }

    /** 解析标题：# 后紧跟空格，取 # 数量作为层级。 */
    private fun parseHeading(line: String, streaming: Boolean): RichSegment.Heading {
        val trimmed = line.trimStart()
        val level = trimmed.takeWhile { it == '#' }.length
        val headingText = trimmed.drop(level).trim()
        return RichSegment.Heading(level, InlineTokenizer.parse(headingText, streaming))
    }

    /** 解析引用块：连续 > 行，去掉标记后解码实体。 */
    private fun parseQuote(lines: List<String>, start: Int): BlockResult {
        val quoteLines = mutableListOf<String>()
        var i = start
        while (i < lines.size && isQuote(lines[i])) {
            quoteLines.add(HtmlEntities.decode(lines[i].substringAfter('>').trimStart()))
            i++
        }
        return BlockResult(RichSegment.Quote(quoteLines), i)
    }

    /** 解析无序列表：连续 - / * / + 行。 */
    private fun parseBulletList(lines: List<String>, start: Int, streaming: Boolean): BlockResult {
        val items = mutableListOf<List<Inline>>()
        var i = start
        while (i < lines.size && isBulletItem(lines[i])) {
            val markerRemoved = lines[i].substringAfter(' ').trim()
            items.add(InlineTokenizer.parse(markerRemoved, streaming))
            i++
        }
        return BlockResult(RichSegment.BulletList(items), i)
    }

    /** 解析有序列表：连续 `数字.` / `数字)` 行，记录起始序号。 */
    private fun parseOrderedList(lines: List<String>, start: Int, streaming: Boolean): BlockResult {
        val items = mutableListOf<List<Inline>>()
        var i = start
        var startNumber = 1
        while (i < lines.size && isOrderedItem(lines[i])) {
            val trimmed = lines[i].trimStart()
            if (items.isEmpty()) {
                startNumber = trimmed.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
            }
            val afterMarker = trimmed.substringAfter('.').substringAfter(')').trim()
            // 针对 "1." 与 "1)"，统一取分隔符后内容
            val content = trimmed.removePrefix(trimmed.takeWhile { it.isDigit() })
                .removePrefix(".").removePrefix(")").trim()
            items.add(InlineTokenizer.parse(content.ifBlank { afterMarker }, streaming))
            i++
        }
        return BlockResult(RichSegment.OrderedList(items, startNumber), i)
    }

    /**
     * 解析段落：连续非空普通行（合并，段内换行保留）。
     *
     * 兜底：起始行若不是「普通内容行」（例如缺少分隔行的单行表格 `| a |`——
     * 表格分支因无分隔行而不匹配），这里必须强行吃掉一行，
     * 否则外层 while 下标原地不动 → 主线程死循环（ANR）。
     */
    private fun parseParagraph(lines: List<String>, start: Int, streaming: Boolean): BlockResult {
        val paraLines = mutableListOf<String>()
        var i = start
        while (i < lines.size && isPlainContentLine(lines[i])) {
            paraLines.add(lines[i])
            i++
        }
        if (i == start) {
            paraLines.add(lines[start])
            i++
        }
        val paraText = paraLines.joinToString("\n").trim()
        return BlockResult(
            if (paraText.isEmpty()) {
                null
            } else {
                RichSegment.Paragraph(InlineTokenizer.parse(paraText, streaming))
            },
            i
        )
    }

    // ---- 表格解析 ----

    private fun parseTable(lines: List<String>, start: Int, streaming: Boolean): BlockResult {
        var i = start
        val rawRows = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith("|")) {
            rawRows.add(lines[i].trim())
            i++
        }
        val cells = rawRows.map { row -> splitTableRow(row).map { it.trim() } }

        // 跳过第二行分隔行（纯 --- 结构行）
        val header = cells.getOrNull(0)?.map { InlineTokenizer.parse(it, streaming) } ?: emptyList()
        val dataRows = mutableListOf<List<List<Inline>>>()
        for (r in 2 until cells.size) {
            val rowCells = cells[r]
            val parsed = rowCells.map { InlineTokenizer.parse(it, streaming) }
            // 对齐列数：不足补空，多余丢弃，保证矩形
            val aligned = (0 until header.size).map { col -> parsed.getOrElse(col) { emptyList() } }
            dataRows.add(aligned)
        }
        return BlockResult(RichSegment.Table(header, dataRows), i)
    }

    private fun splitTableRow(row: String): List<String> {
        // 去掉首尾的 "|"，按剩余 "|" 切分
        var s = row
        if (s.startsWith("|")) s = s.drop(1)
        if (s.endsWith("|")) s = s.dropLast(1)
        return s.split("|")
    }
}

/**
 * 行内元素分词器：把一行/一段普通文本切成 [Inline] 列表。
 */
object InlineTokenizer {

    // 优先级从高到低：行内代码 > 裸 URL > 文件路径（显式前缀，可无扩展名）> 文件路径（带扩展名）> 粗斜体 > 粗体 > 斜体。
    //
    // URL regex 特别说明：
    //  - 前置 two negative lookbehind：(?<!\]\() 排除 [text](url) 内部 MD 链接场景（测试 urlInMarkdownLinkIsNotReSegmented）
    //                             (?<![^A-Za-z0-9]<) 排除 <https://url> 尖括号场景（测试 urlInAngleBracketsIsIgnored）
    //  - 字符集排除：
    //      \s 空格类
    //      <>"` Markdown/HTML 边界
    //      \u4e00-\u9fff CJK 统一汉字（防「URL。正文」整段被吃：正文汉字在 URL 中间位置时，后置剥离无法切除）
    //      \u3000-\u303f CJK 标点（中文句号/顿号/书名号 等）
    //      \uff00-\uffef 全角字符（全角逗号/括号/冒号 等）
    //    只允许 ASCII URL 合法字符与半角标点进入 raw match，再由 stripUrlTrailing 精确处理尾边界。
    //
    // 文件路径 regex 两条：
    //  A) 显式前缀 + 无扩展名也允许：~/xxx  ./gradlew  ../a/b  /data/local/tmp
    //     —— 用户已显式写路径前缀，信号强烈，无扩展名也该识别
    //  B) 多段 / 且结尾有扩展名：app/src/main/AndroidManifest.xml
    //     —— 标准项目相对路径，典型是源码文件
    private val INLINE_TOKEN = Regex(
        "`[^`\\n]+`" +
            "|(?<!\\]\\()(?<![^A-Za-z0-9]<)https?://[^\\s<>\"`\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]+" +
            "|(?:~|\\.\\.?/|/)[^\\s()<>\"`\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]+" +
            "|[\\w.-]+(?:/[\\w.-]+)+\\.[A-Za-z0-9]{1,8}" +
            "|\\*\\*\\*[^*\\n]+\\*\\*\\*" +
            "|\\*\\*[^*\\n]+\\*\\*" +
            "|(?<![*\\w])\\*[^*\\n]+\\*(?!\\*)"
    )

    // 第 1 轮标点剥离：只剥绝对不合法 URL 尾部的中文/全角标点。
    // 注意：此处不剥 ASCII 半角 ) ] ，留给括号配对逻辑精确处理（否则 Wiki (operating_system) 的尾 ) 被误吃）。
    private val URL_TRAILING_PUNCT_P1 = Regex("[。，、；：？！「」『』《》〈〉【】＂〝〞'）〕】,;:!?…—－]+$")

    // 第 2 轮标点剥离：括号配对完成后，再剥尾部残留的句点/逗号/分号等软边界。
    // 注意：这里不剥 ASCII ) ]，因为括号配对逻辑已保证尾部若有 ) ] 必然是成对合法的（例如 Wiki (operating_system)）。
    private val URL_TRAILING_PUNCT_P2 = Regex("[.,;:!?…—－]+$")

    /**
     * 行内分词。
     *
     * @param streaming 文本仍在流式增长。此时若 URL 匹配一路延伸到文本末尾（后面没有任何
     *   字符），说明它可能还没写完——按纯文本渲染，等真正的终止符（空白/行尾/标点）到达
     *   后再升级为链接。这把「半截 URL 每个 delta 重排一次」变成「只在完成时切换一次」。
     *   非流式文本不受影响：结尾 URL 依然是合法链接。
     */
    fun parse(text: String, streaming: Boolean = false): List<Inline> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<Inline>()
        var lastEnd = 0
        var m = INLINE_TOKEN.find(text)
        while (m != null) {
            if (m.range.first > lastEnd) {
                result.add(Inline.Plain(HtmlEntities.decode(text.substring(lastEnd, m.range.first))))
            }
            val token = m.value
            val isUrl = token.startsWith("http://") || token.startsWith("https://")
            // 流式中且 URL 顶到文本末尾：判定为「尚未写完」，按纯文本渲染
            val unterminatedUrl = streaming && isUrl && m.range.last == text.length - 1
            if (unterminatedUrl) {
                result.add(Inline.Plain(HtmlEntities.decode(token)))
            } else {
                result.add(parseToken(token))
            }
            lastEnd = m.range.last + 1
            m = INLINE_TOKEN.find(text, lastEnd)
        }
        if (lastEnd < text.length) {
            result.add(Inline.Plain(HtmlEntities.decode(text.substring(lastEnd))))
        }
        return result
    }

    private fun parseToken(token: String): Inline {
        return when {
            token.startsWith("`") && token.endsWith("`") && token.length >= 2 ->
                Inline.Code(token.substring(1, token.length - 1))

            token.startsWith("http://") || token.startsWith("https://") -> {
                val cleaned = stripUrlTrailing(token)
                Inline.Url(cleaned, null)
            }

            token.startsWith("***") && token.endsWith("***") -> {
                Inline.BoldItalic(token.substring(3, token.length - 3))
            }

            token.startsWith("**") && token.endsWith("**") -> {
                Inline.Bold(token.substring(2, token.length - 2))
            }

            token.startsWith("*") && token.endsWith("*") -> {
                Inline.Italic(token.substring(1, token.length - 1))
            }

            // 文件路径判定：含 / 且（以 ~\/.\/../ 开头 或 多段相对路径）且结尾为扩展名
            isFilePathToken(token) -> Inline.FilePath(token)

            else -> Inline.Plain(token)
        }
    }

    private fun isFilePathToken(token: String): Boolean {
        if (token.contains("://")) return false
        // 排除双斜杠开头：//example.com/x 是 URL 的协议相对形式（或注释），非文件路径
        if (token.startsWith("//")) return false
        if (token.isBlank()) return false
        val explicitPrefix = token.startsWith("/") || token.startsWith("~/") ||
            token.startsWith("./") || token.startsWith("../")
        val isRel = token.count { it == '/' } >= 1
        val hasExt = Regex("\\.[A-Za-z0-9]{1,8}$").containsMatchIn(token)
        return when {
            explicitPrefix && hasExt -> true
            explicitPrefix && isRel -> true
            hasExt && isRel -> true
            else -> false
        }
    }

    private fun stripUrlTrailing(url: String): String {
        // P1：剥全角/中文标点（绝对不会出现在合法 URL 尾部，同时不碰 ASCII 半角右括号）
        var s = URL_TRAILING_PUNCT_P1.replace(url, "")
        // 括号配对：仅把「未配对的右括号」及其后续内容切掉，合法配对（如 Wiki (operating_system)）原样保留
        var depth = 0
        var cut = -1
        for ((idx, ch) in s.withIndex()) {
            when (ch) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth > 0) depth-- else if (cut < 0) cut = idx
            }
        }
        if (cut >= 0) s = s.substring(0, cut)
        // P2：配对处理完，再剥尾部残留的半角右括号/点号/逗号等（都是 URL 非法尾字符）
        s = URL_TRAILING_PUNCT_P2.replace(s, "")
        // 再做一次 P1 兜底：P2 剥完后尾部若刚好露出中文标点再清掉
        s = URL_TRAILING_PUNCT_P1.replace(s, "")
        return s
    }
}