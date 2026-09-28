package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.engine.DatabasePathProvider
import com.mini.me_core.datalayer.engine.LibName
import java.io.File

/**
 * 迁移引擎（设计 §5：数据保护核心）。
 *
 * 职责：
 *  - **[preOpen]（driver 打开前）**：用 [VersionProbe] 原生只读探测真实 user_version，
 *    在「迁移前」对旧版本库做文件级快照（§5.3 安全网）；版本回退（current > target）提前拒绝。
 *    ⚠️ 必须在 `AndroidSqliteDriver` 构造**之前**调用——driver 构造即打开并执行
 *    create/migrate，届时 user_version 已被改写为 target，再探测就永远进不了快照分支。
 *  - **[ensureSchema]（driver 打开后）**：打开后兜底校验 + 代码迁移（[CodeMigration]）。
 *    版本探测与迁移前快照已前移至 [preOpen]，此处 `currentVersion(driver)` 读到的是迁移后的版本。
 *
 * 失败语义（§5.4）：本引擎不写版本号、保留现场，由调用方（DataLayerModule 打开环节）决定重试/回滚；
 * 连续失败 N 次由上层回滚到 [restoreSnapshot] 产出的快照。
 */
class MigrationEngine(
    private val pathProvider: DatabasePathProvider,
    // 默认探测（非 Android / 测试环境）：不读物理版本、一律视为「已对齐」，
    // 不触发快照。真实 Android 环境由 DataLayerModule 注入 AndroidVersionProbe 覆盖。
    private val probe: VersionProbe = object : VersionProbe {
        override fun readVersion(lib: LibName): Int = 0
    },
) {

    companion object {
        const val TAG = "MigrationEngine"

        /**
         * 快照保留份数（含「最近一次」的 `<name>.bak`）。
         *
         * 为什么不再只留一份：单份快照是**覆盖式**的——若应用在「迁移中途失败」后再次启动，
         * 新一轮 preOpen 会立刻用（已被写坏的）主库覆盖掉唯一快照，安全网在真正需要它的那一刻
         * 恰好失效。轮转后至少保住迁移前的若干代现场，可人工挑一份完整恢复。
         */
        const val MAX_SNAPSHOTS = 3

        /** 快照后缀：`<name>.bak` = 最近一次；`<name>.<时间戳>.bak` = 轮转历史。 */
        const val SNAPSHOT_SUFFIX = ".bak"
    }

    /**
     * driver 构造（打开 / 迁移）**之前**调用：探测真实 user_version 并执行迁移前快照。
     *
     * 必须在 [com.mini.me_core.datalayer.engine.ConnectionPool.driver] 的
     * `factory.create(lib)`（即 `AndroidSqliteDriver` 构造）之前触发，
     * 否则快照永远落在「数据已被迁移」之后，失去回滚意义。
     *
     * @return 本次决策动作（[PreOpenAction]），便于上层日志与测试断言。
     */
    fun preOpen(lib: LibName, schema: SqlSchema<*>, heavy: Boolean = false): PreOpenAction {
        val current = probe.readVersion(lib)
        val target = schema.version
        FileLogger.d(TAG, "preOpen($lib): current=$current target=$target")
        val action = decidePreOpen(current, target)
        when (action) {
            PreOpenAction.FRESH -> {
                FileLogger.d(TAG, "  $lib 全新库（current=0），待 driver 打开时 onCreate 建表，无需快照")
            }
            PreOpenAction.UPGRADE_SNAPSHOT -> {
                FileLogger.i(TAG, "  $lib 旧版本 $current → $target，driver 打开(迁移)前先快照保命")
                snapshot(lib, heavy)
            }
            PreOpenAction.ALIGNED_NOOP -> {
                FileLogger.d(TAG, "  $lib 版本已对齐（current==target==$current），无需快照")
            }
            PreOpenAction.DOWNGRADE -> {
                // 提前到打开前拒绝，避免用低版本 schema 打开高版本数据造成损坏。
                error("[$lib] 检测到版本回退：$current > $target，拒绝打开以防数据损坏")
            }
            PreOpenAction.UNREADABLE -> {
                // 文件存在但打不开（损坏 / 密钥不匹配 / 迁移半成品）。
                // 顺序不可颠倒：先快照（复制到 backup/ 保命）→ 再隔离（重命名主库），
                // 之后 driver 才能以全新库重建，且原始文件仍在，可人工恢复。
                FileLogger.e(TAG, "  $lib 库文件存在但无法打开（损坏/密钥不匹配）：先快照保命，再隔离原文件")
                snapshot(lib, heavy)
                preservePlaintextBackup(lib)
                quarantine(lib)
            }
        }
        return action
    }

    fun ensureSchema(
        lib: LibName,
        driver: SqlDriver,
        schema: SqlSchema<*>,
        codeMigrations: List<CodeMigration> = emptyList(),
        sqlMigrations: Array<out AfterVersion> = emptyArray(),
        heavy: Boolean = false,
    ) {
        // ⚠️ driver 已在 factory.create 阶段打开并执行过 onCreate/onUpgrade（schema.create/migrate），
        //    此处 currentVersion(driver) 读到的是「迁移后」的 user_version。
        //    版本探测 + 迁移前快照已前移至 [preOpen]（在 factory.create 之前调用）。
        //    本方法现在只负责：打开后兜底校验 + codeMigrations + 日志。
        val current = currentVersion(driver)
        val target = schema.version
        FileLogger.d(TAG, "ensureSchema($lib): current=$current target=$target（打开后兜底）")
        when {
            current.toLong() == target -> {
                // 显式 no-op。⚠️ 版本相等 ≠ 表结构一致（v0.5.0-rc1 事故）：结构演进未同步新增 .sqm 时
                // user_version 不变但表缺列，结构完整性由 ConnectionPool.onOpened 里的 SchemaSelfHealer 保证。
                FileLogger.v(TAG, "  $lib 版本已对齐（current==target==$current），no-op")
            }
            current.toLong() < target -> {
                // 正常不该进入（preOpen 已先快照并让 driver 完成迁移）；
                // 若因某种原因 preOpen 未跑而 driver 仍完成了升级，这里补执行 codeMigrations 兜底。
                FileLogger.w(TAG, "  $lib 打开后 current($current) < target($target)：preOpen 可能未执行，补跑 codeMigrations")
                codeMigrations
                    .filter { it.from >= current && it.to <= target }
                    .sortedBy { it.from }
                    .forEach { it.block(driver) }
            }
            else -> error("[$lib] 检测到版本回退：$current > $target，拒绝打开以防数据损坏")
        }
    }

    /** 读取 PRAGMA user_version（每库单一版本真相，§5.6）。 */
    fun currentVersion(driver: SqlDriver): Int {
        return driver.executeQuery(null, "PRAGMA user_version", { cursor ->
            // SQLDelight 2.x：SqlCursor.next() 返回 QueryResult<Boolean>，经 .value 解包；
            // executeQuery 的 map 需返回 QueryResult<R>（非裸值）。
            val v = if (cursor.next().value) cursor.getLong(0)?.toInt() ?: 0 else 0
            QueryResult.Value(v)
        }, 0, null).value
    }

    /**
     * 迁移前文件级快照（§5.3）：cp 主库 + -wal + -shm 到 backup/<name>.bak（零逻辑、保真）。
     *
     * 轮转语义：写入新快照**前**，先把上一份 `<name>.bak` 另存为 `<name>.<时间戳>.bak`，
     * 再按 [MAX_SNAPSHOTS] 淘汰最旧的历史快照。
     * `<name>.bak` 始终是「最近一次」，[restoreSnapshot] 的回滚目标不变。
     */
    fun snapshot(lib: LibName, heavy: Boolean) {
        val main = pathProvider.mainDb(lib)
        if (!main.exists()) return
        val bak = pathProvider.snapshotFile(lib)
        rotateCurrentSnapshot(bak)
        main.copyTo(bak, overwrite = true)
        copySidecar(main, bak, "wal")
        copySidecar(main, bak, "shm")
        pruneSnapshots(lib)
        if (heavy) {
            // 重版本/危险迁移：此处叠加逻辑备份（SQL dump / 表级导出），当前留扩展位。
        }
    }

    /**
     * 把「最近一次」快照 `<name>.bak` 另存为历史快照 `<name>.<时间戳>.bak`（-wal / -shm 跟随）。
     *
     * 失败（renameTo 返回 false）时不阻断：宁可覆盖旧快照，也不能让快照流程抛异常拖垮启动。
     */
    private fun rotateCurrentSnapshot(bak: File) {
        if (!bak.exists()) return
        val base = bak.nameWithoutExtension
        var rotated = bak.resolveSibling("$base.${System.currentTimeMillis()}$SNAPSHOT_SUFFIX")
        var seq = 0
        while (rotated.exists() && seq < 100) {
            seq++
            rotated = bak.resolveSibling("$base.${System.currentTimeMillis()}-$seq$SNAPSHOT_SUFFIX")
        }
        if (!bak.renameTo(rotated)) {
            FileLogger.w(TAG, "快照轮转失败（renameTo 返回 false）：${bak.name}，本次将覆盖旧快照")
            return
        }
        FileLogger.d(TAG, "快照轮转：${bak.name} → ${rotated.name}")
        listOf("wal", "shm").forEach { ext ->
            val src = bak.resolveSibling("${bak.name}-$ext")
            if (src.exists() && !src.renameTo(rotated.resolveSibling("${rotated.name}-$ext"))) {
                FileLogger.w(TAG, "快照轮转 $ext 附属文件失败：${src.name}")
            }
        }
    }

    /**
     * 历史快照（**不含**「最近一次」的 `<name>.bak`），按修改时间**降序**（最新在前）。
     *
     * 只认 `<name>.<数字时间戳>.bak` 形态，避免把 `.pre_enc.bak` 等其它产物误当快照淘汰。
     */
    fun listSnapshots(lib: LibName): List<File> {
        val dir = pathProvider.backupDir()
        if (!dir.isDirectory) return emptyList()
        // fileName 含 `.`（minime_agent_v3.db），需转义后再拼正则
        val pattern = Regex("^${Regex.escape(lib.fileName)}\\.\\d+(?:-\\d+)?\\.bak$")
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && pattern.matches(it.name) }
            .sortedByDescending { it.lastModified() }
    }

    /** 淘汰超出 [MAX_SNAPSHOTS] 的历史快照（最旧的先删，含其 -wal / -shm）。 */
    private fun pruneSnapshots(lib: LibName) {
        // `.bak`（最近一次）独立占 1 份，历史最多保留 MAX_SNAPSHOTS - 1 份
        val keep = MAX_SNAPSHOTS - 1
        val history = listSnapshots(lib)
        if (history.size <= keep) return
        history.drop(keep).forEach { stale ->
            listOf("", "-wal", "-shm").forEach { suffix ->
                val f = if (suffix.isEmpty()) stale else stale.resolveSibling("${stale.name}$suffix")
                if (f.exists() && !f.delete()) {
                    FileLogger.w(TAG, "淘汰过期快照失败：${f.name}")
                }
            }
            FileLogger.d(TAG, "淘汰过期快照：${stale.name}")
        }
    }

    /**
     * 在快照保护下执行 [block]（典型场景：driver 创建 / schema 迁移 / 加密形态迁移）。
     *
     * 语义：先 [snapshot] → 执行 → 一旦抛异常立刻 [restoreSnapshot] 回滚，再重抛原异常
     * （不吞异常，上层仍可按既有降级策略处理）。
     *
     * ⚠️ 只适用于 **driver 打开之前**：driver 打开后持有文件句柄，此时替换文件会让
     * 已打开的连接指向被换掉的内容。
     *
     * @return [block] 的返回值。
     */
    fun <T> withSnapshotGuard(lib: LibName, heavy: Boolean = false, block: () -> T): T {
        snapshot(lib, heavy)
        return try {
            block()
        } catch (t: Throwable) {
            val restored = restoreSnapshot(lib)
            FileLogger.e(TAG, "$lib 迁移失败，已自动回滚到快照：restored=$restored", t)
            throw t
        }
    }

    /** 回滚到最近一次快照（§5.4：连续失败 N 次后调用）。 */
    fun restoreSnapshot(lib: LibName): Boolean {
        val main = pathProvider.mainDb(lib)
        val bak = pathProvider.snapshotFile(lib)
        if (!bak.exists()) return false
        // 快照 → 主库（方向不可反：反了会用损坏的主库覆盖掉唯一的安全网快照）
        bak.copyTo(main, overwrite = true)
        restoreSidecar(bak, main, "wal")
        restoreSidecar(bak, main, "shm")
        // 主库已被快照内容替换，原 -wal 属于旧内容，必须清掉避免与新主库不一致
        main.resolveSibling("${main.name}-wal").takeIf { it.exists() && !bak.resolveSibling("${bak.name}-wal").exists() }?.delete()
        return true
    }

    /**
     * 抢救密钥形态迁移留下的源形态备份 `<name>.pre_enc.bak`（KeyRotationMigrator Step8 产物）。
     *
     * 该文件是加密迁移**之前**的明文库，若完整则是损坏现场唯一可直接读取的原始数据。
     * 复制到 backup 目录统一保管：既不覆盖任何现有文件，又让它与快照并列、便于导出恢复。
     *
     * @return 是否发现并保存了明文备份。
     */
    private fun preservePlaintextBackup(lib: LibName): Boolean {
        val main = pathProvider.mainDb(lib)
        val legacy = main.resolveSibling("${main.name}.pre_enc.bak")
        if (!legacy.exists() || legacy.length() == 0L) return false
        val dest = pathProvider.backupDir().resolve("${lib.fileName}.pre_enc.bak")
        return runCatching {
            legacy.copyTo(dest, overwrite = true)
            FileLogger.i(TAG, "发现迁移前明文备份，已另存可人工恢复：${dest.absolutePath}（${legacy.length()} 字节）")
            true
        }.getOrElse {
            FileLogger.w(TAG, "明文备份另存失败：${legacy.absolutePath}（原文件未改动）")
            false
        }
    }

    /**
     * 隔离不可读的主库：重命名为 `<name>.broken-<时间戳>`（-wal / -shm 一并跟随）。
     *
     * 与删除的区别：保留现场，用户可事后用备份/专业工具抢救；也让 driver 能以全新库正常建表启动，
     * 不至于卡在启动崩溃。调用方须**先**调用 [snapshot]——隔离后原路径即为空。
     */
    private fun quarantine(lib: LibName) {
        val main = pathProvider.mainDb(lib)
        if (!main.exists()) return
        val broken = main.resolveSibling("${main.name}.broken-${System.currentTimeMillis()}")
        if (!main.renameTo(broken)) {
            FileLogger.e(TAG, "隔离不可读库失败（renameTo 返回 false）：${main.absolutePath}")
            return
        }
        FileLogger.i(TAG, "已隔离不可读库：${broken.name}（原文件保留，可人工恢复）")
        listOf("wal", "shm").forEach { ext ->
            val src = main.resolveSibling("${main.name}-$ext")
            if (src.exists() && !src.renameTo(broken.resolveSibling("${broken.name}-$ext"))) {
                FileLogger.w(TAG, "隔离 $ext 附属文件失败：${src.name}")
            }
        }
    }

    private fun copySidecar(main: File, bak: File, ext: String) {
        val src = main.resolveSibling("${main.name}-$ext")
        if (src.exists()) src.copyTo(bak.resolveSibling("${bak.name}-$ext"), overwrite = true)
    }

    private fun restoreSidecar(bak: File, main: File, ext: String) {
        val src = bak.resolveSibling("${bak.name}-$ext")
        if (src.exists()) src.copyTo(main.resolveSibling("${main.name}-$ext"), overwrite = true)
    }
}
