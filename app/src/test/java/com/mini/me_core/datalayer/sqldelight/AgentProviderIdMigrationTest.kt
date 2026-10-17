package com.mini.mecore.datalayer.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 8.sqm 迁移回归测试：agent_message 增加 provider_id（Anthropic signature 跨账号回放防护）。
 *
 * 价值：把「老库（v8，22 列）→ 迁移 → v9（23 列，含 provider_id 且存量数据不丢）」
 * 钉死在 CI 上。任何人改坏 8.sqm / agent.sq 列序，本测试立刻失败；
 * SQLDelight 编译期校验 .sq 与迁移结果一致（第一道），此处为运行期第二道。
 */
class AgentProviderIdMigrationTest {

    /** v8 老库：只建 agent_message（8.sqm 只 ALTER 这张表），其余表不影响本迁移。 */
    private fun legacyV8Db(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """
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
              chunk_index          INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
            0,
            null,
        )
        driver.execute(null, "PRAGMA user_version = 8", 0, null)
        return driver
    }

    @Test
    fun `migration adds provider_id column and preserves rows`() {
        val driver = legacyV8Db()
        driver.execute(
            null,
            "INSERT INTO agent_message (id, session_id, role, seq, created_at, content, signature) " +
                "VALUES ('m1', 's1', 'ASSISTANT', 1, 1000, '旧回复', 'sig-legacy')",
            0,
            null,
        )

        AgentDb.Schema.migrate(driver, oldVersion = 8, newVersion = 9)

        val columns = driver.executeQuery(null, "PRAGMA table_info(agent_message)", { c ->
            val names = mutableListOf<String>()
            while (c.next().value) {
                c.getString(1)?.let { names += it }
            }
            QueryResult.Value(names)
        }, 0, null).value
        assertTrue("迁移后应含 provider_id 列，实际: $columns", columns.contains("provider_id"))
        assertEquals("provider_id 必须是最后一列（ALTER ADD COLUMN 语义）", "provider_id", columns.last())

        // 存量行不丢、新列回填为空串（未知 → 回放保持旧行为）。
        val row = driver.executeQuery(
            null,
            "SELECT content, signature, provider_id FROM agent_message WHERE id = 'm1'",
            { c ->
                if (c.next().value) {
                    QueryResult.Value(
                        Triple(
                            c.getString(0) ?: "",
                            c.getString(1) ?: "",
                            c.getString(2) ?: "",
                        )
                    )
                } else {
                    QueryResult.Value(Triple("", "", ""))
                }
            },
            0,
            null,
        ).value
        assertEquals("旧回复", row.first)
        assertEquals("sig-legacy", row.second)
        assertEquals("", row.third)
    }
}
