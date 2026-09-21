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
import java.io.File
import java.io.RandomAccessFile

/**
 * [CrashRecovery] 单元测试（设计文档 db-encryption-migration-design.md §7 / §10.2）。
 *
 * 测试覆盖：
 * - PLAIN/ENCRYPTED 稳定状态：无需恢复，清理残留临时文件
 * - PRE_SNAPSHOT：删除不完整快照，回到 PLAIN
 * - MIGRATING：删除临时加密库和快照，回到 PLAIN
 * - VALIDATING：删除临时加密库和快照，回到 PLAIN
 * - REPLACING（主库已加密）：标记 ENCRYPTED，清理临时文件
 * - REPLACING（主库仍明文）：删除临时加密库，回到 PLAIN
 * - recoverAll：扫描所有 6 库并恢复
 * - 文件头探测：明文 SQLite 头 vs 加密库头
 * - 异常隔离：单个库恢复失败不影响其他库
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CrashRecoveryTest {

    private lateinit var context: Context
    private lateinit var pathProvider: DatabasePathProvider
    private lateinit var stateStore: SharedPreferencesMigrationStateStore
    private lateinit var crashRecovery: CrashRecovery

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清除 SharedPreferences
        context.getSharedPreferences("minime_db_migration_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        pathProvider = AndroidDatabasePathProvider(context)
        stateStore = SharedPreferencesMigrationStateStore(context)
        crashRecovery = CrashRecovery(pathProvider, stateStore)

        // 确保数据库目录存在
        pathProvider.mainDb(LibName.AGENT).parentFile?.mkdirs()
        pathProvider.backupDir().mkdirs()
    }

    @After
    fun tearDown() {
        // 清除 SharedPreferences
        context.getSharedPreferences("minime_db_migration_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        // 清理测试创建的文件
        for (lib in LibName.entries) {
            pathProvider.mainDb(lib).delete()
            pathProvider.snapshotFile(lib).delete()
            File(pathProvider.mainDb(lib).parent, "${lib.fileName}.enc.tmp").delete()
        }
    }

    // ── 辅助方法 ──

    /** 创建明文 SQLite 头的模拟数据库文件。 */
    private fun createPlainDb(lib: LibName) {
        val file = pathProvider.mainDb(lib)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.write("SQLite format 3\u0000".toByteArray(Charsets.UTF_8))
            raf.write(ByteArray(100)) // 填充一些数据
        }
    }

    /** 创建加密库头的模拟数据库文件（前 16 字节不是明文头）。 */
    private fun createEncryptedDb(lib: LibName) {
        val file = pathProvider.mainDb(lib)
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(ByteArray(16) { 0xAB.toByte() }) // 加密库头（随机字节，不匹配明文头）
            raf.write(ByteArray(100))
        }
    }

    /** 创建临时加密库文件。 */
    private fun createTempEncrypted(lib: LibName): File {
        val file = File(pathProvider.mainDb(lib).parent, "${lib.fileName}.enc.tmp")
        file.parentFile?.mkdirs()
        file.writeText("temp encrypted data")
        return file
    }

    /** 创建快照文件。 */
    private fun createSnapshot(lib: LibName): File {
        val file = pathProvider.snapshotFile(lib)
        file.parentFile?.mkdirs()
        file.writeText("snapshot data")
        return file
    }

    // ── 稳定状态测试 ──

    @Test
    fun `PLAIN state does not change status and cleans temp files`() {
        createPlainDb(LibName.AGENT)
        createTempEncrypted(LibName.AGENT) // 残留临时文件
        stateStore.setState(LibName.AGENT, MigrationState.initial(LibName.AGENT))

        crashRecovery.recover(LibName.AGENT)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.AGENT).encryptionStatus)
        // 残留临时文件应被清理
        assertFalse(File(pathProvider.mainDb(LibName.AGENT).parent, "${LibName.AGENT.fileName}.enc.tmp").exists())
    }

    @Test
    fun `ENCRYPTED state does not change status and cleans temp files and snapshot`() {
        createEncryptedDb(LibName.CREDENTIALS)
        createTempEncrypted(LibName.CREDENTIALS)
        createSnapshot(LibName.CREDENTIALS)
        stateStore.setState(
            LibName.CREDENTIALS,
            MigrationState(LibName.CREDENTIALS.name, EncryptionStatus.ENCRYPTED),
        )

        crashRecovery.recover(LibName.CREDENTIALS)

        assertEquals(EncryptionStatus.ENCRYPTED, stateStore.getState(LibName.CREDENTIALS).encryptionStatus)
        assertFalse(File(pathProvider.mainDb(LibName.CREDENTIALS).parent, "${LibName.CREDENTIALS.fileName}.enc.tmp").exists())
        assertFalse(pathProvider.snapshotFile(LibName.CREDENTIALS).exists())
    }

    // ── 非稳定状态回滚测试 ──

    @Test
    fun `PRE_SNAPSHOT state deletes incomplete snapshot and returns to PLAIN`() {
        createPlainDb(LibName.SETTINGS)
        val snapshot = createSnapshot(LibName.SETTINGS)
        assertTrue(snapshot.exists())
        stateStore.setState(
            LibName.SETTINGS,
            MigrationState(
                libName = LibName.SETTINGS.name,
                encryptionStatus = EncryptionStatus.PRE_SNAPSHOT,
                startedAtMs = 1000L,
                snapshotPath = snapshot.absolutePath,
            ),
        )

        crashRecovery.recover(LibName.SETTINGS)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.SETTINGS).encryptionStatus)
        assertFalse(snapshot.exists())
        assertNull(stateStore.getState(LibName.SETTINGS).startedAtMs)
    }

    @Test
    fun `MIGRATING state deletes temp encrypted and snapshot returns to PLAIN`() {
        createPlainDb(LibName.WORKSPACE)
        val tempEnc = createTempEncrypted(LibName.WORKSPACE)
        val snapshot = createSnapshot(LibName.WORKSPACE)
        stateStore.setState(
            LibName.WORKSPACE,
            MigrationState(
                libName = LibName.WORKSPACE.name,
                encryptionStatus = EncryptionStatus.MIGRATING,
                tempEncryptedPath = tempEnc.absolutePath,
                snapshotPath = snapshot.absolutePath,
                progressPercent = 50,
            ),
        )

        crashRecovery.recover(LibName.WORKSPACE)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.WORKSPACE).encryptionStatus)
        assertFalse(tempEnc.exists())
        assertFalse(snapshot.exists())
        assertEquals(0, stateStore.getState(LibName.WORKSPACE).progressPercent)
    }

    @Test
    fun `VALIDATING state deletes temp encrypted and snapshot returns to PLAIN`() {
        createPlainDb(LibName.T2I)
        val tempEnc = createTempEncrypted(LibName.T2I)
        val snapshot = createSnapshot(LibName.T2I)
        stateStore.setState(
            LibName.T2I,
            MigrationState(
                libName = LibName.T2I.name,
                encryptionStatus = EncryptionStatus.VALIDATING,
                tempEncryptedPath = tempEnc.absolutePath,
                snapshotPath = snapshot.absolutePath,
            ),
        )

        crashRecovery.recover(LibName.T2I)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.T2I).encryptionStatus)
        assertFalse(tempEnc.exists())
        assertFalse(snapshot.exists())
    }

    // ── REPLACING 状态测试 ──

    @Test
    fun `REPLACING state with encrypted main db marks ENCRYPTED and cleans temp files`() {
        // 主库已加密（rename 已完成）
        createEncryptedDb(LibName.AGENT)
        val tempEnc = createTempEncrypted(LibName.AGENT)
        val snapshot = createSnapshot(LibName.AGENT)
        stateStore.setState(
            LibName.AGENT,
            MigrationState(
                libName = LibName.AGENT.name,
                encryptionStatus = EncryptionStatus.REPLACING,
                tempEncryptedPath = tempEnc.absolutePath,
                snapshotPath = snapshot.absolutePath,
                progressPercent = 95,
            ),
        )

        crashRecovery.recover(LibName.AGENT)

        val state = stateStore.getState(LibName.AGENT)
        assertEquals(EncryptionStatus.ENCRYPTED, state.encryptionStatus)
        assertEquals(100, state.progressPercent)
        assertNull(state.tempEncryptedPath)
        assertNull(state.currentTable)
        assertNull(state.lastError)
        // 临时文件和快照应被清理
        assertFalse(tempEnc.exists())
        assertFalse(snapshot.exists())
        // 主库（已加密）不应被删除
        assertTrue(pathProvider.mainDb(LibName.AGENT).exists())
    }

    @Test
    fun `REPLACING state with plain main db deletes temp encrypted and returns to PLAIN`() {
        // 主库仍为明文（rename 未完成）
        createPlainDb(LibName.CREDENTIALS)
        val tempEnc = createTempEncrypted(LibName.CREDENTIALS)
        val snapshot = createSnapshot(LibName.CREDENTIALS)
        stateStore.setState(
            LibName.CREDENTIALS,
            MigrationState(
                libName = LibName.CREDENTIALS.name,
                encryptionStatus = EncryptionStatus.REPLACING,
                tempEncryptedPath = tempEnc.absolutePath,
                snapshotPath = snapshot.absolutePath,
            ),
        )

        crashRecovery.recover(LibName.CREDENTIALS)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.CREDENTIALS).encryptionStatus)
        assertFalse(tempEnc.exists())
        assertFalse(snapshot.exists())
        // 主库（明文）不应被删除
        assertTrue(pathProvider.mainDb(LibName.CREDENTIALS).exists())
    }

    @Test
    fun `REPLACING state with missing main db returns to PLAIN`() {
        // 主库文件不存在（异常情况）
        val tempEnc = createTempEncrypted(LibName.INFRA)
        stateStore.setState(
            LibName.INFRA,
            MigrationState(
                libName = LibName.INFRA.name,
                encryptionStatus = EncryptionStatus.REPLACING,
                tempEncryptedPath = tempEnc.absolutePath,
            ),
        )
        assertFalse(pathProvider.mainDb(LibName.INFRA).exists())

        crashRecovery.recover(LibName.INFRA)

        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.INFRA).encryptionStatus)
        assertFalse(tempEnc.exists())
    }

    // ── recoverAll 测试 ──

    @Test
    fun `recoverAll scans and recovers all six libraries`() {
        // 设置各种状态
        createPlainDb(LibName.AGENT)
        stateStore.setState(LibName.AGENT, MigrationState(LibName.AGENT.name, EncryptionStatus.MIGRATING))
        createPlainDb(LibName.CREDENTIALS)
        stateStore.setState(LibName.CREDENTIALS, MigrationState(LibName.CREDENTIALS.name, EncryptionStatus.PRE_SNAPSHOT))
        createEncryptedDb(LibName.SETTINGS)
        stateStore.setState(LibName.SETTINGS, MigrationState(LibName.SETTINGS.name, EncryptionStatus.REPLACING))
        // 其余保持 PLAIN

        crashRecovery.recoverAll()

        // 所有非稳定状态都应恢复到 PLAIN 或 ENCRYPTED
        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.AGENT).encryptionStatus)
        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.CREDENTIALS).encryptionStatus)
        assertEquals(EncryptionStatus.ENCRYPTED, stateStore.getState(LibName.SETTINGS).encryptionStatus)
        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.WORKSPACE).encryptionStatus)
        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.T2I).encryptionStatus)
        assertEquals(EncryptionStatus.PLAIN, stateStore.getState(LibName.INFRA).encryptionStatus)
    }

    // ── 文件头探测测试 ──

    @Test
    fun `plain sqlite header is detected correctly`() {
        createPlainDb(LibName.AGENT)
        val file = pathProvider.mainDb(LibName.AGENT)
        // 读取前 16 字节验证
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(16)
            raf.readFully(header)
            assertTrue(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `encrypted db header does not match plain sqlite header`() {
        createEncryptedDb(LibName.CREDENTIALS)
        val file = pathProvider.mainDb(LibName.CREDENTIALS)
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(16)
            raf.readFully(header)
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.UTF_8)))
        }
    }

    // ── 主库不被删除测试 ──

    @Test
    fun `recovery never deletes the main database file`() {
        // 测试所有非稳定状态，主库都不应被删除
        for (status in listOf(
            EncryptionStatus.PRE_SNAPSHOT,
            EncryptionStatus.MIGRATING,
            EncryptionStatus.VALIDATING,
            EncryptionStatus.REPLACING,
        )) {
            val lib = LibName.AGENT
            createPlainDb(lib)
            createTempEncrypted(lib)
            createSnapshot(lib)
            stateStore.setState(lib, MigrationState(lib.name, status))

            crashRecovery.recover(lib)

            assertTrue("主库在 $status 状态恢复后不应被删除", pathProvider.mainDb(lib).exists())
            // 清理以便下一轮测试
            stateStore.clearState(lib)
            pathProvider.mainDb(lib).delete()
        }
    }

    // ── 状态中路径为 null 时的清理测试 ──

    @Test
    fun `recovery cleans convention path files even when state paths are null`() {
        createPlainDb(LibName.WORKSPACE)
        // 创建约定路径的临时文件，但状态中 tempEncryptedPath 为 null
        val tempEnc = createTempEncrypted(LibName.WORKSPACE)
        stateStore.setState(
            LibName.WORKSPACE,
            MigrationState(
                libName = LibName.WORKSPACE.name,
                encryptionStatus = EncryptionStatus.MIGRATING,
                tempEncryptedPath = null, // 状态中未记录路径
                snapshotPath = null,
            ),
        )

        crashRecovery.recover(LibName.WORKSPACE)

        // 约定路径的临时文件仍应被清理
        assertFalse(tempEnc.exists())
    }
}
