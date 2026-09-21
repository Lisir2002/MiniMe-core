package com.mini.me_core.datalayer.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SharedPreferencesMigrationStateStore] 单元测试（设计文档 db-encryption-migration-design.md §6.2 / §10.2）。
 *
 * 测试覆盖：
 * - 初始状态为 PLAIN
 * - setState/getState 全字段持久化
 * - updateState 原子更新
 * - clearState 恢复初始
 * - 多库状态独立（互不干扰）
 * - 未知状态值回退 PLAIN（向前兼容）
 * - 进度/表计数/错误信息等字段正确读写
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class MigrationStateStoreTest {

    private lateinit var context: Context
    private lateinit var store: SharedPreferencesMigrationStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清除 SharedPreferences，避免测试间污染
        context.getSharedPreferences("minime_db_migration_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = SharedPreferencesMigrationStateStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("minime_db_migration_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    // ── 初始状态测试 ──

    @Test
    fun `getState returns PLAIN for uninitialized library`() {
        val state = store.getState(LibName.AGENT)
        assertEquals(EncryptionStatus.PLAIN, state.encryptionStatus)
        assertEquals(LibName.AGENT.name, state.libName)
        assertNull(state.startedAtMs)
        assertNull(state.snapshotPath)
        assertNull(state.tempEncryptedPath)
        assertEquals(0, state.progressPercent)
        assertNull(state.currentTable)
        assertEquals(0, state.tablesTotal)
        assertEquals(0, state.tablesCompleted)
        assertNull(state.lastError)
        assertEquals(0, state.retryCount)
    }

    // ── setState / getState 全字段测试 ──

    @Test
    fun `setState persists all fields and getState returns them`() {
        val original = MigrationState(
            libName = LibName.CREDENTIALS.name,
            encryptionStatus = EncryptionStatus.MIGRATING,
            startedAtMs = 1234567890L,
            snapshotPath = "/data/data/com.mini.me_core/files/backup/creds.db.bak",
            tempEncryptedPath = "/data/data/com.mini.me_core/files/databases/creds.db.tmp_enc",
            progressPercent = 42,
            currentTable = "api_providers",
            tablesTotal = 8,
            tablesCompleted = 3,
            lastError = "磁盘空间不足",
            retryCount = 2,
        )
        store.setState(LibName.CREDENTIALS, original)
        val loaded = store.getState(LibName.CREDENTIALS)

        assertEquals(original.encryptionStatus, loaded.encryptionStatus)
        assertEquals(original.startedAtMs, loaded.startedAtMs)
        assertEquals(original.snapshotPath, loaded.snapshotPath)
        assertEquals(original.tempEncryptedPath, loaded.tempEncryptedPath)
        assertEquals(original.progressPercent, loaded.progressPercent)
        assertEquals(original.currentTable, loaded.currentTable)
        assertEquals(original.tablesTotal, loaded.tablesTotal)
        assertEquals(original.tablesCompleted, loaded.tablesCompleted)
        assertEquals(original.lastError, loaded.lastError)
        assertEquals(original.retryCount, loaded.retryCount)
    }

    @Test
    fun `setState with null fields removes them from SharedPreferences`() {
        // 先设置有值的状态
        store.setState(
            LibName.AGENT,
            MigrationState(
                libName = LibName.AGENT.name,
                encryptionStatus = EncryptionStatus.MIGRATING,
                startedAtMs = 1000L,
                snapshotPath = "/tmp/snap.db",
                tempEncryptedPath = "/tmp/tmp.db",
                currentTable = "agent_message",
                lastError = "test error",
            ),
        )
        // 再设置 null 字段的状态（ENCRYPTED 稳定状态不需要这些字段）
        store.setState(
            LibName.AGENT,
            MigrationState(
                libName = LibName.AGENT.name,
                encryptionStatus = EncryptionStatus.ENCRYPTED,
                startedAtMs = null,
                snapshotPath = null,
                tempEncryptedPath = null,
                currentTable = null,
                lastError = null,
            ),
        )
        val loaded = store.getState(LibName.AGENT)
        assertEquals(EncryptionStatus.ENCRYPTED, loaded.encryptionStatus)
        assertNull(loaded.startedAtMs)
        assertNull(loaded.snapshotPath)
        assertNull(loaded.tempEncryptedPath)
        assertNull(loaded.currentTable)
        assertNull(loaded.lastError)
    }

    // ── updateState 测试 ──

    @Test
    fun `updateState applies transformation to current state`() {
        // 初始 PLAIN
        store.updateState(LibName.SETTINGS) { current ->
            current.copy(
                encryptionStatus = EncryptionStatus.PRE_SNAPSHOT,
                startedAtMs = 999L,
                progressPercent = 5,
            )
        }
        val state = store.getState(LibName.SETTINGS)
        assertEquals(EncryptionStatus.PRE_SNAPSHOT, state.encryptionStatus)
        assertEquals(999L, state.startedAtMs)
        assertEquals(5, state.progressPercent)
    }

    @Test
    fun `updateState chains multiple updates correctly`() {
        // 模拟迁移进度推进
        store.updateState(LibName.WORKSPACE) {
            it.copy(encryptionStatus = EncryptionStatus.MIGRATING, tablesTotal = 5)
        }
        store.updateState(LibName.WORKSPACE) {
            it.copy(tablesCompleted = 1, currentTable = "remote_connections")
        }
        store.updateState(LibName.WORKSPACE) {
            it.copy(tablesCompleted = 2, currentTable = "mount_points", progressPercent = 40)
        }
        val state = store.getState(LibName.WORKSPACE)
        assertEquals(EncryptionStatus.MIGRATING, state.encryptionStatus)
        assertEquals(5, state.tablesTotal)
        assertEquals(2, state.tablesCompleted)
        assertEquals("mount_points", state.currentTable)
        assertEquals(40, state.progressPercent)
    }

    // ── clearState 测试 ──

    @Test
    fun `clearState resets to initial PLAIN`() {
        store.setState(
            LibName.T2I,
            MigrationState(
                libName = LibName.T2I.name,
                encryptionStatus = EncryptionStatus.ENCRYPTED,
                startedAtMs = 100L,
                progressPercent = 100,
                retryCount = 1,
            ),
        )
        assertTrue(store.getState(LibName.T2I).encryptionStatus == EncryptionStatus.ENCRYPTED)

        store.clearState(LibName.T2I)

        val state = store.getState(LibName.T2I)
        assertEquals(EncryptionStatus.PLAIN, state.encryptionStatus)
        assertNull(state.startedAtMs)
        assertEquals(0, state.progressPercent)
        assertEquals(0, state.retryCount)
    }

    // ── 多库独立测试 ──

    @Test
    fun `multiple libraries have independent states`() {
        // AGENT 加密完成
        store.setState(
            LibName.AGENT,
            MigrationState(LibName.AGENT.name, EncryptionStatus.ENCRYPTED, progressPercent = 100),
        )
        // CREDENTIALS 迁移中
        store.setState(
            LibName.CREDENTIALS,
            MigrationState(LibName.CREDENTIALS.name, EncryptionStatus.MIGRATING, progressPercent = 50),
        )
        // SETTINGS 仍为明文
        // (不设置，保持初始)

        assertEquals(EncryptionStatus.ENCRYPTED, store.getState(LibName.AGENT).encryptionStatus)
        assertEquals(100, store.getState(LibName.AGENT).progressPercent)

        assertEquals(EncryptionStatus.MIGRATING, store.getState(LibName.CREDENTIALS).encryptionStatus)
        assertEquals(50, store.getState(LibName.CREDENTIALS).progressPercent)

        assertEquals(EncryptionStatus.PLAIN, store.getState(LibName.SETTINGS).encryptionStatus)
        assertEquals(0, store.getState(LibName.SETTINGS).progressPercent)
    }

    @Test
    fun `clearState on one library does not affect others`() {
        store.setState(LibName.AGENT, MigrationState(LibName.AGENT.name, EncryptionStatus.ENCRYPTED))
        store.setState(LibName.INFRA, MigrationState(LibName.INFRA.name, EncryptionStatus.ENCRYPTED))

        store.clearState(LibName.AGENT)

        assertEquals(EncryptionStatus.PLAIN, store.getState(LibName.AGENT).encryptionStatus)
        assertEquals(EncryptionStatus.ENCRYPTED, store.getState(LibName.INFRA).encryptionStatus)
    }

    // ── 向前兼容测试 ──

    @Test
    fun `unknown status value falls back to PLAIN`() {
        // 手动写入一个不存在的状态值（模拟未来版本新增/删除枚举）
        val prefs = context.getSharedPreferences("minime_db_migration_state", Context.MODE_PRIVATE)
        prefs.edit().putString("${LibName.AGENT.name}status", "FUTURE_STATUS").commit()

        val state = store.getState(LibName.AGENT)
        assertEquals(EncryptionStatus.PLAIN, state.encryptionStatus)
    }

    // ── 所有 6 库测试 ──

    @Test
    fun `all six libraries can be set and retrieved`() {
        for (lib in LibName.entries) {
            store.setState(
                lib,
                MigrationState(
                    libName = lib.name,
                    encryptionStatus = EncryptionStatus.ENCRYPTED,
                    startedAtMs = lib.ordinal.toLong(),
                    progressPercent = 100,
                ),
            )
        }
        for (lib in LibName.entries) {
            val state = store.getState(lib)
            assertEquals(EncryptionStatus.ENCRYPTED, state.encryptionStatus)
            assertEquals(lib.ordinal.toLong(), state.startedAtMs)
            assertEquals(lib.name, state.libName)
        }
    }

    // ── MigrationState.initial 测试 ──

    @Test
    fun `MigrationState initial creates PLAIN state`() {
        val state = MigrationState.initial(LibName.AGENT)
        assertEquals(EncryptionStatus.PLAIN, state.encryptionStatus)
        assertEquals(LibName.AGENT.name, state.libName)
        assertEquals(0, state.progressPercent)
        assertEquals(0, state.retryCount)
    }

    // ── 状态流转测试 ──

    @Test
    fun `full migration status transition via updateState`() {
        // PLAIN → PRE_SNAPSHOT
        store.updateState(LibName.AGENT) {
            it.copy(encryptionStatus = EncryptionStatus.PRE_SNAPSHOT, startedAtMs = 1L)
        }
        // PRE_SNAPSHOT → MIGRATING
        store.updateState(LibName.AGENT) {
            it.copy(encryptionStatus = EncryptionStatus.MIGRATING, snapshotPath = "/snap.db", tablesTotal = 3)
        }
        // MIGRATING → VALIDATING
        store.updateState(LibName.AGENT) {
            it.copy(encryptionStatus = EncryptionStatus.VALIDATING, tablesCompleted = 3, progressPercent = 90)
        }
        // VALIDATING → REPLACING
        store.updateState(LibName.AGENT) {
            it.copy(encryptionStatus = EncryptionStatus.REPLACING, tempEncryptedPath = "/tmp_enc.db")
        }
        // REPLACING → ENCRYPTED
        store.updateState(LibName.AGENT) {
            it.copy(
                encryptionStatus = EncryptionStatus.ENCRYPTED,
                progressPercent = 100,
                snapshotPath = null,
                tempEncryptedPath = null,
                currentTable = null,
            )
        }

        val final = store.getState(LibName.AGENT)
        assertEquals(EncryptionStatus.ENCRYPTED, final.encryptionStatus)
        assertEquals(100, final.progressPercent)
        assertNull(final.snapshotPath)
        assertNull(final.tempEncryptedPath)
        assertNull(final.currentTable)
        assertFalse(final.encryptionStatus == EncryptionStatus.PLAIN)
    }
}
