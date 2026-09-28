package com.mini.me_core.core.security

import android.content.Context
import com.mini.me_core.core.db.entity.CredentialEncryptionStateEntity
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import com.mini.me_core.datalayer.repository.WorkspaceRepository as V2WorkspaceRepository
import com.mini.me_core.feature.workspace.domain.RemoteAuditAction
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 凭据加密器 3.0（db-encryption-redesign）。
 *
 * 架构：统一密钥管理 UnifiedKeyManager（单一MasterKey "minime_master_key" + EncryptedSharedPreferences）
 * 取代旧版 DEKManager（独立MasterKey "minime_credential_masterkey" + DB存储DEK密文）。
 *
 * DEK用途：field_credentials（字段级加密）。
 * 输出格式兼容旧版："V2:<Base64(IV + ciphertext + 16B GCM tag)>"。
 *
 * 旧版兼容导入：
 * - 首次初始化时，尝试从旧版 credential_encryption_state 表读取DEK密文，
 *   用旧MasterKey "minime_credential_masterkey" 解包，成功则导入新体系。
 * - 旧MasterKey不可用或解包失败时，生成新DEK，旧密文无法解密（需用户重新输入）。
 */
@Singleton
class CredentialEncryptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val v2Workspace: V2WorkspaceRepository,
    private val auditLogRepo: RemoteAuditLogRepository,
    private val keyManager: UnifiedKeyManager,
) {
    private companion object {
        const val TAG = "CredentialEncryptor"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val IV_LEN = 12
        const val SCHEME_V2 = "V2:"
        const val FIELD_DEK_PURPOSE = "field_credentials"
        // 旧版兼容
        const val LEGACY_MASTER_ALIAS = "minime_credential_masterkey"
    }

    private suspend fun getState(): CredentialEncryptionStateEntity? =
        v2Workspace.getEncryptionState()?.toEntity()

    /** 供 UI 读取当前加密状态。 */
    suspend fun encryptionState(): CredentialEncryptionStateEntity? = getState()

    private suspend fun upsertState(e: CredentialEncryptionStateEntity) {
        v2Workspace.upsertEncryptionState(
            masterKeyFingerprint = e.masterKeyFingerprint,
            dekCiphertext = e.dekCiphertext,
            encScheme = e.encScheme,
            lastRotatedAt = e.lastRotatedAt,
            rotationCounter = e.rotationCounter.toLong(),
            biometricRequired = if (e.biometricRequired) 1L else 0L,
            migratedFromV1 = if (e.migratedFromV1) 1L else 0L,
        )
    }

    private val initMutex = Mutex()

    /** ensureInitialized 是否已成功执行过一次。 */
    @Volatile
    private var initialized: Boolean = false

    // ============== 故障统计 ==============

    /** 字段级解密失败计数。 */
    private val decryptFailures = ConcurrentHashMap<String, AtomicInteger>()

    /** 已标记为损坏的字段集合。 */
    private val corruptedFields = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** DEK 重建事件。 */
    private val _dekRotatedEvent = MutableStateFlow<Long>(0L)
    val dekRotatedEvent: StateFlow<Long> = _dekRotatedEvent

    // ============== 初始化 ==============

    /**
     * 启动后首次加密/解密前必须调用；幂等。
     * 内部强制 IO 线程。
     */
    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        initMutex.withLock {
            if (initialized) return@withLock

            runCatching {
                // 尝试旧版DEK导入（仅一次）
                tryImportLegacyFieldDek()

                // 获取或创建field_credentials DEK
                val dek = keyManager.getOrCreateDek(FIELD_DEK_PURPOSE)
                dek.fill(0)

                // 确保DB中有状态记录（用于UI显示轮换计数等）
                val existing = getState()
                if (existing == null) {
                    upsertState(
                        CredentialEncryptionStateEntity(
                            masterKeyFingerprint = "unified-v1",
                            dekCiphertext = "",
                            encScheme = "V2",
                            lastRotatedAt = System.currentTimeMillis(),
                            migratedFromV1 = true,
                        )
                    )
                }

                initialized = true
                FileLogger.i(TAG, "CredentialEncryptor 初始化完成（UnifiedKeyManager）")
            }.onFailure { e ->
                initialized = false
                FileLogger.e(TAG, "ensureInitialized 失败，加密/解密将降级", e)
            }
        }
    }

    /**
     * 尝试从旧版导入field DEK。
     * 旧版DEK存储在workspace DB的credential_encryption_state表，
     * 用旧MasterKey "minime_credential_masterkey"包裹。
     */
    private suspend fun tryImportLegacyFieldDek() {
        if (keyManager.isDekInitialized(FIELD_DEK_PURPOSE)) return

        try {
            val existing = getState() ?: return
            val oldCiphertext = existing.dekCiphertext
            if (oldCiphertext.isBlank()) return

            // 获取旧版MasterKey
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val oldMasterKey = keyStore.getKey(LEGACY_MASTER_ALIAS, null) as? javax.crypto.SecretKey
            if (oldMasterKey == null) {
                FileLogger.w(TAG, "旧版凭据MasterKey不存在($LEGACY_MASTER_ALIAS)，无法导入旧DEK，将生成新DEK")
                return
            }

            // 用旧MasterKey解包DEK
            val combined = Base64.getDecoder().decode(oldCiphertext)
            if (combined.size < IV_LEN + 1) {
                FileLogger.w(TAG, "旧版DEK密文格式错误，无法导入")
                return
            }
            val iv = combined.copyOfRange(0, IV_LEN)
            val ciphertext = combined.copyOfRange(IV_LEN, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, oldMasterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val oldDek = cipher.doFinal(ciphertext)

            // 导入新体系
            keyManager.importDek(FIELD_DEK_PURPOSE, oldDek)
            oldDek.fill(0)
            FileLogger.i(TAG, "成功从旧版导入field_credentials DEK")
        } catch (e: Exception) {
            FileLogger.w(TAG, "旧版field DEK导入失败（将生成新DEK，旧凭据密文可能无法解密）: ${e.message}")
        }
    }

    // ============== 加密/解密 ==============

    /**
     * 加密明文。输出格式 "V2:<Base64(IV + ciphertext + 16B GCM tag)>"。
     */
    suspend fun encrypt(plaintext: String): String = withContext(Dispatchers.IO) {
        if (plaintext.isEmpty()) return@withContext ""
        ensureInitialized()
        try {
            val b64 = keyManager.encrypt(FIELD_DEK_PURPOSE, plaintext.toByteArray(Charsets.UTF_8))
            SCHEME_V2 + b64
        } catch (e: Exception) {
            FileLogger.e(TAG, "V2 加密失败", e)
            throw IllegalStateException("加密凭据失败: ${e.message}", e)
        }
    }

    /**
     * 三路解密：
     * - "V2:" 前缀 → UnifiedKeyManager.decrypt
     * - 空串 → ""
     * - 无前缀 → V1 legacy 尝试，失败回退明文
     */
    suspend fun decrypt(formatted: String, fieldName: String = "unknown"): String = withContext(Dispatchers.IO) {
        if (formatted.isEmpty()) return@withContext ""
        if (corruptedFields.contains(fieldName)) return@withContext ""

        if (formatted.startsWith(SCHEME_V2)) {
            ensureInitialized()
            return@withContext try {
                val b64 = formatted.removePrefix(SCHEME_V2)
                val plain = keyManager.decrypt(FIELD_DEK_PURPOSE, b64)
                decryptFailures[fieldName]?.set(0)
                String(plain, Charsets.UTF_8)
            } catch (e: Exception) {
                val count = decryptFailures.getOrPut(fieldName) { AtomicInteger(0) }.incrementAndGet()
                if (count >= 3) {
                    corruptedFields.add(fieldName)
                    FileLogger.e(TAG, "字段[$fieldName] 连续 $count 次解密失败，标记为损坏")
                }
                FileLogger.e(TAG, "V2解密失败 field=$fieldName failCount=$count", e)
                ""
            }
        }

        // 无前缀：V1 legacy
        decryptV1Legacy(formatted)
    }

    /** 返回当前已标记为损坏的字段集合。 */
    fun getCorruptedFields(): Set<String> = corruptedFields.toSet()

    /** 重置某字段的失败计数与损坏标记。 */
    fun resetFieldFailure(fieldName: String) {
        decryptFailures.remove(fieldName)?.set(0)
        corruptedFields.remove(fieldName)
        FileLogger.i(TAG, "已重置字段[$fieldName]的解密失败计数与损坏标记")
    }

    // ============== 密钥轮换 ==============

    /**
     * 轮换field DEK。
     * 注意：轮换后旧密文无法用新DEK解密，需用户重新输入凭据。
     */
    suspend fun scheduleRotateDek(): OperationResult<RotationReport> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val startMs = System.currentTimeMillis()
            keyManager.rotateDek(FIELD_DEK_PURPOSE)

            val existing = getState()
            upsertState(
                CredentialEncryptionStateEntity(
                    masterKeyFingerprint = "unified-v1",
                    dekCiphertext = "",
                    encScheme = "V2",
                    lastRotatedAt = startMs,
                    rotationCounter = (existing?.rotationCounter ?: 0) + 1,
                    biometricRequired = existing?.biometricRequired ?: false,
                    migratedFromV1 = existing?.migratedFromV1 ?: true,
                )
            )

            _dekRotatedEvent.value = startMs
            FileLogger.i(TAG, "field_credentials DEK轮换完成")

            auditLogRepo.append(
                category = RemoteAuditCategory.CREDENTIAL,
                action = RemoteAuditAction.CRED_ROTATE_DEK,
                success = true,
                message = "DEK轮换完成（UnifiedKeyManager）",
            )

            OperationResult.success(
                RotationReport(
                    rotatedAtMs = startMs,
                    rotationCounter = (existing?.rotationCounter ?: 0) + 1,
                    affectedTables = emptyList(),
                    durationMs = 0,
                )
            )
        } catch (e: Exception) {
            FileLogger.e(TAG, "DEK轮换失败", e)
            OperationResult.failure(e)
        }
    }

    // ============== 生物识别（新架构简化） ==============

    /**
     * 切换MasterKey的生物识别保护。
     * 新架构下MasterKey由EncryptedSharedPreferences管理，暂不支持运行时切换生物识别绑定。
     * 保留接口以兼容UI调用。
     */
    suspend fun setBiometricRequired(required: Boolean): OperationResult<Unit> =
        withContext(Dispatchers.IO) {
            FileLogger.i(TAG, "生物识别保护切换为: $required（新架构由系统统一管理，记录状态）")
            val existing = getState()
            upsertState(
                CredentialEncryptionStateEntity(
                    masterKeyFingerprint = "unified-v1",
                    dekCiphertext = existing?.dekCiphertext ?: "",
                    encScheme = "V2",
                    lastRotatedAt = existing?.lastRotatedAt ?: System.currentTimeMillis(),
                    rotationCounter = existing?.rotationCounter ?: 0,
                    biometricRequired = required,
                    migratedFromV1 = existing?.migratedFromV1 ?: true,
                )
            )
            OperationResult.success(Unit)
        }

    // ============== 紧急解锁 ==============

    /**
     * 紧急重置：清除所有DEK并重新生成。
     * 旧密文将无法解密。
     */
    suspend fun emergencyResetMasterKey(): ResetReport = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()

        keyManager.emergencyReset()
        initialized = false

        // 重新初始化会生成新DEK
        ensureInitialized()

        upsertState(
            CredentialEncryptionStateEntity(
                masterKeyFingerprint = "unified-v1",
                dekCiphertext = "",
                encScheme = "V2",
                lastRotatedAt = startMs,
                rotationCounter = 0,
                biometricRequired = false,
                migratedFromV1 = false,
            )
        )

        FileLogger.i(TAG, "紧急重置完成（UnifiedKeyManager）")
        auditLogRepo.append(
            category = RemoteAuditCategory.SECURITY,
            action = RemoteAuditAction.EMERGENCY_RESET_MASTERKEY,
            success = true,
            message = "紧急重置完成（UnifiedKeyManager）",
        )

        ResetReport(
            newMasterKeyCreatedAtMs = startMs,
            fieldsResetToEmpty = 0,
            fieldsSuccessfullyMigrated = 0,
        )
    }

    // ============== 辅助方法 ==============

    /** 兼容旧版 V1 单密钥解密。 */
    private fun decryptV1Legacy(formatted: String): String {
        return try {
            val combined = Base64.getDecoder().decode(formatted)
            if (combined.size < IV_LEN + 1) return formatted

            val iv = combined.copyOfRange(0, IV_LEN)
            val ciphertext = combined.copyOfRange(IV_LEN, combined.size)

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val alias = "minime_credential_key"
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                ?: return formatted

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            formatted
        }
    }

    /** 检查 V1 单密钥是否存在。 */
    fun isV1KeyAvailable(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.containsAlias("minime_credential_key")
        } catch (e: Exception) {
            false
        }
    }

    // ============== 健康检查 ==============

    suspend fun performHealthCheck(): HealthCheckResult = withContext(Dispatchers.IO) {
        val problems = mutableListOf<String>()

        if (!keyManager.isMasterKeyAvailable()) {
            problems.add("统一MasterKey不存在于Android Keystore")
        }

        try {
            ensureInitialized()
            if (!keyManager.isDekInitialized(FIELD_DEK_PURPOSE)) {
                problems.add("field_credentials DEK未初始化")
            }
        } catch (e: Exception) {
            problems.add("ensureInitialized异常: ${e.message}")
        }

        try {
            val probe = "healthcheck_probe_${System.currentTimeMillis()}"
            val enc = encrypt(probe)
            val dec = decrypt(enc, fieldName = "__healthcheck__")
            if (dec != probe) {
                problems.add("encrypt/decrypt往返不一致")
            }
        } catch (e: Exception) {
            problems.add("encrypt/decrypt往返测试异常: ${e.message}")
        }

        if (problems.isEmpty()) {
            FileLogger.i(TAG, "performHealthCheck通过")
            HealthCheckResult(healthy = true, problems = emptyList())
        } else {
            FileLogger.e(TAG, "performHealthCheck发现问题: $problems")
            HealthCheckResult(healthy = false, problems = problems)
        }
    }

    // ── V2 映射 ──────────────────────────────────────────────────────

    private fun com.mini.mecore.datalayer.sqldelight.workspace.Credential_encryption_state.toEntity() = CredentialEncryptionStateEntity(
        id = id.toInt(),
        masterKeyFingerprint = master_key_fingerprint,
        dekCiphertext = dek_ciphertext,
        encScheme = enc_scheme,
        lastRotatedAt = last_rotated_at,
        rotationCounter = rotation_counter.toInt(),
        biometricRequired = biometric_required == 1L,
        migratedFromV1 = migrated_from_v1 == 1L,
    )

    // ============== 数据类 ==============

    data class RotationReport(
        val rotatedAtMs: Long,
        val rotationCounter: Int = 0,
        val affectedTables: List<TableCount> = emptyList(),
        val durationMs: Long = 0,
    ) {
        data class TableCount(val table: String, val rows: Int)
    }

    data class ResetReport(
        val newMasterKeyCreatedAtMs: Long,
        val fieldsResetToEmpty: Int,
        val fieldsSuccessfullyMigrated: Int,
    )

    data class HealthCheckResult(
        val healthy: Boolean,
        val problems: List<String>,
    )
}

/** MasterKey被外部重置时抛出的异常。 */
class MasterKeyTamperedException(message: String) : Exception(message)

/** 操作结果封装。 */
sealed class OperationResult<T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure<T>(val error: Throwable) : OperationResult<T>()

    companion object {
        fun <T> success(data: T): OperationResult<T> = Success(data)
        fun <T> failure(e: Throwable): OperationResult<T> = Failure(e)
    }
}
