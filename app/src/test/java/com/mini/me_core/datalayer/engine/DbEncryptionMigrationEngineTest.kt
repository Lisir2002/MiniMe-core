package com.mini.me_core.datalayer.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DbEncryptionMigrationEngine 单元测试（Robolectric）。
 *
 * 覆盖：
 * - 引擎初始化
 * - 状态查询（已加密/已明文时直接返回）
 * - 重试耗尽异常
 * - 枚举和异常类
 * - 所有 6 个 LibName 都有对应 Schema
 *
 * 注意：完整的迁移流程（明文→加密→校验→替换）需要 SQLCipher 原生库，
 * 在 Robolectric 环境中不可用，详见仪器测试 DbEncryptionMigrationEngineInstrumentedTest。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DbEncryptionMigrationEngineTest {

    private lateinit var context: Context
    private lateinit var pathProvider: DatabasePathProvider
    private lateinit var keyProvider: DatabaseKeyProvider
    private lateinit var stateStore: MigrationStateStore
    private lateinit var engine: DbEncryptionMigrationEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pathProvider = AndroidDatabasePathProvider(context)
        keyProvider = AndroidDatabaseKeyProvider(context)
        stateStore = SharedPreferencesMigrationStateStore(context)
        engine = DbEncryptionMigrationEngine(context, pathProvider, keyProvider, stateStore)

        // 清理所有库的状态
        LibName.entries.forEach { stateStore.clearState(it) }
    }

    @Test
    fun `migrateToEncrypted - 已加密状态返回 ALREADY_ENCRYPTED`() {
        stateStore.updateState(LibName.AGENT) {
            it.copy(encryptionStatus = EncryptionStatus.ENCRYPTED)
        }

        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToEncrypted(LibName.AGENT)
        }

        assertEquals(MigrationResult.ALREADY_ENCRYPTED, result)
    }

    @Test
    fun `migrateToPlain - 已明文状态返回 ALREADY_PLAIN`() {
        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToPlain(LibName.AGENT)
        }

        assertEquals(MigrationResult.ALREADY_PLAIN, result)
    }

    @Test
    fun `migrateToEncrypted - 重试次数耗尽抛 MigrationException`() {
        // 先设置逻辑版本为当前值，避免引擎自动重置历史失败状态
        stateStore.setLogicVersion(DbEncryptionMigrationEngine.MIGRATION_LOGIC_VERSION)
        stateStore.updateState(LibName.CREDENTIALS) {
            it.copy(retryCount = 3, lastError = "模拟失败")
        }

        val exception = assertThrows(MigrationException::class.java) {
            kotlinx.coroutines.runBlocking {
                engine.migrateToEncrypted(LibName.CREDENTIALS)
            }
        }

        assertTrue(exception.message?.contains("重试次数耗尽") == true)
        assertTrue(exception.message?.contains("模拟失败") == true)
    }

    @Test
    fun `MigrationResult 枚举值完整`() {
        val values = MigrationResult.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(MigrationResult.SUCCESS))
        assertTrue(values.contains(MigrationResult.ALREADY_ENCRYPTED))
        assertTrue(values.contains(MigrationResult.ALREADY_PLAIN))
        assertTrue(values.contains(MigrationResult.FAILED_RETRYABLE))
    }

    @Test
    fun `MigrationException 携带 cause`() {
        val cause = RuntimeException("root cause")
        val exception = MigrationException("迁移失败", cause)

        assertEquals("迁移失败", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `MigrationValidationException 继承 RuntimeException`() {
        val exception = MigrationValidationException("校验失败: 行数不一致")

        assertTrue(exception is RuntimeException)
        assertTrue(exception.message?.contains("行数不一致") == true)
    }

    @Test
    fun `所有 6 个 LibName 都有对应 Schema`() {
        val method = DbEncryptionMigrationEngine::class.java.getDeclaredMethod("getSchema", LibName::class.java)
        method.isAccessible = true

        LibName.entries.forEach { lib ->
            val schema = method.invoke(engine, lib)
            assertNotNull("Schema for $lib should not be null", schema)
        }
    }

    @Test
    fun `引擎初始化不抛异常`() {
        val engine2 = DbEncryptionMigrationEngine(context, pathProvider, keyProvider, stateStore)
        assertNotNull(engine2)
    }
}
