package com.mini.me_core.feature.settings.presentation.components

import com.mini.me_core.feature.workspace.domain.RemoteAuditAction
import com.mini.me_core.feature.workspace.domain.RemoteAuditCategory

/**
 * 操作审计动作名 / 分类名中文化映射（MiniMe）。
 *
 * 未匹配的动作名原样返回（兜底）。英文文案用于 values-en 环境。
 */
object AuditActionMapper {

    private val ZH_NAMES: Map<String, String> = mapOf(
        RemoteAuditAction.SSH_CONNECT_OK to "SSH 连接成功",
        RemoteAuditAction.SSH_CONNECT_FAIL to "SSH 连接失败",
        RemoteAuditAction.SSH_DISCONNECT to "SSH 断开连接",
        RemoteAuditAction.SSH_AUTH_FAIL to "SSH 认证失败",
        RemoteAuditAction.SSH_RECONNECTED to "SSH 自动重连",
        RemoteAuditAction.CRED_ADD to "凭据添加",
        RemoteAuditAction.CRED_UPDATE to "凭据更新",
        RemoteAuditAction.CRED_DELETE to "凭据删除",
        RemoteAuditAction.CRED_ROTATE_DEK to "主密钥轮换",
        RemoteAuditAction.CRED_V1_V2_MIGRATE to "凭据加密迁移",
        RemoteAuditAction.BACKUP_EXPORT_OK to "备份导出成功",
        RemoteAuditAction.BACKUP_EXPORT_FAIL to "备份导出失败",
        RemoteAuditAction.BACKUP_IMPORT_OK to "备份导入成功",
        RemoteAuditAction.BACKUP_IMPORT_FAIL to "备份导入失败",
        RemoteAuditAction.SFTP_UPLOAD_BIG to "SFTP 大文件上传",
        RemoteAuditAction.SFTP_DOWNLOAD_BIG to "SFTP 大文件下载",
        RemoteAuditAction.TAB_RECONNECT_FAILED_3X to "标签页重连失败(3次)",
        RemoteAuditAction.EMERGENCY_RESET_MASTERKEY to "紧急重置主密钥",
        RemoteAuditAction.BIOMETRIC_SWITCH to "生物识别开关",
        RemoteAuditAction.SKILL_EXEC_OK to "技能执行成功",
        RemoteAuditAction.SKILL_EXEC_FAIL to "技能执行失败",
    )

    private val EN_NAMES: Map<String, String> = mapOf(
        RemoteAuditAction.SSH_CONNECT_OK to "SSH connected",
        RemoteAuditAction.SSH_CONNECT_FAIL to "SSH connect failed",
        RemoteAuditAction.SSH_DISCONNECT to "SSH disconnected",
        RemoteAuditAction.SSH_AUTH_FAIL to "SSH auth failed",
        RemoteAuditAction.SSH_RECONNECTED to "SSH auto-reconnected",
        RemoteAuditAction.CRED_ADD to "Credential added",
        RemoteAuditAction.CRED_UPDATE to "Credential updated",
        RemoteAuditAction.CRED_DELETE to "Credential deleted",
        RemoteAuditAction.CRED_ROTATE_DEK to "Master key rotated",
        RemoteAuditAction.CRED_V1_V2_MIGRATE to "Credential encryption migrated",
        RemoteAuditAction.BACKUP_EXPORT_OK to "Backup exported",
        RemoteAuditAction.BACKUP_EXPORT_FAIL to "Backup export failed",
        RemoteAuditAction.BACKUP_IMPORT_OK to "Backup imported",
        RemoteAuditAction.BACKUP_IMPORT_FAIL to "Backup import failed",
        RemoteAuditAction.SFTP_UPLOAD_BIG to "SFTP large upload",
        RemoteAuditAction.SFTP_DOWNLOAD_BIG to "SFTP large download",
        RemoteAuditAction.TAB_RECONNECT_FAILED_3X to "Tab reconnect failed (3x)",
        RemoteAuditAction.EMERGENCY_RESET_MASTERKEY to "Emergency master key reset",
        RemoteAuditAction.BIOMETRIC_SWITCH to "Biometric toggle",
        RemoteAuditAction.SKILL_EXEC_OK to "Skill executed",
        RemoteAuditAction.SKILL_EXEC_FAIL to "Skill execution failed",
    )

    /** 动作显示名；未匹配原样返回。 */
    fun displayName(action: String, english: Boolean = false): String {
        val table = if (english) EN_NAMES else ZH_NAMES
        return table[action] ?: action
    }

    /** 分类小标签：SYNC / RECONNECT_FAIL 归入连接；技能动作归入技能。 */
    fun categoryLabel(category: String, action: String, english: Boolean = false): String {
        if (action.startsWith("SKILL_")) return if (english) "Skill" else "技能"
        return when (category) {
            RemoteAuditCategory.CONNECT,
            RemoteAuditCategory.SYNC,
            RemoteAuditCategory.RECONNECT_FAIL -> if (english) "Connection" else "连接"
            RemoteAuditCategory.CREDENTIAL -> if (english) "Credential" else "凭据"
            RemoteAuditCategory.BACKUP -> if (english) "Backup" else "备份"
            RemoteAuditCategory.SECURITY -> if (english) "Security" else "安全"
            else -> category
        }
    }
}
