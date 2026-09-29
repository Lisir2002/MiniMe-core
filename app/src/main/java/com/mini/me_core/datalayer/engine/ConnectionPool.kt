package com.mini.me_core.datalayer.engine

import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.EncryptedDatabaseManager

/**
 * 连接池（设计 §12.1）。
 *
 * 当前为「单连接 holder」：每库一个 SqlDriver（WAL + synchronous=FULL，读写同连接），
 * 通过此接口暴露，守护单写者、零并发写风险。
 *
 * v2-full-takeover P3：在首次创建 driver 后、返回给任何调用者之前，触发 [onOpened] 回调，
 * 确保 MigrationEngine.ensureSchema + SchemaSelfHealer 自愈总是在业务查询之前完成。
 *
 * 新架构（db-encryption-redesign）：driver 创建统一走 [EncryptedDatabaseManager]，
 * 所有数据库默认加密，无明文/加密路由。
 *
 * ⚠️ **缓存写入顺序（H4）**：必须「先自愈成功、再 put 缓存」。
 * 过去的写法是 `drivers[lib] = created` 之后才 `onOpened?.invoke()`，一旦自愈/ensureSchema
 * 抛异常，缓存里留下的是**未自愈的半成品 driver**，而异常被上层记为 warn 后继续运行；
 * 之后每次 `pool.driver(lib)` 都直接命中缓存返回，preOpen / ensureSchema / 自愈**永久跳过**，
 * 表现为「启动日志干净，但数据就是不对」。现在失败即关闭连接、不写缓存、异常继续上抛，
 * 下次访问会重新走完整流程（自愈可重试）。
 *
 * @param openGuard 包裹「driver 创建」的守卫（默认 null = 不守卫）。由
 *   [com.mini.me_core.datalayer.migration.MigrationEngine.withSnapshotGuard] 提供：
 *   打开前快照、打开失败（含迁移失败）自动回滚，避免停在迁移半成品状态。
 */
class ConnectionPool(
    private val encryptedManager: EncryptedDatabaseManager,
    private val onPreOpen: ((LibName) -> Unit)? = null,
    private val onOpened: ((LibName, SqlDriver) -> Unit)? = null,
    private val openGuard: ((LibName, () -> SqlDriver) -> SqlDriver)? = null,
) {

    private val drivers = mutableMapOf<LibName, SqlDriver>()

    @Synchronized
    fun driver(lib: LibName): SqlDriver {
        val existing = drivers[lib]
        if (existing != null) return existing
        // 迁移前探测 + 快照：必须在 driver 创建之前
        onPreOpen?.invoke(lib)
        // 通过加密数据库管理器创建驱动（内部处理明文→加密升级迁移）
        // dbId（而非 name.lowercase()）：与 DatabaseDefinition.id 同一真源，且与设备区域无关
        val open: () -> SqlDriver = { encryptedManager.getDriverBlocking(lib.dbId) }
        val created = openGuard?.invoke(lib, open) ?: open()
        // 先自愈、后缓存：失败即关连接，绝不留半成品在缓存里（H4）
        try {
            // 立即对齐 schema + 自愈，再让任何业务代码访问。
            onOpened?.invoke(lib, created)
        } catch (t: Throwable) {
            runCatching { created.close() }
            FileLogger.e(
                TAG,
                "onOpened($lib) 失败：已关闭该连接且不写入缓存，下次访问将重跑 preOpen/ensureSchema/自愈",
                t,
            )
            throw t
        }
        drivers[lib] = created
        return created
    }

    @Synchronized
    fun closeAll() {
        drivers.values.forEach { runCatching { it.close() } }
        drivers.clear()
    }

    companion object {
        private const val TAG = "ConnectionPool"
    }
}
