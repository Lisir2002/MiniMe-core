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
 * 旧版升级迁移引擎（官方推荐 sqlcipher_export 方案）。
 *
 * 核心流程（Zetetic官方推荐）：
 * 1. 用SQLCipher打开明文库（空密钥）
 * 2. ATTACH 加密临时库（带密钥）
 * 3. 用 rawExecSQL 执行 SELECT sqlcipher_export('encrypted')
 *    （必须用rawExecSQL，不能用execSQL，否则报"another row available" error 100）
 * 4. DETACH 加密库
 * 5. 关闭明文库
 * 6. 验证加密库
 * 7. 原子替换
 *
 * sqlcipher_export 自动复制：schema、触发器、虚拟表、索引、所有数据，
 * 不需要手动逐表拷贝，避免表结构不匹配和BLOB处理问题。
 *
 * 迁移失败时安全降级：不崩溃，保留明文库，记录错误日志，应用可正常启动。
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
        // SQLCipher的密钥需要是字符串，用Base64编码
        val passphrase = Base64.encodeToString(dek, Base64.NO_WRAP)
        dek.fill(0)

        var plainDb: SQLiteDatabase? = null
        try {
            // Step 1: 清理旧的临时文件
            if (tempFile.exists()) tempFile.delete()

            // Step 2: 用SQLCipher打开明文库（空密钥，因为是明文）
            // 注意：必须用SQLCipher的SQLiteDatabase打开，不能用android标准的，
            // 因为后续要在同一个连接上执行ATTACH和sqlcipher_export
            FileLogger.d(TAG, "[${definition.id}] Step1: 打开明文库")
            SQLiteDatabase.loadLibs(context)
            plainDb = SQLiteDatabase.openDatabase(
                plainFile.absolutePath,
                "",  // 空密钥 = 明文
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )

            // Step 3: ATTACH 加密临时库
            // 密钥中的单引号需要转义
            val escapedPassphrase = passphrase.replace("'", "''")
            FileLogger.d(TAG, "[${definition.id}] Step2: ATTACH加密临时库")
            plainDb.rawExecSQL(
                "ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$escapedPassphrase'"
            )

            // Step 4: 执行 sqlcipher_export（必须用rawExecSQL！）
            // 这会自动复制所有表、索引、触发器、视图、虚拟表和数据
            FileLogger.d(TAG, "[${definition.id}] Step3: 执行sqlcipher_export")
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")

            // Step 5: DETACH
            FileLogger.d(TAG, "[${definition.id}] Step4: DETACH加密库")
            plainDb.rawExecSQL("DETACH DATABASE encrypted")

            // Step 6: 关闭明文库
            plainDb.close()
            plainDb = null

            // Step 7: 验证加密库（用密钥打开，查询表数量）
            FileLogger.d(TAG, "[${definition.id}] Step5: 验证加密库")
            val encDb = SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            val cursor = encDb.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null,
            )
            val tableCount = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            cursor.close()
            encDb.close()
            FileLogger.d(TAG, "[${definition.id}] 加密库验证通过，共 $tableCount 个表")

            if (tableCount == 0L) {
                throw DatabaseEncryptionException("迁移验证失败：加密库中没有任何用户表")
            }

            // Step 8: 备份明文库
            FileLogger.d(TAG, "[${definition.id}] Step6: 备份明文库")
            plainFile.copyTo(backupFile, overwrite = true)
            plainFile.resolveSibling("${plainFile.name}-wal").takeIf { it.exists() }?.delete()
            plainFile.resolveSibling("${plainFile.name}-shm").takeIf { it.exists() }?.delete()

            // Step 9: 原子替换
            FileLogger.d(TAG, "[${definition.id}] Step7: 原子替换")
            if (!tempFile.renameTo(plainFile)) {
                throw DatabaseEncryptionException("原子替换失败: ${tempFile.name} → ${plainFile.name}")
            }

            markMigrationCompleted(definition.id)
            FileLogger.i(TAG, "[${definition.id}] 迁移完成！")

        } catch (e: Exception) {
            FileLogger.e(TAG, "[${definition.id}] 迁移失败: ${e.message}", e)
            runCatching { plainDb?.close() }
            runCatching { tempFile.delete() }
            // 安全降级：不抛出异常，不崩溃，保留明文库，应用可正常启动
            markMigrationFailed(definition.id, e.message ?: "unknown")
            FileLogger.w(TAG, "[${definition.id}] 迁移失败，安全降级为明文模式，应用继续运行")
        }
    }

    // ============== 迁移状态 ==============

    private fun migrationKey(dbId: String): String = "migration_${dbId}_completed"
    private fun migrationFailedKey(dbId: String): String = "migration_${dbId}_failed"

    private fun isMigrationCompleted(dbId: String): Boolean =
        prefs.getBoolean(migrationKey(dbId), false)

    private fun markMigrationCompleted(dbId: String) {
        prefs.edit()
            .putBoolean(migrationKey(dbId), true)
            .remove(migrationFailedKey(dbId))
            .commit()
    }

    private fun markMigrationFailed(dbId: String, reason: String) {
        prefs.edit()
            .putString(migrationFailedKey(dbId), reason)
            .commit()
    }
}
