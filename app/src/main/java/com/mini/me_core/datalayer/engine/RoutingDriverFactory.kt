package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.db.SqlDriver

/**
 * 路由驱动工厂（设计文档 §8 / §12.2）。
 *
 * 根据每库的 [EncryptionStatus] 动态选择明文或加密驱动：
 * - [EncryptionStatus.ENCRYPTED] → [CipherDriverFactory]（SQLCipher 加密）
 * - 其他状态（PLAIN / 迁移中）→ [PlainDriverFactory]（明文 SQLite）
 *
 * P1 阶段默认所有库为 PLAIN，因此 RoutingDriverFactory 的行为与
 * 直接使用 PlainDriverFactory 完全一致，不破坏现有功能。
 *
 * 迁移中的状态（PRE_SNAPSHOT/MIGRATING/VALIDATING/REPLACING）应由
 * ConnectionPool.onPreOpen 先触发迁移完成（或 CrashRecovery 回滚），
 * 然后 RoutingDriverFactory 才能创建驱动。如果在迁移中状态调用 create()，
 * 会回退到明文驱动（此时主库仍是明文，安全）。
 *
 * @param context Application Context
 * @param pathProvider 数据库路径提供者
 * @param keyProvider 数据库密钥提供者（用于加密驱动）
 * @param stateStore 迁移状态存储（用于判断每库加密状态）
 */
class RoutingDriverFactory(
    private val context: Context,
    private val pathProvider: DatabasePathProvider,
    private val keyProvider: DatabaseKeyProvider,
    private val stateStore: MigrationStateStore,
) : DatabaseDriverFactory {

    private val plainFactory by lazy { PlainDriverFactory(context, pathProvider) }
    private val cipherFactory by lazy { CipherDriverFactory(context, pathProvider, keyProvider) }

    /**
     * 根据库的加密状态创建对应驱动。
     *
     * @param lib 数据库标识
     * @return SqlDriver 实例
     * @throws DatabaseEncryptionException 加密驱动创建失败时（fail-close，不静默降级）
     */
    override fun create(lib: LibName): SqlDriver {
        val status = stateStore.getState(lib).encryptionStatus
        return when (status) {
            EncryptionStatus.ENCRYPTED -> cipherFactory.create(lib)
            else -> plainFactory.create(lib)
        }
    }

    /**
     * 查询指定库当前是否为加密状态。
     *
     * @param lib 数据库标识
     * @return true 如果库已加密
     */
    fun isEncrypted(lib: LibName): Boolean =
        stateStore.getState(lib).encryptionStatus == EncryptionStatus.ENCRYPTED
}
