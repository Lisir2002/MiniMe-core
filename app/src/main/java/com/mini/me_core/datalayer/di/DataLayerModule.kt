package com.mini.me_core.datalayer.di

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.backup.DatabaseBackupManager
import com.mini.me_core.datalayer.cleanup.DatabaseCleanupManager
import com.mini.me_core.datalayer.encryption.AndroidUnifiedKeyManager
import com.mini.me_core.datalayer.encryption.DatabaseRegistry
import com.mini.me_core.datalayer.encryption.EncryptedDatabaseManager
import com.mini.me_core.datalayer.encryption.EncryptedDriverFactory
import com.mini.me_core.datalayer.encryption.LegacyMigrationEngine
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import com.mini.me_core.datalayer.encryption.registerBuiltinDatabases
import com.mini.me_core.datalayer.engine.AndroidDatabasePathProvider
import com.mini.me_core.datalayer.engine.AndroidVersionProbe
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.health.DatabaseHealthChecker
import com.mini.me_core.datalayer.migration.MigrationEngine
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
 * - LegacyMigrationEngine：旧版明文库→加密库逐表事务拷贝迁移
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
    fun provideMigrationEngine(pathProvider: DatabasePathProvider): MigrationEngine =
        MigrationEngine(pathProvider, AndroidVersionProbe(pathProvider))

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
    fun provideLegacyMigrationEngine(
        @ApplicationContext context: Context,
        keyManager: UnifiedKeyManager,
    ): LegacyMigrationEngine = LegacyMigrationEngine(context, keyManager)

    @Provides
    @Singleton
    fun provideEncryptedDatabaseManager(
        @ApplicationContext context: Context,
        registry: DatabaseRegistry,
        driverFactory: EncryptedDriverFactory,
        migrationEngine: LegacyMigrationEngine,
    ): EncryptedDatabaseManager =
        EncryptedDatabaseManager(context, registry, driverFactory, migrationEngine)

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
            runCatching {
                engine.preOpen(lib, schema)
                FileLogger.v(TAG, "preOpen($lib) 完成")
            }.onFailure {
                FileLogger.e(TAG, "preOpen($lib) 失败（忽略，driver 打开后由 ensureSchema 兜底）", it)
            }
        }

        val hook: (LibName, app.cash.sqldelight.db.SqlDriver) -> Unit = hook@{ lib, driver ->
            val schema = SCHEMA_MAP[lib]
            if (schema == null) {
                FileLogger.w(TAG, "未知 LibName=$lib，跳过 ensureSchema")
                return@hook
            }
            FileLogger.d(TAG, "ensureSchema($lib) target=${schema.version}")
            runCatching {
                engine.ensureSchema(lib, driver, schema)
                FileLogger.v(TAG, "ensureSchema($lib) 完成")
            }.onFailure {
                FileLogger.e(TAG, "ensureSchema($lib) 失败（忽略，下次打开重试）", it)
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
        return ConnectionPool(encryptedManager, preOpenHook, hook)
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
    fun provideBlobStore(db: InfraDb): BlobStore = BlobStore(db)

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
    ): DatabaseBackupManager = DatabaseBackupManager(context, registry, pool)

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
