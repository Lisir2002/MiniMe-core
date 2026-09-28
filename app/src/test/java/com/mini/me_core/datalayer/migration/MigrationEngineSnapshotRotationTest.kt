package com.mini.me_core.datalayer.migration

import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 快照**轮转**与**失败自动回滚**语义单测。
 *
 * 背景：单份覆盖式快照在「迁移中途失败 → 重启 → 再次 preOpen」时会被写坏的主库覆盖，
 * 安全网恰好在最需要它的那一刻失效。轮转后至少保住迁移前的若干代现场。
 *
 * 钉住的契约：
 *  1. 新快照写入前，上一份 `.bak` 必须被另存为 `<name>.<时间戳>.bak`，不被覆盖；
 *  2. 历史快照按 [MigrationEngine.MAX_SNAPSHOTS] 淘汰（最旧优先）；
 *  3. `.pre_enc.bak`（源形态备份）**不得**被误判为轮转快照而淘汰；
 *  4. [MigrationEngine.withSnapshotGuard] 失败时回滚、成功时不碰主库，且异常必须继续上抛。
 */
class MigrationEngineSnapshotRotationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FolderPathProvider(root: File) : DatabasePathProvider {
        private val dbDir = root.resolve("databases").apply { mkdirs() }
        private val bakDir = root.resolve("backup").apply { mkdirs() }
        override fun mainDb(lib: LibName): File = dbDir.resolve(lib.fileName)
        override fun backupDir(): File = bakDir
    }

    private fun engine(): Pair<MigrationEngine, FolderPathProvider> {
        val p = FolderPathProvider(tmp.newFolder())
        return MigrationEngine(p) to p
    }

    private fun MigrationEngine.snap(lib: LibName) = snapshot(lib, heavy = false)

    @Test
    fun `second snapshot rotates previous one instead of overwriting it`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.SETTINGS).apply { writeText("V1") }

        engine.snap(LibName.SETTINGS)
        assertEquals("V1", p.snapshotFile(LibName.SETTINGS).readText())
        assertTrue("首次快照无历史可轮转", engine.listSnapshots(LibName.SETTINGS).isEmpty())

        main.writeText("V2")
        engine.snap(LibName.SETTINGS)

        assertEquals("`.bak` 始终是最近一次", "V2", p.snapshotFile(LibName.SETTINGS).readText())
        val history = engine.listSnapshots(LibName.SETTINGS)
        assertEquals("上一份快照必须被轮转保留，而非覆盖", 1, history.size)
        assertEquals("V1", history.first().readText())
    }

    @Test
    fun `history snapshots are pruned to retention limit`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.INFRA)

        repeat(5) { i ->
            main.writeText("V$i")
            engine.snap(LibName.INFRA)
            Thread.sleep(5) // 保证时间戳/lastModified 可区分，避免排序不稳定
        }

        assertEquals("最近一次快照", "V4", p.snapshotFile(LibName.INFRA).readText())

        val history = engine.listSnapshots(LibName.INFRA)
        assertEquals("历史快照应被淘汰到 MAX_SNAPSHOTS-1 份", MigrationEngine.MAX_SNAPSHOTS - 1, history.size)
        assertEquals("历史按时间降序（最新在前）", listOf("V3", "V2"), history.map { it.readText() })
    }

    @Test
    fun `rotated snapshot keeps its wal and shm sidecars`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.T2I).apply { writeText("V1") }
        main.resolveSibling("${main.name}-wal").writeText("WAL1")

        engine.snap(LibName.T2I)
        main.writeText("V2")
        main.resolveSibling("${main.name}-wal").writeText("WAL2")
        engine.snap(LibName.T2I)

        val rotated = engine.listSnapshots(LibName.T2I).first()
        assertEquals("V1", rotated.readText())
        assertEquals("轮转时 wal 必须跟随", "WAL1", rotated.resolveSibling("${rotated.name}-wal").readText())
        assertEquals("最近一次 wal", "WAL2", p.snapshotFile(LibName.T2I).resolveSibling("${LibName.T2I.fileName}.bak-wal").readText())
    }

    @Test
    fun `pre_enc backup is not treated as a rotating snapshot`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.AGENT).apply { writeText("V1") }
        // 源形态备份（明文/旧密钥库），与轮转快照同目录，绝不能被淘汰
        val legacy = p.backupDir().resolve("${LibName.AGENT.fileName}.pre_enc.bak").apply { writeText("PLAINTEXT") }

        repeat(5) {
            main.writeText("V${System.nanoTime()}")
            engine.snap(LibName.AGENT)
            Thread.sleep(5)
        }

        assertTrue("源形态备份必须存活，供人工恢复", legacy.exists())
        assertEquals("PLAINTEXT", legacy.readText())
        assertTrue(
            "源形态备份不得计入轮转历史",
            engine.listSnapshots(LibName.AGENT).none { it.name == legacy.name },
        )
    }

    @Test
    fun `withSnapshotGuard rolls back when block throws`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.SETTINGS).apply { writeText("GOOD") }

        var thrown: Throwable? = null
        try {
            engine.withSnapshotGuard(LibName.SETTINGS) {
                main.writeText("HALF-MIGRATED")
                error("迁移炸了")
            }
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue("失败必须继续上抛，由上层决定降级策略", thrown != null)
        assertEquals("失败后主库应回滚为迁移前内容", "GOOD", main.readText())
        assertEquals("回滚不得破坏快照", "GOOD", p.snapshotFile(LibName.SETTINGS).readText())
    }

    @Test
    fun `withSnapshotGuard keeps result and leaves main untouched on success`() {
        val (engine, p) = engine()
        val main = p.mainDb(LibName.WORKSPACE).apply { writeText("GOOD") }

        val result = engine.withSnapshotGuard(LibName.WORKSPACE) { "OK" }

        assertEquals("OK", result)
        assertEquals("成功路径不得改动主库", "GOOD", main.readText())
        assertTrue("成功路径也应留一份现场快照", p.snapshotFile(LibName.WORKSPACE).exists())
    }

    @Test
    fun `listSnapshots ignores other libraries`() {
        val (engine, p) = engine()
        p.mainDb(LibName.SETTINGS).apply { writeText("S1") }
        p.mainDb(LibName.INFRA).apply { writeText("I1") }

        engine.snap(LibName.SETTINGS)
        engine.snap(LibName.INFRA)
        engine.snap(LibName.SETTINGS) // 产生 SETTINGS 的历史快照

        assertTrue("INFRA 无历史快照", engine.listSnapshots(LibName.INFRA).isEmpty())
        assertEquals(1, engine.listSnapshots(LibName.SETTINGS).size)
    }

    @Test
    fun `snapshot of missing main db creates nothing`() {
        val (engine, p) = engine()
        engine.snap(LibName.CREDENTIALS)
        assertFalse(p.snapshotFile(LibName.CREDENTIALS).exists())
        assertTrue(engine.listSnapshots(LibName.CREDENTIALS).isEmpty())
    }
}
