package com.mini.me_core.datalayer.encryption

import android.util.Base64
import com.mini.me_core.datalayer.engine.LibName

/**
 * SQLCipher passphrase 与 DEK purpose 的**唯一构造处**。
 *
 * 背景（必须共用的原因）：
 *  - [EncryptedDriverFactory] 用 `SupportFactory(byte[])` 打开库；
 *  - [com.mini.me_core.datalayer.engine.AndroidVersionProbe] 用
 *    `net.sqlcipher.database.SQLiteDatabase.openDatabase(String)` 只读打开做版本探测。
 * 两者过去各自拼 purpose 与 passphrase，一旦漂移（purpose 命名不一致、编码方式不同），
 * 就会出现「driver 能开、probe 打不开」的假损坏：日志报 `file is not a database`，
 * 而数据其实完好。故抽为单点，两边只准调用本对象。
 *
 * 约定：
 *  - DEK purpose：`db_<库标识>`（与旧版兼容映射 [AndroidUnifiedKeyManager] 的
 *    `LEGACY_DB_PURPOSE_MAP` 保持一致，不可改名）；
 *  - passphrase：`Base64(DEK)` 字符串的 UTF-8 字节（driver 侧）/ 同一字符串（probe 侧）。
 */
object CipherPassphrase {

    /** 按 [LibName] 取 DEK purpose（VersionProbe 侧使用）。 */
    fun purpose(lib: LibName): String = "db_${lib.name.lowercase()}"

    /** 按数据库定义 id 取 DEK purpose（driver 侧使用）。 */
    fun purpose(dbId: String): String = "db_$dbId"

    /** DEK → passphrase 字符串。调用方须在用完后 `dek.fill(0)` 擦除。 */
    fun encode(dek: ByteArray): String = Base64.encodeToString(dek, Base64.NO_WRAP)
}
