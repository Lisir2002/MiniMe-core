package com.mini.me_core.datalayer.engine

import android.content.Context
import com.mini.me_core.core.model.MiniMeLog
import com.mini.me_core.datalayer.migration.MigrationEngine
import net.sqlcipher.database.SQLiteDatabase

/**
 * 明文库 → SQLCipher 加密库的事务化迁移（高风险，严格事务化）。
 *
 * 流程（每库）：
 *   1. [MigrationEngine.snapshot] 文件级快照主库 + -wal + -shm；
 *   2. 开明文库（明文 sqlite）导出；
 *   3. 建同名加密库，导入数据；
 *   4. `PRAGMA quick_check` 校验加密库完整；
 *   5. 校验通过后替换主库文件；
 *   任一步失败 → [MigrationEngine.restoreSnapshot] 回退主库，保持明文可用，不破坏启动。
 *
 * 幂等：加密库一旦建立，首字节即 SQLCipher 加密页，本迁移检测到「已有加密库」直接跳过。
 */
class SqlCipherMigration(
    @Suppress("unused") private val context: Context,
    private val pathProvider: DatabasePathProvider,
    private val keyManager: SqlCipherKeyManager,
    private val migrationEngine: MigrationEngine,
) {
    private companion object {
        const val TAG = "SqlCipherMigration"
    }

    /**
     * 对一个库执行明文→加密迁移（幂等）。返回 true 表示已是加密库或迁移成功。
     */
    fun ensureEncrypted(lib: LibName): Boolean {
        val main = pathProvider.mainDb(lib)
        if (!main.exists()) {
            // 不存在：新库直接由 SQLCipher 建，无需迁移。
            return true
        }
        if (looksEncrypted(main)) {
            MiniMeLog.i(TAG, "${lib.fileName} 已是加密库，跳过迁移")
            return true
        }

        MiniMeLog.w(TAG, "${lib.fileName} 为明文库，开始事务化加密迁移")
        runCatching { migrationEngine.snapshot(lib, heavy = true) }.getOrElse {
            MiniMeLog.e(TAG, "快照失败，中止加密迁移（保持明文）", it)
            return false
        }

        val plainPath = main.absolutePath
        val encPath = "$plainPath.enc"
        return try {
            // 开明文库（以空密码走 SQLCipher 的明文兼容模式读出）。
            val plain = SQLiteDatabase.openDatabase(plainPath, "", null, SQLiteDatabase.OPEN_READONLY)
            try {
                val enc = SQLiteDatabase.openOrCreateDatabase(encPath, keyManager.getOrCreateKey(), null)
                try {
                    exportPlainToEncrypted(plain, enc)
                    val check = enc.rawQuery("PRAGMA quick_check;", null).use { c ->
                        if (c.moveToFirst()) c.getString(0) else "unknown"
                    }
                    if (check != "ok") error("PRAGMA quick_check 失败: $check")
                } finally {
                    enc.close()
                }
            } finally {
                plain.close()
            }
            main.delete()
            java.io.File(encPath).copyTo(main, overwrite = true)
            MiniMeLog.i(TAG, "${lib.fileName} 加密迁移成功并已替换主库")
            true
        } catch (t: Throwable) {
            MiniMeLog.e(TAG, "${lib.fileName} 加密迁移失败，回滚到快照", t)
            runCatching { migrationEngine.restoreSnapshot(lib) }
            java.io.File(encPath).delete()
            false
        }
    }

    /** 开明文库导出到加密库（ATTACH 两库 + 逐表 INSERT SELECT）。 */
    private fun exportPlainToEncrypted(plain: SQLiteDatabase, enc: SQLiteDatabase) {
        enc.execSQL("ATTACH DATABASE '${plain.path}' AS plain_db KEY '';")
        enc.execSQL("BEGIN")
        try {
            val c = enc.rawQuery(
                "SELECT name FROM plain_db.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null,
            )
            try {
                while (c.moveToNext()) {
                    val table = c.getString(0)
                    enc.execSQL("INSERT INTO main.$table SELECT * FROM plain_db.$table;")
                }
            } finally {
                c.close()
            }
            enc.execSQL("COMMIT")
        } catch (t: Throwable) {
            enc.execSQL("ROLLBACK")
            throw t
        } finally {
            enc.execSQL("DETACH DATABASE plain_db;")
        }
    }

    /** 粗略判断是否已是 SQLCipher 加密库：明文 sqlite 头以 "SQLite format 3" 开头。 */
    private fun looksEncrypted(file: java.io.File): Boolean {
        file.inputStream().use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            if (read < 16) return false
            val plainMagic = "SQLite format 3\u0000".toByteArray()
            val isPlain = header.copyOf(plainMagic.size).contentEquals(plainMagic)
            return !isPlain
        }
    }
}
