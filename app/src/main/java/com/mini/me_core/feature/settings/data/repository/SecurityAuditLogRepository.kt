package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.settings.domain.security.SecurityAuditEntry
import com.mini.me_core.feature.settings.domain.security.SecurityAuditLogCodec
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 敏感操作审计日志本地仓库（KVStore 单 key JSON）。
 *
 * 记录：数据库加密开关 / 密钥轮换 / 紧急解锁 / 生物识别变更 / 防截图变更。
 * 最多保留 [SecurityAuditEntry.MAX_ENTRIES] 条，按时间倒序。
 */
@Singleton
class SecurityAuditLogRepository @Inject constructor(
    private val kv: KVStore,
) {
    private companion object {
        const val NS = "settings"
        const val KEY = "security_audit_log"
        val TAG = "SecurityAuditRepo"
    }

    /** 观察审计日志（最新在前）。 */
    fun observeEntries(): Flow<List<SecurityAuditEntry>> =
        kv.observeString(NS, KEY).map { SecurityAuditLogCodec.decode(it) }

    /** 读取当前日志（同步快照）。 */
    suspend fun list(): List<SecurityAuditEntry> = withContext(Dispatchers.IO) {
        SecurityAuditLogCodec.decode(kv.getString(NS, KEY))
    }

    /** 追加一条记录。 */
    suspend fun append(action: String, success: Boolean, detail: String? = null) =
        withContext(Dispatchers.IO) {
            try {
                val current = SecurityAuditLogCodec.decode(kv.getString(NS, KEY))
                val entry = SecurityAuditEntry(
                    action = action,
                    success = success,
                    detail = detail,
                    atMs = System.currentTimeMillis(),
                )
                val updated = SecurityAuditLogCodec.append(current, entry)
                kv.putString(NS, KEY, SecurityAuditLogCodec.encode(updated))
            } catch (e: Exception) {
                FileLogger.w(TAG, "写入安全审计日志失败", e)
            }
        }

    /** 清空全部日志。 */
    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            kv.delete(NS, KEY)
        } catch (e: Exception) {
            FileLogger.w(TAG, "清空安全审计日志失败", e)
        }
    }
}
