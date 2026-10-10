package com.mini.me_core.feature.backup.domain

/**
 * 备份加密范围。
 *
 * @param FULL 全量加密整个 tar.gz 包（当前唯一实现的路径：password 非空即整包 AES-GCM 流式加密）。
 *
 * @param CREDENTIALS_ONLY 仅加密 SSH 密码/API Key/Git Token 等敏感字段。
 *   ⚠️ 该选项历史上从未接入 [BackupManager.export]（虚假 UI 选项），已从设置界面删除，
 *   保留枚举值仅为序列化兼容；短期不实现字段级选择性加密——公共导出默认走 FULL 加密，
 *   杜绝「看似可选实则明文」。
 */
enum class BackupEncryptScope {
    FULL,

    @Deprecated("未实现，UI 已删除；公共导出统一走 FULL 整包加密")
    CREDENTIALS_ONLY
}
