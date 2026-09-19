package com.mini.me_core.datalayer.di

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.AndroidDatabasePathProvider
import com.mini.me_core.datalayer.engine.AndroidVersionProbe
import com.mini.me_core.datalayer.engine.ConnectionPool
import com.mini.me_core.datalayer.engine.CipherDriverFactory
import com.mini.me_core.datalayer.engine.DatabaseDriverFactory
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import com.mini.me_core.datalayer.engine.PlainDriverFactory
import com.mini.me_core.datalayer.engine.SqlCipherKeyManager
import com.mini.me_core.datalayer.migration.DatabaseHealthGuard
import com.mini.me_core.datalayer.migration.MigrationEngine
import com.mini.me_core.datalayer.migration.SchemaSelfHealer
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.datalayer.repository.CredentialsRepository
import com.mini.me_core.datalayer.repository.SettingsRepository
import com.mini.me_core.datalayer.repository.T2iRepository
import com.mini.me_core.datalayer.repository.WorkspaceRepository
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.AuxDb
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
 * 新数据层（data-layer-redesign）DI 模块（设计 §12：L0 引擎）。
 *
 * 拓扑：6 个物理库（5 核心域 + 1 infra），每库独立 Database 类与版本链；
 * 每库打开时经 [MigrationEngine.ensureSchema] 完成「全新建库 / 版本迁移 + 快照安全网」。
 *
 * v2-full-takeover P3-紧急加固：ConnectionPool 在首次创建 driver 后、返回给任何调用者之前，
 * 立刻触发 onOpened 回调，对该库跑 ensureSchema + 必要时 SchemaSelfHealer 自愈。
 * 这解决了「DataRegistryModule.provideDataProviders 先于 provideAgentDb 拿到 driver
 * → ensureSchema + 自愈未执行 → 业务查询遇到缺列的旧表 → 启动即崩」的竞态窗口。
 *
 * 加密插拔（设计 §8 / §12.2）：自测期绑定 [PlainDriverFactory]（明文）；
 * 未来启用 SQLCipher 只需把 [provideDriverFactory] 的返回换成 [CipherDriverFactory]，
 * 业务 / 迁移 / 备份零感知。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataLayerModule {

    private const val TAG = "DatalayerBootstrap"

    // ── L0 引擎 ──────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePathProvider(@ApplicationContext context: Context): DatabasePathProvider =
        AndroidDatabasePathProvider(context)

    @Provides
    @Singleton
    fun provideSqlCipherKeyManager(@ApplicationContext context: Context): SqlCipherKeyManager =
        SqlCipherKeyManager(context)

    @Provides
    @Singleton
    fun provideDriverFactory(
        @ApplicationContext context: Context,
        pathProvider: DatabasePathProvider,
        keyManager: SqlCipherKeyManager,
    ): DatabaseDriverFactory =
        // 全盘加密接线（设计 §8 / §12.2）：由 PlainDriverFactory 切换为 CipherDriverFactory。
        // 明文库 → 加密库的事务化迁移由 SqlCipherMigration 在 preOpen 阶段完成（快照/校验/回退）。
        CipherDriverFactory(context, pathProvider, keyManager)

    /**
     * Schema 映射表：6 个库 → 各自的 SQLDelight Schema。
     * 供 ConnectionPool.onOpened 回调在 driver 创建时立即 ensureSchema 使用——
     * 不依赖具体库的 DI（比如 AgentDb），避免 provideAgentDb 的 provideDataProviders
     * 之间出现注入顺序竞态。
     */
    private val SCHEMA_MAP = mapOf(
        LibName.AGENT to AgentDb.Schema,
        LibName.CREDENTIALS to CredentialsDb.Schema,
        LibName.WORKSPACE to WorkspaceDb.Schema,
        LibName.AUX to AuxDb.Schema,
    )

    @Provides
    @Singleton
    fun provideConnectionPool(
        factory: DatabaseDriverFactory,
        engine: MigrationEngine,
    ): ConnectionPool {
        // 迁移前钩子：在 factory.create（AndroidSqliteDriver 构造，会立即打开并迁移）之前，
        // 用原生只读连接探测真实 user_version，对旧版本库先快照保命。
        // 若漏调，driver 打开后 ensureSchema 仍有 codeMigrations 兜底，但快照安全网会失效——故必须此处先跑。
        val preOpenHook: (LibName) -> Unit = preOpenHook@{ lib ->
            val schema = SCHEMA_MAP[lib]
            if (schema == null) {
                FileLogger.w(TAG, "未知 LibName=$lib，跳过 preOpen")
                return@preOpenHook
            }
            FileLogger.i(TAG, "preOpen($lib) target=${schema.version}")
            runCatching {
                engine.preOpen(lib, schema)
                FileLogger.i(TAG, "preOpen($lib) 完成")
            }.onFailure {
                // preOpen 失败（如只读探测异常）：不阻断启动，driver 仍会打开并由 ensureSchema 兜底。
                FileLogger.e(TAG, "preOpen($lib) 失败（忽略，driver 打开后由 ensureSchema 兜底）", it)
            }
        }

        val hook: (LibName, app.cash.sqldelight.db.SqlDriver) -> Unit = hook@{ lib, driver ->
            val schema = SCHEMA_MAP[lib]
            if (schema == null) {
                FileLogger.w(TAG, "未知 LibName=$lib，跳过 ensureSchema")
                return@hook
            }
            FileLogger.i(TAG, "ensureSchema($lib) target=${schema.version}")
            runCatching {
                engine.ensureSchema(lib, driver, schema)
                FileLogger.i(TAG, "ensureSchema($lib) 完成")
            }.onFailure {
                FileLogger.e(TAG, "ensureSchema($lib) 失败（忽略，下次打开重试）", it)
            }

            // ARC-07：对全部 4 个库跑 PRAGMA quick_check；失败计数 + 泛化自愈，
            // 连续 N 次失败自动 restoreSnapshot（不再 runCatching 静默忽略）。
            runCatching { DatabaseHealthGuard.onOpened(lib, driver, engine) }
                .onFailure { FileLogger.e(TAG, "$lib 健康守卫异常", it) }

            // AGENT 库额外做关键列硬保证（agent_message.id / agent_session.id）。
            if (lib == LibName.AGENT) {
                FileLogger.i(TAG, "开始 AGENT 库关键列硬保证")
                runCatching {
                    SchemaSelfHealer.ensureAgentMessageUsable(driver)
                    SchemaSelfHealer.ensureAgentSessionUsable(driver)
                }.onFailure {
                    FileLogger.e(TAG, "AGENT 库关键列硬保证失败（FATAL）", it)
                    throw it
                }
            }
        }
        return ConnectionPool(factory, preOpenHook, hook)
    }

    @Provides
    @Singleton
    fun provideMigrationEngine(pathProvider: DatabasePathProvider): MigrationEngine =
        MigrationEngine(pathProvider, AndroidVersionProbe(pathProvider))

    // ── 6 个 Database：ConnectionPool.onOpened 已确保 ensureSchema + 自愈先跑，
    //   这里再调一遍是幂等安全网（provideAgentDb 里的自愈会在 ConnectionPool 之后再跑一次，
    //   但对已修好的表只会做一次「结构完好，跳过」的幂等检查）───────────────────────

    @Provides
    @Singleton
    fun provideAgentDb(pool: ConnectionPool, engine: MigrationEngine): AgentDb {
        val driver = pool.driver(LibName.AGENT)
        // ConnectionPool.onOpened 已跑过 ensureSchema + 自愈，这里再补一遍幂等复核，
        // 且无论 onOpened 是否执行（理论上 AGENT 一定执行过），都保证 provideAgentDb 返回的
        // AgentDb 所操作的库一定是结构完整的。
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
    fun provideAuxDb(pool: ConnectionPool, engine: MigrationEngine): AuxDb {
        val driver = pool.driver(LibName.AUX)
        engine.ensureSchema(LibName.AUX, driver, AuxDb.Schema)
        return AuxDb(driver)
    }

    @Provides
    @Singleton
    fun provideWorkspaceDb(pool: ConnectionPool, engine: MigrationEngine): WorkspaceDb {
        val driver = pool.driver(LibName.WORKSPACE)
        engine.ensureSchema(LibName.WORKSPACE, driver, WorkspaceDb.Schema)
        return WorkspaceDb(driver)
    }

    // ── 5 个一等 Store（设计 §6）─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideKVStore(db: AuxDb): KVStore = KVStore(db)

    @Provides
    @Singleton
    fun provideDocumentStore(db: AuxDb, pool: ConnectionPool): DocumentStore =
        DocumentStore(db, pool.driver(LibName.AUX))

    @Provides
    @Singleton
    fun provideQueue(db: AuxDb): Queue = Queue(db)

    @Provides
    @Singleton
    fun provideBlobStore(db: AuxDb): BlobStore = BlobStore(db)

    @Provides
    @Singleton
    fun provideTimeSeries(db: AuxDb): TimeSeries = TimeSeries(db)

    // ── 5 个域 Repository（设计 §11 / L2 门面）────────────────────────────

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
    fun provideSettingsRepository(db: AuxDb): SettingsRepository = SettingsRepository(db)

    @Provides
    @Singleton
    fun provideWorkspaceRepository(db: WorkspaceDb): WorkspaceRepository = WorkspaceRepository(db)

    @Provides
    @Singleton
    fun provideT2iRepository(db: AuxDb): T2iRepository = T2iRepository(db)
}
