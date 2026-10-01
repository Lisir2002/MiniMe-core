package com.mini.me_core.feature.update.data

import android.content.Context
import android.os.Environment
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.update.domain.ReleaseInfo
import com.mini.me_core.feature.update.domain.UpdateAvailability
import com.mini.me_core.feature.update.domain.VersionComparator
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 用户主动暂停下载时抛出的异常（普通 Exception，非 CancellationException）。
 * 用于与真实网络失败区分：暂停时保留已下载分片，由 ViewModel 转为 PAUSED 状态。
 */
class DownloadPausedException(cause: Throwable? = null) : Exception("download paused by user", cause)

/**
 * 下载进度回调模型。
 */
data class DownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val filePath: String,
)

/**
 * GitHub Releases 数据仓库（单例）。
 *
 * 负责：
 *  - 调用 /releases/latest 与 /releases?per_page=30
 *  - 解析为 [ReleaseInfo]
 *  - 最新版结果缓存（KVStore，避免频繁请求）
 *  - 启动静默检查 + 更新可用性 StateFlow（供关于页徽标）
 *  - APK 下载（支持取消）
 *
 * 复用项目已有的 [OkHttpClient]（含代理/DNS 兜底/超时）。
 */
@Singleton
class GitHubReleaseRepository @Inject constructor(
    @Named("direct") private val client: OkHttpClient,
    private val kv: KVStore,
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "GitHubReleaseRepo"
        const val NS = "update"
        const val KEY_AUTO_CHECK = "auto_check_enabled"
        const val KEY_LAST_CHECK = "last_check_time"
        const val KEY_CACHED_LATEST_JSON = "cached_latest_json"
        const val KEY_CACHED_LATEST_TAG = "cached_latest_tag"

        /** 缓存 TTL：30 分钟内不重复请求 /releases/latest。用户手动进入更新页时强制刷新不受此限制。 */
        const val CACHE_TTL_MS = 30L * 60L * 1000L

        const val API_LATEST =
            "https://api.github.com/repos/Lisir2002/MiniMe-core/releases/latest"
        const val API_LIST =
            "https://api.github.com/repos/Lisir2002/MiniMe-core/releases?per_page=100"

        /**
         * 下载/API 镜像站备用线路（按优先级排序）。
         * 直连失败后依次尝试，ghproxy 类镜像通过前缀代理原始 URL。
         */
        val MIRROR_PREFIXES = listOf(
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
            "https://mirror.ghproxy.com/",
        )
    }

    private val _availability = MutableStateFlow<UpdateAvailability>(UpdateAvailability.Idle)
    val availability: StateFlow<UpdateAvailability> = _availability.asStateFlow()

    /** 当前在途的网络请求（最后一个 call）。取消/暂停时立即 cancel()。 */
    private val activeCall = AtomicReference<Call?>(null)

    /** 用户主动取消标志：为 true 时下载循环不再切换镜像站重试，并删除已下载文件。 */
    private val downloadCancelled = AtomicBoolean(false)

    /** 用户主动暂停标志：为 true 时停止读取，保留已下载文件以便 Range 续传。 */
    private val downloadPaused = AtomicBoolean(false)

    /** 当前下载目标文件（暂停/恢复时用于查询已下载字节数）。 */
    @Volatile
    private var currentTargetFile: File? = null

    // ── 设置：自动检查开关 ─────────────────────────────────────────────

    val autoCheckEnabled: Flow<Boolean> =
        kv.observeBool(NS, KEY_AUTO_CHECK).map { it ?: true }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        kv.putBool(NS, KEY_AUTO_CHECK, enabled)
    }

    suspend fun isAutoCheckEnabled(): Boolean = autoCheckEnabled.first()

    suspend fun getLastCheckTime(): Long = kv.getInt(NS, KEY_LAST_CHECK) ?: 0L

    // ── 网络请求 ───────────────────────────────────────────────────────

    /**
     * 获取最新 Release。优先读缓存（TTL 内），[force] 强制走网络。
     * @throws IllegalStateException 解析失败 / HTTP 错误。
     */
    suspend fun fetchLatest(force: Boolean = false): ReleaseInfo = withContext(Dispatchers.IO) {
        val cached = readCachedLatest()
        if (!force && cached != null && isCacheFresh()) {
            return@withContext cached
        }
        val release = requestRelease(API_LATEST)
        writeLatestCache(release)
        release
    }

    /**
     * 获取历史 Release 列表（时间倒序已由 API 保证）。
     * @param includePrerelease 是否包含预发布（默认 false，仅正式版）。
     */
    suspend fun fetchHistory(includePrerelease: Boolean = false): List<ReleaseInfo> =
        withContext(Dispatchers.IO) {
            val releases = requestReleaseList(API_LIST)
            releases.filter { r ->
                !r.isDraft &&
                (includePrerelease || !r.isPrerelease) &&
                isMainAppRelease(r.tag)
            }
        }

    /**
     * 判断是否为主应用版本。
     * 主应用版本格式：v0.0.0.21、v0.0.0.24 等（v + 四段数字）。
     * 附属应用格式：logviewer-v0.0.6 等，需过滤。
     */
    private fun isMainAppRelease(tag: String): Boolean =
        tag.matches(Regex("^v\\d+\\.\\d+\\.\\d+\\.\\d+$"))

    /**
     * 启动静默检查：后台调一次 /releases/latest，不弹窗。
     * 结果写入 [availability]（关于页徽标用），并刷新缓存。
     * 开关关闭或缓存未过期时不发请求。
     */
    suspend fun silentCheckOnStartup(currentVersion: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (!isAutoCheckEnabled()) return@withContext

            // 先按缓存给出瞬时结论（红点立刻可见），再决定是否刷新网络。
            val cached = readCachedLatest()
            if (cached != null) {
                emitAvailability(cached, currentVersion)
            }
            if (cached != null && isCacheFresh()) {
                FileLogger.d(TAG, "静默检查：缓存未过期，跳过网络请求")
                return@withContext
            }

            val latest = requestRelease(API_LATEST)
            writeLatestCache(latest)
            emitAvailability(latest, currentVersion)
            FileLogger.i(TAG, "静默检查完成：latest=${latest.tag} current=$currentVersion")
        }.onFailure {
            FileLogger.w(TAG, "静默检查失败（忽略，不打扰用户）", it)
        }
    }

    private fun emitAvailability(latest: ReleaseInfo, currentVersion: String) {
        _availability.value = if (VersionComparator.isNewer(latest.versionName, currentVersion)) {
            UpdateAvailability.UpdateAvailable(latest.tag)
        } else {
            UpdateAvailability.UpToDate
        }
    }

    // ── 缓存 ─────────────────────────────────────────────────────────

    private fun isCacheFresh(): Boolean {
        val last = getLastCheckTimeBlocking()
        if (last <= 0L) return false
        return System.currentTimeMillis() - last < CACHE_TTL_MS
    }

    private fun getLastCheckTimeBlocking(): Long = kv.getInt(NS, KEY_LAST_CHECK) ?: 0L

    private fun readCachedLatest(): ReleaseInfo? {
        val json = kv.getString(NS, KEY_CACHED_LATEST_JSON) ?: return null
        return runCatching { ReleaseJsonParser.parseOne(json) }.getOrNull()
    }

    private fun writeLatestCache(release: ReleaseInfo) {
        kv.putString(NS, KEY_CACHED_LATEST_TAG, release.tag)
        kv.putString(NS, KEY_CACHED_LATEST_JSON, releaseBodyJson(release))
        kv.putInt(NS, KEY_LAST_CHECK, System.currentTimeMillis())
    }

    /**
     * 将 ReleaseInfo 序列化为最小 JSON 存缓存（仅保留下次展示所需字段，避免依赖 Gson @Serialize）。
     */
    private fun releaseBodyJson(r: ReleaseInfo): String = buildString {
        append('{')
        append("\"tag_name\":\"").append(escape(r.tag)).append("\",")
        append("\"name\":\"").append(escape(r.name)).append("\",")
        append("\"published_at\":\"").append(escape(iso(r.publishedAt))).append("\",")
        append("\"body\":\"").append(escape(r.body ?: "")).append("\",")
        append("\"html_url\":\"").append(escape(r.htmlUrl)).append("\",")
        append("\"prerelease\":").append(r.isPrerelease).append(',')
        append("\"draft\":").append(r.isDraft).append(',')
        append("\"assets\":[")
        r.assets.forEachIndexed { i, a ->
            if (i > 0) append(',')
            append("{\"name\":\"").append(escape(a.name)).append("\",")
            append("\"browser_download_url\":\"").append(escape(a.downloadUrl)).append("\",")
            append("\"size\":").append(a.sizeBytes).append(',')
            append("\"content_type\":\"").append(escape(a.contentType)).append("\"}")
        }
        append("]}")
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "").replace("\t", " ")

    private fun iso(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(epochMs))

    // ── JSON 解析（带镜像站备用线路）──────────────────────────────────

    private fun requestRelease(url: String): ReleaseInfo =
        requestWithMirrors(url) { body -> ReleaseJsonParser.parseOne(body) }

    private fun requestReleaseList(url: String): List<ReleaseInfo> =
        requestWithMirrors(url) { body -> ReleaseJsonParser.parseList(body) }

    /**
     * 带镜像站备用线路的 HTTP 请求。
     * 先直连原始 URL，失败后依次尝试 [MIRROR_PREFIXES] 中的镜像前缀，全部失败才抛异常。
     * 镜像站可能返回 HTML 错误页，校验 Content-Type 为 JSON 才解析，否则继续下一个。
     */
    private fun <T> requestWithMirrors(originalUrl: String, parser: (String) -> T): T {
        val urls = buildList {
            add(originalUrl)
            addAll(MIRROR_PREFIXES.map { prefix -> prefix + originalUrl })
        }
        var lastError: Throwable? = null
        urls.forEachIndexed { index, url ->
            val label = if (index == 0) "直连" else "镜像${MIRROR_PREFIXES[index - 1]}"
            runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MiniMe-core-UpdateChecker")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val contentType = resp.header("Content-Type").orEmpty()
                    val body = resp.body?.string().orEmpty()
                    // 镜像站可能返回 HTML 错误页，校验为 JSON 才解析
                    if (!contentType.contains("json", ignoreCase = true) &&
                        !body.trimStart().startsWith("{") && !body.trimStart().startsWith("[")
                    ) {
                        error("非 JSON 响应（Content-Type=$contentType），可能是镜像站错误页")
                    }
                    return parser(body)
                }
            }.onFailure { e ->
                lastError = e
                if (index == 0) {
                    FileLogger.i(TAG, "直连失败，切换镜像: ${e.message}")
                } else if (index < urls.size - 1) {
                    FileLogger.i(TAG, "$label 失败，切换下一个镜像: ${e.message}")
                }
            }
        }
        throw lastError ?: IllegalStateException("所有线路均失败")
    }

    // ── 下载 ─────────────────────────────────────────────────────────

    /**
     * 下载 APK 到私有下载目录。回调在 IO 线程。
     *
     * @param resumeFrom 从指定字节数续传（携带 `Range: bytes=<n>-`）。
     *   - 服务器返回 206：追加写入；
     *   - 服务器返回 200（不支持 Range）：删除旧文件从头下载。
     * @return 下载完成的文件绝对路径。
     * @throws CancellationException 用户主动取消。
     * @throws DownloadPausedException 用户主动暂停（文件已保留）。
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        onProgress: (DownloadProgress) -> Unit,
        resumeFrom: Long = 0L,
    ): String = withContext(Dispatchers.IO) {
        // 新一轮下载（含恢复）都先复位取消/暂停标志。
        downloadCancelled.set(false)
        downloadPaused.set(false)

        val originalUrl = release.downloadUrl ?: error("no apk asset")
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val fileName = "MiniMe-core-${release.versionName}.apk"
        val targetFile = File(dir, fileName)
        val filePath = targetFile.absolutePath
        currentTargetFile = targetFile

        // 构建下载 URL 列表：直连 + 镜像站备用线路
        val downloadUrls = buildList {
            add(originalUrl)
            addAll(MIRROR_PREFIXES.map { prefix -> prefix + originalUrl })
        }

        // 协程被取消（如 ViewModel 清除）时，同步取消在途网络请求。
        val job = coroutineContext[Job]
        val cancelHandle = job?.invokeOnCompletion {
            activeCall.getAndSet(null)?.runCatching { cancel() }
        }

        var lastError: Throwable? = null
        try {
            // 用普通 for 循环替代 forEachIndexed：取消/暂停时直接抛出，
            // 不允许 runCatching 吞掉异常后继续尝试下一个镜像。
            for ((index, url) in downloadUrls.withIndex()) {
                // 每次进入镜像前检查：取消/暂停立即终止整个循环
                if (downloadCancelled.get()) {
                    targetFile.delete()
                    throw CancellationException("download cancelled by user")
                }
                if (downloadPaused.get()) {
                    throw DownloadPausedException()
                }

                val reqBuilder = Request.Builder().url(url)
                if (resumeFrom > 0L) {
                    reqBuilder.header("Range", "bytes=$resumeFrom-")
                }
                val call = client.newCall(reqBuilder.build())
                activeCall.set(call)

                try {
                    call.execute().use { resp ->
                        val supportsRange = resp.code == 206
                        // 服务器不支持 Range（返回 200）：从头下载，删除旧分片
                        val effectiveResumeFrom =
                            if (resumeFrom > 0L && supportsRange) resumeFrom else 0L
                        if (resumeFrom > 0L && !supportsRange && targetFile.exists()) {
                            targetFile.delete()
                        }
                        if (!resp.isSuccessful && !supportsRange) error("HTTP ${resp.code}")
                        val body = resp.body ?: error("empty body")
                        val partialLength = body.contentLength()
                        val contentLength = if (effectiveResumeFrom > 0L && partialLength > 0L)
                            effectiveResumeFrom + partialLength else partialLength

                        val source = body.source()
                        val sink = if (effectiveResumeFrom > 0L)
                            FileOutputStream(targetFile, true).sink().buffer()
                        else
                            targetFile.sink().buffer()

                        var totalRead = effectiveResumeFrom
                        val buffer = okio.Buffer()
                        var lastPct = -1
                        var lastTickMs = System.currentTimeMillis()
                        var lastTickBytes = effectiveResumeFrom
                        var currentSpeed = 0L

                        try {
                            while (true) {
                                // 每个 buffer 读取后都检查取消/暂停
                                if (downloadCancelled.get()) {
                                    runCatching { sink.close() }
                                    runCatching { source.close() }
                                    call.cancel()
                                    targetFile.delete()
                                    throw CancellationException("download cancelled by user")
                                }
                                if (downloadPaused.get()) {
                                    runCatching { sink.close() }
                                    runCatching { source.close() }
                                    call.cancel()
                                    // 暂停：保留已下载文件，不删除
                                    throw DownloadPausedException()
                                }
                                val read = source.read(buffer, 8192L)
                                if (read == -1L) break
                                sink.write(buffer, read)
                                totalRead += read

                                val pct = if (contentLength > 0L)
                                    ((totalRead * 100L) / contentLength).toInt() else 0

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastTickMs
                                if (elapsed >= 200L) {
                                    val delta = totalRead - lastTickBytes
                                    currentSpeed =
                                        if (elapsed > 0L) (delta * 1000L) / elapsed else 0L
                                    lastTickMs = now
                                    lastTickBytes = totalRead
                                }

                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(
                                        DownloadProgress(
                                            pct, totalRead, contentLength, currentSpeed, filePath,
                                        ),
                                    )
                                }
                            }
                            sink.close()
                            source.close()
                        } catch (e: CancellationException) {
                            runCatching { sink.close() }
                            runCatching { source.close() }
                            throw e
                        } catch (e: DownloadPausedException) {
                            runCatching { sink.close() }
                            runCatching { source.close() }
                            throw e
                        }

                        if (contentLength > 0L) {
                            onProgress(DownloadProgress(100, contentLength, contentLength, 0L, filePath))
                        }
                        return@withContext filePath
                    }
                } catch (e: CancellationException) {
                    activeCall.compareAndSet(call, null)
                    throw e
                } catch (e: DownloadPausedException) {
                    activeCall.compareAndSet(call, null)
                    throw e
                } catch (e: Throwable) {
                    activeCall.compareAndSet(call, null)
                    // call.cancel() 后阻塞 read 会抛 IOException，此时按取消/暂停处理
                    if (downloadCancelled.get()) {
                        targetFile.delete()
                        throw CancellationException("download cancelled by user", e)
                    }
                    if (downloadPaused.get()) {
                        // 暂停触发的流中断：保留文件
                        throw DownloadPausedException(e)
                    }
                    // 普通失败：删除不完整文件，继续尝试下一个镜像
                    targetFile.delete()
                    lastError = e
                    val label = if (index == 0) "直连" else "镜像${MIRROR_PREFIXES[index - 1]}"
                    FileLogger.i(TAG, "下载$label 失败，切换下一个镜像: ${e.message}")
                }
            }
            throw lastError ?: IllegalStateException("所有下载线路均失败")
        } finally {
            cancelHandle?.dispose()
            activeCall.getAndSet(null)?.runCatching { cancel() }
        }
    }

    /** 当前已落盘的字节数（暂停后恢复续传用）。 */
    fun currentDownloadedBytes(): Long =
        currentTargetFile?.takeIf { it.exists() }?.length() ?: 0L

    /** 暂停当前下载：设置暂停标志并取消在途 call，保留已下载文件。 */
    fun pauseDownload() {
        downloadPaused.set(true)
        activeCall.getAndSet(null)?.runCatching { cancel() }
    }

    /** 恢复下载前复位暂停标志（实际续传由调用方重新调用 downloadApk(resumeFrom=...)）。 */
    fun resumeDownload() {
        downloadPaused.set(false)
        downloadCancelled.set(false)
    }

    /** 取消当前下载：设置取消标志、取消在途 call、删除已下载临时文件。 */
    fun cancelDownload() {
        downloadCancelled.set(true)
        downloadPaused.set(false)
        activeCall.getAndSet(null)?.runCatching { cancel() }
        currentTargetFile?.takeIf { it.exists() }?.delete()
    }

    /** 格式化字节大小为可读字符串（供 UI 复用）。 */
    fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    /** 格式化发布时间为 yyyy-MM-dd HH:mm。 */
    fun formatDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "--"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}
