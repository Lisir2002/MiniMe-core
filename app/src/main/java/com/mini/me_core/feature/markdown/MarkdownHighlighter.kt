package com.mini.me_core.feature.markdown

/**
 * Markdown 高亮引擎对外门面。
 *
 * 供 SoraCodeViewer 等外部组件使用，隐藏内部的 TextMate 分析器与行内代码高亮细节。
 *
 * 典型用法：
 * ```
 * val highlighter = MarkdownHighlighter()
 * val lang = highlighter.createLanguage()
 * editor.colorScheme = TextMateManager.createColorScheme()
 * editor.setEditorLanguage(lang)
 * // ...
 * highlighter.onDestroy() // 页面销毁时调用
 * ```
 */
class MarkdownHighlighter {

    /** 行内代码高亮器（与所有 MarkdownAnalyzer 共享，复用 LRU 缓存）。 */
    private val inlineCodeHighlighter = InlineCodeHighlighter()

    /**
     * 创建配置好的 [MarkdownLanguage]，可直接交给 CodeEditor.setEditorLanguage()。
     * 必须在 TextMateManager 完成初始化之后调用。
     */
    fun createLanguage(): MarkdownLanguage {
        return MarkdownLanguage.create(inlineCodeHighlighter)
    }

    /** 暴露行内代码高亮器，供外部做异步预览/调试。 */
    fun getInlineCodeHighlighter(): InlineCodeHighlighter = inlineCodeHighlighter

    /** 主题切换时调用：行内代码缓存的颜色已失效，必须清空。 */
    fun onThemeChanged() {
        inlineCodeHighlighter.clearCache()
    }

    /** 释放资源。 */
    fun onDestroy() {
        inlineCodeHighlighter.destroy()
    }
}
