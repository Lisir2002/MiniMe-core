package com.mini.me_core.feature.settings.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsSearchHistoryManager.buildNewHistory] 纯函数单测。
 *
 * 验证搜索历史列表的增删/去重/截断逻辑：
 * - 新增搜索词置顶
 * - 重复搜索词去重并移到最前（忽略大小写）
 * - 超过 10 条截断尾部
 * - 空白词不改变列表
 */
class SettingsSearchHistoryManagerTest {

    @Test
    fun `新增搜索词置顶`() {
        val result = SettingsSearchHistoryManager.buildNewHistory(emptyList(), "model")
        assertEquals(listOf("model"), result)
    }

    @Test
    fun `多条搜索词按时间倒序`() {
        var list = SettingsSearchHistoryManager.buildNewHistory(emptyList(), "model")
        list = SettingsSearchHistoryManager.buildNewHistory(list, "proxy")
        assertEquals(listOf("proxy", "model"), list)
    }

    @Test
    fun `重复搜索词去重并移到最前`() {
        var list = SettingsSearchHistoryManager.buildNewHistory(emptyList(), "model")
        list = SettingsSearchHistoryManager.buildNewHistory(list, "proxy")
        list = SettingsSearchHistoryManager.buildNewHistory(list, "model")
        assertEquals(listOf("model", "proxy"), list)
    }

    @Test
    fun `超过最大条数截断尾部`() {
        var list = emptyList<String>()
        for (i in 1..12) {
            list = SettingsSearchHistoryManager.buildNewHistory(list, "keyword$i")
        }
        assertEquals(SettingsSearchHistoryManager.MAX_HISTORY_SIZE, list.size)
        assertEquals("keyword12", list[0])
        assertEquals("keyword3", list.last())
    }

    @Test
    fun `空白词不改变列表`() {
        val initial = listOf("model", "proxy")
        assertEquals(initial, SettingsSearchHistoryManager.buildNewHistory(initial, ""))
        assertEquals(initial, SettingsSearchHistoryManager.buildNewHistory(initial, "   "))
    }

    @Test
    fun `搜索词忽略大小写去重`() {
        var list = SettingsSearchHistoryManager.buildNewHistory(emptyList(), "Model")
        list = SettingsSearchHistoryManager.buildNewHistory(list, "MODEL")
        assertEquals(1, list.size)
        assertEquals("MODEL", list[0])
    }

    @Test
    fun `空历史加词返回单元素`() {
        val result = SettingsSearchHistoryManager.buildNewHistory(emptyList(), "theme")
        assertEquals(listOf("theme"), result)
    }
}
