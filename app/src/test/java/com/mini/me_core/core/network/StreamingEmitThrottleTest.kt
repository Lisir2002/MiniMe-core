package com.mini.me_core.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamingEmitThrottle] 行为单测（防回归）。
 *
 * 该节流器是「长对话不再被 LMKD 静默杀进程」治理的一环：它决定了下游多久取一次
 * 全量累积文本。若被改坏（例如每次都 emit 或永远不 emit），长输出的内存表现会
 * 直接退回 O(n²)，因此这里把窗口、字符兜底、flush、reset 四条规则都钉死。
 */
class StreamingEmitThrottleTest {

    /**
     * 伪时钟：显式推进时间，避免依赖真实时钟造成 flaky。
     *
     * 初值取一个「已运行很久」的量级（与真实 System.currentTimeMillis() 同量级），
     * 这样节流器的「首次调用必然 emit」判据（now - 0 >= windowMs）才成立——
     * 真实运行环境下同样成立。
     */
    private var now = 1_700_000_000_000L

    private fun throttle(
        windowMs: Long = StreamingEmitThrottle.DEFAULT_WINDOW_MS,
        maxPendingChars: Int = StreamingEmitThrottle.DEFAULT_MAX_PENDING_CHARS,
    ) = StreamingEmitThrottle(windowMs, maxPendingChars) { now }

    @Test
    fun firstCallAlwaysEmits() {
        val t = throttle()
        // 首次必须立刻输出，否则首帧内容要等一个窗口才可见。
        assertTrue("首次调用应当 emit", t.shouldEmit(1))
    }

    @Test
    fun mergesCallsInsideWindow() {
        val t = throttle(windowMs = 80L)
        assertTrue(t.shouldEmit(1))
        // 窗口内继续追加：不应重复 emit
        assertFalse("窗口内不应 emit", t.shouldEmit(1))
        assertFalse("窗口内不应 emit", t.shouldEmit(1))
        // 跨过窗口后恢复
        now += 80L
        assertTrue("跨过窗口后应当 emit", t.shouldEmit(1))
    }

    @Test
    fun charBudgetForcesEmitEvenInsideWindow() {
        val t = throttle(windowMs = 10_000L, maxPendingChars = 100)
        assertTrue(t.shouldEmit(10))
        assertFalse("未达字符兜底且未过窗口时不应 emit", t.shouldEmit(10))
        // 累计达到 100 字符（10 + 90）：即使窗口远未到期也强制 emit
        assertTrue("累计达到字符兜底应当 emit", t.shouldEmit(90))
    }

    @Test
    fun flushEmitsOnlyWhenPendingExists() {
        val t = throttle(windowMs = 10_000L, maxPendingChars = 1_000)
        assertTrue(t.shouldEmit(5))
        assertFalse("窗口内的第二次不应 emit", t.shouldEmit(5))
        assertTrue("flush 应把窗口内残留发出", t.flush())
        assertFalse("flush 后无残留，再 flush 不应 emit", t.flush())
        // 没有新内容时也不应补发
        assertFalse("无新增内容时不应 emit", t.shouldEmit(0))
        assertFalse(t.flush())
    }

    @Test
    fun resetDiscardsPendingAndRestarts() {
        val t = throttle(windowMs = 10_000L, maxPendingChars = 1_000)
        assertTrue(t.shouldEmit(50))
        t.reset()
        // reset 后回到初始态：下一次调用必然 emit（流重试后要立刻可见）
        assertTrue("reset 后应重新立即 emit", t.shouldEmit(1))
    }

    @Test
    fun longOutputEmitCountIsBounded() {
        // 20 万字符、每 chunk 8 字符（贴近真实 token 粒度）：
        // 逐 token emit 会产生 2.5 万次全量拷贝；节流后应降到数百次量级。
        val t = throttle()
        val chunkCount = 200_000 / 8
        var emits = 0
        repeat(chunkCount) {
            // 时间也随 chunk 推进，模拟 60 秒内吐完 20 万字符。
            now += 2L
            if (t.shouldEmit(8)) emits++
        }
        assertTrue(
            "20 万字符的 emit 次数应远小于 chunk 数（实际 emits=$emits / chunks=$chunkCount）",
            emits < chunkCount / 10,
        )
        assertEquals("末次 flush 前应仍有残留可发", true, t.flush())
    }
}
