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

        /**
         * 单表孤儿删除量占比上限（致命项 F2 的护栏）。
         *
         * 超过这个比例说明「父表异常」而非「确有少量孤儿」——典型即自愈半途失败导致
         * `agent_session` 少行。此时继续执行等于批量清空子表，必须停下等人工确认。
         */
        private const val MAX_ORPHAN_DELETE_RATIO = 0.2
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
     *
     * **两道护栏（致命项 F2 修复，不得移除）**：
     *  1. **父表非空断言**：`agent_session` 为 0 行时立即中止。否则
     *     `session_id NOT IN (SELECT id FROM agent_session)` 对空集恒真 → 17 张子表被**全部删光**，
     *     而日志看起来只是「清理孤儿 N 行」。
     *  2. **单表删除占比上限**（[MAX_ORPHAN_DELETE_RATIO]）：孤儿数超过该表总行数的阈值时跳过该表并告警，
     *     宁可留着孤儿，也不能把「父表异常」误判成「数据该删」。
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

            // 护栏 1：父表必须非空
            val sessionCount = queryLong(driver, "SELECT COUNT(*) FROM agent_session;")
            if (sessionCount == 0L) {
                val duration = System.currentTimeMillis() - startTime
                FileLogger.e(
                    TAG,
                    "孤儿清理已中止：agent_session 为 0 行。" +
                        "此时「session_id NOT IN (...)」对所有行恒真，继续执行将删光 ${childTables.size} 张子表。" +
                        "请检查 agent 库是否损坏 / 自愈是否半途失败",
                )
                return CleanupResult(0, 0, emptyList(), duration)
            }

            for (table in childTables) {
                try {
                    allStats.add(
                        deleteOrphans(
                            driver = driver,
                            table = table,
                            parentTable = "agent_session",
                            parentColumn = "id",
                            childColumn = "session_id",
                        ),
                    )
                } catch (e: Exception) {
                    FileLogger.w(TAG, "清理孤儿记录失败: $table", e)
                }
            }
            // checkpoint_file_snapshots 通过 checkpoint_id 关联
            try {
                allStats.add(
                    deleteOrphans(
                        driver = driver,
                        table = "checkpoint_file_snapshots",
                        parentTable = "session_checkpoints",
                        parentColumn = "id",
                        childColumn = "checkpoint_id",
                    ),
                )
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
        val lib = LibName.entries.find { it.dbId == dbId }
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

    /**
     * 带护栏的孤儿删除：先统计「总行数 / 孤儿数」，两道校验都过了才真正 DELETE。
     *
     * @return 实际删除行数（被护栏拦下时为 0）。
     */
    private fun deleteOrphans(
        driver: SqlDriver,
        table: String,
        parentTable: String,
        parentColumn: String,
        childColumn: String,
    ): TableCleanupStats {
        val orphanWhere = "\"$childColumn\" NOT IN (SELECT \"$parentColumn\" FROM \"$parentTable\")"
        val total = queryLong(driver, "SELECT COUNT(*) FROM \"$table\";")
        if (total == 0L) return TableCleanupStats(table, 0)

        val orphans = queryLong(driver, "SELECT COUNT(*) FROM \"$table\" WHERE $orphanWhere;")
        if (orphans == 0L) return TableCleanupStats(table, 0)

        // 护栏 2：占比异常高 = 父表出了问题，不是数据该删
        if (orphans > total * MAX_ORPHAN_DELETE_RATIO) {
            FileLogger.e(
                TAG,
                "孤儿清理跳过 $table：孤儿 $orphans / 总行 $total（超过 ${MAX_ORPHAN_DELETE_RATIO * 100}% 阈值）。" +
                    "疑似 $parentTable 异常（如自愈半途失败），需人工确认后再清理",
            )
            return TableCleanupStats(table, 0)
        }

        val rows = driver.execute(null, "DELETE FROM \"$table\" WHERE $orphanWhere;", 0, null).value
        FileLogger.i(TAG, "清理孤儿记录: $table 删除 $rows 行（总 $total / 孤儿 $orphans）")
        return TableCleanupStats(table, rows.toLong())
    }

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
