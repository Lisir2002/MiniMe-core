package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [MigrationEngine] UNREADABLE 分支中「现场另存失败」的 fail-closed 路径。
 *
 * 已有 [MigrationEngineUnreadableDbTest] 覆盖「现场另存成功 → 隔离原文件」。本类补齐
 * 关键的相反分支：当 backup 目录不可用（被普通文件占用 / 无写权限）导致
 * [MigrationEngine.preserveBrokenCopy] 返回 false 时，[MigrationEngine.preOpen] 必须抛
 * [MigrationRejectedException]（reason=[MigrationRejectedException.RejectReason.UNREADABLE_NO_SAFETY_COPY]），
 * 并**绝不**隔离/删除原主库——否则等于在没保住证据的情况下静默清空用户数据。
 */
class MigrationEngineUnreadableNoSafetyCopyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeProbe(var version: Int) : VersionProbe {
        override fun readVersion(lib: LibName): Int = version
    }

    /**
     * 让 backupDir() 指向一个**普通文件**（而非目录）。
     * 这样 [MigrationEngine.preserveBrokenCopy] 里 `dir.isDirectory` 为 false、`dir.mkdirs()`
     * 也会失败（文件已存在同名普通文件）→ 返回 false → preOpen 必须 fail-closed。
     */
    private class BlockedBackupPathProvider(root: File) : DatabasePathProvider {
        private val dbDir = root.resolve("databases").apply { mkdirs() }
        // 关键：backup 路径上放一个普通文件，使 mkdirs() 失败
        val blockedBackup = root.resolve("backup_blocker").apply { writeText("not-a-dir") }
        override fun mainDb(lib: LibName): File = dbDir.resolve(lib.fileName)
        override fun backupDir(): File = blockedBackup
    }

    private fun fakeSchema(version: Int) = object : SqlSchema<QueryResult<Unit>> {
        override val version: Long = version.toLong()
        override fun create(driver: SqlDriver) = QueryResult.Unit
        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ) = QueryResult.Unit
    }

    @Test
    fun `preOpen_throwsUnreadableNoSafetyCopy_whenBackupDirUnusable_andDoesNotQuarantineMainDb`() {
        val p = BlockedBackupPathProvider(tmp.newFolder())
        val main = p.mainDb(LibName.AGENT)
        main.writeText("PRECIOUS-BYTES")

        val engine = MigrationEngine(p, FakeProbe(version = VERSION_UNREADABLE))
        val e = assertThrows(MigrationRejectedException::class.java) {
            engine.preOpen(LibName.AGENT, fakeSchema(3))
        }

        assertEquals(
            MigrationRejectedException.RejectReason.UNREADABLE_NO_SAFETY_COPY,
            e.reason,
        )
        assertTrue(
            "异常信息应点明现场另存失败",
            e.message!!.contains("现场另存失败") || e.message!!.contains("拒绝隔离重建"),
        )
        // 关键不变量：现场没保住就**绝不能**隔离/删除主库
        assertTrue(
            "主库必须原样保留（未被隔离/删除），否则等于静默清空用户数据",
            main.exists(),
        )
        assertEquals("PRECIOUS-BYTES", main.readText())
        // 也不应产生任何 broken 隔离文件
        val isolated = main.parentFile!!.listFiles { f ->
            f.name.startsWith("${main.name}.broken-")
        }
        assertFalse("主库路径下不应出现 .broken- 隔离文件", isolated != null && isolated.isNotEmpty())
    }
}
