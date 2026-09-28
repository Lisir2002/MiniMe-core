package com.mini.me_core.datalayer.encryption

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mini.me_core.core.util.FileLogger
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一密钥管理器接口。
 *
 * 管理MasterKey和所有DEK，提供加密/解密/轮换/恢复能力。
 * 单一MasterKey（Android Keystore），统一管理数据库DEK和字段加密DEK。
 */
interface UnifiedKeyManager {
    /** 获取或创建指定用途的DEK（32字节AES-256） */
    suspend fun getOrCreateDek(purpose: String): ByteArray

    /** 获取已存在的DEK，不存在返回null */
    suspend fun getDek(purpose: String): ByteArray?

    /** 轮换指定用途的DEK（生成新DEK，旧DEK不再保留用于加密新数据） */
    suspend fun rotateDek(purpose: String): ByteArray

    /** 检查DEK是否已初始化 */
    fun isDekInitialized(purpose: String): Boolean

    /** 用指定DEK加密数据，返回Base64密文（IV + ciphertext+tag） */
    suspend fun encrypt(purpose: String, plaintext: ByteArray): String

    /** 用指定DEK解密数据，输入Base64密文，失败抛出异常 */
    suspend fun decrypt(purpose: String, ciphertextB64: String): ByteArray

    /** MasterKey是否可用 */
    fun isMasterKeyAvailable(): Boolean

    /** 紧急重置（清除所有DEK，加密数据将不可读） */
    suspend fun emergencyReset()

    /**
     * 导入已存在的DEK到新体系（用于旧版DEK迁移）。
     * 将给定DEK用新MasterKey包裹后存入EncryptedSharedPreferences。
     */
    suspend fun importDek(purpose: String, dek: ByteArray)
}

/**
 * Android 统一密钥管理器实现。
 *
 * 密钥层级：
 * ```
 * Android Keystore (硬件-backed TEE/StrongBox)
 *   └── MasterKey ("minime_master_key", AES-256-GCM，不出Keystore)
 *         └── EncryptedSharedPreferences (用MasterKey加密的键值存储)
 *               ├── dek_db_agent → Base64(IV + AES-GCM(DEK))
 *               ├── dek_db_credentials → ...
 *               ├── dek_field_credentials → ...
 *               └── dek_<future> → ...
 * ```
 *
 * 旧版兼容导入：
 * - 旧版数据库DEK存储在 SharedPreferences "minime_db_keys"，key "dek_wrapped_<LIB_NAME>"，
 *   用旧MasterKey alias "minime_db_master"包裹。
 * - 升级时自动尝试解包旧DEK并用新MasterKey重新包裹，尽量恢复旧密钥。
 * - 如果旧MasterKey不可用或解包失败，生成新DEK并记录警告日志。
 *
 * @param context Application Context
 */
class AndroidUnifiedKeyManager(
    private val context: Context,
) : UnifiedKeyManager {

    companion object {
        private const val TAG = "UnifiedKeyManager"
        private const val MASTER_KEY_ALIAS = "minime_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEK_LEN_BYTES = 32 // AES-256
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        // ── 旧版兼容：旧数据库密钥存储 ──
        private const val LEGACY_DB_PREFS = "minime_db_keys"
        private const val LEGACY_DB_MASTER_ALIAS = "minime_db_master"

        /**
         * 旧版 LibName.name → 新版 purpose 映射。
         * 旧版DEK存储key格式为 "dek_wrapped_<LIB_NAME_NAME>"。
         */
        private val LEGACY_DB_PURPOSE_MAP = mapOf(
            "db_agent" to "dek_wrapped_AGENT",
            "db_credentials" to "dek_wrapped_CREDENTIALS",
            "db_settings" to "dek_wrapped_SETTINGS",
            "db_workspace" to "dek_wrapped_WORKSPACE",
            "db_t2i" to "dek_wrapped_T2I",
            "db_infra" to "dek_wrapped_INFRA",
        )
    }

    /** EncryptedSharedPreferences 懒加载（首次访问时获取MasterKey） */
    private val encryptedPrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    /**
     * 创建 EncryptedSharedPreferences。
     * MasterKey获取失败时重试3次（间隔100ms），仍失败抛异常，不静默降级。
     */
    private fun createEncryptedPrefs(): SharedPreferences {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    "minime_unified_keys",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                lastError = e
                FileLogger.w(TAG, "MasterKey/EncryptedSharedPreferences 第 ${attempt + 1} 次初始化失败，100ms后重试: ${e.message}")
                if (attempt < 2) {
                    try { Thread.sleep(100) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
        throw DatabaseEncryptionException(
            "MasterKey获取失败（重试3次后仍失败），fail-close不降级: cause=${lastError?.javaClass?.simpleName}",
            lastError,
        )
    }

    override fun isMasterKeyAvailable(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.containsAlias(MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    override fun isDekInitialized(purpose: String): Boolean {
        return try {
            encryptedPrefs.contains(dekPrefKey(purpose))
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getOrCreateDek(purpose: String): ByteArray = withContext(Dispatchers.IO) {
        // 先尝试新体系
        val existing = readDek(purpose)
        if (existing != null) return@withContext existing

        // 新体系没有，尝试旧版导入
        val imported = tryImportLegacyDek(purpose)
        if (imported != null) {
            FileLogger.i(TAG, "成功从旧版导入DEK: purpose=$purpose")
            return@withContext imported
        }

        // 旧版也没有，生成新DEK
        FileLogger.i(TAG, "生成新DEK: purpose=$purpose")
        val newDek = generateRandomDek()
        storeDek(purpose, newDek)
        newDek
    }

    override suspend fun getDek(purpose: String): ByteArray? = withContext(Dispatchers.IO) {
        readDek(purpose)
    }

    override suspend fun rotateDek(purpose: String): ByteArray = withContext(Dispatchers.IO) {
        // 清除旧DEK，生成新DEK
        encryptedPrefs.edit().remove(dekPrefKey(purpose)).apply()
        val newDek = generateRandomDek()
        storeDek(purpose, newDek)
        FileLogger.i(TAG, "DEK已轮换: purpose=$purpose")
        newDek
    }

    override suspend fun importDek(purpose: String, dek: ByteArray) = withContext(Dispatchers.IO) {
        storeDek(purpose, dek.copyOf())
        FileLogger.i(TAG, "DEK已导入: purpose=$purpose")
    }

    override suspend fun encrypt(purpose: String, plaintext: ByteArray): String = withContext(Dispatchers.IO) {
        val dek = getOrCreateDek(purpose)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } finally {
            dek.fill(0)
        }
    }

    override suspend fun decrypt(purpose: String, ciphertextB64: String): ByteArray = withContext(Dispatchers.IO) {
        val dek = getDek(purpose)
            ?: throw DatabaseEncryptionException("DEK未初始化，无法解密: purpose=$purpose")
        try {
            val combined = Base64.decode(ciphertextB64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LEN + 1) {
                throw DatabaseEncryptionException("密文格式错误：长度不足")
            }
            val iv = combined.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } finally {
            dek.fill(0)
        }
    }

    override suspend fun emergencyReset() = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit().clear().commit()
            FileLogger.w(TAG, "紧急重置：所有DEK已清除，加密数据将不可读")
        } catch (e: Exception) {
            throw DatabaseEncryptionException("紧急重置失败: cause=${e.javaClass.simpleName}", e)
        }
    }

    // ============== 内部方法 ==============

    private fun dekPrefKey(purpose: String): String = "dek_$purpose"

    /** 从EncryptedSharedPreferences读取DEK（解密后返回明文字节） */
    private fun readDek(purpose: String): ByteArray? {
        return try {
            val wrapped = encryptedPrefs.getString(dekPrefKey(purpose), null) ?: return null
            // EncryptedSharedPreferences 已用MasterKey自动解密，这里存储的就是Base64编码的DEK
            Base64.decode(wrapped, Base64.NO_WRAP)
        } catch (e: Exception) {
            FileLogger.e(TAG, "读取DEK失败: purpose=$purpose, cause=${e.javaClass.simpleName}", e)
            null
        }
    }

    /** 将DEK存入EncryptedSharedPreferences（自动用MasterKey加密） */
    private fun storeDek(purpose: String, dek: ByteArray) {
        val encoded = Base64.encodeToString(dek, Base64.NO_WRAP)
        val committed = encryptedPrefs.edit().putString(dekPrefKey(purpose), encoded).commit()
        if (!committed) {
            dek.fill(0)
            throw DatabaseEncryptionException("DEK持久化失败（EncryptedSharedPreferences commit返回false）: purpose=$purpose")
        }
    }

    /** 生成密码学安全的随机DEK（AES-256，32字节） */
    private fun generateRandomDek(): ByteArray {
        val dek = ByteArray(DEK_LEN_BYTES)
        SecureRandom().nextBytes(dek)
        return dek
    }

    /**
     * 尝试从旧版SharedPreferences导入数据库DEK。
     * 旧版使用独立MasterKey "minime_db_master" 包裹DEK，存储在 "minime_db_keys"。
     * 解包成功后将DEK存入新体系。
     */
    private fun tryImportLegacyDek(purpose: String): ByteArray? {
        // 只有数据库DEK有旧版存储位置
        val legacyKey = LEGACY_DB_PURPOSE_MAP[purpose] ?: return null
        return try {
            val legacyPrefs = context.getSharedPreferences(LEGACY_DB_PREFS, Context.MODE_PRIVATE)
            val wrappedB64 = legacyPrefs.getString(legacyKey, null) ?: return null

            // 获取旧版MasterKey
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val oldMasterKey = keyStore.getKey(LEGACY_DB_MASTER_ALIAS, null) as? SecretKey
                ?: run {
                    FileLogger.w(TAG, "旧版数据库MasterKey不存在($LEGACY_DB_MASTER_ALIAS)，无法导入旧DEK: $purpose")
                    return null
                }

            // 用旧MasterKey解包DEK
            val combined = Base64.decode(wrappedB64, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LEN + 1) {
                FileLogger.w(TAG, "旧版包裹密文格式错误: $purpose")
                return null
            }
            val iv = combined.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, oldMasterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val dek = cipher.doFinal(ciphertext)

            // 存入新体系（storeDek内部会持久化）
            storeDek(purpose, dek)
            dek
        } catch (e: Exception) {
            FileLogger.w(TAG, "旧版DEK导入失败（$purpose），将生成新DEK: ${e.message}")
            null
        }
    }
}
