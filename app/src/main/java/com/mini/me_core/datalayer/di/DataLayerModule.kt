package com.mini.me_core.datalayer.di

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.backup.DatabaseBackupManager
import com.mini.me_core.datalayer.cleanup.DatabaseCleanupManager
import com.mini.me_core.datalayer.encryption.AndroidUnifiedKeyManager
import com.mini.me_core.datalayer.encryption.DatabaseRegistry
import com.mini.me_core.datalayer.encryption.EncryptedDatabaseManager
import com.mini.me_core.datalayer.encryption.EncryptedDriverFactory
import com.mini.me_core.datalayer.encryption.KeyRotationMigrator
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import com.mini.me_core.datalayer.encryption.registerBuiltinDatabases
import com.mini.me_core.datalayer.engine.AndroidDatabasePathProvider
import com.mini.me_core.datalayer.engine.AndroidVersionProbe
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.health.DatabaseHealthChecker
import com.mini.me_core.datalayer.migration.MigrationEngine
import com.mini.me_core.datalayer.migration.MigrationRejectedException
import com.mini.me_core.datalayer.migration.SchemaSelfHealer
import com.mini.me_core.datalayer.monitor.QueryPerformanceMonitor
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.datalayer.repository.CredentialsRepository
import com.mini.me_core.datalayer.repository.SettingsRepository
import com.mini.me_core.datalayer.repository.T2iRepository
import com.mini.me_core.datalayer.repository.WorkspaceRepository
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.InfraDb
import com.mini.mecore.datalayer.sqldelight.SettingsDb
import com.mini.mecore.datalayer.sqldelight.T2iDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb
import com.mini.me_core.datalayer.store.BlobStore
import com.mini.me_core.datalayer.store.DocumentStore
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.datalayer.store.Queue
import com.mini.me_core.datalayer.store.TimeSeries
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层 DI 模块（db-encryption-redesign 新架构）。
 *
 * 拓扑：6 个物理库，全部默认SQLCipher加密。
 * - DatabaseRegistry：注册表，启动时注册6个内置库定义
 * - UnifiedKeyManager：统一密钥管理（单一MasterKey + EncryptedSharedPreferences）
 * - EncryptedDriverFactory：SQLCipher加密驱动创建
 * - KeyRotationMigrator：密钥形态迁移（明文→加密；未来 DEK 轮换只需换 provider）
 * - EncryptedDatabaseManager：统一管理驱动获取和升级触发
 * - ConnectionPool：每库单连接holder，保留ensureSchema + SchemaSelfHealer钩子
 *
 * 保留：MigrationEngine（schema版本迁移）、DatabasePathProvider（快照/回滚）。
 * 移除：旧版双轨明文/加密路由、CrashRecovery、分散的密钥管理。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataLayerModule {

    private const val TAG = "DatalayerBootstrap"

    // ── 路径与Schema迁移（保留）──────────────────────────────────────────

    @Provides
    @Singleton
    fun providePathProvider(@ApplicationContext context: Context): DatabasePathProvider =
        AndroidDatabasePathProvider(context)

    @Provides
    @Singleton
    fun provideMigrationEngine(
        @ApplicationContext context: Context,
        pathProvider: DatabasePathProvider,
        keyManager: UnifiedKeyManager,
    ): MigrationEngine =
        // 6 库默认 SQLCipher 加密：探测必须加密感知（明文探测加密库会误报 SQLITE_NOTADB，
        // 并使 preOpen 恒判 FRESH、迁移前快照失效）。注入 UnifiedKeyManager 让探测能解密读版本。
        MigrationEngine(pathProvider, AndroidVersionProbe(context, pathProvider, keyManager))

    // ── 新加密架构 ──────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabaseRegistry(): DatabaseRegistry =
        DatabaseRegistry().also { registerBuiltinDatabases(it) }

    @Provides
    @Singleton
    fun provideUnifiedKeyManager(@ApplicationContext context: Context): UnifiedKeyManager =
        AndroidUnifiedKeyManager(context)

    @Provides
    @Singleton
    fun provideEncryptedDriverFactory(
        @ApplicationContext context: Context,
        keyManager: UnifiedKeyManager,
    ): EncryptedDriverFactory = EncryptedDriverFactory(context, keyManager)

    @Provides
    @Singleton
    fun provideKeyRotationMigrator(@ApplicationContext context: Context): KeyRotationMigrator =
        KeyRotationMigrator(context)

    @Provides
    @Singleton
    fun provideEncryptedDatabaseManager(
        registry: DatabaseRegistry,
        driverFactory: EncryptedDriverFactory,
        migrator: KeyRotationMigrator,
        keyManager: UnifiedKeyManager,
    ): EncryptedDatabaseManager =
        // 迁移形态由 provider 决定：换密钥体系只需换这里的 provider，迁移主体不动。
        EncryptedDatabaseManager(registry, driverFactory, migrator, KeyRotationMigrator.dekProvider(keyManager))

    // ── ConnectionPool（改为使用EncryptedDatabaseManager）──────────────

    /**
     * Schema 映射表：6 个库 → 各自的 SQLDelight Schema。
     * 供 ConnectionPool.onPreOpen/onOpened 回调在 driver 创建前后跑 ensureSchema 使用。
     */
    private val SCHEMA_MAP = mapOf(
        LibName.AGENT to AgentDb.Schema,
        LibName.CREDENTIALS to CredentialsDb.Schema,
        LibName.SETTINGS to SettingsDb.Schema,
        LibName.WORKSPACE to WorkspaceDb.Schema,
        LibName.T2I to T2iDb.Schema,
        LibName.INFRA to InfraDb.Schema,
    )

    @Provides
    @Singleton
    fun provideConnectionPool(
        encryptedManager: EncryptedDatabaseManager,
        engine: MigrationEngine,
    ): ConnectionPool {
        // 迁移前钩子：在 driver 创建之前，用原生只读连接探测真实 user_version，对旧版本库先快照保命。
        val preOpenHook: (LibName) -> Unit = preOpenHook@{ lib ->
            val schema = SCHEMA_MAP[lib]
            if (schema == null) {
                FileLogger.w(TAG, "未知 LibName=$lib，跳过 preOpen")
                return@preOpenHook
            }
            FileLogger.d(TAG, "preOpen($lib) target=${schema.version}")
            try {
                engine.preOpen(lib, schema)
                FileLogger.v(TAG, "preOpen($lib) 完成")
            } catch (t: Throwable) {
                // H5：致命拒绝必须终止，不能像"快照失败"那样记个日志就继续。
                //   · VERSION_DOWNGRADE：低版本 schema 打开高版本库 = 必然损坏；
                //   · UNREADABLE_NO_SAFETY_COPY：坏库现场没保住就隔离重建 = 静默清空。
                if (t is MigrationRejectedException) {
                    FileLogger.e(TAG, "preOpen($lib) 致命拒绝（${t.reason}），终止启动", t)
                    throw t
                }
                FileLogger.e(TAG, "preOpen($lib) 失败（非致命，driver 打开后由 ensureSchema 兜底）", t)
            }
        }

        val hook: (LibName, app.cash.sqldelight.db.SqlDriver) -> Unit = hook@{ lib, driver ->
            val schema = SCHEMA_MAP[lib]
            if (schema == null) {
                FileLogger.w(TAG, "未知 LibName=$lib，跳过 ensureSchema")
                return@hook
            }
            FileLogger.d(TAG, "ensureSchema($lib) target=${schema.version}")
            try {
                engine.ensureSchema(lib, driver, schema)
                FileLogger.v(TAG, "ensureSchema($lib) 完成")
            } catch (t: Throwable) {
                // 同 preOpen：版本回退等致命拒绝必须上抛（H5），其余留给下次打开重试。
                if (t is MigrationRejectedException) {
                    FileLogger.e(TAG, "ensureSchema($lib) 致命拒绝（${t.reason}），终止启动", t)
                    throw t
                }
                FileLogger.e(TAG, "ensureSchema($lib) 失败（忽略，下次打开重试）", t)
            }

            // 对 AGENT 库额外跑 SchemaSelfHealer 自愈
            if (lib == LibName.AGENT) {
                FileLogger.d(TAG, "开始 AGENT 库结构自愈")
                runCatching {
                    SchemaSelfHealer.healAgentSession(driver)
                    SchemaSelfHealer.healAgentMessage(driver)
                    SchemaSelfHealer.ensureAgentMessageUsable(driver)
                    SchemaSelfHealer.ensureAgentSessionUsable(driver)
                    FileLogger.d(TAG, "AGENT 库结构自愈完成")
                }.onFailure {
                    FileLogger.e(TAG, "AGENT 库结构自愈失败（FATAL）", it)
                    throw it
                }
            }
        }
        // 打开守卫：driver 创建（含加密形态迁移、schema 迁移）失败时自动回滚到迁移前快照，
        // 避免主库停在「迁移半成品」状态——那正是过去读到 file is not a database 的来源之一。
        val openGuard: (LibName, () -> app.cash.sqldelight.db.SqlDriver) -> app.cash.sqldelight.db.SqlDriver =
            { lib, open -> engine.withSnapshotGuard(lib) { open() } }

        // A1：慢查询监控接线。过去 MonitoringDriverWrapper 全仓无注入点 → 采集链路整体悬空。
        // 这里在自愈完成后（onOpened 之后、写缓存之前）按需包装 driver，把每次 execute/executeQuery 的
        // 耗时上报 QueryPerformanceMonitor。开关与 QueryPerformanceMonitor.enabled 一致，
        // release 下可由上层置 false 关闭（零开销）。
        val wrapDriver: (LibName, app.cash.sqldelight.db.SqlDriver) -> app.cash.sqldelight.db.SqlDriver =
            { lib, driver ->
                if (QueryPerformanceMonitor.enabled) {
                    com.mini.me_core.datalayer.monitor.MonitoringDriverWrapper(driver, lib.dbId)
                } else {
                    driver
                }
            }

        return ConnectionPool(
            encryptedManager = encryptedManager,
            onPreOpen = preOpenHook,
            onOpened = hook,
            openGuard = openGuard,
            wrapDriver = wrapDriver,
        )
    }

    // ── 6 个 Database：从 ConnectionPool 获取加密驱动 ──────────────────

    @Provides
    @Singleton
    fun provideAgentDb(pool: ConnectionPool, engine: MigrationEngine): AgentDb {
        val driver = pool.driver(LibName.AGENT)
        engine.ensureSchema(LibName.AGENT, driver, AgentDb.Schema)
        SchemaSelfHealer.healAgentSession(driver)
        SchemaSelfHealer.healAgentMessage(driver)
        SchemaSelfHealer.ensureAgentMessageUsable(driver)
        SchemaSelfHealer.ensureAgentSessionUsable(driver)
        return AgentDb(driver)
    }

    @Provides
    @Singleton
    fun provideCredentialsDb(pool: ConnectionPool, engine: MigrationEngine): CredentialsDb {
        val driver = pool.driver(LibName.CREDENTIALS)
        engine.ensureSchema(LibName.CREDENTIALS, driver, CredentialsDb.Schema)
        return CredentialsDb(driver)
    }

    @Provides
    @Singleton
    fun provideSettingsDb(pool: ConnectionPool, engine: MigrationEngine): SettingsDb {
        val driver = pool.driver(LibName.SETTINGS)
        engine.ensureSchema(LibName.SETTINGS, driver, SettingsDb.Schema)
        return SettingsDb(driver)
    }

    @Provides
    @Singleton
    fun provideWorkspaceDb(pool: ConnectionPool, engine: MigrationEngine): WorkspaceDb {
        val driver = pool.driver(LibName.WORKSPACE)
        engine.ensureSchema(LibName.WORKSPACE, driver, WorkspaceDb.Schema)
        return WorkspaceDb(driver)
    }

    @Provides
    @Singleton
    fun provideT2iDb(pool: ConnectionPool, engine: MigrationEngine): T2iDb {
        val driver = pool.driver(LibName.T2I)
        engine.ensureSchema(LibName.T2I, driver, T2iDb.Schema)
        return T2iDb(driver)
    }

    @Provides
    @Singleton
    fun provideInfraDb(pool: ConnectionPool, engine: MigrationEngine): InfraDb {
        val driver = pool.driver(LibName.INFRA)
        engine.ensureSchema(LibName.INFRA, driver, InfraDb.Schema)
        return InfraDb(driver)
    }

    // ── 5 个一等 Store ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideKVStore(db: InfraDb): KVStore = KVStore(db)

    @Provides
    @Singleton
    fun provideDocumentStore(db: InfraDb, pool: ConnectionPool): DocumentStore =
        DocumentStore(db, pool.driver(LibName.INFRA))

    @Provides
    @Singleton
    fun provideQueue(db: InfraDb): Queue = Queue(db)

    @Provides
    @Singleton
    fun provideBlobStore(db: InfraDb, @ApplicationContext context: Context): BlobStore =
        // M10：落盘目录用应用私有 filesDir，与 DB 同属进程私有、随卸载清理；
        // 大对象写此处、DB 仅存相对路径，缩短单连接写锁占用。
        BlobStore(db, context.filesDir)

    @Provides
    @Singleton
    fun provideTimeSeries(db: InfraDb): TimeSeries = TimeSeries(db)

    // ── 5 个域 Repository ───────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAgentRepository(db: AgentDb): AgentRepository = AgentRepository(db)

    @Provides
    @Singleton
    fun provideWakeQueueStore(agent: AgentRepository): com.mini.me_core.datalayer.repository.WakeQueueStore = agent

    @Provides
    @Singleton
    fun provideCredentialsRepository(db: CredentialsDb): CredentialsRepository = CredentialsRepository(db)

    @Provides
    @Singleton
    fun provideSettingsRepository(db: SettingsDb): SettingsRepository = SettingsRepository(db)

    @Provides
    @Singleton
    fun provideWorkspaceRepository(db: WorkspaceDb): WorkspaceRepository = WorkspaceRepository(db)

    @Provides
    @Singleton
    fun provideT2iRepository(db: T2iDb): T2iRepository = T2iRepository(db)

    // ── P2 扩展：健康检查 / 备份恢复 / 清理 / 性能监控 ───────────────────

    @Provides
    @Singleton
    fun provideDatabaseHealthChecker(
        @ApplicationContext context: Context,
        registry: DatabaseRegistry,
        pool: ConnectionPool,
    ): DatabaseHealthChecker = DatabaseHealthChecker(context, registry, pool)

    @Provides
    @Singleton
    fun provideDatabaseBackupManager(
        @ApplicationContext context: Context,
        registry: DatabaseRegistry,
        pool: ConnectionPool,
        keyManager: UnifiedKeyManager,
    ): DatabaseBackupManager =
        // 注入 keyManager：恢复后的完整性校验走「只读打开」通道，必须能取到本机 DEK；
        // 取不到（跨设备备份）即判定为无法解密并回滚，而不是绕过校验（审计 F4 / M5）。
        DatabaseBackupManager(context, registry, pool, keyManager)

    @Provides
    @Singleton
    fun provideDatabaseCleanupManager(
        pool: ConnectionPool,
    ): DatabaseCleanupManager = DatabaseCleanupManager(pool)

    /**
     * 性能监控器是单例 object，这里提供一个 @Provides 方法便于 Hilt 注入。
     * release 构建可在调用方设置 QueryPerformanceMonitor.enabled = false 禁用。
     */
    @Provides
    @Singleton
    fun provideQueryPerformanceMonitor(): QueryPerformanceMonitor = QueryPerformanceMonitor
}
