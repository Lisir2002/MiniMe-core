package com.mini.me_core.datalayer.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.mini.me_core.core.util.FileLogger

/**
 * 幂等「结构自愈」器（MySQLite 数据保护 §5.7）。
 *
 * 背景：历史库存在「schema 版本已递增 / 版本号相同但表结构不一致」的坑
 * （见 [MigrationEngine] 里 v0.5.0-rc1 同款事故：加列/改列却未同步新增 .sqm，
 * 导致 `user_version == target` 命中 no-op，旧表缺列残留、首查即崩）。
 * 典型故障：`no such column: agent_message.id`。
 *
 * 本器通过 PRAGMA table_info 做「列存在性校验」，缺缺必要列时执行 SQLite 无损重建
 * （改名旧表 → 建新表 → 按列交集迁移数据 → 重建索引 → 删旧表）。全程不改 schema 版本、
 * 不丢数据、幂等（已修复则直接跳过）。新建库因 schema 本身就含全列，也会直接跳过。
 *
 * v2-full-takeover P3 诊断加固：healAgentSession / healAgentMessage / healTable 全节点
 * 加 FileLogger，让崩溃快照里能看到「existing 列是什么、missing 列是什么、RENAME 是否成功」，
 * 彻底消除「自愈没生效到底是没执行还是执行了但跑失败」的盲区。
 */
object SchemaSelfHealer {

    private const val TAG = "SchemaSelfHealer"

    // ── agent_message 目标结构（与 sqldelight/agent/agent.sq 保持一致）──────────

    private val AGENT_MESSAGE_COLUMNS = listOf(
        "id", "session_id", "role", "seq", "created_at", "task_id", "content",
        "tool_calls_json", "tool_call_id", "tool_name", "tool_args", "is_error",
        "reasoning", "signature", "attachments_json", "is_compacted",
        "is_context_summary", "is_compaction_marker", "input_tokens", "output_tokens",
        "chunk_group_id", "chunk_index"
    )

    private val AGENT_MESSAGE_CREATE = """
        CREATE TABLE agent_message (
          id                   TEXT    NOT NULL PRIMARY KEY,
          session_id           TEXT    NOT NULL,
          role                 TEXT    NOT NULL,
          seq                  INTEGER NOT NULL,
          created_at           INTEGER NOT NULL,
          task_id              TEXT    NOT NULL DEFAULT '',
          content              TEXT    NOT NULL DEFAULT '',
          tool_calls_json      TEXT,
          tool_call_id         TEXT,
          tool_name            TEXT,
          tool_args            TEXT,
          is_error             INTEGER NOT NULL DEFAULT 0,
          reasoning            TEXT,
          signature            TEXT,
          attachments_json     TEXT,
          is_compacted         INTEGER NOT NULL DEFAULT 0,
          is_context_summary   INTEGER NOT NULL DEFAULT 0,
          is_compaction_marker INTEGER NOT NULL DEFAULT 0,
          input_tokens         INTEGER NOT NULL DEFAULT 0,
          output_tokens        INTEGER NOT NULL DEFAULT 0,
          chunk_group_id       TEXT    NOT NULL DEFAULT '',
          chunk_index          INTEGER NOT NULL DEFAULT 0,
          FOREIGN KEY (session_id) REFERENCES agent_session(id)
        );
    """.trimIndent()

    private const val AGENT_MESSAGE_SESSION_IDX =
        "CREATE INDEX agent_message_session_idx ON agent_message (session_id, seq);"

    /**
     * agent_session 的排序索引（与 `agent.sq` 的 `agent_session_updated_idx` 一致）。
     *
     * ⚠️ 自愈时必须一并重建：旧表被 DROP 后索引随之消失，少了这一条会话列表会退化为全表扫描
     * （历史上 `healAgentSession` 未传 indexSqls，索引永久丢失）。
     */
    private const val AGENT_SESSION_UPDATED_IDX =
        "CREATE INDEX agent_session_updated_idx ON agent_session (updated_at);"

    // ── agent_session 目标结构（与 sqldelight/agent/agent.sq 保持一致）──────────
    // agent_session 与 agent_message 同属 P2-3「对齐全表」的演进表，结构漂移风险同类，一并自愈。

    private val AGENT_SESSION_COLUMNS = listOf(
        "id", "title", "mode", "model", "status", "created_at", "updated_at",
        "workspace_path", "reasoning_effort", "provider_id",
        "total_input_tokens", "total_output_tokens", "last_input_tokens"
    )

    private val AGENT_SESSION_CREATE = """
        CREATE TABLE agent_session (
          id                  TEXT    NOT NULL PRIMARY KEY,
          title               TEXT,
          mode                TEXT    NOT NULL,
          model               TEXT,
          status              TEXT    NOT NULL,
          created_at          INTEGER NOT NULL,
          updated_at          INTEGER NOT NULL,
          workspace_path      TEXT    NOT NULL DEFAULT '',
          reasoning_effort    TEXT    NOT NULL DEFAULT 'MEDIUM',
          provider_id         TEXT,
          total_input_tokens  INTEGER NOT NULL DEFAULT 0,
          total_output_tokens INTEGER NOT NULL DEFAULT 0,
          last_input_tokens   INTEGER NOT NULL DEFAULT 0
        );
    """.trimIndent()

    /** 修复 agent_message 缺 id（或其他目标列）的历史库。打开 AgentDb 后调用一次。 */
    fun healAgentMessage(driver: SqlDriver) {
        FileLogger.i(TAG, "healAgentMessage 开始")
        healTable(
            driver = driver,
            table = "agent_message",
            targetColumns = AGENT_MESSAGE_COLUMNS,
            createSql = AGENT_MESSAGE_CREATE,
            indexSqls = listOf(AGENT_MESSAGE_SESSION_IDX),
        )
    }

    /**
     * 保证性复核（在 [healAgentMessage] 之后调用）：若 `id` 列仍缺失（极端漂移 / 前次自愈半途未落地），
     * 立即对齐全列再次无损重建；仍失败则抛出明确异常（让启动上层可见，而非落入 confusing 的
     * `no such column` 崩溃）。确保「打开后 agent_message 一定可查询」。
     */
    fun ensureAgentMessageUsable(driver: SqlDriver) {
        if (hasColumn(driver, "agent_message", "id")) {
            FileLogger.i(TAG, "agent_message.id 列存在，结构正常")
            return
        }
        FileLogger.w(TAG, "agent_message.id 仍缺失！强制再次重建")
        healTable(
            driver = driver,
            table = "agent_message",
            targetColumns = AGENT_MESSAGE_COLUMNS,
            createSql = AGENT_MESSAGE_CREATE,
            indexSqls = listOf(AGENT_MESSAGE_SESSION_IDX),
        )
        if (!hasColumn(driver, "agent_message", "id")) {
            val cols = runCatching { tableColumns(driver, "agent_message").joinToString() }.getOrDefault("?")
            val legacyCols = runCatching { tableColumns(driver, "agent_message_legacy").joinToString() }.getOrDefault("?")
            val sessionCols = runCatching { tableColumns(driver, "agent_session").joinToString() }.getOrDefault("?")
            FileLogger.e(TAG, "agent_message 自愈后仍缺 id 列！" +
                    " 当前 agent_message 列: [$cols]；" +
                    " agent_message_legacy 列: [$legacyCols]；" +
                    " agent_session 列: [$sessionCols]")
            throw IllegalStateException(
                "agent_message 自愈后仍缺 id 列，表结构异常且无法自愈，请人工介入检查 agent.db。" +
                        "当前 agent_message 列: [$cols]；agent_session 列: [$sessionCols]"
            )
        }
        FileLogger.i(TAG, "强制重建后 agent_message.id 已存在")
    }

    /** 判断 [table] 是否含 [column]（PRAGMA table_info 命中）。 */
    fun hasColumn(driver: SqlDriver, table: String, column: String): Boolean =
        tableColumns(driver, table).contains(column)

    /** 与 agent_message 同风险的 agent_session 结构自愈（含索引重建，见 [AGENT_SESSION_UPDATED_IDX]）。 */
    fun healAgentSession(driver: SqlDriver) {
        FileLogger.i(TAG, "healAgentSession 开始")
        healTable(
            driver = driver,
            table = "agent_session",
            targetColumns = AGENT_SESSION_COLUMNS,
            createSql = AGENT_SESSION_CREATE,
            indexSqls = listOf(AGENT_SESSION_UPDATED_IDX),
        )
    }

    /** agent_session 保证性复核（同 [ensureAgentMessageUsable]）。 */
    fun ensureAgentSessionUsable(driver: SqlDriver) {
        if (hasColumn(driver, "agent_session", "id")) {
            FileLogger.i(TAG, "agent_session.id 列存在，结构正常")
            return
        }
        FileLogger.w(TAG, "agent_session.id 仍缺失！强制再次重建")
        healTable(
            driver = driver,
            table = "agent_session",
            targetColumns = AGENT_SESSION_COLUMNS,
            createSql = AGENT_SESSION_CREATE,
            indexSqls = listOf(AGENT_SESSION_UPDATED_IDX),
        )
        if (!hasColumn(driver, "agent_session", "id")) {
            val cols = runCatching { tableColumns(driver, "agent_session").joinToString() }.getOrDefault("?")
            FileLogger.e(TAG, "agent_session 自愈后仍缺 id 列！当前列: [$cols]")
            throw IllegalStateException(
                "agent_session 自愈后仍缺 id 列，表结构异常且无法自愈，请人工介入检查 agent.db"
            )
        }
        FileLogger.i(TAG, "强制重建后 agent_session.id 已存在")
    }

    /**
     * 通用自愈：若 [table] 缺 [targetColumns] 中任意列，则无损重建为 [createSql] 定义的结构。
     *
     * 顺序：改名旧表 → 建新表 → 按列交集迁移 → 重建索引 → 删旧表。任一步失败则旧表保留，重启自愈重试，不丢数据。
     *
     * 幂等安全垫（否则自愈本身会在启动 main 线程再次 FATAL）：
     *  - [table] 整表缺失：PRAGMA table_info 返回 0 行 → 判定全列缺失，直接 RENAME 会抛 "no such table"，
     *    这里先判空跳过（整表缺失属于 ensureSchema 建表情景，缺列自愈不越权处理）。
     *  - 索引名冲突：SQLite 中 RENAME 后的旧表会保留原索引名，随后 CREATE INDEX 同名会抛 "index already exists"。
     *    这里在建新表前 DROP INDEX IF EXISTS 预清理（旧索引随旧表删表一并消失，属于待回收资源，先删安全）。
     *  - 缺列回填：被缺的 NOT NULL 无默认列（典型即 PK `id`）若直接省略则 INSERT 抛 "NOT NULL constraint failed"。
     *    迁移时对缺失列按「先自身 DEFAULT、再 id→由 rowid 推导、再按类型兜底」回填，保证不丢行、不撞约束。
     *
     * **事务性（致命项 F1 修复，必须保持）**：
     *  旧实现 RENAME → CREATE → INSERT → DROP 全程裸奔：INSERT 一旦失败（单行约束冲突/磁盘满/进程被杀），
     *  新表列齐全 → 下次启动 `missing.isEmpty()` 直接跳过 → `*_legacy` 里的历史数据**永远读不出来且无人知晓**。
     *  现在整段包在事务里：失败即回滚，旧表恢复原名，下次启动可重试；并在 DROP 前断言
     *  `COUNT(新表) == COUNT(legacy)`，不等则中止并保留 legacy。
     *
     * **外键安全（审计报告「待确认 1」的结论）**：
     *  SQLite 3.25+ 的 `ALTER TABLE ... RENAME` 会**改写其它表的 FK 定义**指向新名（`*_legacy`），
     *  随后 DROP 掉 legacy 就留下悬空 FK；开启 `foreign_keys` 后子表 INSERT 会直接失败。
     *  故自愈期间：① 先 `PRAGMA legacy_alter_table=ON`（让 RENAME 不改写引用，子表 FK 继续指向新表）；
     *  ② 临时关闭 `foreign_keys`（重建期间强制 FK 没有意义，且 `legacy_alter_table` 不可用时用它兜底）；
     *  ③ 结束后恢复。两个 PRAGMA 都包 `runCatching`——设备 SQLite 版本不支持时降级，不阻断自愈。
     *
     * v2-full-takeover P3 诊断加固：全节点输出 FileLogger，让崩溃快照里能看到 existing / missing /
     *  RENAME / CREATE / INSERT / DROP 的每一步状态。
     */
    fun healTable(
        driver: SqlDriver,
        table: String,
        targetColumns: List<String>,
        createSql: String,
        indexSqls: List<String> = emptyList(),
    ) {
        val existing = tableColumns(driver, table).toSet()
        FileLogger.i(TAG, "healTable($table): existing=${existing.joinToString()}")

        // 0 列 = 表不存在（合法表不可能 0 列），跳过，避免 RENAME "no such table" 崩溃。
        if (existing.isEmpty()) {
            FileLogger.i(TAG, "  $table 不存在或无列（existing.isEmpty），跳过")
            return
        }

        val missing = targetColumns.filter { it !in existing }
        if (missing.isEmpty()) {
            FileLogger.i(TAG, "  $table 结构完好，无需修复")
            return
        }

        FileLogger.w(TAG, "  $table 缺 ${missing.size} 列: ${missing.joinToString()}，开始无损重建")

        val legacy = "${table}_legacy"

        // 预清理待重建索引：旧表 RENAME 后索引名仍占用同名，先 DROP 避免建新索引冲突。
        indexSqls.forEach { sql ->
            val name = Regex("""(?i)CREATE\s+(UNIQUE\s+)?INDEX\s+IF\s+NOT\s+EXISTS\s+\"?([\w]+)\"?""").find(sql)?.groupValues?.get(2)
                ?: Regex("""(?i)CREATE\s+(UNIQUE\s+)?INDEX\s+\"?([\w]+)\"?""").find(sql)?.groupValues?.get(2)
            if (name != null) {
                try { exec(driver, "DROP INDEX IF EXISTS \"$name\";") } catch (_: Exception) { /* 索引导出即可，失败不阻断 */ }
            }
        }

        // 清理上次自愈中途崩溃（RENAME 后未 DROP）残留的 ${table}_legacy，避免本次 RENAME 撞名失败、
        // 永久锁死后续自愈。若存量数据仍在新表，legacy 为空表，直接删除无数据损失。
        try {
            val legacyExists = !tableColumns(driver, legacy).isEmpty()
            FileLogger.i(TAG, "  预清理 ${table}_legacy（存在=$legacyExists）")
            exec(driver, "DROP TABLE IF EXISTS $legacy;")
        } catch (e: Exception) {
            FileLogger.w(TAG, "  预清理 ${table}_legacy 失败（忽略，RENAME 前再试）: ${e.message}")
            /* 删除失败不阻断主流，RENAME 前再试 */
        }

        // ── FK/ALTER 兼容开关：必须在 BEGIN 之前（事务内 PRAGMA foreign_keys 是 no-op）──
        val fkWasOn = foreignKeysEnabled(driver)
        if (fkWasOn) runCatching { exec(driver, "PRAGMA foreign_keys = OFF;") }
        runCatching { exec(driver, "PRAGMA legacy_alter_table = ON;") }

        val txStarted = beginImmediate(driver)
        try {
            FileLogger.i(TAG, "  ALTER TABLE $table RENAME TO $legacy")
            exec(driver, "ALTER TABLE $table RENAME TO $legacy;")

            FileLogger.i(TAG, "  CREATE TABLE $table")
            exec(driver, createSql)

            val meta = columnMeta(createSql)
            // 迁移：常见列透传，缺失列按默认/类型兜底回填（不丢旧行、不撞 NOT NULL 约束）。
            val insertCols = ArrayList(targetColumns)
            val selectExprs = ArrayList<String>(targetColumns.size)
            val synthetic = ArrayList<String>()
            for (col in targetColumns) {
                if (col in existing) {
                    selectExprs += "\"$col\""
                } else {
                    val m = meta[col]
                    selectExprs += when {
                        // 主键缺失：用 rowid 推导而非随机 UUID —— 随机会让这批行与
                        // task_id / tool_call_id / chunk_group_id 的追溯链彻底断裂，
                        // 而 rowid 推导值可反查回 legacy 表（审计报告 H2）。
                        col == "id" -> {
                            synthetic += col
                            syntheticIdExpr(table, existing)
                        }
                        m?.default != null -> m.default                    // 优先表定义 DEFAULT
                        m?.notNull == true -> if ((m.type ?: "").startsWith("INT")) "0" else "''" // 按类型兜底
                        else -> "NULL"
                    }
                }
            }
            if (synthetic.isNotEmpty()) {
                FileLogger.w(
                    TAG,
                    "  $table 缺主键，按行序推导回填（可反查 $legacy 的 rowid）: ${synthetic.joinToString()}",
                )
            }
            val cols = insertCols.joinToString(",") { "\"$it\"" }
            val exprs = selectExprs.joinToString(",")
            FileLogger.i(TAG, "  INSERT INTO $table ($cols) SELECT $exprs FROM $legacy")
            exec(driver, "INSERT INTO $table ($cols) SELECT $exprs FROM $legacy;")

            indexSqls.forEach {
                FileLogger.i(TAG, "  CREATE INDEX: ${it.take(80)}...")
                exec(driver, it)
            }

            // DROP 前行数断言：行数对不上说明数据没搬全，绝不能删 legacy（删了就永久丢）。
            val newCount = countRows(driver, table)
            val legacyCount = countRows(driver, legacy)
            if (newCount != legacyCount) {
                throw IllegalStateException(
                    "自愈行数校验失败：$table=$newCount 行，但 $legacy=$legacyCount 行；" +
                        "已回滚并保留 $legacy，请人工检查后重试",
                )
            }

            FileLogger.i(TAG, "  DROP TABLE $legacy（行数校验通过：两者均 $newCount 行）")
            exec(driver, "DROP TABLE $legacy;")

            if (txStarted) exec(driver, "COMMIT;")
        } catch (t: Throwable) {
            if (txStarted) runCatching { exec(driver, "ROLLBACK;") }
            FileLogger.e(TAG, "healTable($table) 失败，已回滚：$table 恢复原状，下次启动可重试", t)
            throw t
        } finally {
            runCatching { exec(driver, "PRAGMA legacy_alter_table = OFF;") }
            if (fkWasOn) runCatching { exec(driver, "PRAGMA foreign_keys = ON;") }
        }

        val afterCols = tableColumns(driver, table)
        FileLogger.i(TAG, "healTable($table) 完成，当前列: ${afterCols.joinToString()}")
    }

    /**
     * 主键缺失时的回填表达式：**由 rowid 推导**而非 `randomblob`。
     *
     * 旧实现用 `lower(hex(randomblob(16)))`，每次自愈结果都不同，这批行与
     * `task_id` / `tool_call_id` / `chunk_group_id` 的关联链彻底断裂且无法回溯。
     * 用 `session_id#rowid`（无 session_id 时退化为 `表名#rowid`）既能保证唯一非空，
     * 又能在需要时反查 `*_legacy` 的原始行。
     */
    private fun syntheticIdExpr(table: String, existing: Set<String>): String =
        if ("session_id" in existing) "COALESCE(\"session_id\", '') || '#' || rowid"
        else "'$table' || '#' || rowid"

    /** 开启 IMMEDIATE 事务；已在事务中（嵌套）时返回 false，由外层负责提交/回滚。 */
    private fun beginImmediate(driver: SqlDriver): Boolean =
        try {
            exec(driver, "BEGIN IMMEDIATE TRANSACTION;")
            true
        } catch (e: Exception) {
            FileLogger.w(TAG, "  开启事务失败（多半已在事务中，交由外层管理）: ${e.message}")
            false
        }

    private fun foreignKeysEnabled(driver: SqlDriver): Boolean =
        runCatching {
            driver.executeQuery(null, "PRAGMA foreign_keys", { cursor ->
                val v = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                QueryResult.Value(v == 1L)
            }, 0, null).value
        }.getOrDefault(false)

    private fun countRows(driver: SqlDriver, table: String): Long =
        driver.executeQuery(null, "SELECT COUNT(*) FROM \"$table\"", { cursor ->
            val v = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
            QueryResult.Value(v)
        }, 0, null).value

    /**
     * 解析 CREATE TABLE 的列元数据：name → (type, notNull(含 DEFAULT), default 字面量或 null)。
     * 仅用于自愈回填缺列，面向受控的 [createSql]（本类定义），不追求通用 SQL 完备解析。
     */
    private fun columnMeta(createSql: String): Map<String, ColumnMeta> {
        val map = HashMap<String, ColumnMeta>()
        val body = createSql.substringAfter('(').substringBeforeLast(')')
        for (line in body.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("FOREIGN")) continue
            if (t.startsWith("CONSTRAINT")) continue
            val noTrail = t.removeSuffix(",").trim()
            val tokens = noTrail.split(Regex("\\s+"))
            if (tokens.size < 2) continue
            val name = tokens[0].removePrefix("\"").removeSuffix("\"")
            val upper = noTrail.uppercase()
            val notNull = upper.contains("NOT NULL")
            val type = tokens[1].uppercase().takeIf { it == "TEXT" || it == "INTEGER" || it == "INT" }
            val default = Regex("""(?i)\bDEFAULT\s+('[^']*'|\"(?:\"\")*[^\"]*\"|\S+)""").find(noTrail)?.groupValues?.get(1)
            map[name] = ColumnMeta(type = type, notNull = notNull, default = default)
        }
        return map
    }

    private data class ColumnMeta(val type: String?, val notNull: Boolean, val default: String?)

    /** 读取某表所有列名（PRAGMA table_info 的 name 列，index=1）。 */
    fun tableColumns(driver: SqlDriver, table: String): List<String> =
        driver.executeQuery(null, "PRAGMA table_info($table)", ::mapNameColumn, 0, null).value

    private fun mapNameColumn(cursor: SqlCursor): QueryResult<List<String>> {
        val names = ArrayList<String>()
        while (cursor.next().value) {
            cursor.getString(1)?.let { names += it }
        }
        return QueryResult.Value(names)
    }

    /**
     * 执行单条 SQL：**统一剥离尾部分号**。
     * `AGENTS.md` 的迁移 SQL 纪律要求字面量不含 `;`（防切分器误切多语句），
     * 这里在出口统一处理，调用方保持既有书写习惯即可（审计报告 L3）。
     */
    private fun exec(driver: SqlDriver, sql: String) {
        driver.execute(null, sql.trim().removeSuffix(";"), 0, null)
    }
}
