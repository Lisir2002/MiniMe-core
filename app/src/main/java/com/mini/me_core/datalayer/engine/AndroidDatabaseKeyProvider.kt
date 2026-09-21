package com.mini.me_core.datalayer.engine

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.cash.sqldelight.db.SqlDriver
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android 数据库密钥提供者实现（设计文档 db-encryption-migration-design.md §4.3）。
 *
 * 密钥层级：
 * ```
 * Android Keystore (硬件-backed TEE/StrongBox)
 *   └── MasterKey ("minime_db_master", AES-256-GCM，不出 Keystore)
 *         └── 包裹 per-DB DEK (AES-256，32 字节随机)
 *               └── SQLCipher passphrase (Base64(DEK)，SQLCipher 内部再做 PBKDF2)
 *                     └── 数据库页加密 (AES-256-CBC + HMAC-SHA512)
 * ```
 *
 * 安全约定：
 * - DEK 明文不落盘，SharedPreferences 只存 MasterKey 包裹后的密文（Base64）
 * - MasterKey 不出 Keystore，密钥操作在 TEE 内完成
 * - 任何失败抛 [DatabaseEncryptionException]，绝不静默降级
 * - 日志中不输出任何密钥内容（包括 DEK、包裹密文、passphrase）
 * - [getPassphrase] 返回的 ByteArray 调用方必须 fill(0) 擦除
 *
 * @param context Application Context（用于获取 SharedPreferences）
 */
open class AndroidDatabaseKeyProvider(
    private val context: Context,
) : DatabaseKeyProvider {

    companion object {
        private const val TAG = "DatabaseKeyProvider"
        private const val PREFS_NAME = "minime_db_keys"
        private const val MASTER_KEY_ALIAS = "minime_db_master"
        private const val DEK_LEN_BYTES = 32 // AES-256
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128

        /**
         * 将 DEK 原始字节编码为 SQLCipher passphrase 字符串。
         *
         * **重要**：CipherDriverFactory 必须使用此函数编码 passphrase，
         * 与 rotatePassphrase 中 `PRAGMA rekey` 使用的编码方式保持一致，
         * 否则密钥不匹配导致数据库无法打开。
         *
         * 编码方式：Base64（NO_WRAP，无换行）。
         * SQLCipher 内部会对此 passphrase 做 PBKDF2 派生（默认 64000 轮 SHA-512），
         * 因此即使 passphrase 是 Base64 字符串，最终密钥仍有足够熵。
         *
         * @param dek DEK 原始字节（32 字节）
         * @return Base64 编码的 passphrase 字符串
         */
        fun encodePassphrase(dek: ByteArray): String =
            Base64.encodeToString(dek, Base64.NO_WRAP)
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * MasterKey 懒加载：首次访问时调用 [obtainMasterKey] 加载/生成。
     * 线程安全（LazyThreadSafetyMode.SYNCHRONIZED）。
     * 若 Keystore 不可用或密钥生成失败，异常会在首次访问时抛出（fail-close）。
     *
     * [obtainMasterKey] 为 protected open，测试子类可覆盖以注入软件密钥，
     * 绕过 Robolectric 对 AndroidKeyStore KeyGenerator 的限制。
     */
    private val masterKey: SecretKey by lazy { obtainMasterKey() }

    /**
     * 获取或生成 MasterKey（生产实现：Android Keystore）。
     *
     * 首次调用时从 Android Keystore 加载，不存在则生成。
     * 测试子类可覆盖此方法返回软件生成的 AES 密钥。
     *
     * @return MasterKey（AES-256，用于 wrap/unwrap DEK）
     * @throws DatabaseEncryptionException Keystore 不可用或密钥生成失败时
     */
    protected open fun obtainMasterKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey)
                ?: generateMasterKey(keyStore)
        } catch (e: Exception) {
            throw DatabaseEncryptionException(
                "获取/生成 MasterKey 失败: cause=${e.javaClass.simpleName}",
                e,
            )
        }
    }

    /**
     * 在 Android Keystore 中生成 MasterKey（AES-256-GCM）。
     *
     * 密钥参数：
     * - 用途：加密 + 解密（用于 wrap/unwrap DEK）
     * - 块模式：GCM
     * - 填充：无（GCM 自带认证）
     * - 密钥大小：256 位
     * - 随机化加密：true（每次 wrap 生成新 IV）
     * - 用户认证：false（P2 阶段开启生物识别绑定）
     */
    private fun generateMasterKey(keyStore: KeyStore): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // P2 阶段可开启生物识别绑定（设计文档 §4.5）
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(spec)
        return generator.generateKey()
    }

    override suspend fun getPassphrase(lib: LibName): ByteArray = withContext(Dispatchers.IO) {
        try {
            val wrappedKey = prefs.getString(wrappedKeyPrefName(lib), null)
            val dek = if (wrappedKey != null) {
                unwrapDek(wrappedKey)
            } else {
                // 首次调用：生成随机 DEK，包裹后持久化
                val newDek = generateRandomDek()
                val wrapped = wrapDek(newDek)
                val committed = prefs.edit()
                    .putString(wrappedKeyPrefName(lib), wrapped)
                    .commit()
                if (!committed) {
                    newDek.fill(0)
                    throw DatabaseEncryptionException(
                        "DEK 包裹密文持久化失败（SharedPreferences commit 返回 false）: ${lib.name}",
                    )
                }
                newDek
            }
            dek // 调用方负责 fill(0) 擦除
        } catch (e: DatabaseEncryptionException) {
            throw e
        } catch (e: Exception) {
            // 不输出密钥内容，只输出库名和异常类型
            throw DatabaseEncryptionException(
                "获取数据库密钥失败: ${lib.name}, cause=${e.javaClass.simpleName}",
                e,
            )
        }
    }

    /**
     * 生成密码学安全的随机 DEK（AES-256，32 字节）。
     */
    private fun generateRandomDek(): ByteArray {
        val dek = ByteArray(DEK_LEN_BYTES)
        SecureRandom().nextBytes(dek)
        return dek
    }

    /**
     * 用 MasterKey（AES/GCM）包裹 DEK，返回 Base64 编码字符串。
     *
     * 密文格式：Base64(IV(12 bytes) + Ciphertext+Tag)
     * GCM 的 tag 由 Cipher.doFinal 自动附在密文末尾。
     *
     * @param dek DEK 原始字节（调用方持有，本函数不修改）
     * @return Base64 编码的包裹密文（无换行）
     */
    private fun wrapDek(dek: ByteArray): String {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(dek)
        // 格式: base64(iv + ciphertext+tag)
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 用 MasterKey 解包 DEK。
     *
     * @param wrapped Base64 编码的包裹密文（格式：IV + Ciphertext+Tag）
     * @return DEK 原始字节（32 字节）
     * @throws DatabaseEncryptionException 解包失败（GCM tag 校验失败、MasterKey 不匹配、密文损坏等）
     */
    private fun unwrapDek(wrapped: String): ByteArray {
        val combined = Base64.decode(wrapped, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LEN + 1) {
            throw DatabaseEncryptionException("包裹密文格式错误：长度不足")
        }
        val iv = combined.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * 构造指定库的 DEK 包裹密文在 SharedPreferences 中的键名。
     */
    private fun wrappedKeyPrefName(lib: LibName): String = "dek_wrapped_${lib.name}"

    override suspend fun rotatePassphrase(lib: LibName, driver: SqlDriver) {
        withContext(Dispatchers.IO) {
            var newDek: ByteArray? = null
            try {
                newDek = generateRandomDek()
                // SQLCipher rekey：用新密钥重新加密整个数据库（原子操作）
                // passphrase 必须与 CipherDriverFactory 中使用的编码方式一致（Base64）
                val newPassphrase = encodePassphrase(newDek)
                driver.execute(null, "PRAGMA rekey = ?", 1) {
                    bindString(0, newPassphrase)
                }
                // 持久化新 DEK 的包裹密文（覆盖旧值）
                val wrapped = wrapDek(newDek)
                val committed = prefs.edit()
                    .putString(wrappedKeyPrefName(lib), wrapped)
                    .commit()
                if (!committed) {
                    throw DatabaseEncryptionException(
                        "密钥轮换后持久化失败（SharedPreferences commit 返回 false）: ${lib.name}",
                    )
                }
            } catch (e: DatabaseEncryptionException) {
                throw e
            } catch (e: Exception) {
                throw DatabaseEncryptionException(
                    "轮换数据库密钥失败: ${lib.name}, cause=${e.javaClass.simpleName}",
                    e,
                )
            } finally {
                // 安全擦除新 DEK 内存
                newDek?.fill(0)
            }
        }
    }

    override fun isInitialized(lib: LibName): Boolean =
        prefs.contains(wrappedKeyPrefName(lib))

    override suspend fun emergencyReset() {
        withContext(Dispatchers.IO) {
            try {
                val committed = prefs.edit().clear().commit()
                if (!committed) {
                    throw DatabaseEncryptionException("紧急清除失败（SharedPreferences commit 返回 false）")
                }
                // 注意：MasterKey 仍在 Keystore 中，但所有 DEK 包裹密文已清除
                // 加密数据库将永久不可读（DEK 无法恢复）
            } catch (e: DatabaseEncryptionException) {
                throw e
            } catch (e: Exception) {
                throw DatabaseEncryptionException(
                    "紧急清除失败: cause=${e.javaClass.simpleName}",
                    e,
                )
            }
        }
    }
}
