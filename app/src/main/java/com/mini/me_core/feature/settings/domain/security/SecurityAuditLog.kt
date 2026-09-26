package com.mini.me_core.feature.settings.domain.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 敏感操作审计日志条目（安全设置页本地记录）。
 *
 * 与远程 [com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository]
 * 区别：本日志只记录本机安全开关的变更（加密开关 / 密钥轮换 / 紧急解锁 / 生物识别变更），
 * 数据量小，存储于 KVStore 的单个 JSON key 下，便于安全页内联展示与清空。
 */
@Serializable
data class SecurityAuditEntry(
    /** 稳定动作标识。 */
    val action: String,
    /** 是否成功。 */
    val success: Boolean,
    /** 人类可读的补充说明。 */
    val detail: String? = null,
    /** 发生时间（epoch millis）。 */
    val atMs: Long,
) {
    companion object {
        /** 记录的动作类型（稳定字符串，勿改）。 */
        const val ACTION_DB_ENCRYPTION = "DB_ENCRYPTION_TOGGLE"
        const val ACTION_KEY_ROTATE = "KEY_ROTATE"
        const val ACTION_EMERGENCY_UNLOCK = "EMERGENCY_UNLOCK"
        const val ACTION_BIOMETRIC = "BIOMETRIC_CHANGE"
        const val ACTION_SECURE_SCREEN = "SECURE_SCREEN_CHANGE"

        /** 最多保留条数。 */
        const val MAX_ENTRIES = 100
    }
}

/**
 * 审计日志的（反）序列化器：纯函数，可在 JVM 单测覆盖，不依赖 Android/KVStore。
 */
object SecurityAuditLogCodec {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun encode(entries: List<SecurityAuditEntry>): String =
        json.encodeToString(entries)

    fun decode(raw: String?): List<SecurityAuditEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SecurityAuditEntry>>(raw) }
            .getOrDefault(emptyList())
    }

    /**
     * 追加一条记录，按时间倒序（最新在前），并裁剪到 [SecurityAuditEntry.MAX_ENTRIES] 条。
     * 纯函数：输入旧列表 + 新条目，输出新列表。
     */
    fun append(oldEntries: List<SecurityAuditEntry>, newEntry: SecurityAuditEntry): List<SecurityAuditEntry> {
        val merged = (listOf(newEntry) + oldEntries)
            .sortedByDescending { it.atMs }
        return merged.take(SecurityAuditEntry.MAX_ENTRIES)
    }
}
