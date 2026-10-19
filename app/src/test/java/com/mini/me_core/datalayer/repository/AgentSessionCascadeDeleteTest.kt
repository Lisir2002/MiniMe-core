package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mini.mecore.datalayer.sqldelight.AgentDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 会话级联删除回归：对话列表点击删除不再崩溃。
 *
 * 根因：EncryptedDriverFactory 强制 `PRAGMA foreign_keys=ON`，而 deleteSession 只执行
 * `DELETE FROM agent_session` 单表删除 —— agent_message / agent_checkpoint 外键指向
 * agent_session，agent_message_part / agent_tool_call 外键指向 agent_message，
 * 只要会话有消息或检查点，一删就抛 SQLiteConstraintException 且无人捕获（UI 直接崩溃）。
 *
 * 本测试用同一外键 enforcement 的内存库钉死三件事：
 * 1. 外键强制确实生效（裸删父行必抛，防止测试因 PRAGMA 漏设而假绿）；
 * 2. 修复后的级联删除把会话及其全部关联数据一次删净；
 * 3. 审计/恢复账本（zth_hard_constraint_delete_audits）按设计保留，不随会话删除。
 */
class AgentSessionCascadeDeleteTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: AgentDb
    private lateinit var repo: AgentRepository

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDb.Schema.create(driver)
        // 复刻生产 enforcement：外键强制开启（EncryptedDriverFactory 的 PRAGMA foreign_keys=ON）。
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
        db = AgentDb(driver)
        repo = AgentRepository(db)
    }

    @After
    fun tearDown() = driver.close()

    private fun count(table: String): Long = driver.executeQuery(null, "SELECT COUNT(*) FROM $table", { c ->
        c.next()
        QueryResult.Value(c.getLong(0) ?: 0L)
    }, 0, null).value

    /** 造一个「有消息、有子块、有工具调用、有两类检查点、有 sentinel、有审计」的会话。 */
    private fun seedRichSession(sessionId: String, messageId: String) {
        val q = db.agentQueries
        // 子行 id 按会话派生：同一测试内多次播种不撞主键。
        val partId = "part-$sessionId"
        val toolCallId = "tool-$sessionId"
        val agentCkId = "agent-ck-$sessionId"
        val sessionCkId = "session-ck-$sessionId"
        val snapshotId = "snap-$sessionId"
        val sentinelId = "sentinel-$sessionId"
        val auditId = "audit-$sessionId"
        q.upsertSession(sessionId, "回归会话", "AGENT", "gpt-test", "active", 1L, 2L, "/ws", "MEDIUM", null, 0L, 0L, 0L)
        q.insertMessage(
            messageId, sessionId, "USER", 1L, 10L, "", "你好", null, null, null, null, 0L,
            null, null, null, 0L, 0L, 0L, 0L, 0L, "", 0L, "",
        )
        q.insertMessagePart(partId, messageId, "TEXT", 0L, "你好", null, null, null, null)
        q.insertToolCall(toolCallId, messageId, "read_file", "{}", "{}", "SUCCESS", 11L)
        // agent_checkpoint：外键直接指向 agent_session。
        q.insertCheckpoint(agentCkId, sessionId, "{}", 12L)
        // session_checkpoints + checkpoint_file_snapshots：快照外键指向检查点。
        q.insertCheckpointFull(sessionCkId, sessionId, messageId, "snippet", 13L)
        q.insertCheckpointFileSnapshot(snapshotId, sessionCkId, "/f.txt", "rel/f.txt", "MODIFY", 14L)
        // 会话域用户状态：随会话删除。
        q.insertSentinel(
            sentinelId, sessionId, 1L, "chain-1", 0L, "tpl", "SUB",
            "plan", null, "card", "CONFIRMED", 1L, null, -1L, 0L, 15L,
        )
        // 审计账本：按设计保留（回滚恢复扫描的数据源），断言不随会话删除。
        q.insertDeleteAudit(auditId, sessionId, "agent_message", "keys", "SUB", 0L, 16L)
    }

    @Test
    fun `deleting session with messages and checkpoints does not throw and clears all rows`() = runBlocking {
        seedRichSession("s1", "m1")
        assertEquals("种子数据应就位", 1L, count("agent_session"))
        assertEquals("种子数据应就位", 1L, count("agent_message"))
        assertEquals("种子数据应就位", 1L, count("agent_message_part"))
        assertEquals("种子数据应就位", 1L, count("agent_tool_call"))
        assertEquals("种子数据应就位", 1L, count("agent_checkpoint"))
        assertEquals("种子数据应就位", 1L, count("session_checkpoints"))
        assertEquals("种子数据应就位", 1L, count("checkpoint_file_snapshots"))
        assertEquals("种子数据应就位", 1L, count("zth_user_confirmed_sentinels"))
        assertEquals("种子数据应就位", 1L, count("zth_hard_constraint_delete_audits"))

        // 负向对照：同一把外键 enforcement 下，裸删父行必抛 —— 证明测试环境没有假绿。
        assertThrows("裸 DELETE FROM agent_session 应触发外键约束失败", Exception::class.java) {
            driver.execute(null, "DELETE FROM agent_session WHERE id = 's1'", 0, null)
        }

        repo.deleteSession("s1")

        assertEquals(0L, count("agent_session"))
        assertEquals(0L, count("agent_message"))
        assertEquals(0L, count("agent_message_part"))
        assertEquals(0L, count("agent_tool_call"))
        assertEquals(0L, count("agent_checkpoint"))
        assertEquals(0L, count("session_checkpoints"))
        assertEquals(0L, count("checkpoint_file_snapshots"))
        assertEquals(0L, count("zth_user_confirmed_sentinels"))
        // 审计账本按设计保留：删除会话不销毁回滚恢复的数据源。
        assertEquals("审计账本应按设计保留", 1L, count("zth_hard_constraint_delete_audits"))
    }

    @Test
    fun `deleting childless session still works`() = runBlocking {
        val q = db.agentQueries
        q.upsertSession("s2", "空会话", "AGENT", null, "active", 1L, 2L, "", "MEDIUM", null, 0L, 0L, 0L)

        repo.deleteSession("s2")

        assertEquals(0L, count("agent_session"))
    }

    @Test
    fun `deleting one session leaves other sessions untouched`() = runBlocking {
        seedRichSession("s3", "m3")
        val q = db.agentQueries
        q.upsertSession("s4", "旁观会话", "AGENT", null, "active", 1L, 2L, "", "MEDIUM", null, 0L, 0L, 0L)
        q.insertMessage("m4", "s4", "USER", 1L, 10L, "", "别删我", null, null, null, null, 0L, null, null, null, 0L, 0L, 0L, 0L, 0L, "", 0L, "")

        repo.deleteSession("s3")

        assertEquals(1L, count("agent_session"))
        assertEquals("旁观会话的消息必须保留", 1L, count("agent_message"))
        assertTrue(db.agentQueries.selectSessionById("s4").executeAsOneOrNull() != null)
    }

    @Test
    fun `deleteAllSessions wipes every session foreign-key-safely`() = runBlocking {
        seedRichSession("s5", "m5")
        seedRichSession("s6", "m6")
        val q = db.agentQueries
        q.upsertSession("s7", "空会话", "AGENT", null, "active", 1L, 2L, "", "MEDIUM", null, 0L, 0L, 0L)

        repo.deleteAllSessions()

        assertEquals(0L, count("agent_session"))
        assertEquals(0L, count("agent_message"))
        assertEquals(0L, count("agent_message_part"))
        assertEquals(0L, count("agent_tool_call"))
        assertEquals(0L, count("agent_checkpoint"))
        assertEquals(0L, count("session_checkpoints"))
        assertEquals(0L, count("checkpoint_file_snapshots"))
        assertEquals(0L, count("zth_user_confirmed_sentinels"))
    }
}
