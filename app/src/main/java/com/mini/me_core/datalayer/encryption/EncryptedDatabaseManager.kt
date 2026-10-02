package com.mini.me_core.datalayer.encryption

import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 加密数据库管理器。
 *
 * 统一管理所有数据库的生命周期：
 * - 首次创建：直接创建加密数据库
 * - 形态迁移：检测到仍处于源形态（旧版明文库）的库，触发 [KeyRotationMigrator]
 * - 驱动创建：统一使用SQLCipher加密驱动
 *
 * 所有数据库默认加密，不再有明文/加密路由选择。
 *
 * 迁移的**形态**由注入的 provider 决定（[KeyRotationMigrator.PassphraseProvider]），
 * 本类不感知密钥细节：将来换密钥体系只需在 DI 处换 provider，不改这里。
 */
class EncryptedDatabaseManager(
    private val registry: DatabaseRegistry,
    private val driverFactory: EncryptedDriverFactory,
    private val migrator: KeyRotationMigrator,
    private val targetPassphrase: KeyRotationMigrator.PassphraseProvider,
) {

    private companion object {
        const val TAG = "EncryptedDbManager"
    }

    /**
     * 获取指定数据库的SqlDriver（suspend版本）。
     * 如检测到仍为源形态（旧版明文库），先执行升级加密。
     */
    suspend fun getDriver(dbId: String): SqlDriver {
        val definition = registry.get(dbId)
            ?: throw IllegalArgumentException("数据库未注册: $dbId")

        // 检测并执行形态迁移（懒加载，数据库被访问时才触发）
        if (migrator.needsMigration(definition)) {
            FileLogger.i(TAG, "检测到源形态库，开始明文→加密迁移: $dbId")
            migrator.migrate(definition, KeyRotationMigrator.PLAIN, targetPassphrase)
        }

        return driverFactory.create(definition)
    }

    /**
     * 阻塞版本的getDriver（供ConnectionPool在非协程上下文中调用）。
     *
     * 此处必须阻塞是因为 [ConnectionPool.driver] 是同步函数（@Synchronized），
     * 被 DI Provider、启动初始化等非协程路径调用。runBlocking 切到 IO 线程执行
     * DEK 读取与驱动创建，避免加密计算占用调用方线程。
     *
     * 注意：若调用方处于主线程（如 Application.onCreate 的首屏 DB 预热），
     * 此函数会短暂阻塞主线程等待 IO 完成。正常情况下打开已有库仅需毫秒级
     * PRAGMA 检查，可接受；首次创建或迁移时耗时较长，应确保不在主线程触发。
     */
    fun getDriverBlocking(dbId: String): SqlDriver {
        return runBlocking(Dispatchers.IO) {
            getDriver(dbId)
        }
    }

    /**
     * 检查指定数据库是否已完成迁移（用于状态查询）。
     */
    fun isEncrypted(dbId: String): Boolean {
        val definition = registry.get(dbId) ?: return false
        return !migrator.needsMigration(definition)
    }
}
