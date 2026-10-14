package com.mini.me_core.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FileLogger.formatPending] 的纯函数单测（防回归）。
 *
 * 这个函数是「崩溃 / 退出前把内存队列里的日志抢救到磁盘」的核心：
 * 旧实现的 `flushSync` 只写一行 summary，队列里最多 8192 行崩溃前的日志全部丢失，
 * 正是「闪退后查不到日志」的直接原因。这里把它的三条契约钉死——
 * 顺序、字符预算（崩溃路径不做重 IO）、落盘前脱敏。
 */
class FileLoggerDrainTest {

    private fun queued(
        level: String = "INFO",
        tag: String = "Tag",
        message: String = "message",
        stack: String? = null,
    ) = FileLogger.QueuedLine(level, tag, message, stack)

    @Test
    fun keepsQueueOrder() {
        val out = FileLogger.formatPending(
            lines = listOf(
                queued(tag = "FIRST", message = "第一行"),
                queued(tag = "SECOND", message = "第二行"),
                queued(tag = "THIRD", message = "第三行"),
            ),
            maxChars = 64 * 1024,
        )
        val first = out.indexOf("FIRST")
        val second = out.indexOf("SECOND")
        val third = out.indexOf("THIRD")
        assertTrue("三行都应被格式化输出", first >= 0 && second >= 0 && third >= 0)
        assertTrue("应保持入队顺序（FIRST 在 SECOND 前）", first < second)
        assertTrue("应保持入队顺序（SECOND 在 THIRD 前）", second < third)
    }

    @Test
    fun respectsCharBudget() {
        // 崩溃路径必须毫秒级返回：预算给到极小，只应输出第一行就不再继续。
        val out = FileLogger.formatPending(
            lines = listOf(
                queued(tag = "ONLY", message = "这一行之后的内容都不应再拼"),
                queued(tag = "LATER", message = "不应出现"),
            ),
            maxChars = 10,
        )
        assertTrue("预算内应输出第一行", out.contains("ONLY"))
        assertFalse("超出字符预算后不应继续拼接", out.contains("LATER"))
        assertTrue("输出应被预算限制住", out.length < 200)
    }

    @Test
    fun sanitizesBeforeWrite() {
        // 日志会被导出到公共目录，落盘前必须脱敏。
        val out = FileLogger.formatPending(
            lines = listOf(
                queued(tag = "Net", message = """{"apiKey":"sk-abc123secret"}"""),
            ),
            maxChars = 64 * 1024,
        )
        assertFalse("敏感值不应残留: $out", out.contains("sk-abc123secret"))
        assertTrue("应打码: $out", out.contains("[REDACTED"))
    }

    @Test
    fun includesLevelAndThread() {
        val out = FileLogger.formatPending(
            lines = listOf(queued(level = "ERROR", tag = "Crash", message = "boom")),
            maxChars = 1024,
            threadName = "crash-thread",
        )
        assertTrue("应包含级别", out.contains("ERROR"))
        assertTrue("应包含 tag", out.contains("Crash"))
        assertTrue("应包含线程名", out.contains("crash-thread"))
        assertTrue("应包含消息", out.contains("boom"))
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertTrue(FileLogger.formatPending(emptyList(), 1024).isEmpty())
    }
}
