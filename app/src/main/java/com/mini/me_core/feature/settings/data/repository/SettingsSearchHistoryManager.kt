package com.mini.me_core.feature.settings.data.repository

import android.util.Log
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置页搜索历史管理器。
 *
 * 用 KVStore 持久化最近搜索词（namespace="settings_search", key="history"）。
 * 最多保留 [MAX_HISTORY_SIZE] 条，新搜索词置顶、去重。
 *
 * 设计参考 ThemeSettingsManager：KVStore 注入 + StateFlow 暴露 + 启动时加载。
 * 纯内存 StateFlow，写操作同步更新 flow，不阻塞 UI。
 */
@Singleton
class SettingsSearchHistoryManager @Inject constructor(
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "SearchHistory"
        private const val NAMESPACE = "settings_search"
        private const val KEY_HISTORY = "history"

        /** 最多保留的历史条数 */
        const val MAX_HISTORY_SIZE = 10

        /**
         * 纯函数：根据当前历史列表和新搜索词，计算新的历史列表。
         * - 空白词返回原列表
         * - 去重（忽略大小写）后新词置顶
         * - 超过 [MAX_HISTORY_SIZE] 截断尾部
         */
        fun buildNewHistory(current: List<String>, newQuery: String, maxSize: Int = MAX_HISTORY_SIZE): List<String> {
            val trimmed = newQuery.trim()
            if (trimmed.isEmpty()) return current
            val mutable = current.toMutableList()
            mutable.removeAll { it.equals(trimmed, ignoreCase = true) }
            mutable.add(0, trimmed)
            return if (mutable.size > maxSize) mutable.subList(0, maxSize).toList() else mutable.toList()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow(loadFromKv())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /**
     * 记录一次搜索词。
     * - 空白词不记录
     * - 已存在的词移到最前（去重）
     * - 超过 [MAX_HISTORY_SIZE] 截断尾部
     */
    suspend fun recordSearch(query: String) {
        val newList = buildNewHistory(_history.value, query)
        if (newList == _history.value) return
        persist(newList)
    }

    /** 清空全部搜索历史。 */
    suspend fun clearHistory() {
        persist(emptyList())
    }

    private fun loadFromKv(): List<String> {
        val raw = kv.getJson(NAMESPACE, KEY_HISTORY)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            @Suppress("USELESS_ELVIS")
            json.decodeFromString<SearchHistoryDto>(raw).items ?: emptyList()
        }.getOrElse {
            Log.w(TAG, "Failed to parse search history: ${it.message}")
            emptyList()
        }
    }

    private fun persist(items: List<String>) {
        kv.putJson(NAMESPACE, KEY_HISTORY, json.encodeToString(SearchHistoryDto(items)))
        _history.value = items
    }

    @Serializable
    private data class SearchHistoryDto(val items: List<String> = emptyList())
}
