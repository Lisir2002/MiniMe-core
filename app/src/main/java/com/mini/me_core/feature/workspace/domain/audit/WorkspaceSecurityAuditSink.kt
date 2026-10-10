package com.mini.me_core.feature.workspace.domain.audit

import com.mini.me_core.core.security.SecurityAuditSink
import com.mini.me_core.core.util.LogSanitizer
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * feature/workspace 侧 [SecurityAuditSink] 实现：把 core 下发的安全事件
 * （越界拒绝、安装校验拒绝、凭据/密钥轮换等）转发到现有 [RemoteAuditLogRepository]
 * 落「脱敏审计」，与远程操作审计同库同保留策略。
 *
 * 契约对齐（B 组端口）：
 *  - message 落库前再过一遍 [LogSanitizer] 纵深脱敏（调用方已脱敏，这里是兜底）；
 *  - 写入失败只吞掉不抛异常——审计失败不能阻断主流程。
 */
@Singleton
class WorkspaceSecurityAuditSink @Inject constructor(
    private val auditLogRepository: RemoteAuditLogRepository,
) : SecurityAuditSink {

    override suspend fun recordSecurityEvent(
        category: String,
        action: String,
        success: Boolean,
        message: String?,
    ) {
        runCatching {
            val safe = message?.let { LogSanitizer.sanitize(it) }
            auditLogRepository.append(
                category = category,
                action = action,
                success = success,
                message = safe,
            )
        }
    }
}
