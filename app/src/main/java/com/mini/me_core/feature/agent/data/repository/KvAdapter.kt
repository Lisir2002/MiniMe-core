package com.mini.me_core.feature.agent.data.repository

import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.agent.domain.mcp.server.KvPort
import javax.inject.Inject

class KvAdapter @Inject constructor(private val kv: KVStore) : KvPort {
    override suspend fun getInt(namespace: String, key: String): Long? = kv.getInt(namespace, key)
    override suspend fun getBool(namespace: String, key: String): Boolean? = kv.getBool(namespace, key)
    override suspend fun getString(namespace: String, key: String): String? = kv.getString(namespace, key)
    override suspend fun putString(namespace: String, key: String, value: String) { kv.putString(namespace, key, value) }
    override suspend fun putInt(namespace: String, key: String, value: Long) { kv.putInt(namespace, key, value) }
    override suspend fun putBool(namespace: String, key: String, value: Boolean) { kv.putBool(namespace, key, value) }
}
