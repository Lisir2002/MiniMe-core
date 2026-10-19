package com.mini.me_core.feature.settings.domain.model

object ModelContextPolicy {
    const val DEFAULT_CONTEXT_TOKENS = 128_000
    const val DEFAULT_OUTPUT_RESERVE_TOKENS = 20_000
    const val MIN_PRESERVE_RECENT_TOKENS = 2_000
    const val MAX_PRESERVE_RECENT_TOKENS = 8_000
    const val CHARS_PER_TOKEN = 4

    fun usableInputTokens(metadata: ModelMetadata): Int {
        val context = metadata.contextTokens.takeIf { it > 0 } ?: DEFAULT_CONTEXT_TOKENS
        val output = metadata.outputTokens?.takeIf { it > 0 } ?: DEFAULT_OUTPUT_RESERVE_TOKENS
        val reserved = minOf(DEFAULT_OUTPUT_RESERVE_TOKENS, output)
        return metadata.inputTokens
            ?.let { (it - reserved).coerceAtLeast(MIN_PRESERVE_RECENT_TOKENS) }
            ?: (context - reserved).coerceAtLeast(MIN_PRESERVE_RECENT_TOKENS)
    }

    fun preserveRecentTokens(usableTokens: Int): Int =
        (usableTokens / 4).coerceIn(MIN_PRESERVE_RECENT_TOKENS, MAX_PRESERVE_RECENT_TOKENS)

    /** 纯字符数口径（英文启发式）：4 字符 ≈ 1 token。仅用于无文本可分析的场景。 */
    fun estimateTokens(chars: Int): Int =
        (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /**
     * CJK-aware 文本口径：中日韩表意文字每字符约 1 token（主流 tokenizer 实测 0.7~1.2），
     * 其余（英文/数字/符号/代码）维持 [CHARS_PER_TOKEN] 口径。
     *
     * 历史教训：曾全量按 chars/4 估算，中文会话被低估约 4 倍——压缩触发器与尾随预算
     * 都按错的尺度计算，真实载荷已是模型窗口数倍时应用还以为"未到阈值"，
     * 压缩请求本身也随之超限被 API 拒绝（见 ContextCompactor 窗口式压缩）。
     */
    fun estimateTokens(text: String): Int {
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjkChar(ch)) cjk++ else other++
        }
        return cjk + (other + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN
    }

    /** CJK 表意/音节文字 + 全角符号（按 token 计 1 个的字符类）。 */
    private fun isCjkChar(ch: Char): Boolean = when (ch.code) {
        in 0x2E80..0x2EFF -> true   // CJK 部首补充
        in 0x3000..0x303F -> true   // CJK 标点
        in 0x3040..0x30FF -> true   // 日文平假名/片假名
        in 0x3100..0x312F -> true   // 注音符号
        in 0x3400..0x4DBF -> true   // CJK 统一表意扩展 A
        in 0x4E00..0x9FFF -> true   // CJK 统一表意（基本区）
        in 0xA000..0xA48F -> true   // 彝文
        in 0xAC00..0xD7AF -> true   // 韩文音节
        in 0xF900..0xFAFF -> true   // CJK 兼容表意
        in 0xFE30..0xFE4F -> true   // CJK 兼容形式
        in 0xFF00..0xFFEF -> true   // 全角字母/符号
        else -> false
    }
}

