package com.mini.me_core.feature.terminal.domain

import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** 一条 SSH 密钥元数据（私钥内容不落明文，仅记录元信息）。 */
@Serializable
data class SshKeyEntry(
    val id: String,
    val name: String,
    val type: String, // Ed25519 / RSA
    val publicKey: String,
    val createdAt: Long,
    val isDefault: Boolean = false,
)

private const val NS = "ssh_keys"
private const val KEY_LIST = "key_list"

/**
 * F3.7 SSH 密钥存储：元数据持久化到 KVStore。私钥本体存容器 ~/.ssh，不明文回显。
 */
@Singleton
class SshKeyStore @Inject constructor(
    private val kv: KVStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val keys: Flow<List<SshKeyEntry>> =
        kv.observeString(NS, KEY_LIST).map { raw ->
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<SshKeyEntry>>(raw) }.getOrDefault(emptyList())
        }

    suspend fun list(): List<SshKeyEntry> =
        kv.getString(NS, KEY_LIST)?.let {
            runCatching { json.decodeFromString<List<SshKeyEntry>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()

    suspend fun add(entry: SshKeyEntry) {
        val cur = list()
        val next = if (entry.isDefault) {
            (listOf(entry) + cur.map { it.copy(isDefault = false) })
        } else cur + entry
        kv.putString(NS, KEY_LIST, json.encodeToString(next))
    }

    suspend fun delete(id: String) {
        kv.putString(NS, KEY_LIST, json.encodeToString(list().filterNot { it.id == id }))
    }

    suspend fun rename(id: String, newName: String) {
        kv.putString(NS, KEY_LIST, json.encodeToString(list().map {
            if (it.id == id) it.copy(name = newName) else it
        }))
    }

    suspend fun setDefault(id: String) {
        kv.putString(NS, KEY_LIST, json.encodeToString(list().map {
            it.copy(isDefault = it.id == id)
        }))
    }
}
