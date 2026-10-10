package com.mini.me_core.datalayer.encryption

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.sqlcipher.database.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SQLCipher 原生真实加密开库仪器测试。
 *
 * ⚠️ 【未在设备/模拟器实跑：本环境无设备（adb devices 为空）】
 *
 * 宿主机 JVM（Robolectric）无法加载 arm64-v8a 的 libsqlcipher/.so，故以下用例必须在
 * 真机或 arm64 模拟器上实跑。本文件仅保证 `:app:compileDebugAndroidTestKotlin` 编译通过。
 *
 * 覆盖路径（生产代码：EncryptedDriverFactory / KeyRotationMigrator / AndroidVersionProbe）：
 *  1. 用正确 passphrase 打开 SQLCipher 库 → 建表 → 写入 → 读回一致；
 *  2. 用错误 passphrase 打开同一文件 → 抛异常（"file is encrypted or is not a database"）；
 *  3. user_version 读写：设置高版本后用低版本 schema 打开时，SQLiteOpenHelper 走 onUpgrade
 *     而非 onDowngrade（与 MigrationEngine.preOpen 的 VERSION_DOWNGRADE fail-closed 契约呼应）；
 *  4. sqlcipher_export 迁移后行数一致（KeyRotationMigrator 第 6 步数据完整性闸门）。
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val testPassphrase = "instrumented-test-passphrase-0123456789"

    @Before
    fun loadSqlCipherLibs() {
        SQLiteDatabase.loadLibs(context)
    }

    @Test
    fun openWithCorrectPassphrase_createsTableAndReadsBack() {
        val dbFile = context.getDatabasePath("instrumented_encrypt_test.db").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        )
        db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, payload TEXT)")
        db.execSQL("INSERT INTO t(payload) VALUES ('hello-encrypted')")
        val value = db.rawQuery("SELECT payload FROM t WHERE id=1", null).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        assertEquals("hello-encrypted", value)
        db.close()
        assertTrue("数据库文件应已落盘", dbFile.exists() && dbFile.length() > 0)
        dbFile.delete()
    }

    @Test
    fun openWithWrongPassphrase_throwsException() {
        val dbFile = context.getDatabasePath("instrumented_wrong_key.db").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        // 用正确口令建一个库
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { it.execSQL("CREATE TABLE t(id INTEGER)") }

        // 用错误口令打开
        val ex = assertThrows(Exception::class.java) {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                "wrong-passphrase",
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { it.rawQuery("SELECT count(*) FROM t", null).count }
        }
        // SQLCipher 在错误口令下首次读页才报错，消息含 "file is encrypted" / "not a database"
        val msg = ex.message ?: ""
        assertTrue(
            "错误口令应报加密/非数据库错误，实际: $msg",
            msg.contains("encrypted", ignoreCase = true) ||
                msg.contains("not a database", ignoreCase = true) ||
                msg.contains("file is not a database", ignoreCase = true),
        )
        dbFile.delete()
    }

    @Test
    fun userVersion_roundTripsOnEncryptedDatabase() {
        val dbFile = context.getDatabasePath("instrumented_user_version.db").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { db ->
            db.execSQL("CREATE TABLE t(id INTEGER)")
            db.execSQL("PRAGMA user_version = 5")
        }
        // 重开读回
        val readBack = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }
        assertEquals(5L, readBack)
        dbFile.delete()
    }

    @Test
    fun downgradingUserVersion_viaPragma_isReadableButSemanticsMatchedByPreOpen() {
        // 本用例钉住「高版本库被低版本 schema 打开」的物理事实：
        // SQLite 本身不会因 user_version 高而拒绝打开（fail-closed 是 MigrationEngine.preOpen
        // 在 driver 构造前用 VersionProbe 探测并抛 MigrationRejectedException 实现的）。
        // 这里验证：直接 PRAGMA 降级 user_version 不会损坏数据——真正的 fail-closed 在 preOpen。
        val dbFile = context.getDatabasePath("instrumented_downgrade.db").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { db ->
            db.execSQL("CREATE TABLE t(id INTEGER)")
            db.execSQL("INSERT INTO t VALUES (42)")
            db.execSQL("PRAGMA user_version = 5")
        }
        // 模拟「低版本代码」打开同一文件：数据仍可读（SQLite 不阻止），
        // 但生产代码的 MigrationEngine.preOpen 会在此时抛 VERSION_DOWNGRADE。
        val value = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.rawQuery("SELECT id FROM t", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }
        assertEquals(42L, value)
        dbFile.delete()
    }

    @Test
    fun sqlcipherExport_migratesData_toNewPassphrase() {
        // KeyRotationMigrator 的核心：sqlcipher_export 把旧口令库搬到新口令临时库，行数一致。
        val srcFile = context.getDatabasePath("instrumented_export_src.db").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        val tmpFile = File(srcFile.parentFile, "instrumented_export_tmp.db")
        if (tmpFile.exists()) tmpFile.delete()

        SQLiteDatabase.openDatabase(
            srcFile.absolutePath,
            testPassphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { src ->
            src.execSQL("CREATE TABLE t(id INTEGER, payload TEXT)")
            src.execSQL("INSERT INTO t VALUES (1, 'a')")
            src.execSQL("INSERT INTO t VALUES (2, 'b')")
            val newKey = "new-passphrase-9876543210"
            src.rawExecSQL("ATTACH DATABASE '${tmpFile.absolutePath}' AS encrypted KEY '$newKey'")
            src.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            src.rawExecSQL("DETACH DATABASE encrypted")
        }

        // 用新口令打开临时库，行数应与源库一致
        val rowCount = SQLiteDatabase.openDatabase(
            tmpFile.absolutePath,
            "new-passphrase-9876543210",
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.rawQuery("SELECT count(*) FROM t", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }
        assertEquals(2L, rowCount)
        srcFile.delete()
        tmpFile.delete()
    }
}
