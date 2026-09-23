package com.mini.me_core.feature.agent.presentation.component.agentfirst.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.AIEditorTheme
import com.mini.me_core.feature.agent.domain.tool.PendingToolPermission
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.RunningToolOutput
import com.mini.me_core.feature.agent.presentation.TaskGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroup
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType
import com.mini.me_core.feature.agent.presentation.component.agentfirst.TaskCard

/**
 * TaskCard 各状态 @Preview（Stage 2 视觉验收）。
 *
 * 覆盖 Running（蓝色脉冲 + 进度条 + 步骤列表）/ Completed（绿色折叠）/
 * Failed（红色）/ WaitingApproval（橙色）。
 * 仅用于 Android Studio 预览，不参与生产逻辑。
 */

// —— 测试数据构建辅助 ——

private fun userMsg(id: String, text: String) = AgentUIMessage(
    id = id, role = MessageRole.USER, content = text
)

private fun toolMsg(
    id: String,
    toolName: String = "Bash",
    command: String = "./gradlew assembleDebug",
    content: String = "BUILD SUCCESSFUL",
    isError: Boolean = false
) = AgentUIMessage(
    id = id,
    role = MessageRole.TOOL,
    content = content,
    toolName = toolName,
    toolArgs = """{"command":"$command"}""",
    isError = isError
)

private fun replyMsg(id: String, text: String) = AgentUIMessage(
    id = id, role = MessageRole.ASSISTANT, content = text
)

private fun buildGroup(
    taskId: String,
    title: String,
    isExpanded: Boolean,
    isStreaming: Boolean = false,
    subGroups: List<TaskSubGroup>
) = TaskGroup(
    taskId = taskId,
    title = title,
    timestamp = System.currentTimeMillis(),
    subGroups = subGroups,
    isExpanded = isExpanded,
    isStreaming = isStreaming
)

// —— Preview 1：Running（展开，蓝色脉冲 + 步骤列表）——

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun TaskCardRunningPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                val runningTool = RunningToolOutput(
                    messageId = "tool-1",
                    text = "> Task :app:compileDebugKotlin\nw: deprecation note\ne: file.kt:10: Unresolved reference\n",
                    toolName = "Bash",
                    toolArgs = """{"command":"./gradlew assembleDebug"}"""
                )
                TaskCard(
                    group = buildGroup(
                        taskId = "task-run-1",
                        title = "编译并构建 Debug 包",
                        isExpanded = true,
                        isStreaming = false,
                        subGroups = listOf(
                            TaskSubGroup("task-run-1-0", TaskSubGroupType.USER, listOf(
                                userMsg("user-1", "帮我编译并构建 Debug 包")
                            )),
                            TaskSubGroup("task-run-1-1", TaskSubGroupType.TOOL, listOf(
                                toolMsg("tool-1", content = "正在编译...")
                            ), isExpanded = false)
                        )
                    ),
                    agentState = AgentUIState.Streaming,
                    runningTools = listOf(runningTool),
                    environmentSnapshots = emptyMap(),
                    onToggleTask = {},
                    onToggleSubGroup = { _, _ -> }
                )
            }
        }
    }
}

// —— Preview 2：Completed（折叠，绿色）——

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun TaskCardCompletedCollapsedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                val editContent = "{\"path\":\"LoginScreen.kt\",\"added_lines\":3,\"removed_lines\":1,\"hunks\":[{\"start_line\":42,\"diff\":\"+ buttonColor = Blue\"}]}"
                TaskCard(
                    group = buildGroup(
                        taskId = "task-done-1",
                        title = "修复登录页面样式问题",
                        isExpanded = false,
                        subGroups = listOf(
                            TaskSubGroup("task-done-1-0", TaskSubGroupType.USER, listOf(
                                userMsg("user-2", "修复登录页面按钮颜色不对的问题")
                            )),
                            TaskSubGroup("task-done-1-1", TaskSubGroupType.TOOL, listOf(
                                toolMsg("tool-2", toolName = "editFile", command = "", content = editContent)
                            )),
                            TaskSubGroup("task-done-1-2", TaskSubGroupType.REPLY, listOf(
                                replyMsg("reply-1", "已修复登录页面按钮颜色，改为品牌蓝色。")
                            ))
                        )
                    ),
                    agentState = AgentUIState.Idle,
                    runningTools = emptyList(),
                    environmentSnapshots = emptyMap(),
                    onToggleTask = {},
                    onToggleSubGroup = { _, _ -> }
                )
            }
        }
    }
}

// —— Preview 3：Failed（展开，红色）——

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun TaskCardFailedPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                TaskCard(
                    group = buildGroup(
                        taskId = "task-fail-1",
                        title = "运行单元测试",
                        isExpanded = true,
                        subGroups = listOf(
                            TaskSubGroup("task-fail-1-0", TaskSubGroupType.USER, listOf(
                                userMsg("user-3", "运行一下单元测试看看")
                            )),
                            TaskSubGroup("task-fail-1-1", TaskSubGroupType.TOOL, listOf(
                                toolMsg(
                                    id = "tool-3",
                                    command = "./gradlew test",
                                    content = "> Task :app:testDebugUnitTest FAILED\ncom.example.LoginTest > testLogin FAILED\n  AssertionError: expected:<true> but was:<false>",
                                    isError = true
                                )
                            ))
                        )
                    ),
                    agentState = AgentUIState.Idle,
                    runningTools = emptyList(),
                    environmentSnapshots = emptyMap(),
                    onToggleTask = {},
                    onToggleSubGroup = { _, _ -> }
                )
            }
        }
    }
}

// —— Preview 4：WaitingApproval（展开，橙色）——

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun TaskCardWaitingApprovalPreview() {
    AIEditorTheme {
        Surface {
            Column(Modifier.padding(com.mini.me_core.core.theme.tokens.PrimitiveSpacing.Sm)) {
                val approvalArgs = "{\"command\":\"npm install axios\"}"
                TaskCard(
                    group = buildGroup(
                        taskId = "task-approval-1",
                        title = "安装项目依赖",
                        isExpanded = true,
                        subGroups = listOf(
                            TaskSubGroup("task-approval-1-0", TaskSubGroupType.USER, listOf(
                                userMsg("user-4", "安装项目所需的依赖包")
                            )),
                            TaskSubGroup("task-approval-1-1", TaskSubGroupType.TOOL, listOf(
                                toolMsg("tool-4", command = "npm install axios")
                            ))
                        )
                    ),
                    agentState = AgentUIState.Idle,
                    runningTools = emptyList(),
                    environmentSnapshots = emptyMap(),
                    pendingPermission = PendingToolPermission(
                        id = "perm-1",
                        toolName = "Bash",
                        title = "执行安装命令",
                        summary = "npm install axios",
                        details = "即将在项目目录执行 npm install axios",
                        argsPreview = approvalArgs
                    ),
                    onToggleTask = {},
                    onToggleSubGroup = { _, _ -> },
                    onViewChanges = {}
                )
            }
        }
    }
}
