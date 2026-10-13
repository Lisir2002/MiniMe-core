package com.mini.me_core.feature.workspace.domain.remote

import android.os.FileObserver
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.jvm.Volatile

class SyncEngine(
    private val mount: com.mini.me_core.feature.workspace.domain.model.RemoteMount,
    private val connection: com.mini.me_core.feature.workspace.domain.model.RemoteConnection,
    private val syncClient: RemoteSyncClient,
    private val ignoredPatternsStr: String,
    private val useGitIgnore: Boolean,
    private val maxSyncBatchSize: Int,
    /** 同步方向：bidirectional / upload_only / download_only（未知值按双向处理）。 */
    syncDirection: String = DIRECTION_BIDIRECTIONAL,
    /** 冲突策略提供者（lambda 而非快照：设置页改策略后对已连接引擎即时生效）。 */
    private val conflictStrategyProvider: () -> String = { STRATEGY_REMOTE_OVERWRITE },
) {
    companion object {
        private const val TAG = "SyncEngine"

        const val DIRECTION_BIDIRECTIONAL = "bidirectional"
        const val DIRECTION_UPLOAD_ONLY = "upload_only"
        const val DIRECTION_DOWNLOAD_ONLY = "download_only"

        const val STRATEGY_REMOTE_OVERWRITE = "remote_overwrite"
        const val STRATEGY_LOCAL_OVERWRITE = "local_overwrite"
        const val STRATEGY_SKIP = "skip"
        const val STRATEGY_RENAME = "rename"

        /** 同名冲突时生成 `base(1).ext` 形式的副本名（依次递增直到不冲突）。 */
        fun dedupeName(name: String, taken: (String) -> Boolean): String {
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var n = 1
            while (taken("$base($n)$ext")) n++
            return "$base($n)$ext"
        }
    }

    /** 当前同步方向（@Volatile：监听线程读、UI 线程写）。 */
    @Volatile
    private var direction: String = normalizeDirection(syncDirection)

    /** 热更新同步方向（设置页切换后调用；无需重连）。 */
    fun setSyncDirection(newDirection: String) {
        direction = normalizeDirection(newDirection)
        // 不允许上传的方向没必要继续监听本地（handleLocalChange 内也有同款 guard，双保险）。
        if (!allowsWatch()) stopWatching()
        FileLogger.i(TAG, "Sync direction updated -> $direction (mount=${mount.id})")
    }

    private fun normalizeDirection(d: String): String = when (d) {
        DIRECTION_UPLOAD_ONLY, DIRECTION_DOWNLOAD_ONLY, DIRECTION_BIDIRECTIONAL -> d
        else -> DIRECTION_BIDIRECTIONAL
    }

    private fun allowsUpload(): Boolean = direction != DIRECTION_DOWNLOAD_ONLY
    private fun allowsDownload(): Boolean = direction != DIRECTION_UPLOAD_ONLY
    private fun allowsWatch(): Boolean = allowsUpload()

    /** 当前冲突策略（未知值回落默认 remote_overwrite；provider 只读一次，避免同次决策值漂移）。 */
    private fun strategy(): String {
        val s = conflictStrategyProvider()
        return when (s) {
            STRATEGY_LOCAL_OVERWRITE, STRATEGY_SKIP, STRATEGY_RENAME, STRATEGY_REMOTE_OVERWRITE -> s
            else -> STRATEGY_REMOTE_OVERWRITE
        }
    }

    private val customIgnores = ignoredPatternsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    private var gitIgnorePatterns = emptyList<String>()

    private fun isIgnored(path: String, fileName: String? = null): Boolean {
        val nameToCheck = fileName ?: File(path).name
        val parts = path.split(File.separatorChar, '/')
        
        // 1. 检查自定义忽略规则 (主要针对目录名和文件名)
        if (parts.any { it in customIgnores }) return true
        
        // 2. 检查 .gitignore 规则
        if (useGitIgnore && gitIgnorePatterns.isNotEmpty()) {
            if (gitIgnorePatterns.any { pattern -> matchesGitIgnore(pattern, parts) }) {
                return true
            }
        }
        return false
    }

    /**
     * gitignore 模式匹配（适度实现，不做完整规范解析）：
     * - `*.ext`：匹配任意层级同名扩展名的文件/目录；
     * - `**` 通配前缀：任意层级开始；
     * - 多段模式（如 build 下的 *.log）：在路径段序列的任意位置匹配；
     * - 前导 `/` 锚定因调用方同时传入本地/远程两种完整路径、无法可靠定位工作区根，
     *   这里保守地按非锚定处理（宁可多忽略，不因解析失败漏忽略）。
     * 匹配基于完整路径段序列，本地与远程路径均适用。
     */
    private fun matchesGitIgnore(pattern: String, parts: List<String>): Boolean {
        var p = pattern.trim().trimEnd('/').trimStart('/')
        if (p.isEmpty()) return false
        // 双星通配前缀：任意层级开始
        p = p.removePrefix("**/")
        if (p.isEmpty()) return false
        // *.ext 文件模式：匹配任意层级的文件名
        if (p.startsWith("*.") && '/' !in p) {
            val ext = p.removePrefix("*.")
            return parts.any { it.endsWith(ext) }
        }
        val segs = p.split('/')
        if (segs.size > parts.size) return false
        return (0..parts.size - segs.size).any { start ->
            segs.indices.all { i -> segMatch(segs[i], parts[start + i]) }
        }
    }

    /** 单段匹配：`**` 与 `*` 通配段、精确段。 */
    private fun segMatch(pattern: String, segment: String): Boolean {
        if (pattern == "**") return true
        if ('*' !in pattern) return pattern == segment
        val regex = Regex("^" + Regex.escape(pattern).replace("\\*", ".*") + "$")
        return regex.matches(segment)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileObservers = mutableListOf<FileObserver>()
    private val retryCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    
    // 使用 Channel 做缓冲和防抖
    private val syncChannel = Channel<String>(Channel.UNLIMITED)

    init {
        if (useGitIgnore) {
            val gitignore = File(mount.localMountPath, ".gitignore")
            if (gitignore.exists()) {
                gitIgnorePatterns = gitignore.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { it.trim().removeSuffix("/") }
            }
        }

        scope.launch {
            while (isActive) {
                // 等待队列中的第一个事件
                val firstItem = syncChannel.receive()
                val batch = mutableSetOf(firstItem) // 使用 Set 去重，避免极短时间内同一文件多次触发上传

                // 尝试收集更多就绪的事件，但不超过最大批处理数量
                while (batch.size < maxSyncBatchSize) {
                    val next = syncChannel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }

                // 按顺序处理批次
                for (localPath in batch) {
                    handleLocalChange(localPath)
                }

                // 如果达到批次上限，说明可能正处于大量修改阶段，短暂延迟让服务器缓冲一下
                if (batch.size >= maxSyncBatchSize) {
                    delay(300)
                }
            }
        }
    }

    /**
     * 全量拉取远程工作区到本地。
     * upload_only 方向下直接跳过（本地不应被远程改动）。
     */
    suspend fun downloadWorkspace() = withContext(Dispatchers.IO) {
        if (!allowsDownload()) {
            FileLogger.i(TAG, "Skip downloadWorkspace: direction=$direction (mount=${mount.id})")
            return@withContext
        }
        if (!syncClient.isConnected()) {
            throw IllegalStateException("Client not connected")
        }
        val localRoot = File(mount.localMountPath)
        if (!localRoot.exists()) {
            localRoot.mkdirs()
        }
        
        // 简单递归下载实现
        suspend fun pull(remoteDir: String, localDir: File) {
            val files = syncClient.listFiles(remoteDir)
            for (file in files) {
                if (isIgnored("$remoteDir/${file.name}", file.name)) continue
                val rPath = "$remoteDir/${file.name}"
                val lFile = File(localDir, file.name)
                if (file.isDirectory) {
                    lFile.mkdirs()
                    pull(rPath, lFile)
                } else {
                    // 冲突检测：本地已存在同名文件时按策略处理（remote_overwrite → 覆盖本地；
                    // local_overwrite/skip → 保留本地；rename → 远程另存副本）。
                    val target = if (lFile.exists()) {
                        when (strategy()) {
                            STRATEGY_RENAME -> File(localDir, dedupeName(file.name) { n -> File(localDir, n).exists() })
                            STRATEGY_LOCAL_OVERWRITE, STRATEGY_SKIP -> {
                                FileLogger.i(TAG, "Download conflict ($rPath): strategy=${strategy()} → keep local, skip")
                                null
                            }
                            else -> lFile // remote_overwrite：覆盖本地
                        }
                    } else lFile
                    if (target == null) continue
                    try {
                        syncClient.downloadFile(rPath, target.absolutePath)
                        delay(50) // 延时加大到50ms
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Download Error for $rPath: ${e.message}")
                        forceReconnect()
                    }
                }
            }
        }
        pull(mount.remotePath, localRoot)
    }

    /**
     * 全量推送本地工作区到远程。
     * download_only 方向下直接跳过（本地是只读镜像）。
     */
    suspend fun uploadWorkspace() = withContext(Dispatchers.IO) {
        if (!allowsUpload()) {
            FileLogger.i(TAG, "Skip uploadWorkspace: direction=$direction (mount=${mount.id})")
            return@withContext
        }
        if (!syncClient.isConnected()) {
            throw IllegalStateException("Client not connected")
        }
        val localRoot = File(mount.localMountPath)
        if (!localRoot.exists()) return@withContext

        // 远程目录条目缓存：push 递归同一远程目录只 listFiles 一次（冲突检测用）。
        val remoteDirEntries = mutableMapOf<String, List<RemoteFileInfo>>()
        suspend fun remoteNamesOf(remoteDir: String): List<RemoteFileInfo> =
            remoteDirEntries.getOrPut(remoteDir) {
                try { syncClient.listFiles(remoteDir) } catch (e: Exception) { emptyList() }
            }

        suspend fun push(localDir: File, remoteDir: String) {
            val files = localDir.listFiles() ?: return
            for (file in files) {
                if (isIgnored(file.absolutePath, file.name)) continue
                val rPath = "$remoteDir/${file.name}"
                if (file.isDirectory) {
                    try { syncClient.createDirectory(rPath) } catch (e: Exception) { FileLogger.w(TAG, "createDirectory failed: $rPath", e) }
                    push(file, rPath)
                } else {
                    // 冲突检测：远程已存在同名文件时按策略处理（remote_overwrite/skip → 保护远程；
                    // local_overwrite → 本地覆盖；rename → 上传为远程副本）。
                    val remoteEntries = remoteNamesOf(remoteDir)
                    val remoteExists = remoteEntries.any { it.name == file.name && !it.isDirectory }
                    val targetPath = if (remoteExists) {
                        when (strategy()) {
                            STRATEGY_LOCAL_OVERWRITE -> rPath
                            STRATEGY_RENAME -> "$remoteDir/" + dedupeName(file.name) { n -> remoteEntries.any { it.name == n } }
                            else -> {
                                FileLogger.i(TAG, "Upload conflict ($rPath): strategy=${strategy()} → keep remote, skip")
                                null
                            }
                        }
                    } else rPath
                    if (targetPath == null) continue
                    try {
                        syncClient.uploadFile(file.absolutePath, targetPath)
                        delay(50) // 延时加大到50ms
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Upload Error for $targetPath: ${e.message}")
                        forceReconnect()
                        // 全量同步失败的也放入增量队列兜底重传
                        scope.launch {
                            delay(1000)
                            syncChannel.send(file.absolutePath)
                        }
                    }
                }
            }
        }
        push(localRoot, mount.remotePath)
    }

    /**
     * 开始监听本地镜像目录的改变。
     * download_only 方向下本地变化不应产生上传，故不启动监听。
     */
    fun startWatching() {
        if (!allowsWatch()) {
            FileLogger.i(TAG, "Skip startWatching: direction=$direction (mount=${mount.id})")
            return
        }
        // 防止重复监听：重新进入时先清理旧的 observer（目录删除/重建或重复调用可能残留）
        stopWatching()
        val mask = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM
        // 已监听目录去重：动态新建的子目录可能触发重复的 watchDirectory 递归
        val watchedDirs = mutableSetOf<String>()

        fun watchDirectory(dir: File) {
            val key = dir.absolutePath
            if (!dir.exists() || !dir.isDirectory || isIgnored(dir.absolutePath) || key in watchedDirs) return
            watchedDirs.add(key)
            val observer = object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) return
                    val fullLocalPath = File(dir, path).absolutePath
                    
                    // 如果新建了子目录，递归增加监听
                    if ((event and FileObserver.CREATE) != 0) {
                        val file = File(fullLocalPath)
                        if (file.isDirectory) {
                            watchDirectory(file)
                        }
                    }
                    
                    scope.launch {
                        syncChannel.send(fullLocalPath)
                    }
                }
            }
            observer.startWatching()
            fileObservers.add(observer)
            
            // 递归监听已有的子目录
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    watchDirectory(child)
                }
            }
        }
        
        watchDirectory(File(mount.localMountPath))
    }

    fun stopWatching() {
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()
    }

    private suspend fun handleLocalChange(localPath: String) {
        if (!allowsUpload()) return // download_only：本地只读镜像，任何本地变化都不上传
        if (isIgnored(localPath)) return
        val file = File(localPath)
        // 计算对应的远程路径
        val relativePath = localPath.removePrefix(mount.localMountPath).removePrefix("/")
        val remotePath = "${mount.remotePath}/$relativePath"

        try {
            if (file.exists()) {
                if (file.isDirectory) {
                    syncClient.createDirectory(remotePath)
                    FileLogger.i(TAG, "Sync: Created remote directory $remotePath")
                } else {
                    // 增量上传同样做冲突检测：远程已有同名文件时按策略处理，
                    // 与全量 push 保持一致（remote_overwrite/skip 保护远程，rename 传副本）。
                    val remoteEntries = try { syncClient.listFiles(mount.remotePath + (if (relativePath.contains('/')) "/" + relativePath.substringBeforeLast('/') else "")) } catch (e: Exception) { emptyList() }
                    val remoteExists = remoteEntries.any { it.name == file.name && !it.isDirectory }
                    val targetPath = if (remoteExists) {
                        when (strategy()) {
                            STRATEGY_LOCAL_OVERWRITE -> remotePath
                            STRATEGY_RENAME -> remotePath.substringBeforeLast('/') + "/" + dedupeName(file.name) { n -> remoteEntries.any { it.name == n } }
                            else -> {
                                FileLogger.i(TAG, "Upload conflict ($remotePath): strategy=${strategy()} → keep remote, skip")
                                null
                            }
                        }
                    } else remotePath
                    if (targetPath == null) return
                    syncClient.uploadFile(localPath, targetPath)
                    FileLogger.i(TAG, "Sync: Uploaded to $targetPath")
                    delay(50) // 延时加大到50ms
                }
            } else {
                syncClient.delete(remotePath)
                FileLogger.i(TAG, "Sync: Deleted $remotePath")
                delay(50)
            }
            retryCounts.remove(localPath) // 成功后清除重试计数
        } catch (e: Exception) {
            FileLogger.e(TAG, "Sync Error for $localPath: ${e.message}", e)
            forceReconnect()
            val count = retryCounts.getOrDefault(localPath, 0)
            if (count < 3) {
                retryCounts[localPath] = count + 1
                FileLogger.i(TAG, "Sync: Retry ${count + 1}/3 for $localPath")
                // 将失败的文件重新放入队列
                scope.launch {
                    delay(1000)
                    syncChannel.send(localPath)
                }
            } else {
                FileLogger.e(TAG, "Sync: Max retries reached for $localPath. Giving up.")
                retryCounts.remove(localPath)
            }
        }
    }

    fun shutdown() {
        stopWatching()
        scope.cancel()
    }

    private suspend fun forceReconnect() {
        try {
            syncClient.disconnect()
        } catch (e: Exception) {
            FileLogger.w(TAG, "disconnect failed", e)
        }
        try {
            syncClient.connect(
                connection.host,
                connection.port,
                connection.username,
                RemoteAuth.Password(connection.password)
            )
            FileLogger.i(TAG, "Sync: Force reconnected to server successfully.")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Sync: Failed to force reconnect: ${e.message}")
        }
    }
}
