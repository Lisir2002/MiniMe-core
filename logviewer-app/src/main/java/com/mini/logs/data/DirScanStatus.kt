package com.mini.logs.data

import java.io.File

/**
 * 目录扫描诊断结果。用于区分"无日志"的具体原因，
 * 避免权限问题被静默吞掉导致用户困惑。
 */
data class DirScanStatus(
    val dir: File,
    val exists: Boolean,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val logFileCount: Int,
    val error: String? = null,
) {
    /** 目录是否可用且有日志。 */
    val hasLogs: Boolean get() = logFileCount > 0

    /** 人类可读的状态描述。 */
    fun describe(): String = when {
        !exists -> "目录不存在：${dir.absolutePath}"
        !isDirectory -> "路径不是目录：${dir.absolutePath}"
        !canRead -> "无读取权限：${dir.absolutePath}（请授予存储权限或使用手动选择目录）"
        logFileCount == 0 -> "目录存在但无日志文件：${dir.absolutePath}"
        else -> "找到 $logFileCount 个日志文件：${dir.absolutePath}"
    }

    companion object {
        /** 扫描指定目录并生成诊断。 */
        fun scan(dir: File): DirScanStatus {
            if (!dir.exists()) {
                return DirScanStatus(dir, exists = false, isDirectory = false, canRead = false, logFileCount = 0)
            }
            if (!dir.isDirectory) {
                return DirScanStatus(dir, exists = true, isDirectory = false, canRead = dir.canRead(), logFileCount = 0)
            }
            val files = try {
                dir.listFiles { f ->
                    f.isFile && f.name.startsWith("log-") && f.name.endsWith(".txt")
                }
            } catch (e: SecurityException) {
                return DirScanStatus(
                    dir, exists = true, isDirectory = true, canRead = false,
                    logFileCount = 0, error = e.message
                )
            }
            // files 为 null 通常表示权限不足或 I/O 错误
            if (files == null) {
                return DirScanStatus(
                    dir, exists = true, isDirectory = true, canRead = dir.canRead(),
                    logFileCount = 0, error = "listFiles 返回 null（权限不足或系统限制）"
                )
            }
            return DirScanStatus(
                dir, exists = true, isDirectory = true,
                canRead = dir.canRead(), logFileCount = files.size
            )
        }
    }
}
