package com.mini.me_core.datalayer.backup

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.CipherPassphrase
import com.mini.me_core.datalayer.encryption.DatabaseDefinition
import com.mini.me_core.datalayer.encryption.DatabaseRegistry
import com.mini.me_core.datalayer.encryption.SqliteIntrospect
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.exception.DataLayerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SQLiteDatabase
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** 备份导出结果。 */
data class BackupResult(
    val success: Boolean,
    val dbId: String,
    val filePath: String,
    val sizeBytes: Long,
    val checksum: String,
    /** 备份时的总行数快照（-1 = 未抓到快照，例如本机没有该库 DEK）。 */
    val rowCount: Long = -1L,
    val errorMessage: String? = null,
)

/** 备份文件元数据。 */
data class BackupMetadata(
    val formatVersion: Int,
    val dbId: String,
    val originalFileName: String,
    val timestamp: Long,
    val appVersion: Int,
    val dataSize: Long,
    val checksum: ByteArray,
    /**
     * 备份时的逐表行数快照（格式 v2 起写入；v1 备份为 null）。
     * 恢复后据此比对，挡住「integrity_check 通过但数据不对」的静默错误。
     */
    val rowCounts: Map<String, Long>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupMetadata) return false
        return checksum.contentEquals(other.checksum) && dbId == other.dbId
    }
    override fun hashCode(): Int = checksum.contentHashCode()
}

/** 恢复结果。 */
data class RestoreResult(
    val success: Boolean,
    val dbId: String,
    val restoredFrom: String,
    val integrityPassed: Boolean,
    /** 恢复后校验到的用户表数（未校验为 0）。 */
    val verifiedTables: Int = 0,
    /** 恢复后校验到的总行数（未校验为 0）。 */
    val verifiedRows: Long = 0L,
    val errorMessage: String? = null,
)

/** 备份验证结果。 */
data class BackupValidationResult(
    val valid: Boolean,
    val metadata: BackupMetadata? = null,
    val errorMessage: String? = null,
)

/** 恢复后完整性校验的结论。区分「库损坏」与「密钥不匹配」，二者处置与提示完全不同。 */
private sealed interface IntegrityVerdict {
    data class Ok(val tables: Int, val rows: Long) : IntegrityVerdict
    data class Corrupt(val detail: String) : IntegrityVerdict
    data class Undecryptable(val detail: String) : IntegrityVerdict
}

/**
 * 数据库备份与恢复管理器。
 *
 * 备份格式（.minimebak）：
 * - Magic: "MINIME_BACKUP" (12 bytes)
 * - Version: Int（写 v2；读兼容 v1 / v2）
 * - dbId 长度 + UTF-8 字节
 * - 原始文件名长度 + UTF-8 字节
 * - Timestamp: Long (epoch millis)
 * - App version: Int
 * - Data length: Long
 * - Data: 数据库文件原始字节（SQLCipher 加密，直接拷贝）
 * - **v2 起**：行数快照块（Int 表数 + 每个「表名 / Long 行数」）——与 Data 一同计入校验和
 * - SHA-256 checksum: 32 bytes（v1 对 Data 计算；v2 对 Data + 行数快照块计算）
 *
 * 恢复安全：
 * - 恢复前自动将当前库复制为 `.restore_bak`，任何一步失败都可回滚；
 * - 备份数据先写 `.restore.tmp`，长度与校验和都通过后再**原子 rename** 到库路径，
 *   避免「写一半」把库变成半成品；
 * - 完整性校验走**只读通道**（[SqliteIntrospect]）：不进 ConnectionPool，不触发版本探测 /
 *   快照 / 隔离 / schema 迁移 / 自愈，因此校验过程绝不改动被校验的库。
 *   ⚠️ 旧实现走 `connectionPool.driver(lib)`，会触发 preOpen 把刚恢复的库判定为 UNREADABLE
 *   并隔离、随后重建一个**空库**，空库的 `integrity_check` 当然返回 `ok`——于是"恢复成功"
 *   而数据全没了（审计 F4）。
 *
 * 内存：全链路流式读写（256KB 缓冲），任何大小的库都不整体入内存；
 * 声明长度一律先校验上界与文件实际长度，杜绝 `Long.toInt()` 溢出。
 *
 * ⚠️ 跨设备限制：库文件由 Keystore 中的 DEK 加密，DEK 不可导出，
 * 故本备份**只能在同一设备（同一 Keystore）上恢复**；跨设备恢复会被明确判为
 * [IntegrityVerdict.Undecryptable] 并回滚，而不是报"恢复成功"（审计 M5）。
 */
class DatabaseBackupManager(
    private val context: Context,
    private val registry: DatabaseRegistry,
    private val connectionPool: ConnectionPool,
    private val keyManager: UnifiedKeyManager? = null,
) {

    companion object {
        private const val TAG = "DatabaseBackupManager"
        private val MAGIC = "MINIME_BACKUP".toByteArray(Charsets.UTF_8) // 12 bytes

        /** 首版格式：只有 Data 参与校验和。 */
        private const val FORMAT_VERSION_1 = 1

        /** 现行格式：Data + 行数快照块参与校验和，恢复后可比对行数。 */
        private const val FORMAT_VERSION_2 = 2

        /** 写出的格式版本。读侧同时兼容 v1/v2，老备份不失效。 */
        private const val FORMAT_VERSION = FORMAT_VERSION_2

        private const val BACKUP_EXT = ".minimebak"
        private const val RESTORE_BAK_SUFFIX = ".restore_bak"
        private const val RESTORE_TMP_SUFFIX = ".restore.tmp"
        private const val CHECKSUM_LEN = 32
        private const val COPY_BUFFER = 256 * 1024

        /** 单个备份数据段的上界（8 GiB）：防 `toInt()` 溢出，也防畸形文件申请超大数组。 */
        private const val MAX_DATA_BYTES = 8L * 1024 * 1024 * 1024

        /** 行数快照块中表数的合理上界（畸形文件保护）。 */
        private const val MAX_SNAPSHOT_TABLES = 10_000
    }

    // ── 导出 ──────────────────────────────────────────────────────────

    /**
     * 导出单个数据库的加密备份。
     *
     * 先 checkpoint WAL 确保数据一致性，再**流式**拷贝文件字节（不整体入内存），
     * 同时抓一份逐表行数快照写入备份，供恢复后比对。
     */
    fun exportBackup(dbId: String, outputFile: File): BackupResult {
        val def = registry.get(dbId)
            ?: return BackupResult(false, dbId, outputFile.absolutePath, 0, "", -1L, "数据库未注册")

        val tmpFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        try {
            FileLogger.i(TAG, "开始导出备份: $dbId -> ${outputFile.absolutePath}")

            // 1. WAL checkpoint：把 WAL 中脏页写回主库文件，确保拷贝的是一致性状态
            checkpointWal(dbId)

            // 2. 校验库文件
            val dbFile = context.getDatabasePath(def.fileName)
            if (!dbFile.exists()) {
                return BackupResult(false, dbId, outputFile.absolutePath, 0, "", -1L,
                    "数据库文件不存在: ${def.fileName}")
            }
            val dataSize = dbFile.length()
            if (dataSize <= 0L) {
                return BackupResult(false, dbId, outputFile.absolutePath, 0, "", -1L,
                    "数据库文件为空: ${def.fileName}")
            }
            if (dataSize > MAX_DATA_BYTES) {
                return BackupResult(false, dbId, outputFile.absolutePath, 0, "", -1L,
                    "数据库文件过大，拒绝导出: $dataSize bytes")
            }

            // 3. 抓行数快照（只读打开，失败不阻断导出——字节完整性由校验和保证）
            val rowCounts = snapshotRowCounts(dbFile, dbId)
            val snapshotBytes = encodeRowCounts(rowCounts)

            // 4. 获取 app version
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } catch (e: Exception) {
                1
            }

            // 5. 流式写入备份文件（先写 .tmp，成功后再 rename，避免留下半个备份）
            val digest = MessageDigest.getInstance("SHA-256")
            if (tmpFile.exists()) tmpFile.delete()
            DataOutputStream(FileOutputStream(tmpFile).buffered(COPY_BUFFER)).use { out ->
                out.write(MAGIC)
                out.writeInt(FORMAT_VERSION)
                writeString(out, dbId)
                writeString(out, def.fileName)
                out.writeLong(System.currentTimeMillis())
                out.writeInt(appVersion)
                out.writeLong(dataSize)

                FileInputStream(dbFile).use { input ->
                    val buf = ByteArray(COPY_BUFFER)
                    var remaining = dataSize
                    while (remaining > 0L) {
                        val want = if (remaining < COPY_BUFFER) remaining.toInt() else COPY_BUFFER
                        val n = input.read(buf, 0, want)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        remaining -= n
                    }
                    if (remaining > 0L) {
                        throw DataLayerException.backupInvalid(
                            "库文件在拷贝过程中缩短（缺 $remaining 字节）",
                        )
                    }
                }

                out.write(snapshotBytes)
                digest.update(snapshotBytes)
                val checksum = digest.digest()
                out.write(checksum)

                if (!tmpFile.renameTo(outputFile)) {
                    throw DataLayerException.backupInvalid("落盘失败: ${outputFile.absolutePath}")
                }
                FileLogger.i(
                    TAG,
                    "备份完成: $dbId, $dataSize bytes, ${rowCounts?.size ?: 0} 张表快照, " +
                        "sha256=${checksum.toHex().take(16)}...",
                )
                return BackupResult(
                    success = true,
                    dbId = dbId,
                    filePath = outputFile.absolutePath,
                    sizeBytes = outputFile.length(),
                    checksum = checksum.toHex(),
                    rowCount = rowCounts?.let { SqliteIntrospect.totalRows(it) } ?: -1L,
                )
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "导出备份失败: $dbId", e)
            return BackupResult(false, dbId, outputFile.absolutePath, 0, "", -1L, e.message ?: "未知错误")
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    /**
     * 导出所有数据库到指定目录。
     * 文件名格式：<dbId>_<timestamp>.minimebak
     */
    fun exportAllBackups(outputDir: File): List<BackupResult> {
        if (!outputDir.exists()) outputDir.mkdirs()
        val timestamp = System.currentTimeMillis()
        return registry.getAll().map { def ->
            val outFile = File(outputDir, "${def.id}_$timestamp$BACKUP_EXT")
            exportBackup(def.id, outFile)
        }
    }

    // ── 验证 ──────────────────────────────────────────────────────────

    /**
     * 仅验证备份文件有效性（格式 + 长度 + 校验和），不执行恢复、不驻留内存。
     */
    fun validateBackup(backupFile: File): BackupValidationResult {
        return try {
            val metadata = scanBackupFile(backupFile) { _, _ -> /* 只算校验和，丢弃数据 */ }
            BackupValidationResult(valid = true, metadata = metadata)
        } catch (e: Exception) {
            FileLogger.e(TAG, "备份文件验证失败: ${backupFile.absolutePath}", e)
            BackupValidationResult(valid = false, errorMessage = e.message ?: "无法读取备份文件")
        }
    }

    // ── 恢复 ──────────────────────────────────────────────────────────

    /**
     * 从备份恢复数据库。
     *
     * 流程：
     * 1. 验证备份文件格式、长度与校验和（先判再动，避免污染现有库）
     * 2. 校验元数据里的 dbId 与目标库一致（拒绝把 A 库备份恢复到 B 库）
     * 3. 关闭所有数据库连接，将当前库复制为 `.restore_bak`
     * 4. **流式**写入 `.restore.tmp` 并复算校验和，通过后原子 rename 到库路径
     * 5. 走**只读通道**执行完整性校验（`integrity_check` + 表数 + 行数快照比对）
     * 6. 任何一步失败都自动回滚到 `.restore_bak`
     */
    fun importBackup(backupFile: File, dbId: String? = null): RestoreResult {
        FileLogger.i(TAG, "开始恢复备份: ${backupFile.absolutePath}")

        // 1. 读取并验证备份（不写入任何东西）
        val validation = validateBackup(backupFile)
        val metadata = validation.metadata
        if (!validation.valid || metadata == null) {
            return RestoreResult(false, dbId ?: "unknown", backupFile.absolutePath, false,
                errorMessage = "备份文件无效: ${validation.errorMessage}")
        }

        // 2. dbId 一致性：备份归属必须与恢复目标一致
        val targetDbId = dbId ?: metadata.dbId
        if (dbId != null && dbId != metadata.dbId) {
            FileLogger.e(TAG, "拒绝恢复：备份属于 ${metadata.dbId}，与目标 $dbId 不一致")
            return RestoreResult(false, targetDbId, backupFile.absolutePath, false,
                errorMessage = "备份属于数据库 ${metadata.dbId}，与目标 $dbId 不一致，已拒绝恢复")
        }
        val def = registry.get(targetDbId)
            ?: return RestoreResult(false, targetDbId, backupFile.absolutePath, false,
                errorMessage = "目标数据库未注册: $targetDbId")

        // 3. 关闭所有连接（释放文件锁）
        FileLogger.i(TAG, "关闭所有数据库连接")
        connectionPool.closeAll()

        val dbFile = context.getDatabasePath(def.fileName)
        val restoreBak = File(dbFile.parentFile, "${dbFile.name}$RESTORE_BAK_SUFFIX")
        val tmpFile = File(dbFile.parentFile, "${dbFile.name}$RESTORE_TMP_SUFFIX")

        try {
            // 3.1 创建当前库的安全副本
            if (dbFile.exists()) {
                FileLogger.i(TAG, "创建安全副本: ${restoreBak.absolutePath}")
                dbFile.copyTo(restoreBak, overwrite = true)
            }
            // 删除可能残留的 WAL/SHM（否则恢复后旧 WAL 会覆盖新数据）
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()

            // 4. 流式写入临时文件并复算校验和（不整体入内存）
            if (tmpFile.exists()) tmpFile.delete()
            var written = 0L
            FileOutputStream(tmpFile).buffered(COPY_BUFFER).use { out ->
                scanBackupFile(backupFile) { buf, n ->
                    out.write(buf, 0, n)
                    written += n
                }
            }
            if (written != metadata.dataSize) {
                throw DataLayerException.backupInvalid(
                    "写入字节数与声明不一致：$written ≠ ${metadata.dataSize}",
                )
            }
            FileLogger.i(TAG, "恢复数据落盘完成: $written bytes -> ${tmpFile.absolutePath}")

            // 4.1 原子替换（POSIX rename 覆盖是原子的，避免"写一半"的半成品库）
            if (!tmpFile.renameTo(dbFile)) {
                throw DataLayerException.backupInvalid("原子替换失败: ${dbFile.absolutePath}")
            }

            // 5. 只读通道完整性校验（绝不走 ConnectionPool：见类注释 F4）
            FileLogger.i(TAG, "只读通道校验完整性: $targetDbId")
            when (val verdict = verifyIntegrity(def, metadata)) {
                is IntegrityVerdict.Ok -> {
                    FileLogger.i(
                        TAG,
                        "恢复成功: $targetDbId（${verdict.tables} 张表 / ${verdict.rows} 行）",
                    )
                    return RestoreResult(
                        success = true,
                        dbId = targetDbId,
                        restoredFrom = backupFile.absolutePath,
                        integrityPassed = true,
                        verifiedTables = verdict.tables,
                        verifiedRows = verdict.rows,
                    )
                }
                is IntegrityVerdict.Undecryptable -> {
                    // 典型场景：备份来自另一台设备，或本机密钥已重置。
                    // 明确失败并回滚，绝不报"恢复成功"（审计 M5）。
                    FileLogger.e(TAG, "恢复后无法用本机密钥解密，回滚: ${verdict.detail}")
                    rollbackRestore(dbFile, restoreBak)
                    return RestoreResult(
                        success = false,
                        dbId = targetDbId,
                        restoredFrom = backupFile.absolutePath,
                        integrityPassed = false,
                        errorMessage = "备份无法用本机密钥解密（通常来自其它设备，或本机密钥已重置），已自动回滚。" +
                            "数据库备份只能在同一台设备上恢复。",
                    )
                }
                is IntegrityVerdict.Corrupt -> {
                    FileLogger.e(TAG, "恢复后完整性校验失败，回滚: ${verdict.detail}")
                    rollbackRestore(dbFile, restoreBak)
                    return RestoreResult(
                        success = false,
                        dbId = targetDbId,
                        restoredFrom = backupFile.absolutePath,
                        integrityPassed = false,
                        errorMessage = "恢复后完整性校验失败：${verdict.detail}，已自动回滚",
                    )
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "恢复过程中异常，尝试回滚", e)
            runCatching { rollbackRestore(dbFile, restoreBak) }
                .onFailure { FileLogger.e(TAG, "回滚也失败了，需要人工恢复", it) }
            return RestoreResult(false, targetDbId, backupFile.absolutePath, false,
                errorMessage = "恢复失败: ${e.message}")
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
            // 安全副本保留，让用户/运维可人工回滚
            FileLogger.i(TAG, "恢复流程结束，安全副本保留在: ${restoreBak.absolutePath}")
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────

    /** 对指定库执行 WAL checkpoint(FULL)，确保所有脏页写回主库文件。 */
    private fun checkpointWal(dbId: String) {
        try {
            val lib = LibName.entries.find { it.dbId == dbId } ?: return
            val driver: SqlDriver = connectionPool.driver(lib)
            driver.execute(null, "PRAGMA wal_checkpoint(FULL);", 0)
            FileLogger.v(TAG, "WAL checkpoint 完成: $dbId")
        } catch (e: Exception) {
            FileLogger.w(TAG, "WAL checkpoint 失败（继续导出，可能丢失最近写入）: $dbId", e)
        }
    }

    /**
     * 恢复后的完整性校验：**只读**打开刚落盘的库文件。
     *
     * 三重闸门：
     *  1. `PRAGMA integrity_check` 返回 `ok`；
     *  2. 用户表数 > 0；
     *  3. 与备份时的行数快照逐表比对（v2 备份）。
     *
     * 全程不进 ConnectionPool，因此不会触发 preOpen 的「UNREADABLE → 快照 → 隔离 → 重建空库」，
     * 也就不会出现「空库 integrity_check = ok → 报恢复成功」的静默丢数据（F4）。
     */
    private fun verifyIntegrity(def: DatabaseDefinition, metadata: BackupMetadata): IntegrityVerdict {
        val dbFile = context.getDatabasePath(def.fileName)
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return IntegrityVerdict.Corrupt("恢复后库文件不存在或为空")
        }
        val km = keyManager
        if (km == null) {
            // 无法验证即失败：宁可回滚，也不能报"成功"（fail-close）。
            return IntegrityVerdict.Undecryptable("未注入 UnifiedKeyManager，无法验证加密库")
        }
        val purpose = CipherPassphrase.purpose(def.id)
        val dek = try {
            // getDek（而非 getOrCreateDek）：校验场景绝不能"顺手"造一把新 DEK，
            // 那会让本可判定的「密钥不匹配」变成「新 DEK + 打不开」且旧数据永久不可解。
            // 此处阻塞是因为 importBackup() 是同步函数，被 ViewModel/AutoBackupManager
            // 在后台协程中调用；runBlocking 切 IO 线程取 DEK，不阻塞调用线程。
            runBlocking(Dispatchers.IO) { km.getDek(purpose) }
        } catch (e: Exception) {
            return IntegrityVerdict.Undecryptable("读取本机 DEK 失败: ${e.javaClass.simpleName}")
        }
        if (dek == null) {
            return IntegrityVerdict.Undecryptable("本机没有该库的 DEK（$purpose）")
        }
        return try {
            val passphrase = CipherPassphrase.encode(dek)
            SqliteIntrospect.withReadable(context, dbFile, passphrase) { db ->
                val integrity = readIntegrity(db)
                if (integrity.isNullOrBlank() || !integrity.equals("ok", ignoreCase = true)) {
                    return@withReadable IntegrityVerdict.Corrupt("integrity_check 报告: ${integrity ?: "(无结果)"}")
                }
                val tableCount = SqliteIntrospect.countUserTables(db)
                if (tableCount == 0L) {
                    return@withReadable IntegrityVerdict.Corrupt("库中没有任何用户表（疑似空库或表结构丢失）")
                }
                val counts = SqliteIntrospect.snapshotRowCounts(db)
                val expected = metadata.rowCounts
                if (!expected.isNullOrEmpty()) {
                    val mismatch = SqliteIntrospect.diffCounts(expected, counts)
                    if (mismatch != null) {
                        return@withReadable IntegrityVerdict.Corrupt("$mismatch（备份快照 ${SqliteIntrospect.totalRows(expected)} 行）")
                    }
                }
                IntegrityVerdict.Ok(tableCount.toInt(), SqliteIntrospect.totalRows(counts))
            }
        } catch (e: Exception) {
            // 打不开 / 首次读页失败 = 密钥不匹配或文件不是本形态的库，二者都按"无法解密"处置。
            IntegrityVerdict.Undecryptable("只读打开失败: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            dek.fill(0)
        }
    }

    /** 读 `PRAGMA integrity_check` 的首行结果。 */
    private fun readIntegrity(db: SQLiteDatabase): String? {
        val cursor = db.rawQuery("PRAGMA integrity_check;", null)
        return try {
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } finally {
            runCatching { cursor.close() }
        }
    }

    /** 抓当前库的逐表行数快照；无 DEK / 打不开时返回 null（不阻断导出）。 */
    private fun snapshotRowCounts(dbFile: File, dbId: String): Map<String, Long>? {
        val km = keyManager ?: return null
        val dek = try {
            // 此处阻塞是因为 exportBackup() 是同步函数，在后台线程执行；
            // runBlocking 切 IO 线程取 DEK，避免加密计算占用调用线程。
            runBlocking(Dispatchers.IO) { km.getDek(CipherPassphrase.purpose(dbId)) }
        } catch (e: Exception) {
            FileLogger.w(TAG, "取 DEK 失败，跳过行数快照: $dbId (${e.javaClass.simpleName})")
            return null
        } ?: return null
        return try {
            val passphrase = CipherPassphrase.encode(dek)
            SqliteIntrospect.withReadable(context, dbFile, passphrase) { db ->
                SqliteIntrospect.snapshotRowCounts(db)
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "行数快照失败（不阻断导出）: $dbId", e)
            null
        } finally {
            dek.fill(0)
        }
    }

    /** 回滚：用安全副本覆盖当前文件。 */
    private fun rollbackRestore(dbFile: File, restoreBak: File) {
        if (restoreBak.exists()) {
            restoreBak.copyTo(dbFile, overwrite = true)
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()
            FileLogger.i(TAG, "回滚完成: ${dbFile.absolutePath}")
        } else {
            FileLogger.w(TAG, "安全副本不存在，无法回滚: ${restoreBak.absolutePath}")
        }
    }

    /**
     * 顺序扫描备份文件：读头 → 流式吐出数据段（同时算校验和）→ 读行数快照（v2）→ 校验 checksum。
     *
     * @param onData 数据段回调（buf, 有效长度）。调用方可直接写盘，全链路不整体入内存。
     * @return 校验通过后的元数据；任何格式 / 长度 / 校验和问题都抛 [DataLayerException]。
     */
    private fun scanBackupFile(file: File, onData: (ByteArray, Int) -> Unit): BackupMetadata {
        val digest = MessageDigest.getInstance("SHA-256")
        DataInputStream(FileInputStream(file).buffered(COPY_BUFFER)).use { input ->
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw DataLayerException.backupInvalid("magic 不匹配，不是有效的 MiniMe 备份文件")
            }
            val version = input.readInt()
            if (version != FORMAT_VERSION_1 && version != FORMAT_VERSION_2) {
                throw DataLayerException.backupInvalid("不支持的备份格式版本: $version")
            }
            val dbId = readString(input)
            val originalFileName = readString(input)
            val timestamp = input.readLong()
            val appVersion = input.readInt()
            val dataSize = input.readLong()
            validateDataSize(dataSize, file)

            // 数据段：分块吐给调用方，逐块更新摘要
            val buf = ByteArray(COPY_BUFFER)
            var remaining = dataSize
            while (remaining > 0L) {
                val want = if (remaining < COPY_BUFFER) remaining.toInt() else COPY_BUFFER
                val n = input.read(buf, 0, want)
                if (n <= 0) {
                    throw DataLayerException.backupInvalid(
                        "数据段提前结束：声明 $dataSize 字节，还差 $remaining 字节",
                    )
                }
                onData(buf, n)
                digest.update(buf, 0, n)
                remaining -= n
            }

            // 行数快照块（v2 起）：纳入校验和，防止被篡改后误导恢复校验
            val rowCounts: Map<String, Long>? = if (version >= FORMAT_VERSION_2) {
                val (block, counts) = readRowCountsBlock(input)
                digest.update(block)
                counts
            } else {
                null
            }

            val checksum = ByteArray(CHECKSUM_LEN)
            input.readFully(checksum)
            val actual = digest.digest()
            if (!actual.contentEquals(checksum)) {
                throw DataLayerException.backupInvalid("校验和不匹配，文件可能已损坏")
            }

            return BackupMetadata(
                formatVersion = version,
                dbId = dbId,
                originalFileName = originalFileName,
                timestamp = timestamp,
                appVersion = appVersion,
                dataSize = dataSize,
                checksum = checksum,
                rowCounts = rowCounts,
            )
        }
    }

    /** 声明长度的三道校验：非空、不超上界、不超过文件实际大小。 */
    private fun validateDataSize(dataSize: Long, file: File) {
        if (dataSize <= 0L) throw DataLayerException.backupInvalid("数据段长度为 0")
        if (dataSize > MAX_DATA_BYTES) {
            throw DataLayerException.backupInvalid("数据段长度异常（$dataSize）")
        }
        if (dataSize > file.length()) {
            throw DataLayerException.backupInvalid(
                "文件不完整：声明 $dataSize 字节，实际仅 ${file.length()} 字节",
            )
        }
    }

    /** 读 v2 行数快照块，同时回传原始字节（调用方需把它计入校验和）。 */
    private fun readRowCountsBlock(input: DataInputStream): Pair<ByteArray, Map<String, Long>> {
        val tableCount = input.readInt()
        if (tableCount < 0 || tableCount > MAX_SNAPSHOT_TABLES) {
            throw DataLayerException.backupInvalid("行数快照表数异常: $tableCount")
        }
        val buffer = ByteArrayOutputStream()
        val echo = DataOutputStream(buffer)
        val counts = LinkedHashMap<String, Long>(tableCount.coerceAtMost(1024))
        repeat(tableCount) {
            val name = readString(input)
            val rows = input.readLong()
            writeString(echo, name)
            echo.writeLong(rows)
            counts[name] = rows
        }
        echo.flush()
        return Pair(buffer.toByteArray(), counts)
    }

    /** 行数快照序列化（v2 写入）。counts 为 null 时写「0 张表」。 */
    private fun encodeRowCounts(counts: Map<String, Long>?): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            if (counts == null) {
                out.writeInt(0)
            } else {
                out.writeInt(counts.size)
                for ((table, rows) in counts) {
                    writeString(out, table)
                    out.writeLong(rows)
                }
            }
        }
        return buffer.toByteArray()
    }

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        if (len < 0 || len > MAX_SNAPSHOT_TABLES * 1024) {
            throw DataLayerException.backupInvalid("内嵌字符串长度异常: $len")
        }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
