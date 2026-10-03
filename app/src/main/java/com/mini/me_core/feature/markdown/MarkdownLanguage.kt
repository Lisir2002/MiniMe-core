package com.mini.me_core.feature.markdown

import android.util.Log
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import com.mini.me_core.feature.editor.textmate.TextMateManager

/**
 * Markdown 语言（sora-editor [io.github.rosemoe.sora.lang.Language] 实现）。
 *
 * 与普通 TextMate 语言的区别仅在于：分析器换成了 [MarkdownAnalyzer]，
 * 在 markdown grammar 高亮之上叠加行内代码语法着色。
 *
 * 其余能力（符号配对、换行处理、缩进）直接委托给一个内部创建的
 * markdown [TextMateLanguage] 实例，避免重复实现。
 *
 * 注意：委托用的 [TextMateLanguage] 仅借用其辅助能力，不会被 attach 到编辑器，
 * 因此它的分析线程不会真正启动。
 */
class MarkdownLanguage private constructor(
    private val delegate: TextMateLanguage,
    private val analyzeManager: MarkdownAnalyzer,
) : EmptyLanguage() {

    companion object {
        private const val TAG = "MarkdownLanguage"

        /** 增强版 markdown grammar 的 scopeName（符号淡化 + 内容高亮 scope 分离）。 */
        private const val SCOPE_MARKDOWN_ENHANCED = "text.html.markdown.enhanced"

        /** 普通 markdown grammar 的 scopeName，作为增强版加载失败时的回退。 */
        private const val SCOPE_MARKDOWN_FALLBACK = "text.html.markdown"

        /**
         * 工厂方法：创建配置好的 MarkdownLanguage。
         * 必须在 [TextMateManager] 完成初始化（grammar/主题已加载）之后调用。
         *
         * 优先使用增强版 grammar（[SCOPE_MARKDOWN_ENHANCED]）；
         * 若其未注册到 [GrammarRegistry]，则回退到普通版 [SCOPE_MARKDOWN_FALLBACK]。
         */
        fun create(inlineCodeHighlighter: InlineCodeHighlighter): MarkdownLanguage {
            // 优先探测增强版 grammar 是否已加载，决定实际使用的 scopeName
            val enhancedLoaded = runCatching {
                GrammarRegistry.getInstance().findGrammar(SCOPE_MARKDOWN_ENHANCED)
            }.getOrNull() != null
            val scopeToUse = if (enhancedLoaded) {
                SCOPE_MARKDOWN_ENHANCED
            } else {
                Log.w(TAG, "增强版 markdown grammar 未加载，回退到 $SCOPE_MARKDOWN_FALLBACK")
                SCOPE_MARKDOWN_FALLBACK
            }

            // 委托语言：借用其符号配对/换行/缩进等辅助能力
            val delegate = TextMateManager.createLanguage(
                scopeToUse,
                autoCompletion = false,
            )
            // 直接从 registry 取 markdown grammar，构造自定义分析器
            val grammar = GrammarRegistry.getInstance()
                .findGrammar(scopeToUse)
            checkNotNull(grammar) { "markdown grammar 未加载：$scopeToUse" }
            val config = runCatching {
                GrammarRegistry.getInstance().findLanguageConfiguration(scopeToUse)
            }.getOrNull()

            val analyzer = MarkdownAnalyzer(
                language = delegate,
                grammar = grammar,
                configuration = config,
                themeRegistry = ThemeRegistry.getInstance(),
                inlineCodeHighlighter = inlineCodeHighlighter,
            )
            return MarkdownLanguage(delegate, analyzer)
        }
    }

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getSymbolPairs(): SymbolPairMatch = delegate.symbolPairs ?: SymbolPairMatch()

    override fun getNewlineHandlers(): Array<NewlineHandler> =
        delegate.newlineHandlers ?: emptyArray()

    override fun getIndentAdvance(content: ContentReference, line: Int, lineContent: Int): Int =
        delegate.getIndentAdvance(content, line, lineContent)

    override fun useTab(): Boolean = delegate.useTab()

    override fun getFormatter(): Formatter = delegate.formatter

    override fun destroy() {
        analyzeManager.destroy()
        runCatching { delegate.destroy() }
            .onFailure { Log.w(TAG, "委托 TextMateLanguage 销毁失败", it) }
    }
}
