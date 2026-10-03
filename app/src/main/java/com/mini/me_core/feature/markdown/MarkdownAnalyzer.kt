package com.mini.me_core.feature.markdown

import android.graphics.Typeface
import android.util.Log
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.span.SpanConstColorResolver
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.langs.textmate.MyState
import io.github.rosemoe.sora.langs.textmate.TextMateAnalyzer
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.utils.ColorUtils
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration

/**
 * Markdown 高亮分析器。
 *
 * 继承 sora-editor 的 [TextMateAnalyzer]，复用其对 markdown grammar 的完整增量分析能力，
 * 并在生成 span 阶段「叠加」行内代码（`code`）的语法着色。
 *
 * 工作流程：
 *  1. 基类逐行 tokenize markdown grammar（标题、列表、引用、链接、代码块等），产出基础 span
 *  2. 本类覆写 [generateSpansForLine]：拿到基础 span 后，扫描本行反引号
 *  3. 对每个行内代码区域调用 [InlineCodeHighlighter] 取 token 颜色
 *  4. 按列区间合并：代码区域用 token 颜色覆盖基础颜色，其余区域保留基础 markdown 高亮
 *
 * 线程模型：基类在专用分析线程上串行调用 tokenizeLine → generateSpansForLine，
 * 因此这里读到的行文本与当前行一致；所有重活都在分析线程，不阻塞主线程。
 *
 * @param inlineCodeHighlighter 行内代码高亮器（由门面统一持有，便于跨实例共享缓存）
 */
class MarkdownAnalyzer(
    language: TextMateLanguage,
    grammar: IGrammar,
    configuration: LanguageConfiguration?,
    themeRegistry: ThemeRegistry,
    private val inlineCodeHighlighter: InlineCodeHighlighter,
) : TextMateAnalyzer(language, grammar, configuration, themeRegistry) {

    companion object {
        private const val TAG = "MarkdownAnalyzer"

        /** 行内代码块的背景色（中性灰 chip，约 14% 不透明度，深浅主题通用）。 */
        private const val CODE_BG = 0x24808080.toInt()

        /** 向上回溯查找邻近代码块语言的最大行数。 */
        private const val FENCE_LOOKBACK = 60
    }

    /** 最近一次 tokenizeLine 处理的行号（分析线程串行访问，无需同步）。 */
    private var currentLineIndex: Int = -1

    override fun tokenizeLine(
        line: CharSequence,
        state: MyState,
        lineIndex: Int,
    ): IncrementalAnalyzeManager.LineTokenizeResult<MyState, Span> {
        // 记录当前行号，供 generateSpansForLine 取行文本
        currentLineIndex = lineIndex
        return super.tokenizeLine(line, state, lineIndex)
    }

    override fun generateSpansForLine(
        result: IncrementalAnalyzeManager.LineTokenizeResult<MyState, Span>,
    ): MutableList<Span> {
        val baseSpans = super.generateSpansForLine(result)
        // 没有反引号就直接沿用 markdown 基础高亮
        val lineIndex = currentLineIndex
        if (lineIndex < 0) return baseSpans.toMutableList()
        val lineText = contentRef?.getLine(lineIndex) ?: return baseSpans.toMutableList()
        if (!lineText.contains('`')) return baseSpans.toMutableList()

        return overlayInlineCode(baseSpans, lineText, lineIndex)
    }

    /**
     * 在基础 span 上叠加行内代码着色。
     *
     * 算法：把整行按「基础 span 列点 + 代码区域边界 + token 边界」切分成若干列区间，
     * 逐区间决定样式——代码区域内用 token 颜色覆盖，其余区间保留基础 markdown 样式。
     */
    private fun overlayInlineCode(
        baseSpans: List<Span>,
        lineText: String,
        lineIndex: Int,
    ): MutableList<Span> {
        // 1. 本行行内代码区域 [contentStart, contentEnd)（不含反引号本身）
        val regions = InlineCodeRegionScanner.scan(lineText)
        if (regions.isEmpty()) return baseSpans.toMutableList()

        // 2. 上下文语言（邻近代码块的语言）
        val ctxLang = findNearestFenceLanguage(lineIndex)

        // 3. 收集 token 级覆盖区间
        class Override(val start: Int, val end: Int, val ms: MarkdownSpan)
        val overrides = ArrayList<Override>()
        for (region in regions) {
            val code = lineText.substring(region.first, region.second)
            val tokens = runCatching { inlineCodeHighlighter.highlight(code, ctxLang) }
                .onFailure { Log.d(TAG, "行内代码高亮异常: ${it.message}") }
                .getOrDefault(emptyList())
            for (ms in tokens) {
                overrides += Override(region.first + ms.start, region.first + ms.end, ms)
            }
        }

        // 4. 收集所有切分点
        val lineLen = lineText.length
        val points = sortedSetOf(0, lineLen)
        for (s in baseSpans) points.add(s.column)
        for (o in overrides) {
            points.add(o.start)
            points.add(o.end)
        }
        for (r in regions) {
            points.add(r.first)
            points.add(r.second)
        }

        val ptList = points.toList()
        val result = ArrayList<Span>(ptList.size.coerceAtLeast(1))
        val defaultFg = resolveDefaultForeground()

        for (i in 0 until ptList.size - 1) {
            val a = ptList[i]
            val b = ptList[i + 1]
            if (a >= b) continue
            val mid = a + (b - a) / 2

            val ov = overrides.firstOrNull { mid >= it.start && mid < it.end }
            when {
                ov != null -> {
                    // token 着色区间
                    val fg = if (ov.ms.color != 0) ov.ms.color else defaultFg
                    val style = TextStyle.makeStyle(
                        0, 0,
                        (ov.ms.fontStyle and Typeface.BOLD) != 0,
                        (ov.ms.fontStyle and Typeface.ITALIC) != 0,
                        ov.ms.isStrikethrough,
                    )
                    result += Span.obtain(a, style).apply {
                        setSpanExt(
                            SpanExtAttrs.EXT_COLOR_RESOLVER,
                            SpanConstColorResolver(fg, CODE_BG),
                        )
                    }
                }
                regions.any { mid >= it.first && mid < it.second } -> {
                    // 代码区域内但无 token 着色：默认前景 + 代码背景
                    val style = TextStyle.makeStyle(0, 0, false, false, false)
                    result += Span.obtain(a, style).apply {
                        setSpanExt(
                            SpanExtAttrs.EXT_COLOR_RESOLVER,
                            SpanConstColorResolver(defaultFg, CODE_BG),
                        )
                    }
                }
                else -> {
                    // 非代码区域：保留基础 markdown 高亮
                    val base = findBaseSpanAt(baseSpans, mid)
                    result += Span.obtain(a, base?.style ?: TextStyle.makeStyle(0))
                }
            }
        }
        return result
    }

    /** 在基础 span 列表中找到覆盖列 mid 的那个 span。 */
    private fun findBaseSpanAt(baseSpans: List<Span>, mid: Int): Span? {
        var found: Span? = null
        for (s in baseSpans) {
            if (s.column <= mid) found = s else break
        }
        return found
    }

    /** 向上回溯最近一个带语言标注的围栏代码块，返回其 scopeName。 */
    private fun findNearestFenceLanguage(lineIndex: Int): String? {
        val ref = contentRef ?: return null
        val lineCount = ref.lineCount
        var start = (lineIndex - 1).coerceAtMost(lineCount - 1)
        val end = maxOf(0, start - FENCE_LOOKBACK)
        for (ln in start downTo end) {
            val line = ref.getLine(ln) ?: continue
            val trimmed = line.trimStart()
            if (!trimmed.startsWith("```") && !trimmed.startsWith("~~~")) continue
            // 解析 info string：```python → python
            val info = trimmed.removePrefix("```").removePrefix("~~~").trim()
            if (info.isEmpty()) continue // 空围栏（闭合符或无语言标注），继续向上找
            val langToken = info.split(Regex("\\s+")).firstOrNull() ?: continue
            langNameToScope(langToken)?.let { return it }
        }
        return null
    }

    /** 围栏 info string 中的语言名 → TextMate scopeName。 */
    private fun langNameToScope(name: String): String? {
        return when (name.lowercase()) {
            "py", "python" -> "source.python"
            "js", "javascript" -> "source.js"
            "mjs", "cjs" -> "source.js"
            "ts", "typescript" -> "source.ts"
            "jsx" -> "source.js.jsx"
            "tsx" -> "source.tsx"
            "java" -> "source.java"
            "kt", "kotlin" -> "source.kotlin"
            "c" -> "source.c"
            "cpp", "c++", "cc", "cxx" -> "source.cpp"
            "rs", "rust" -> "source.rust"
            "go", "golang" -> "source.go"
            "php" -> "source.php"
            "html" -> "text.html.basic"
            "vue" -> "text.html.vue"
            "css" -> "source.css"
            "scss" -> "source.css.scss"
            "sql" -> "source.sql"
            "sh", "bash", "shell", "zsh" -> "source.shell"
            "rb", "ruby" -> "source.ruby"
            "json" -> "source.json"
            "yaml", "yml" -> "source.yaml"
            "xml" -> "text.xml"
            else -> null
        }
    }

    /** 从当前 TextMate 主题取默认前景色（ARGB）；失败返回 0。 */
    private fun resolveDefaultForeground(): Int {
        return runCatching {
            val theme = ThemeRegistry.getInstance().currentThemeModel?.theme
                ?: return@runCatching 0
            val fgId = theme.defaults.foregroundId
            if (fgId < 0) return@runCatching 0
            val colorStr = theme.getColor(fgId) ?: return@runCatching 0
            ColorUtils.parseRGBAToARGB(colorStr)
        }.getOrDefault(0)
    }
}
