package com.mini.me_core.feature.packager.domain.model

/**
 * 构建记录
 *
 * @param id 构建记录唯一ID（UUID）
 * @param projectId 所属项目ID
 * @param versionName 构建时的版本名
 * @param versionCode 构建时的版本号
 * @param status 构建状态
 * @param startTime 开始时间戳
 * @param endTime 结束时间戳（可选，构建中为 null）
 * @param durationMs 构建耗时（毫秒，可选）
 * @param apkSize 产物大小（字节，可选）
 * @param apkPath 产物文件路径（可选）
 * @param logPath 构建日志文件路径（可选）
 * @param errorMessage 失败时的错误信息（可选）
 */
data class BuildRecord(
    val id: String,
    val projectId: String,
    val versionName: String,
    val versionCode: Int,
    val status: BuildStatus,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val apkSize: Long? = null,
    val apkPath: String? = null,
    val logPath: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        /**
         * 创建构建中的记录
         */
        fun createBuilding(
            projectId: String,
            versionName: String,
            versionCode: Int
        ): BuildRecord = BuildRecord(
            id = java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            versionName = versionName,
            versionCode = versionCode,
            status = BuildStatus.BUILDING,
            startTime = System.currentTimeMillis()
        )
    }

    /**
     * 标记构建成功
     */
    fun markSuccess(apkPath: String, apkSize: Long, logPath: String): BuildRecord {
        val end = System.currentTimeMillis()
        return copy(
            status = BuildStatus.SUCCESS,
            endTime = end,
            durationMs = end - startTime,
            apkPath = apkPath,
            apkSize = apkSize,
            logPath = logPath,
            errorMessage = null
        )
    }

    /**
     * 标记构建失败
     */
    fun markFailed(errorMessage: String, logPath: String?): BuildRecord {
        val end = System.currentTimeMillis()
        return copy(
            status = BuildStatus.FAILED,
            endTime = end,
            durationMs = end - startTime,
            logPath = logPath,
            errorMessage = errorMessage
        )
    }

    /** 格式化耗时为可读字符串 */
    val formattedDuration: String
        get() {
            val ms = durationMs ?: return "-"
            val seconds = ms / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return if (minutes > 0) "${minutes}分${remainingSeconds}秒" else "${remainingSeconds}秒"
        }

    /** 格式化APK大小为可读字符串 */
    val formattedApkSize: String
        get() {
            val bytes = apkSize ?: return "-"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1 -> String.format("%.2f MB", mb)
                kb >= 1 -> String.format("%.1f KB", kb)
                else -> "$bytes B"
            }
        }
}
