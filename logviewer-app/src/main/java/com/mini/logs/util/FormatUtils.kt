package com.mini.logs.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间/数字格式化工具。 */
object FormatUtils {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun formatTime(timestamp: Long): String =
        if (timestamp > 0) timeFormat.format(Date(timestamp)) else ""

    fun formatDateTime(timestamp: Long): String =
        if (timestamp > 0) dateTimeFormat.format(Date(timestamp)) else ""

    fun formatDate(timestamp: Long): String =
        if (timestamp > 0) dateFormat.format(Date(timestamp)) else ""

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) return ""
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            minutes < 1440 -> "${minutes / 60}小时前"
            else -> "${minutes / 1440}天前"
        }
    }

    fun formatCount(count: Int): String =
        if (count >= 10000) String.format("%.1f万", count / 10000.0)
        else count.toString()

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    /** 设备信息摘要，用于导出。 */
    fun deviceInfo(): String = buildString {
        append("device=").append(android.os.Build.MANUFACTURER).append(' ')
            .append(android.os.Build.MODEL).append('\n')
        append("android=").append(android.os.Build.VERSION.RELEASE)
            .append(" (sdk ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
        append("app=MiniMe Logs 1.0.0\n")
    }
}
