package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 加密库「打不开」语义回归测试（对应启动日志：
 * `readVersion(AGENT) 加密库只读打开失败（可能损坏/密钥不匹配）`）。
 *
 * 修复前：探测失败返回 0，与「文件不存在」同义 → [decidePreOpen] 恒判 [PreOpenAction.FRESH]
 * → 既不生成迁移前快照，又让上层把「有数据的坏库」当全新库处理，自愈重建即静默清空历史数据。
 *
 * 修复后：探测失败返回 [VERSION_UNREADABLE]（-1），[PreOpenAction.UNREADABLE] 分支
 * 执行「先快照保命 → 再隔离原文件 → 才允许 driver 以全新库重建」。
 *
 * 纯 JVM，不依赖 Android framework。
 */
class MigrationEngineUnreadableDbTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeProbe(var version: Int) : VersionProbe {
        override fun readVersion(lib: LibName): Int = version
    }

    private class FolderPathProvider(root: File) : DatabasePathProvider {
        private val dbDir = root.resolve("databases").apply { mkdirs() }
        private val bakDir = root.resolve("backup").apply { mkdirs() }
        override fun mainDb(lib: LibName): File = dbDir.resolve(lib.fileName)
        override fun backupDir(): File = bakDir
    }

    private fun fakeSchema(version: Int) = object : SqlSchema<QueryResult<Unit>> {
        override val version: Long = version.toLong()
        override fun create(driver: SqlDriver) = QueryResult.Unit
        override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion) = QueryResult.Unit
    }

    @Test
    fun `decidePreOpen 必须把不可读与全新库区分开`() {
        assertEquals(PreOpenAction.UNREADABLE, decidePreOpen(VERSION_UNREADABLE, 3))
        assertEquals(PreOpenAction.FRESH, decidePreOpen(0, 3))
        assertNotEquals("不可读库绝不可被判为全新库", PreOpenAction.FRESH, decidePreOpen(VERSION_UNREADABLE, 3))
    }

    @Test
    fun `preOpen 对不可读库先快照再隔离，绝不按全新库处理`() {
        val p = FolderPathProvider(tmp.newFolder())
        val main = p.mainDb(LibName.AGENT)
        main.writeText("CORRUPT-BUT-PRECIOUS")

        val engine = MigrationEngine(p, FakeProbe(version = VERSION_UNREADABLE))
        val action = engine.preOpen(LibName.AGENT, fakeSchema(3))

        assertEquals(PreOpenAction.UNREADABLE, action)

        val bak = p.snapshotFile(LibName.AGENT)
        assertTrue("不可读库必须先快照保命", bak.exists())
        assertEquals("快照必须保住原始字节，否则人工恢复无从谈起", "CORRUPT-BUT-PRECIOUS", bak.readText())

        assertFalse("隔离后主库路径须空出，供 driver 以全新库重建", main.exists())
        val isolated = main.parentFile!!.listFiles { f -> f.name.startsWith("${main.name}.broken-") }
        assertTrue("原文件必须被隔离保留，而不是删除", isolated != null && isolated.isNotEmpty())
    }

    @Test
    fun `preOpen 对不可读但文件缺失的库不产生快照`() {
        val p = FolderPathProvider(tmp.newFolder())
        val engine = MigrationEngine(p, FakeProbe(version = VERSION_UNREADABLE))
        val action = engine.preOpen(LibName.INFRA, fakeSchema(3))

        assertEquals(PreOpenAction.UNREADABLE, action)
        assertFalse("文件不存在时无需快照", p.snapshotFile(LibName.INFRA).exists())
    }
}
