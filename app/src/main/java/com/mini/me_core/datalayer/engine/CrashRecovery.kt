package com.mini.me_core.datalayer.engine

import com.mini.me_core.core.util.FileLogger
import java.io.File
import java.io.RandomAccessFile

/**
 * 启动时崩溃恢复（设计文档 db-encryption-migration-design.md §7.1 / §7.2 机制 4）。
 *
 * 在 `Application.onCreate()` 中、任何数据库访问之前调用 [recoverAll]。
 * 扫描所有 6 个库的迁移状态，对非稳定状态执行恢复。
 *
 * 恢复策略（设计文档 §7.1 表格）：
 * - PLAIN / ENCRYPTED：稳定状态，无需恢复（清理残留临时文件）
 * - PRE_SNAPSHOT：删除不完整快照，回到 PLAIN（主库未动，安全）
 * - MIGRATING：删除临时加密库，回到 PLAIN（主库未动，安全）
 * - VALIDATING：删除临时加密库，回到 PLAIN（主库未动，安全）
 * - REPLACING：通过文件头探测判断 rename 是否已完成
 *   - 主库已加密（文件头不是 "SQLite format 3\0"）→ 标记 ENCRYPTED，清理临时文件和快照
 *   - 主库仍是明文 → 删除临时加密库，回到 PLAIN
 *
 * 安全约定：
 * - 恢复操作仅限文件系统和 SharedPreferences，**不打开数据库**
 * - 主线程耗时 < 100ms（仅文件删除和 SharedPreferences 读写）
 * - 任何恢复失败记录日志但不抛出（崩溃恢复本身不应导致启动崩溃）
 * - 不删除主库文件（主库始终是安全的）
 *
 * @param pathProvider 数据库路径提供者（用于定位主库、快照、临时文件）
 * @param stateStore 迁移状态存储（用于读取和更新状态）
 */
class CrashRecovery(
    private val pathProvider: DatabasePathProvider,
    private val stateStore: MigrationStateStore,
) {

    private companion object {
        const val TAG = "CrashRecovery"

        /** 临时加密库文件名后缀（迁移引擎创建，与主库同目录）。 */
        const val TEMP_ENCRYPTED_SUFFIX = ".enc.tmp"

        /** SQLite 明文文件头（前 16 字节）。SQLCipher 加密库第一页被加密，不匹配此头。 */
        val SQLITE_PLAIN_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)

        /** 文件头探测读取字节数。 */
        const val HEADER_READ_BYTES = 16
    }

    /**
     * 恢复所有 6 个数据库的迁移状态。
     *
     * 在 Application.onCreate() 中调用，在任何数据库访问之前执行。
     * 遍历 [LibName] 所有值，对每个库调用 [recover]。
     *
     * 线程安全：可在主线程调用（仅文件操作，不打开数据库，耗时 < 100ms）。
     * 异常隔离：单个库恢复失败不影响其他库。
     */
    fun recoverAll() {
        for (lib in LibName.entries) {
            try {
                recover(lib)
            } catch (e: Exception) {
                // 崩溃恢复本身不应导致启动崩溃，记录日志继续
                FileLogger.e(TAG, "恢复库 ${lib.name} 时发生异常（跳过，不影响其他库）", e)
            }
        }
    }

    /**
     * 恢复单个数据库的迁移状态。
     *
     * 根据当前 [EncryptionStatus] 执行对应恢复策略。
     * 稳定状态（PLAIN/ENCRYPTED）仅清理残留临时文件。
     * 非稳定状态回滚到 PLAIN（或在 REPLACING 且 rename 已完成时标记 ENCRYPTED）。
     *
     * @param lib 数据库标识
     */
    fun recover(lib: LibName) {
        val state = stateStore.getState(lib)
        when (state.encryptionStatus) {
            EncryptionStatus.PLAIN -> {
                // 稳定状态：清理可能残留的临时文件
                cleanupTempFiles(lib)
            }

            EncryptionStatus.ENCRYPTED -> {
                // 稳定状态：清理残留临时文件和快照
                cleanupTempFiles(lib)
                cleanupSnapshot(lib, state)
            }

            EncryptionStatus.PRE_SNAPSHOT -> {
                // 快照中崩溃：删除不完整快照，回到 PLAIN
                FileLogger.w(TAG, "库 ${lib.name} 处于 PRE_SNAPSHOT 状态（崩溃恢复），删除不完整快照，回到 PLAIN")
                cleanupSnapshot(lib, state)
                stateStore.clearState(lib)
            }

            EncryptionStatus.MIGRATING -> {
                // 数据拷贝中崩溃：删除临时加密库，回到 PLAIN
                FileLogger.w(TAG, "库 ${lib.name} 处于 MIGRATING 状态（崩溃恢复），删除临时加密库，回到 PLAIN")
                cleanupTempEncrypted(lib, state)
                cleanupSnapshot(lib, state)
                stateStore.clearState(lib)
            }

            EncryptionStatus.VALIDATING -> {
                // 校验中崩溃：删除临时加密库，回到 PLAIN
                FileLogger.w(TAG, "库 ${lib.name} 处于 VALIDATING 状态（崩溃恢复），删除临时加密库，回到 PLAIN")
                cleanupTempEncrypted(lib, state)
                cleanupSnapshot(lib, state)
                stateStore.clearState(lib)
            }

            EncryptionStatus.REPLACING -> {
                // 替换中崩溃：需要检测 rename 是否已完成
                recoverReplacingState(lib, state)
            }
        }
    }

    /**
     * 处理 REPLACING 状态的崩溃恢复。
     *
     * 通过文件头探测判断主库是否已被加密库替换：
     * - 主库文件头不是 "SQLite format 3\0" → rename 已完成，主库已加密 → 标记 ENCRYPTED
     * - 主库文件头是 "SQLite format 3\0" → rename 未完成，主库仍是明文 → 删除临时加密库，回到 PLAIN
     * - 主库文件不存在 → 异常情况，删除临时加密库，回到 PLAIN
     *
     * @param lib 数据库标识
     * @param state 当前迁移状态
     */
    private fun recoverReplacingState(lib: LibName, state: MigrationState) {
        val mainDb = pathProvider.mainDb(lib)
        val isEncrypted = try {
            mainDb.exists() && !hasSqlitePlainHeader(mainDb)
        } catch (e: Exception) {
            FileLogger.e(TAG, "检测库 ${lib.name} 文件头时失败，按明文处理", e)
            false
        }

        if (isEncrypted) {
            // rename 已完成：主库已加密，标记 ENCRYPTED，清理临时文件和快照
            FileLogger.w(TAG, "库 ${lib.name} 处于 REPLACING 状态且主库已加密（rename 已完成），标记 ENCRYPTED")
            stateStore.setState(
                lib,
                state.copy(
                    encryptionStatus = EncryptionStatus.ENCRYPTED,
                    progressPercent = 100,
                    tempEncryptedPath = null,
                    currentTable = null,
                    lastError = null,
                ),
            )
            cleanupTempEncrypted(lib, state)
            cleanupSnapshot(lib, state)
        } else {
            // rename 未完成（或主库不存在）：主库仍是明文，删除临时加密库，回到 PLAIN
            FileLogger.w(TAG, "库 ${lib.name} 处于 REPLACING 状态但主库仍为明文（rename 未完成），删除临时加密库，回到 PLAIN")
            cleanupTempEncrypted(lib, state)
            cleanupSnapshot(lib, state)
            stateStore.clearState(lib)
        }
    }

    /**
     * 检测文件是否具有 SQLite 明文文件头（"SQLite format 3\0"）。
     *
     * SQLCipher 加密库的第一页被加密，文件头不匹配明文头。
     * 这是区分明文/加密库的可靠方法（无需打开数据库）。
     *
     * @param file 数据库文件
     * @return true 表示文件头匹配明文 SQLite 格式；false 表示不匹配（可能是加密库或损坏文件）
     */
    private fun hasSqlitePlainHeader(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_READ_BYTES) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(HEADER_READ_BYTES)
                raf.readFully(header)
                header.contentEquals(SQLITE_PLAIN_HEADER)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "读取文件头失败: ${file.absolutePath}", e)
            false
        }
    }

    /**
     * 清理临时加密库文件。
     *
     * 优先使用状态中记录的路径，其次使用约定路径（主库 + .enc.tmp 后缀）。
     *
     * @param lib 数据库标识
     * @param state 当前迁移状态（可能包含 tempEncryptedPath）
     */
    private fun cleanupTempEncrypted(lib: LibName, state: MigrationState) {
        // 优先删除状态中记录的临时加密库路径
        state.tempEncryptedPath?.let { path ->
            deleteFileSafely(File(path), "临时加密库（状态记录）")
        }
        // 同时清理约定路径的临时文件（防止状态中路径缺失）
        val conventionPath = File(pathProvider.mainDb(lib).parent, "${lib.fileName}$TEMP_ENCRYPTED_SUFFIX")
        if (conventionPath.absolutePath != state.tempEncryptedPath) {
            deleteFileSafely(conventionPath, "临时加密库（约定路径）")
        }
    }

    /**
     * 清理迁移前快照文件。
     *
     * @param lib 数据库标识
     * @param state 当前迁移状态（可能包含 snapshotPath）
     */
    private fun cleanupSnapshot(lib: LibName, state: MigrationState) {
        // 优先删除状态中记录的快照路径
        state.snapshotPath?.let { path ->
            deleteFileSafely(File(path), "快照（状态记录）")
        }
        // 同时清理约定路径的快照（DatabasePathProvider.snapshotFile）
        val conventionSnapshot = pathProvider.snapshotFile(lib)
        if (conventionSnapshot.absolutePath != state.snapshotPath) {
            deleteFileSafely(conventionSnapshot, "快照（约定路径）")
        }
    }

    /**
     * 清理所有可能的临时文件（稳定状态下的残留清理）。
     *
     * @param lib 数据库标识
     */
    private fun cleanupTempFiles(lib: LibName) {
        val conventionPath = File(pathProvider.mainDb(lib).parent, "${lib.fileName}$TEMP_ENCRYPTED_SUFFIX")
        deleteFileSafely(conventionPath, "残留临时加密库")
    }

    /**
     * 安全删除文件（不存在则跳过，失败仅记录日志不抛出）。
     *
     * @param file 要删除的文件
     * @param description 文件描述（用于日志）
     */
    private fun deleteFileSafely(file: File, description: String) {
        try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    FileLogger.i(TAG, "已删除${description}: ${file.name}")
                } else {
                    FileLogger.w(TAG, "删除${description}失败（文件可能被占用）: ${file.absolutePath}")
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "删除${description}时发生异常: ${file.absolutePath}", e)
        }
    }
}
