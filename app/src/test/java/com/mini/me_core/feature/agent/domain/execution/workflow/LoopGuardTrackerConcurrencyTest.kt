package com.mini.me_core.feature.agent.domain.execution.workflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LoopGuardTracker 并发计数正确性。
 * 并行 awaitAll 下连续相同调用 N 次，count 必须等于 N（无丢失/重复）。
 */
class LoopGuardTrackerConcurrencyTest {

    @Test
    fun record_serial_countsCorrect() {
        val t = LoopGuardTracker(thresholds = listOf(3, 5, 8))
        repeat(5) { t.record("Bash", mapOf("c" to jsonArg("ls"))) }
        assertEquals(5, t.currentCount)
        assertNotNull(t.takeAdvisory()) // 3 级已提醒
    }

    @Test
    fun record_parallel_sameArgs_countsCorrect() = runBlocking {
        val t = LoopGuardTracker(thresholds = listOf(3, 5, 8))
        val n = 100
        withContext(Dispatchers.Default) {
            (1..n).map {
                async { t.record("Bash", mapOf("c" to jsonArg("ls -la"))) }
            }.awaitAll()
        }
        // 100 次全是同一工具同一参数，串行化后计数必须严格等于 100。
        assertEquals(n, t.currentCount)
    }

    @Test
    fun record_parallel_mixedArgs_resetsStreak() = runBlocking {
        val t = LoopGuardTracker(thresholds = listOf(3))
        withContext(Dispatchers.Default) {
            (1..50).map { i ->
                async {
                    val tool = if (i % 2 == 0) "Bash" else "ls"
                    t.record(tool, mapOf("n" to jsonArg(i.toString())))
                }
            }.awaitAll()
        }
        // 每次参数不同，streak 应被重置为 1。
        assertEquals(1, t.currentCount)
        assertNull(t.takeAdvisory())
    }

    private fun jsonArg(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
}
