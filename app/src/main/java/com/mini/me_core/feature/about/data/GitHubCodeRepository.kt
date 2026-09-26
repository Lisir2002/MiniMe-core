package com.mini.me_core.feature.about.data

import android.content.Context
import android.os.Environment
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仓库文件树节点。type 为 [RepoType.DIR] 时 children 按「目录在前、文件在后」字母序排列。
 */
data class RepoFileNode(
    val name: String,
    val path: String,
    val type: RepoType,
    val size: Long = 0L,
    val children: List<RepoFileNode> = emptyList(),
)

enum class RepoType { DIR, FILE }

/** 源码下载进度。 */
data class SourceDownloadProgress(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val filePath: String,
)

/**
 * GitHub 代码仓库数据仓库（单例）。
 *
 * 负责：
 *  - 拉取仓库文件树（/git/trees/{branch}?recursive=1），内存缓存 10 分钟
 *  - 拉取单个文件内容（/contents/{path}，Base64 解码）
 *  - 流式下载仓库 zipball 到 ExternalFilesDir/Downloads
 *
 * 复用应用内统一的 [OkHttpClient]（含代理 / DNS 兜底 / 超时）。
 */
@Singleton
class GitHubCodeRepository @Inject constructor(
    private val client: OkHttpClient,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "GitHubCodeRepo"
        private const val API = "https://api.github.com"
        private const val RAW = "https://raw.githubusercontent.com"

        /** 文件树内存缓存 TTL：10 分钟。 */
        private const val TREE_CACHE_TTL_MS = 10L * 60L * 1000L
    }

    private data class TreeCacheEntry(
        val root: RepoFileNode,
        val cachedAtMs: Long,
    )

    @Volatile
    private var treeCache: TreeCacheEntry? = null

    /** 拉取文件树。同 owner/repo/branch 的结果在 10 分钟内复用内存缓存。 */
    suspend fun fetchRepoTree(owner: String, repo: String, branch: String): RepoFileNode =
        withContext(Dispatchers.IO) {
            treeCache?.let {
                if (System.currentTimeMillis() - it.cachedAtMs < TREE_CACHE_TTL_MS) {
                    return@withContext it.root
                }
            }
            val url = "$API/repos/$owner/$repo/git/trees/$branch?recursive=1"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MiniMe-core-CodeBrowser")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val root = parseTree(body)
                treeCache = TreeCacheEntry(root, System.currentTimeMillis())
                root
            }
        }

    /**
     * 拉取单个文件的文本内容。
     * 通过 raw 端点流式读取，避免 /contents 对大文件的 1MB Base64 限制。
     */
    suspend fun fetchFileContent(owner: String, repo: String, path: String, branch: String): String =
        withContext(Dispatchers.IO) {
            // 优先用 /contents 端点（Base64），小文件可靠；失败再回退 raw。
            val contentsUrl = "$API/repos/$owner/$repo/contents/$path?ref=$branch"
            runCatching {
                val req = Request.Builder()
                    .url(contentsUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MiniMe-core-CodeBrowser")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val b64 = json.optString("content", "").replace("\n", "")
                    if (b64.isNotEmpty()) {
                        return@withContext String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    }
                }
            }
            // 回退 raw
            val rawUrl = "$RAW/$owner/$repo/$branch/$path"
            val req = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "MiniMe-core-CodeBrowser")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }

    /**
     * 把远程文件内容写入应用缓存目录的临时文件，返回本地路径（交给 NativeCodeViewer 高亮）。
     */
    suspend fun downloadFileToTemp(
        owner: String,
        repo: String,
        path: String,
        branch: String,
    ): String = withContext(Dispatchers.IO) {
        val content = fetchFileContent(owner, repo, path, branch)
        val dir = File(context.cacheDir, "codebrowser").apply { mkdirs() }
        val safeName = path.substringAfterLast('/').ifEmpty { "file" }
        val tmp = File(dir, "${System.currentTimeMillis()}_$safeName")
        tmp.writeText(content, Charsets.UTF_8)
        tmp.absolutePath
    }

    /**
     * 流式下载仓库 zipball 到 ExternalFilesDir/Downloads。
     * @return 下载完成的文件绝对路径。
     */
    suspend fun downloadRepoZip(
        owner: String,
        repo: String,
        branch: String,
        onProgress: (SourceDownloadProgress) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val url = "https://github.com/$owner/$repo/archive/refs/heads/$branch.zip"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val targetFile = File(dir, "$repo-$branch.zip")
        if (targetFile.exists()) targetFile.delete()

        val req = Request.Builder().url(url).header("User-Agent", "MiniMe-core-CodeBrowser").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body ?: error("empty body")
            val contentLength = body.contentLength()
            val source = body.source()
            val sink = targetFile.sink().buffer()
            val buffer = okio.Buffer()
            var totalRead = 0L
            var lastPct = -1
            var lastTickMs = System.currentTimeMillis()
            var lastTickBytes = 0L
            var currentSpeed = 0L
            while (true) {
                val read = source.read(buffer, 8192L)
                if (read == -1L) break
                sink.write(buffer, read)
                totalRead += read
                val pct = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt() else 0
                val now = System.currentTimeMillis()
                val elapsed = now - lastTickMs
                if (elapsed >= 200L) {
                    val delta = totalRead - lastTickBytes
                    currentSpeed = if (elapsed > 0) (delta * 1000L) / elapsed else 0L
                    lastTickMs = now
                    lastTickBytes = totalRead
                }
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(SourceDownloadProgress(pct, totalRead, contentLength, currentSpeed, targetFile.absolutePath))
                }
            }
            sink.close()
            source.close()
            if (contentLength > 0) {
                onProgress(SourceDownloadProgress(100, contentLength, contentLength, 0L, targetFile.absolutePath))
            }
            FileLogger.i(TAG, "源码 zip 下载完成: ${targetFile.absolutePath}")
            targetFile.absolutePath
        }
    }

    /** 使缓存失效（切换分支后调用）。 */
    fun invalidateCache() {
        treeCache = null
    }

    // ── 解析 ─────────────────────────────────────────────────────────

    /** 把 GitHub trees API 的扁平条目列表组装成嵌套树。 */
    private fun parseTree(body: String): RepoFileNode {
        val json = JSONObject(body)
        val tree = json.optJSONArray("tree") ?: error("invalid tree json")
        // path -> node
        val byPath = LinkedHashMap<String, RepoFileNode>()
        // 先建立所有节点
        for (i in 0 until tree.length()) {
            val o = tree.getJSONObject(i)
            val path = o.optString("path")
            val typeRaw = o.optString("type")
            val size = o.optLong("size", 0L)
            val type = if (typeRaw == "tree") RepoType.DIR else RepoType.FILE
            val name = path.substringAfterLast('/')
            byPath[path] = RepoFileNode(name = name, path = path, type = type, size = size)
        }
        // 组装父子关系
        val childrenMap = LinkedHashMap<String, MutableList<RepoFileNode>>()
        val root = RepoFileNode(name = "", path = "", type = RepoType.DIR)
        for (node in byPath.values) {
            val lastSlash = node.path.lastIndexOf('/')
            val parentPath = if (lastSlash < 0) "" else node.path.substring(0, lastSlash)
            childrenMap.getOrPut(parentPath) { mutableListOf() }.add(node)
        }
        // 对每个目录排序：目录在前，字母序
        for (list in childrenMap.values) {
            list.sortWith(compareBy({ if (it.type == RepoType.DIR) 0 else 1 }, { it.name.lowercase() }))
        }
        fun buildChildren(dirPath: String): List<RepoFileNode> {
            val kids = childrenMap[dirPath] ?: return emptyList()
            return kids.map { child ->
                if (child.type == RepoType.DIR) {
                    child.copy(children = buildChildren(child.path))
                } else child
            }
        }
        return root.copy(children = buildChildren(""))
    }
}
