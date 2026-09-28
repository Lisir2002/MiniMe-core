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

    /**
     * 获取已存在的DEK，不存在返回null。
     *
     * ⚠️ 与「读取失败」严格区分：DEK 存在但解不开（Keystore 故障 / 密文损坏）时**抛异常**，
     *    绝不返回 null —— null 会被误读为「还没有 DEK」进而生成新 DEK 覆盖，
     *    一旦覆盖，用旧 DEK 加密的数据将**永久不可解**。
     */
    suspend fun getDek(purpose: String): ByteArray?

    /**
     * 轮换指定用途的DEK。
     *
     * 语义随 purpose 而异（这是设计 §4.2 决策 3 的落地，但按用途分类）：
     *  - **数据库密钥**（[CipherPassphrase.DB_PREFIX] 开头）：**直接拒绝**。库文件是整体加密的，
     *    换 DEK 而不重加密文件 = 该库立即不可读；必须走
     *    [com.mini.me_core.datalayer.encryption.KeyRotationMigrator]（旧/新口令 provider）
     *    做 `sqlcipher_export` 整体重加密。
     *  - **其它用途**（如字段加密）：旧 DEK 保留为 `dek_<purpose>_prev`，用于解密轮换前产生的历史密文；
     *    [decrypt] 在当前 DEK 校验失败时自动回退到 prev。
     *
     * @throws IllegalArgumentException 对数据库密钥调用（应改用 KeyRotationMigrator）
     */
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

        /** DEK 在 EncryptedSharedPreferences 中的键前缀（值 = Base64(DEK)）。 */
        private const val DEK_KEY_PREFIX = "dek_"

        /** 轮换后旧 DEK 的保存后缀（仅非数据库用途；供历史密文解密）。 */
        private const val DEK_PREV_SUFFIX = "_prev"

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
        when (val read = readDekResult(purpose)) {
            is DekRead.Present -> return@withContext read.dek
            is DekRead.Broken -> {
                // ⚠️ fail-close：DEK 存在但解不开时**绝不**生成新 DEK 覆盖。
                //    覆盖 = 用旧 DEK 加密的数据永久不可解（6 个库全部报废）。
                throw DatabaseEncryptionException(
                    "DEK 已存在但无法解密，拒绝重建以免永久丢失数据: purpose=$purpose, " +
                        "cause=${read.cause.javaClass.simpleName}",
                    read.cause,
                )
            }
            DekRead.Absent -> Unit
        }

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
        when (val read = readDekResult(purpose)) {
            is DekRead.Present -> read.dek
            // 存在但解不开 ≠ 不存在。抛异常而非返回 null，避免调用方误判为「未初始化」。
            is DekRead.Broken -> throw DatabaseEncryptionException(
                "DEK 存在但无法解密: purpose=$purpose, cause=${read.cause.javaClass.simpleName}",
                read.cause,
            )
            DekRead.Absent -> null
        }
    }

    override suspend fun rotateDek(purpose: String): ByteArray = withContext(Dispatchers.IO) {
        if (CipherPassphrase.isDbPurpose(purpose)) {
            // 库文件是**整体加密**的：换 DEK 而不重加密文件 = 该库立刻不可读，且无旧 DEK 可救。
            // 正确做法是 KeyRotationMigrator（旧口令/新口令 provider）跑 sqlcipher_export。
            throw IllegalArgumentException(
                "数据库密钥不允许直接轮换：换 DEK 而库文件未重加密会使该库立即不可读。" +
                    "请改用 KeyRotationMigrator(sqlcipher_export 整体重加密): purpose=$purpose",
            )
        }

        val current = when (val read = readDekResult(purpose)) {
            is DekRead.Present -> read.dek
            is DekRead.Broken -> throw DatabaseEncryptionException(
                "当前 DEK 无法解密，拒绝轮换: purpose=$purpose",
                read.cause,
            )
            DekRead.Absent -> null
        }
        // 旧 DEK 先转存为 prev（保历史数据优先于保新密钥）
        if (current != null) {
            storePrevDek(purpose, current)
            current.fill(0)
        }
        val newDek = generateRandomDek()
        storeDek(purpose, newDek)
        FileLogger.i(TAG, "DEK已轮换: purpose=$purpose（旧DEK保留为 prev，仍可解密历史数据）")
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
        // 候选 DEK：当前 → 上一代（轮换前的历史密文用旧 DEK 才能解开）
        val candidates = dekCandidates(purpose)
        if (candidates.isEmpty()) {
            throw DatabaseEncryptionException("DEK未初始化，无法解密: purpose=$purpose")
        }
        var lastError: Exception? = null
        for (dek in candidates) {
            try {
                return@withContext decryptWith(dek, ciphertextB64)
            } catch (e: Exception) {
                lastError = e
            } finally {
                dek.fill(0)
            }
        }
        throw DatabaseEncryptionException(
            "解密失败（已尝试 ${candidates.size} 个候选DEK，含轮换前的旧DEK）: purpose=$purpose",
            lastError,
        )
    }

    /**
     * 紧急重置：清除**字段及其它非数据库用途**的 DEK。
     *
     * ⚠️ 绝不清除 `db_*`（数据库 DEK）：那会让 6 个库全部不可读，下次启动走
     *    「不可读 → 隔离 → 以全新库重建」，等于清空用户全部数据。本操作的语义是
     *    「重置凭据主密钥、凭据需重新录入」，不应波及数据库。
     */
    override suspend fun emergencyReset() = withContext(Dispatchers.IO) {
        try {
            val editor = encryptedPrefs.edit()
            var removed = 0
            for (key in encryptedPrefs.all.keys) {
                // 只处理 DEK 键；同 prefs 里的其它业务键不属本操作职责范围
                if (!key.startsWith(DEK_KEY_PREFIX)) continue
                val purpose = key.removePrefix(DEK_KEY_PREFIX)
                if (!CipherPassphrase.isDbPurpose(purpose)) {
                    editor.remove(key)
                    removed++
                }
            }
            val committed = editor.commit()
            if (!committed) throw DatabaseEncryptionException("紧急重置失败（commit 返回 false）")
            FileLogger.w(TAG, "紧急重置：已清除 $removed 个非数据库DEK；数据库DEK(db_*)保留，避免全库不可读")
        } catch (e: Exception) {
            throw DatabaseEncryptionException("紧急重置失败: cause=${e.javaClass.simpleName}", e)
        }
    }

    // ============== 内部方法 ==============

    /**
     * DEK 读取结果。**区分「不存在」与「存在但解不开」是本类的关键不变量**：
     * 把后者当前者处理会导致生成新 DEK 覆盖旧 DEK，数据永久不可解（设计 §2.1.5 / §7.3）。
     */
    private sealed interface DekRead {
        data class Present(val dek: ByteArray) : DekRead
        data object Absent : DekRead
        data class Broken(val cause: Exception) : DekRead
    }

    private fun dekPrefKey(purpose: String): String = "$DEK_KEY_PREFIX$purpose"

    private fun prevDekPrefKey(purpose: String): String = "$DEK_KEY_PREFIX$purpose$DEK_PREV_SUFFIX"

    /** 读取 DEK：不存在 → [DekRead.Absent]；存在但读取/解码失败 → [DekRead.Broken]。 */
    private fun readDekResult(purpose: String): DekRead {
        val wrapped = try {
            encryptedPrefs.getString(dekPrefKey(purpose), null)
        } catch (e: Exception) {
            // 连 prefs 都读不了（Keystore 故障 / 存储损坏）：无法判定为「不存在」，按损坏处理
            FileLogger.e(TAG, "读取DEK失败(存储层): purpose=$purpose, cause=${e.javaClass.simpleName}", e)
            return DekRead.Broken(e)
        } ?: return DekRead.Absent
        return try {
            // EncryptedSharedPreferences 已用MasterKey自动解密，这里存储的就是Base64编码的DEK
            val dek = Base64.decode(wrapped, Base64.NO_WRAP)
            if (dek.isEmpty()) {
                DekRead.Broken(IllegalStateException("DEK 解码结果为空"))
            } else {
                DekRead.Present(dek)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "读取DEK失败(解码): purpose=$purpose, cause=${e.javaClass.simpleName}", e)
            DekRead.Broken(e)
        }
    }

    /** 解密候选：当前 DEK（必须存在）+ 上一代 DEK（存在时）。 */
    private fun dekCandidates(purpose: String): List<ByteArray> {
        val result = ArrayList<ByteArray>(2)
        when (val read = readDekResult(purpose)) {
            is DekRead.Present -> result += read.dek
            is DekRead.Broken -> throw DatabaseEncryptionException(
                "DEK 存在但无法解密: purpose=$purpose, cause=${read.cause.javaClass.simpleName}",
                read.cause,
            )
            DekRead.Absent -> Unit
        }
        runCatching { encryptedPrefs.getString(prevDekPrefKey(purpose), null) }
            .getOrNull()
            ?.let { wrapped ->
                runCatching { Base64.decode(wrapped, Base64.NO_WRAP) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { result += it }
            }
        return result
    }

    private fun decryptWith(dek: ByteArray, ciphertextB64: String): ByteArray {
        val combined = Base64.decode(ciphertextB64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LEN + 1) {
            throw DatabaseEncryptionException("密文格式错误：长度不足")
        }
        val iv = combined.copyOfRange(0, GCM_IV_LEN)
        val ciphertext = combined.copyOfRange(GCM_IV_LEN, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
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

    /** 把上一代 DEK 另存为 `dek_<purpose>_prev`，供 [decrypt] 解密轮换前的历史密文。 */
    private fun storePrevDek(purpose: String, oldDek: ByteArray) {
        runCatching {
            val encoded = Base64.encodeToString(oldDek, Base64.NO_WRAP)
            encryptedPrefs.edit().putString(prevDekPrefKey(purpose), encoded).commit()
        }.onFailure { e ->
            FileLogger.w(TAG, "保存上一代DEK失败（历史密文将无法解密）: purpose=$purpose, ${e.message}")
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
