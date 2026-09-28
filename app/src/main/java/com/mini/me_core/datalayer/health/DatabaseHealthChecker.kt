package com.mini.me_core.datalayer.health

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.DatabaseRegistry
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.LibName
import java.io.File
/** 完整性检查结果。 */
data class HealthResult(
    val passed: Boolean,
    val message: String,
    val details: String = "",
)

/** 单个数据库的统计信息。 */
data class DatabaseStats(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val totalRows: Long,
    val tableCounts: Map<String, Long>,
    val integrityPassed: Boolean,
    val lastModified: Long,
)

/**
 * 数据库健康检查器。
 *
 * 提供：
 * - PRAGMA integrity_check 完整性校验
 * - 数据库文件大小统计（含 WAL/SHM）
 * - 各表行数统计
 * - 全库概览
 *
 * 所有检查均为只读操作，不修改数据。
 */
class DatabaseHealthChecker(
    private val context: Context,
    private val registry: DatabaseRegistry,
    private val connectionPool: ConnectionPool,
) {

    companion object {
        private const val TAG = "DatabaseHealthChecker"
    }

    /**
     * 执行 PRAGMA integrity_check。
     * SQLCipher 加密库同样支持此 PRAGMA。
     */
    fun checkIntegrity(dbId: String): HealthResult {
        return try {
            val driver = getDriver(dbId)
            val result = driver.executeQuery(
                null,
                "PRAGMA integrity_check;",
                { cursor ->
                    val sb = StringBuilder()
                    var passed = false
                    while (cursor.next().value) {
                        val msg = cursor.getString(0) ?: ""
                        if (sb.isNotEmpty()) sb.append("; ")
                        sb.append(msg)
                        if (msg.equals("ok", ignoreCase = true)) passed = true
                    }
                    QueryResult.Value(HealthResult(passed = passed, message = if (passed) "通过" else "失败", details = sb.toString()))
                },
                0,
            ).value
            FileLogger.i(TAG, "integrity_check($dbId): passed=${result.passed}")
            result
        } catch (e: Exception) {
            FileLogger.e(TAG, "integrity_check($dbId) 异常", e)
            HealthResult(passed = false, message = "异常", details = e.message ?: "")
        }
    }

    /**
     * 获取数据库文件大小（字节），包含主库 + -wal + -shm 文件。
     */
    fun getDatabaseSize(dbId: String): Long {
        val def = registry.get(dbId) ?: return 0L
        val main = context.getDatabasePath(def.fileName)
        var size = if (main.exists()) main.length() else 0L
        // WAL 和 SHM 文件可能存在也可能不存在（checkpoint 后自动删除）
        val wal = File(main.parentFile, "${main.name}-wal")
        val shm = File(main.parentFile, "${main.name}-shm")
        if (wal.exists()) size += wal.length()
        if (shm.exists()) size += shm.length()
        return size
    }

    /**
     * 获取所有用户表的行数。
     * 排除 sqlite_ 前缀的系统表和 android_metadata。
     */
    fun getTableRowCounts(dbId: String): Map<String, Long> {
        val driver = getDriver(dbId)
        // 1. 查询所有用户表名
        val tableNames = driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name;",
            { cursor ->
                val out = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let { out.add(it) }
                }
                QueryResult.Value(out)
            },
            0,
        ).value

        // 2. 逐表 COUNT(*)
        val counts = mutableMapOf<String, Long>()
        for (table in tableNames) {
            try {
                val count = driver.executeQuery(
                    null,
                    "SELECT COUNT(*) FROM \"$table\";",
                    { cursor ->
                        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                    },
                    0,
                ).value
                counts[table] = count
            } catch (e: Exception) {
                FileLogger.w(TAG, "COUNT($table) 失败，跳过", e)
            }
        }
        return counts
    }

    /**
     * 获取所有已注册数据库的完整统计信息。
     */
    fun getAllDatabaseStats(): List<DatabaseStats> {
        return registry.getAll().map { def ->
            val size = getDatabaseSize(def.id)
            val integrity = checkIntegrity(def.id)
            val tableCounts = try {
                getTableRowCounts(def.id)
            } catch (e: Exception) {
                FileLogger.w(TAG, "获取表行数失败: ${def.id}", e)
                emptyMap()
            }
            val totalRows = tableCounts.values.sum()
            val mainFile = context.getDatabasePath(def.fileName)
            DatabaseStats(
                id = def.id,
                fileName = def.fileName,
                sizeBytes = size,
                totalRows = totalRows,
                tableCounts = tableCounts,
                integrityPassed = integrity.passed,
                lastModified = if (mainFile.exists()) mainFile.lastModified() else 0L,
            )
        }
    }

    /** 通过 dbId 获取对应 driver（dbId 映射到 LibName）。 */
    private fun getDriver(dbId: String): SqlDriver {
        val lib = LibName.entries.find { it.name.equals(dbId, ignoreCase = true) }
            ?: throw IllegalArgumentException("未知数据库 id: $dbId")
        return connectionPool.driver(lib)
    }
}
