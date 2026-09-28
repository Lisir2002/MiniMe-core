package com.mini.me_core.datalayer.encryption

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * 加密数据库管理器。
 *
 * 统一管理所有数据库的生命周期：
 * - 首次创建：直接创建加密数据库
 * - 升级检测：检测旧版文明文数据库，触发LegacyMigration
 * - 驱动创建：统一使用SQLCipher加密驱动
 *
 * 所有数据库默认加密，不再有明文/加密路由选择。
 */
class EncryptedDatabaseManager(
    private val context: Context,
    private val registry: DatabaseRegistry,
    private val driverFactory: EncryptedDriverFactory,
    private val migrationEngine: LegacyMigrationEngine,
) {

    private companion object {
        const val TAG = "EncryptedDbManager"
    }

    /**
     * 获取指定数据库的SqlDriver（suspend版本）。
     * 如检测到旧版文明文数据库，先执行升级加密。
     */
    suspend fun getDriver(dbId: String): SqlDriver {
        val definition = registry.get(dbId)
            ?: throw IllegalArgumentException("数据库未注册: $dbId")

        // 检测并执行旧版升级（懒加载，数据库被访问时才触发）
        if (migrationEngine.needsMigration(definition)) {
            FileLogger.i(TAG, "检测到旧版明文库，开始升级加密: $dbId")
            migrationEngine.migrateToEncrypted(definition)
        }

        return driverFactory.createBlocking(definition)
    }

    /**
     * 阻塞版本的getDriver（供ConnectionPool在非协程上下文中调用）。
     * 内部使用runBlocking切换到IO线程。
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
        return !migrationEngine.needsMigration(definition)
    }
}
