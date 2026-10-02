package com.mini.me_core.datalayer.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CipherPassphrase] 纯函数行为测试。
 *
 * 守护密钥构造的四条不变量中可在 JVM 单测内钉住的部分：
 *  - purpose 一律带 `db_` 前缀；
 *  - `isDbPurpose` 能区分「库密钥」与「字段密钥」；
 *  - [CipherPassphrase.openParams] 派生的「字符串」与「UTF-8 字节」两态必须同源一致。
 *
 * 注：[CipherPassphrase.encode] 依赖 `android.util.Base64`，需在 Robolectric / 仪器化
 * 环境下验证；此处不覆盖，避免对 Android framework 桩的返回值产生隐式依赖。
 */
class CipherPassphraseTest {

    @Test
    fun purpose_isPrefixedWithDbPrefix() {
        assertEquals("db_agent", CipherPassphrase.purpose("agent"))
        assertEquals("db_settings", CipherPassphrase.purpose("settings"))
    }

    @Test
    fun purpose_doesNotApplyLocaleDependentCaseTransform() {
        // dbId 为纯拼接，不做 lowercase/uppercase，区域变化不应改变结果。
        assertEquals("db_T2I", CipherPassphrase.purpose("T2I"))
        assertEquals("db_INFRA", CipherPassphrase.purpose("INFRA"))
    }

    @Test
    fun isDbPurpose_trueOnlyForDbPrefixedPurpose() {
        assertTrue(CipherPassphrase.isDbPurpose("db_agent"))
        assertTrue(CipherPassphrase.isDbPurpose("db_settings"))
        // 前缀即判定依据，后缀任意
        assertTrue(CipherPassphrase.isDbPurpose("db_anything_here"))
    }

    @Test
    fun isDbPurpose_falseForFieldKeysAndEdgeInputs() {
        assertFalse(CipherPassphrase.isDbPurpose("field_credentials"))
        assertFalse(CipherPassphrase.isDbPurpose("master_key"))
        assertFalse(CipherPassphrase.isDbPurpose(""))
        // 仅前缀但无 id 不算有效库密钥
        assertFalse(CipherPassphrase.isDbPurpose("db_"))
    }

    @Test
    fun openParams_derivesStringAndUtf8BytesFromSameSource() {
        val passphrase = "QmFzZTY0LWRlbW8tREVLA=="
        val params = CipherPassphrase.openParams(passphrase)

        assertEquals(passphrase, params.passphrase)
        assertArrayEquals(passphrase.toByteArray(Charsets.UTF_8), params.bytes)
    }

    @Test
    fun openParams_twoOpenParamsWithSamePassphraseAreEqual() {
        val a = CipherPassphrase.openParams("same-passphrase")
        val b = CipherPassphrase.openParams("same-passphrase")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun openParams_bytesTrackPassphraseUtf8Representation() {
        // 含非 ASCII 字符时，bytes 必须是其 UTF-8 编码而非平台默认。
        val passphrase = "pàss—wõrd"
        val params = CipherPassphrase.openParams(passphrase)
        assertArrayEquals(passphrase.toByteArray(Charsets.UTF_8), params.bytes)
        assertNotEquals(passphrase.length.toLong(), params.bytes.size.toLong())
    }
}
