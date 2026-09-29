package com.mini.me_core.datalayer.encryption

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * SQLCipher **只读**连接的通用内省工具（表清单 / 逐表行数 / 快照比对）。
 *
 * 为什么抽出来（而不是各写一份）：
 *  - [KeyRotationMigrator] 用「迁移前/后逐表 `COUNT(*)` 比对」作为数据完整性闸门；
 *  - [com.mini.me_core.datalayer.backup.DatabaseBackupManager] 用「恢复后行数 ↔ 备份时快照比对」
 *    作为恢复闸门（审计 F4：校验绝不能走会改库的读写通道）。
 * 两者要的是同一套「只读打开 + 逐表计数 + 比对」语义，各写一份必然漂移，故收敛于此。
 *
 * 只读打开的三重意义：
 *  1. **不进 ConnectionPool**：不触发 `onPreOpen`（版本探测 / 快照 / 隔离）与 `onOpened`
 *     （`ensureSchema` + `SchemaSelfHealer` 自愈），因此校验过程不会改动被校验对象；
 *     这正是 F4 的根因——旧实现走 `connectionPool.driver(lib)`，preOpen 判定 UNREADABLE 后
 *     把库隔离、再重建一个空库，空库的 `integrity_check` 当然返回 `ok`，于是"恢复成功"。
 *  2. **不触发 SQLDelight 生命周期**：读到的是文件真实状态，不是迁移后的目标状态。
 *  3. 口令一律由 [CipherPassphrase] 构造，避免「driver 能开、校验打不开」的假损坏。
 *
 * 调用方须保证传入的 [Context] 可用于 `SQLiteDatabase.loadLibs`（本工具内部已调用，幂等）。
 */
internal object SqliteIntrospect {

    /** 行数快照中「该表无法统计」的哨兵值（contentless FTS5 等虚拟表会拒绝 `COUNT(*)`）。 */
    const val COUNT_UNSUPPORTED = -1L

    /** 行数快照写日志时的表数上限，避免超大库刷屏。 */
    private const val MAX_LOGGED_TABLES = 20

    /** 以只读方式打开一次库执行 [block]，无论成败都保证关闭。 */
    fun <T> withReadable(
        context: Context,
        file: File,
        passphrase: String,
        block: (SQLiteDatabase) -> T,
    ): T {
        SQLiteDatabase.loadLibs(context)
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            block(db)
        } finally {
            runCatching { db.close() }
        }
    }

    /** 执行返回单值 Long 的查询（无结果返回 0）。 */
    fun queryLong(db: SQLiteDatabase, sql: String): Long {
        val cursor = db.rawQuery(sql, null)
        return try {
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } finally {
            runCatching { cursor.close() }
        }
    }

    /** 用户表名清单（排除 `sqlite_%` 内部表），按名排序，保证快照顺序稳定。 */
    fun userTables(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        )
        try {
            while (cursor.moveToNext()) tables.add(cursor.getString(0))
        } finally {
            runCatching { cursor.close() }
        }
        return tables
    }

    fun countUserTables(db: SQLiteDatabase): Long =
        queryLong(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")

    /**
     * 抓逐表行数快照：`表名 → COUNT(*)`。
     *
     * 无法统计的虚拟表记 [COUNT_UNSUPPORTED] 哨兵，比对时跳过（不判失败）。
     */
    fun snapshotRowCounts(db: SQLiteDatabase): Map<String, Long> {
        val tables = userTables(db)
        val counts = LinkedHashMap<String, Long>(tables.size)
        for (table in tables) {
            val quoted = "\"${table.replace("\"", "\"\"")}\""
            counts[table] = runCatching { queryLong(db, "SELECT COUNT(*) FROM $quoted") }
                .getOrDefault(COUNT_UNSUPPORTED)
        }
        return counts
    }

    /**
     * 比对两份行数快照。
     *
     * @return null = 一致；否则返回首个不一致处的可读描述（调用方据此中止操作）。
     */
    fun diffCounts(source: Map<String, Long>, target: Map<String, Long>): String? {
        for ((table, srcCount) in source) {
            if (srcCount == COUNT_UNSUPPORTED) continue
            val dstCount = target[table] ?: return "目标库缺少表 $table"
            if (dstCount == COUNT_UNSUPPORTED) continue
            if (dstCount != srcCount) return "表 $table 行数不一致：源 $srcCount → 目标 $dstCount"
        }
        val srcTotal = totalRows(source)
        val dstTotal = totalRows(target)
        if (srcTotal > 0L && dstTotal == 0L) {
            return "目标库总行数为 0，而源库有 $srcTotal 行（数据疑似整体丢失）"
        }
        return null
    }

    /** 快照总行数（跳过无法统计的表）。 */
    fun totalRows(counts: Map<String, Long>): Long =
        counts.values.fold(0L) { acc, v -> if (v < 0) acc else acc + v }

    /** 快照的日志形式：只打前 [MAX_LOGGED_TABLES] 张表 + 总行数。 */
    fun formatCounts(counts: Map<String, Long>): String {
        if (counts.isEmpty()) return "(空)"
        val head = counts.entries.take(MAX_LOGGED_TABLES)
            .joinToString(", ") { (t, c) -> "$t=${if (c < 0) "n/a" else c.toString()}" }
        val more = if (counts.size > MAX_LOGGED_TABLES) ", …(+${counts.size - MAX_LOGGED_TABLES})" else ""
        return "$head$more"
    }
}
