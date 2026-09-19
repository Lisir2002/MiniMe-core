package com.mini.me_core.di

import com.mini.me_core.core.security.CredentialEncryptionStateEntity
import com.mini.me_core.core.security.CredentialStateStore
import com.mini.me_core.core.security.SecurityAuditRecorder
import com.mini.me_core.datalayer.repository.WorkspaceRepository
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * :core:security 端口的 :app 装配（架构规则 #5：Hilt 图在 :app 聚合，不拆到各模块）。
 *
 * 反转前：CredentialEncryptor 直接依赖 datalayer.WorkspaceRepository 与
 * feature.workspace.RemoteAuditLogRepository，造成 core/security ↔ feature/workspace 包级循环。
 * 反转后：:core:security 只认 SecurityAuditRecorder / CredentialStateStore 两个窄端口，
 * 这里用适配器把它们接到既有的 V2 仓储，业务语义不变。
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityPortsModule {

    @Provides
    @Singleton
    fun provideCredentialStateStore(ws: WorkspaceRepository): CredentialStateStore =
        CredentialStateStoreAdapter(ws)

    @Provides
    @Singleton
    fun provideSecurityAuditRecorder(ar: RemoteAuditLogRepository): SecurityAuditRecorder =
        SecurityAuditRecorderAdapter(ar)
}

/**
 * CredentialStateStore 适配器：把 datalayer 的 SQLDelight 行映射回 :core:security 的领域模型。
 * 映射逻辑迁移自原 CredentialEncryptor 私有扩展 Credential_encryption_state.toEntity()。
 */
private class CredentialStateStoreAdapter(
    private val ws: WorkspaceRepository,
) : CredentialStateStore {

    override suspend fun loadState(): CredentialEncryptionStateEntity? =
        ws.getEncryptionState()?.toEntity()

    override suspend fun upsertState(state: CredentialEncryptionStateEntity) {
        ws.upsertEncryptionState(
            masterKeyFingerprint = state.masterKeyFingerprint,
            dekCiphertext = state.dekCiphertext,
            encScheme = state.encScheme,
            lastRotatedAt = state.lastRotatedAt,
            rotationCounter = state.rotationCounter.toLong(),
            biometricRequired = if (state.biometricRequired) 1L else 0L,
            migratedFromV1 = if (state.migratedFromV1) 1L else 0L,
        )
    }

    private fun com.mini.mecore.datalayer.sqldelight.workspace.Credential_encryption_state.toEntity() =
        CredentialEncryptionStateEntity(
            id = id.toInt(),
            masterKeyFingerprint = master_key_fingerprint,
            dekCiphertext = dek_ciphertext,
            encScheme = enc_scheme,
            lastRotatedAt = last_rotated_at,
            rotationCounter = rotation_counter.toInt(),
            biometricRequired = biometric_required == 1L,
            migratedFromV1 = migrated_from_v1 == 1L,
        )
}

/**
 * SecurityAuditRecorder 适配器：复用既有 RemoteAuditLogRepository.append()，不改变审计写入语义。
 */
private class SecurityAuditRecorderAdapter(
    private val auditLog: RemoteAuditLogRepository,
) : SecurityAuditRecorder {

    override suspend fun record(category: String, action: String, success: Boolean, message: String?) {
        auditLog.append(
            category = category,
            action = action,
            success = success,
            message = message,
        )
    }
}
