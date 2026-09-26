package com.mini.me_core.feature.workspace.domain.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.feature.workspace.data.local.entity.RemoteAuditLogEntity
import com.mini.me_core.feature.workspace.domain.RemoteAuditAction
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * 远程审计日志仓储（纯 V2，经 [V2WorkspaceRepository] workspace 域 SQLDelight 门面）。
 *
 * 对调用方（SkillExecutor / SettingsViewModel /
 * SecuritySettingsViewModel / RemoteAuditLogsScreen / AuditPurgeWorker）签名完全不变。
 */
@Singleton
class RemoteAuditLogRepository @Inject constructor(
    private val v2Workspace: V2WorkspaceRepository,
) {
    companion object {
        private const val TAG = "RemoteAuditLogRepo"
        private const val MAX_MESSAGE_LENGTH = 500
        private const val RETENTION_MAX_COUNT = 10_000L
        private const val RETENTION_DAYS = 90L

        // 操作审计页面分类 Tab 键（表现层与 Repository 共用）。
        const val TAB_ALL = "ALL"
        const val TAB_CONNECT = "CONNECT"
        const val TAB_CREDENTIAL = "CREDENTIAL"
        const val TAB_BACKUP = "BACKUP"
        const val TAB_SECURITY = "SECURITY"
        const val TAB_SKILL = "SKILL"
    }

    /**
     * 主写入口：截断 message ≤500 字符，取当前时间。
     * 所有 audit 写入都走此方法，确保格式统一。
     */
    suspend fun append(
        category: String,
        action: String,
        success: Boolean,
        message: String? = null,
        connectionId: String? = null,
        connectionName: String? = null,
        remoteHost: String? = null,
        sourceIp: String? = null,
        createdAtMs: Long = Instant.now().toEpochMilli()
    ): Long = withContext(Dispatchers.IO) {
        val truncated = message?.take(MAX_MESSAGE_LENGTH)
        try {
            v2Workspace.insertAuditLog(
                category = category,
                action = action,
                connectionId = connectionId,
                connectionName = connectionName,
                remoteHost = remoteHost,
                success = if (success) 1L else 0L,
                message = truncated,
                sourceIp = sourceIp,
                createdAt = createdAtMs,
            )
            val count = v2Workspace.countAuditLogs()
            if (count % 50L == 0L) enforceRetentionV2()
            count
        } catch (e: Exception) {
            FileLogger.w(TAG, "写入审计日志失败", e)
            -1L
        }
    }

    suspend fun pageDesc(page: Int, pageSize: Int = 50): List<RemoteAuditLogEntity> =
        withContext(Dispatchers.IO) {
            v2Workspace.pageAuditLogs(offset = page * pageSize.toLong(), limit = pageSize.toLong())
                .map { it.toEntity() }
        }

    // ============== 操作审计页面重构（MiniMe）==============

    /** 总事件数。 */
    suspend fun countAll(): Int = withContext(Dispatchers.IO) {
        v2Workspace.countAuditLogs().toInt()
    }

    /** 失败事件数（success=false）。 */
    suspend fun countFailed(): Int = withContext(Dispatchers.IO) {
        v2Workspace.countAuditLogsFailed().toInt()
    }

    /** [timestampMs]（今天 0 点）之后的事件数。 */
    suspend fun countSince(timestampMs: Long): Int = withContext(Dispatchers.IO) {
        v2Workspace.countAuditLogsSince(timestampMs).toInt()
    }

    /**
     * 按分类 Tab 分页。[category] 为 null 或 [TAB_ALL] 时返回全部；
     * SYNC / RECONNECT_FAIL 归入「连接」；技能按动作集合过滤（历史上技能事件以 SECURITY 分类写入）。
     */
    suspend fun pageDescByCategory(
        category: String?,
        page: Int,
        pageSize: Int = 50,
    ): List<RemoteAuditLogEntity> = withContext(Dispatchers.IO) {
        val offset = page * pageSize.toLong()
        val limit = pageSize.toLong()
        val rows = when (category) {
            TAB_CONNECT -> v2Workspace.pageAuditLogsByCategories(
                listOf(RemoteAuditCategory.CONNECT, RemoteAuditCategory.SYNC, RemoteAuditCategory.RECONNECT_FAIL),
                offset, limit,
            )
            TAB_CREDENTIAL -> v2Workspace.pageAuditLogsByCategories(
                listOf(RemoteAuditCategory.CREDENTIAL), offset, limit,
            )
            TAB_BACKUP -> v2Workspace.pageAuditLogsByCategories(
                listOf(RemoteAuditCategory.BACKUP), offset, limit,
            )
            TAB_SECURITY -> v2Workspace.pageAuditLogsByCategories(
                listOf(RemoteAuditCategory.SECURITY), offset, limit,
            )
            TAB_SKILL -> v2Workspace.pageAuditLogsByActions(
                listOf(RemoteAuditAction.SKILL_EXEC_OK, RemoteAuditAction.SKILL_EXEC_FAIL),
                offset, limit,
            )
            else -> v2Workspace.pageAuditLogs(offset, limit)
        }
        rows.map { it.toEntity() }
    }

    /**
     * 全局搜索（忽略分类 Tab）：在连接名 / 主机 / 消息 / 原始动作上做 LIKE 匹配，降序分页。
     * 中文动作名匹配由表现层配合 AuditActionMapper 二次过滤。
     */
    suspend fun search(query: String, page: Int, pageSize: Int = 50): List<RemoteAuditLogEntity> =
        withContext(Dispatchers.IO) {
            val kw = "%${query.trim()}%"
            v2Workspace.searchAuditLogs(kw, page * pageSize.toLong(), pageSize.toLong())
                .map { it.toEntity() }
        }

    /** 清空全部审计日志，返回删除条数。 */
    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        v2Workspace.deleteAllAuditLogs()
    }

    suspend fun listByConnection(connectionId: String, limit: Int = 200): List<RemoteAuditLogEntity> =
        withContext(Dispatchers.IO) {
            v2Workspace.pageAuditLogsByConnection(connectionId, limit.toLong()).map { it.toEntity() }
        }

    suspend fun filter(
        categories: List<String> = emptyList(),
        onlyFailures: Boolean = false,
        sinceMs: Long = 0L,
        limit: Int = 500
    ): List<RemoteAuditLogEntity> = withContext(Dispatchers.IO) {
        val cats = if (categories.isEmpty()) {
            v2Workspace.listAuditCategories().ifEmpty { listOf("") }
        } else {
            categories
        }
        val successes = if (onlyFailures) listOf(false) else listOf(true, false)
        v2Workspace.filterAuditLogs(cats, successes, sinceMs, limit.toLong()).map { it.toEntity() }
    }

    /**
     * 清理保留期外的日志。
     * @return 实际删除条数。
     */
    suspend fun purgeBefore(dateMs: Long): Int = withContext(Dispatchers.IO) {
        val before = v2Workspace.countAuditLogs()
        v2Workspace.deleteAuditLogsOlderThan(dateMs)
        (before - v2Workspace.countAuditLogs()).toInt().coerceAtLeast(0)
    }

    /**
     * 导出为 JSON 字符串（脱敏版本，不含 password/passphrase 等敏感字段）。
     */
    suspend fun exportToJson(
        filterCategories: List<String>? = null,
        sinceMs: Long = 0L
    ): String = withContext(Dispatchers.IO) {
        val logs = if (filterCategories != null) {
            filter(categories = filterCategories, sinceMs = sinceMs, limit = 5000)
        } else {
            pageDesc(page = 0, pageSize = 5000)
        }
        val json = Json { prettyPrint = true }
        json.encodeToString(logs.map { log ->
            mapOf(
                "id" to log.id,
                "category" to log.category,
                "action" to log.action,
                "connectionId" to log.connectionId,
                "connectionName" to log.connectionName,
                "remoteHost" to log.remoteHost,
                "success" to log.success,
                "message" to log.message,
                "createdAt" to log.createdAt
            )
        })
    }

    /** 获取当前日志总数。 */
    suspend fun count(): Long = withContext(Dispatchers.IO) {
        v2Workspace.countAuditLogs()
    }

    // ============== 保留策略 ==============

    /**
     * 检查是否达到保留上限，是则截断。
     * 触发条件：10000 条 或 90 天，先到先截断。
     */
    suspend fun enforceRetentionIfNeeded() {
        val currentCount = v2Workspace.countAuditLogs()
        if (currentCount > RETENTION_MAX_COUNT) {
            val cutoffMs = Instant.now().minusSeconds(RETENTION_DAYS * 86400L).toEpochMilli()
            val before = v2Workspace.countAuditLogs()
            v2Workspace.deleteAuditLogsOlderThan(cutoffMs)
            val deleted = (before - v2Workspace.countAuditLogs()).toInt().coerceAtLeast(0)
            FileLogger.i(TAG, "审计日志保留清理：删除 $deleted 条（保留 ${RETENTION_MAX_COUNT} 条 / ${RETENTION_DAYS} 天）")
        }
    }

    /** V2 专用保留清理（append 内部用，避免重复查 count）。 */
    private suspend fun enforceRetentionV2() {
        val currentCount = v2Workspace.countAuditLogs()
        if (currentCount > RETENTION_MAX_COUNT) {
            val cutoffMs = Instant.now().minusSeconds(RETENTION_DAYS * 86400L).toEpochMilli()
            val before = currentCount
            v2Workspace.deleteAuditLogsOlderThan(cutoffMs)
            FileLogger.i(TAG, "审计日志保留清理(V2)：删除 ${before - v2Workspace.countAuditLogs()} 条")
        }
    }

    private fun com.mini.mecore.datalayer.sqldelight.workspace.Remote_audit_logs.toEntity() = RemoteAuditLogEntity(
        id = id,
        category = category,
        action = action,
        connectionId = connection_id,
        connectionName = connection_name,
        remoteHost = remote_host,
        success = success == 1L,
        message = message,
        sourceIp = source_ip,
        createdAt = created_at,
    )
}
