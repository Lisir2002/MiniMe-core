package com.mini.me_core.core.security

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全审计端口（core 侧定义，地基不反向依赖 feature 层）。
 *
 * 背景：[CredentialEncryptor] 原本直接依赖
 * `feature.workspace.domain.repository.RemoteAuditLogRepository`，形成 core → feature 的反向依赖。
 * 现改由本端口解耦：core 只面向本接口写「脱敏安全事件」，feature/workspace 侧稍后提供
 * 转发到远程审计仓储的实现（经 Hilt 绑定）。
 *
 * 端口契约（供上层接线实现）：
 * ```
 * interface SecurityAuditSink {
 *     suspend fun recordSecurityEvent(
 *         category: String,
 *         action: String,
 *         success: Boolean,
 *         message: String? = null,
 *     )
 * }
 * ```
 * 实现方必须：
 *  - 绝不把密钥 / 密文 / token / 口令原文写入 message；
 *  - message 长度自行截断并做脱敏；
 *  - 写入失败只记本地日志，不向调用方抛异常（审计失败不应阻断密钥轮换主流程）。
 */
interface SecurityAuditSink {

    /**
     * 记录一次与凭据/密钥相关的安全事件。
     *
     * @param category 事件分类，取值见 [SecurityAuditCategory]。
     * @param action 事件动作，取值见 [SecurityAuditAction]。
     * @param success 本次操作是否成功。
     * @param message 脱敏后的可读说明；严禁含密钥材料。
     */
    suspend fun recordSecurityEvent(
        category: String,
        action: String,
        success: Boolean,
        message: String? = null,
    )
}

/** 安全审计分类常量（与 feature 层字符串对齐，但 core 不 import feature）。 */
object SecurityAuditCategory {
    const val CREDENTIAL = "CREDENTIAL"
    const val SECURITY = "SECURITY"
}

/** 安全审计动作常量（与 feature 层字符串对齐，但 core 不 import feature）。 */
object SecurityAuditAction {
    const val CRED_ROTATE_DEK = "CRED_ROTATE_DEK"
    const val EMERGENCY_RESET_MASTERKEY = "EMERGENCY_RESET_MASTERKEY"
}

/**
 * 默认 no-op 安全审计实现。
 *
 * 在 feature/workspace 侧转发实现接入前，core 可独立编译运行：本实现仅把事件打到
 * [android.util.Log]（且不含任何密钥材料），不落远程、不写库。接入正式实现后，
 * 经 Hilt 用真实实现替换本绑定（见 SecurityAuditModule）。
 */
@Singleton
class NoOpSecurityAuditSink @Inject constructor() : SecurityAuditSink {

    override suspend fun recordSecurityEvent(
        category: String,
        action: String,
        success: Boolean,
        message: String?,
    ) {
        // 仅本地日志，且 message 已由调用方保证脱敏；不写任何远程/持久化。
        Log.i(TAG, "[$category/$action success=$success] ${message.orEmpty()}")
    }

    companion object {
        private const val TAG = "SecurityAudit"
    }
}
