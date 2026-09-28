package com.mini.me_core.datalayer.engine

import app.cash.sqldelight.db.SqlDriver
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
        val open: () -> SqlDriver = { encryptedManager.getDriverBlocking(lib.name.lowercase()) }
        val created = openGuard?.invoke(lib, open) ?: open()
        drivers[lib] = created
        // 立即对齐 schema + 自愈，再让任何业务代码访问。
        onOpened?.invoke(lib, created)
        return created
    }

    @Synchronized
    fun closeAll() {
        drivers.values.forEach { runCatching { it.close() } }
        drivers.clear()
    }
}
