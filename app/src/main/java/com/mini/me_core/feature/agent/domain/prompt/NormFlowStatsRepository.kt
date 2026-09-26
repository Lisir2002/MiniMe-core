package com.mini.me_core.feature.agent.domain.prompt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AGENT 规范运行统计（Singleton，内存累计）：
 *
 * 记录累计注入 token 估算值（[recordInjection] 在 step 前注入块装配完成后调用）。
 * token 估算按 字符数 / 4（中英文混合粗估），与 TokenUsageIndicator.formatTokenCountShort 展示对齐。
 *
 * 仅内存统计，进程重启清零；仪表盘「重置统计」调 [clear]。
 */
@Singleton
class NormFlowStatsRepository @Inject constructor() {

    private val _injectTokenCount = MutableStateFlow(0)
    /** 累计注入 token 估算值（字符数 / 4 累加）。 */
    val injectTokenCount: StateFlow<Int> = _injectTokenCount.asStateFlow()

    /**
     * 记录一次 step 前注入块的字符数，按 charCount / 4 累加 token 估算。
     * 传入非正字符数时忽略（无注入）。
     */
    fun recordInjection(charCount: Int) {
        if (charCount <= 0) return
        _injectTokenCount.value = _injectTokenCount.value + charCount / CHARS_PER_TOKEN
    }

    /** 清空全部统计（仪表盘「重置统计」用）。 */
    fun clear() {
        _injectTokenCount.value = 0
    }

    private companion object {
        /** 中英文混合 token 粗估换算：约 4 字符 ≈ 1 token。 */
        const val CHARS_PER_TOKEN = 4
    }
}
