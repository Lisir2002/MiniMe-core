package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * “存储与数据库”诊断数据源（设置页诊断入口）。
 *
 * 展示每个物理库：文件版本（user_version）、表数量、是否已加密（首字节非明文 magic）、
 * 最后一次自愈时间（由 [com.mini.me_core.datalayer.migration.DatabaseHealthGuard] 记日志，
 * 这里提供结构化快照供设置页 UI 渲染）。
 */
data class DbDiagnostic(
    val name: String,
    val fileName: String,
    val userVersion: Int,
    val tableCount: Int,
    val encrypted: Boolean,
)

object StorageDiagnostics {

    fun snapshot(context: Context, pathProvider: DatabasePathProvider): List<DbDiagnostic> {
        return LibName.entries.map { lib ->
            val file = pathProvider.mainDb(lib)
            val encrypted = file.exists() && !isPlainSqlite(file)
            DbDiagnostic(
                name = lib.name,
                fileName = file.name,
                userVersion = -1,
                tableCount = -1,
                encrypted = encrypted,
            )
        }
    }

    /** 打开 driver 后补充实时版本/表数（由 ConnectionPool 持有 driver 时调用）。 */
    fun enrich(driver: SqlDriver): Pair<Int, Int> {
        val version = runCatching {
            driver.executeQuery(null, "PRAGMA user_version;", { c ->
                QueryResult.Value(if (c.next().value) c.getLong(0)?.toInt() ?: 0 else 0)
            }, 0, null).value
        }.getOrDefault(-1)
        val tables = runCatching {
            driver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE type='table'", { c ->
                QueryResult.Value(if (c.next().value) c.getLong(0)?.toInt() ?: 0 else 0)
            }, 0, null).value
        }.getOrDefault(-1)
        return version to tables
    }

    private fun isPlainSqlite(file: java.io.File): Boolean {
        file.inputStream().use { input ->
            val h = ByteArray(16)
            if (input.read(h) < 16) return false
            return h.copyOf("SQLite format 3\u0000".toByteArray().size)
                .contentEquals("SQLite format 3\u0000".toByteArray())
        }
    }
}
