package com.mini.me_core.feature.settings.data.repository

import android.util.Log
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F5.3 设置变更历史与回滚（数据层）。
 *
 * 持久化在 KVStore（namespace="settings_history", key="changes"），与搜索历史一致，
 * 不引入新的数据库 schema。每条记录保存：时间、设置项名、key、值类型、旧值、新值、来源。
 *
 * 约束（对齐设计文档 F5.3）：
 * - 最多保留 [MAX_RECORDS] 条，且超过 [RETENTION_MS]（30 天）自动清理
 * - 回滚时把旧值写回 settings namespace，且回滚动作本身再记一条（来源 ROLLBACK）
 * - 旧值/新值统一序列化为字符串，回滚时按 [type] 还原成 bool/int/string/json 写回
 */
@Singleton
class SettingsChangeHistory @Inject constructor(
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "SettingsChangeHistory"
        private const val SETTINGS_NS = "settings"
        private const val HISTORY_NS = "settings_history"
        private const val HISTORY_KEY = "changes"

        /** 最多保留条数（F5.3：500 条）。 */
        const val MAX_RECORDS = 500

        /** 最长保留时间：30 天（毫秒）。 */
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    /** 变更来源。 */
    enum class Source {
        USER, IMPORT, ROLLBACK, DEFAULT;

        /** 用于 UI 展示的枚举名（非本地化，本地化在 strings.xml 做映射）。 */
        fun key(): String = name
    }

    /** 单条变更记录。值统一序列化为字符串，[type] 决定回滚时如何写回。 */
    @Serializable
    data class ChangeRecord(
        val id: Long,
        val timestamp: Long,
        val key: String,
        val displayName: String,
        /** string / int / bool / json */
        val type: String,
        val oldValue: String?,
        val newValue: String?,
        val source: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow(loadFromKv())
    val history: StateFlow<List<ChangeRecord>> = _history.asStateFlow()

    /**
     * 记录一次设置变更。
     * - 旧值 == 新值时不记录（避免无意义噪音）
     * - 记录后按 500 条 / 30 天裁剪
     */
    suspend fun recordChange(
        key: String,
        displayName: String,
        type: String,
        oldValue: String?,
        newValue: String?,
        source: Source = Source.USER,
    ) = withContext(Dispatchers.IO) {
        if (oldValue == newValue) return@withContext
        val record = ChangeRecord(
            id = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            key = key,
            displayName = displayName,
            type = type,
            oldValue = oldValue,
            newValue = newValue,
            source = source.key(),
        )
        val merged = (listOf(record) + _history.value).let { prune(it) }
        persist(merged)
    }

    /**
     * 回滚单条记录：把该记录的旧值写回 settings namespace。
     * 成功后再记一条 ROLLBACK 来源的变更（旧值=当前值，新值=被恢复的旧值）。
     * @return 是否成功写回
     */
    suspend fun rollback(record: ChangeRecord): Boolean = withContext(Dispatchers.IO) {
        val before = readCurrentRaw(record.key)
        val restored = runCatching { writeRaw(record.key, record.type, record.oldValue) }.isSuccess
        if (restored) {
            recordChangeInternal(
                key = record.key,
                displayName = record.displayName,
                type = record.type,
                oldValue = before,
                newValue = record.oldValue,
                source = Source.ROLLBACK,
            )
        }
        restored
    }

    /** 清空全部历史。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        persist(emptyList())
    }

    /** 读取某个 key 当前在 settings namespace 的序列化值（用于回滚前快照）。 */
    private fun readCurrentRaw(key: String): String? {
        val entry = kv.get(SETTINGS_NS, key) ?: return null
        return when (entry.type) {
            "bool" -> entry.boolVal?.let { if (it != 0L) "true" else "false" }
            "int" -> entry.intVal?.toString()
            "json" -> entry.jsonVal
            else -> entry.stringVal
        }
    }

    /** 按类型把序列化值写回 settings namespace。 */
    private fun writeRaw(key: String, type: String, raw: String?) {
        when (type) {
            "bool" -> kv.putBool(SETTINGS_NS, key, raw == "true")
            "int" -> kv.putInt(SETTINGS_NS, key, raw?.toLongOrNull() ?: 0L)
            "json" -> kv.putJson(SETTINGS_NS, key, raw ?: "{}")
            else -> kv.putString(SETTINGS_NS, key, raw ?: "")
        }
    }

    private fun recordChangeInternal(
        key: String,
        displayName: String,
        type: String,
        oldValue: String?,
        newValue: String?,
        source: Source,
    ) {
        if (oldValue == newValue) return
        val record = ChangeRecord(
            id = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            key = key,
            displayName = displayName,
            type = type,
            oldValue = oldValue,
            newValue = newValue,
            source = source.key(),
        )
        val merged = prune(listOf(record) + _history.value)
        persist(merged)
    }

    /** 裁剪：保留最近 MAX_RECORDS 条，且丢弃超过 30 天的记录。 */
    private fun prune(list: List<ChangeRecord>): List<ChangeRecord> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return list.filter { it.timestamp >= cutoff }
            .sortedByDescending { it.timestamp }
            .take(MAX_RECORDS)
    }

    private fun loadFromKv(): List<ChangeRecord> {
        val raw = kv.getJson(HISTORY_NS, HISTORY_KEY)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            @Suppress("USELESS_ELVIS")
            json.decodeFromString<HistoryDto>(raw).items ?: emptyList()
        }.getOrElse {
            Log.w(TAG, "Failed to parse change history: ${it.message}")
            emptyList()
        }
    }

    private fun persist(items: List<ChangeRecord>) {
        kv.putJson(HISTORY_NS, HISTORY_KEY, json.encodeToString(HistoryDto.serializer(), HistoryDto(items)))
        _history.value = items
    }

    @Serializable
    private data class HistoryDto(val items: List<ChangeRecord> = emptyList())
}
