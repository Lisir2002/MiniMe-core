package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.feature.agent.domain.core.model.AgentMessage
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1 Observation Masking 单测（批次 D）。
 * 覆盖：窗口外占位符化、窗口内保留、红线（错误/todo/短输出/用户指令）、
 * output_path 取回提示、tool_use 与推理骨架保留、无 mask 时返回原实例。
 */
class ObservationMaskerTest {

    private val masker = ObservationMasker()

    private var callSeq = 0

    private fun assistant(vararg toolNames: String): AgentMessage.AssistantMessage {
        val calls = toolNames.map { name ->
            callSeq++
            ToolCall(id = "call_${name}_$callSeq", name = name, arguments = emptyMap())
        }
        return AgentMessage.AssistantMessage(
            id = "a_${toolNames.joinToString("_")}_$callSeq",
            content = " reasoning skeleton ",
            toolCalls = calls,
            reasoning = "thinking..."
        )
    }

    private fun toolResult(
        toolName: String,
        payload: String,
        id: String = "r_$toolName"
    ): AgentMessage.ToolResultMessage {
        // 与 ToolResult.toTransportString 同构：{"status":"success","data":{...}}
        return AgentMessage.ToolResultMessage(
            id = id,
            toolName = toolName,
            result = """{"status":"success","data":$payload}"""
        )
    }

    private fun bigPayload(size: Int = 600): String = "\"" + "x".repeat(size) + "\""

    /** 构造 N 个完整工具回合（assistant + toolResult），每回合 output 为大 payload。 */
    private fun rounds(count: Int): List<AgentMessage> {
        val list = mutableListOf<AgentMessage>()
        repeat(count) { i ->
            list.add(assistant("Bash"))
            list.add(toolResult("Bash", bigPayload(), id = "r_$i"))
        }
        return list
    }

    @Test
    fun mask_withinWindow_returnsSameInstance() {
        val messages = rounds(10)
        assertSame(messages, masker.mask(messages, keepRecentToolRounds = 10))
    }

    @Test
    fun mask_beyondWindow_oldOutputsPlaceholdered() {
        val messages = rounds(12)
        val masked = masker.mask(messages, keepRecentToolRounds = 10)

        // 前 2 回合被 mask，后 10 回合保留原文
        val results = masked.filterIsInstance<AgentMessage.ToolResultMessage>()
        assertEquals(12, results.size)
        assertTrue(results[0].result.startsWith("[历史输出已归档"))
        assertTrue(results[1].result.startsWith("[历史输出已归档"))
        assertTrue(results[2].result.contains("\"status\":\"success\""))
        assertTrue(results[11].result.contains("\"status\":\"success\""))
    }

    @Test
    fun mask_placeholder_containsSizeHint() {
        val messages = rounds(11)
        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        val first = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertTrue(first.result.contains("约 "))
        assertTrue(first.result.contains(" 字符"))
    }

    @Test
    fun mask_withOutputPath_placeholderKeepsRetrievalPath() {
        val withPath = toolResult("Bash", """{"output":"${"x".repeat(600)}","output_path":"/root/.mini_me_core/tool-output/abc.log","output_truncated":true}""", id = "r_path")
        // withPath 位于第 1 回合（窗口外，keep=10 时共 11 回合）
        val messages = mutableListOf<AgentMessage>()
        messages.add(assistant("Bash"))
        messages.add(withPath)
        messages.addAll(rounds(10))
        val masked = masker.mask(messages, keepRecentToolRounds = 10)

        val firstResult = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertTrue(firstResult.result.contains("输出已归档"))
        assertTrue(firstResult.result.contains("/root/.mini_me_core/tool-output/abc.log"))
        assertTrue(firstResult.result.contains("readFile"))
    }

    @Test
    fun mask_errorResult_neverMasked() {
        val errorResult = AgentMessage.ToolResultMessage(
            id = "r_err",
            toolName = "Bash",
            result = """{"status":"error","message":"${"build failed ".repeat(50)}","code":"BUILD_FAILED"}"""
        )
        val messages = mutableListOf<AgentMessage>()
        messages.add(assistant("Bash"))
        messages.add(errorResult)
        messages.addAll(rounds(10))

        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        val firstResult = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertTrue(firstResult.result.contains("\"status\":\"error\""))
        assertTrue(firstResult.result.contains("BUILD_FAILED"))
    }

    @Test
    fun mask_todoResult_neverMasked() {
        val todoResult = toolResult("todo", bigPayload(), id = "r_todo")
        val messages = mutableListOf<AgentMessage>()
        messages.add(assistant("todo"))
        messages.add(todoResult)
        messages.addAll(rounds(10))

        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        val firstResult = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertTrue(firstResult.result.contains("\"status\":\"success\""))
    }

    @Test
    fun mask_shortOutput_notMasked() {
        val short = toolResult("Bash", "\"ok\"", id = "r_short")
        val messages = mutableListOf<AgentMessage>()
        messages.add(assistant("Bash"))
        messages.add(short)
        messages.addAll(rounds(10))

        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        val firstResult = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertEquals(short.result, firstResult.result)
    }

    @Test
    fun mask_userMessagesNeverTouched() {
        val user = AgentMessage.UserMessage(id = "u1", content = "把构建修好")
        val messages = mutableListOf<AgentMessage>()
        messages.add(user)
        messages.addAll(rounds(12))

        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        assertSame(user, masked.first())
    }

    @Test
    fun mask_assistantActionsAndReasoningPreserved() {
        val messages = rounds(12)
        val masked = masker.mask(messages, keepRecentToolRounds = 10)

        val assistants = masked.filterIsInstance<AgentMessage.AssistantMessage>()
        // tool_use 动作与推理骨架原样保留（含旧回合的 assistant 消息）
        assertTrue(assistants.all { it.toolCalls.isNotEmpty() })
        assertTrue(assistants.all { it.reasoning == "thinking..." })
        // 每个 assistant 的 toolCall id 与原始消息逐一对应（配对关系未被 masking 破坏）
        val originalCalls = messages.filterIsInstance<AgentMessage.AssistantMessage>().flatMap { it.toolCalls.map { c -> c.id } }
        val maskedCalls = assistants.flatMap { it.toolCalls.map { c -> c.id } }
        assertEquals(originalCalls, maskedCalls)
    }

    @Test
    fun mask_customWindow_respectsParameter() {
        val messages = rounds(5)
        // 保留 2 回合 → 前 3 回合 mask
        val masked = masker.mask(messages, keepRecentToolRounds = 2)
        val results = masked.filterIsInstance<AgentMessage.ToolResultMessage>()
        assertTrue(results[0].result.startsWith("[历史输出已归档"))
        assertTrue(results[2].result.startsWith("[历史输出已归档"))
        assertTrue(results[3].result.contains("\"status\":\"success\""))
        assertTrue(results[4].result.contains("\"status\":\"success\""))
    }

    @Test
    fun mask_emptyList_returnsEmpty() {
        assertTrue(masker.mask(emptyList()).isEmpty())
    }

    @Test
    fun mask_malformedResultJson_fallsBackToGenericPlaceholder() {
        // 非 JSON 的历史输出（旧数据/异常路径）：仍按长度归档，不当错误保留
        val malformed = AgentMessage.ToolResultMessage(id = "r_bad", toolName = "Bash", result = "x".repeat(600))
        val messages = mutableListOf<AgentMessage>()
        messages.add(assistant("Bash"))
        messages.add(malformed)
        messages.addAll(rounds(10))

        val masked = masker.mask(messages, keepRecentToolRounds = 10)
        val firstResult = masked.filterIsInstance<AgentMessage.ToolResultMessage>().first()
        assertTrue(firstResult.result.startsWith("[历史输出已归档"))
    }
}
