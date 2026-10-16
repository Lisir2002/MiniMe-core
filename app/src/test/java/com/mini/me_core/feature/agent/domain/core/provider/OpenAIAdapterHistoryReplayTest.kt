package com.mini.me_core.feature.agent.domain.core.provider

import com.mini.me_core.feature.agent.data.remote.openai.OpenAIApi
import com.mini.me_core.feature.agent.data.remote.openai.OpenAIChatMessage
import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回归：「对话中切换模型后发消息必失败」——buildHistory 原样回放旧供应商的协议私有产物。
 *
 * 场景：用户在对话中途从 DeepSeek/Claude/Gemini 切到非 deepseek 的 OpenAI 兼容模型，
 * 历史 reasoning 被原样塞进 reasoning_content，严格上游（vLLM 等）直接 400；
 * 新对话无历史所以正常。同时覆盖跨协议 tool_call_id 规范化（Gemini 的 id = 函数名）。
 */
class OpenAIAdapterHistoryReplayTest {

    private fun adapter(model: String): OpenAIAdapter =
        OpenAIAdapter(api = mockk(relaxed = true), okHttpClient = OkHttpClient()).also { it.model = model }

    private fun assistant(reasoning: String, toolCalls: List<ToolCall> = emptyList()) =
        AgentMessage.AssistantMessage(id = "a1", content = "回复", toolCalls = toolCalls, reasoning = reasoning)

    private fun toolCall(id: String, name: String) =
        ToolCall(id = id, name = name, arguments = mapOf("path" to JsonPrimitive("/a.kt")))

    @Test
    fun nonDeepseekModel_doesNotReplayReasoningContent() {
        // 切换模型根因：qwen 等非 deepseek 模型收到历史 reasoning_content → 400
        val result = adapter("qwen-max").convertToOpenAIMessages(listOf(assistant(reasoning = "思考过程")), false)

        assertNull(result.single().reasoning_content)
    }

    @Test
    fun deepseekModel_replaysReasoningContentAsIs() {
        val result = adapter("deepseek-chat").convertToOpenAIMessages(listOf(assistant(reasoning = "思考过程")), false)

        assertEquals("思考过程", result.single().reasoning_content)
    }

    @Test
    fun deepseekModel_replaysEmptyReasoningAsEmptyString() {
        // DeepSeek 思考模式要求字段存在（空串也要带），否则工具轮 400
        val result = adapter("deepseek-reasoner").convertToOpenAIMessages(listOf(assistant(reasoning = "")), false)

        assertEquals("", result.single().reasoning_content)
    }

    @Test
    fun toolCallIds_sanitizedAndKeptPairedAcrossProtocols() {
        // Gemini 时代的 tool_call id = 函数名（含下划线），原样回放可能被严格上游拒绝
        val messages = listOf(
            assistant(reasoning = "", toolCalls = listOf(toolCall("read_file", "read_file"))),
            AgentMessage.ToolResultMessage(id = "read_file", toolName = "read_file", result = "文件内容")
        )

        val result = adapter("gpt-4o").convertToOpenAIMessages(messages, false)

        val assistantMsg = result[0] as OpenAIChatMessage
        val toolMsg = result[1] as OpenAIChatMessage
        assertEquals("readfile", assistantMsg.tool_calls?.single()?.id)
        // 配对一致性：tool 侧 id 必须与 assistant 侧 sanitize 结果相同，否则 400
        assertEquals("readfile", toolMsg.tool_call_id)
    }

    @Test
    fun toolCallIds_sanitizeIsIdempotentForWellFormedIds() {
        // OpenAI 自身产生的 "call_abc123"：过滤后仍配对一致，同协议回放不受影响
        val messages = listOf(
            assistant(reasoning = "", toolCalls = listOf(toolCall("call_abc123", "f"))),
            AgentMessage.ToolResultMessage(id = "call_abc123", toolName = "f", result = "ok")
        )

        val result = adapter("gpt-4o").convertToOpenAIMessages(messages, false)

        assertEquals("callabc123", (result[0] as OpenAIChatMessage).tool_calls?.single()?.id)
        assertEquals("callabc123", (result[1] as OpenAIChatMessage).tool_call_id)
    }
}
