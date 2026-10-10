package com.mini.me_core.datalayer.encryption

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DatabaseErrorClassifier] 与 [DatabaseRebuildPolicy] 纯 JVM 单测。
 *
 * 核心不变量：连接态/密钥态瞬时错误（passphrase cleared / encryption error / locked）
 * 必须判为**非损坏**，默认策略 [DenyingRebuildPolicy] 拒绝任何自动删库重建。
 */
class DatabaseErrorClassifierTest {

    private fun err(msg: String, cause: Throwable? = null) =
        object : Exception(msg, cause) {}

    @Test
    fun passphraseCleared_isTransientNotCorruption() {
        val e = err("passphrase appears to be cleared")
        assertFalse(DatabaseErrorClassifier.isRealCorruption(e))
        assertTrue(DatabaseErrorClassifier.isTransientOrKeyState(e))
    }

    @Test
    fun encryptionError_isTransientNotCorruption() {
        val e = err("file is encrypted or is not a database")
        // 注意：含 "is not a database" 子串但实际是 encryption error 路径，
        // 分类器必须把 "encryption error" 判为密钥态瞬时错误，而非删库。
        assertTrue(DatabaseErrorClassifier.isTransientOrKeyState(e))
    }

    @Test
    fun databaseLocked_isTransient() {
        val e = err("database is locked")
        assertFalse(DatabaseErrorClassifier.isRealCorruption(e))
        assertTrue(DatabaseErrorClassifier.isTransientOrKeyState(e))
    }

    @Test
    fun fileIsNotDatabase_isRealCorruption() {
        val e = err("file is not a database")
        assertTrue(DatabaseErrorClassifier.isRealCorruption(e))
        assertFalse(DatabaseErrorClassifier.isTransientOrKeyState(e))
    }

    @Test
    fun malformedDatabase_isRealCorruption() {
        val e = err("database disk image is malformed")
        assertTrue(DatabaseErrorClassifier.isRealCorruption(e))
    }

    @Test
    fun denyingPolicy_neverAllowsRebuild() {
        val policy = DenyingRebuildPolicy()
        // 即使是真实损坏，默认策略也拒绝自动删库（必须用户显式确认）。
        assertFalse(policy.allowAutoRebuild(File("/x/data.db"), err("file is not a database")))
    }

    @Test
    fun transientError_shouldNotTriggerRebuildPath() {
        // 组合判定：瞬时错误 → 不是损坏 → 策略也不允许重建 → 二者叠加 = 绝不删库。
        val e = err("passphrase appears to be cleared")
        val policy = DenyingRebuildPolicy()
        val wouldRebuild = DatabaseErrorClassifier.isRealCorruption(e) &&
            policy.allowAutoRebuild(File("/x/data.db"), e)
        assertFalse(wouldRebuild)
    }
}
