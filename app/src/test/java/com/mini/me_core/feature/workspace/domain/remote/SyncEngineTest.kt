package com.mini.me_core.feature.workspace.domain.remote

import com.mini.me_core.feature.workspace.domain.model.RemoteConnection
import com.mini.me_core.feature.workspace.domain.model.RemoteMount
import com.mini.me_core.feature.workspace.domain.model.RemoteProtocol
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * SyncEngine 方向约束 + 冲突策略回归测试（issue #2 / #3）。
 *
 * 覆盖矩阵：
 * - 方向：download_only 禁上传（全量 + 增量路径）、upload_only 禁下载、双向全放行；
 *   热更新 setSyncDirection 即时生效；未知方向回落 bidirectional。
 * - 下载侧冲突：remote_overwrite 覆盖本地 / local_overwrite、skip 保留本地 / rename 生成本地副本。
 * - 上传侧冲突：remote_overwrite、skip 保护远程 / local_overwrite 覆盖远程 / rename 生成远程副本。
 * - 未知策略回落 remote_overwrite（下载覆盖本地、上传保护远程）。
 *
 * 说明：startWatching 依赖 android.os.FileObserver（真机监听），其方向门控与
 * uploadWorkspace 共用 allowsWatch()=allowsUpload()，由上传路径的用例间接覆盖，
 * 本测试不触达 FileObserver（纯 JVM 环境构造器为桩实现）。
 */
class SyncEngineTest {

    private lateinit var tmp: File
    private lateinit var localRoot: File
    private lateinit var client: FakeRemoteSyncClient

    @Before
    fun setUp() {
        tmp = File(System.getProperty("java.io.tmpdir"), "sync-engine-test-${System.nanoTime()}")
        localRoot = File(tmp, "local").apply { mkdirs() }
        client = FakeRemoteSyncClient()
    }

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun engine(
        direction: String = SyncEngine.DIRECTION_BIDIRECTIONAL,
        strategy: () -> String = { SyncEngine.STRATEGY_REMOTE_OVERWRITE },
        ignored: String = "",
    ): SyncEngine = SyncEngine(
        mount = RemoteMount(
            id = "m1",
            connectionId = "c1",
            remotePath = "/remote",
            localMountPath = localRoot.absolutePath,
            syncDirection = direction,
        ),
        connection = RemoteConnection(
            id = "c1", name = "c", protocol = RemoteProtocol.SFTP,
            host = "h", port = 22, username = "u",
        ),
        syncClient = client,
        ignoredPatternsStr = ignored,
        useGitIgnore = false,
        maxSyncBatchSize = 5,
        syncDirection = direction,
        conflictStrategyProvider = strategy,
    )

    // ══════════════ 纯函数 ══════════════
    @Test
    fun `dedupeName generates incrementing copy names`() {
        val taken = setOf("a.txt", "a(1).txt")
        assertEquals("a(2).txt", SyncEngine.dedupeName("a.txt") { it in taken })
        // 无扩展名
        assertEquals("README(1)", SyncEngine.dedupeName("README") { it == "README" })
        // 隐藏文件：lastIndexOf('.')==0 不当扩展名，整体作为 base
        assertEquals(".env(1)", SyncEngine.dedupeName(".env") { it == ".env" })
        // 首个名字未被占用时直接返回 (1)
        assertEquals("a(1).txt", SyncEngine.dedupeName("a.txt") { false })
    }

    // ══════════════ 方向约束 ══════════════

    @Test
    fun `download_only blocks upload but allows download`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(direction = SyncEngine.DIRECTION_DOWNLOAD_ONLY)
        try {
            e.uploadWorkspace()
            assertTrue("download_only 不应产生任何上传", client.uploaded.isEmpty())
            assertEquals("REMOTE", client.remote["/remote/a.txt"])

            e.downloadWorkspace()
            assertEquals("REMOTE", File(localRoot, "a.txt").readText())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `upload_only blocks download but allows upload`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(direction = SyncEngine.DIRECTION_UPLOAD_ONLY)
        try {
            e.downloadWorkspace()
            assertTrue("upload_only 不应产生任何下载", client.downloaded.isEmpty())
            assertEquals("LOCAL", File(localRoot, "a.txt").readText())

            e.uploadWorkspace()
            // 默认 remote_overwrite 策略保护远程：远程已有同名 → 跳过上传
            assertEquals("REMOTE", client.remote["/remote/a.txt"])
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `bidirectional allows both directions`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/b.txt"] = "REMOTE_B"
        val e = engine()
        try {
            e.uploadWorkspace()
            // b 远程不存在 → 上传；a 远程不存在 → 上传
            assertEquals("LOCAL", client.remote["/remote/a.txt"])
            e.downloadWorkspace()
            assertEquals("REMOTE_B", File(localRoot, "b.txt").readText())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `setSyncDirection hot update takes effect immediately`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        val e = engine()
        try {
            e.uploadWorkspace()
            assertEquals("LOCAL", client.remote["/remote/a.txt"])
            assertEquals(1, client.uploaded.size)

            e.setSyncDirection(SyncEngine.DIRECTION_DOWNLOAD_ONLY)
            e.uploadWorkspace()
            assertEquals("热更新为 download_only 后上传应被拒绝", 1, client.uploaded.size)
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `unknown direction falls back to bidirectional`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        val e = engine(direction = "bogus_direction")
        try {
            e.uploadWorkspace()
            assertEquals("未知方向应回落双向（上传放行）", "LOCAL", client.remote["/remote/a.txt"])
        } finally {
            e.shutdown()
        }
    }

    // ══════════════ 下载侧冲突策略 ══════════════

    @Test
    fun `download conflict remote_overwrite overwrites local`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_REMOTE_OVERWRITE })
        try {
            e.downloadWorkspace()
            assertEquals("REMOTE", File(localRoot, "a.txt").readText())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `download conflict local_overwrite keeps local`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_LOCAL_OVERWRITE })
        try {
            e.downloadWorkspace()
            assertEquals("local_overwrite 应保留本地文件", "LOCAL", File(localRoot, "a.txt").readText())
            assertTrue("本地已存在时不应触发下载调用", client.downloaded.isEmpty())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `download conflict skip keeps local`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_SKIP })
        try {
            e.downloadWorkspace()
            assertEquals("skip 应保留本地文件", "LOCAL", File(localRoot, "a.txt").readText())
            assertTrue(client.downloaded.isEmpty())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `download conflict rename keeps local and saves remote copy`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_RENAME })
        try {
            e.downloadWorkspace()
            assertEquals("rename 应保留本地原文件", "LOCAL", File(localRoot, "a.txt").readText())
            assertEquals("rename 应把远程内容写入副本", "REMOTE", File(localRoot, "a(1).txt").readText())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `download without local conflict proceeds normally`() = runBlocking {
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine()
        try {
            e.downloadWorkspace()
            assertEquals("REMOTE", File(localRoot, "a.txt").readText())
        } finally {
            e.shutdown()
        }
    }

    // ══════════════ 上传侧冲突策略 ══════════════

    @Test
    fun `upload conflict remote_overwrite protects remote`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_REMOTE_OVERWRITE })
        try {
            e.uploadWorkspace()
            assertEquals("remote_overwrite 应保护远程不被本地覆盖", "REMOTE", client.remote["/remote/a.txt"])
            assertTrue("冲突文件不应触发上传调用", client.uploaded.isEmpty())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `upload conflict skip protects remote`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_SKIP })
        try {
            e.uploadWorkspace()
            assertEquals("REMOTE", client.remote["/remote/a.txt"])
            assertTrue(client.uploaded.isEmpty())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `upload conflict local_overwrite overwrites remote`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_LOCAL_OVERWRITE })
        try {
            e.uploadWorkspace()
            assertEquals("local_overwrite 应用本地覆盖远程", "LOCAL", client.remote["/remote/a.txt"])
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `upload conflict rename uploads remote copy`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { SyncEngine.STRATEGY_RENAME })
        try {
            e.uploadWorkspace()
            assertEquals("rename 应保留远程原文件", "REMOTE", client.remote["/remote/a.txt"])
            assertEquals("rename 应把本地上传为远程副本", "LOCAL", client.remote["/remote/a(1).txt"])
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `unknown strategy falls back to remote_overwrite semantics`() = runBlocking {
        File(localRoot, "a.txt").writeText("LOCAL")
        client.remote["/remote/a.txt"] = "REMOTE"
        val e = engine(strategy = { "bogus_strategy" })
        try {
            // 上传侧：回落 remote_overwrite → 保护远程
            e.uploadWorkspace()
            assertEquals("REMOTE", client.remote["/remote/a.txt"])
            // 下载侧：回落 remote_overwrite → 覆盖本地
            File(localRoot, "b.txt").writeText("LOCAL_B")
            client.remote["/remote/b.txt"] = "REMOTE_B"
            e.downloadWorkspace()
            assertEquals("REMOTE_B", File(localRoot, "b.txt").readText())
        } finally {
            e.shutdown()
        }
    }

    @Test
    fun `ignored files are skipped in both directions`() = runBlocking {
        File(localRoot, "node_modules").mkdirs()
        File(localRoot, "node_modules/x.txt").writeText("X")
        client.remote["/remote/node_modules/y.txt"] = "Y"
        val e = engine(ignored = "node_modules")
        try {
            e.uploadWorkspace()
            e.downloadWorkspace()
            assertFalse("忽略目录不应上传", client.remote.containsKey("/remote/node_modules/x.txt"))
            assertFalse("忽略目录不应下载到本地", File(localRoot, "node_modules/y.txt").exists())
        } finally {
            e.shutdown()
        }
    }

    /** 内存版 RemoteSyncClient：记录上传/下载调用序列，语义对齐真实实现的目录递归。 */
    private class FakeRemoteSyncClient : RemoteSyncClient {
        val remote = ConcurrentHashMap<String, String>()
        val dirs = ConcurrentHashMap.newKeySet<String>().apply { add("/remote") }
        val uploaded = mutableListOf<Pair<String, String>>()
        val downloaded = mutableListOf<Pair<String, String>>()

        override suspend fun connect(host: String, port: Int, username: String, auth: RemoteAuth) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun isConnected(): Boolean = true

        override suspend fun listFiles(remotePath: String): List<RemoteFileInfo> {
            val prefix = remotePath.trimEnd('/') + "/"
            val names = mutableSetOf<String>()
            dirs.filter { it.startsWith(prefix) }.forEach { p ->
                val rest = p.removePrefix(prefix)
                if (rest.isNotEmpty() && '/' !in rest) names += rest
            }
            remote.keys.filter { it.startsWith(prefix) }.forEach { p ->
                val rest = p.removePrefix(prefix)
                if (rest.isNotEmpty() && '/' !in rest) names += rest
            }
            return names.map { n ->
                val full = prefix + n
                RemoteFileInfo(
                    name = n,
                    isDirectory = full in dirs,
                    size = remote[full]?.length?.toLong() ?: 0L,
                    lastModified = 0L,
                )
            }
        }

        override suspend fun downloadFile(remotePath: String, localPath: String) {
            downloaded += remotePath to localPath
            File(localPath).writeText(remote[remotePath] ?: error("fake remote missing: $remotePath"))
        }

        override suspend fun uploadFile(localPath: String, remotePath: String) {
            uploaded += localPath to remotePath
            remote[remotePath] = File(localPath).readText()
        }

        override suspend fun createDirectory(remotePath: String) {
            dirs += remotePath
        }

        override suspend fun delete(remotePath: String) {
            remote.remove(remotePath)
            dirs.remove(remotePath)
        }
    }
}
