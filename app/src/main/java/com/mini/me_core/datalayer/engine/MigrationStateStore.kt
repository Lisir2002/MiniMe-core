package com.mini.me_core.datalayer.engine

import android.content.Context
import android.content.SharedPreferences

/**
 * 数据库加密状态枚举（设计文档 db-encryption-migration-design.md §6.1）。
 *
 * 每个数据库独立维护迁移状态，状态持久化于 SharedPreferences。
 * 状态流转：PLAIN → PRE_SNAPSHOT → MIGRATING → VALIDATING → REPLACING → ENCRYPTED
 *
 * 任何非稳定状态（PRE_SNAPSHOT/MIGRATING/VALIDATING/REPLACING）下崩溃，
 * 启动时由 CrashRecovery 检测并回滚到 PLAIN（利用迁移前快照恢复）。
 */
enum class EncryptionStatus {
    /** 明文库，未开始迁移（稳定状态）。 */
    PLAIN,

    /** 迁移前快照中（非稳定状态，崩溃后回滚）。 */
    PRE_SNAPSHOT,

    /** 明文→加密数据拷贝中（非稳定状态，崩溃后回滚）。 */
    MIGRATING,

    /** 数据校验中（行数+校验和，非稳定状态，崩溃后回滚）。 */
    VALIDATING,

    /** 原子替换中（renameTo，非稳定状态，崩溃后回滚）。 */
    REPLACING,

    /** 已加密，正常使用（稳定状态）。 */
    ENCRYPTED,
}

/**
 * 单个数据库的迁移状态（设计文档 §6.2）。
 *
 * 状态持久化于 SharedPreferences（逐字段存储，无需 JSON 序列化）。
 * 所有时间字段使用 epoch millis（System.currentTimeMillis()）。
 *
 * @property libName 数据库标识名（LibName.name）
 * @property encryptionStatus 当前加密状态
 * @property startedAtMs 迁移开始时间（epoch millis），未开始为 null
 * @property snapshotPath 迁移前快照文件绝对路径，无快照为 null
 * @property tempEncryptedPath 临时加密库文件绝对路径，无临时库为 null
 * @property progressPercent 迁移进度百分比（0-100）
 * @property currentTable 当前正在迁移的表名，未在迁移表为 null
 * @property tablesTotal 迁移总表数
 * @property tablesCompleted 已完成迁移表数
 * @property lastError 上次错误信息，无错误为 null
 * @property retryCount 当前迁移重试次数
 */
data class MigrationState(
    val libName: String,
    val encryptionStatus: EncryptionStatus,
    val startedAtMs: Long? = null,
    val snapshotPath: String? = null,
    val tempEncryptedPath: String? = null,
    val progressPercent: Int = 0,
    val currentTable: String? = null,
    val tablesTotal: Int = 0,
    val tablesCompleted: Int = 0,
    val lastError: String? = null,
    val retryCount: Int = 0,
) {
    companion object {
        /**
         * 创建初始 PLAIN 状态。
         *
         * @param lib 数据库标识
         * @return 初始状态（PLAIN，进度 0，无错误）
         */
        fun initial(lib: LibName): MigrationState = MigrationState(
            libName = lib.name,
            encryptionStatus = EncryptionStatus.PLAIN,
        )
    }
}

/**
 * 迁移状态存储接口（设计文档 §6.2）。
 *
 * 负责每库迁移状态的持久化与读取。实现必须线程安全（多线程可能并发读写状态）。
 * 状态存储于应用私有 SharedPreferences，不随 App 卸载保留（与数据库同生命周期）。
 */
interface MigrationStateStore {

    /**
     * 获取指定数据库的迁移状态。
     *
     * 若该库从未设置过状态，返回 [MigrationState.initial]（PLAIN 状态）。
     *
     * @param lib 数据库标识
     * @return 当前迁移状态（永不为 null）
     */
    fun getState(lib: LibName): MigrationState

    /**
     * 设置指定数据库的迁移状态（全量覆盖）。
     *
     * @param lib 数据库标识
     * @param state 新状态
     */
    fun setState(lib: LibName, state: MigrationState)

    /**
     * 原子更新指定数据库的迁移状态。
     *
     * 读取当前状态，应用 [update] lambda，再写回。整个过程加锁，
     * 避免读-改-写竞态。
     *
     * @param lib 数据库标识
     * @param update 状态转换函数（接收当前状态，返回新状态）
     */
    fun updateState(lib: LibName, update: (MigrationState) -> MigrationState)

    /**
     * 清除指定数据库的迁移状态（恢复为初始 PLAIN 状态）。
     *
     * @param lib 数据库标识
     */
    fun clearState(lib: LibName)

    /**
     * 读取已安装的迁移逻辑版本（全局，不区分数据库）。
     *
     * 用于在迁移代码缺陷修复后识别并重置历史失败状态。
     *
     * @return 上次写入的逻辑版本，从未写入返回 0
     */
    fun getLogicVersion(): Int

    /**
     * 写入当前迁移逻辑版本（全局）。
     *
     * @param version 迁移逻辑版本
     */
    fun setLogicVersion(version: Int)
}

/**
 * SharedPreferences 实现的迁移状态存储（设计文档 §6.2）。
 *
 * 每个库的状态字段存储在同一个 SharedPreferences 文件中，
 * key 格式为 `<libName>_<fieldName>`，避免多库状态互相覆盖。
 *
 * 线程安全：[updateState] 使用 [synchronized] 保护读-改-写；
 * [getState]/[setState]/[clearState] 依赖 SharedPreferences 自身的线程安全。
 *
 * SharedPreferences 名称：`minime_db_migration_state`（设计文档 §6.1）。
 *
 * @param context Application Context
 */
class SharedPreferencesMigrationStateStore(
    context: Context,
) : MigrationStateStore {

    private companion object {
        const val PREFS_NAME = "minime_db_migration_state"

        // 字段名后缀
        private const val FIELD_STATUS = "status"
        private const val FIELD_STARTED_AT = "started_at_ms"
        private const val FIELD_SNAPSHOT_PATH = "snapshot_path"
        private const val FIELD_TEMP_ENCRYPTED_PATH = "temp_encrypted_path"
        private const val FIELD_PROGRESS = "progress_percent"
        private const val FIELD_CURRENT_TABLE = "current_table"
        private const val FIELD_TABLES_TOTAL = "tables_total"
        private const val FIELD_TABLES_COMPLETED = "tables_completed"
        private const val FIELD_LAST_ERROR = "last_error"
        private const val FIELD_RETRY_COUNT = "retry_count"

        // 全局迁移逻辑版本 key（不区分数据库）
        private const val KEY_LOGIC_VERSION = "migration_logic_version"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用于 updateState 的锁对象（独立于 prefs，避免持锁时间过长）。 */
    private val updateLock = Any()

    override fun getState(lib: LibName): MigrationState {
        val prefix = lib.name
        val statusName = prefs.getString("$prefix$FIELD_STATUS", null)
            ?: return MigrationState.initial(lib)
        val encryptionStatus = try {
            EncryptionStatus.valueOf(statusName)
        } catch (e: IllegalArgumentException) {
            // 未知状态值（如版本升级新增/删除枚举），回退到 PLAIN
            EncryptionStatus.PLAIN
        }
        return MigrationState(
            libName = lib.name,
            encryptionStatus = encryptionStatus,
            startedAtMs = prefs.getLong("$prefix$FIELD_STARTED_AT", -1).let { if (it < 0) null else it },
            snapshotPath = prefs.getString("$prefix$FIELD_SNAPSHOT_PATH", null),
            tempEncryptedPath = prefs.getString("$prefix$FIELD_TEMP_ENCRYPTED_PATH", null),
            progressPercent = prefs.getInt("$prefix$FIELD_PROGRESS", 0),
            currentTable = prefs.getString("$prefix$FIELD_CURRENT_TABLE", null),
            tablesTotal = prefs.getInt("$prefix$FIELD_TABLES_TOTAL", 0),
            tablesCompleted = prefs.getInt("$prefix$FIELD_TABLES_COMPLETED", 0),
            lastError = prefs.getString("$prefix$FIELD_LAST_ERROR", null),
            retryCount = prefs.getInt("$prefix$FIELD_RETRY_COUNT", 0),
        )
    }

    override fun setState(lib: LibName, state: MigrationState) {
        val prefix = lib.name
        val editor = prefs.edit()
        editor.putString("$prefix$FIELD_STATUS", state.encryptionStatus.name)
        if (state.startedAtMs != null) {
            editor.putLong("$prefix$FIELD_STARTED_AT", state.startedAtMs)
        } else {
            editor.remove("$prefix$FIELD_STARTED_AT")
        }
        if (state.snapshotPath != null) {
            editor.putString("$prefix$FIELD_SNAPSHOT_PATH", state.snapshotPath)
        } else {
            editor.remove("$prefix$FIELD_SNAPSHOT_PATH")
        }
        if (state.tempEncryptedPath != null) {
            editor.putString("$prefix$FIELD_TEMP_ENCRYPTED_PATH", state.tempEncryptedPath)
        } else {
            editor.remove("$prefix$FIELD_TEMP_ENCRYPTED_PATH")
        }
        editor.putInt("$prefix$FIELD_PROGRESS", state.progressPercent)
        if (state.currentTable != null) {
            editor.putString("$prefix$FIELD_CURRENT_TABLE", state.currentTable)
        } else {
            editor.remove("$prefix$FIELD_CURRENT_TABLE")
        }
        editor.putInt("$prefix$FIELD_TABLES_TOTAL", state.tablesTotal)
        editor.putInt("$prefix$FIELD_TABLES_COMPLETED", state.tablesCompleted)
        if (state.lastError != null) {
            editor.putString("$prefix$FIELD_LAST_ERROR", state.lastError)
        } else {
            editor.remove("$prefix$FIELD_LAST_ERROR")
        }
        editor.putInt("$prefix$FIELD_RETRY_COUNT", state.retryCount)
        // 使用 commit() 同步写入，确保状态持久化后才返回（迁移状态不可丢失）
        editor.commit()
    }

    override fun updateState(lib: LibName, update: (MigrationState) -> MigrationState) {
        synchronized(updateLock) {
            val current = getState(lib)
            val newState = update(current)
            setState(lib, newState)
        }
    }

    override fun clearState(lib: LibName) {
        val prefix = lib.name
        val editor = prefs.edit()
        editor.remove("$prefix$FIELD_STATUS")
        editor.remove("$prefix$FIELD_STARTED_AT")
        editor.remove("$prefix$FIELD_SNAPSHOT_PATH")
        editor.remove("$prefix$FIELD_TEMP_ENCRYPTED_PATH")
        editor.remove("$prefix$FIELD_PROGRESS")
        editor.remove("$prefix$FIELD_CURRENT_TABLE")
        editor.remove("$prefix$FIELD_TABLES_TOTAL")
        editor.remove("$prefix$FIELD_TABLES_COMPLETED")
        editor.remove("$prefix$FIELD_LAST_ERROR")
        editor.remove("$prefix$FIELD_RETRY_COUNT")
        editor.commit()
    }

    override fun getLogicVersion(): Int = prefs.getInt(KEY_LOGIC_VERSION, 0)

    override fun setLogicVersion(version: Int) {
        prefs.edit().putInt(KEY_LOGIC_VERSION, version).commit()
    }
}
