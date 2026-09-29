package com.mini.me_core.datalayer.exception

/**
 * 数据层错误码枚举。
 *
 * 所有数据层异常统一使用此错误码，便于上层捕获后做差异化处理和用户提示。
 * 错误码区间 1001-1999，按功能模块分段。
 */
enum class DataLayerErrorCode(val code: Int, val userMessage: String) {

    // 数据库生命周期（1001-1009）
    DATABASE_NOT_FOUND(1001, "数据库不存在或未注册"),
    DATABASE_OPEN_FAILED(1002, "数据库打开失败"),
    INTEGRITY_CHECK_FAILED(1009, "数据库完整性校验失败，数据可能已损坏"),

    // 加密与密钥（1003-1006）
    ENCRYPTION_FAILED(1003, "数据加密失败"),
    DECRYPTION_FAILED(1004, "数据解密失败"),
    KEY_NOT_FOUND(1005, "加密密钥不存在，请重新初始化"),
    KEY_CORRUPTED(1006, "加密密钥损坏，数据无法恢复"),

    // 迁移（1007-1008）
    MIGRATION_FAILED(1007, "数据库结构迁移失败"),
    MIGRATION_DATA_LOSS(1008, "数据库迁移过程中可能丢失数据"),

    // 备份与恢复（1010-1012）
    BACKUP_FAILED(1010, "数据库备份失败"),
    RESTORE_FAILED(1011, "数据库恢复失败"),
    BACKUP_INVALID(1012, "备份文件无效或已损坏"),

    // 查询与并发（1013-1015）
    QUERY_TIMEOUT(1013, "数据库查询超时"),
    CONSTRAINT_VIOLATION(1014, "数据约束违反，操作被拒绝"),
    CONCURRENT_ACCESS(1015, "数据库并发访问冲突，请稍后重试"),

    // 全文检索（1017）
    FTS_QUERY_ERROR(1017, "全文检索语法错误，请简化检索词"),

    // 清理（1016）
    CLEANUP_FAILED(1016, "数据库清理失败"),

    // 兜底（1999）
    UNKNOWN_ERROR(1999, "数据层未知错误");

    companion object {
        fun fromCode(code: Int): DataLayerErrorCode =
            entries.find { it.code == code } ?: UNKNOWN_ERROR
    }
}
