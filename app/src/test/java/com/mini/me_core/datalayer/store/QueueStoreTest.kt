package com.mini.me_core.datalayer.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mini.mecore.datalayer.sqldelight.InfraDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Queue] 一等可靠队列的行为测试（内存 SQLite，走 SQLDelight 编译生成的查询）。
 *
 * 覆盖：入队自增 id、pending 按 topic+status+next_run 过滤并按 next_run 升序、
 * mark 状态流转、get 按 id、delete，以及「到点才可见」的时间闸门语义。
 */
class QueueStoreTest {

    private fun freshQueue(): Queue {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        InfraDb.Schema.create(driver)
        return Queue(InfraDb(driver))
    }

    @Test
    fun enqueue_returnsMonotonicIncreasingIds() {
        val q = freshQueue()
        val id1 = q.enqueue("topic-a", "payload-1")
        val id2 = q.enqueue("topic-a", "payload-2")
        val id3 = q.enqueue("topic-a", "payload-3")

        assertEquals(1L, id1)
        assertEquals(2L, id2)
        assertEquals(3L, id3)
    }

    @Test
    fun pending_returnsOnlyDueItemsSortedByNextRun() {
        val q = freshQueue()
        q.enqueue("a", "later", nextRun = 10_000L)
        q.enqueue("a", "earlier", nextRun = 5_000L)
        q.enqueue("a", "now", nextRun = 1_000L)

        val due = q.pending("a", now = 6_000L)
        assertEquals(listOf("now", "earlier"), due.map { it.payload })
    }

    @Test
    fun pending_filtersByTopic() {
        val q = freshQueue()
        q.enqueue("a", "payload-a", nextRun = 1L)
        q.enqueue("b", "payload-b", nextRun = 1L)

        assertEquals(1, q.pending("a", now = 9_999L).size)
        assertEquals("payload-a", q.pending("a", now = 9_999L).first().payload)
    }

    @Test
    fun pending_doesNotReturnFutureItems() {
        val q = freshQueue()
        q.enqueue("a", "future", nextRun = 100_000L)

        assertTrueEmpty(q.pending("a", now = 1_000L))
    }

    @Test
    fun pending_excludesNonPendingStatus() {
        val q = freshQueue()
        val id = q.enqueue("a", "work", nextRun = 1L)
        q.mark("done", attempt = 1, error = null, id = id)

        assertTrueEmpty(q.pending("a", now = 9_999L))
    }

    @Test
    fun mark_updatesStatusAttemptAndError() {
        val q = freshQueue()
        val id = q.enqueue("a", "work", nextRun = 1L)

        q.mark("running", attempt = 1, error = null, id = id)
        assertEquals("running", q.get(id)!!.status)
        assertEquals(1L, q.get(id)!!.attempt)

        q.mark("failed", attempt = 2, error = "boom", id = id)
        assertEquals("failed", q.get(id)!!.status)
        assertEquals(2L, q.get(id)!!.attempt)
        assertEquals("boom", q.get(id)!!.errorMsg)
    }

    @Test
    fun get_unknownId_returnsNull() {
        val q = freshQueue()
        assertNull(q.get(999L))
    }

    @Test
    fun delete_removesItem() {
        val q = freshQueue()
        val id = q.enqueue("a", "work", nextRun = 1L)
        q.delete(id)
        assertNull(q.get(id))
    }

    @Test
    fun emptyTopic_returnsEmptyList() {
        val q = freshQueue()
        assertTrueEmpty(q.pending("no-such-topic", now = 1L))
    }

    private fun assertTrueEmpty(list: List<QueueItem>) {
        assertEquals(0, list.size)
    }
}
