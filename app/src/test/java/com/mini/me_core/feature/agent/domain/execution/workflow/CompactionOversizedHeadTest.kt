package com.mini.me_core.feature.agent.domain.execution.workflow

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import com.mini.me_core.feature.agent.domain.core.provider.AIProvider
import com.mini.me_core.feature.agent.domain.core.provider.AIResponse
import com.mini.me_core.feature.agent.domain.core.provider.AIStreamChunk
import com.mini.me_core.feature.agent.domain.core.provider.SystemPromptSegment
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

/**
 * 超限会话压缩回归：上下文真正超限后，压缩必须仍然成功，且请求体回归有界。
 *
 * 用户实测现象（本测试对应的线上事故）：某中文对话「上下文满了压缩几次」后，
 * 每次提问都报网络不稳定，新对话正常。
 *
 * 根因链（修复前实测数据，641,440 字中文会话）：
 * 1. token 估算按 chars/4（英文口径），中文低估约 4 倍——压缩触发时真实载荷早已超限；
 * 2. 压缩请求把整个 head 无预算拼进一条 user 消息：实测 prompt 622,705 tokens
 *    （模型窗口 128k 的 4.9 倍）→ API 拒绝；
 * 3. 失败被 catch 后原样返回，且照样 emit CompactionFinished（UI 假报「压缩完成」）；
 * 4. DB 分毫未动，下一轮 buildHistory 回放全量历史 → 每轮请求都超限 →
 *    供应商/网关超时或重置 → 重试风暴 → 用户只看到「网络不稳定」。
 *
 * 修复（2026-10）：CJK-aware 估算 + 滚动窗口逐段摘要（压缩请求永不超限）+
 * 失败显式 emit CompactionFailed + 失败冷却。本测试钉死修复后的契约：
 * A. 压缩成功（多窗合并，每一次调用都在模型窗口内）；
 * B. DB 落定：恰好 1 条生效摘要、head 全部标记 is_compacted；
 * C. 下一轮请求体有界（CJK 口径低于模型窗口）；
 * D. 不再出现「假完成」——失败路径必须 emit CompactionFailed（见另一个用例）。
 */
class CompactionOversizedHeadTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: AgentDb
    private lateinit var repo: AgentRepository
    private lateinit var persistence: MessagePersistenceUseCase
    private lateinit var compactor: ContextCompactor

    private val emittedEvents = mutableListOf<String>()
    private val perCallPromptTokens = mutableListOf<Int>()

    /** 真实 API 口径：中文 1 字 ≈ 1 token；超过输入上限即拒绝（对应 400 context_length_exceeded）。 */
    private inner class RealisticProvider(private val inputTokenLimit: Int = 128_000) : AIProvider {
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
            systemSegments: List<SystemPromptSegment>?
        ): AIResponse {
            val promptText = messages.joinToString("\n") { msg ->
                when (msg) {
                    is AgentMessage.UserMessage -> msg.content
                    is AgentMessage.AssistantMessage -> msg.content
                    is AgentMessage.ToolResultMessage -> msg.result
                }
            }
            val tokens = systemPrompt.length + promptText.length // CJK 口径
            perCallPromptTokens += tokens
            if (tokens > inputTokenLimit) {
                throw IllegalStateException("context_length_exceeded: prompt is too long: $tokens tokens > $inputTokenLimit")
            }
            return AIResponse(content = "## Goal\n- 调试数据库迁移\n## Progress\n- 已完成多轮 readFile 验证\n")
        }

        override fun completeStream(
            systemPrompt: String,
            messages: List<AgentMessage>,
            tools: List<AgentTool>,
            reasoningEffort: String?,
            systemSegments: List<SystemPromptSegment>?
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
        compactor = ContextCompactor(repo, mockk(relaxed = true), CompactionCooldown())
    }

    @After
    fun tearDown() = driver.close()

    private val sessionId = "sess-overflow"

    private fun cn(chars: Int, seed: String): String {
        val unit = "用户在调试安卓应用的数据库迁移，$seed。"
        return unit.repeat((chars / unit.length) + 1).take(chars)
    }

    private fun ensureSession() {
        db.agentQueries.upsertSession(
            sessionId, "超限审计会话", "AGENT", "gpt-test", "active", 1L, 2L, "/ws", "MEDIUM",
            null, 0L, 0L, 0L
        )
    }

    private fun count(sql: String): Long = driver.executeQuery(
        null, sql, { c -> c.next(); QueryResult.Value(c.getLong(0) ?: 0L) }, 0, null
    ).value

    private fun AgentMessage.chars(): Int = when (this) {
        is AgentMessage.UserMessage -> content.length
        is AgentMessage.AssistantMessage -> content.length
        is AgentMessage.ToolResultMessage -> result.length
    }

    /** 灌一个中文长会话：40 回合 ≈ 64 万字（CJK 口径 ≈ 64 万 token，默认窗口 128k 的 5 倍）。 */
    private suspend fun seedLongChineseSession(turns: Int) {
        repeat(turns) { t ->
            val userId = "u-$t"
            val assistantId = "a-$t"
            val tcId = "tc-$t"
            persistence.persist(sessionId, MessageRole.USER, cn(8_000, "第 $t 轮提问"), id = userId)
            persistence.persist(
                sessionId, MessageRole.ASSISTANT, cn(4_000, "第 $t 轮助手分析"),
                id = assistantId,
                toolCalls = listOf(ToolCall(tcId, "readFile", mapOf("path" to JsonPrimitive("/a.kt"))))
            )
            persistence.persist(
                sessionId, MessageRole.TOOL, "{\"status\":\"ok\",\"data\":{\"output\":\"${cn(2_000, "第 $t 轮工具输出")}\"}}",
                id = "tool_$tcId", toolCallId = tcId, toolName = "readFile", toolArgs = "{}"
            )
            persistence.persist(sessionId, MessageRole.ASSISTANT, cn(2_000, "第 $t 轮总结"), id = "af-$t")
        }
    }

    @Test
    fun `超限会话压缩必须成功且每窗都在模型窗口内`() = runBlocking {
        ensureSession()
        seedLongChineseSession(turns = 40)

        val provider = RealisticProvider()
        val history = persistence.buildHistory(sessionId, "[running]")
        val historyChars = history.sumOf { it.chars() }
        println("[超限审计] 历史规模：chars=$historyChars（CJK tokens≈$historyChars，窗口 128000）")

        val compacted = compactor.compactIfNeeded(history, provider, sessionId) { event ->
            emittedEvents += event::class.simpleName.orEmpty()
        }

        // A. 压缩成功：窗口化生效（多次调用），且每一次调用都没被 API 拒绝。
        println(
            "[超限审计] 摘要调用次数=${perCallPromptTokens.size}, " +
                "各次 prompt tokens=$perCallPromptTokens, 事件=$emittedEvents"
        )
        assertTrue("超限 head 必须走滚动窗口（多次调用）", perCallPromptTokens.size > 1)
        perCallPromptTokens.forEach { tokens ->
            assertTrue("压缩请求仍超限被拒：$tokens > 128000", tokens <= 128_000)
        }
        assertTrue("压缩后列表必须变短", compacted.size < history.size)
        assertTrue("不得出现假完成/显式失败", emittedEvents.contains("CompactionFinished") && !emittedEvents.contains("CompactionFailed"))

        // B. DB 落定：恰好 1 条生效摘要；head 全部标记 is_compacted。
        assertEquals("生效摘要必须恰好 1 条", 1L, count("SELECT COUNT(*) FROM agent_message WHERE is_compacted = 0 AND is_context_summary = 1"))
        assertTrue("head 必须被标记压缩", count("SELECT COUNT(*) FROM agent_message WHERE is_compacted = 1") > 0)

        // C. 下一轮请求体有界：CJK 口径远低于模型窗口。
        val nextHistory = persistence.buildHistory(sessionId, "[running]")
        val nextChars = nextHistory.sumOf { it.chars() }
        println("[超限审计] 下一轮请求体（仅消息）：chars=$nextChars")
        assertTrue("下一轮请求体必须有界（实际 $nextChars）", nextChars <= 60_000)
    }

    @Test
    fun `压缩失败必须显式上报且不写坏数据库`() = runBlocking {
        ensureSession()
        seedLongChineseSession(turns = 6)

        // 永远拒绝的供应商（模拟持续网络故障/持续超限）。
        val provider = object : AIProvider {
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
                systemSegments: List<SystemPromptSegment>?
            ): AIResponse = throw java.io.IOException("connection reset")
            override fun completeStream(
                systemPrompt: String,
                messages: List<AgentMessage>,
                tools: List<AgentTool>,
                reasoningEffort: String?,
                systemSegments: List<SystemPromptSegment>?
            ): Flow<AIStreamChunk> = emptyFlow()
        }

        val history = persistence.buildHistory(sessionId, "[running]")
        val compacted = compactor.compactIfNeeded(history, provider, sessionId, force = true) { event ->
            emittedEvents += event::class.simpleName.orEmpty()
        }

        // 原样返回、显式失败、绝不冒充完成。
        assertEquals(history.size, compacted.size)
        assertTrue("必须 emit CompactionFailed：$emittedEvents", emittedEvents.contains("CompactionFailed"))
        assertTrue("失败不得 emit CompactionFinished：$emittedEvents", !emittedEvents.contains("CompactionFinished"))
        // DB 零变化：无摘要行、无压缩标记。
        assertEquals(0L, count("SELECT COUNT(*) FROM agent_message WHERE is_context_summary = 1"))
        assertEquals(0L, count("SELECT COUNT(*) FROM agent_message WHERE is_compacted = 1"))
    }

    @Test
    fun `空摘要按失败处理`() = runBlocking {
        ensureSession()
        seedLongChineseSession(turns = 6)

        val provider = object : AIProvider {
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
                systemSegments: List<SystemPromptSegment>?
            ): AIResponse = AIResponse(content = "   ") // 空白摘要
            override fun completeStream(
                systemPrompt: String,
                messages: List<AgentMessage>,
                tools: List<AgentTool>,
                reasoningEffort: String?,
                systemSegments: List<SystemPromptSegment>?
            ): Flow<AIStreamChunk> = emptyFlow()
        }

        val history = persistence.buildHistory(sessionId, "[running]")
        val compacted = compactor.compactIfNeeded(history, provider, sessionId, force = true) { event ->
            emittedEvents += event::class.simpleName.orEmpty()
        }

        assertEquals(history.size, compacted.size)
        assertTrue("空摘要必须 emit CompactionFailed：$emittedEvents", emittedEvents.contains("CompactionFailed"))
        assertEquals("空摘要不得写库", 0L, count("SELECT COUNT(*) FROM agent_message WHERE is_context_summary = 1"))
    }
}
