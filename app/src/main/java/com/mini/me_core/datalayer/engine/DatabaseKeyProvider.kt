package com.mini.me_core.datalayer.engine

import app.cash.sqldelight.db.SqlDriver

/**
 * 数据库密钥提供者（设计文档 db-encryption-migration-design.md §4）。
 *
 * 安全约定（严格遵守，不可违反）：
 * - [getPassphrase] 返回的 [ByteArray] 调用方必须在使用后 `fill(0)` 擦除
 * - 任何密钥获取失败必须抛异常，绝不返回空值或默认值（fail-close 原则）
 * - 密钥不在日志中输出（包括异常消息中不包含密钥内容、不包含包裹密文内容）
 * - 每库独立 DEK，一个库的 DEK 泄露不影响其他库
 * - DEK 明文不落盘，SharedPreferences 只存 MasterKey 包裹后的密文
 */
interface DatabaseKeyProvider {

    /**
     * 获取指定数据库的加密口令（DEK 原始字节，AES-256，32 字节）。
     *
     * 首次调用时生成随机 DEK，用 Android Keystore MasterKey（AES/GCM）包裹后
     * 持久化到私有 SharedPreferences；后续调用解包返回同一 DEK。
     *
     * @param lib 数据库标识（6 库之一，每库独立 DEK）
     * @return DEK 原始字节（32 字节），调用方使用后**必须** `fill(0)` 擦除
     * @throws DatabaseEncryptionException 密钥生成 / 解包 / 持久化失败时（绝不返回 null 或空数组）
     */
    suspend fun getPassphrase(lib: LibName): ByteArray

    /**
     * 轮换指定数据库的 DEK。
     *
     * 生成新 DEK，通过 SQLCipher `PRAGMA rekey` 用新密钥重新加密整个数据库，
     * 然后用 MasterKey 包裹新 DEK 并持久化（覆盖旧包裹密文）。
     * 轮换失败时数据库保持旧密钥可用（原子性由 SQLCipher rekey 保证）。
     *
     * @param lib 数据库标识
     * @param driver 已打开的加密数据库驱动（必须处于加密状态，否则 rekey 无意义）
     * @throws DatabaseEncryptionException 轮换失败时
     */
    suspend fun rotatePassphrase(lib: LibName, driver: SqlDriver)

    /**
     * 检查指定数据库的 DEK 是否已初始化（包裹密文是否存在于 SharedPreferences）。
     *
     * 注意：返回 true 仅表示包裹密文存在，不保证解包一定成功
     * （如 MasterKey 被删除、Keystore 不可用等，解包仍会抛异常）。
     *
     * @param lib 数据库标识
     * @return true 表示该库已有 DEK 包裹密文；false 表示尚未初始化
     */
    fun isInitialized(lib: LibName): Boolean

    /**
     * 紧急清除：删除所有 DEK 包裹密文。
     *
     * **警告**：调用后所有加密数据库将永久不可读（DEK 已无法恢复）。
     * 仅在用户明确要求重置全部数据时使用，调用前必须有二次确认 UI。
     * MasterKey 仍保留在 Android Keystore 中（可用于未来生成新 DEK），
     * 但所有已存在的 DEK 包裹密文被清除。
     *
     * @throws DatabaseEncryptionException 清除失败时
     */
    suspend fun emergencyReset()
}

/**
 * 数据库加密相关异常（设计文档 §4.2 / §5.1）。
 *
 * 所有密钥操作、驱动创建、迁移失败均抛出此异常（或其子类），
 * 严格遵循 fail-close 原则——绝不静默降级为明文或返回默认值。
 *
 * 异常消息中**禁止**包含任何密钥内容（passphrase / DEK / 包裹密文 / MasterKey 别名以外的敏感数据）。
 */
class DatabaseEncryptionException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)
