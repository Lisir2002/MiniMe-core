package com.mini.me_core.datalayer.engine

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.mini.me_core.core.util.FileLogger
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.CredentialsDb
import com.mini.mecore.datalayer.sqldelight.InfraDb
import com.mini.mecore.datalayer.sqldelight.SettingsDb
import com.mini.mecore.datalayer.sqldelight.T2iDb
import com.mini.mecore.datalayer.sqldelight.WorkspaceDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory

/**
 * SQLCipher 加密驱动工厂（设计文档 db-encryption-migration-design.md §5.1）。
 *
 * 使用 SQLCipher for Android（基于 SQLite 3.41.2 + OpenSSL）提供全库透明加密：
 * - 数据库页加密：AES-256-CBC + HMAC-SHA512（SQLCipher 默认）
 * - 密钥派生：PBKDF2（默认 64000 轮 SHA-512，由 SQLCipher 内部完成）
 * - passphrase 来源：[DatabaseKeyProvider] 提供的 per-DB DEK（Base64 编码后传入）
 *
 * 安全约定（严格遵守）：
 * - [create] 中任何异常/错误都向上抛出，**绝不回退到明文驱动**（fail-close）
 * - DEK 原始字节使用后立即 `fill(0)` 擦除
 * - passphrase 字节使用后立即 `fill(0)` 擦除（SQLCipher clearPassphrase 也会自动擦除）
 * - 不在日志中输出 passphrase / DEK / 密钥内容（异常消息只含库名）
 * - 捕获 [UnsatisfiedLinkError]（SQLCipher 原生库加载失败）并包装为 [DatabaseEncryptionException]
 *
 * @param context Application Context
 * @param pathProvider 数据库路径提供者（当前未直接使用，保留接口一致性）
 * @param keyProvider 数据库密钥提供者（用于获取 per-DB DEK）
 */
class CipherDriverFactory(
    private val context: Context,
    @Suppress("unused") private val pathProvider: DatabasePathProvider,
    private val keyProvider: DatabaseKeyProvider,
) : DatabaseDriverFactory {

    private companion object {
        const val TAG = "CipherDriverFactory"
    }

    override fun create(lib: LibName): SqlDriver {
        // 必须在 IO 线程获取密钥（Keystore 操作可能阻塞）
        // getPassphrase 返回 DEK 原始字节，调用方负责 fill(0) 擦除
        val dek = runBlocking(Dispatchers.IO) {
            keyProvider.getPassphrase(lib)
        }
        // 将 DEK 编码为 Base64 String，再转 UTF-8 字节，作为 SQLCipher passphrase。
        // SQLCipher 4.5.4 SupportFactory 只接受 byte[]，内部将其作为 passphrase 做 PBKDF2 派生。
        // 编码方式必须与 AndroidDatabaseKeyProvider.rotatePassphrase 中 PRAGMA rekey 一致。
        // SQLCipher clearPassphrase=true（默认）会在使用后自动擦除 passphrase 字节。
        val passphraseBytes = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        try {
            // SupportFactory(passphrase: byte[])：SQLCipher 会对此 passphrase 做 PBKDF2 派生
            val cipherFactory = SupportFactory(passphraseBytes)
            return when (lib) {
                LibName.AGENT -> AndroidSqliteDriver(AgentDb.Schema, context, lib.fileName, cipherFactory)
                LibName.CREDENTIALS -> AndroidSqliteDriver(CredentialsDb.Schema, context, lib.fileName, cipherFactory)
                LibName.SETTINGS -> AndroidSqliteDriver(SettingsDb.Schema, context, lib.fileName, cipherFactory)
                LibName.WORKSPACE -> AndroidSqliteDriver(WorkspaceDb.Schema, context, lib.fileName, cipherFactory)
                LibName.T2I -> AndroidSqliteDriver(T2iDb.Schema, context, lib.fileName, cipherFactory)
                LibName.INFRA -> AndroidSqliteDriver(InfraDb.Schema, context, lib.fileName, cipherFactory)
            }
        } catch (e: UnsatisfiedLinkError) {
            // SQLCipher 原生库（libsqlcipher.so）加载失败
            // 这是 Error 而非 Exception，必须单独捕获
            FileLogger.e(TAG, "SQLCipher 原生库加载失败 lib=${lib.name}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "SQLCipher 原生库加载失败: ${lib.name}",
                e,
            )
        } catch (e: Exception) {
            // fail-close：加密驱动创建失败，抛异常，绝不回退明文
            // 异常消息中不包含 passphrase / DEK 内容
            FileLogger.e(TAG, "加密驱动创建失败 lib=${lib.name}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "加密数据库打开失败: ${lib.name}",
                e,
            )
        } finally {
            // 安全擦除：DEK 原始字节和 passphrase 字节都清零
            // （SQLCipher clearPassphrase=true 也会自动擦除，此处双重保险）
            dek.fill(0)
            passphraseBytes.fill(0)
        }
    }
}
