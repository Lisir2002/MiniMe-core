package com.mini.me_core.datalayer.engine

import android.content.Context
import java.io.File

/**
 * 6 个库的物理路径解析（设计 §12.3）。
 *
 * 硬性保护意图：所有库放在 App 内部存储 /data/data/<pkg>/files/ 之下，
 * 普通用户不可见、不可删，需 root 才能访问（防误删）。
 *
 * ⚠️ 严禁解析到外部/共享存储（getExternalFilesDir / scoped storage / SD 卡）。
 *    任何新库路径必须经本 Provider，杜绝误指到用户可访问空间。
 *
 * v2-full-takeover P0-3：抽为接口，平台实现见 [AndroidDatabasePathProvider]，
 * 使 [com.mini.me_core.datalayer.migration.MigrationEngine] 的快照/回滚可在 JVM 单测覆盖。
 */
/**
 * 库文件名是**数据契约（data contract）**，不可随意手改。
 *
 * ⚠️ 改 `fileName` 后缀（如 v2 → v3）= 放弃旧文件名指向的物理文件 = **清空该库的全部历史数据**
 * （App 之后只会去读新文件名，旧文件不再被打开）。rc7 曾因误判 `no such column: agent_message.id`
 * 根因（实为 SQL 形态不合法，非库损坏）而把 agent 库改名 `minime_agent_v3.db`，
 * 导致所有历史会话被静默清空——这是一次过度反应，代价不可逆。
 *
 * 纪律（固化，防复发）：
 *  1. 表结构演进 / 缺列等问题，**一律走 [SchemaSelfHealer] 无损重建或 .sqm 迁移**，绝不用「改文件名换库」规避；
 *  2. 仅当库文件确属**不可自愈的损坏**（如 `SQLITE_CORRUPT`、只读打开即失败）时，才考虑换文件名重建，
 *     且必须在 changelog 显式记录「为何弃旧库」并保留旧文件可人工恢复；
 *  3. 任何 `fileName` 变更都是破坏性数据事件，需经评审，不在修复提交里顺手改。
 */
/**
 * @param fileName 物理文件名（数据契约）。
 * @param dbId **密钥与注册表侧的唯一标识**：必须与 [com.mini.me_core.datalayer.encryption.DatabaseDefinition.id]
 *   逐字相等（见 `BuiltinDatabases`）。
 *
 * ⚠️ 为什么不从 `name` 推导（如 `name.lowercase()`）：
 *  1. 该 API 语义上是 **locale-sensitive**（等价 `lowercase(Locale.getDefault())`）。
 *     实测（Kotlin 2.1 / JVM）：纯 ASCII 走快路径，土耳其语下 `"T2I".lowercase()` 仍得 `t2i`；
 *     但同一区域的 `java.lang.String.toLowerCase(Locale)` 得到的是 `t2ı`、`I` → `ı`。
 *     把标识正确性押在「当前版本恰好有 ASCII 快路径」上，是不可接受的地基——
 *     一旦库标识含非 ASCII 或实现变化，`db_t2ı` 与 `db_t2i` 错配 → 加密库被判损坏 →
 *     隔离重建 → 历史数据清空。
 *  2. 更本质地：枚举名与 `DatabaseDefinition.id` 本就是两个命名空间，靠字符串变换维系
 *     一致性属于隐式契约。显式写死常量，两路共用同一字面量，与区域和实现彻底解耦。
 */
enum class LibName(val fileName: String, val dbId: String) {
    AGENT("minime_agent_v3.db", "agent"),
    CREDENTIALS("minime_credentials_v2.db", "credentials"),
    SETTINGS("minime_settings_v2.db", "settings"),
    WORKSPACE("minime_workspace_v2.db", "workspace"),
    T2I("minime_t2i_v2.db", "t2i"),
    INFRA("minime_infra_v2.db", "infra"),
}

interface DatabasePathProvider {

    /** 主库路径。 */
    fun mainDb(lib: LibName): File

    /** 备份目录（快照落点所在目录）。 */
    fun backupDir(): File

    /** 迁移前文件级快照落点（设计 §5.3）。 */
    fun snapshotFile(lib: LibName): File = backupDir().resolve("${lib.fileName}.bak")
}

/**
 * Android 实现：主库经系统 getDatabasePath 落到 files/databases/<fileName>
 * （与 AndroidSqliteDriver 实际落点一致）；快照目录 files/backup/ 与主库平级，
 * 便于整目录排除 Android 云备份。
 */
class AndroidDatabasePathProvider(private val context: Context) : DatabasePathProvider {

    override fun mainDb(lib: LibName): File = context.getDatabasePath(lib.fileName)

    override fun backupDir(): File = context.getFilesDir().resolve("backup").also { it.mkdirs() }
}
