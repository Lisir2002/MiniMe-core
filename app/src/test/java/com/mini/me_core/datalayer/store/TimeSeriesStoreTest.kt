package com.mini.me_core.datalayer.store

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mini.mecore.datalayer.sqldelight.InfraDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [TimeSeries] 时序存储行为测试（内存 SQLite）。
 *
 * 覆盖：record 自增 id、byType 按 ts 升序、range 区间过滤、
 * purgeOlderThan 删除冷数据并返回剩余条数。
 */
class TimeSeriesStoreTest {

    private fun freshSeries(): TimeSeries {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        InfraDb.Schema.create(driver)
        return TimeSeries(InfraDb(driver))
    }

    @Test
    fun record_returnsMonotonicIncreasingIds() {
        val ts = freshSeries()
        val id1 = ts.record(1000L, "event", "{}")
        val id2 = ts.record(2000L, "event", "{}")
        assertEquals(1L, id1)
        assertEquals(2L, id2)
    }

    @Test
    fun record_persistsPayloadAndMeta() {
        val ts = freshSeries()
        val id = ts.record(1000L, "click", """{"x":1}""", meta = "screen-a")

        val entry = ts.byType("click").single()
        assertEquals("click", entry.type)
        assertEquals("""{"x":1}""", entry.payloadJson)
        assertEquals("screen-a", entry.meta)
        assertEquals(id, entry.id)
    }

    @Test
    fun record_nullMeta_isStoredAsNull() {
        val ts = freshSeries()
        val id = ts.record(1000L, "event", "{}", meta = null)
        assertNull(ts.byType("event").single().meta)
    }

    @Test
    fun byType_returnsEntriesSortedByTsAscending() {
        val ts = freshSeries()
        ts.record(3000L, "event", "later")
        ts.record(1000L, "event", "earlier")
        ts.record(2000L, "event", "middle")

        assertEquals(listOf("earlier", "middle", "later"), ts.byType("event").map { it.payloadJson })
    }

    @Test
    fun byType_filtersByType() {
        val ts = freshSeries()
        ts.record(1000L, "a", "payload-a")
        ts.record(1000L, "b", "payload-b")

        assertEquals(1, ts.byType("a").size)
        assertEquals("payload-a", ts.byType("a").single().payloadJson)
    }

    @Test
    fun range_returnsEntriesWithinInclusiveBounds() {
        val ts = freshSeries()
        ts.record(1000L, "m", "at-low")
        ts.record(2000L, "m", "in-low")
        ts.record(3000L, "m", "in-high")
        ts.record(4000L, "m", "at-high")
        ts.record(5000L, "m", "out")

        val rows = ts.range("m", 2000L, 4000L)
        assertEquals(listOf("in-low", "in-high", "at-high"), rows.map { it.payloadJson })
    }

    @Test
    fun purgeOlderThan_deletesColdDataAndReturnsRemainingCount() {
        val ts = freshSeries()
        ts.record(100L, "audit", "old-1")
        ts.record(200L, "audit", "old-2")
        ts.record(300L, "audit", "old-3")
        ts.record(900L, "audit", "keep")

        val remaining = ts.purgeOlderThan("audit", thresholdTs = 300L)
        // ts < 300 被删除（100, 200），ts >= 300 保留（300, 900）
        assertEquals(2, remaining)
        assertEquals(listOf("old-3", "keep"), ts.byType("audit").map { it.payloadJson })
    }

    @Test
    fun purgeOlderThan_doesNotAffectOtherTypes() {
        val ts = freshSeries()
        ts.record(100L, "audit", "old-audit")
        ts.record(100L, "trace", "old-trace")

        ts.purgeOlderThan("audit", thresholdTs = 500L)
        assertEquals(1, ts.byType("trace").size)
    }

    @Test
    fun unknownType_queriesReturnEmpty() {
        val ts = freshSeries()
        assertEquals(0, ts.byType("missing").size)
        assertEquals(0, ts.range("missing", 0L, 9999L).size)
    }
}
