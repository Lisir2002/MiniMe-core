package com.mini.me_core.datalayer.encryption

/**
 * 数据库加密相关异常。
 *
 * 所有密钥操作、驱动创建、迁移失败均抛出此异常（或其子类），
 * 严格遵循 fail-close 原则——绝不静默降级为明文或返回默认值。
 *
 * 异常消息中禁止包含任何密钥内容（passphrase / DEK / 包裹密文等）。
 */
class DatabaseEncryptionException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)
