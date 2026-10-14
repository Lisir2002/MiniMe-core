package com.mini.me_core.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DeltaAccumulator 的长输出性能预算测试（防回归）。
 *
 * 背景：早期实现在每个 chunk 都要 `buffer.toString()` 维护一份「上次完整快照」，
 * 加上下游每 token 读取一次全量文本，一次 20 万字符的回复会产生约 5 万次 O(n) 拷贝
 * （累计数十 GB 分配），是长对话 / 长输出被 LMKD 静默杀进程的主因。
 *
 * 治理后：
 *  - 去重比较改为零分配的 CharSequence 比对（O(chunk 长度)）；
 *  - `text` 读取带缓存；
 *  - 下游 emit 由 [StreamingEmitThrottle] 节流。
 *
 * 这里用「20 万字符、8 字符/token」的规模同时校验两件事：
 *  1. 总耗时在预算内（旧实现在此规模下是秒级到十秒级）；
 *  2. emit 次数被节流压到百次量级（若有人拆掉节流，此断言立即失败）。
 *
 * 阈值刻意取宽松值：目的是拦住数量级的退化，而不是做精确基准。
 */
class DeltaAccumulatorPerfTest {

    private companion object {
        const val TOTAL_CHARS = 200_000
        const val CHUNK_SIZE = 8
        /** 耗时预算：治理后实测应在百毫秒级，这里留足机器差异的余量。 */
        const val BUDGET_MS = 5_000L
    }

    @Test
    fun longStreamStaysWithinBudget() {
        val acc = DeltaAccumulator()
        val throttle = StreamingEmitThrottle()
        val chunk = "x".repeat(CHUNK_SIZE)
        val chunkCount = TOTAL_CHARS / CHUNK_SIZE

        val start = System.currentTimeMillis()
        var emits = 0
        var totalLength = 0
        repeat(chunkCount) { i ->
            // 每个 chunk 内容不同（避免被 AUTO_DETECT 去重，保持真实的累积路径）
            val appended = acc.accept("$chunk$i").appendedLength()
            if (throttle.shouldEmit(appended)) {
                totalLength += acc.text.length
                emits++
            }
        }
        if (throttle.flush()) {
            totalLength += acc.text.length
            emits++
        }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "20 万字符流式累积耗时 ${elapsed}ms 超出预算 ${BUDGET_MS}ms（疑似退回 O(n²) 拷贝）",
            elapsed < BUDGET_MS,
        )
        assertTrue(
            "emit 次数应被节流压低（实际 emits=$emits，chunk 数=$chunkCount）",
            emits < chunkCount / 10,
        )
        // 内容正确性：总输入（每 chunk 8 字符 + 递增序号）远超累积器的 20 万字符护栏，
        // 因此最终长度应被钉在上限处，且截断标记已置位。
        assertTrue(
            "累积文本应达到 20 万字符护栏（实际 ${acc.text.length}）",
            acc.text.length >= 200_000,
        )
        assertTrue("超出护栏后应标记截断", acc.isTruncated)
        assertTrue("统计长度应大于 0", totalLength > 0)
    }

    @Test
    fun repeatedTextReadDoesNotRecompute() {
        val acc = DeltaAccumulator()
        acc.accept("a".repeat(50_000))
        // 连续多次读取应命中缓存：这里只验证结果稳定，耗时由上面预算测试覆盖。
        val first = acc.text
        repeat(100) { assertEquals(first, acc.text) }
    }
}
