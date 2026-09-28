package com.mini.me_core.datalayer.encryption

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
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
            // Step 1: 创建加密临时库（用SQLDelight建表）
            FileLogger.d(TAG, "[${definition.id}] Step1: 创建加密临时库")
            if (tempFile.exists()) tempFile.delete()
            val initDriver = AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = tempFile.name,
                factory = SupportFactory(passphraseBytes),
            )
            initDriver.close()

            // Step 2: 打开明文库（只读）和加密临时库（读写）
            FileLogger.d(TAG, "[${definition.id}] Step2: 打开数据库")
            val plainDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                plainFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )
            SQLiteDatabase.loadLibs(context)
            val encDb = SQLiteDatabase.openOrCreateDatabase(
                tempFile.absolutePath,
                passphraseBytes,
                null,
            )

            // Step 3: 开启事务，逐表拷贝
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
        val columnNames = mutableListOf<String>()
        val colCursor = source.rawQuery("PRAGMA table_info($table)", null)
        colCursor.use {
            while (it.moveToNext()) columnNames.add(it.getString(1))
        }
        if (columnNames.isEmpty()) {
            FileLogger.w(TAG, "  表 $table 无列，跳过")
            return
        }

        val placeholders = columnNames.joinToString(",") { "?" }
        val columnsStr = columnNames.joinToString(",")
        val insertSql = "INSERT OR REPLACE INTO $table ($columnsStr) VALUES ($placeholders)"

        var totalRows = 0
        val cursor = source.rawQuery("SELECT $columnsStr FROM $table", null)
        cursor.use {
            val stmt = dest.compileStatement(insertSql)
            while (it.moveToNext()) {
                stmt.clearBindings()
                for (i in columnNames.indices) {
                    when (val value = it.getString(i)) {
                        null -> stmt.bindNull(i + 1)
                        else -> stmt.bindString(i + 1, value)
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
