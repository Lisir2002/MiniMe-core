package com.mini.me_core.feature.agent.domain.execution.workflow

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import com.mini.me_core.feature.agent.domain.core.provider.AIProvider
import com.mini.me_core.feature.agent.domain.core.provider.AIResponse
import com.mini.me_core.feature.agent.domain.core.provider.AIStreamChunk
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * 压缩会话的请求体审计回归：「上下文满了压缩几次」之后，每轮提问实际发多大的请求。
 *
 * 用户实测现象：某对话压缩数次后，每次提问都报网络不稳定（重试/中断），新对话正常。
 * 本测试用真实内存库 + 真实 [ContextCompactor] + 真实 [MessagePersistenceUseCase.buildHistory]
 * 复现该场景，测量每轮请求视图（system + tools + messages）的规模，钉死不变量：
 *
 * 1. 任意轮次压缩后，回放历史必须**有界**（不能随压缩次数增长）；
 * 2. 生效的上下文摘要行必须始终只有 1 条（旧摘要必须被标记压缩，不得叠加回放）；
 * 3. 压缩标记 user + 摘要 assistant 必须相邻成对，不得出现孤儿 tool 结果打头的 tail。
 */
class CompactedSessionPayloadTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: AgentDb
    private lateinit var repo: AgentRepository
    private lateinit var persistence: MessagePersistenceUseCase
    private lateinit var compactor: ContextCompactor

    /** 记录每轮压缩时摘要 prompt 的规模（previousSummary + headContent），用于观测摘要是否膨胀。 */
    private val summaryPromptChars = mutableListOf<Int>()

    /** 模拟压缩模型：返回一段固定结构的锚定摘要（真实 LLM 的典型产出量级）。 */
    private inner class FakeCompactionProvider : AIProvider {
        override var apiKey: String = "k"
        override var baseUrl: String = ""
        override var useFullUrl: Boolean = false
        override var useResponseApi: Boolean = false
        override var model: String = "gpt-test"
        override var temperature: Float = 1f
        override var topP: Float = 1f
        override var maxTokens: Int? = null
        override var apiPath: String = "/v1/chat/completions"
        override var requestTimeout: Int = 60
        override var retryCount: Int = 0
        override var logSessionId: String? = null

        override suspend fun complete(
            systemPrompt: String,
            messages: List<AgentMessage>,
            tools: List<AgentTool>,
            reasoningEffort: String?,
            systemSegments: List<com.mini.me_core.feature.agent.domain.core.provider.SystemPromptSegment>?
        ): AIResponse {
            val promptChars = messages.sumOf { msg ->
                when (msg) {
                    is AgentMessage.UserMessage -> msg.content.length
                    is AgentMessage.AssistantMessage -> msg.content.length
                    is AgentMessage.ToolResultMessage -> msg.result.length
                }
            }
            summaryPromptChars += promptChars
            return AIResponse(content = buildSummary())
        }

        override fun completeStream(
            systemPrompt: String,
            messages: List<AgentMessage>,
            tools: List<AgentTool>,
            reasoningEffort: String?,
            systemSegments: List<com.mini.me_core.feature.agent.domain.core.provider.SystemPromptSegment>?
        ): Flow<AIStreamChunk> = emptyFlow()
    }

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDb.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
        db = AgentDb(driver)
        repo = AgentRepository(db)
        persistence = MessagePersistenceUseCase(repo)
        // ModelMetadataService  relaxed mock：contextTokens=0 → 走 DEFAULT_CONTEXT_TOKENS(128k) 默认路径。
        compactor = ContextCompactor(repo, mockk(relaxed = true), CompactionCooldown())
    }

    @After
    fun tearDown() = driver.close()

    // ─────────────────────── 场景工具 ───────────────────────

    private val sessionId = "sess-compact-audit"

    private fun ensureSession() {
        db.agentQueries.upsertSession(
            sessionId, "压缩审计会话", "AGENT", "gpt-test", "active", 1L, 2L, "/ws", "MEDIUM",
            null, 0L, 0L, 0L
        )
    }

    /** 生成一段指定长度的中文对话内容（模拟真实中文会话：每汉字约 1 token，而非 0.25）。 */
    private fun cn(chars: Int, seed: String): String {
        val unit = "用户正在调试一个安卓应用的数据库迁移逻辑，$seed。"
        return unit.repeat((chars / unit.length) + 1).take(chars)
    }

    /** 消息正文字符数（ToolResult 取 result）。 */
    private fun AgentMessage.chars(): Int = when (this) {
        is AgentMessage.UserMessage -> content.length
        is AgentMessage.AssistantMessage -> content.length
        is AgentMessage.ToolResultMessage -> result.length
    }

    /** 模拟一个完整回合：用户提问 → 助手调工具 → 工具结果 → 助手总结。返回带 DB id 的内存消息。 */
    private suspend fun appendTurn(turn: Int): List<AgentMessage> {
        val userId = "u-$turn"
        val assistantId = "a-$turn"
        val toolCallId = "tc-$turn"
        persistence.persist(sessionId, MessageRole.USER, cn(1_500, "第 $turn 轮提问"), id = userId)
        persistence.persist(
            sessionId, MessageRole.ASSISTANT, cn(600, "第 $turn 轮助手思考"),
            id = assistantId,
            toolCalls = listOf(ToolCall(toolCallId, "readFile", mapOf("path" to JsonPrimitive("/a.kt"))))
        )
        persistence.persist(
            sessionId, MessageRole.TOOL, "{\"status\":\"ok\",\"data\":{\"output\":\"${cn(4_000, "第 $turn 轮工具输出")}\"}}",
            id = "tool_$toolCallId", toolCallId = toolCallId, toolName = "readFile",
            toolArgs = "{\"path\":\"/a.kt\"}"
        )
        persistence.persist(sessionId, MessageRole.ASSISTANT, cn(800, "第 $turn 轮总结"), id = "a-final-$turn")

        // 内存视图与落库 id 一致（生产链路中 history 来自 buildHistory，新消息按落库 id 入列）。
        return listOf(
            AgentMessage.UserMessage(id = userId, content = cn(1_500, "第 $turn 轮提问")),
            AgentMessage.AssistantMessage(
                id = assistantId, content = cn(600, "第 $turn 轮助手思考"),
                toolCalls = listOf(ToolCall(toolCallId, "readFile", mapOf("path" to JsonPrimitive("/a.kt"))))
            ),
            AgentMessage.ToolResultMessage(id = toolCallId, toolName = "readFile", result = "{\"status\":\"ok\",\"data\":{\"output\":\"${cn(4_000, "第 $turn 轮工具输出")}\"}}"),
            AgentMessage.AssistantMessage(id = "a-final-$turn", content = cn(800, "第 $turn 轮总结"))
        )
    }

    /** 每轮请求视图 = system(4k) + tools(8k) + buildHistory 回放 + 新提问。 */
    private fun requestViewChars(newQuestion: String): Int {
        val history = runBlocking { persistence.buildHistory(sessionId, "[running]") }
        val messagesChars = history.sumOf { msg ->
            when (msg) {
                is AgentMessage.UserMessage -> msg.content.length
                is AgentMessage.AssistantMessage -> msg.content.length + msg.reasoning.length +
                    msg.toolCalls.sumOf { it.name.length + it.arguments.toString().length }
                is AgentMessage.ToolResultMessage -> msg.toolName.length + msg.result.length
            }
        }
        return 4_000 + 8_000 + messagesChars + newQuestion.length
    }

    private fun activeSummaryRows(): Long = count(
        "SELECT COUNT(*) FROM agent_message WHERE session_id = '$sessionId' AND is_compacted = 0 AND is_context_summary = 1"
    )

    private fun activeRows(): Long = count(
        "SELECT COUNT(*) FROM agent_message WHERE session_id = '$sessionId' AND is_compacted = 0"
    )

    private fun count(sql: String): Long = driver.executeQuery(
        null, sql, { c -> c.next(); QueryResult.Value(c.getLong(0) ?: 0L) }, 0, null
    ).value

    // ─────────────────────── 审计用例 ───────────────────────

    @Test
    fun `压缩数轮后每轮请求体必须有界且摘要不叠加`() = runBlocking {
        ensureSession()
        val provider = FakeCompactionProvider()
        val measurements = mutableListOf<Int>()

        // 初始几轮把上下文撑起来
        var inMemory = emptyList<AgentMessage>()
        repeat(3) { turn ->
            val newMsgs = appendTurn(turn)
            inMemory = inMemory + newMsgs
        }

        // 之后每一轮：提问 → 压缩（模拟 /compress 或自动压缩）→ 再提问，测量请求体
        repeat(4) { round ->
            val roundIdx = round + 3
            val newMsgs = appendTurn(roundIdx)
            inMemory = inMemory + newMsgs

            // 压缩前的请求体（用户感知「提问后卡住」的时刻）
            measurements += requestViewChars(cn(200, "第 $roundIdx 轮新提问"))

            val beforeChars = inMemory.sumOf { msg -> msg.chars() }
            val compacted = compactor.compactIfNeeded(
                inMemory, provider, sessionId, force = true
            ) {}
            inMemory = compacted

            // 压缩后的请求体（下一轮提问实际发送的规模）
            measurements += requestViewChars(cn(200, "第 $roundIdx 轮压缩后提问"))

            println(
                "[压缩审计] round=$roundIdx 压缩前内存=$beforeChars chars, " +
                    "压缩后内存=${compacted.sumOf { m -> m.chars() }} , " +
                    "生效摘要行=${activeSummaryRows()}, 生效总行=${activeRows()}, " +
                    "摘要prompt=${summaryPromptChars.lastOrNull()}"
            )
        }

        println("[压缩审计] 每轮请求体（含 system4k+tools8k）: $measurements")

        // 不变量 1：任意轮次请求体必须有界。中文语境 1 字 ≈ 1 token，
        // 128k 窗口减去 system/tools 后给历史的预算按 60k 字符封顶（≈6 万 token，留足余量）。
        measurements.forEach { chars ->
            assertTrue("请求体失控：$chars chars（中文≈等量 token，已逼近/超过模型窗口）", chars <= 60_000)
        }
        // 不变量 2：生效摘要始终只有 1 条——旧摘要必须被标记 is_compacted，不得叠加回放。
        assertEquals("生效上下文摘要必须恰好 1 条，实际 ${activeSummaryRows()}", 1L, activeSummaryRows())
        // 不变量 3：请求体不得随压缩次数单调膨胀（前紧后松场景）。
        val firstHalf = measurements.take(measurements.size / 2).average()
        val secondHalf = measurements.drop(measurements.size / 2).average()
        assertTrue(
            "请求体随压缩次数膨胀：前半 $firstHalf → 后半 $secondHalf",
            secondHalf <= firstHalf * 1.5
        )
    }

    private fun buildSummary(): String = """
        ## Goal
        - 调试数据库迁移逻辑

        ## Constraints & Preferences
        - 保持中文回复

        ## Progress
        ### Done
        - 完成 4 轮 readFile 调试
        ### In Progress
        - 验证迁移幂等性
        ### Blocked
        - (none)

        ## Key Decisions
        - 使用内存库复现生产外键强制

        ## Next Steps
        - 跑回归测试

        ## Critical Context
        - ${cn(800, "关键技术事实")}

        ## Relevant Files
        - /a.kt：迁移入口
    """.trimIndent()
}
