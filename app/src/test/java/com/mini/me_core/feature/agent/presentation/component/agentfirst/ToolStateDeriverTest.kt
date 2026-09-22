package com.mini.me_core.feature.agent.presentation.component.agentfirst.derivers

import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolStateDeriverTest {

    private fun toolMessage(
        id: String = "tool_1",
        content: String = "output",
        isError: Boolean = false,
        toolName: String = "Bash"
    ) = AgentUIMessage(
        id = id,
        role = MessageRole.TOOL,
        content = content,
        toolName = toolName,
        toolArgs = """{"command":"ls"}""",
        isError = isError
    )

    // ===== RUNNING =====

    @Test
    fun liveOutputNotNull_returnsRUNNING() {
        val msg = toolMessage(content = "[running] Bash")
        val live = RunningToolOutput(messageId = "tool_1", text = "partial output", toolName = "Bash")
        assertEquals(ToolCallState.RUNNING, ToolStateDeriver.derive(msg, live, AgentUIState.Idle))
    }

    @Test
    fun liveOutputNotNull_evenIfIsError_returnsRUNNING() {
        // 运行中优先，即使消息标记为 error（不应发生，但优先级明确）
        val msg = toolMessage(content = "output", isError = true)
        val live = RunningToolOutput(messageId = "tool_1", text = "running", toolName = "Bash")
        assertEquals(ToolCallState.RUNNING, ToolStateDeriver.derive(msg, live, AgentUIState.Idle))
    }

    // ===== CANCELLED (冷启动异常终止) =====

    @Test
    fun pendingMarkerWithNoLiveOutputAndIdle_returnsCANCELLED() {
        val msg = toolMessage(content = "${SessionUseCase.PENDING_TOOL_MARKER} Bash")
        assertEquals(ToolCallState.CANCELLED, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun legacyPendingMarkerWithNoLiveOutputAndIdle_returnsCANCELLED() {
        val msg = toolMessage(content = "${SessionUseCase.LEGACY_PENDING_TOOL_MARKER} Bash")
        assertEquals(ToolCallState.CANCELLED, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun pendingMarkerWithStreamingState_returnsRUNNING() {
        // isStreaming 时 pending marker 是正常的运行中状态
        val msg = toolMessage(content = "${SessionUseCase.PENDING_TOOL_MARKER} Bash")
        val live = RunningToolOutput(messageId = "tool_1", text = "running", toolName = "Bash")
        assertEquals(ToolCallState.RUNNING, ToolStateDeriver.derive(msg, live, AgentUIState.Streaming))
    }

    // ===== TIMED_OUT =====

    @Test
    fun isErrorWithTimeoutText_returnsTIMED_OUT() {
        val msg = toolMessage(content = "命令执行超时（超过 60 秒已强制终止）。末尾输出...", isError = true)
        assertEquals(ToolCallState.TIMED_OUT, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun isErrorWithTimedOutEnglish_returnsTIMED_OUT() {
        val msg = toolMessage(content = "Command execution timed out", isError = true)
        assertEquals(ToolCallState.TIMED_OUT, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun isErrorWithTOOL_TIMEOUT_returnsTIMED_OUT() {
        val msg = toolMessage(content = "Error: TOOL_TIMEOUT", isError = true)
        assertEquals(ToolCallState.TIMED_OUT, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    // ===== CANCELLED (用户中断) =====

    @Test
    fun isErrorWithStoppedText_returnsCANCELLED() {
        val msg = toolMessage(content = "已停止 agent_stopped_by_user", isError = true)
        assertEquals(ToolCallState.CANCELLED, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun isErrorWithStoppedByUserEnglish_returnsCANCELLED() {
        val msg = toolMessage(content = "Stopped by user", isError = true)
        assertEquals(ToolCallState.CANCELLED, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    // ===== ERROR =====

    @Test
    fun isErrorWithoutSpecialMarkers_returnsERROR() {
        val msg = toolMessage(content = "Some random error occurred", isError = true)
        assertEquals(ToolCallState.ERROR, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun isErrorWithPermissionDenied_returnsERROR() {
        val msg = toolMessage(content = "Permission denied", isError = true)
        assertEquals(ToolCallState.ERROR, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    // ===== SUCCESS =====

    @Test
    fun noErrorNoLiveOutput_returnsSUCCESS() {
        val msg = toolMessage(content = "Build successful")
        assertEquals(ToolCallState.SUCCESS, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    @Test
    fun emptyContentNoError_returnsSUCCESS() {
        val msg = toolMessage(content = "")
        assertEquals(ToolCallState.SUCCESS, ToolStateDeriver.derive(msg, null, AgentUIState.Idle))
    }

    // ===== containsTimeoutMarker =====

    @Test
    fun containsTimeoutMarker_chineseTimeout_returnsTrue() {
        assertTrue(ToolStateDeriver.containsTimeoutMarker("命令执行超时"))
    }

    @Test
    fun containsTimeoutMarker_forcedTermination_returnsTrue() {
        assertTrue(ToolStateDeriver.containsTimeoutMarker("已强制终止"))
    }

    @Test
    fun containsTimeoutMarker_englishTimedOut_returnsTrue() {
        assertTrue(ToolStateDeriver.containsTimeoutMarker("command timed out"))
    }

    @Test
    fun containsTimeoutMarker_normalOutput_returnsFalse() {
        assertFalse(ToolStateDeriver.containsTimeoutMarker("Build successful in 10s"))
    }

    @Test
    fun containsTimeoutMarker_blank_returnsFalse() {
        assertFalse(ToolStateDeriver.containsTimeoutMarker(""))
    }

    // ===== containsCancelledMarker =====

    @Test
    fun containsCancelledMarker_stopped_returnsTrue() {
        assertTrue(ToolStateDeriver.containsCancelledMarker("已停止"))
    }

    @Test
    fun containsCancelledMarker_agentStopped_returnsTrue() {
        assertTrue(ToolStateDeriver.containsCancelledMarker("agent_stopped_by_user"))
    }

    @Test
    fun containsCancelledMarker_normalOutput_returnsFalse() {
        assertFalse(ToolStateDeriver.containsCancelledMarker("Build successful"))
    }

    @Test
    fun containsCancelledMarker_blank_returnsFalse() {
        assertFalse(ToolStateDeriver.containsCancelledMarker(""))
    }
}
