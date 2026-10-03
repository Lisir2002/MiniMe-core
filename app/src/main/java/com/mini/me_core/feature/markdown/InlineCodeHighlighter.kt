package com.mini.me_core.feature.markdown

import android.graphics.Typeface
import android.util.Log
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.utils.ColorUtils
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.internal.grammar.ScopeStack
import org.eclipse.tm4e.core.internal.theme.FontStyle
import org.eclipse.tm4e.core.internal.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 行内代码（`code`）语法高亮器。
 *
 * 职责：对 Markdown 行内反引号包裹的代码片段做与代码块同等质量的 TextMate 高亮。
 *
 * 处理流水线：
 *  1. 语言自动检测（上下文 → 特征匹配 → 启发式 → 兜底纯文本）
 *  2. 从 GrammarRegistry 取对应 IGrammar，按行 tokenize（保留跨行状态）
 *  3. 对每个 token，用当前 TextMate 主题把 scope 解析为 ARGB 颜色
 *  4. 组装为 [MarkdownSpan] 列表（偏移相对代码内容起点）
 *
 * 性能策略：
 *  - LRU 缓存（默认 500 条）：相同「内容+语言」只高亮一次
 *  - 长度阈值：超过 [InlineCodePipeline.MAX_HIGHLIGHT_CHARS] 字符只做等宽/背景，不做逐 token 着色
 *  - 高负载模式：外部可临时关闭逐 token 着色，只保留背景
 *  - [highlight] 在调用方线程执行（MarkdownAnalyzer 已在后台分析线程），
 *    [highlightAsync] 供外部直接调用时切到后台协程线程
 *
 * 缓存/阈值/高负载等纯策略见 [InlineCodePipeline]，本类只负责把 TextMate 取色接进去。
 */
class InlineCodeHighlighter {

    companion object {
        private const val TAG = "InlineCodeHighlighter"

        /** Markdown grammar 的 scopeName。 */
        const val MARKDOWN_SCOPE = "text.html.markdown"
    }

    /** 后台协程作用域，用于 highlightAsync。 */
    private val bgScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** 纯逻辑管线：语言检测 + 缓存 + 阈值 + 高负载。 */
    private val pipeline = InlineCodePipeline(
        tokenizer = { code, scopeName -> tokenize(code, scopeName) },
        grammarAvailable = { scopeName -> isGrammarAvailable(scopeName) },
    )

    /**
     * 同步高亮行内代码。在后台线程调用（MarkdownAnalyzer 的分析线程），不阻塞主线程。
     *
     * @param code 反引号内部的代码文本
     * @param contextLanguage 上下文推断出的语言 scopeName（如邻近代码块语言），可为 null
     * @return 高亮片段列表，偏移相对 code 起点；空列表表示不做逐 token 着色
     */
    fun highlight(code: String, contextLanguage: String? = null): List<MarkdownSpan> {
        return runCatching { pipeline.highlight(code, contextLanguage) }
            .onFailure { Log.d(TAG, "行内代码高亮失败: ${it.message}") }
            .getOrDefault(emptyList())
    }

    /**
     * 异步高亮：切到后台协程执行，完成后回调到主线程。
     * 供外部组件（非分析线程）直接使用。
     */
    fun highlightAsync(
        code: String,
        contextLanguage: String? = null,
        callback: (List<MarkdownSpan>) -> Unit,
    ) {
        bgScope.launch {
            val result = highlight(code, contextLanguage)
            withContext(Dispatchers.Main) { callback(result) }
        }
    }

    /** 清空缓存（主题切换后颜色会变，必须清空）。 */
    fun clearCache() {
        pipeline.clearCache()
    }

    /** 高负载模式开关：开启时临时关闭逐 token 着色。 */
    fun setHighLoadMode(enabled: Boolean) {
        pipeline.highLoadMode = enabled
    }

    /** 释放资源。 */
    fun destroy() {
        clearCache()
    }

    // ------------------------------------------------------------------
    // TextMate 可用性校验
    // ------------------------------------------------------------------

    /** 校验该 scopeName 的 grammar 是否已加载可用。 */
    private fun isGrammarAvailable(scopeName: String): Boolean {
        return runCatching { GrammarRegistry.getInstance().findGrammar(scopeName) != null }
            .getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // tm4e tokenize + 主题取色
    // ------------------------------------------------------------------

    /** 用 tm4e grammar 逐行 tokenize，把 token 翻译为 [MarkdownSpan]。 */
    private fun tokenize(code: String, scopeName: String): List<MarkdownSpan> {
        val grammar: IGrammar = GrammarRegistry.getInstance().findGrammar(scopeName) ?: return emptyList()
        val theme: Theme = ThemeRegistry.getInstance().currentThemeModel?.theme ?: return emptyList()

        val result = ArrayList<MarkdownSpan>()
        var state: org.eclipse.tm4e.core.grammar.IStateStack? = null
        var offset = 0

        for (line in code.split("\n")) {
            val lineResult = grammar.tokenizeLine(line, state, null)
            state = lineResult.ruleStack

            for (token in lineResult.tokens) {
                val scopes = token.scopes
                if (scopes.isEmpty()) continue
                val fg = resolveScopeColor(theme, scopes)
                val fs = resolveFontStyle(theme, scopes)
                result += MarkdownSpan(
                    start = offset + token.startIndex,
                    end = offset + token.endIndex,
                    color = fg,
                    scope = scopes.last(),
                    fontStyle = fs,
                )
            }
            offset += line.length + 1 // +1 对应被 split 掉的换行符
        }
        return result
    }

    /** 把一个 token 的 scope 栈解析为 ARGB 前景色；无法解析返回 0。 */
    private fun resolveScopeColor(theme: Theme, scopes: List<String>): Int {
        return runCatching {
            val attrs = theme.match(ScopeStack.from(scopes)) ?: return@runCatching 0
            val fgId = attrs.foregroundId
            if (fgId < 0) return@runCatching 0
            val colorStr = theme.getColor(fgId) ?: return@runCatching 0
            ColorUtils.parseRGBAToARGB(colorStr)
        }.getOrDefault(0)
    }

    /** 把 scope 栈解析为字体样式位（bold/italic）。 */
    private fun resolveFontStyle(theme: Theme, scopes: List<String>): Int {
        return runCatching {
            val attrs = theme.match(ScopeStack.from(scopes)) ?: return@runCatching Typeface.NORMAL
            var style = Typeface.NORMAL
            val fs = attrs.fontStyle
            if (fs and FontStyle.Bold != 0) style = style or Typeface.BOLD
            if (fs and FontStyle.Italic != 0) style = style or Typeface.ITALIC
            style
        }.getOrDefault(Typeface.NORMAL)
    }
}
