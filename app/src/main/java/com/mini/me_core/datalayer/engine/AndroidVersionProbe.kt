package com.mini.me_core.datalayer.engine

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.encryption.CipherPassphrase
import com.mini.me_core.datalayer.encryption.UnifiedKeyManager
import com.mini.me_core.datalayer.migration.VERSION_UNREADABLE
import com.mini.me_core.datalayer.migration.VersionProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase as CipherSQLiteDatabase

/**
 * [VersionProbe] 的 Android 实现：在 SQLDelight driver 构造**之前**，读取库文件的真实 `user_version`。
 *
 * ⚠️ 本项目 6 个库默认全部 SQLCipher 加密
 * （见 [com.mini.me_core.datalayer.encryption.DatabaseDefinition.encryptionRequired]，
 * 且 [com.mini.me_core.datalayer.encryption.EncryptedDriverFactory] 恒用 `SupportFactory` 建驱动）。
 * 因此**不能**用明文 [android.database.sqlite.SQLiteDatabase] 直接打开加密库——加密库头不是合法明文
 * SQLite 头，会抛 `SQLITE_NOTADB`（code 26）。历史事故：该误报被记为「库损坏」，且使 [preOpen] 恒判
 * `FRESH`，导致迁移前文件级快照安全网（§5.3）从未执行。
 *
 * 探测策略（按文件头分流，消除误报）：
 *  1. 文件不存在 / 空文件 → 0（全新库）；
 *  2. 前 16 字节 == `SQLite format 3\u0000` → 明文库 → 明文只读打开读 `PRAGMA user_version`；
 *  3. 否则视为 SQLCipher 加密库 → 用 [UnifiedKeyManager] 取 DEK，以 SQLCipher 只读打开读取；
 *  4. 有密钥仍打不开 → 真·损坏 / 密钥不匹配 → 记日志返回 [VERSION_UNREADABLE]（-1），
 *     上层 [com.mini.me_core.datalayer.migration.PreOpenAction.UNREADABLE] 先快照再隔离，
 *     **不**按全新库处理（返回 0 会导致历史数据被静默清空）。
 *
 * 只读打开不会触发 SQLDelight 的 `onCreate` / `onUpgrade`，故读到的是库当前真实版本，
 * 而非迁移后的目标版本。见 [VersionProbe] 文档说明。
 */
class AndroidVersionProbe(
    private val context: Context,
    private val pathProvider: DatabasePathProvider,
    private val keyManager: UnifiedKeyManager? = null,
) : VersionProbe {

    override fun readVersion(lib: LibName): Int {
        val file = pathProvider.mainDb(lib)
        if (!file.exists()) {
            FileLogger.i(TAG, "readVersion($lib): 文件不存在 → 全新库(0)")
            return 0
        }
        if (file.length() == 0L) {
            FileLogger.i(TAG, "readVersion($lib): 空文件 → 全新库(0)")
            return 0
        }
        return if (isPlaintextSqlite(file)) readVersionPlaintext(file, lib) else readVersionEncrypted(file, lib)
    }

    /** 文件头是否等于明文 SQLite 魔数 `SQLite format 3\u0000`。 */
    private fun isPlaintextSqlite(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val head = ByteArray(SQLITE_MAGIC.size)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n <= 0) break
                read += n
            }
            read == head.size && head.contentEquals(SQLITE_MAGIC)
        }
    } catch (e: Exception) {
        FileLogger.w(TAG, "readVersion: 读取文件头失败 ${file.name}，按加密库处理", e)
        false
    }

    private fun readVersionPlaintext(file: File, lib: LibName): Int = try {
        FrameworkSQLiteDatabase.openDatabase(file.absolutePath, null, FrameworkSQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    } catch (e: Exception) {
        // 明文库仍打不开：真·损坏 / 不可读。
        // ⚠️ 返回 UNREADABLE 而非 0：文件已有数据，绝不可被当作「全新库」静默重建。
        FileLogger.e(
            TAG,
            "readVersion($lib) 明文库只读打开失败（真·损坏）：size=${file.length()} head=${headHex(file)}，" +
                "返回 UNREADABLE 交由上层先快照再隔离",
            e,
        )
        VERSION_UNREADABLE
    }

    private fun readVersionEncrypted(file: File, lib: LibName): Int {
        val km = keyManager
        if (km == null) {
            // 未注入密钥管理器（如 JVM/特殊环境）：无法解密探测，保守按 0 处理。
            FileLogger.w(TAG, "readVersion($lib): 加密库但未注入 UnifiedKeyManager，按 0 处理")
            return 0
        }
        var cipherDb: CipherSQLiteDatabase? = null
        return try {
            // purpose 与 passphrase 必须与 EncryptedDriverFactory 完全一致（共用 CipherPassphrase），
            // 否则会出现「driver 能开、probe 打不开」的假损坏。
            val dek = runBlocking(Dispatchers.IO) { km.getOrCreateDek(CipherPassphrase.purpose(lib)) }
            val passphrase = CipherPassphrase.encode(dek)
            dek.fill(0)
            CipherSQLiteDatabase.loadLibs(context)
            val opened = CipherSQLiteDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                CipherSQLiteDatabase.OPEN_READONLY,
            )
            cipherDb = opened
            val cursor = opened.rawQuery("PRAGMA user_version", null)
            try {
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            } finally {
                cursor.close()
            }
        } catch (e: Exception) {
            // 有密钥仍打不开：真·损坏 / 密钥不匹配 / 迁移中断的半成品。
            // ⚠️ 返回 UNREADABLE 而非 0：0 会被判为「全新库」，跳过快照并可能被自愈清空。
            FileLogger.e(
                TAG,
                "readVersion($lib) 加密库只读打开失败（损坏/密钥不匹配）：size=${file.length()} head=${headHex(file)}，" +
                    "返回 UNREADABLE 交由上层先快照再隔离",
                e,
            )
            VERSION_UNREADABLE
        } finally {
            runCatching { cipherDb?.close() }
        }
    }

    /** 取文件头前 8 字节的十六进制，用于区分「文件损坏」与「密钥不匹配」的现场取证。 */
    private fun headHex(file: File): String = try {
        FileInputStream(file).use { input ->
            val head = ByteArray(8)
            val n = input.read(head)
            if (n <= 0) "<empty>" else head.copyOf(n).joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        "<unreadable:${e.javaClass.simpleName}>"
    }

    companion object {
        private const val TAG = "AndroidVersionProbe"
        private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}