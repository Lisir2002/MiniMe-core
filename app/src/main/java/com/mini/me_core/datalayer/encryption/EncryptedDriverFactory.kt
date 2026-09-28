package com.mini.me_core.datalayer.encryption

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.util.Base64
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory

/**
 * 加密驱动工厂。
 *
 * 简化版：只负责创建SQLCipher加密驱动，无明文/加密路由逻辑。
 * 所有数据库默认加密。
 *
 * 安全约定：
 * - 任何异常向上抛出，绝不回退明文驱动（fail-close）
 * - DEK和passphrase使用后立即fill(0)擦除
 * - 日志中不输出密钥内容
 * - 捕获UnsatisfiedLinkError（SQLCipher原生库加载失败）
 */
class EncryptedDriverFactory(
    private val context: Context,
    private val keyManager: UnifiedKeyManager,
) {

    private companion object {
        const val TAG = "EncryptedDriverFactory"
    }

    /**
     * 创建加密驱动（同步阻塞版本，供ConnectionPool在非协程上下文中调用）。
     *
     * @param definition 数据库定义
     * @return SQLCipher加密的SqlDriver
     * @throws DatabaseEncryptionException 驱动创建失败时
     */
    fun createBlocking(definition: DatabaseDefinition): SqlDriver {
        val dek = runBlocking(Dispatchers.IO) {
            keyManager.getOrCreateDek("db_${definition.id}")
        }
        // DEK编码为Base64字符串，再转UTF-8字节作为SQLCipher passphrase
        val passphraseBytes = Base64.encodeToString(dek, Base64.NO_WRAP)
            .toByteArray(Charsets.UTF_8)
        dek.fill(0)
        try {
            val cipherFactory = SupportFactory(passphraseBytes)
            return AndroidSqliteDriver(
                schema = definition.schema,
                context = context,
                name = definition.fileName,
                factory = cipherFactory,
            )
        } catch (e: UnsatisfiedLinkError) {
            FileLogger.e(TAG, "SQLCipher原生库加载失败: ${definition.id}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "SQLCipher原生库加载失败: ${definition.id}",
                e,
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "加密驱动创建失败: ${definition.id}（不回退明文）", e)
            throw DatabaseEncryptionException(
                "加密数据库打开失败: ${definition.id}",
                e,
            )
        } finally {
            passphraseBytes.fill(0)
        }
    }
}
