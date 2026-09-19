package com.mini.me_core.core.security

/**
 * 安全审计记录端口（由 :app / feature.workspace 注入实现）。
 *
 * 反转前：CredentialEncryptor 直接依赖 feature.workspace.domain.repository.RemoteAuditLogRepository
 * （它又依赖 datalayer），形成 core/security ↔ feature/workspace 的包级循环。
 * 反转后：:core:security 只依赖这个窄接口，实现在 :app 用 RemoteAuditLogRepository 适配。
 *
 * category/action 取值见 com.mini.me_core.core.model.RemoteAuditCategory / RemoteAuditAction。
 */
interface SecurityAuditRecorder {
    /**
     * 记录一条安全审计事件。
     * @param success 是否成功（失败也要记录）。
     * @param message 可读描述（可空）。
     */
    suspend fun record(category: String, action: String, success: Boolean, message: String? = null)
}

/**
 * 凭据加密状态持久化端口（由 :app 注入实现，底层走 datalayer 的 WorkspaceRepository）。
 *
 * 反转前：CredentialEncryptor 直接依赖 datalayer.repository.WorkspaceRepository，
 * :core:security 因此无法脱离 datalayer 独立成模块。反转后只依赖这个接口。
 */
interface CredentialStateStore {
    /** 读取单行加密状态（id 恒为 1）；未初始化返回 null。 */
    suspend fun loadState(): CredentialEncryptionStateEntity?

    /** upsert 单行加密状态。 */
    suspend fun upsertState(state: CredentialEncryptionStateEntity)
}
