package com.mini.me_core.feature.settings.presentation.component

import com.mini.me_core.core.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运行日志查看器：日志行分组与等级折叠的纯逻辑测试。
 */
class LogLineItemTest {

    private fun header(level: LogLevel, tag: String, msg: String) =
        "2026-09-23 09:23:45.123 ${level.name} [$tag] $msg"

    @Test
    fun buildLogEntries_groupsHeaderWithFollowingStackLines() {
        val lines = listOf(
            header(LogLevel.ERROR, "Mcp", "boom"),
            "\tat java.lang.Thread.dump(Native Method)",
            "\tat com.mini.Main.run(Main.kt:1)",
            header(LogLevel.INFO, "App", "started"),
        )
        val items = buildLogEntries(lines)

        assertEquals(2, items.size)
        val e0 = items[0] as LogListItem.Entry
        assertEquals(LogLevel.ERROR, e0.parsed.level)
        assertEquals(2, e0.stack.size)
        assertEquals("App", (items[1] as LogListItem.Entry).parsed.tag)
    }

    @Test
    fun buildLogEntries_orphanLinesBecomeLoose() {
        val lines = listOf(
            "--- 日志文件超过 5MB 已重置 ---",
            header(LogLevel.INFO, "App", "started"),
        )
        val items = buildLogEntries(lines)
        assertTrue(items[0] is LogListItem.Loose)
        assertTrue(items[1] is LogListItem.Entry)
    }

    @Test
    fun applyCollapse_foldsConsecutiveEntriesOfCollapsedLevel() {
        val lines = listOf(
            header(LogLevel.ERROR, "A", "e1"),
            header(LogLevel.ERROR, "B", "e2"),
            header(LogLevel.WARN, "C", "w1"),
            header(LogLevel.ERROR, "D", "e3"),
        )
        val items = buildLogEntries(lines)
        val collapsed = applyCollapse(items, setOf(LogLevel.ERROR))

        // 前两个 ERROR 折成一行；WARN 保留；最后一个 ERROR 折成一行
        assertEquals(3, collapsed.size)
        val c0 = collapsed[0] as LogListItem.Collapsed
        assertEquals(LogLevel.ERROR, c0.level)
        assertEquals(2, c0.count)
        assertTrue(collapsed[1] is LogListItem.Entry) // WARN
        val c2 = collapsed[2] as LogListItem.Collapsed
        assertEquals(1, c2.count)
    }

    @Test
    fun applyCollapse_noCollapsedLevels_passthrough() {
        val lines = listOf(header(LogLevel.INFO, "A", "x"))
        val items = buildLogEntries(lines)
        assertEquals(items, applyCollapse(items, emptySet()))
    }

    @Test
    fun applyCollapse_stackLinesFollowHeaderIntoFold() {
        val lines = listOf(
            header(LogLevel.ERROR, "A", "e1"),
            "\tat stack.frame",
            header(LogLevel.ERROR, "B", "e2"),
        )
        val items = buildLogEntries(lines)
        val collapsed = applyCollapse(items, setOf(LogLevel.ERROR))
        assertEquals(1, collapsed.size)
        assertEquals(2, (collapsed[0] as LogListItem.Collapsed).count)
    }
}
