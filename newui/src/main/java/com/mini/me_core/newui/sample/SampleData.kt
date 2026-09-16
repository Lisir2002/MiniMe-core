package com.mini.me_core.newui.sample

import androidx.compose.ui.graphics.Color
import com.mini.me_core.newui.designsystem.component.molecule.AppApprovalChoice
import com.mini.me_core.newui.designsystem.component.molecule.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.molecule.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.molecule.AppMcpAppState
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanState
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanStep
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanStepStatus
import com.mini.me_core.newui.designsystem.component.molecule.AppSkillCallState
import com.mini.me_core.newui.designsystem.component.molecule.AppToolCallState
import com.mini.me_core.newui.designsystem.component.molecule.AppToolChainStep
import com.mini.me_core.newui.designsystem.component.molecule.AppToolChainStepState
import com.mini.me_core.newui.designsystem.component.molecule.AppToolSummaryState
import com.mini.me_core.newui.designsystem.token.generated.AppColor

/** 画廊对话流演示数据：消息 / 标记两类，key 唯一用于 Lazy 键与流式定位。 */
internal sealed interface ChatItem {
    val key: Int

    data class Msg(
        override val key: Int,
        val text: String,
        val state: AppChatMessageState,
        val isUser: Boolean,
        val grouped: Boolean = false,
    ) : ChatItem

    data class Marker(
        override val key: Int,
        val text: String,
        val kind: AppChatMarkerKind = AppChatMarkerKind.Tool,
        val running: Boolean = false,
        val tone: Color = AppColor.StatusSuccess,
    ) : ChatItem

    data class Tool(
        override val key: Int,
        val title: String,
        val state: AppToolCallState,
        val summary: String? = null,
        val serverPrefix: String? = null,
        val durationMs: Long? = null,
        val input: String? = null,
        val output: String? = null,
        val streamOutput: String? = null,
        val approvalHint: String? = null,
        val onApprove: (() -> Unit)? = null,
        val onReject: (() -> Unit)? = null,
        val onChoice: ((AppApprovalChoice) -> Unit)? = null,
        val alwaysDisabled: Boolean = false,
        val alwaysDisabledReason: String? = null,
        val approvalExpired: Boolean = false,
        val approvalRemembered: Boolean = false,
    ) : ChatItem

    data class McpApp(
        override val key: Int,
        val title: String,
        val state: AppMcpAppState,
        val serverPrefix: String? = null,
        val resourceUri: String? = null,
        val onReload: (() -> Unit)? = null,
        val onExpand: (() -> Unit)? = null,
    ) : ChatItem

    data class Skill(
        override val key: Int,
        val name: String,
        val state: AppSkillCallState,
        val args: String? = null,
        val description: String? = null,
        val durationMs: Long? = null,
    ) : ChatItem

    data class Thinking(
        override val key: Int,
        val text: String,
        val isStreaming: Boolean = false,
    ) : ChatItem

    data class Plan(
        override val key: Int,
        val title: String,
        val steps: List<AppPlanStep>,
        val state: AppPlanState,
        val pendingSelection: String? = null,
        val reason: String = null,
        val onApprove: (() -> Unit)? = null,
        val onRefine: (() -> Unit)? = null,
    ) : ChatItem

    data class Attachment(
        override val key: Int,
        val fileName: String,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val containerPath: String? = null,
        val isImage: Boolean = false,
        val onClick: (() -> Unit)? = null,
    ) : ChatItem

    data class ToolChain(
        override val key: Int,
        val steps: List<AppToolChainStep>,
        val label: String = "工具链",
    ) : ChatItem

    data class ToolSummary(
        override val key: Int,
        val text: String,
        val state: AppToolSummaryState = AppToolSummaryState.Done,
        val toolCount: Int = 1,
    ) : ChatItem
}

internal val initialChatItems: List<ChatItem> = listOf(
    ChatItem.Marker(key = -1, text = "今天 · 09:41", kind = AppChatMarkerKind.Date),
    ChatItem.Msg(key = -2, text = "帮我剖析一下项目结构", state = AppChatMessageState.Complete, isUser = true),
    ChatItem.Msg(
        key = -3,
        text = "好的，我扫描了 `app/src/main/java`，**核心模块**如下：\n\n- `agent`：AI Agent 核心（提示词 + 工具 + 多 Provider）\n- `terminal`：终端与会话管理\n- `workspace`：工作区与文档\n\n```kotlin\nval modules = listOf(\"agent\", \"terminal\", \"workspace\")\n```",
        state = AppChatMessageState.Complete,
        isUser = false,
    ),
    ChatItem.Marker(key = -4, text = "扫描代码库 · app/src/main/java"),
)
