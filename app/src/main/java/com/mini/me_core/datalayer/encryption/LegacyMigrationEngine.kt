package com.mini.me_core.datalayer.encryption

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * 旧版升级迁移引擎（逐表事务拷贝）。
 *
 * 放弃 ATTACH + sqlcipher_export，改用更可靠的逐表事务拷贝：
 * 1. 检查明文库是否存在
 * 2. 创建加密临时库（.enc.tmp），用SQLDelight Schema建表
 * 3. 开启事务，逐表从明文库SELECT * → 批量INSERT OR REPLACE到加密库
 * 4. 数据校验（行数比对）
 * 5. 原子替换（renameTo）
 * 6. 标记完成
 */
class LegacyMigrationEngine(
    private val context: Context,
    private val keyManager: UnifiedKeyManager,
) {

    companion object {
        private const val TAG = "LegacyMigrationEngine"
        private const val MIGRATION_PREFS = "minime_encryption_migration"
        private const val TEMP_SUFFIX = ".enc.tmp"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    }

    fun needsMigration(definition: DatabaseDefinition): Boolean {
        if (isMigrationCompleted(definition.id)) return false
        val plainFile = context.getDatabasePath(definition.fileName)
        return plainFile.exists() && plainFile.length() > 0L
    }

    fun migrateToEncrypted(definition: DatabaseDefinition) {
        if (!needsMigration(definition)) {
            FileLogger.d(TAG, "迁移跳过: ${definition.id}（已完成或明文库不存在）")
            return
        }

        FileLogger.i(TAG, "开始明文→加密迁移: ${definition.id} (${definition.fileName})")
        val plainFile = context.getDatabasePath(definition.fileName)
        val tempFile = File(plainFile.parentFile, definition.fileName + TEMP_SUFFIX)
        val backupFile = File(plainFile.parentFile, definition.fileName + ".pre_enc.bak")

        val dek = runBlocking(Dispatchers.IO) {
            keyManager.getOrCreateDek("db_${definition.id}")
        }
        val passphraseBytes = Base64.encodeToString(dek, Base64.NO_WRAP).toByteArray(Charsets.UTF_8)
        dek.fill(0)

        try {
            // Step 1: 打开明文库（只读）
            FileLogger.d(TAG, "[${definition.id}] Step1: 打开明文库")
            val plainDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                plainFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )

            // Step 2: 创建并打开加密临时库，直接从明文库复制schema
            // 不依赖AndroidSqliteDriver（懒加载可能导致表未创建），直接用SQLiteDatabase
            FileLogger.d(TAG, "[${definition.id}] Step2: 创建加密库并复制schema")
            if (tempFile.exists()) tempFile.delete()
            SQLiteDatabase.loadLibs(context)
            val encDb = SQLiteDatabase.openOrCreateDatabase(
                tempFile.absolutePath,
                passphraseBytes,
                null,
            )

            // 从明文库读取并执行所有schema对象（表、索引、触发器、视图）
            replicateSchema(plainDb, encDb, definition.id)

            // Step 3: 开启事务，逐表拷贝数据
            FileLogger.d(TAG, "[${definition.id}] Step3: 逐表拷贝数据")
            encDb.beginTransaction()
            try {
                val tableNames = getUserTables(plainDb)
                FileLogger.d(TAG, "[${definition.id}] 发现 ${tableNames.size} 个用户表: ${tableNames.joinToString()}")

                for (table in tableNames) {
                    copyTable(plainDb, encDb, table)
                }
                encDb.setTransactionSuccessful()
            } finally {
                encDb.endTransaction()
            }

            // Step 4: 数据校验
            FileLogger.d(TAG, "[${definition.id}] Step4: 数据校验")
            validateRowCounts(plainDb, encDb)

            // Step 5: 关闭数据库
            FileLogger.d(TAG, "[${definition.id}] Step5: 关闭数据库")
            plainDb.close()
            encDb.close()

            // Step 6: 备份明文库
            FileLogger.d(TAG, "[${definition.id}] Step6: 备份明文库")
            plainFile.copyTo(backupFile, overwrite = true)
            plainFile.resolveSibling("${plainFile.name}-wal").takeIf { it.exists() }?.delete()
            plainFile.resolveSibling("${plainFile.name}-shm").takeIf { it.exists() }?.delete()

            // Step 7: 原子替换
            FileLogger.d(TAG, "[${definition.id}] Step7: 原子替换")
            if (!tempFile.renameTo(plainFile)) {
                throw DatabaseEncryptionException("原子替换失败: ${tempFile.name} → ${plainFile.name}")
            }

            markMigrationCompleted(definition.id)
            FileLogger.i(TAG, "[${definition.id}] 迁移完成！")

        } catch (e: DatabaseEncryptionException) {
            throw e
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${definition.id}] 迁移失败: ${e.message}", e)
            runCatching { tempFile.delete() }
            throw DatabaseEncryptionException("明文→加密迁移失败: ${definition.id}", e)
        } finally {
            passphraseBytes.fill(0)
        }
    }

    /**
     * 从明文库复制完整schema到加密库（表、索引、触发器、视图）。
     * 直接读取sqlite_master中的sql语句并执行，确保加密库结构与明文库完全一致。
     */
    private fun replicateSchema(
        plainDb: android.database.sqlite.SQLiteDatabase,
        encDb: SQLiteDatabase,
        dbId: String,
    ) {
        // 按顺序创建：表 → 视图 → 触发器 → 索引
        // 表必须先创建，其他对象依赖表
        val types = listOf("table", "view", "trigger", "index")
        var totalObjects = 0

        for (type in types) {
            val cursor = plainDb.rawQuery(
                "SELECT name, sql FROM sqlite_master WHERE type=? " +
                    "AND sql IS NOT NULL " +
                    "AND name NOT LIKE 'sqlite_%' " +
                    "AND name != 'android_metadata' " +
                    "ORDER BY name",
                arrayOf(type),
            )
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    val sql = it.getString(1)
                    if (sql.isNotBlank()) {
                        // 跳过自动索引（sqlite_autoindex_开头）
                        if (name.startsWith("sqlite_autoindex_")) continue
                        FileLogger.d(TAG, "  [$dbId] 创建$type: $name")
                        encDb.execSQL(sql)
                        totalObjects++
                    }
                }
            }
        }
        FileLogger.d(TAG, "  [$dbId] schema复制完成，共 $totalObjects 个对象")
    }

    private fun getUserTables(db: android.database.sqlite.SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
            null,
        )
        cursor.use {
            while (it.moveToNext()) tables.add(it.getString(0))
        }
        return tables
    }

    private fun copyTable(
        source: android.database.sqlite.SQLiteDatabase,
        dest: SQLiteDatabase,
        table: String,
    ) {
        data class ColumnInfo(val name: String, val type: String)
        val columns = mutableListOf<ColumnInfo>()
        val colCursor = source.rawQuery("PRAGMA table_info($table)", null)
        colCursor.use {
            while (it.moveToNext()) {
                val name = it.getString(1)
                val type = it.getString(2) ?: ""
                columns.add(ColumnInfo(name, type.uppercase()))
            }
        }
        if (columns.isEmpty()) {
            FileLogger.w(TAG, "  表 $table 无列，跳过")
            return
        }

        val placeholders = columns.joinToString(",") { "?" }
        val columnsStr = columns.joinToString(",") { it.name }
        val insertSql = "INSERT OR REPLACE INTO $table ($columnsStr) VALUES ($placeholders)"

        var totalRows = 0
        val cursor = source.rawQuery("SELECT $columnsStr FROM $table", null)
        cursor.use {
            val stmt = dest.compileStatement(insertSql)
            while (it.moveToNext()) {
                stmt.clearBindings()
                for (i in columns.indices) {
                    val colType = columns[i].type
                    when {
                        it.isNull(i) -> stmt.bindNull(i + 1)
                        colType.contains("BLOB") -> stmt.bindBlob(i + 1, it.getBlob(i))
                        colType.contains("INT") -> stmt.bindLong(i + 1, it.getLong(i))
                        colType.contains("REAL") || colType.contains("FLOA") ||
                            colType.contains("DOUB") -> stmt.bindDouble(i + 1, it.getDouble(i))
                        else -> stmt.bindString(i + 1, it.getString(i))
                    }
                }
                stmt.execute()
                totalRows++
            }
        }
        FileLogger.d(TAG, "  表 $table: 拷贝 $totalRows 行")
    }

    private fun validateRowCounts(
        source: android.database.sqlite.SQLiteDatabase,
        dest: SQLiteDatabase,
    ) {
        val tables = getUserTables(source)
        var mismatches = 0
        for (table in tables) {
            val srcCount = queryRowCount(source, table)
            val destCount = queryRowCountEnc(dest, table)
            if (srcCount != destCount) {
                FileLogger.e(TAG, "行数不匹配: $table 明文=$srcCount 加密=$destCount")
                mismatches++
            }
        }
        if (mismatches > 0) {
            throw DatabaseEncryptionException("数据校验失败：$mismatches 个表行数不匹配")
        }
        FileLogger.d(TAG, "数据校验通过：${tables.size} 个表行数一致")
    }

    private fun queryRowCount(db: android.database.sqlite.SQLiteDatabase, table: String): Long {
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private fun queryRowCountEnc(db: SQLiteDatabase, table: String): Long {
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    // ============== 迁移状态 ==============

    private fun migrationKey(dbId: String): String = "migration_${dbId}_completed"

    private fun isMigrationCompleted(dbId: String): Boolean =
        prefs.getBoolean(migrationKey(dbId), false)

    private fun markMigrationCompleted(dbId: String) {
        prefs.edit().putBoolean(migrationKey(dbId), true).commit()
    }
}
