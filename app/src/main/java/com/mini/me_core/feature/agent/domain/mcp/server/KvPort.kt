package com.mini.me_core.feature.agent.domain.mcp.server

/** 键值存储端口（domain 定义，KVStore 实现）。 */
interface KvPort {
    suspend fun getInt(namespace: String, key: String): Long?
    suspend fun getBool(namespace: String, key: String): Boolean?
    suspend fun getString(namespace: String, key: String): String?
    suspend fun putString(namespace: String, key: String, value: String)
    suspend fun putInt(namespace: String, key: String, value: Long)
    suspend fun putBool(namespace: String, key: String, value: Boolean)
}
