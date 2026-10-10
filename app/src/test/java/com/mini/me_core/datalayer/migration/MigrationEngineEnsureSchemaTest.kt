package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [MigrationEngine.ensureSchema] 打开后兜底分支测试（与 preOpen 路径互补）。
 *
 * preOpen 在 driver 构造前用 [VersionProbe] 探测真实版本并做快照；ensureSchema 在 driver
 * 打开后兜底校验。本测试用真实 JVM sqlite 驱动钉住三个分支：
 *  - current == target：no-op（不跑 codeMigrations）；
 *  - current < target：preOpen 未跑时补跑 codeMigrations；
 *  - current > target：版本回退，抛 [MigrationRejectedException]（VERSION_DOWNGRADE，fail-closed）。
 *
 * 用文件型 JdbcSqliteDriver（非内存）以便 PRAGMA user_version 跨连接稳定。
 */
class MigrationEngineEnsureSchemaTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun openDb(file: File): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")

    /** 直接写 PRAGMA user_version，模拟「库已被旧版/高版本代码打开过」的现场。 */
    private fun setUserVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0, null)
    }

    private fun getUserVersion(driver: SqlDriver): Long =
        driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            val v = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
            QueryResult.Value(v)
        }, 0, null).value

    private fun fakeSchema(version: Long) = object : SqlSchema<QueryResult<Unit>> {
        override val version: Long = version
        override fun create(driver: SqlDriver) = QueryResult.Unit
        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ) = QueryResult.Unit
    }

    @Test
    fun `ensureSchema currentEqualsTarget_isNoOp_andDoesNotRunCodeMigrations`() {
        val engine = MigrationEngine(DatabasePathProviderStub(tmp.newFolder()))
        val dbFile = tmp.newFile("v3.db")
        val driver = openDb(dbFile)
        setUserVersion(driver, 3)

        var codeMigrationRan = false
        engine.ensureSchema(
            lib = com.mini.me_core.datalayer.engine.LibName.SETTINGS,
            driver = driver,
            schema = fakeSchema(3),
            codeMigrations = listOf(
                CodeMigration(from = 1, to = 3) { codeMigrationRan = true },
            ),
        )

        assertFalse("current==target 时不应补跑 codeMigrations", codeMigrationRan)
        assertEquals(3L, getUserVersion(driver))
        driver.close()
    }

    @Test
    fun `ensureSchema currentLessTarget_runsEligibleCodeMigrations`() {
        val engine = MigrationEngine(DatabasePathProviderStub(tmp.newFolder()))
        val dbFile = tmp.newFile("v2.db")
        val driver = openDb(dbFile)
        setUserVersion(driver, 2)

        val ran = mutableListOf<Int>()
        engine.ensureSchema(
            lib = com.mini.me_core.datalayer.engine.LibName.SETTINGS,
            driver = driver,
            schema = fakeSchema(3),
            codeMigrations = listOf(
                CodeMigration(from = 2, to = 3) { ran += 2 },
                // from < current：不在本轮回填范围
                CodeMigration(from = 1, to = 2) { ran += 1 },
            ),
        )

        // from >= current(2) 且 to <= target(3) 的才跑
        assertEquals(listOf(2), ran)
        driver.close()
    }

    @Test
    fun `ensureSchema currentGreaterTarget_throwsVersionDowngrade_failClosed`() {
        val engine = MigrationEngine(DatabasePathProviderStub(tmp.newFolder()))
        val dbFile = tmp.newFile("v5.db")
        val driver = openDb(dbFile)
        setUserVersion(driver, 5)

        val e = assertThrows(MigrationRejectedException::class.java) {
            engine.ensureSchema(
                lib = com.mini.me_core.datalayer.engine.LibName.SETTINGS,
                driver = driver,
                schema = fakeSchema(3),
            )
        }

        assertEquals(MigrationRejectedException.RejectReason.VERSION_DOWNGRADE, e.reason)
        assertTrue("异常信息应点明版本回退", e.message!!.contains("版本回退"))
        driver.close()
    }

    @Test
    fun `currentVersion_readsPragmaUserVersion`() {
        val engine = MigrationEngine(DatabasePathProviderStub(tmp.newFolder()))
        val dbFile = tmp.newFile("uv.db")
        val driver = openDb(dbFile)
        setUserVersion(driver, 7)

        assertEquals(7, engine.currentVersion(driver))
        driver.close()
    }

    /** 最小 DatabasePathProvider stub：ensureSchema 不触碰路径，给空实现即可。 */
    private class DatabasePathProviderStub(private val root: File) : com.mini.me_core.datalayer.engine.DatabasePathProvider {
        override fun mainDb(lib: com.mini.me_core.datalayer.engine.LibName): File =
            root.resolve("${lib.fileName}.stub")

        override fun backupDir(): File = root.resolve("backup-stub").apply { mkdirs() }
    }
}
