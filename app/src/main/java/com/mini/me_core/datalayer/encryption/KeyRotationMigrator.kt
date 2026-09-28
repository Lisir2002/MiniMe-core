package com.mini.me_core.datalayer.encryption

import android.content.Context
import android.content.SharedPreferences
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * 密钥形态迁移器：**任意「旧口令 → 新口令」形态迁移共用同一套主体**。
 *
 * 由原 `LegacyMigrationEngine`（明文→加密专用）泛化而来：
 * 过去换一次密钥形态就要复制一遍 9 步迁移代码（明文→加密、DEK 轮换、KDF 演进……），
 * 每复制一次就多一处「改了一半」的风险面。现在形态差异被收敛成两个入参：
 * [PassphraseProvider] `from`（旧形态）/ `to`（新形态）——**换密钥只换 provider，不改迁移主体**。
 *
 * 主体（Zetetic 官方推荐 `sqlcipher_export` 方案，顺序不可调换）：
 *  1. 用 **from** 口令打开源库（明文形态 provider 返回空串）；
 *  2. `ATTACH` 临时库并附 **to** 口令；
 *  3. `SELECT sqlcipher_export('encrypted')`（必须 `rawExecSQL`，用 `execSQL` 会报
 *     "another row available" error 100）；
 *  4. `DETACH`；5. 关源库；6. 用 **to** 口令验证（表数 > 0）；
 *  7. 备份源形态库为 `<name>.pre_enc.bak`（可人工恢复）；
 *  8. 原子替换；9. 标记完成。
 *
 * `sqlcipher_export` 自动搬运 schema / 触发器 / 虚拟表 / 索引 / 全部数据，
 * 无需逐表拷贝，规避表结构不匹配与 BLOB 处理问题。
 *
 * 失败语义：**安全降级**——不抛异常、不崩溃，保留源库与现场，记录失败原因，应用继续启动。
 * 源库用 from 口令都打不开时（说明它并不处于源形态：已迁移过 / 密钥不匹配），
 * **放弃迁移且不标记完成**——绝不可把它当损坏库重建，那会静默清空数据；
 * 交给 `MigrationEngine` 的 UNREADABLE 分支做「快照 → 隔离」处置。
 */
class KeyRotationMigrator(private val context: Context) {

    /**
     * 口令提供方：给定库 id，返回该库在**某一密钥形态**下的 SQLCipher 口令。
     *
     * 实现须自行保证不阻塞主线程（取 DEK 等耗时操作请在内部 `runBlocking(Dispatchers.IO)`）。
     */
    fun interface PassphraseProvider {
        fun passphraseFor(dbId: String): String
    }

    companion object {
        private const val TAG = "KeyRotationMigrator"
        private const val MIGRATION_PREFS = "minime_encryption_migration"
        private const val TEMP_SUFFIX = ".enc.tmp"

        /**
         * 「明文 → 加密」这一档的标识。
         * 其状态键刻意沿用旧版 `migration_<dbId>_completed`，保证已升级的设备不会重跑迁移。
         */
        const val TAG_ENCRYPTED = "encrypted"

        /** 明文形态：SQLCipher 以空口令打开。 */
        val PLAIN: PassphraseProvider = PassphraseProvider { _ -> "" }

        /**
         * 当前密钥体系下的加密形态 provider：purpose `db_<id>` 的 DEK → `Base64(DEK)` 口令。
         *
         * ⚠️ purpose 与 passphrase 一律经 [CipherPassphrase] 构造（密钥四条不变量 §1/§2），
         * 禁止在别处手拼，否则与 driver / 版本探测漂移后即表现为「库打不开」的假损坏。
         */
        fun dekProvider(keyManager: UnifiedKeyManager): PassphraseProvider =
            PassphraseProvider { dbId ->
                val dek = runBlocking(Dispatchers.IO) {
                    keyManager.getOrCreateDek(CipherPassphrase.purpose(dbId))
                }
                try {
                    CipherPassphrase.encode(dek)
                } finally {
                    dek.fill(0)
                }
            }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    }

    /** 该库是否仍需要跑 `tag` 这一档迁移（已完成 / 文件不存在都算不需要）。 */
    fun needsMigration(definition: DatabaseDefinition, tag: String = TAG_ENCRYPTED): Boolean {
        if (isCompleted(definition.id, tag)) return false
        val file = dbFile(definition)
        return file.exists() && file.length() > 0L
    }

    /**
     * 把库从 [from] 形态迁到 [to] 形态。
     *
     * @return true = 迁移成功（或本就无需迁移）；false = 跳过 / 降级失败，源库原样保留。
     */
    fun migrate(
        definition: DatabaseDefinition,
        from: PassphraseProvider,
        to: PassphraseProvider,
        tag: String = TAG_ENCRYPTED,
    ): Boolean {
        if (isCompleted(definition.id, tag)) {
            FileLogger.d(TAG, "迁移跳过: ${definition.id}（tag=$tag 已完成）")
            return true
        }
        val mainFile = dbFile(definition)
        if (!mainFile.exists() || mainFile.length() == 0L) {
            FileLogger.d(TAG, "迁移跳过: ${definition.id}（库文件不存在或为空）")
            return false
        }

        val sourcePassphrase = from.passphraseFor(definition.id)
        val targetPassphrase = to.passphraseFor(definition.id)
        if (sourcePassphrase == targetPassphrase) {
            FileLogger.w(TAG, "[${definition.id}] 源口令与目标口令相同，无需迁移")
            return false
        }

        FileLogger.i(TAG, "[$tag] 开始密钥形态迁移: ${definition.id} (${definition.fileName})")
        val tempFile = File(mainFile.parentFile, definition.fileName + TEMP_SUFFIX)
        val backupFile = File(mainFile.parentFile, definition.fileName + ".pre_enc.bak")

        var sourceDb: SQLiteDatabase? = null
        try {
            // Step 1: 清理旧的临时文件
            if (tempFile.exists()) tempFile.delete()

            // Step 2: 用 from 口令打开源库。打不开 = 它不在源形态，
            // 放弃迁移（不标记完成、不动文件），由上层 UNREADABLE 分支处置。
            SQLiteDatabase.loadLibs(context)
            if (!canOpen(mainFile, sourcePassphrase)) {
                FileLogger.w(
                    TAG,
                    "[${definition.id}] 源形态打开失败：该库并非 tag=$tag 的源形态" +
                        "（可能已迁移过或密钥不匹配），放弃迁移并保留原文件",
                )
                return false
            }
            FileLogger.d(TAG, "[${definition.id}] Step1: 打开源库")
            sourceDb = SQLiteDatabase.openDatabase(
                mainFile.absolutePath,
                sourcePassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )

            // Step 3: ATTACH 目标形态临时库（口令中的单引号需转义）
            val escaped = targetPassphrase.replace("'", "''")
            FileLogger.d(TAG, "[${definition.id}] Step2: ATTACH目标形态临时库")
            sourceDb.rawExecSQL(
                "ATTACH DATABASE '${tempFile.absolutePath}' AS encrypted KEY '$escaped'",
            )

            // Step 4: sqlcipher_export（自动搬运 schema/触发器/虚拟表/索引/全部数据）
            FileLogger.d(TAG, "[${definition.id}] Step3: 执行sqlcipher_export")
            sourceDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")

            // Step 5: DETACH
            FileLogger.d(TAG, "[${definition.id}] Step4: DETACH目标库")
            sourceDb.rawExecSQL("DETACH DATABASE encrypted")

            // Step 6: 关闭源库
            sourceDb.close()
            sourceDb = null

            // Step 7: 用 to 口令验证目标库（表数量 > 0）
            FileLogger.d(TAG, "[${definition.id}] Step5: 验证目标库")
            val tableCount = countUserTables(tempFile, targetPassphrase)
            FileLogger.d(TAG, "[${definition.id}] 目标库验证通过，共 $tableCount 个表")
            if (tableCount == 0L) {
                throw DatabaseEncryptionException("迁移验证失败：目标库中没有任何用户表")
            }

            // Step 8: 备份源形态库（可人工恢复；明文→加密时即明文库）
            FileLogger.d(TAG, "[${definition.id}] Step6: 备份源形态库")
            mainFile.copyTo(backupFile, overwrite = true)
            mainFile.resolveSibling("${mainFile.name}-wal").takeIf { it.exists() }?.delete()
            mainFile.resolveSibling("${mainFile.name}-shm").takeIf { it.exists() }?.delete()

            // Step 9: 原子替换
            FileLogger.d(TAG, "[${definition.id}] Step7: 原子替换")
            if (!tempFile.renameTo(mainFile)) {
                throw DatabaseEncryptionException("原子替换失败: ${tempFile.name} → ${mainFile.name}")
            }

            markCompleted(definition.id, tag)
            FileLogger.i(TAG, "[${definition.id}] [$tag] 迁移完成！")
            return true
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${definition.id}] [$tag] 迁移失败: ${e.message}", e)
            runCatching { sourceDb?.close() }
            runCatching { tempFile.delete() }
            // 安全降级：不抛出异常、不崩溃，保留源库，应用可正常启动
            markFailed(definition.id, tag, e.message ?: "unknown")
            FileLogger.w(TAG, "[${definition.id}] 迁移失败，安全降级保留源形态，应用继续运行")
            return false
        }
    }

    // ============== 内部 ==============

    private fun dbFile(definition: DatabaseDefinition): File =
        context.getDatabasePath(definition.fileName)

    /**
     * 试探性只读打开：验证该文件确处于「[passphrase] 形态」。
     * 仅 open 成功不算数——错误口令下 SQLCipher 可能在首次读页才失败，故补一次轻量查询。
     */
    private fun canOpen(file: File, passphrase: String): Boolean {
        return try {
            val db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                val cursor = db.rawQuery("SELECT count(*) FROM sqlite_master", null)
                cursor.moveToFirst()
                cursor.close()
            } finally {
                runCatching { db.close() }
            }
            true
        } catch (e: Exception) {
            FileLogger.d(TAG, "形态探测失败：${file.name}（${e.javaClass.simpleName}）")
            false
        }
    }

    private fun countUserTables(file: File, passphrase: String): Long {
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                null,
            )
            val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            cursor.close()
            count
        } finally {
            runCatching { db.close() }
        }
    }

    // ── 迁移状态 ──
    // 明文→加密（TAG_ENCRYPTED）沿用旧键名，避免已升级设备重复迁移；
    // 其它档位（如未来的 DEK 轮换）用带 tag 的键名，互不干扰。

    private fun completedKey(dbId: String, tag: String): String =
        if (tag == TAG_ENCRYPTED) "migration_${dbId}_completed" else "migration_${dbId}_${tag}_completed"

    private fun failedKey(dbId: String, tag: String): String =
        if (tag == TAG_ENCRYPTED) "migration_${dbId}_failed" else "migration_${dbId}_${tag}_failed"

    private fun isCompleted(dbId: String, tag: String): Boolean =
        prefs.getBoolean(completedKey(dbId, tag), false)

    private fun markCompleted(dbId: String, tag: String) {
        prefs.edit()
            .putBoolean(completedKey(dbId, tag), true)
            .remove(failedKey(dbId, tag))
            .commit()
    }

    private fun markFailed(dbId: String, tag: String, reason: String) {
        prefs.edit()
            .putString(failedKey(dbId, tag), reason)
            .commit()
    }
}
