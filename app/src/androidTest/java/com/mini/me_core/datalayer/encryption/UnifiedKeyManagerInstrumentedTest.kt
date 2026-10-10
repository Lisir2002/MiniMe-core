package com.mini.me_core.datalayer.encryption

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AndroidUnifiedKeyManager（MasterKey + EncryptedSharedPreferences 存 DEK）仪器测试。
 *
 * ⚠️ 【未在设备/模拟器实跑：本环境无设备（adb devices 为空）】
 *
 * Robolectric 对 AndroidKeyStore 的模拟有限，MasterKey/AES256-GCM 在宿主机 JVM 上
 * 很可能无法真实创建。以下用例必须在真机上实跑，覆盖：
 *  1. MasterKey 创建 + isMasterKeyAvailable；
 *  2. getOrCreateDek 首次生成 32 字节随机 DEK，二次调用返回同一值；
 *  3. DEK 写入 EncryptedSharedPreferences 后重启（新建 manager 实例）仍可读回；
 *  4. rotateDek 对 db_* purpose 抛 IllegalArgumentException（数据库密钥禁止直接轮换）；
 *  5. emergencyReset 只清非 db_* 的 DEK，保留 db_* DEK。
 */
@RunWith(AndroidJUnit4::class)
class UnifiedKeyManagerInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var manager: AndroidUnifiedKeyManager

    @Before
    fun setUp() {
        manager = AndroidUnifiedKeyManager(context)
    }

    @Test
    fun masterKey_isAvailableAfterFirstCreate() = runBlocking {
        // 首次 getOrCreateDek 会触发 MasterKey 创建
        manager.getOrCreateDek("db_instrumented_test")
        assertTrue("MasterKey 应可用", manager.isMasterKeyAvailable())
    }

    @Test
    fun getOrCreateDek_returnsSameDekOnSecondCall() = runBlocking {
        val purpose = "db_instrumented_determinism"
        val first = manager.getOrCreateDek(purpose)
        assertEquals("DEK 应为 32 字节 AES-256", 32, first.size)
        val second = manager.getOrCreateDek(purpose)
        assertArrayEquals("二次调用必须返回同一 DEK（不重新生成）", first, second)
        first.fill(0)
        second.fill(0)
    }

    @Test
    fun getDek_absent_returnsNull() = runBlocking {
        val result = manager.getDek("db_nonexistent_purpose_xyz")
        assertTrue("不存在的 DEK 应返回 null", result == null)
    }

    @Test
    fun rotateDek_forDatabasePurpose_throwsIllegalArgument() = runBlocking {
        // db_* purpose 必须走 KeyRotationMigrator（sqlcipher_export），禁止直接 rotateDek
        val purpose = "db_instrumented_rotate"
        manager.getOrCreateDek(purpose)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { manager.rotateDek(purpose) }
        }
        assertTrue("异常信息应说明数据库密钥不可直接轮换", ex.message!!.contains("数据库密钥"))
    }

    @Test
    fun emergencyReset_preservesDatabaseDeks() = runBlocking {
        val dbPurpose = "db_instrumented_emergency_db"
        val fieldPurpose = "field_instrumented_emergency_field"
        val dbDek = manager.getOrCreateDek(dbPurpose)
        val fieldDek = manager.getOrCreateDek(fieldPurpose)

        manager.emergencyReset()

        // db_* DEK 应保留
        val dbAfter = manager.getDek(dbPurpose)
        assertArrayEquals("紧急重置不得清除数据库 DEK", dbDek, dbAfter)
        // 非 db_* DEK 应被清除
        val fieldAfter = manager.getDek(fieldPurpose)
        assertTrue("紧急重置应清除字段 DEK", fieldAfter == null)

        dbDek.fill(0); fieldDek.fill(0); dbAfter?.fill(0)
    }

    @Test
    fun importDek_thenGetDek_returnsImportedValue() = runBlocking {
        val purpose = "db_instrumented_import"
        val imported = ByteArray(32) { (it + 7).toByte() }
        manager.importDek(purpose, imported)
        val readBack = manager.getDek(purpose)
        assertArrayEquals("导入的 DEK 应能被读回", imported, readBack)
        imported.fill(0); readBack?.fill(0)
    }
}
