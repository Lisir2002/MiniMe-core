package com.mini.me_core.feature.browser.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.webkit.CookieManager
import android.webkit.URLUtil
import androidx.core.app.NotificationCompat
import com.mini.me_core.R
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.workspace.domain.WorkspacePathMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F4.2 下载管理器：负责浏览器下载任务的执行与管理。
 *
 * 功能：
 *  - 下载列表：文件名 / URL / 大小 / 进度 / 速度 / 状态（下载中 / 暂停 / 完成 / 失败 / 取消）；
 *  - 下载中：进度条 + 速度 + 暂停 / 继续 / 取消；
 *  - 完成：打开 / 分享 / 删除 / 查看目录；失败：重试 / 删除；
 *  - 下载通知：Notification 显示进度与完成；
 *  - 存储目录：工作区 downloads（与 [BrowserController] 的 FileProvider 打开链路一致）；
 *  - 下载设置：是否询问、仅 Wi-Fi、完成通知开关（SharedPreferences 持久化）。
 *
 * 线程模型：下载在 IO 协程执行，通过 OkHttp [okhttp3.Call] 支持取消；暂停通过取消当前请求并保留
 * 已下载分片、续传时携带 `Range` 头实现断点续传。
 */
@Singleton
class BrowserDownloadManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    private val pathMapper: WorkspacePathMapper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<List<BrowserDownloadInfo>>(emptyList())
    val downloads: StateFlow<List<BrowserDownloadInfo>> = _downloads.asStateFlow()

    /** 未完成（下载中 / 暂停 / 失败）的下载数，供底栏角标。 */
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    /** 在途任务的控制句柄（Call + 暂停标记）。 */
    private class JobControl(
        val call: okhttp3.Call,
        val paused: AtomicBoolean = AtomicBoolean(false)
    )

    private val jobs = ConcurrentHashMap<String, JobControl>()

    private val prefs = context.getSharedPreferences("browser_download_prefs", Context.MODE_PRIVATE)

    // ── 下载设置（持久化） ───────────────────────────
    var askBeforeDownload: Boolean
        get() = prefs.getBoolean(KEY_ASK, true)
        set(v) = prefs.edit().putBoolean(KEY_ASK, v).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(v) = prefs.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    var notifyOnComplete: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIFY, v).apply()

    /** 下载目录（宿主文件）。 */
    fun downloadDir(): File = pathMapper.toHostFile("~/workspace/downloads").apply { mkdirs() }

    /** 是否处于 Wi-Fi 连接（仅 Wi-Fi 下载设置用）。 */
    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** 入队一个新下载（WebView setDownloadListener 回调）。 */
    fun enqueue(url: String, userAgent: String?, contentDisposition: String?, mimetype: String?) {
        scope.launch { startDownload(url, userAgent, contentDisposition, mimetype, resumeFrom = 0L, existingId = null) }
    }

    /** 暂停下载：取消当前请求，保留分片。 */
    fun pause(id: String) {
        jobs[id]?.let {
            it.paused.set(true)
            it.call.cancel()
        } ?: run {
            update(id) { it.copy(status = "paused") }
        }
    }

    /** 继续下载：从已下载字节断点续传。 */
    fun resume(id: String) {
        val info = _downloads.value.firstOrNull { it.id == id } ?: return
        if (info.status != "paused") return
        jobs[id]?.paused?.set(false)
        scope.launch {
            startDownload(info.url, null, null, null, resumeFrom = info.downloadedBytes, existingId = id)
        }
    }

    /** 取消下载：终止请求并删除分片。 */
    fun cancel(id: String) {
        jobs.remove(id)?.let { it.call.cancel() }
        update(id) { it.copy(status = "cancelled") }
        notifyCancelled(id)
    }

    /** 重试失败 / 取消的下载：从头重新下载。 */
    fun retry(info: BrowserDownloadInfo) {
        scope.launch {
            startDownload(info.url, null, null, null, resumeFrom = 0L, existingId = info.id)
        }
    }

    /** 删除一条下载记录（同时删除已落盘文件）。 */
    fun delete(id: String) {
        jobs.remove(id)?.let { it.call.cancel() }
        val info = _downloads.value.firstOrNull { it.id == id }
        info?.let { runCatching { hostFile(it)?.delete() } }
        _downloads.update { list -> list.filterNot { it.id == id } }
        refreshActiveCount()
    }

    /** 清空下载列表。 */
    fun clear() {
        jobs.values.forEach { runCatching { it.call.cancel() } }
        jobs.clear()
        _downloads.value = emptyList()
        refreshActiveCount()
    }

    /** 下载任务对应的宿主文件（仅完成状态有效）。 */
    fun hostFile(info: BrowserDownloadInfo): File? {
        if (info.status != "done" || info.path.isBlank()) return null
        return runCatching { pathMapper.toHostFile(info.path) }.getOrNull()?.takeIf { it.exists() }
    }

    // ── 内部实现 ───────────────────────────

    private suspend fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        resumeFrom: Long,
        existingId: String?
    ) {
        val id = existingId ?: UUID.randomUUID().toString()
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val dir = downloadDir()
        val outFile = File(dir, fileName)

        val base = _downloads.value.firstOrNull { it.id == id }
        val total = base?.totalBytes ?: -1L
        upsert(
            BrowserDownloadInfo(
                id = id, url = url, fileName = fileName,
                status = "downloading",
                totalBytes = total,
                downloadedBytes = resumeFrom,
                timestamp = base?.timestamp ?: System.currentTimeMillis()
            )
        )

        // 仅 Wi-Fi 限制
        if (wifiOnly && !isOnWifi()) {
            upsert(BrowserDownloadInfo(id, url, fileName, status = "paused", totalBytes = total, downloadedBytes = resumeFrom, timestamp = System.currentTimeMillis()))
            return
        }

        return try {
            val cookies = (runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()) ?: ""
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent ?: "MiniMe-Browser")
                .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
            if (resumeFrom > 0) reqBuilder.header("Range", "bytes=$resumeFrom-")

            val call = okHttp.newCall(reqBuilder.build())
            jobs[id] = JobControl(call)

            call.execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("空响应体")
                val contentLength = body.contentLength()
                val totalBytes = if (resumeFrom > 0 && contentLength > 0) resumeFrom + contentLength
                else if (contentLength > 0) contentLength else -1L
                upsert(current(id).copy(totalBytes = totalBytes))

                val append = resumeFrom > 0 && resp.code == 206
                body.byteStream().use { input ->
                    java.io.FileOutputStream(outFile, append).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes = resumeFrom
                        var lastReport = System.currentTimeMillis()
                        var lastBytes = bytes
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            output.write(buffer, 0, n)
                            bytes += n
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 400) {
                                val speed = ((bytes - lastBytes) * 1000 / (now - lastReport)).coerceAtLeast(0)
                                lastReport = now
                                lastBytes = bytes
                                upsert(current(id).copy(downloadedBytes = bytes, speedBps = speed))
                                notifyProgress(id, fileName, bytes, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }

                jobs.remove(id)
                val containerPath = pathMapper.toContainerPath(outFile.absolutePath)
                upsert(current(id).copy(status = "done", path = containerPath, downloadedBytes = outFile.length(), speedBps = 0))
                notifyComplete(id, fileName, outFile)
                FileLogger.i("BrowserDownloadMgr", "下载完成: ${outFile.absolutePath}")
            }
        } catch (e: Exception) {
            val wasPaused = jobs[id]?.paused?.get() == true
            jobs.remove(id)
            if (wasPaused) {
                upsert(current(id).copy(status = "paused", speedBps = 0))
            } else {
                val cancelled = (existingId != null && _downloads.value.firstOrNull { it.id == id }?.status == "cancelled")
                if (!cancelled) {
                    upsert(current(id).copy(status = "error", error = e.message ?: "下载失败", speedBps = 0))
                    notifyError(id, fileName)
                }
                FileLogger.w("BrowserDownloadMgr", "下载失败: $url", e)
            }
        } finally {
            refreshActiveCount()
        }
    }

    private fun current(id: String): BrowserDownloadInfo =
        _downloads.value.firstOrNull { it.id == id } ?: BrowserDownloadInfo(id = id, url = "")

    private fun upsert(info: BrowserDownloadInfo) {
        _downloads.update { list ->
            val idx = list.indexOfFirst { it.id == info.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = info } else (list + info)
        }
        refreshActiveCount()
    }

    private fun update(id: String, transform: (BrowserDownloadInfo) -> BrowserDownloadInfo) {
        _downloads.update { list ->
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) list else list.toMutableList().also { it[idx] = transform(it[idx]) }
        }
        refreshActiveCount()
    }

    private fun refreshActiveCount() {
        _activeCount.value = _downloads.value.count {
            it.status == "downloading" || it.status == "paused" || it.status == "error"
        }
    }

    // ── 通知 ───────────────────────────

    private val channelId = "mini_browser_downloads"

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(
                    channelId,
                    context.getString(R.string.browser_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = context.getString(R.string.browser_notif_channel_desc) }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun nm(): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun notifyProgress(id: String, fileName: String, bytes: Long, total: Long) {
        ensureChannel()
        val percent = if (total > 0) (bytes * 100 / total).toInt() else 0
        val nid = id.hashCode()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(fileName)
            .setContentText(if (total > 0) "$percent%" else formatBytes(bytes))
            .setProgress(if (total > 0) total.toInt() else 100, if (total > 0) bytes.toInt() else 0, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        runCatching { nm()?.notify(nid, builder.build()) }
    }

    private fun notifyComplete(id: String, fileName: String, file: File) {
        if (!notifyOnComplete) return
        ensureChannel()
        val nid = id.hashCode()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(context.getString(R.string.browser_notif_done))
            .setContentText(fileName)
            .setAutoCancel(true)
        runCatching { nm()?.notify(nid, builder.build()) }
    }

    private fun notifyError(id: String, fileName: String) {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(context.getString(R.string.browser_notif_failed))
            .setContentText(fileName)
            .setAutoCancel(true)
        runCatching { nm()?.notify(id.hashCode(), builder.build()) }
    }

    private fun notifyCancelled(id: String) {
        runCatching { nm()?.cancel(id.hashCode()) }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    companion object {
        private const val KEY_ASK = "ask_before_download"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_NOTIFY = "notify_on_complete"
    }
}
