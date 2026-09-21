package com.mini.me_core.datalayer.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.mini.mecore.datalayer.sqldelight.InfraDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DbEncryptionMigrationEngine 仪器测试（需真机/模拟器运行）。
 *
 * 覆盖完整迁移流程：
 * - 明文数据库创建 + 测试数据插入
 * - migrateToEncrypted 正向迁移
 * - 迁移后数据一致性校验（行数 + 内容）
 * - migrateToPlain 反向迁移
 * - 迁移后数据库可正常读写
 *
 * 运行方式：连接真机/模拟器后执行
 *   ./gradlew :app:connectedDebugAndroidTest
 *
 * 注意：此测试需要 SQLCipher 原生库（.so），仅在真机/模拟器上可用，
 * Robolectric 单元测试环境不支持。
 */
@RunWith(AndroidJUnit4::class)
class DbEncryptionMigrationEngineInstrumentedTest {

    private lateinit var context: Context
    private lateinit var pathProvider: DatabasePathProvider
    private lateinit var keyProvider: DatabaseKeyProvider
    private lateinit var stateStore: MigrationStateStore
    private lateinit var engine: DbEncryptionMigrationEngine

    // 使用 INFRA 库做测试（最小的库，迁移快）
    private val testLib = LibName.INFRA

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        pathProvider = AndroidDatabasePathProvider(context)
        keyProvider = AndroidDatabaseKeyProvider(context)
        stateStore = SharedPreferencesMigrationStateStore(context)
        engine = DbEncryptionMigrationEngine(context, pathProvider, keyProvider, stateStore)

        // 清理测试库和状态
        cleanupTestDb()
        stateStore.clearState(testLib)
    }

    @After
    fun tearDown() {
        cleanupTestDb()
        stateStore.clearState(testLib)
    }

    private fun cleanupTestDb() {
        val dbFile = pathProvider.mainDb(testLib)
        dbFile.takeIf { it.exists() }?.delete()
        dbFile.parentFile?.let { parent ->
            listOf("-wal", "-shm", "-journal", ".enc.tmp", ".plain.tmp").forEach { suffix ->
                java.io.File(parent, "${dbFile.name}$suffix").takeIf { it.exists() }?.delete()
            }
        }
    }

    /** 创建明文测试数据库并插入测试数据。 */
    private fun createPlainTestDbWithData(rowCount: Int = 50) {
        val driver = AndroidSqliteDriver(
            schema = InfraDb.Schema,
            context = context,
            name = testLib.fileName,
            factory = FrameworkSQLiteOpenHelperFactory(),
        )
        try {
            // InfraDb 有 kv_store 表，插入测试数据
            for (i in 0 until rowCount) {
                driver.execute(
                    null,
                    "INSERT OR REPLACE INTO kv_store (namespace, key, value, updated_at) VALUES (?, ?, ?, ?)",
                    4,
                ) {
                    bindString(0, "test_ns")
                    bindString(1, "key_$i")
                    bindString(2, "value_$i")
                    bindLong(3, System.currentTimeMillis())
                }
            }
        } finally {
            driver.close()
        }
    }

    /** 查询明文库的行数。 */
    private fun queryRowCount(): Long {
        val driver = AndroidSqliteDriver(
            schema = InfraDb.Schema,
            context = context,
            name = testLib.fileName,
            factory = FrameworkSQLiteOpenHelperFactory(),
        )
        return try {
            driver.executeQuery(
                null,
                "SELECT COUNT(*) FROM kv_store WHERE namespace = ?",
                { cursor ->
                    val count = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                    app.cash.sqldelight.db.QueryResult.Value(count)
                },
                1,
            ) {
                bindString(0, "test_ns")
            }.value
        } finally {
            driver.close()
        }
    }

    @Test
    fun test_migrateToEncrypted_小库迁移成功() {
        // 1. 创建明文测试库
        createPlainTestDbWithData(rowCount = 30)
        assertEquals(30L, queryRowCount())

        // 2. 执行正向迁移
        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToEncrypted(testLib)
        }

        // 3. 验证迁移结果
        assertEquals(MigrationResult.SUCCESS, result)

        // 4. 验证状态为 ENCRYPTED
        val state = stateStore.getState(testLib)
        assertEquals(EncryptionStatus.ENCRYPTED, state.encryptionStatus)
        assertEquals(100, state.progressPercent)

        // 5. 验证主库文件存在且不是明文 SQLite 头
        val dbFile = pathProvider.mainDb(testLib)
        assertTrue("主库文件应存在", dbFile.exists())
        val header = ByteArray(16)
        java.io.RandomAccessFile(dbFile, "r").use { it.readFully(header) }
        val plainHeader = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8).copyOf(16)
        assertTrue("加密库文件头不应是明文 SQLite 头", !header.contentEquals(plainHeader))

        // 6. 验证加密后可以用加密驱动打开并读取数据
        val dek = kotlinx.coroutines.runBlocking { keyProvider.getPassphrase(testLib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)
        val encryptedDriver = AndroidSqliteDriver(
            schema = InfraDb.Schema,
            context = context,
            name = testLib.fileName,
            factory = net.sqlcipher.database.SupportFactory(passphrase),
        )
        passphrase.fill(0)
        try {
            val count = encryptedDriver.executeQuery(
                null,
                "SELECT COUNT(*) FROM kv_store WHERE namespace = ?",
                { cursor ->
                    val c = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                    app.cash.sqldelight.db.QueryResult.Value(c)
                },
                1,
            ) {
                bindString(0, "test_ns")
            }.value
            assertEquals("加密库行数应与明文一致", 30L, count)
        } finally {
            encryptedDriver.close()
        }
    }

    @Test
    fun test_migrateToEncrypted_已加密返回ALREADY_ENCRYPTED() {
        // 预置状态为 ENCRYPTED
        stateStore.updateState(testLib) {
            it.copy(encryptionStatus = EncryptionStatus.ENCRYPTED)
        }

        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToEncrypted(testLib)
        }

        assertEquals(MigrationResult.ALREADY_ENCRYPTED, result)
    }

    @Test
    fun test_migrateToPlain_反向迁移成功() {
        // 1. 先正向迁移到加密
        createPlainTestDbWithData(rowCount = 20)
        kotlinx.coroutines.runBlocking { engine.migrateToEncrypted(testLib) }

        // 2. 执行反向迁移
        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToPlain(testLib)
        }

        // 3. 验证结果
        assertEquals(MigrationResult.SUCCESS, result)

        // 4. 验证状态回到 PLAIN
        val state = stateStore.getState(testLib)
        assertEquals(EncryptionStatus.PLAIN, state.encryptionStatus)

        // 5. 验证明文库可以正常打开并读取
        assertEquals(20L, queryRowCount())
    }

    @Test
    fun test_migrateToPlain_已明文返回ALREADY_PLAIN() {
        val result = kotlinx.coroutines.runBlocking {
            engine.migrateToPlain(testLib)
        }
        assertEquals(MigrationResult.ALREADY_PLAIN, result)
    }

    @Test
    fun test_迁移后加密库可正常写入() {
        // 1. 迁移到加密
        createPlainTestDbWithData(rowCount = 10)
        kotlinx.coroutines.runBlocking { engine.migrateToEncrypted(testLib) }

        // 2. 用加密驱动写入新数据
        val dek = kotlinx.coroutines.runBlocking { keyProvider.getPassphrase(testLib) }
        val passphrase = AndroidDatabaseKeyProvider.encodePassphrase(dek).toByteArray(Charsets.UTF_8)
        dek.fill(0)
        val driver = AndroidSqliteDriver(
            schema = InfraDb.Schema,
            context = context,
            name = testLib.fileName,
            factory = net.sqlcipher.database.SupportFactory(passphrase),
        )
        passphrase.fill(0)
        try {
            driver.execute(null, "BEGIN TRANSACTION", 0, null)
            for (i in 100 until 110) {
                driver.execute(
                    null,
                    "INSERT OR REPLACE INTO kv_store (namespace, key, value, updated_at) VALUES (?, ?, ?, ?)",
                    4,
                ) {
                    bindString(0, "test_ns")
                    bindString(1, "new_key_$i")
                    bindString(2, "new_value_$i")
                    bindLong(3, System.currentTimeMillis())
                }
            }
            driver.execute(null, "COMMIT", 0, null)

            // 3. 验证总行数 = 10（原有）+ 10（新增）
            val count = driver.executeQuery(
                null,
                "SELECT COUNT(*) FROM kv_store WHERE namespace = ?",
                { cursor ->
                    val c = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                    app.cash.sqldelight.db.QueryResult.Value(c)
                },
                1,
            ) {
                bindString(0, "test_ns")
            }.value
            assertEquals(20L, count)
        } finally {
            driver.close()
        }
    }

    @Test
    fun test_引擎初始化() {
        assertNotNull(engine)
        assertNotNull(pathProvider)
        assertNotNull(keyProvider)
        assertNotNull(stateStore)
    }
}
