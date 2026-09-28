package com.mini.me_core.datalayer.encryption

import com.mini.me_core.datalayer.engine.LibName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 库标识一致性测试（密钥体系不变量 §1/§2 的守门测试）。
 *
 * 背景：过去 driver 侧用 `DatabaseDefinition.id`、probe 侧用 `LibName.name.lowercase()`
 * 各自推导 DEK purpose，两路一旦漂移（区域设置 / 命名变化），表现为
 * 「库打不开」的**假损坏**，进而被隔离重建 —— 历史数据被静默清空。
 *
 * 现在的唯一真源是 [LibName.dbId]：本测试保证它与 [DatabaseDefinition.id]、
 * [CipherPassphrase.purpose] 三处逐字一致，且**不随设备区域变化**。
 *
 * ⚠️ 新增第 7 个库时必须同时更新 `LibName` 与 `BuiltinDatabases`，
 *    本测试的 [libEnumAndRegistryCoverSameSet] 会挡住「只改了一处」。
 */
class KeyIdentityConsistencyTest {

    private val defs: Map<LibName, DatabaseDefinition> = mapOf(
        LibName.AGENT to AgentDatabase,
        LibName.CREDENTIALS to CredentialsDatabase,
        LibName.SETTINGS to SettingsDatabase,
        LibName.WORKSPACE to WorkspaceDatabase,
        LibName.T2I to T2iDatabase,
        LibName.INFRA to InfraDatabase,
    )

    @Test
    fun libEnumAndRegistryCoverSameSet() {
        assertEquals(
            "LibName 与内置库定义数量不一致：新增库必须同时改 LibName 与 BuiltinDatabases",
            LibName.entries.size,
            defs.size,
        )
    }

    @Test
    fun libDbIdEqualsDatabaseDefinitionId() {
        for ((lib, def) in defs) {
            assertEquals("${lib.name} 的 dbId 与 DatabaseDefinition.id 必须逐字相等", def.id, lib.dbId)
        }
    }

    @Test
    fun libFileNameEqualsDatabaseDefinitionFileName() {
        for ((lib, def) in defs) {
            assertEquals("${lib.name} 的 fileName 与 DatabaseDefinition.fileName 必须逐字相等", def.fileName, lib.fileName)
        }
    }

    @Test
    fun purposeIsPrefixedDbId() {
        for ((lib, def) in defs) {
            assertEquals("${lib.name} 的 purpose 应为 db_<id>", "${CipherPassphrase.DB_PREFIX}${def.id}", CipherPassphrase.purpose(lib))
            // driver 侧（按 id）与 probe 侧（按 LibName）两条路径必须算出同一个 purpose
            assertEquals("${lib.name}：purpose(LibName) 与 purpose(dbId) 必须一致", CipherPassphrase.purpose(def.id), CipherPassphrase.purpose(lib))
        }
    }

    /**
     * 区域无关性：`lowercase()` 是 locale-sensitive API（语义等价 `lowercase(Locale.getDefault())`），
     * 不该用来推导标识。这里把默认区域切到土耳其语 / 阿塞拜疆语（`I` → `ı`）验证 purpose 依旧恒定。
     *
     * 注：实测 Kotlin/JVM 对纯 ASCII 有快路径，`"T2I".lowercase()` 在 tr-TR 下仍是 `t2i`，
     * 所以本用例对现有 6 个 ASCII 标识**不会**因改回 `lowercase()` 而失败——
     * 它真正守住的是「标识一旦含非 ASCII 也不能出错」，并把"不要做字符串变换"这一约束固定下来。
     */
    @Test
    fun purposeIsRegionIndependent() {
        val origin = Locale.getDefault()
        try {
            for (tag in listOf("tr-TR", "az-AZ")) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                assertEquals("$tag 下 T2I 的 purpose 必须恒定", "db_t2i", CipherPassphrase.purpose(LibName.T2I))
                assertEquals("$tag 下 INFRA 的 purpose 必须恒定", "db_infra", CipherPassphrase.purpose(LibName.INFRA))
            }
        } finally {
            Locale.setDefault(origin)
        }
    }

    @Test
    fun isDbPurposeDistinguishesDbAndFieldKeys() {
        for (lib in LibName.entries) {
            assertTrue("${lib.name} 属数据库密钥", CipherPassphrase.isDbPurpose(CipherPassphrase.purpose(lib)))
        }
        assertFalse("字段密钥不是数据库密钥", CipherPassphrase.isDbPurpose("field_credentials"))
        assertFalse("空串不是数据库密钥", CipherPassphrase.isDbPurpose(""))
    }
}
