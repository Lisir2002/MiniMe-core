package com.mini.me_core.datalayer.exception

/**
 * 统一数据层异常。
 *
 * 所有数据层错误（加密、迁移、备份、恢复、查询、清理）均应抛出此异常，
 * 携带 [DataLayerErrorCode] 便于上层分类处理。
 *
 * fail-fast 原则：不静默吞异常，不返回空数据掩盖错误。
 * 异常消息中禁止包含密钥、passphrase 等敏感内容。
 */
class DataLayerException(
    message: String,
    val errorCode: DataLayerErrorCode,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** 返回用户可理解的中文错误描述（用于 UI 提示）。 */
    fun userFriendlyMessage(): String = errorCode.userMessage

    override fun toString(): String {
        return "DataLayerException(code=${errorCode.code}, name=${errorCode.name}): $message"
    }

    companion object {
        /** 便捷工厂：数据库未注册。 */
        fun databaseNotFound(dbId: String) =
            DataLayerException("数据库未注册: $dbId", DataLayerErrorCode.DATABASE_NOT_FOUND)

        /** 便捷工厂：备份文件无效。 */
        fun backupInvalid(reason: String) =
            DataLayerException("备份文件无效: $reason", DataLayerErrorCode.BACKUP_INVALID)

        /** 便捷工厂：完整性校验失败。 */
        fun integrityFailed(dbId: String, details: String) =
            DataLayerException("$dbId 完整性校验失败: $details", DataLayerErrorCode.INTEGRITY_CHECK_FAILED)
    }
}
