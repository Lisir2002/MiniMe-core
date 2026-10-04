package com.mini.me_core.datalayer.encryption

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.util.Base64
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 加密驱动工厂。
 *
 * 简化版：只负责创建SQLCipher加密驱动，无明文/加密路由逻辑。
 * 所有数据库默认加密。
 *
 * 安全约定：
 * - 任何异常向上抛出，绝不回退明文驱动（fail-close）
 * - DEK和passphrase使用后立即fill(0)擦除
 * - 日志中不输出密钥内容
 * - 捕获UnsatisfiedLinkError（SQLCipher原生库加载失败）
 *
 * 打开后强制 PRAGMA（H8）：
 * 代码注释多年声称「WAL + synchronous=FULL」，但**从未有任何一行真正执行过**，
 * 实际落的是 Android 默认（journal_mode 通常仍是 delete 模式 / synchronous=FULL 默认 2）。
 * 现在显式执行并**回读生效值**记日志，让"声称"变成可验证的事实：
 *  - `journal_mode=WAL`：读写并发、崩溃后依靠 WAL 恢复，与本仓「单写者 + 快照」策略配套；
 *  - `synchronous=FULL`：每次提交都 fsync，掉电不丢已提交事务（F2/自愈都建立在"已提交即安全"之上）；
 *  - `foreign_keys=ON`：让 .sq 里声明的 FOREIGN KEY **真正生效**，从源头杜绝新的孤儿行
 *    （此前它们是空声明，孤儿全靠 DatabaseCleanupManager 那个危险任务兜底）。
 *
 * ⚠️ `foreign_keys` 必须在**事务之外**设置（事务内该 PRAGMA 是 no-op），故放在 driver 构造之后、
 * 返回之前；它与 [com.mini.me_core.datalayer.migration.SchemaSelfHealer] 的
 * `legacy_alter_table` + 临时关闭 FK 流程不冲突（自愈内部会自己关掉再恢复）。
 *
 * @param enableForeignKeys FK 开关（默认开）。出现大面积 FK 约束报错时的紧急回退阀：
 *   由 DI 处传 false 即可恢复旧行为，无需改代码。
 */
class EncryptedDriverFactory(
    private val context: Context,
    private val keyManager: UnifiedKeyManager,
    private val enableForeignKeys: Boolean = true,
) {

    companion object {
        private const val TAG = "EncryptedDriverFactory"
    }

    /**
     * 创建加密驱动（同步阻塞版本，供ConnectionPool在非协程上下文中调用）。
     *
     * @param definition 数据库定义
     * @return SQLCipher加密的SqlDriver
     * @throws DatabaseEncryptionException 驱动创建失败时
     */
    fun createBlocking(definition: DatabaseDefinition): SqlDriver {
        val dek = runBlocking(Dispatchers.IO) {
            keyManager.getOrCreateDek(CipherPassphrase.purpose(definition.id))
        }
        // DEK 编码为 Base64 字符串，再派生「字符串 + UTF-8 字节」两态（L2：两态同源，杜绝漂移）。
        // ⚠️ 必须与 AndroidVersionProbe 的探测打开方式完全一致，否则出现「driver 能开、probe 打不开」的假损坏。
        val open = CipherPassphrase.openParams(CipherPassphrase.encode(dek))
        dek.fill(0)
        try {
            return createDriverWithRecovery(definition, open.bytes.copyOf())
        } catch (e: UnsatisfiedLinkError) {
            FileLogger.e(TAG, "SQLCipher原生库加载失败: ${definition.id}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "SQLCipher原生库加载失败: ${definition.id}",
                e,
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "加密驱动创建失败: ${definition.id}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "加密数据库打开失败: ${definition.id}",
                e,
            )
        } finally {
            open.bytes.fill(0)
        }
    }

    /**
     * 创建加密驱动，内含数据库损坏自动恢复。
     *
     * 恢复策略：首次打开若因数据库文件损坏失败（file is not a database / malformed /
     * encryption error 等），将损坏文件备份到 databases/corrupted_backup/ 后删除原文件
     * 及 WAL/SHM，再用同一密钥重新打开（SQLCipher 会创建全新空库）。二次失败才向上抛出。
     *
     * 这样即使数据库文件因强杀、掉电、存储空间不足等原因损坏，应用也不会启动崩溃，
     * 而是自动恢复到可用状态（损坏文件保留备份，不丢原始证据）。
     */
    private fun createDriverWithRecovery(definition: DatabaseDefinition, passphraseBytes: ByteArray): SqlDriver {
        return try {
            val cipherFactory = SupportFactory(passphraseBytes)
            val driver = AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = definition.fileName,
                factory = cipherFactory,
            )
            applyPragmas(driver, definition.id)
            driver
        } catch (e: Exception) {
            if (isCorruptionError(e)) {
                FileLogger.w(TAG, "检测到数据库损坏: ${definition.id}（${e.javaClass.simpleName}: ${e.message}），启动自动恢复")
                val recovered = tryRecoverAndReopen(definition, passphraseBytes)
                if (recovered != null) {
                    FileLogger.i(TAG, "数据库损坏自动恢复成功: ${definition.id}")
                    return recovered
                }
                FileLogger.e(TAG, "数据库损坏自动恢复失败（二次打开仍异常）: ${definition.id}")
            }
            throw e
        }
    }

    /** 判断异常是否为数据库文件损坏类型。 */
    private fun isCorruptionError(e: Exception): Boolean {
        val msg = (e.message ?: "").lowercase(Locale.ROOT)
        val causeMsg = (e.cause?.message ?: "").lowercase(Locale.ROOT)
        val combined = "$msg $causeMsg"
        return combined.contains("file is not a database") ||
            combined.contains("malformed database") ||
            combined.contains("database disk image is malformed") ||
            combined.contains("encryption error") ||
            combined.contains("passphrase appears to be cleared") ||
            combined.contains("not an error") ||
            (e.cause is android.database.sqlite.SQLiteException)
    }

    /** 备份损坏文件、删除原文件及 WAL/SHM，然后重新打开。成功返回驱动，失败返回 null。 */
    private fun tryRecoverAndReopen(definition: DatabaseDefinition, passphraseBytes: ByteArray): SqlDriver? {
        val dbFile = context.getDatabasePath(definition.fileName)
        return try {
            // 备份损坏文件
            backupCorruptedFile(dbFile, definition.id)
            // 删除原文件及 WAL/SHM
            deleteDatabaseFiles(dbFile)
            // 重新打开（SQLCipher 会创建全新空库）
            val cipherFactory = SupportFactory(passphraseBytes.copyOf())
            val driver = AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = definition.fileName,
                factory = cipherFactory,
            )
            applyPragmas(driver, definition.id)
            driver
        } catch (e: Exception) {
            FileLogger.e(TAG, "恢复后重新打开失败: ${definition.id}", e)
            null
        }
    }

    /** 将损坏的数据库文件备份到 databases/corrupted_backup/ 目录，文件名带时间戳。 */
    private fun backupCorruptedFile(dbFile: File, dbId: String) {
        if (!dbFile.exists()) return
        try {
            val backupDir = File(dbFile.parentFile, "corrupted_backup")
            if (!backupDir.exists()) backupDir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "${dbFile.name}_corrupted_$timestamp")
            dbFile.copyTo(backupFile, overwrite = true)
            FileLogger.i(TAG, "损坏数据库已备份: $dbId -> ${backupFile.absolutePath} (${dbFile.length()} bytes)")
            // 同时备份 WAL 和 SHM（如果存在）
            listOf("${dbFile.absolutePath}-wal", "${dbFile.absolutePath}-shm").forEach { path ->
                val f = File(path)
                if (f.exists()) {
                    val walBackup = File(backupDir, "${f.name}_corrupted_$timestamp")
                    f.copyTo(walBackup, overwrite = true)
                }
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "损坏数据库备份失败（不影响恢复）: $dbId", e)
        }
    }

    /** 删除数据库文件及关联的 WAL/SHM 文件。 */
    private fun deleteDatabaseFiles(dbFile: File) {
        try {
            if (dbFile.exists()) dbFile.delete()
            listOf("${dbFile.absolutePath}-wal", "${dbFile.absolutePath}-shm", "${dbFile.absolutePath}-journal").forEach { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            FileLogger.i(TAG, "已删除损坏数据库文件: ${dbFile.absolutePath}")
        } catch (e: Exception) {
            FileLogger.w(TAG, "删除损坏数据库文件失败: ${dbFile.absolutePath}", e)
        }
    }

    /**
     * 打开后显式设置并**回读**关键 PRAGMA（H8）。
     *
     * 回读的意义：只执行不回读，日志里永远看不出到底生效没有——
     * 本仓过去正是"注释写了 WAL 但代码没执行，谁也没发现"。
     */
    private fun applyPragmas(driver: SqlDriver, dbId: String) {
        val journal = queryPragma(driver, "PRAGMA journal_mode=WAL")
        val synchronous = queryPragma(driver, "PRAGMA synchronous=FULL")
        val fk = if (enableForeignKeys) queryPragma(driver, "PRAGMA foreign_keys=ON") else null
        FileLogger.i(
            TAG,
            "[$dbId] PRAGMA 生效值: journal_mode=$journal, synchronous=$synchronous, " +
                "foreign_keys=${fk ?: "OFF(已按配置关闭)"}",
        )
        if (enableForeignKeys && fk != "1") {
            FileLogger.w(TAG, "[$dbId] foreign_keys 未生效（读回 $fk）：FK 约束仍为空声明，孤儿行不会被拦截")
        }
    }

    /** 执行一条会返回单值的 PRAGMA 并读回首列；失败只记日志，不影响打开流程。 */
    private fun queryPragma(driver: SqlDriver, sql: String): String? = try {
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                val v = if (cursor.next().value) {
                    cursor.getString(0) ?: cursor.getLong(0)?.toString()
                } else {
                    null
                }
                QueryResult.Value(v)
            },
            0,
            null,
        ).value
    } catch (e: Exception) {
        FileLogger.w(TAG, "PRAGMA 执行/读取失败: $sql（${e.javaClass.simpleName}）", e)
        null
    }
}
