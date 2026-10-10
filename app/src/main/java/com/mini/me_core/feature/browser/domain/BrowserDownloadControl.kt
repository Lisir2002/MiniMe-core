package com.mini.me_core.feature.browser.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 下载闭环控制（行为保持自 [BrowserController] 原样搬迁）。
 *
 * 负责模型侧的下载编排：等待下载完成、按 id 取宿主文件、从任意 URL 抓取文件到缓存目录。
 * 底层下载任务管理仍委托注入的 [BrowserDownloadManager]，这里只做编排，
 * 不改任何下载拦截结论与文件落点。
 */
internal class BrowserDownloadControl(
    private val downloadManager: BrowserDownloadManager,
    private val okHttp: OkHttpClient,
    private val context: Context
) {

    /** 下载任务列表（供 list_downloads 工具）。 */
    fun listDownloads(): List<BrowserDownloadInfo> = downloadManager.downloads.value

    /** 等待下载完成（监听下载管理器状态）。 */
    suspend fun waitForDownload(timeoutMs: Long = 30_000): BrowserDownloadInfo? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val downloads = downloadManager.downloads.value
            val recent = downloads.firstOrNull()
            if (recent != null && recent.status == "done") return recent
            if (recent != null && recent.status == "error") return recent
            delay(500)
        }
        return null
    }

    /** 下载文件到 workspace 目录。 */
    fun downloadToWorkspace(downloadId: String): File? {
        val info = downloadManager.downloads.value.firstOrNull { it.id == downloadId }
            ?: return null
        return downloadManager.hostFile(info)
    }

    /** 从 URL 下载文件并返回本地 File。 */
    suspend fun downloadFileFromUrl(url: String): File? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).build()
                val resp = okHttp.newCall(req).execute()
                if (!resp.isSuccessful) return@withContext null
                val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}" }
                val dir = java.io.File(context.cacheDir, "downloads").apply { mkdirs() }
                val file = java.io.File(dir, fileName)
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }.getOrNull()
        }
    }
}
