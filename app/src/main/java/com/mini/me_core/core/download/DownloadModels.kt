package com.mini.me_core.core.download

/**
 * 通用下载任务模型，供 APK 更新 / 源码 zip / 浏览器 / 远程同步等所有下载场景复用。
 *
 * 由各场景的 ViewModel / Manager 自行维护对应数据源，UI 层仅消费本模型渲染统一弹窗。
 */
data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String? = null,
    val localPath: String? = null,
)

enum class DownloadStatus { PENDING, CONFIRMING, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/**
 * 字节大小格式化工具（无硬编码颜色/文案，纯数值→字符串）。
 * 各场景复用，避免重复实现。
 */
object DownloadFormatters {
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    /** 截断 URL 用于展示：保留协议+host，过长则省略中段。 */
    fun truncateUrl(url: String, maxLen: Int = 48): String {
        if (url.length <= maxLen) return url
        return runCatching {
            val parsed = android.net.Uri.parse(url)
            val host = parsed.host ?: return url
            val scheme = parsed.scheme ?: "https"
            val path = parsed.path ?: ""
            val full = "$scheme://$host$path"
            if (full.length <= maxLen) full
            else full.take(maxLen - 1) + "…"
        }.getOrDefault(url.take(maxLen - 1) + "…")
    }

    /** 根据已下载/总大小/速度估算剩余时间，返回可读字符串（秒/分/时）。 */
    fun formatRemaining(downloaded: Long, total: Long, speed: Long): String? {
        if (total <= 0 || speed <= 0 || downloaded <= 0) return null
        val remaining = (total - downloaded).coerceAtLeast(0)
        if (remaining <= 0) return null
        val seconds = remaining / speed
        return when {
            seconds < 60 -> "${seconds.coerceAtLeast(1)}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}
