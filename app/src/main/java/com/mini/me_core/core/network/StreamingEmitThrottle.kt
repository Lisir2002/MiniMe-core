package com.mini.me_core.core.network

/**
 * 流式输出 emit 节流器：把「每个 token 一次」的下游事件合并为「窗口/字符数」级别。
 *
 * 存在理由（长对话内存治理）：
 * 流式链路上每来一个 token 都要取一次全量累积文本（`DeltaAccumulator.text` →
 * `StringBuilder.toString()`），一次 20 万字符的回复会产生约 5 万次全量拷贝、
 * 累计数十 GB 的分配抖动，是长对话 / 长输出被 LMKD 静默杀进程的主要推手。
 * 节流后同样的回复只 emit 数百次，累计分配下降两个数量级。
 *
 * 两条触发规则（任一满足即 emit）：
 *  1. **时间窗口**：距上次 emit 超过 [windowMs] 毫秒——保证 UI 的打字机观感（80ms 内人眼难分辨）；
 *  2. **字符兜底**：累计新增字符达到 [maxPendingChars]——保证长输出不被时间窗口拖慢。
 *
 * 流结束（或重试前重置）必须调用 [flush]，把窗口内最后一次累积发出，避免尾部内容滞后。
 *
 * 本类是纯 Kotlin（不依赖 Android / 协程），便于单元测试注入时间源。
 *
 * @param windowMs 时间窗口（毫秒）
 * @param maxPendingChars 字符兜底阈值（按「新增字符数」累计，非全文长度）
 * @param timeProvider 时间源，测试可注入伪时钟
 */
class StreamingEmitThrottle(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxPendingChars: Int = DEFAULT_MAX_PENDING_CHARS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var lastEmitAtMillis = 0L
    private var pendingChars = 0

    /**
     * 累积 [appendedChars] 个字符后判断是否应当 emit。
     *
     * @param appendedChars 本次实际追加的字符数（被去重 / 折叠掉的 chunk 传 0）
     * @return true 表示调用方应当发出一次事件
     */
    fun shouldEmit(appendedChars: Int): Boolean {
        if (appendedChars > 0) pendingChars += appendedChars
        val now = timeProvider()
        // 字符兜底优先：长输出时不能让时间窗口成为吞吐瓶颈。
        if (pendingChars >= maxPendingChars) return consume(now)
        // 首次 emit（lastEmitAtMillis == 0）必然命中时间窗口，保证首帧立刻可见。
        if (now - lastEmitAtMillis >= windowMs) return consume(now)
        return false
    }

    /**
     * 流结束前强制输出一次：把窗口内最后一次累积发出，避免尾部内容滞后。
     *
     * @return true 表示存在未发出的累积、调用方应当补发一次；false 表示无残留，无需补发
     */
    fun flush(): Boolean {
        if (pendingChars == 0) return false
        return consume(timeProvider())
    }

    /**
     * 清空节流状态。用于流重试 / 累积器 reset：此时下游会被整体重置，
     * 不应再补发旧窗口内残留的累积。
     */
    fun reset() {
        lastEmitAtMillis = 0L
        pendingChars = 0
    }

    private fun consume(now: Long): Boolean {
        lastEmitAtMillis = now
        pendingChars = 0
        return true
    }

    companion object {
        /** 默认时间窗口：80ms（Compose 约 5 帧，观感上仍是连续打字）。 */
        const val DEFAULT_WINDOW_MS = 80L

        /** 默认字符兜底：累计新增 1024 字符即强制 emit 一次。 */
        const val DEFAULT_MAX_PENDING_CHARS = 1024
    }
}
