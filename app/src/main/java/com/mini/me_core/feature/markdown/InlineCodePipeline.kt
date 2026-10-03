package com.mini.me_core.feature.markdown

import java.util.Collections

/**
 * 行内代码高亮的纯逻辑管线（不依赖 Android / TextMate 运行时）。
 *
 * 从 [InlineCodeHighlighter] 抽取而来，负责：
 *  - 长度阈值与高负载短路
 *  - LRU 缓存命中
 *  - 语言检测委托给 [InlineCodeLanguageDetector]
 *  - 真正的 tokenize 由调用方通过 [tokenizer] 注入（生产环境接 TextMate，测试接 fake）
 *
 * 这样缓存命中、阈值、高负载等策略都能在 JVM 单测中直接覆盖。
 */
internal class InlineCodePipeline(
    private val tokenizer: (code: String, scopeName: String) -> List<MarkdownSpan>,
    private val grammarAvailable: (scopeName: String) -> Boolean,
    private val maxCacheSize: Int = CACHE_MAX_SIZE,
    private val maxHighlightChars: Int = MAX_HIGHLIGHT_CHARS,
) {

    companion object {
        /** LRU 缓存条数上限。 */
        const val CACHE_MAX_SIZE = 500

        /** 超过该字符数不做逐 token 着色，仅由分析器套用等宽/背景。 */
        const val MAX_HIGHLIGHT_CHARS = 500
    }

    /** 高负载模式：开启后临时关闭逐 token 着色，只返回空列表（由分析器套背景）。 */
    @Volatile
    var highLoadMode = false

    /** 缓存命中计数（测试观察点，生产环境仅作诊断用）。 */
    @Volatile
    var cacheHitCount = 0
        private set

    /**
     * LRU 缓存。key = "scopeName\u0000代码内容"，value = 高亮结果。
     * 用 synchronizedMap 包裹，避免分析线程与异步回调线程并发读写。
     */
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<MarkdownSpan>>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<MarkdownSpan>>,
            ): Boolean = size > maxCacheSize
        }
    )

    /**
     * 同步高亮行内代码。
     *
     * @param code 反引号内部的代码文本
     * @param contextLanguage 上下文推断出的语言 scopeName（如邻近代码块语言），可为 null
     * @return 高亮片段列表，偏移相对 code 起点；空列表表示不做逐 token 着色
     */
    fun highlight(code: String, contextLanguage: String? = null): List<MarkdownSpan> {
        // 高负载或过长：直接返回空，由上层套等宽/背景
        if (highLoadMode || code.length > maxHighlightChars || code.isEmpty()) {
            return emptyList()
        }

        val scopeName = InlineCodeLanguageDetector.detect(code, contextLanguage, grammarAvailable)
            ?: return emptyList() // 兜底纯文本：不做逐 token 着色

        val key = "$scopeName\u0000$code"
        cache[key]?.let {
            cacheHitCount++
            return it
        }

        val result = tokenizer(code, scopeName)

        // 只缓存有结果的，避免把重复失败结果也缓存进去
        if (result.isNotEmpty()) {
            cache[key] = result
        }
        return result
    }

    /** 清空缓存（主题切换后颜色会变，必须清空）。 */
    fun clearCache() {
        cache.clear()
    }
}
