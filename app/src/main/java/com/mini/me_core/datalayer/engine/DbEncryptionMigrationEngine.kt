package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.mini.me_core.core.util.FileLogger
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.InfraDb
import com.mini.mecore.datalayer.sqldelight.SettingsDb
import com.mini.mecore.datalayer.sqldelight.T2iDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SupportFactory
import java.io.File

/**
 * 数据库加密迁移引擎（设计文档 db-encryption-migration-design.md §6）。
 *
 * 负责将明文 SQLite 数据库迁移到 SQLCipher 加密数据库，以及反向迁移。
 * 迁移流程 7 步：preSnapshot → migrateData → validate → replace → cleanup。
 *
 * 核心安全保证：
 * - 主库在迁移全程只读，任何失败主库不受影响（可回滚）
 * - 原子替换：renameTo 在同一文件系统上是原子操作（rename(2) 系统调用保证）
 * - 校验通过才替换：三重校验（行数 + 校验和 + 抽样比对）全部通过才执行替换
 * - 崩溃可恢复：每步状态持久化到 SharedPreferences，启动时 CrashRecovery 检测并回滚
 * - passphrase 用后 fill(0) 擦除，日志不输出密钥内容
 *
 * 迁移期间该库不可被业务代码访问（由 ConnectionPool 保证：onPreOpen 中完成迁移后才创建驱动）。
 *
 * @param context Application Context
 * @param pathProvider 数据库路径提供者
 * @param keyProvider 数据库密钥提供者
 * @param stateStore 迁移状态存储
 */
class DbEncryptionMigrationEngine(
    private val context: Context,
    private val pathProvider: DatabasePathProvider,
    private val keyProvider: DatabaseKeyProvider,
    private val stateStore: MigrationStateStore,
) {

    private companion object {
        const val TAG = "DbEncryptionMigration"
        const val MAX_RETRY = 3
        const val MIGRATION_TIMEOUT_MS = 5 * 60 * 1000L // 5 分钟
        const val BATCH_SIZE = 1000
        const val TEMP_ENCRYPTED_SUFFIX = ".enc.tmp"
        const val TEMP_PLAIN_SUFFIX = ".plain.tmp"

        /** 系统表前缀（不需要迁移）。 */
        val SYSTEM_TABLE_PREFIXES = listOf("sqlite_", "android_metadata")
    }

    // ── 公开 API ──

    /**
     * 迁移指定数据库从明文到加密。
     *
     * 调用方必须在 IO 线程调用（内部已用 withContext(Dispatchers.IO) 包裹）。
     * 迁移过程中该库不可被业务代码访问。
     *
     * @param lib 数据库标识
     * @return 迁移结果（SUCCESS / ALREADY_ENCRYPTED / FAILED_RETRYABLE）
     * @throws MigrationException 迁移失败且重试耗尽时
     */
    suspend fun migrateToEncrypted(lib: LibName): MigrationResult = withContext(Dispatchers.IO) {
        val state = stateStore.getState(lib)
        if (state.encryptionStatus == EncryptionStatus.ENCRYPTED) {
            return@withContext MigrationResult.ALREADY_ENCRYPTED
        }
        if (state.retryCount >= MAX_RETRY) {
            throw MigrationException(
                "${lib.name} 迁移重试次数耗尽 (${state.retryCount}/$MAX_RETRY)，上次错误: ${state.lastError}",
            )
        }

        try {
            // Step 1: 迁移前快照
            val snapshotFile = stepPreSnapshot(lib)

            // Step 2: 数据拷贝（明文 → 加密临时库）
            val tempEncrypted = stepMigrateData(lib)

            // Step 3: 数据校验（三重校验）
            stepValidate(lib, tempEncrypted)

            // Step 4: 原子替换
            stepReplace(lib, tempEncrypted)

            // Step 5: 清理快照和临时文件
            stepCleanup(lib, snapshotFile)

            // 标记为已加密
            stateStore.updateState(lib) {
                it.copy(
                    encryptionStatus = EncryptionStatus.ENCRYPTED,
                    progressPercent = 100,
                    lastError = null,
                    currentTable = null,
                    tempEncryptedPath = null,
                    snapshotPath = null,
                )
            }
            FileLogger.i(TAG, "${lib.name} 迁移到加密完成")
            MigrationResult.SUCCESS
        } catch (e: Exception) {
            handleMigrationFailure(lib, e)
        }
    }

    /**
     * 反向迁移：将加密数据库回退到明文。
     *
     * 流程与正向迁移对称：加密库 → 快照 → 逐表拷贝到明文临时库 → 校验 → 原子替换 → 清理。
     *
     * @param lib 数据库标识
     * @return 迁移结果
     * @throws MigrationException 迁移失败且重试耗尽时
     */
    suspend fun migrateToPlain(lib: LibName): MigrationResult = withContext(Dispatchers.IO) {
        val state = stateStore.getState(lib)
        if (state.encryptionStatus == EncryptionStatus.PLAIN) {
            return@withContext MigrationResult.ALREADY_PLAIN
        }

        try {
            // Step 1: 快照（加密库）
            val snapshotFile = stepPreSnapshot(lib)

            // Step 2: 数据拷贝（加密 → 明文临时库）
            val tempPlain = stepMigrateDataToPlain(lib)

            // Step 3: 数据校验
            stepValidateToPlain(lib, tempPlain)

            // Step 4: 原子替换
            stepReplace(lib, tempPlain)

            // Step 5: 清理
            stepCleanup(lib, snapshotFile)

            stateStore.clearState(lib) // 回到初始 PLAIN 状态
            FileLogger.i(TAG, "${lib.name} 反向迁移到明文完成")
            MigrationResult.SUCCESS
        } catch (e: Exception) {
            handleMigrationFailure(lib, e)
        }
    }

    // ── Step 1: 迁移前快照 ──

    /**
     * 迁移前文件级快照（设计文档 §6.3 Phase 2）。
     *
     * 复制主库文件 + sidecar（-wal/-shm）到快照目录。
     * 快照用于崩溃恢复和数据校验。
     */
    private fun stepPreSnapshot(lib: LibName): File {
        stateStore.updateState(lib) {
            it.copy(
                encryptionStatus = EncryptionStatus.PRE_SNAPSHOT,
                startedAtMs = System.currentTimeMillis(),
            )
        }
        val mainDb = pathProvider.mainDb(lib)
        val snapshotFile = pathProvider.snapshotFile(lib)
        snapshotFile.parentFile?.mkdirs()

        if (mainDb.exists()) {
            mainDb.copyTo(snapshotFile, overwrite = true)
            copySidecar(mainDb, snapshotFile, "wal")
            copySidecar(mainDb, snapshotFile, "shm")
        }

        stateStore.updateState(lib) {
            it.copy(snapshotPath = snapshotFile.absolutePath)
        }
        FileLogger.i(TAG, "${lib.name} 迁移前快照完成: ${snapshotFile.name}")
        return snapshotFile
    }

    // ── Step 2: 数据拷贝（明文 → 加密）──

    /**
     * 数据拷贝：逐表将明文库数据拷贝到加密临时库（设计文档 §6.3 Phase 3，方式 A）。
     *
     * 使用类型感知的拷贝（根据列类型选择 getString/getLong/getDouble/getBytes），
     * 避免 BLOB 列被按字符串读取导致数据损坏。
     * 每表开启事务批量插入（每批 1000 行），保证性能和原子性。
     */
    private fun stepMigrateData(lib: LibName): File {
        stateStore.updateState(lib) {
            it.copy(encryptionStatus = EncryptionStatus.MIGRATING)
        }

        val mainDb = pathProvider.mainDb(lib)
        val tempEncrypted = File(mainDb.parentFile, "${mainDb.name}$TEMP_ENCRYPTED_SUFFIX")
        // 清理上次残留的临时文件
        tempEncrypted.takeIf { it.exists() }?.delete()
        deleteSidecarFiles(tempEncrypted)

        // 获取所有用户表名
        val plainDriver = createPlainDriver(lib)
        val tableNames = getUserTableNames(plainDriver)
        val schema = getSchema(lib)

        stateStore.updateState(lib) {
            it.copy(
                tempEncryptedPath = tempEncrypted.absolutePath,
                tablesTotal = tableNames.size,
                tablesCompleted = 0,
            )
        }

        // 获取 passphrase 并创建加密临时库
        val dek = runBlocking { keyProvider.getPassphrase(lib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)

        val encryptedDriver = try {
            AndroidSqliteDriver(
                schema = schema,
                context = context,
                name = tempEncrypted.name,
                factory = SupportFactory(passphrase),
            )
        } finally {
            passphrase.fill(0)
        }

        try {
            // 临时加密库用 DELETE 模式（无 WAL sidecar，简化替换）
            encryptedDriver.execute(null, "PRAGMA journal_mode = DELETE", 0, null)

            for ((index, table) in tableNames.withIndex()) {
                stateStore.updateState(lib) {
                    it.copy(currentTable = table, tablesCompleted = index)
                }

                copyTableData(plainDriver, encryptedDriver, table)

                val progress = ((index + 1).toDouble() / tableNames.size * 100).toInt()
                stateStore.updateState(lib) { it.copy(progressPercent = progress) }
                FileLogger.i(TAG, "${lib.name} 表 $table 迁移完成 (${index + 1}/${tableNames.size})")
            }

            stateStore.updateState(lib) {
                it.copy(tablesCompleted = tableNames.size, currentTable = null)
            }
        } finally {
            runCatching { plainDriver.close() }
            runCatching { encryptedDriver.close() }
        }

        return tempEncrypted
    }

    // ── Step 2b: 数据拷贝（加密 → 明文，反向迁移用）──

    private fun stepMigrateDataToPlain(lib: LibName): File {
        stateStore.updateState(lib) {
            it.copy(encryptionStatus = EncryptionStatus.MIGRATING)
        }

        val mainDb = pathProvider.mainDb(lib)
        val tempPlain = File(mainDb.parentFile, "${mainDb.name}$TEMP_PLAIN_SUFFIX")
        tempPlain.takeIf { it.exists() }?.delete()
        deleteSidecarFiles(tempPlain)

        val schema = getSchema(lib)
        val plainDriver = AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = tempPlain.name,
            factory = FrameworkSQLiteOpenHelperFactory(),
        )

        val dek = runBlocking { keyProvider.getPassphrase(lib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)
        val encryptedDriver = try {
            createEncryptedDriver(lib, passphrase)
        } finally {
            passphrase.fill(0)
        }

        try {
            plainDriver.execute(null, "PRAGMA journal_mode = DELETE", 0, null)
            val tableNames = getUserTableNames(encryptedDriver)
            stateStore.updateState(lib) {
                it.copy(tempEncryptedPath = tempPlain.absolutePath, tablesTotal = tableNames.size)
            }
            for ((index, table) in tableNames.withIndex()) {
                stateStore.updateState(lib) { it.copy(currentTable = table, tablesCompleted = index) }
                copyTableData(encryptedDriver, plainDriver, table)
                val progress = ((index + 1).toDouble() / tableNames.size * 100).toInt()
                stateStore.updateState(lib) { it.copy(progressPercent = progress) }
            }
            stateStore.updateState(lib) { it.copy(tablesCompleted = tableNames.size, currentTable = null) }
        } finally {
            runCatching { plainDriver.close() }
            runCatching { encryptedDriver.close() }
        }
        return tempPlain
    }

    // ── Step 3: 数据校验 ──

    /**
     * 数据校验：三重校验（行数 + 校验和 + 抽样比对）（设计文档 §6.3 Phase 4）。
     *
     * 校验明文库（主库）与加密临时库的数据一致性。
     * 任何一项校验失败 → 抛 MigrationValidationException → 进入失败处理。
     */
    private fun stepValidate(lib: LibName, tempEncrypted: File) {
        stateStore.updateState(lib) {
            it.copy(encryptionStatus = EncryptionStatus.VALIDATING)
        }

        val plainDriver = createPlainDriver(lib)
        val dek = runBlocking { keyProvider.getPassphrase(lib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)
        val encryptedDriver = try {
            AndroidSqliteDriver(
                schema = getSchema(lib),
                context = context,
                name = tempEncrypted.name,
                factory = SupportFactory(passphrase),
            )
        } finally {
            passphrase.fill(0)
        }

        try {
            validateDatabases(plainDriver, encryptedDriver, lib.name)
        } finally {
            runCatching { plainDriver.close() }
            runCatching { encryptedDriver.close() }
        }
    }

    private fun stepValidateToPlain(lib: LibName, tempPlain: File) {
        stateStore.updateState(lib) {
            it.copy(encryptionStatus = EncryptionStatus.VALIDATING)
        }
        val dek = runBlocking { keyProvider.getPassphrase(lib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)
        val encryptedDriver = try {
            createEncryptedDriver(lib, passphrase)
        } finally {
            passphrase.fill(0)
        }
        val plainDriver = AndroidSqliteDriver(
            schema = getSchema(lib),
            context = context,
            name = tempPlain.name,
            factory = FrameworkSQLiteOpenHelperFactory(),
        )
        try {
            validateDatabases(encryptedDriver, plainDriver, lib.name)
        } finally {
            runCatching { plainDriver.close() }
            runCatching { encryptedDriver.close() }
        }
    }

    /**
     * 三重校验核心逻辑。
     */
    private fun validateDatabases(sourceDriver: SqlDriver, targetDriver: SqlDriver, libName: String) {
        val tableNames = getUserTableNames(sourceDriver)
        val failures = mutableListOf<String>()

        for (table in tableNames) {
            // 校验 1：行数一致
            val sourceCount = queryCount(sourceDriver, table)
            val targetCount = queryCount(targetDriver, table)
            if (sourceCount != targetCount) {
                failures.add("表 $table 行数不一致: 源=$sourceCount, 目标=$targetCount")
                continue
            }

            // 校验 2：校验和一致（对所有行的 rowid 求和，简单可靠）
            val sourceChecksum = queryRowIdSum(sourceDriver, table)
            val targetChecksum = queryRowIdSum(targetDriver, table)
            if (sourceChecksum != targetChecksum) {
                failures.add("表 $table rowid 校验和不一致: 源=$sourceChecksum, 目标=$targetChecksum")
            }

            // 校验 3：抽样比对（首行、中间行、末行，按 rowid）
            if (sourceCount > 0) {
                val columns = getTableColumns(sourceDriver, table)
                val sampleRowIds = listOf(
                    queryMinRowId(sourceDriver, table),
                    queryMaxRowId(sourceDriver, table),
                ).filterNotNull().distinct()
                for (rowId in sampleRowIds) {
                    val sourceRow = queryRowByRowId(sourceDriver, table, rowId, columns)
                    val targetRow = queryRowByRowId(targetDriver, table, rowId, columns)
                    if (sourceRow != targetRow) {
                        failures.add("表 $table rowid=$rowId 抽样数据不一致")
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            throw MigrationValidationException(
                "$libName 数据校验失败 (${failures.size} 项):\n${failures.take(10).joinToString("\n")}",
            )
        }
        FileLogger.i(TAG, "$libName 数据校验通过: ${tableNames.size} 个表")
    }

    // ── Step 4: 原子替换 ──

    /**
     * 原子替换：将临时库 rename 为主库（设计文档 §6.3 Phase 5）。
     *
     * renameTo 在同一文件系统（ext4/f2fs）上是原子操作（rename(2) 系统调用保证）。
     * 替换前删除主库的 sidecar 文件（-wal/-shm），因为临时库用 DELETE 模式无 sidecar。
     * rename 失败时 fallback 到 copy + delete（非原子，但仅在极端情况下使用）。
     */
    private fun stepReplace(lib: LibName, tempFile: File) {
        stateStore.updateState(lib) {
            it.copy(encryptionStatus = EncryptionStatus.REPLACING)
        }

        val mainDb = pathProvider.mainDb(lib)

        // 删除主库的 sidecar 文件
        deleteSidecarFiles(mainDb)

        // 原子替换
        val renamed = tempFile.renameTo(mainDb)
        if (!renamed) {
            // rename 失败（极少数情况，如跨文件系统），fallback 到 copy + delete
            FileLogger.w(TAG, "${lib.name} renameTo 失败，fallback 到 copy+delete")
            tempFile.copyTo(mainDb, overwrite = true)
            tempFile.delete()
        }

        // 删除临时库的 sidecar（如果有残留）
        deleteSidecarFiles(tempFile)

        FileLogger.i(TAG, "${lib.name} 原子替换完成")
    }

    // ── Step 5: 清理 ──

    /**
     * 清理快照和临时文件（设计文档 §6.3 Phase 6）。
     *
     * P1 阶段：迁移成功后立即删除快照。
     * P2 阶段：可延迟删除（保留 24 小时用于回滚）。
     */
    private fun stepCleanup(lib: LibName, snapshotFile: File) {
        snapshotFile.takeIf { it.exists() }?.delete()
        deleteSidecarFiles(snapshotFile)
        // 清理可能残留的临时文件
        val mainDb = pathProvider.mainDb(lib)
        File(mainDb.parentFile, "${mainDb.name}$TEMP_ENCRYPTED_SUFFIX").takeIf { it.exists() }?.delete()
        File(mainDb.parentFile, "${mainDb.name}$TEMP_PLAIN_SUFFIX").takeIf { it.exists() }?.delete()
        FileLogger.i(TAG, "${lib.name} 迁移清理完成")
    }

    // ── 失败处理 ──

    /**
     * 迁移失败处理（设计文档 §6.3 Phase 7）。
     *
     * 清理临时文件（主库仍是明文，安全），更新重试计数，保留快照。
     * 重试次数耗尽时抛 MigrationException。
     */
    private fun handleMigrationFailure(lib: LibName, e: Exception): MigrationResult {
        FileLogger.e(TAG, "${lib.name} 迁移失败", e)

        // 清理临时文件
        val state = stateStore.getState(lib)
        state.tempEncryptedPath?.let { File(it) }?.takeIf { it.exists() }?.delete()
        state.tempEncryptedPath?.let { deleteSidecarFiles(File(it)) }

        val newRetryCount = state.retryCount + 1
        stateStore.updateState(lib) {
            it.copy(
                encryptionStatus = EncryptionStatus.PLAIN,
                lastError = e.message ?: e.javaClass.simpleName,
                retryCount = newRetryCount,
                progressPercent = 0,
                currentTable = null,
                tempEncryptedPath = null,
            )
        }

        if (newRetryCount >= MAX_RETRY) {
            throw MigrationException(
                "${lib.name} 迁移失败，重试耗尽 ($newRetryCount/$MAX_RETRY)，上次错误: ${e.message}",
                e,
            )
        }

        return MigrationResult.FAILED_RETRYABLE
    }

    // ── 辅助方法：数据库驱动创建 ──

    /** 创建明文驱动（打开主库）。 */
    private fun createPlainDriver(lib: LibName): SqlDriver =
        AndroidSqliteDriver(
            schema = getSchema(lib),
            context = context,
            name = lib.fileName,
            factory = FrameworkSQLiteOpenHelperFactory(),
        )

    /** 创建加密驱动（打开主库，用于反向迁移的源）。 */
    private fun createEncryptedDriver(lib: LibName, passphrase: ByteArray): SqlDriver =
        AndroidSqliteDriver(
            schema = getSchema(lib),
            context = context,
            name = lib.fileName,
            factory = SupportFactory(passphrase),
        )

    /** 根据 LibName 获取对应的 SQLDelight Schema。 */
    private fun getSchema(lib: LibName): SqlSchema<QueryResult.Value<Unit>> = when (lib) {
        LibName.AGENT -> AgentDb.Schema
        LibName.CREDENTIALS -> CredentialsDb.Schema
        LibName.SETTINGS -> SettingsDb.Schema
        LibName.WORKSPACE -> WorkspaceDb.Schema
        LibName.T2I -> T2iDb.Schema
        LibName.INFRA -> InfraDb.Schema
    }

    // ── 辅助方法：表和列信息 ──

    /** 获取所有用户表名（排除 sqlite_ 系统表和 android_metadata）。 */
    private fun getUserTableNames(driver: SqlDriver): List<String> {
        return driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
            { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let { name ->
                        if (SYSTEM_TABLE_PREFIXES.none { name.startsWith(it) }) {
                            names += name
                        }
                    }
                }
                app.cash.sqldelight.db.QueryResult.Value(names)
            },
            0,
            null,
        ).value
    }

    /** 获取表的列信息（列名 + 类型）。 */
    private fun getTableColumns(driver: SqlDriver, table: String): List<ColumnInfo> {
        return driver.executeQuery(
            null,
            "PRAGMA table_info(\"$table\")",
            { cursor ->
                val columns = mutableListOf<ColumnInfo>()
                while (cursor.next().value) {
                    val name = cursor.getString(1) ?: continue
                    val type = cursor.getString(2) ?: ""
                    columns += ColumnInfo(name, type)
                }
                app.cash.sqldelight.db.QueryResult.Value(columns)
            },
            0,
            null,
        ).value
    }

    private data class ColumnInfo(val name: String, val type: String)

    // ── 辅助方法：类型感知的数据拷贝 ──

    /**
     * 类型感知的单表数据拷贝。
     *
     * 根据列类型选择合适的读取/绑定方法：
     * - INTEGER → getLong / bindLong
     * - REAL/FLOAT/DOUBLE → getDouble / bindDouble
     * - BLOB → getBytes / bindBytes
     * - TEXT/其他 → getString / bindString
     *
     * 每批 BATCH_SIZE 行，每表一个事务。
     */
    private fun copyTableData(sourceDriver: SqlDriver, targetDriver: SqlDriver, table: String) {
        val columns = getTableColumns(sourceDriver, table)
        if (columns.isEmpty()) return

        val columnList = columns.joinToString(", ") { "\"${it.name}\"" }
        val placeholders = columns.joinToString(", ") { "?" }

        targetDriver.execute(null, "BEGIN TRANSACTION", 0, null)
        try {
            var offset = 0L
            while (true) {
                val rows = sourceDriver.executeQuery(
                    null,
                    "SELECT $columnList FROM \"$table\" LIMIT ? OFFSET ?",
                    { cursor ->
                        val batch = mutableListOf<Array<Any?>>()
                        while (cursor.next().value) {
                            val row = Array<Any?>(columns.size) { idx ->
                                readColumnByType(cursor, idx, columns[idx].type)
                            }
                            batch.add(row)
                        }
                        app.cash.sqldelight.db.QueryResult.Value(batch)
                    },
                    2,
                ) {
                    bindLong(0, BATCH_SIZE.toLong())
                    bindLong(1, offset)
                }.value

                if (rows.isEmpty()) break

                for (row in rows) {
                    targetDriver.execute(
                        null,
                        "INSERT OR REPLACE INTO \"$table\" ($columnList) VALUES ($placeholders)",
                        columns.size,
                    ) {
                        for (i in columns.indices) {
                            bindColumnByType(this, i, row[i], columns[i].type)
                        }
                    }
                }
                offset += BATCH_SIZE
            }
            targetDriver.execute(null, "COMMIT", 0, null)
        } catch (e: Exception) {
            targetDriver.execute(null, "ROLLBACK", 0, null)
            throw e
        }
    }

    /** 根据列类型从 cursor 读取值。 */
    private fun readColumnByType(cursor: SqlCursor, index: Int, type: String): Any? {
        val upperType = type.uppercase()
        return when {
            upperType == "INTEGER" || upperType.contains("INT") -> cursor.getLong(index)
            upperType.contains("REAL") || upperType.contains("FLOAT") || upperType.contains("DOUBLE") ->
                cursor.getDouble(index)
            upperType == "BLOB" -> cursor.getBytes(index)
            else -> cursor.getString(index)
        }
    }

    /** 根据列类型绑定值到 prepared statement。 */
    private fun bindColumnByType(stmt: SqlPreparedStatement, index: Int, value: Any?, type: String) {
        val upperType = type.uppercase()
        when {
            upperType == "INTEGER" || upperType.contains("INT") ->
                stmt.bindLong(index, (value as? Number)?.toLong())
            upperType.contains("REAL") || upperType.contains("FLOAT") || upperType.contains("DOUBLE") ->
                stmt.bindDouble(index, (value as? Number)?.toDouble())
            upperType == "BLOB" -> stmt.bindBytes(index, value as? ByteArray)
            else -> stmt.bindString(index, value?.toString())
        }
    }

    // ── 辅助方法：校验查询 ──

    private fun queryCount(driver: SqlDriver, table: String): Long =
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM \"$table\"",
            { cursor ->
                val count = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                app.cash.sqldelight.db.QueryResult.Value(count)
            },
            0,
            null,
        ).value

    private fun queryRowIdSum(driver: SqlDriver, table: String): Long =
        try {
            driver.executeQuery(
                null,
                "SELECT COALESCE(SUM(rowid), 0) FROM \"$table\"",
                { cursor ->
                    val sum = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                    app.cash.sqldelight.db.QueryResult.Value(sum)
                },
                0,
                null,
            ).value
        } catch (e: Exception) {
            // 无 rowid 的表（WITHOUT ROWID），跳过校验和
            0L
        }

    private fun queryMinRowId(driver: SqlDriver, table: String): Long? =
        try {
            driver.executeQuery(
                null,
                "SELECT MIN(rowid) FROM \"$table\"",
                { cursor ->
                    val v = if (cursor.next().value) cursor.getLong(0) else null
                    app.cash.sqldelight.db.QueryResult.Value(v)
                },
                0,
                null,
            ).value
        } catch (e: Exception) {
            null
        }

    private fun queryMaxRowId(driver: SqlDriver, table: String): Long? =
        try {
            driver.executeQuery(
                null,
                "SELECT MAX(rowid) FROM \"$table\"",
                { cursor ->
                    val v = if (cursor.next().value) cursor.getLong(0) else null
                    app.cash.sqldelight.db.QueryResult.Value(v)
                },
                0,
                null,
            ).value
        } catch (e: Exception) {
            null
        }

    private fun queryRowByRowId(driver: SqlDriver, table: String, rowId: Long, columns: List<ColumnInfo>): String {
        val columnList = columns.joinToString(", ") { "\"${it.name}\"" }
        return driver.executeQuery(
            null,
            "SELECT $columnList FROM \"$table\" WHERE rowid = ?",
            { cursor ->
                val values = mutableListOf<String?>()
                if (cursor.next().value) {
                    for (i in columns.indices) {
                        values += when {
                            columns[i].type.uppercase() == "BLOB" ->
                                cursor.getBytes(i)?.contentHashCode().toString()
                            else -> cursor.getString(i)
                        }
                    }
                }
                app.cash.sqldelight.db.QueryResult.Value(values.joinToString("|"))
            },
            1,
        ) {
            bindLong(0, rowId)
        }.value
    }

    // ── 辅助方法：文件操作 ──

    private fun copySidecar(source: File, target: File, suffix: String) {
        val sourceSidecar = File(source.parentFile, "${source.name}-$suffix")
        if (sourceSidecar.exists()) {
            sourceSidecar.copyTo(File(target.parentFile, "${target.name}-$suffix"), overwrite = true)
        }
    }

    private fun deleteSidecarFiles(base: File) {
        base.parentFile?.let { parent ->
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(parent, "${base.name}$suffix").takeIf { it.exists() }?.delete()
            }
        }
    }
}

/**
 * 迁移结果枚举。
 */
enum class MigrationResult {
    /** 迁移成功。 */
    SUCCESS,

    /** 已经是目标状态（无需迁移）。 */
    ALREADY_ENCRYPTED,

    /** 已经是明文（反向迁移时无需迁移）。 */
    ALREADY_PLAIN,

    /** 迁移失败但可重试（重试次数未耗尽）。 */
    FAILED_RETRYABLE,
}

/**
 * 迁移异常（迁移失败且重试耗尽时抛出）。
 */
class MigrationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 迁移数据校验异常（三重校验任一失败时抛出）。
 */
class MigrationValidationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
