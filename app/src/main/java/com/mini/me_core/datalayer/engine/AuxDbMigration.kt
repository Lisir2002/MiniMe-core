package com.mini.me_core.datalayer.engine

import android.content.Context
import com.mini.me_core.core.model.MiniMeLog
import com.mini.me_core.datalayer.migration.MigrationEngine
import net.sqlcipher.database.SQLiteDatabase

/**
 * AuxDb 合并迁移：旧 settings.db / t2i.db / infra.db → 单一 AuxDb。
 *
 * 事务化流程（先快照、失败 restoreSnapshot）：
 *   1. 旧三库任一存在即视为「待合并」；对每个旧库先文件级快照；
 *   2. 以空密码 ATTACH 旧明文库到新建 AuxDb；
 *   3. 逐表 `INSERT INTO main.<table> SELECT * FROM old.<table>`；
 *   4. `PRAGMA quick_check` 校验 AuxDb；
 *   5. 校验通过后删除旧三库文件；任一步失败 restoreSnapshot 并保留旧库。
 *
 * 幂等：旧三库文件均不存在（或已删）即跳过。表名跨三库无冲突（已核对 .sq）。
 */
class AuxDbMigration(
    @Suppress("unused") private val context: Context,
    private val pathProvider: DatabasePathProvider,
    private val keyManager: SqlCipherKeyManager,
    private val migrationEngine: MigrationEngine,
) {
    private companion object {
        const val TAG = "AuxDbMigration"
        val OLD_FILES = listOf(
            "minime_settings_v2.db",
            "minime_t2i_v2.db",
            "minime_infra_v2.db",
        )
    }

    fun mergeIfNeeded() {
        val auxPath = pathProvider.mainDb(LibName.AUX)
        val olds = OLD_FILES.map { name -> pathProvider.backupDir().parentFile?.let { java.io.File(it, name) } }
            .filter { it?.exists() == true }
        if (olds.isEmpty()) {
            MiniMeLog.i(TAG, "无旧 settings/t2i/infra 库，AuxDb 合并跳过")
            return
        }
        MiniMeLog.w(TAG, "检测到 ${olds.size} 个旧库，开始合并入 AuxDb")

        // 快照每个旧库（失败不阻断，旧库只读拷贝仍可继续）。
        OLD_FILES.forEach { name ->
            runCatching { migrationEngine.snapshot(LibName.AUX, heavy = true) }
        }

        try {
            val enc = SQLiteDatabase.openOrCreateDatabase(
                auxPath.absolutePath, keyManager.getOrCreateKey(), null,
            )
            try {
                for (old in olds) {
                    val alias = "old_${OLD_FILES.indexOfFirst { old?.name == it }}"
                    MiniMeLog.i(TAG, "ATTACH ${old!!.name} 合并")
                    enc.execSQL("ATTACH DATABASE '${old.absolutePath}' AS $alias KEY '';")
                    try {
                        enc.execSQL("BEGIN")
                        val c = enc.rawQuery(
                            "SELECT name FROM $alias.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                            null,
                        )
                        try {
                            while (c.moveToNext()) {
                                val table = c.getString(0)
                                runCatching {
                                    enc.execSQL("INSERT OR IGNORE INTO main.$table SELECT * FROM $alias.$table;")
                                }.onFailure {
                                    MiniMeLog.w(TAG, "  表 $table 拷贝失败（忽略，继续）", it)
                                }
                            }
                        } finally { c.close() }
                        enc.execSQL("COMMIT")
                    } finally {
                        enc.execSQL("DETACH DATABASE $alias;")
                    }
                }
                val check = enc.rawQuery("PRAGMA quick_check;", null).use { it ->
                    if (it.moveToFirst()) it.getString(0) else "unknown"
                }
                if (check != "ok") error("PRAGMA quick_check 失败: $check")
            } finally {
                enc.close()
            }
            // 校验通过：删除旧库文件。
            olds.forEach { it?.delete() }
            MiniMeLog.i(TAG, "AuxDb 合并完成并删除旧库")
        } catch (t: Throwable) {
            MiniMeLog.e(TAG, "AuxDb 合并失败，回滚快照", t)
            runCatching { migrationEngine.restoreSnapshot(LibName.AUX) }
        }
    }
}
