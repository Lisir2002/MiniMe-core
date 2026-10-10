package com.mini.me_core.datalayer.encryption

import java.util.Locale

/**
 * 打开加密数据库时的错误分类器（纯 JVM，可单测）。
 *
 * 必须把两类错误严格区分：
 *  - [isRealCorruption]：库文件本身损坏（不可解析），才考虑快照/重建；
 *  - [isTransientOrKeyState]：连接态/密钥态瞬时错误（口令被清除、SQLCipher 未正确 attach、
 *    临时锁等），**绝不**删库重建，原样上抛由上层重试/修复。
 */
object DatabaseErrorClassifier {

    private fun combined(e: Exception): String {
        val msg = (e.message ?: "").lowercase(Locale.ROOT)
        val causeMsg = (e.cause?.message ?: "").lowercase(Locale.ROOT)
        return "$msg $causeMsg"
    }

    /** 真实文件级损坏：只有库文件不可解析才算。 */
    fun isRealCorruption(e: Exception): Boolean {
        val c = combined(e)
        return c.contains("file is not a database") ||
            c.contains("malformed database") ||
            c.contains("database disk image is malformed")
    }

    /** 连接态/密钥态瞬时错误（不应删库）。 */
    fun isTransientOrKeyState(e: Exception): Boolean {
        val c = combined(e)
        return c.contains("passphrase appears to be cleared") ||
            c.contains("encryption error") ||
            // SQLCipher 经典「库已加密但 key 不对/未 attach」：属密钥态，不是文件损坏。
            c.contains("file is encrypted") ||
            c.contains("wrong key") ||
            c.contains("bad key") ||
            c.contains("missing key") ||
            c.contains("not an error") ||
            c.contains("database is locked") ||
            c.contains("unable to open database") ||
            c.contains("missing database file")
    }
}
