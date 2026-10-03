package com.mini.me_core.feature.editor.snippets

import android.os.Bundle
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleSnippetCompletionItem
import io.github.rosemoe.sora.lang.completion.SnippetDescription
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

/**
 * 带片段补全的 Language 包装器。
 *
 * 委托给原生 [TextMateLanguage]（负责语法高亮、标识符补全），并在 [requireAutoComplete] 中
 * 追加匹配的代码片段补全项。选中片段后由 sora-editor 的 SnippetController 自动展开
 * （占位符高亮、Tab 跳转、相同编号同步修改、`${0}` 最终光标均由其内置引擎处理）。
 *
 * 仅在编辑模式（autoCompletion=true）下由 SoraCodeViewer 包装创建。
 *
 * @property inner 原生 TextMateLanguage
 * @property scopeName 当前文件的 TextMate scope（用于匹配对应语言的片段）
 */
class SnippetLanguageWrapper(
    private val inner: TextMateLanguage,
    private val scopeName: String,
) : Language {

    override fun getAnalyzeManager(): AnalyzeManager = inner.analyzeManager

    override fun getInterruptionLevel(): Int = inner.interruptionLevel

    /**
     * 先让原生语言发布标识符/关键字补全，再追加片段补全项。
     */
    @Throws(CompletionCancelledException::class)
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle,
    ) {
        // 1. 原生语言的补全（标识符等）
        inner.requireAutoComplete(content, position, publisher, extraArguments)

        // 2. 片段补全
        if (!SnippetSettings.completionEnabled) return
        runCatching {
            val prefix = computeWordPrefix(content, position)
            if (prefix.isEmpty()) return
            val snippets = SnippetRepository.match(scopeName, prefix)
            for (snippet in snippets) {
                runCatching {
                    val codeSnippet = CodeSnippetParser.parse(snippet.rawBody())
                    val desc = SnippetDescription(prefix.length, codeSnippet, true)
                    publisher.addItem(
                        SimpleSnippetCompletionItem(snippet.prefix, snippet.description, desc),
                    )
                }
            }
            publisher.updateList()
        }
    }

    /** 计算光标左侧的「触发词前缀」（连续的标识符字符）。 */
    private fun computeWordPrefix(content: ContentReference, position: CharPosition): String {
        val line = content.getLine(position.line)
        val end = position.column
        var start = end
        while (start > 0) {
            val c = line[start - 1]
            if (c.isLetterOrDigit() || c == '_') start-- else break
        }
        return line.substring(start, end)
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int =
        inner.getIndentAdvance(content, line, column)

    override fun useTab(): Boolean = inner.useTab()

    override fun getFormatter(): Formatter = inner.formatter

    override fun getSymbolPairs(): SymbolPairMatch = inner.symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler> =
        inner.newlineHandlers ?: emptyArray()

    override fun getQuickQuoteHandler(): QuickQuoteHandler = inner.quickQuoteHandler!!

    override fun destroy() = inner.destroy()
}
