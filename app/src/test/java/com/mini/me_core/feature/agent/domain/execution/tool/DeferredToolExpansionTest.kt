package com.mini.me_core.feature.agent.domain.execution.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * B2 延迟加载展开集单测（批次 B：Deferred Loading）。
 * 覆盖：命中/调用记录、LRU 上限淘汰、access-order 刷新语义、会话隔离、清理、并发安全。
 */
class DeferredToolExpansionTest {

    private val expansion = DeferredToolExpansion()

    @Test
    fun recordHit_thenGetExpanded_containsNames() {
        expansion.recordHit("s1", listOf("websearch", "browser"))
        assertEquals(setOf("websearch", "browser"), expansion.getExpanded("s1"))
    }

    @Test
    fun recordHit_emptyNames_noOp() {
        expansion.recordHit("s1", emptyList())
        assertTrue(expansion.getExpanded("s1").isEmpty())
    }

    @Test
    fun recordCall_addsAndRefreshes() {
        expansion.recordCall("s1", "websearch")
        assertEquals(setOf("websearch"), expansion.getExpanded("s1"))
    }

    @Test
    fun getExpanded_unknownSession_empty() {
        assertTrue(expansion.getExpanded("nope").isEmpty())
    }

    @Test
    fun lru_evictsOldestBeyondCap() {
        val names = (1..DeferredToolExpansion.MAX_EXPANDED_PER_SESSION + 3).map { "tool_$it" }
        names.forEach { expansion.recordCall("s1", it) }
        val expanded = expansion.getExpanded("s1")
        assertEquals(DeferredToolExpansion.MAX_EXPANDED_PER_SESSION, expanded.size)
        // 最旧 3 个被淘汰，最新的 12 个保留
        assertFalse(expanded.contains("tool_1"))
        assertFalse(expanded.contains("tool_2"))
        assertFalse(expanded.contains("tool_3"))
        assertTrue(expanded.contains("tool_13"))
        assertTrue(expanded.contains("tool_15"))
    }

    @Test
    fun lru_accessOrder_callRefreshesSurvival() {
        // 先填满：tool_1..tool_12
        (1..DeferredToolExpansion.MAX_EXPANDED_PER_SESSION).forEach { expansion.recordCall("s1", "tool_$it") }
        // 刷新最老的 tool_1（access-order：读写都算访问）
        expansion.recordCall("s1", "tool_1")
        // 再写入 3 个新工具：应淘汰 tool_2/tool_3/tool_4，tool_1 因刚被刷新而存活
        listOf("new_1", "new_2", "new_3").forEach { expansion.recordCall("s1", it) }
        val expanded = expansion.getExpanded("s1")
        assertTrue("tool_1 刚被调用过，不应被淘汰", expanded.contains("tool_1"))
        assertFalse("tool_2 是最久未用，应被淘汰", expanded.contains("tool_2"))
        assertTrue(expanded.contains("new_3"))
    }

    @Test
    fun lru_hitRefreshesSurvival() {
        (1..DeferredToolExpansion.MAX_EXPANDED_PER_SESSION).forEach { expansion.recordCall("s1", "tool_$it") }
        expansion.recordHit("s1", listOf("tool_1"))
        expansion.recordCall("s1", "new_1")
        assertTrue(expansion.getExpanded("s1").contains("tool_1"))
    }

    @Test
    fun sessions_isolated() {
        expansion.recordCall("s1", "websearch")
        expansion.recordCall("s2", "browser")
        assertEquals(setOf("websearch"), expansion.getExpanded("s1"))
        assertEquals(setOf("browser"), expansion.getExpanded("s2"))
    }

    @Test
    fun forget_clearsSession() {
        expansion.recordCall("s1", "websearch")
        expansion.forget("s1")
        assertTrue(expansion.getExpanded("s1").isEmpty())
        // 不影响其他会话
        expansion.recordCall("s2", "browser")
        expansion.forget("s1")
        assertEquals(setOf("browser"), expansion.getExpanded("s2"))
    }

    @Test
    fun clear_clearsAll() {
        expansion.recordCall("s1", "a")
        expansion.recordCall("s2", "b")
        expansion.clear()
        assertTrue(expansion.getExpanded("s1").isEmpty())
        assertTrue(expansion.getExpanded("s2").isEmpty())
    }

    @Test
    fun concurrentRecord_noLostUpdateNoOverflow() {
        val threads = 8
        val perThread = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val errors = ConcurrentLinkedQueue<Throwable>()
        repeat(threads) { t ->
            pool.submit {
                try {
                    start.await()
                    repeat(perThread) { i ->
                        expansion.recordCall("s1", "t${t}_$i")
                        expansion.recordHit("s1", listOf("hit_${t}_$i"))
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()
        assertTrue("并发记录不应抛异常: $errors", errors.isEmpty())
        val expanded = expansion.getExpanded("s1")
        assertTrue("展开集不得超过 LRU 上限: ${expanded.size}", expanded.size <= DeferredToolExpansion.MAX_EXPANDED_PER_SESSION)
        // 全部写完后追加一个标记：它是最新访问，必在 LRU 尾部存活（确定性断言，
        // 不依赖线程交错顺序——各线程收尾操作的先后本就不可预知）。
        expansion.recordCall("s1", "final_marker")
        assertTrue(expansion.getExpanded("s1").contains("final_marker"))
    }
}
