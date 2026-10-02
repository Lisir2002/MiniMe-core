package com.mini.me_core.datalayer.encryption

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DatabaseRegistry] 纯逻辑测试：注册、查询、重复注册防护、线程安全语义。
 *
 * 注册表是启动期的单点事实来源，任何「重复注册」都应在启动期 fail-fast，
 * 而不是静默覆盖导致两个 dbId 指向不同 schema。
 */
class DatabaseRegistryTest {

    /** 最小可用的假库定义，schema 行为无关（注册表不触碰 schema）。 */
    private fun fakeDefinition(id: String, fileName: String = "$id.db") = object : DatabaseDefinition {
        override val id: String = id
        override val fileName: String = fileName
        override val schema: SqlSchema<QueryResult.Value<Unit>> = object : SqlSchema<QueryResult.Value<Unit>> {
            override val version: Long = 1L
            override fun create(driver: SqlDriver) = QueryResult.Unit
            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ) = QueryResult.Unit
        }
        override val version: Int = 1
    }

    @Test
    fun get_returnsRegisteredDefinition() {
        val registry = DatabaseRegistry()
        val def = fakeDefinition("agent")
        registry.register(def)

        assertSame(def, registry.get("agent"))
    }

    @Test
    fun get_unknownId_returnsNull() {
        val registry = DatabaseRegistry()
        assertNull(registry.get("not-registered"))
    }

    @Test
    fun isRegistered_reflectsRegistrationState() {
        val registry = DatabaseRegistry()
        assertFalse(registry.isRegistered("agent"))

        registry.register(fakeDefinition("agent"))
        assertTrue(registry.isRegistered("agent"))
    }

    @Test
    fun getAll_returnsAllRegisteredDefinitionsInRegistrationSet() {
        val registry = DatabaseRegistry()
        registry.register(fakeDefinition("agent"))
        registry.register(fakeDefinition("settings"))
        registry.register(fakeDefinition("workspace"))

        val ids = registry.getAll().map { it.id }.toSet()
        assertEquals(setOf("agent", "settings", "workspace"), ids)
    }

    @Test
    fun getAll_emptyRegistry_returnsEmptyList() {
        val registry = DatabaseRegistry()
        assertTrue(registry.getAll().isEmpty())
    }

    @Test
    fun register_duplicateId_throwsIllegalArgumentException() {
        val registry = DatabaseRegistry()
        registry.register(fakeDefinition("agent"))

        val e = assertThrows(IllegalArgumentException::class.java) {
            registry.register(fakeDefinition("agent", fileName = "other.db"))
        }
        assertTrue(e.message!!.contains("agent"))
    }

    @Test
    fun register_differentIdsWithSameFileName_allowed() {
        // 文件名相同不应阻止注册（不同库可复用文件名约定），id 才是唯一键。
        val registry = DatabaseRegistry()
        registry.register(fakeDefinition("a", fileName = "shared.db"))
        registry.register(fakeDefinition("b", fileName = "shared.db"))

        assertEquals(2, registry.getAll().size)
    }
}
