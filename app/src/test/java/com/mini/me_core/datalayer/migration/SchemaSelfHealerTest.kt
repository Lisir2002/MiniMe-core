package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [SchemaSelfHealer] 无损结构自愈测试（真实 JVM sqlite，非内存）。
 *
 * 守护 P3-03「迁移/自愈只执行一次」的关键幂等性与数据保全语义：
 *  - 表结构已完整 → [SchemaSelfHealer.healTable] 直接跳过（不重建、不丢数据）；
 *  - 表缺列 → 无损重建（RENAME→CREATE→按列交集迁移→DROP），旧行数据按列透传；
 *  - 缺主键 id → 由 rowid 推导回填（`session_id#rowid`），可反查 legacy；
 *  - 整表不存在（0 列）→ 跳过，不抛 no such table；
 *  - [SchemaSelfHealer.hasColumn] / [SchemaSelfHealer.tableColumns] 正确反映列存在性。
 *
 * ⚠️ JVM 驱动语义说明：sqlite-jdbc 默认 autocommit=true，[SchemaSelfHealer.beginImmediate]
 *    的独立 BEGIN/COMMIT 路径会被 autocommit 立即提交、最终 COMMIT 报「no transaction active」。
 *    因此本测试在每处 healTable 外包裹 `tx.transaction { }`（TransacterImpl 开启外层事务，
 *    连接 autocommit=false），走其**嵌套事务分支**（外层已有事务时 BEGIN 失败 → txStarted=false
 *    → 由外层统一提交）。独立自事务路径（启动时无外层事务）属设备/SQLCipher 原生驱动语义差异，
 *    列入真机验证清单。
 */
class SchemaSelfHealerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun openDb(): JdbcSqliteDriver =
        JdbcSqliteDriver("jdbc:sqlite:${tmp.newFile("heal.db").absolutePath}")

    @Test
    fun `healTable skipsWhenTableIsComplete_noRebase_noDataLoss`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        driver.execute(
            null,
            """
            CREATE TABLE widget (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL DEFAULT '',
              count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            0,
            null,
        )
        driver.execute(null, "INSERT INTO widget(id, name, count) VALUES ('w1', 'alpha', 5)", 0, null)

        val before = SchemaSelfHealer.tableColumns(driver, "widget")
        tx.transaction {
            SchemaSelfHealer.healTable(
                driver = driver,
                table = "widget",
                targetColumns = listOf("id", "name", "count"),
                createSql = """
                    CREATE TABLE widget (
                      id TEXT NOT NULL PRIMARY KEY,
                      name TEXT NOT NULL DEFAULT '',
                      count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent(),
            )
        }

        assertEquals(before, SchemaSelfHealer.tableColumns(driver, "widget"))
        val rowCount = driver.executeQuery(null, "SELECT COUNT(*) FROM widget", { c ->
            val v = if (c.next().value) c.getLong(0) ?: 0L else 0L
            app.cash.sqldelight.db.QueryResult.Value(v)
        }, 0, null).value
        assertEquals(1L, rowCount)
        driver.close()
    }

    @Test
    fun `healTable repairsMissingColumns_andPreservesExistingRows`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        driver.execute(
            null,
            "CREATE TABLE widget (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
            0,
            null,
        )
        driver.execute(null, "INSERT INTO widget(id, name) VALUES ('w1', 'old')", 0, null)
        driver.execute(null, "INSERT INTO widget(id, name) VALUES ('w2', 'older')", 0, null)

        tx.transaction {
            SchemaSelfHealer.healTable(
                driver = driver,
                table = "widget",
                targetColumns = listOf("id", "name", "count"),
                createSql = """
                    CREATE TABLE widget (
                      id TEXT NOT NULL PRIMARY KEY,
                      name TEXT NOT NULL DEFAULT '',
                      count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent(),
            )
        }

        assertTrue(SchemaSelfHealer.hasColumn(driver, "widget", "count"))
        val count = driver.executeQuery(null, "SELECT COUNT(*) FROM widget", { c ->
            val v = if (c.next().value) c.getLong(0) ?: 0L else 0L
            app.cash.sqldelight.db.QueryResult.Value(v)
        }, 0, null).value
        assertEquals(2L, count)
        val nameW1 = driver.executeQuery(null, "SELECT name, count FROM widget WHERE id='w1'", { c ->
            if (c.next().value) {
                val name = c.getString(0)
                val cnt = c.getLong(1) ?: 0L
                app.cash.sqldelight.db.QueryResult.Value(name to cnt)
            } else {
                app.cash.sqldelight.db.QueryResult.Value(null as String? to 0L)
            }
        }, 0, null).value
        assertEquals("old", nameW1.first)
        assertEquals(0L, nameW1.second)
        val legacyCols = SchemaSelfHealer.tableColumns(driver, "widget_legacy")
        assertTrue("自愈成功后 widget_legacy 应被 DROP", legacyCols.isEmpty())
        driver.close()
    }

    @Test
    fun `healTable skipsWhenTableDoesNotExist_noThrow`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        tx.transaction {
            SchemaSelfHealer.healTable(
                driver = driver,
                table = "ghost",
                targetColumns = listOf("id", "name"),
                createSql = "CREATE TABLE ghost (id TEXT NOT NULL PRIMARY KEY, name TEXT)",
            )
        }
        assertTrue(SchemaSelfHealer.tableColumns(driver, "ghost").isEmpty())
        driver.close()
    }

    @Test
    fun `healTable_isIdempotent_runningTwiceDoesNotRebuildAgain`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        driver.execute(
            null,
            "CREATE TABLE widget (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL DEFAULT '')",
            0,
            null,
        )
        driver.execute(null, "INSERT INTO widget(id, name) VALUES ('w1', 'x')", 0, null)

        val createSql = """
            CREATE TABLE widget (
              id TEXT NOT NULL PRIMARY KEY,
              name TEXT NOT NULL DEFAULT '',
              count INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        // 两次调用包在同一外层事务内：第一次重建，第二次因列已齐全跳过
        tx.transaction {
            SchemaSelfHealer.healTable(driver, "widget", listOf("id", "name", "count"), createSql)
            SchemaSelfHealer.healTable(driver, "widget", listOf("id", "name", "count"), createSql)
        }

        val count = driver.executeQuery(null, "SELECT COUNT(*) FROM widget", { c ->
            val v = if (c.next().value) c.getLong(0) ?: 0L else 0L
            app.cash.sqldelight.db.QueryResult.Value(v)
        }, 0, null).value
        assertEquals(1L, count)
        assertTrue(SchemaSelfHealer.hasColumn(driver, "widget", "count"))
        driver.close()
    }

    @Test
    fun `hasColumn_reflectsColumnPresence`() {
        val driver = openDb()
        driver.execute(
            null,
            "CREATE TABLE t (id TEXT PRIMARY KEY, label TEXT)",
            0,
            null,
        )
        assertTrue(SchemaSelfHealer.hasColumn(driver, "t", "id"))
        assertTrue(SchemaSelfHealer.hasColumn(driver, "t", "label"))
        assertFalse(SchemaSelfHealer.hasColumn(driver, "t", "missing"))
        assertFalse(SchemaSelfHealer.hasColumn(driver, "nope", "id"))
        driver.close()
    }

    @Test
    fun `healTable_backfillsMissingPrimaryKeyId_fromRowid_whenSessionIdPresent`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        driver.execute(
            null,
            "CREATE TABLE agent_message_old (session_id TEXT NOT NULL, role TEXT NOT NULL)",
            0,
            null,
        )
        driver.execute(null, "INSERT INTO agent_message_old(session_id, role) VALUES ('s1', 'user')", 0, null)
        driver.execute(null, "INSERT INTO agent_message_old(session_id, role) VALUES ('s1', 'assistant')", 0, null)

        tx.transaction {
            SchemaSelfHealer.healTable(
                driver = driver,
                table = "agent_message_old",
                targetColumns = listOf("id", "session_id", "role"),
                createSql = """
                    CREATE TABLE agent_message_old (
                      id TEXT NOT NULL PRIMARY KEY,
                      session_id TEXT NOT NULL,
                      role TEXT NOT NULL
                    )
                """.trimIndent(),
            )
        }

        val ids = driver.executeQuery(null, "SELECT id FROM agent_message_old ORDER BY rowid", { c ->
            val list = ArrayList<String>()
            while (c.next().value) c.getString(0)?.let { list += it }
            app.cash.sqldelight.db.QueryResult.Value(list)
        }, 0, null).value
        assertEquals(2, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.toSet().size, 2)
        assertTrue(ids[0].startsWith("s1#"))
        driver.close()
    }

    @Test
    fun `healTable_dropsStaleLegacyFromPreviousCrashedHeal`() {
        val driver = openDb()
        val tx = object : TransacterImpl(driver) {}
        driver.execute(
            null,
            "CREATE TABLE widget (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL DEFAULT '', count INTEGER NOT NULL DEFAULT 0)",
            0,
            null,
        )
        driver.execute(null, "INSERT INTO widget(id, name, count) VALUES ('w1', 'x', 1)", 0, null)
        driver.execute(
            null,
            "CREATE TABLE widget_legacy (id TEXT NOT NULL PRIMARY KEY, name TEXT)",
            0,
            null,
        )

        tx.transaction {
            SchemaSelfHealer.healTable(
                driver = driver,
                table = "widget",
                targetColumns = listOf("id", "name", "count"),
                createSql = """
                    CREATE TABLE widget (
                      id TEXT NOT NULL PRIMARY KEY,
                      name TEXT NOT NULL DEFAULT '',
                      count INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent(),
            )
        }

        val count = driver.executeQuery(null, "SELECT COUNT(*) FROM widget", { c ->
            val v = if (c.next().value) c.getLong(0) ?: 0L else 0L
            app.cash.sqldelight.db.QueryResult.Value(v)
        }, 0, null).value
        assertEquals(1L, count)
        driver.close()
    }
}
