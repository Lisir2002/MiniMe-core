package com.mini.me_core.feature.agent.presentation.component.agentfirst.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.AIEditorTheme
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.component.agentfirst.ToolCallCard

/**
 * ToolCallCard 各状态 @Preview（Stage 1 视觉验收）。
 *
 * 覆盖 Running（蓝色脉冲，流式自动展开）/ Success（绿色）/ Error（红色，自动展开）/
 * TimedOut（橙色，自动展开）/ Cancelled（灰色）。每种状态分别给出折叠态与展开态。
 * 仅用于 Android Studio 预览，不参与生产逻辑。
 */
private fun toolMsg(
    id: String,
    toolName: String = "Bash",
    toolArgs: String = """{"command":"./gradlew assembleDebug"}""",
    content: String,
    isError: Boolean = false
): AgentUIMessage = AgentUIMessage(
    id = id,
    role = MessageRole.TOOL,
    content = content,
    toolName = toolName,
    toolArgs = toolArgs,
    isError = isError
)

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardRunningExpandedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-run", content = ""),
                    liveOutput = RunningToolOutput(
                        messageId = "t-run",
                        text = "> Task :app:compileDebugKotlin\nw: some deprecation note\ne: file.kt:10: Unresolved reference 'foo'\n",
                        toolName = "Bash",
                        toolArgs = """{"command":"./gradlew assembleDebug"}"""
                    ),
                    environmentSnapshot = null,
                    agentState = AgentUIState.Streaming
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardSuccessCollapsedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-ok", content = "BUILD SUCCESSFUL in 2m 35s\n58 actionable tasks: 8 executed, 50 up-to-date"),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = false
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardSuccessExpandedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-ok2", content = "BUILD SUCCESSFUL in 2m 35s\n58 actionable tasks: 8 executed, 50 up-to-date"),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = true
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardErrorExpandedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-err", content = "sh: gradlew: command not found", isError = true),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = true
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardErrorCollapsedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-err2", content = "sh: gradlew: command not found", isError = true),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = false
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardTimedOutExpandedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-timeout", content = "命令执行超时：build did not finish within 300000ms", isError = true),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = true
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardCancelledCollapsedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-cancel", content = SessionUseCase.PENDING_TOOL_MARKER, isError = true),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = false
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun ToolCallCardCancelledExpandedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                ToolCallCard(
                    message = toolMsg("t-cancel2", content = SessionUseCase.PENDING_TOOL_MARKER, isError = true),
                    liveOutput = null,
                    environmentSnapshot = null,
                    agentState = AgentUIState.Idle,
                    initiallyExpanded = true
                )
            }
        }
    }
}
