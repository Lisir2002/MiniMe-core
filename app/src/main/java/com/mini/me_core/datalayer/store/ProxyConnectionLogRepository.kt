package com.mini.me_core.datalayer.store

import com.mini.mecore.datalayer.sqldelight.InfraDb
import javax.inject.Inject
import javax.inject.Singleton

/** 一条结束的代理连接审计记录。 */
data class ProxyConnectionEntry(
    val id: Long,
    val timestamp: Long,
    val host: String?,
    val ip: String?,
    val port: Long?,
    val protocol: String?,
    val upBytes: Long,
    val downBytes: Long,
    val durationMs: Long,
    val status: String?,
)

/**
 * P3-19：代理连接审计日志持久化（INFRA 库 proxy_connection_log 表）。
 * 批量插入、分页查询、按域名搜索、过期清理、全量导出。
 */
@Singleton
class ProxyConnectionLogRepository @Inject constructor(
    private val db: InfraDb,
) {
    private val q get() = db.proxy_connection_logQueries

    /** 批量写入一条结束连接（调用方攒一批后调一次，减少写库次数）。 */
    fun insertBatch(entries: List<ProxyConnectionEntry>) {
        if (entries.isEmpty()) return
        db.transaction {
            entries.forEach { e ->
                q.insertLog(
                    e.timestamp, e.host, e.ip, e.port, e.protocol,
                    e.upBytes, e.downBytes, e.durationMs, e.status
                )
            }
        }
    }

    fun recent(limit: Long = 100, offset: Long = 0): List<ProxyConnectionEntry> =
        q.selectRecent(limit, offset).executeAsList().map { it.toEntry() }

    fun search(hostQuery: String, limit: Long = 100, offset: Long = 0): List<ProxyConnectionEntry> =
        q.selectByHost("%$hostQuery%", limit, offset).executeAsList().map { it.toEntry() }

    fun all(): List<ProxyConnectionEntry> =
        q.selectAll().executeAsList().map { it.toEntry() }

    fun purgeOlderThan(threshold: Long) { q.deleteOlderThan(threshold) }

    private fun com.mini.mecore.datalayer.sqldelight.infra.Proxy_connection_log.toEntry() =
        ProxyConnectionEntry(
            id = id, timestamp = timestamp, host = host, ip = ip, port = port,
            protocol = protocol, upBytes = up_bytes, downBytes = down_bytes,
            durationMs = duration_ms, status = status
        )
}
