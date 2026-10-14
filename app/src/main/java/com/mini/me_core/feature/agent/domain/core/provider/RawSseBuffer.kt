package com.mini.me_core.feature.agent.domain.core.provider

/**
 * 原始 SSE 累积缓冲（带字符上限）。
 *
 * 存在理由（长对话内存治理）：
 * 各家 adapter 原先直接用一个无上限的 `StringBuilder` 累积整轮原始 SSE，
 * 打算「整轮结束后一次性落盘」。一次几十 MB 的长响应会把这些字符全量驻留在堆里，
 * 既推高内存峰值（诱发 LMKD 静默杀进程），又因为「还没到整轮结束」而**一行都落不了盘**——
 * 长输出崩溃时恰恰什么日志都拿不到。
 *
 * 这里保留「整轮结束后落盘」的既有语义（避免高频写盘），只加一道护栏：
 * 超过 [maxChars] 后停止累积并统计丢弃量，落盘时附一行省略说明，
 * 保证可诊断性不丢（能看出「日志被截断了多少」），同时把内存占用钉死在上限内。
 *
 * 本类是纯 Kotlin，可单测。
 */
internal class RawSseBuffer(
    private val maxChars: Int = MAX_RAW_SSE_CHARS,
) {
    private val builder = StringBuilder()
    private var droppedChars = 0L

    /** 累积一行原始 SSE；超出上限后只统计丢弃量，不再占用内存。 */
    fun appendLine(line: String) {
        val remaining = maxChars - builder.length
        if (remaining <= 0) {
            droppedChars += line.length + 1
            return
        }
        if (line.length <= remaining) {
            builder.append(line).append('\n')
        } else {
            // 只写入能容纳的部分（含换行符位置），其余计入丢弃。
            builder.append(line, 0, remaining)
            droppedChars += line.length - remaining + 1
        }
    }

    /** 生成落盘文本：超限时附省略说明，保留「被截断了多少」的可诊断信息。 */
    fun buildText(): String = buildString {
        append(builder)
        if (droppedChars > 0) {
            append("\n…（原始 SSE 超出 ")
            append(maxChars)
            append(" 字符上限，已省略 ")
            append(droppedChars)
            append(" 字符）\n")
        }
    }

    /** 已累积的字符数（不含被丢弃的部分）。 */
    fun length(): Int = builder.length

    /** 被丢弃的字符数。 */
    fun droppedChars(): Long = droppedChars

    companion object {
        /** 单轮原始 SSE 的累积上限：256KB（足够诊断，又不至于拖垮堆）。 */
        const val MAX_RAW_SSE_CHARS = 256 * 1024
    }
}
