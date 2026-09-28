package com.mini.me_core.datalayer.backup

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.DatabaseRegistry
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.exception.DataLayerErrorCode
import com.mini.me_core.datalayer.exception.DataLayerException
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
    val errorMessage: String? = null,
)

/** 备份验证结果。 */
data class BackupValidationResult(
    val valid: Boolean,
    val metadata: BackupMetadata? = null,
    val errorMessage: String? = null,
)

/**
 * 数据库备份与恢复管理器。
 *
 * 备份格式（.minimebak）：
 * - Magic: "MINIME_BACKUP" (12 bytes)
 * - Version: Int (1)
 * - dbId 长度 + UTF-8 字节
 * - 原始文件名长度 + UTF-8 字节
 * - Timestamp: Long (epoch millis)
 * - App version: Int
 * - Data length: Long
 * - Data: 数据库文件原始字节（SQLCipher 加密，直接拷贝）
 * - SHA-256 checksum: 32 bytes（对 Data 部分计算）
 *
 * 恢复安全：恢复前自动将当前库复制为 .restore_bak，失败可回滚。
 */
class DatabaseBackupManager(
    private val context: Context,
    private val registry: DatabaseRegistry,
    private val connectionPool: ConnectionPool,
) {

    companion object {
        private const val TAG = "DatabaseBackupManager"
        private val MAGIC = "MINIME_BACKUP".toByteArray(Charsets.UTF_8) // 12 bytes
        private const val FORMAT_VERSION = 1
        private const val BACKUP_EXT = ".minimebak"
        private const val RESTORE_BAK_SUFFIX = ".restore_bak"
    }

    // ── 导出 ──────────────────────────────────────────────────────────

    /**
     * 导出单个数据库的加密备份。
     * 先 checkpoint WAL 确保数据一致性，再拷贝文件字节写入备份文件。
     */
    fun exportBackup(dbId: String, outputFile: File): BackupResult {
        val def = registry.get(dbId)
            ?: return BackupResult(false, dbId, outputFile.absolutePath, 0, "", "数据库未注册")

        try {
            FileLogger.i(TAG, "开始导出备份: $dbId -> ${outputFile.absolutePath}")

            // 1. WAL checkpoint：把 WAL 中脏页写回主库文件，确保拷贝的是一致性状态
            checkpointWal(dbId)

            // 2. 读取主库文件字节
            val dbFile = context.getDatabasePath(def.fileName)
            if (!dbFile.exists()) {
                return BackupResult(false, dbId, outputFile.absolutePath, 0, "", "数据库文件不存在: ${def.fileName}")
            }
            val data = dbFile.readBytes()

            // 3. 计算 SHA-256 校验和
            val digest = MessageDigest.getInstance("SHA-256")
            val checksum = digest.digest(data)

            // 4. 获取 app version
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } catch (e: Exception) {
                1
            }

            // 5. 写入备份文件
            DataOutputStream(FileOutputStream(outputFile).buffered()).use { out ->
                out.write(MAGIC)
                out.writeInt(FORMAT_VERSION)
                writeString(out, dbId)
                writeString(out, def.fileName)
                out.writeLong(System.currentTimeMillis())
                out.writeInt(appVersion)
                out.writeLong(data.size.toLong())
                out.write(data)
                out.write(checksum)
            }

            FileLogger.i(TAG, "备份完成: $dbId, ${data.size} bytes, sha256=${checksum.toHex().take(16)}...")
            return BackupResult(
                success = true,
                dbId = dbId,
                filePath = outputFile.absolutePath,
                sizeBytes = outputFile.length(),
                checksum = checksum.toHex(),
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "导出备份失败: $dbId", e)
            return BackupResult(false, dbId, outputFile.absolutePath, 0, "", e.message ?: "未知错误")
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
     * 仅验证备份文件有效性（格式 + checksum），不执行恢复。
     */
    fun validateBackup(backupFile: File): BackupValidationResult {
        return try {
            val (metadata, dataBytes) = readBackupFile(backupFile)
            // 重新计算 checksum 比对
            val digest = MessageDigest.getInstance("SHA-256")
            val actualChecksum = digest.digest(dataBytes)
            if (!actualChecksum.contentEquals(metadata.checksum)) {
                return BackupValidationResult(
                    valid = false,
                    metadata = metadata,
                    errorMessage = "校验和不匹配，文件可能已损坏",
                )
            }
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
     * 1. 验证备份文件格式和 checksum
     * 2. 关闭所有数据库连接
     * 3. 将当前库文件复制为 .restore_bak（安全副本，失败可回滚）
     * 4. 将备份数据写入数据库文件位置
     * 5. 执行完整性校验
     * 6. 校验失败时自动从 .restore_bak 回滚
     */
    fun importBackup(backupFile: File, dbId: String? = null): RestoreResult {
        FileLogger.i(TAG, "开始恢复备份: ${backupFile.absolutePath}")

        // 1. 读取并验证备份
        val validation = validateBackup(backupFile)
        if (!validation.valid || validation.metadata == null) {
            return RestoreResult(false, dbId ?: "unknown", backupFile.absolutePath, false,
                "备份文件无效: ${validation.errorMessage}")
        }
        val metadata = validation.metadata
        val targetDbId = dbId ?: metadata.dbId
        val def = registry.get(targetDbId)
            ?: return RestoreResult(false, targetDbId, backupFile.absolutePath, false, "目标数据库未注册: $targetDbId")

        // 读取备份数据
        val (_, dataBytes) = readBackupFile(backupFile)

        // 2. 关闭所有连接（释放文件锁）
        FileLogger.i(TAG, "关闭所有数据库连接")
        connectionPool.closeAll()

        val dbFile = context.getDatabasePath(def.fileName)
        val restoreBak = File(dbFile.parentFile, "${dbFile.name}$RESTORE_BAK_SUFFIX")

        try {
            // 3. 创建当前库的安全副本
            if (dbFile.exists()) {
                FileLogger.i(TAG, "创建安全副本: ${restoreBak.absolutePath}")
                dbFile.copyTo(restoreBak, overwrite = true)
                // 同时删除可能残留的 WAL/SHM（否则恢复后旧 WAL 会覆盖新数据）
                File(dbFile.parentFile, "${dbFile.name}-wal").delete()
                File(dbFile.parentFile, "${dbFile.name}-shm").delete()
            }

            // 4. 写入备份数据
            FileLogger.i(TAG, "写入恢复数据: ${dataBytes.size} bytes -> ${dbFile.absolutePath}")
            dbFile.writeBytes(dataBytes)

            // 5. 重新打开并验证完整性
            FileLogger.i(TAG, "重新打开数据库并校验完整性: $targetDbId")
            val integrityPassed = verifyIntegrity(targetDbId)

            if (!integrityPassed) {
                // 6. 校验失败：回滚到安全副本
                FileLogger.w(TAG, "恢复后完整性校验失败，执行回滚: $targetDbId")
                rollbackRestore(dbFile, restoreBak)
                return RestoreResult(false, targetDbId, backupFile.absolutePath, false,
                    "恢复后完整性校验失败，已自动回滚")
            }

            FileLogger.i(TAG, "恢复成功: $targetDbId")
            return RestoreResult(true, targetDbId, backupFile.absolutePath, true)
        } catch (e: Exception) {
            FileLogger.e(TAG, "恢复过程中异常，尝试回滚", e)
            // 异常时尝试回滚
            try {
                rollbackRestore(dbFile, restoreBak)
            } catch (rollbackEx: Exception) {
                FileLogger.e(TAG, "回滚也失败了，需要人工恢复", rollbackEx)
            }
            return RestoreResult(false, targetDbId, backupFile.absolutePath, false,
                "恢复失败: ${e.message}")
        } finally {
            // 清理安全副本（恢复成功后保留一份也行，这里删除避免占用空间）
            // 保守起见保留，让用户/运维决定
            FileLogger.i(TAG, "恢复流程结束，安全副本保留在: ${restoreBak.absolutePath}")
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────

    /** 对指定库执行 WAL checkpoint(FULL)，确保所有脏页写回主库文件。 */
    private fun checkpointWal(dbId: String) {
        try {
            val lib = LibName.entries.find { it.name.equals(dbId, ignoreCase = true) } ?: return
            val driver: SqlDriver = connectionPool.driver(lib)
            driver.execute(null, "PRAGMA wal_checkpoint(FULL);", 0)
            FileLogger.v(TAG, "WAL checkpoint 完成: $dbId")
        } catch (e: Exception) {
            FileLogger.w(TAG, "WAL checkpoint 失败（继续导出，可能丢失最近写入）: $dbId", e)
        }
    }

    /** 通过打开连接执行 PRAGMA integrity_check。 */
    private fun verifyIntegrity(dbId: String): Boolean {
        return try {
            val lib = LibName.entries.find { it.name.equals(dbId, ignoreCase = true) } ?: return false
            val driver = connectionPool.driver(lib)
            driver.executeQuery(
                null,
                "PRAGMA integrity_check;",
                { cursor ->
                    var ok = false
                    while (cursor.next().value) {
                        if (cursor.getString(0)?.equals("ok", ignoreCase = true) == true) ok = true
                    }
                    QueryResult.Value(ok)
                },
                0,
            ).value
        } catch (e: Exception) {
            FileLogger.e(TAG, "完整性校验异常: $dbId", e)
            false
        }
    }

    /** 回滚：用安全副本覆盖当前文件。 */
    private fun rollbackRestore(dbFile: File, restoreBak: File) {
        if (restoreBak.exists()) {
            restoreBak.copyTo(dbFile, overwrite = true)
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()
            FileLogger.i(TAG, "回滚完成: ${dbFile.absolutePath}")
        }
    }

    /** 读取备份文件，返回元数据和数据字节。 */
    private fun readBackupFile(file: File): Pair<BackupMetadata, ByteArray> {
        DataInputStream(FileInputStream(file).buffered()).use { input ->
            // 校验 magic
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            if (!magic.contentEquals(MAGIC)) {
                throw DataLayerException.backupInvalid("magic 不匹配，不是有效的 MiniMe 备份文件")
            }
            val version = input.readInt()
            if (version != FORMAT_VERSION) {
                throw DataLayerException.backupInvalid("不支持的备份格式版本: $version")
            }
            val dbId = readString(input)
            val originalFileName = readString(input)
            val timestamp = input.readLong()
            val appVersion = input.readInt()
            val dataSize = input.readLong()
            val data = ByteArray(dataSize.toInt())
            input.readFully(data)
            val checksum = ByteArray(32)
            input.readFully(checksum)

            val metadata = BackupMetadata(
                formatVersion = version,
                dbId = dbId,
                originalFileName = originalFileName,
                timestamp = timestamp,
                appVersion = appVersion,
                dataSize = dataSize,
                checksum = checksum,
            )
            return Pair(metadata, data)
        }
    }

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val len = input.readInt()
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
