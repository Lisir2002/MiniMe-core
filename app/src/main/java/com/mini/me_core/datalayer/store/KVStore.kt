package com.mini.me_core.datalayer.store

import app.cash.sqldelight.coroutines.asFlow
import com.mini.mecore.datalayer.sqldelight.InfraDb
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 一等 KVStore（设计 §6.1）：替代散落的 DataStore。
 * 标量按类型拆列（string/int/bool/json），写入层宽松、读取层解析。
 */
data class KvEntry(
    val namespace: String,
    val key: String,
    val type: String,
    val stringVal: String?,
    val intVal: Long?,
    val boolVal: Long?,
    val jsonVal: String?,
    val updatedAt: Long,
)

class KVStore(private val db: InfraDb) {

    private companion object {
        const val TAG = "KVStore"
    }

    private val queries get() = db.kvQueries

    fun putString(namespace: String, key: String, value: String) =
        queries.upsertKv(namespace, key, "string", value, null, null, null, now())

    fun putInt(namespace: String, key: String, value: Long) =
        queries.upsertKv(namespace, key, "int", null, value, null, null, now())

    fun putBool(namespace: String, key: String, value: Boolean) =
        queries.upsertKv(namespace, key, "bool", null, null, if (value) 1L else 0L, null, now())

    fun putJson(namespace: String, key: String, value: String) =
        queries.upsertKv(namespace, key, "json", null, null, null, value, now())

    fun get(namespace: String, key: String): KvEntry? =
        queries.selectKv(namespace, key).executeAsOneOrNull()?.toEntry()

    fun getAll(namespace: String): List<KvEntry> =
        queries.selectKvByNamespace(namespace).executeAsList().map { it.toEntry() }

    /**
     * 类型化 get 便捷方法。
     *
     * ⚠️ **类型校验（审计 H7）**：读取时必须比对存储的 `type` 列，类型不符即返回 null 并告警。
     * 过去 `putString` 后 `getInt` 会静默返回 null——历史上「设置项重启丢失」正是这一模式
     * （写入的类型与读取的类型不一致，调用方误以为没值）。类型守卫让这种不一致显式暴露，
     * 而不是表现得像「键不存在」。
     */
    fun getString(namespace: String, key: String): String? = typedGet(namespace, key, "string")?.stringVal
    fun getInt(namespace: String, key: String): Long? = typedGet(namespace, key, "int")?.intVal
    fun getBool(namespace: String, key: String): Boolean? =
        typedGet(namespace, key, "bool")?.boolVal?.let { it != 0L }
    fun getJson(namespace: String, key: String): String? = typedGet(namespace, key, "json")?.jsonVal

    /** 类型守卫：类型匹配返回条目，否则记 warn 并返回 null。 */
    private fun typedGet(namespace: String, key: String, expected: String): KvEntry? {
        val e = get(namespace, key) ?: return null
        if (e.type != expected) {
            FileLogger.w(
                TAG,
                "KV 类型不匹配：key=$namespace:$key 实际存为 ${e.type}，按 $expected 读取，返回 null",
            )
            return null
        }
        return e
    }

    /** 响应式观察（替代 DataStore.data）。 */
    fun observe(namespace: String, key: String): Flow<KvEntry?> =
        queries.selectKv(namespace, key)
            .asFlow()
            .map { it.executeAsOneOrNull()?.toEntry() }
            // H7：查询在 IO 线程执行，避免 UI 收集时在主线程读加密库（与 repository 内 mapToList(IO) 一致）。
            .flowOn(Dispatchers.IO)

    /** 类型化 observe 便捷方法：直接 Flow<String?> / Flow<Long?> / Flow<Boolean?>。 */
    fun observeString(namespace: String, key: String): Flow<String?> =
        observe(namespace, key).map { it?.stringVal }
    fun observeInt(namespace: String, key: String): Flow<Long?> =
        observe(namespace, key).map { it?.intVal }
    fun observeBool(namespace: String, key: String): Flow<Boolean?> =
        observe(namespace, key).map { it?.boolVal?.let { v -> v != 0L } }

    fun delete(namespace: String, key: String) =
        queries.tombstoneKv(now(), namespace, key)

    private fun now() = System.currentTimeMillis()

    private fun com.mini.mecore.datalayer.sqldelight.infra.Kv_store.toEntry() = KvEntry(
        namespace = namespace,
        key = key,
        type = type,
        stringVal = string_val,
        intVal = int_val,
        boolVal = bool_val,
        jsonVal = json_val,
        updatedAt = updated_at,
    )
}
