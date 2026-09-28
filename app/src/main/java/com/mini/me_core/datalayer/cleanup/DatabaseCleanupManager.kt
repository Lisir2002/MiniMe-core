package com.mini.me_core.datalayer.cleanup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.LibName

/** 单表清理统计。 */
data class TableCleanupStats(
    val tableName: String,
    val rowsDeleted: Long,
    val spaceReclaimedBytes: Long = 0,
)

/** 清理结果。 */
data class CleanupResult(
    val totalRowsDeleted: Long,
    val spaceReclaimedBytes: Long,
    val perTableStats: List<TableCleanupStats>,
    val durationMs: Long,
)

/** 清理预估。 */
data class CleanupEstimate(
    val estimatedRows: Long,
    val estimatedSpaceBytes: Long,
    val perTableEstimates: List<TableCleanupStats>,
)

/**
 * 数据库清理管理器。
 *
 * 清理策略：
 * - 过期数据清理：按时间阈值删除已完成/已过期的记录
 * - 孤儿记录清理：删除引用了不存在 session_id 的子表记录
 * - VACUUM：回收已删除记录占用的磁盘空间
 *
 * 每条 DELETE 语句在 SQLite 中本身是原子的；跨表清理按库分组执行。
 */
class DatabaseCleanupManager(
    private val connectionPool: ConnectionPool,
) {

    companion object {
        private const val TAG = "DatabaseCleanupManager"
        private const val DAY_MS = 24L * 60 * 60 * 1000

        private const val WAKE_QUEUE_MAX_AGE_DAYS = 30L
        private const val TELEMETRY_MAX_AGE_DAYS = 90L
        private const val MODE_SWITCH_MAX_AGE_DAYS = 7L
        private const val QUEUE_DONE_MAX_AGE_DAYS = 7L
        private const val TS_STORE_MAX_AGE_DAYS = 30L
        private const val AUDIT_LOG_MAX_AGE_DAYS = 30L
    }

    /**
     * 清理所有数据库中的过期数据。
     * 各库独立执行，单库失败不影响其他库。
     */
    fun cleanupExpiredData(): CleanupResult {
        val startTime = System.currentTimeMillis()
        val allStats = mutableListOf<TableCleanupStats>()
        val now = System.currentTimeMillis()

        // agent 库
        try {
            val driver = connectionPool.driver(LibName.AGENT)
            allStats.add(deleteWhere(driver, "wake_queue",
                "status = 'CONSUMED' AND created_at_ms < $now - ${WAKE_QUEUE_MAX_AGE_DAYS * DAY_MS}"))
            allStats.add(deleteWhere(driver, "zth_telemetry_events",
                "created_at_ms < $now - ${TELEMETRY_MAX_AGE_DAYS * DAY_MS}"))
            allStats.add(deleteWhere(driver, "mode_switch_history",
                "timestamp_ms < $now - ${MODE_SWITCH_MAX_AGE_DAYS * DAY_MS}"))
        } catch (e: Exception) {
            FileLogger.e(TAG, "agent 库过期清理失败", e)
        }

        // infra 库
        try {
            val driver = connectionPool.driver(LibName.INFRA)
            allStats.add(deleteWhere(driver, "queue_store",
                "status IN ('done', 'failed') AND created_at < $now - ${QUEUE_DONE_MAX_AGE_DAYS * DAY_MS}"))
            allStats.add(deleteWhere(driver, "ts_store",
                "ts < $now - ${TS_STORE_MAX_AGE_DAYS * DAY_MS}"))
        } catch (e: Exception) {
            FileLogger.e(TAG, "infra 库过期清理失败", e)
        }

        // workspace 库
        try {
            val driver = connectionPool.driver(LibName.WORKSPACE)
            allStats.add(deleteWhere(driver, "remote_audit_logs",
                "created_at < $now - ${AUDIT_LOG_MAX_AGE_DAYS * DAY_MS}"))
        } catch (e: Exception) {
            FileLogger.e(TAG, "workspace 库过期清理失败", e)
        }

        val totalRows = allStats.sumOf { it.rowsDeleted }
        val duration = System.currentTimeMillis() - startTime
        FileLogger.i(TAG, "过期清理完成: 删除 $totalRows 行, 耗时 ${duration}ms")
        return CleanupResult(totalRows, 0, allStats, duration)
    }

    /**
     * 清理孤儿记录：删除 session_id 不在 agent_session 中的子表记录。
     */
    fun cleanupOrphanedRecords(): CleanupResult {
        val startTime = System.currentTimeMillis()
        val allStats = mutableListOf<TableCleanupStats>()

        val childTables = listOf(
            "agent_message",
            "agent_checkpoint",
            "todo_items",
            "session_checkpoints",
            "file_edit_hunks",
            "mode_switch_history",
            "agent_goals",
            "agent_plans",
            "agent_jobs",
            "agent_schedules",
            "agent_trajectories",
            "agent_playbook_runs",
            "skill_conversation_state",
            "wake_queue",
            "zth_user_confirmed_sentinels",
            "zth_hard_constraint_delete_audits",
            "zth_l0_soft_compact_restore_logs",
        )

        try {
            val driver = connectionPool.driver(LibName.AGENT)
            for (table in childTables) {
                try {
                    val rows = driver.execute(
                        null,
                        "DELETE FROM \"$table\" WHERE session_id NOT IN (SELECT id FROM agent_session);",
                        0,
                        null,
                    ).value
                    if (rows > 0) {
                        FileLogger.i(TAG, "清理孤儿记录: $table 删除 $rows 行")
                    }
                    allStats.add(TableCleanupStats(table, rows.toLong()))
                } catch (e: Exception) {
                    FileLogger.w(TAG, "清理孤儿记录失败: $table", e)
                }
            }
            // checkpoint_file_snapshots 通过 checkpoint_id 关联
            try {
                val rows = driver.execute(
                    null,
                    "DELETE FROM checkpoint_file_snapshots WHERE checkpoint_id NOT IN (SELECT id FROM session_checkpoints);",
                    0,
                    null,
                ).value
                if (rows > 0) {
                    FileLogger.i(TAG, "清理孤儿记录: checkpoint_file_snapshots 删除 $rows 行")
                }
                allStats.add(TableCleanupStats("checkpoint_file_snapshots", rows.toLong()))
            } catch (e: Exception) {
                FileLogger.w(TAG, "清理 checkpoint_file_snapshots 失败", e)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "孤儿记录清理失败", e)
        }

        val totalRows = allStats.sumOf { it.rowsDeleted }
        val duration = System.currentTimeMillis() - startTime
        FileLogger.i(TAG, "孤儿记录清理完成: 删除 $totalRows 行, 耗时 ${duration}ms")
        return CleanupResult(totalRows, 0, allStats, duration)
    }

    /**
     * 执行 VACUUM 回收磁盘空间。
     * 返回执行前后页面大小差值（回收的字节数）。
     */
    fun vacuumDatabase(dbId: String): Long {
        val lib = LibName.entries.find { it.name.equals(dbId, ignoreCase = true) }
            ?: throw IllegalArgumentException("未知数据库: $dbId")
        val driver = connectionPool.driver(lib)

        val pageSize = queryLong(driver, "PRAGMA page_size;")
        val beforePages = queryLong(driver, "PRAGMA page_count;")

        FileLogger.i(TAG, "执行 VACUUM: $dbId, 前大小=${beforePages * pageSize}")
        driver.execute(null, "VACUUM;", 0, null)

        val afterPages = queryLong(driver, "PRAGMA page_count;")
        val reclaimed = (beforePages - afterPages) * pageSize
        FileLogger.i(TAG, "VACUUM 完成: $dbId, 回收=${reclaimed} bytes")
        return reclaimed
    }

    /**
     * 预估可清理的数据量（不实际删除）。
     */
    fun getCleanupEstimate(): CleanupEstimate {
        val estimates = mutableListOf<TableCleanupStats>()
        val now = System.currentTimeMillis()

        try {
            val driver = connectionPool.driver(LibName.AGENT)
            estimates.add(TableCleanupStats("wake_queue",
                queryLong(driver, "SELECT COUNT(*) FROM wake_queue WHERE status='CONSUMED' AND created_at_ms < $now - ${WAKE_QUEUE_MAX_AGE_DAYS * DAY_MS};")))
            estimates.add(TableCleanupStats("zth_telemetry_events",
                queryLong(driver, "SELECT COUNT(*) FROM zth_telemetry_events WHERE created_at_ms < $now - ${TELEMETRY_MAX_AGE_DAYS * DAY_MS};")))
            estimates.add(TableCleanupStats("mode_switch_history",
                queryLong(driver, "SELECT COUNT(*) FROM mode_switch_history WHERE timestamp_ms < $now - ${MODE_SWITCH_MAX_AGE_DAYS * DAY_MS};")))
        } catch (e: Exception) {
            FileLogger.w(TAG, "agent 库预估失败", e)
        }

        try {
            val driver = connectionPool.driver(LibName.INFRA)
            estimates.add(TableCleanupStats("queue_store",
                queryLong(driver, "SELECT COUNT(*) FROM queue_store WHERE status IN ('done','failed') AND created_at < $now - ${QUEUE_DONE_MAX_AGE_DAYS * DAY_MS};")))
            estimates.add(TableCleanupStats("ts_store",
                queryLong(driver, "SELECT COUNT(*) FROM ts_store WHERE ts < $now - ${TS_STORE_MAX_AGE_DAYS * DAY_MS};")))
        } catch (e: Exception) {
            FileLogger.w(TAG, "infra 库预估失败", e)
        }

        try {
            val driver = connectionPool.driver(LibName.WORKSPACE)
            estimates.add(TableCleanupStats("remote_audit_logs",
                queryLong(driver, "SELECT COUNT(*) FROM remote_audit_logs WHERE created_at < $now - ${AUDIT_LOG_MAX_AGE_DAYS * DAY_MS};")))
        } catch (e: Exception) {
            FileLogger.w(TAG, "workspace 库预估失败", e)
        }

        val totalRows = estimates.sumOf { it.rowsDeleted }
        val estimatedSpace = totalRows * 200L
        return CleanupEstimate(totalRows, estimatedSpace, estimates)
    }

    // ── 内部工具 ──────────────────────────────────────────────────────

    /** 执行 DELETE，返回删除行数。 */
    private fun deleteWhere(driver: SqlDriver, table: String, whereClause: String): TableCleanupStats {
        val rows = driver.execute(null, "DELETE FROM \"$table\" WHERE $whereClause;", 0, null).value
        FileLogger.v(TAG, "DELETE $table: $rows rows")
        return TableCleanupStats(table, rows.toLong())
    }

    /** 执行返回单个 Long 的查询。 */
    private fun queryLong(driver: SqlDriver, sql: String): Long {
        return driver.executeQuery(
            null,
            sql,
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0,
            null,
        ).value
    }
}
