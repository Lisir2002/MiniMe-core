package com.mini.me_core.newui.sample

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import com.mini.me_core.newui.designsystem.component.atom.AppCard
import com.mini.me_core.newui.designsystem.component.atom.AppChip
import com.mini.me_core.newui.designsystem.component.atom.AppIcon
import com.mini.me_core.newui.designsystem.component.atom.IconContainer
import com.mini.me_core.newui.designsystem.component.molecule.AppAccordion
import com.mini.me_core.newui.designsystem.component.molecule.AppAlertTone
import com.mini.me_core.newui.designsystem.component.molecule.AppAvatar
import com.mini.me_core.newui.designsystem.component.molecule.AppBadge
import com.mini.me_core.newui.designsystem.component.molecule.AppBadgeDot
import com.mini.me_core.newui.designsystem.component.molecule.AppBreadcrumb
import com.mini.me_core.newui.designsystem.component.molecule.AppCrumb
import com.mini.me_core.newui.designsystem.component.molecule.AppButton
import com.mini.me_core.newui.designsystem.component.molecule.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.molecule.AppChatBubble
import com.mini.me_core.newui.designsystem.component.molecule.AppChatMarker
import com.mini.me_core.newui.designsystem.component.molecule.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.molecule.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.molecule.AppMarkdownText
import com.mini.me_core.newui.designsystem.component.molecule.AppMarquee
import com.mini.me_core.newui.designsystem.component.molecule.AppMessageRow
import com.mini.me_core.newui.designsystem.component.molecule.AppMcpAppCard
import com.mini.me_core.newui.designsystem.component.molecule.AppMcpAppState
import com.mini.me_core.newui.designsystem.component.molecule.AppSkillCallCard
import com.mini.me_core.newui.designsystem.component.molecule.AppSkillCallState
import com.mini.me_core.newui.designsystem.component.molecule.AppToolCallCard
import com.mini.me_core.newui.designsystem.component.molecule.AppToolCallState
import com.mini.me_core.newui.designsystem.component.molecule.AppApprovalChoice
import com.mini.me_core.newui.designsystem.component.molecule.AppThinkingBlock
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanCard
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanState
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanStep
import com.mini.me_core.newui.designsystem.component.molecule.AppPlanStepStatus
import com.mini.me_core.newui.designsystem.component.molecule.AppAttachmentCard
import com.mini.me_core.newui.designsystem.component.molecule.AppToolChainTimeline
import com.mini.me_core.newui.designsystem.component.molecule.AppToolChainStep
import com.mini.me_core.newui.designsystem.component.molecule.AppToolChainStepState
import com.mini.me_core.newui.designsystem.component.molecule.AppToolSummaryCard
import com.mini.me_core.newui.designsystem.component.molecule.AppToolSummaryState
import com.mini.me_core.newui.designsystem.component.molecule.AppMessageScroller
import com.mini.me_core.newui.designsystem.component.molecule.AppCheckRow
import com.mini.me_core.newui.designsystem.component.molecule.AppConfetti
import com.mini.me_core.newui.designsystem.component.molecule.AppIconButton
import com.mini.me_core.newui.designsystem.component.molecule.AppDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppDialogTone
import com.mini.me_core.newui.designsystem.component.molecule.AppDivider
import com.mini.me_core.newui.designsystem.component.molecule.AppActionSheet
import com.mini.me_core.newui.designsystem.component.molecule.AppActionSheetItem
import com.mini.me_core.newui.designsystem.component.molecule.AppAlertDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppBottomSheetList
import com.mini.me_core.newui.designsystem.component.molecule.AppConfirmDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppMenu
import com.mini.me_core.newui.designsystem.component.molecule.AppMenuDivider
import com.mini.me_core.newui.designsystem.component.molecule.AppMenuItem
import com.mini.me_core.newui.designsystem.component.molecule.AppPromptDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppSelectionDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppSelectionItem
import com.mini.me_core.newui.designsystem.component.molecule.AppSelectionList
import com.mini.me_core.newui.designsystem.component.molecule.AppSelectionMode
import com.mini.me_core.newui.designsystem.component.molecule.AppSelectField
import com.mini.me_core.newui.designsystem.component.molecule.AppComboBox
import com.mini.me_core.newui.designsystem.component.molecule.AppCommandGroup
import com.mini.me_core.newui.designsystem.component.molecule.AppCommandPalette
import com.mini.me_core.newui.designsystem.component.molecule.AppContextMenu
import com.mini.me_core.newui.designsystem.component.molecule.AppMenuAction
import com.mini.me_core.newui.designsystem.component.molecule.AppFormDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppPermissionDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppSuccessDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppMultiSelectDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppCascadingMenu
import com.mini.me_core.newui.designsystem.component.molecule.AppCascadeNode
import com.mini.me_core.newui.designsystem.component.molecule.AppNavigationMenu
import com.mini.me_core.newui.designsystem.component.molecule.AppNavigationItem
import com.mini.me_core.newui.designsystem.component.molecule.AppCountdownDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppLoadingOverlay
import com.mini.me_core.newui.designsystem.component.molecule.AppUpdateDialog
import com.mini.me_core.newui.designsystem.component.molecule.AppFAB
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterChips
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterField
import com.mini.me_core.newui.designsystem.component.molecule.AppChecklistFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppChecklistToolbar
import com.mini.me_core.newui.designsystem.component.molecule.AppRangeFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppBooleanFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppRatingFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppDateFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppDropdownFilter
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterTokens
import com.mini.me_core.newui.designsystem.component.molecule.AppFilterSheet
import com.mini.me_core.newui.designsystem.component.molecule.AppInlineAlert
import com.mini.me_core.newui.designsystem.component.molecule.AppMenuRow
import com.mini.me_core.newui.designsystem.component.molecule.AppProgressBar
import com.mini.me_core.newui.designsystem.component.molecule.AppRatingBar
import com.mini.me_core.newui.designsystem.component.molecule.AppRingProgress
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchBar
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchableDropdown
import com.mini.me_core.newui.designsystem.component.molecule.AppSearchableOption
import com.mini.me_core.newui.designsystem.component.molecule.AppSectionGroup
import com.mini.me_core.newui.designsystem.component.molecule.AppSectionHeader
import com.mini.me_core.newui.designsystem.component.molecule.AppSegmentedToggle
import com.mini.me_core.newui.designsystem.component.molecule.AppShimmerBox
import com.mini.me_core.newui.designsystem.component.molecule.AppSkeletonList
import com.mini.me_core.newui.designsystem.component.molecule.AppSlider
import com.mini.me_core.newui.designsystem.component.molecule.AppSparkline
import com.mini.me_core.newui.designsystem.component.molecule.AppStatCard
import com.mini.me_core.newui.designsystem.component.molecule.AppStatusDot
import com.mini.me_core.newui.designsystem.component.molecule.AppStepper
import com.mini.me_core.newui.designsystem.component.molecule.AppSwitchRow
import com.mini.me_core.newui.designsystem.component.molecule.AppTabs
import com.mini.me_core.newui.designsystem.component.molecule.AppTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppToast
import com.mini.me_core.newui.designsystem.component.molecule.AppTypewriterText
import com.mini.me_core.newui.designsystem.component.molecule.AppSwipeAction
import com.mini.me_core.newui.designsystem.component.molecule.AppDock
import com.mini.me_core.newui.designsystem.component.molecule.AppDockItem
import com.mini.me_core.newui.designsystem.component.molecule.AppGradientBorder
import com.mini.me_core.newui.designsystem.component.molecule.AppRollingNumber
import com.mini.me_core.newui.designsystem.component.molecule.AppScrambleText
import com.mini.me_core.newui.designsystem.component.molecule.AppScrollProgress
import com.mini.me_core.newui.designsystem.component.molecule.AppSpotlightCard
import com.mini.me_core.newui.designsystem.component.molecule.AppTerminalLog
import com.mini.me_core.newui.designsystem.component.molecule.AppTypewriterText
import com.mini.me_core.newui.designsystem.component.molecule.AppSwipeAction
import com.mini.me_core.newui.designsystem.component.molecule.AppSwipeButton
import com.mini.me_core.newui.designsystem.component.molecule.AppTimeline
import com.mini.me_core.newui.designsystem.component.molecule.AppTimelineItem
import com.mini.me_core.newui.designsystem.component.molecule.AppTimelineTone
import com.mini.me_core.newui.designsystem.component.molecule.AppProgressSteps
import com.mini.me_core.newui.designsystem.component.molecule.AppTagInput
import com.mini.me_core.newui.designsystem.component.molecule.AppFilledTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppPasswordField
import com.mini.me_core.newui.designsystem.component.molecule.AppCountedTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppValidatedTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppInputValidity
import com.mini.me_core.newui.designsystem.component.molecule.AppMultiLineTextField
import com.mini.me_core.newui.designsystem.component.molecule.AppMessageField
import com.mini.me_core.newui.designsystem.component.molecule.AppPagination
import com.mini.me_core.newui.designsystem.component.molecule.AppKeyCombo
import com.mini.me_core.newui.designsystem.component.molecule.AppKeyCap
import com.mini.me_core.newui.designsystem.component.molecule.AppNotificationItem
import com.mini.me_core.newui.designsystem.component.molecule.AppMiniBarChart
import com.mini.me_core.newui.designsystem.component.molecule.AppFileCard
import com.mini.me_core.newui.designsystem.component.molecule.AppFileState
import com.mini.me_core.newui.designsystem.layout.AppEmptyState
import com.mini.me_core.newui.designsystem.layout.AppErrorState
import com.mini.me_core.newui.designsystem.layout.AppLoadingState
import com.mini.me_core.newui.designsystem.layout.pageContentPadding
import com.mini.me_core.newui.designsystem.layout.pageMaxWidth
import com.mini.me_core.newui.designsystem.slot.AppShell
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.theme.AppType
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/** 画廊对话流演示数据：消息 / 标记两类，key 唯一用于 Lazy 键与流式定位。 */
private sealed interface ChatItem {
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
        val reason: String? = null,
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

private val initialChatItems: List<ChatItem> = listOf(
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

/**
 * 样板页（§7 S0）：在一个页面内陈列令牌 / 原子组件 / 布局 / 三态 / 槽位，
 * 供负责人检验并敲定"基本完整落地"。附 android @Preview 可独立预览。
 */
@Composable
fun DesignGallery(onNavigateBack: (() -> Unit)? = null) {
    // 对话流完整演示页：样板页内部本地切换，不进 app 主导航；返回回到样板页。
    var showChatFlow by remember { mutableStateOf(false) }
    AppTheme {
        if (showChatFlow) {
            ChatFlowGallery(onNavigateBack = { showChatFlow = false })
        } else {
            AppShell(
                title = "Design Gallery",
                onNavigateBack = onNavigateBack,
            ) {
                GalleryBody(onOpenChatFlow = { showChatFlow = true })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryBody(onOpenChatFlow: () -> Unit = {}) {
    val scroll = rememberScrollState()
    var showDialog by remember { mutableStateOf(false) }
    var fieldText by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var segmentedIndex by remember { mutableStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }
    var stepperValue by remember { mutableStateOf(3) }
    var fabExpanded by remember { mutableStateOf(false) }
    var toastVisible by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(3) }
    var sliderValue by remember { mutableStateOf(34f) }
    var checkOn by remember { mutableStateOf(true) }
    var accordionOpen by remember { mutableStateOf(true) }
    var tabIndex by remember { mutableStateOf(0) }
    var filterSet by remember { mutableStateOf(setOf(0, 2)) }
    var page by remember { mutableStateOf(2) }
    var tags by remember { mutableStateOf(listOf("kotlin", "compose", "agent")) }
    // 滑扫协调：同批只开一项
    var swipeExpanded by remember { mutableStateOf<Int?>(null) }
    // 列表菜单 / 弹窗族演示状态
    var menuExpanded by remember { mutableStateOf(false) }
    var selectValue by remember { mutableStateOf("Claude") }
    var bottomSheetOpen by remember { mutableStateOf(false) }
    var singlePick by remember { mutableStateOf(0) }
    var multiPick by remember { mutableStateOf(setOf(1, 3)) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    // 新增列表/弹窗补充类型演示状态
    var comboValue by remember { mutableStateOf("Auto") }
    // AppSearchableDropdown 演示：本地 / 远程（150ms 假延迟）双数据源
    var localPick by remember { mutableStateOf<String?>(null) }
    var remotePick by remember { mutableStateOf<String?>(null) }
    var remoteQ by remember { mutableStateOf("") }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteResults by remember { mutableStateOf<List<AppSearchableOption>>(emptyList()) }
    var contextVisible by remember { mutableStateOf(false) }
    var contextPos by remember { mutableStateOf(Offset.Zero) }
    var paletteOpen by remember { mutableStateOf(false) }
    var paletteQuery by remember { mutableStateOf("") }
    var showFormDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showMultiSelectDialog by remember { mutableStateOf(false) }
    var multiSelData by remember { mutableStateOf(setOf(0, 2)) }
    var lastSwipeAction by remember { mutableStateOf<String?>(null) }
    // 本阶段新增类型演示状态：级联菜单 / 导航菜单 / 倒计时弹窗 / 阻塞遮罩
    var cascadeExpanded by remember { mutableStateOf(false) }
    var navIndex by remember { mutableStateOf(0) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showLoadingOverlay by remember { mutableStateOf(false) }
    // 筛选组件族演示状态
    var filterText by remember { mutableStateOf("") }
    var checklistSel by remember { mutableStateOf(setOf(0, 2)) }
    var rangeVal by remember { mutableStateOf(30f..80f) }
    var boolIdx by remember { mutableStateOf(0) }
    var ratingFilter by remember { mutableStateOf(3) }
    var dateIdx by remember { mutableStateOf(0) }
    var dropdownSel by remember { mutableStateOf(setOf(0)) }
    var dropdownSingle by remember { mutableStateOf(setOf(2)) }
    var activeTokens by remember {
        mutableStateOf(listOf("状态" to "进行中", "类型" to "代码文件"))
    }
    // 输入框族演示状态
    var passText by remember { mutableStateOf("secret123") }
    var countedText by remember { mutableStateOf("Compose 语法") }
    var validText by remember { mutableStateOf("user@example.com") }
    var multiText by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }
    var dialogInput by remember { mutableStateOf("") }
    var showDialogInput by remember { mutableStateOf(false) }
    // ===== AI 对话流演示状态：状态机 + 流式/失败重试/工具卡 =====
    val chatScope = rememberCoroutineScope()
    var chatSeq by remember { mutableIntStateOf(100) }
    var chatList by remember { mutableStateOf(initialChatItems) }
    val streamText = "正在逐步分析 **Agent 工具注册链路**：\n\n- 工具经 `ToolRegistry` 注册为 `AgentTool`\n- 权限由 `ToolPermissionManager` 审批\n\n```kotlin\nregistry.register(FileTools)\nregistry.register(ExecuteCommandTool)\n```\n\n稍等，我继续读取 `AgentModule.kt` 的依赖配置…"
    val retryText = "重试成功！已重新建立连接，`AgentRepository` 状态正常。"

    fun chatStream() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Msg(key = id, text = "", state = AppChatMessageState.Streaming, isUser = false)
        chatScope.launch {
            var shown = 0
            while (shown <= streamText.length) {
                chatList = chatList.map { if (it is ChatItem.Msg && it.key == id) it.copy(text = streamText.take(shown)) else it }
                shown += 3
                delay(18)
            }
            chatList = chatList.map { if (it is ChatItem.Msg && it.key == id) it.copy(state = AppChatMessageState.Complete) else it }
        }
    }

    fun chatFail() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Msg(key = id, text = "糟糕，连接 Provider 时超时了，请重试。", state = AppChatMessageState.Error, isUser = false)
    }

    fun chatRetry(id: Int) {
        chatList = chatList.map { if (it is ChatItem.Msg && it.key == id) it.copy(state = AppChatMessageState.Streaming, text = "") else it }
        chatScope.launch {
            var shown = 0
            while (shown <= retryText.length) {
                chatList = chatList.map { if (it is ChatItem.Msg && it.key == id) it.copy(text = retryText.take(shown)) else it }
                shown += 4
                delay(16)
            }
            chatList = chatList.map { if (it is ChatItem.Msg && it.key == id) it.copy(state = AppChatMessageState.Complete) else it }
        }
    }

    fun chatTool() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Tool(
            key = id,
            title = "执行命令",
            summary = "./gradlew :app:assembleDebug",
            state = AppToolCallState.Running,
            input = """{"command": "./gradlew :app:assembleDebug", "cwd": "/workspace"}""",
            streamOutput = "> Task :app:compileDebugKotlin",
        )
        chatScope.launch {
            delay(700)
            chatList = chatList.map {
                if (it is ChatItem.Tool && it.key == id) {
                    it.copy(streamOutput = "> Task :app:compileDebugKotlin UP-TO-DATE\n> Task :app:assembleDebug")
                } else {
                    it
                }
            }
            delay(900)
            chatList = chatList.map {
                if (it is ChatItem.Tool && it.key == id) {
                    it.copy(
                        state = AppToolCallState.Success,
                        durationMs = 1600,
                        streamOutput = null,
                        output = """{"exitCode": 0, "tookMs": 1600}""",
                    )
                } else {
                    it
                }
            }
        }
    }

    /** 人工审批（Intervention）：工具待许可 → 允许进入执行 → 成功。 */
    /** 人工审批（Intervention）：工具待许可 → 三档选择（拒绝 / 本次 / 始终允许 → 记忆）。 */
    fun chatToolApproval() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Tool(
            key = id,
            title = "执行命令",
            summary = "curl -X POST https://api.example.com/deploy",
            state = AppToolCallState.AwaitingApproval,
            input = """{"command": "curl -X POST https://api.example.com/deploy"}""",
            approvalHint = "该命令将向生产环境发起部署请求",
            onChoice = { choice ->
                when (choice) {
                    AppApprovalChoice.Reject -> chatList = chatList.map {
                        if (it is ChatItem.Tool && it.key == id) {
                            it.copy(
                                state = AppToolCallState.Error,
                                summary = "已拒绝执行 · 用户取消",
                                approvalHint = null,
                                onChoice = null,
                            )
                        } else {
                            it
                        }
                    }
                    AppApprovalChoice.Once -> {
                        chatList = chatList.map {
                            if (it is ChatItem.Tool && it.key == id) {
                                it.copy(
                                    state = AppToolCallState.Running,
                                    approvalHint = null,
                                    onChoice = null,
                                )
                            } else {
                                it
                            }
                        }
                        chatScope.launch {
                            delay(1200)
                            chatList = chatList.map {
                                if (it is ChatItem.Tool && it.key == id) {
                                    it.copy(
                                        state = AppToolCallState.Success,
                                        durationMs = 1200,
                                        output = """{"deployId": "dep-20260914-01", "status": "ok"}""",
                                    )
                                } else {
                                    it
                                }
                            }
                        }
                    }
                    AppApprovalChoice.Always -> {
                        // 记忆放行：本条立即执行成功，并追加一条"已记住"的后续审批卡
                        chatList = chatList.map {
                            if (it is ChatItem.Tool && it.key == id) {
                                it.copy(
                                    state = AppToolCallState.Success,
                                    durationMs = 820,
                                    output = """{"deployId": "dep-20260914-02", "status": "ok"}""",
                                    approvalHint = null,
                                    onChoice = null,
                                )
                            } else {
                                it
                            }
                        }
                        chatList = chatList + ChatItem.Tool(
                            key = chatSeq++,
                            title = "执行命令",
                            summary = "curl -X POST https://api.example.com/rollback",
                            state = AppToolCallState.AwaitingApproval,
                            input = """{"command": "curl -X POST https://api.example.com/rollback"}""",
                            approvalHint = "同类命令已记忆为始终允许",
                            approvalRemembered = true,
                        )
                    }
                }
            },
        )
    }

    /** 审批超时（Intervention 降级）：等待超时 → 默认策略拒绝，操作行收起为警示提示。 */
    fun chatToolApprovalExpired() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Tool(
            key = id,
            title = "执行命令",
            summary = "rm -rf ./build/artifacts",
            state = AppToolCallState.AwaitingApproval,
            input = """{"command": "rm -rf ./build/artifacts", "force": true}""",
            approvalHint = "该命令将删除构建产物目录",
            approvalExpired = true,
        )
    }

    /** MCP App：工具声明 `ui://` 资源，Host 渲染沙箱 iframe 交互式界面。 */
    fun chatMcpApp() {
        val id = chatSeq++
        chatList = chatList + ChatItem.McpApp(
            key = id,
            title = "构建耗时分析",
            state = AppMcpAppState.Loading,
            serverPrefix = "github",
            resourceUri = "ui://analytics/build-duration",
            onReload = {
                chatList = chatList.map {
                    if (it is ChatItem.McpApp && it.key == id) {
                        it.copy(state = AppMcpAppState.Loading)
                    } else {
                        it
                    }
                }
                chatScope.launch {
                    delay(600)
                    chatList = chatList.map {
                        if (it is ChatItem.McpApp && it.key == id) {
                            it.copy(state = AppMcpAppState.Ready)
                        } else {
                            it
                        }
                    }
                }
            },
        )
        chatScope.launch {
            delay(800)
            chatList = chatList.map {
                if (it is ChatItem.McpApp && it.key == id) it.copy(state = AppMcpAppState.Ready) else it
            }
        }
    }

    fun chatMcpTool() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Tool(
            key = id,
            title = "列出会话文件",
            serverPrefix = "github",
            state = AppToolCallState.Running,
            input = """{"query": "repo:minime/minime-core issues/42"}""",
        )
        chatScope.launch {
            delay(1200)
            chatList = chatList.map {
                if (it is ChatItem.Tool && it.key == id) {
                    it.copy(
                        state = AppToolCallState.Success,
                        durationMs = 1200,
                        output = """{"total": 3, "items": ["issue-42.md", "design-notes.md", "rc-log.md"]}""",
                    )
                } else {
                    it
                }
            }
        }
    }

    fun chatSkill() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Skill(
            key = id,
            name = "review-code",
            args = "--scope agent",
            state = AppSkillCallState.Running,
            description = "审查 MiniMe agent 模块的代码质量：检查 ToolRegistry 注册、权限审批链路与错误处理，并给出修改建议。",
        )
        chatScope.launch {
            delay(900)
            chatList = chatList.map {
                if (it is ChatItem.Skill && it.key == id) {
                    it.copy(state = AppSkillCallState.Success, durationMs = 900)
                } else {
                    it
                }
            }
        }
    }
    /** 思考过程折叠块：流式追加（ReasoningDelta）→ 完成定格。 */
    fun chatThinking() {
        val id = chatSeq++
        val thinkingText = "用户请求是「剖析项目结构」。\n\n我需要先读取 AGENTS.md 确认技术栈与纪律，再按 Feature-based 架构扫描 core / feature / datalayer 三大目录，最后给出模块关系图。\n\n扫描结果显示：核心是 agent 模块的 ToolRegistry 与权限审批链路，workspace 依赖 terminal 的容器能力。"
        chatList = chatList + ChatItem.Thinking(key = id, text = "", isStreaming = true)
        chatScope.launch {
            var shown = 0
            while (shown <= thinkingText.length) {
                chatList = chatList.map { if (it is ChatItem.Thinking && it.key == id) it.copy(text = thinkingText.take(shown)) else it }
                shown += 4
                delay(14)
            }
            chatList = chatList.map { if (it is ChatItem.Thinking && it.key == id) it.copy(isStreaming = false) else it }
        }
    }

    /** 计划审批卡：未决 → 批准执行 → 已批准；或继续细化 → 步骤更新后再次待决。 */
    fun chatPlan() {
        val id = chatSeq++
        chatList = chatList + ChatItem.Plan(
            key = id,
            title = "接入 SQLDelight V2 六库拓扑",
            steps = listOf(
                AppPlanStep("梳理现有 Room 域库的 schema 与依赖", AppPlanStepStatus.Done),
                AppPlanStep("在 datalayer/ 下搭建 V2 引擎与迁移链", AppPlanStepStatus.InProgress),
                AppPlanStep("改造 V1toV2FullMigrator 一次性移植器", AppPlanStepStatus.Pending),
                AppPlanStep("切 Repository 门面并跑通全量测试", AppPlanStepStatus.Pending),
            ),
            state = AppPlanState.AwaitingApproval,
            pendingSelection = "迁移器是否采用「读旧库直写新库」的直迁方案？",
            reason = "计划待你确认后切换 BUILD 模式执行",
            onApprove = {
                chatList = chatList.map {
                    if (it is ChatItem.Plan && it.key == id) {
                        it.copy(state = AppPlanState.InProgress, onApprove = null, onRefine = null)
                    } else {
                        it
                    }
                }
                chatScope.launch {
                    delay(1400)
                    chatList = chatList.map {
                        if (it is ChatItem.Plan && it.key == id) {
                            it.copy(
                                state = AppPlanState.Approved,
                                steps = it.steps.map { step ->
                                    if (step.status == AppPlanStepStatus.Pending) step.copy(status = AppPlanStepStatus.InProgress) else step
                                },
                            )
                        } else {
                            it
                        }
                    }
                }
            },
            onRefine = {
                chatList = chatList.map {
                    if (it is ChatItem.Plan && it.key == id) {
                        it.copy(
                            pendingSelection = null,
                            steps = it.steps + AppPlanStep("补充直迁失败的回滚与重试策略", AppPlanStepStatus.Pending),
                        )
                    } else {
                        it
                    }
                }
            },
        )
    }

    /** 附件卡：sendFile 展示型工具的产物（图片 + 文件）。 */
    fun chatAttachment() {
        // 两个附件各自从 chatSeq 取号：若第二条偷懒用 id+1，chatSeq 未同步自增，
        // 下一个演示项会复用相同 key，LazyColumn key 冲突直接崩溃。
        val id = chatSeq++
        val id2 = chatSeq++
        chatList = chatList + ChatItem.Attachment(
            key = id,
            fileName = "architecture-graph.png",
            mimeType = "image/png",
            sizeBytes = 1_360_000,
            containerPath = "~/workspace/artifacts/architecture-graph.png",
            isImage = true,
            onClick = { /* 演示占位：打开图片预览 */ },
        )
        chatList = chatList + ChatItem.Attachment(
            key = id2,
            fileName = "migration-plan.md",
            mimeType = "text/markdown",
            sizeBytes = 12_800,
            containerPath = "~/workspace/artifacts/migration-plan.md",
            onClick = { /* 演示占位：打开文档 */ },
        )
    }

    /** 工具链时间线：多步骤工具调用串联视图，头部汇总 + 状态节点 + 每步耗时。 */
    fun chatToolChain() {
        val id = chatSeq++
        chatList = chatList + ChatItem.ToolChain(
            key = id,
            label = "工具链",
            steps = listOf(
                AppToolChainStep(
                    title = "读取项目结构",
                    summary = "scan app/src/main/java",
                    state = AppToolChainStepState.Success,
                    durationMs = 320,
                ),
                AppToolChainStep(
                    title = "执行构建",
                    summary = "./gradlew :app:assembleDebug",
                    state = AppToolChainStepState.Success,
                    durationMs = 1840,
                ),
                AppToolChainStep(
                    title = "运行单元测试",
                    summary = "./gradlew :app:testReleaseUnitTest",
                    state = AppToolChainStepState.Success,
                    durationMs = 960,
                ),
                AppToolChainStep(
                    title = "发布 Release",
                    summary = "打 tag 并推送远端",
                    state = AppToolChainStepState.Running,
                ),
            ),
        )
        chatScope.launch {
            delay(1400)
            chatList = chatList.map {
                if (it is ChatItem.ToolChain && it.key == id) {
                    it.copy(
                        steps = it.steps.mapIndexed { index, step ->
                            if (index == it.steps.lastIndex) {
                                step.copy(state = AppToolChainStepState.Success, durationMs = 1320)
                            } else {
                                step
                            }
                        },
                    )
                } else {
                    it
                }
            }
        }
    }

    /** 结果摘要卡：工具链完成后对结果做意图归纳（流式总结 → 完成）。 */
    fun chatToolSummary() {
        val id = chatSeq++
        chatList = chatList + ChatItem.ToolSummary(
            key = id,
            text = "",
            state = AppToolSummaryState.Summarizing,
            toolCount = 4,
        )
        val summaryText = "已按计划完成 4 次工具调用：项目结构扫描、Debug 构建、单元测试全部通过，Release 已发布。构建耗时 1.8s，无告警。"
        chatScope.launch {
            var shown = 0
            while (shown <= summaryText.length) {
                chatList = chatList.map {
                    if (it is ChatItem.ToolSummary && it.key == id) {
                        it.copy(text = summaryText.take(shown))
                    } else {
                        it
                    }
                }
                shown += 3
                delay(16)
            }
            chatList = chatList.map {
                if (it is ChatItem.ToolSummary && it.key == id) {
                    it.copy(state = AppToolSummaryState.Done)
                } else {
                    it
                }
            }
        }
    }

    // 文件卡运行态：自动循环演示（上传推进 → 完成）
    var fileProgress by remember { mutableStateOf(0f) }
    var fileState by remember { mutableStateOf(AppFileState.Uploading) }
    LaunchedEffect(Unit) {
        while (true) {
            fileState = AppFileState.Uploading
            fileProgress = 0f
            while (fileProgress < 1f) {
                delay(360)
                fileProgress = (fileProgress + 0.10f).coerceAtMost(1f)
            }
            fileState = AppFileState.Downloaded
            delay(1800)
        }
    }
    // 可视化运行态：进度/环形/步骤/图表自动循环演示
    var progressBar by remember { mutableStateOf(0f) }
    var ringProgress by remember { mutableStateOf(0f) }
    var stepIndex by remember { mutableStateOf(0) }
    var sparkData by remember { mutableStateOf(listOf(20f, 34f, 28f, 52f, 48f, 70f, 86f, 66f, 92f)) }
    var barData by remember { mutableStateOf(listOf(40f, 72f, 58f, 90f, 66f, 84f)) }
    LaunchedEffect(Unit) {
        val rnd = Random.Default
        while (true) {
            progressBar = 0f
            ringProgress = 0f
            repeat(10) { i ->
                progressBar = (i + 1) / 10f
                ringProgress = (i + 1) / 10f
                delay(240)
            }
            stepIndex = (stepIndex + 1) % 4
            sparkData = List(9) { 20f + rnd.nextFloat() * 80f }
            barData = List(6) { 30f + rnd.nextFloat() * 70f }
            delay(1500)
        }
    }
    Column(
        modifier = Modifier
            .verticalScroll(scroll)
            .pageMaxWidth()
            .pageContentPadding()
            .padding(top = AppSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Md),
    ) {
        Section("令牌 · 色板") {
            ColorRow(
                listOf(
                    "BrandPrimary" to appPalette().primary,
                    "BrandSurface" to appPalette().surface,
                    "BrandAccent" to appPalette().accent,
                    "StatusSuccess" to AppColor.StatusSuccess,
                    "StatusDanger" to AppColor.StatusDanger,
                ),
            )
        }

        Section("令牌 · 度量/圆角/阴影") {
            Text(
                "Spacing · 间距增量（隔块间实际留白）",
                style = AppType.SectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SpacingBar("XS", AppSpacing.Xs)
            SpacingBar("SM", AppSpacing.Sm)
            SpacingBar("MD", AppSpacing.Md)
            SpacingBar("LG", AppSpacing.Lg)
            SpacingBar("XL", AppSpacing.Xl)
            SpacingBar("XXL", AppSpacing.Xxl)
            Text(
                "Radius · 圆角（实块对照）",
                style = AppType.SectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
                RadiusTile("SM", AppRadius.Sm)
                RadiusTile("MD", AppRadius.Md)
                RadiusTile("LG", AppRadius.Lg)
                RadiusTile("PILL", AppRadius.Pill)
            }
            Text(
                "Elevation · 阴影（卡片对照）",
                style = AppType.SectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
                ElevationTile("Z0", AppElevation.Z0)
                ElevationTile("Z1", AppElevation.Z1)
                ElevationTile("Z2", AppElevation.Z2)
                ElevationTile("Z4", AppElevation.Z4)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
                MetricParam("Sizing", "touch=${AppSizing.TouchTarget} iconBlock=${AppSizing.IconBlock}")
                MetricParam("Layout", "pageH=${AppLayout.PageHorizontal} max=${AppLayout.ContentMaxWidth}")
            }
        }

        Section("令牌 · 排版（iOS 类型尺度）") {
            Text("Large Title · 导航大标题", style = AppType.LargeTitle)
            Text("Title1 · 首屏区块主标题", style = AppType.Title1)
            Text("Title2 · 次级区块标题", style = AppType.Title2)
            Text("Title3 · 小标题", style = AppType.Title3)
            Text("Headline · 加粗正文", style = AppType.Headline)
            Text("Body · 标准正文", style = AppType.Body)
            Text("Callout · 次要正文", style = AppType.Callout)
            Text("Subhead · 注释行", style = AppType.Subhead)
            Text("Footnote · 脚注", style = AppType.Footnote)
            Text("Caption1 · 辅助说明", style = AppType.Caption1)
            Text("Counter", style = AppType.Caption2, color = appPalette().labelSecondary)
        }

        Section("原子组件") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(AppSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
                    IconContainer(icon = Icons.Rounded.Code, tint = Color.White)
                    Text(
                        text = "AppCard · IconContainer",
                        modifier = Modifier.padding(start = AppSpacing.Lg),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppChip(text = "选中", icon = Icons.Rounded.Home)
                AppChip(text = "未选中", selected = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppIcon(icon = Icons.Rounded.Settings)
                AppIcon(icon = Icons.Rounded.Palette, size = AppSizing.IconL)
                AppIcon(icon = Icons.Rounded.Code, size = AppSizing.IconXs)
            }
        }

        Section("分子组件 · 按钮") {
            AppButton(text = "Primary", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppButton(
                    text = "Tonal",
                    variant = AppButtonVariant.FilledTonal,
                    onClick = {},
                )
                AppButton(
                    text = "Outlined",
                    variant = AppButtonVariant.Outlined,
                    onClick = {},
                )
                AppButton(
                    text = "Text",
                    variant = AppButtonVariant.Text,
                    onClick = {},
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppButton(text = "禁用", enabled = false, onClick = {})
                AppButton(
                    text = "危险",
                    variant = AppButtonVariant.Outlined,
                    onClick = {},
                )
            }
        }

        Section("分子组件 · 输入") {
            AppTextField(
                value = fieldText,
                onValueChange = { fieldText = it },
                label = "示例输入",
                placeholder = "请输入内容…",
            )
        }

        Section("分子组件族 · 筛选（按数据类型个性化）") {
            // 综合筛选面板：把多种数据类型的筛选控件组合进抽屉卡片，联动「已选计数」。
            val sheetActive =
                (if (filterText.isNotEmpty()) 1 else 0) +
                    (if (boolIdx != 0) 1 else 0) +
                    (if (rangeVal != 30f..80f) 1 else 0) +
                    (if (ratingFilter > 0) 1 else 0) +
                    checklistSel.size
            AppFilterSheet(
                title = "数据筛选",
                activeCount = sheetActive,
                onClearAll = {
                    filterText = ""; boolIdx = 0; rangeVal = 30f..80f
                    ratingFilter = 0; checklistSel = emptySet()
                },
                onReset = {
                    boolIdx = 0; rangeVal = 30f..80f; ratingFilter = 0; checklistSel = emptySet()
                },
                onApply = {},
            ) {
                // 文本型：关键字筛选
                AppFilterField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = "按名称 / 标签筛查…",
                )
                // 布尔型：三态（全部 / 进行中 / 已完成）
                AppBooleanFilter(
                    labels = listOf("全部状态", "进行中", "已完成"),
                    selectedIndex = boolIdx,
                    onSelect = { boolIdx = it },
                )
                // 数值型：价格 / 大小范围双滑块
                AppRangeFilter(
                    value = rangeVal,
                    onValueChange = { rangeVal = it },
                    prefix = "¥",
                )
                // 评分型：星级筛选
                AppRatingFilter(
                    value = ratingFilter,
                    onValueChange = { ratingFilter = it },
                )
            }
            // 枚举型：多选 + 计数 + 全选/清空
            AppChecklistToolbar(
                selectedCount = checklistSel.size,
                total = 4,
                onSelectAll = { checklistSel = setOf(0, 1, 2, 3) },
                onClear = { checklistSel = emptySet() },
            )
            AppChecklistFilter(
                options = listOf("聊天对话", "代码文件", "设计文档", "终端会话"),
                counts = listOf(128, 45, 23, 67),
                selected = checklistSel,
                onToggle = { i ->
                    checklistSel = if (i in checklistSel) checklistSel - i else checklistSel + i
                },
            )
            // 日期型：预设范围
            AppDateFilter(
                presets = listOf("不限", "今日", "本周", "本月"),
                selectedIndex = dateIdx,
                onSelect = { dateIdx = it },
                selectedRangeText = if (dateIdx == 0) {
                    "不限时间"
                } else {
                    listOf("2026/9/5 至今", "2026/8/31 ~9/5", "2026/9/1 ~9/5")[dateIdx - 1]
                },
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            // 可搜索下拉筛选（多选 + 单选两种语义，参考 iOSDropDown 交互）
            AppDropdownFilter(
                options = listOf("全部类型", "代码文件", "设计文档", "终端会话", "聊天对话"),
                selected = dropdownSel,
                onToggle = { i ->
                    dropdownSel = if (i in dropdownSel) dropdownSel - i else dropdownSel + i
                },
                label = "类型",
                maxPopupHeight = 240,
                onClear = { dropdownSel = emptySet() },
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            AppDropdownFilter(
                options = listOf("全部状态", "进行中", "已完成", "已归档"),
                selected = dropdownSingle,
                onToggle = { i ->
                    dropdownSingle = if (i in dropdownSingle) emptySet() else setOf(i)
                },
                label = "状态",
                singleSelect = true,
                searchable = false,
                maxPopupHeight = 200,
            )
            // 已激活筛选 token 行（参考 iOS 26 search tokens）：可逐个移除 + 批量清除
            AppFilterTokens(
                active = activeTokens,
                onRemove = { i -> activeTokens = activeTokens.filterIndexed { idx, _ -> idx != i } },
                onClearAll = { activeTokens = emptyList() },
            )
        }

        Section("分子组件族 · 输入框") {
            // 填充式文本输入框（带前置图标 + 一键清除）
            AppFilledTextField(
                value = fieldText,
                onValueChange = { fieldText = it },
                label = "填充式文本框",
                placeholder = "例如：项目名称",
                leadingIcon = Icons.Rounded.Person,
            )
            // 密码输入框（可见性切换）
            AppPasswordField(
                value = passText,
                onValueChange = { passText = it },
                label = "密码输入框",
                placeholder = "输入密码",
            )
            // 带字符计数输入框（超限截断）
            AppCountedTextField(
                value = countedText,
                onValueChange = { countedText = it },
                label = "带计数输入框",
                maxLength = 16,
            )
            // 验证态输入框（Normal / Error / Success）
            AppValidatedTextField(
                value = validText,
                onValueChange = { validText = it },
                label = "验证态输入框",
                placeholder = "请输入邮箱",
                validity = when {
                    validText.isBlank() -> AppInputValidity.Normal
                    "@" in validText && "." in validText -> AppInputValidity.Success
                    else -> AppInputValidity.Error
                },
                helper = when {
                    validText.isBlank() -> "邮箱 / 手机号等格式校验"
                    "@" in validText && "." in validText -> "校验通过"
                    else -> "请输入合法邮箱地址"
                },
            )
            // 多行文本域（随内容增高）
            AppMultiLineTextField(
                value = multiText,
                onValueChange = { multiText = it },
                label = "多行文本域",
                placeholder = "支持多行输入，随内容增高…",
            )
            // 消息输入框（Chat Composer）：输入后发送按钮亮起
            AppMessageField(
                value = msgText,
                onValueChange = { msgText = it },
                onSend = { if (msgText.isNotBlank()) msgText = "" },
                placeholder = "消息输入框：输入后发送按钮亮起…",
            )
            // 弹窗输入框：输入框在弹窗 / 表单内的标准用法
            TextButton(onClick = { showDialogInput = true }) { Text("打开弹窗输入") }
            if (showDialogInput) {
                AlertDialog(
                    onDismissRequest = { showDialogInput = false },
                    title = { Text("弹窗输入框") },
                    text = {
                        AppFilledTextField(
                            value = dialogInput,
                            onValueChange = { dialogInput = it },
                            label = "名称",
                            placeholder = "请输入…",
                            leadingIcon = Icons.Rounded.Person,
                        )
                    },
                    confirmButton = { TextButton(onClick = { showDialogInput = false }) { Text("确定") } },
                    dismissButton = { TextButton(onClick = { showDialogInput = false }) { Text("取消") } },
                )
            }
        }

        Section("分子组件 · 分组 / 列表行 / 状态") {
            AppSectionHeader(title = "区块标题")
            AppSectionGroup {
                AppMenuRow(
                    title = "设置项（无图标）",
                    subtitle = "副标题说明",
                    trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
                AppDivider()
                AppMenuRow(
                    title = "带图标块",
                    subtitle = "iconContainer = true",
                    icon = Icons.Rounded.Code,
                    iconContainer = true,
                )
                AppDivider()
                AppMenuRow(
                    title = "纯色前置图标",
                    icon = Icons.Rounded.Settings,
                    iconContainer = false,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
                AppStatusDot(color = AppColor.StatusSuccess, label = "成功")
                AppStatusDot(color = AppColor.StatusWarning, label = "警告")
                AppStatusDot(color = AppColor.StatusDanger)
            }
        }

        Section("分子组件族 · 列表菜单") {
            // 下拉菜单：锚定到按钮的 DropdownMenu
            Box {
                AppButton(
                    text = "更多操作",
                    variant = AppButtonVariant.Outlined,
                    onClick = { menuExpanded = !menuExpanded },
                )
                AppMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
                    AppMenuItem(label = "重命名", leadingIcon = Icons.Rounded.Settings, onClick = { menuExpanded = false })
                    AppMenuItem(label = "加入收藏", leadingIcon = Icons.Rounded.Notifications, onClick = { menuExpanded = false })
                    AppMenuDivider()
                    AppMenuItem(
                        label = "删除",
                        leadingIcon = Icons.Rounded.DeleteOutline,
                        tint = AppColor.StatusDanger,
                        onClick = { menuExpanded = false },
                    )
                }
            }
            // 暴露式下拉选择框：常驻显示已选项
            AppSelectField(
                value = selectValue,
                options = listOf("Claude", "OpenAI", "Gemini"),
                onSelect = { selectValue = it },
                label = "默认模型",
                leadingIcon = Icons.Rounded.Settings,
            )
            // 内联选择列表：单选
            AppSelectionList(
                items = listOf(
                    AppSelectionItem("Claude", "Sonnet 4"),
                    AppSelectionItem("OpenAI", "GPT-5"),
                    AppSelectionItem("Gemini", "2.0 Pro"),
                ),
                mode = AppSelectionMode.Single,
                selected = singlePick,
                onSelect = { singlePick = it },
            )
            // 内联选择列表：多选
            AppSelectionList(
                items = listOf(
                    AppSelectionItem("聊天气泡"),
                    AppSelectionItem("文件卡片"),
                    AppSelectionItem("消息通知"),
                    AppSelectionItem("状态指示"),
                ),
                mode = AppSelectionMode.Multiple,
                selected = multiPick,
                onSelect = {},
                onToggle = { i -> multiPick = if (i in multiPick) multiPick - i else multiPick + i },
            )
            AppButton(text = "抽屉选择列表", variant = AppButtonVariant.FilledTonal, onClick = { bottomSheetOpen = true })
            if (bottomSheetOpen) {
                AppBottomSheetList(onDismiss = { bottomSheetOpen = false }, title = "选择目标位置") {
                    AppSelectionList(
                        items = listOf(
                            AppSelectionItem("工作区", "workspace"),
                            AppSelectionItem("文档", "docs"),
                            AppSelectionItem("日志", "logs"),
                        ),
                        mode = AppSelectionMode.Single,
                        selected = singlePick,
                        onSelect = { singlePick = it }
                    )
                }
            }
            // 可搜索下拉选择框：输入即过滤
            AppComboBox(
                value = comboValue,
                options = listOf("Auto", "Gemini 2.0", "GPT-5", "Claude Sonnet", "Qwen Max", "DeepSeek V3"),
                onSelect = { comboValue = it },
                label = "可搜索下拉选择框",
                leadingIcon = Icons.Rounded.Search,
                onClear = { comboValue = "" },
            )
            // 上下文菜单：长按目标弹出
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadius.Md))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { pos -> contextPos = pos; contextVisible = true })
                    }
                    .padding(AppSpacing.Md),
            ) {
                Text(
                    text = "长按此处打开上下文菜单",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (contextVisible) {
                AppContextMenu(
                    visible = true,
                    position = contextPos,
                    items = listOf(
                        AppMenuAction("复制路径", Icons.Rounded.Code),
                        AppMenuAction("打开文件", Icons.Rounded.InsertDriveFile),
                        AppMenuAction("删除", Icons.Rounded.Delete, danger = true),
                    ),
                    onItemClick = { contextVisible = false },
                    onDismiss = { contextVisible = false },
                )
            }
            Text(
                text = "命令面板 = 系统快捷键命令中枢（桌面入口）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppIconButton(
                text = "命令面板 Ctrl+K",
                variant = AppButtonVariant.FilledTonal,
                // 装饰图标：旁侧已有文字/语义，跳过无障碍
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                onClick = { paletteOpen = true },
            )
            // 级联子菜单：文件 → 导出 → 格式 的多级飞墙
            // 按钮靠右放置，验证子列空间不足时自动翻左不溢出
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box {
                    AppIconButton(
                        text = "级联子菜单",
                        variant = AppButtonVariant.FilledTonal,
                        // 装饰图标：旁侧已有文字/语义，跳过无障碍
                        leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, contentDescription = null) },
                        onClick = { cascadeExpanded = !cascadeExpanded },
                    )
                    AppCascadingMenu(
                        expanded = cascadeExpanded,
                        onDismiss = { cascadeExpanded = false },
                        items = listOf(
                            AppCascadeNode("文件", icon = Icons.Rounded.InsertDriveFile, children = listOf(
                                AppCascadeNode("打开…", icon = Icons.Rounded.Search),
                                AppCascadeNode("导出", icon = Icons.Rounded.Archive, children = listOf(
                                    AppCascadeNode("Markdown", icon = Icons.Rounded.Code),
                                    AppCascadeNode("JSON", icon = Icons.Rounded.Code),
                                    AppCascadeNode("PNG 插图", icon = Icons.Rounded.Palette),
                                )),
                            )),
                            AppCascadeNode("分享", icon = Icons.Rounded.Notifications),
                            AppCascadeNode("删除", icon = Icons.Rounded.Delete, danger = true),
                        ),
                        onItemClick = { cascadeExpanded = false },
                    )
                }
            }
            // 导航/侧栏列表：带计数徽标与危险项
            AppNavigationMenu(
                items = listOf(
                    AppNavigationItem("工作台", Icons.Rounded.Home),
                    AppNavigationItem("会话", Icons.Rounded.Code, badge = 12),
                    AppNavigationItem("通知", Icons.Rounded.Notifications, badge = 3),
                    AppNavigationItem("退出登录", Icons.Rounded.Settings, danger = true),
                ),
                selectedIndex = navIndex,
                onSelect = { navIndex = it },
            )
        }

        Section("分子组件 · 弹窗") {
            Text("确认 / 提示 / 输入 / 选择 / 更新 · 动作面板", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppButton(text = "确认", onClick = { showDialog = true })
                AppButton(text = "提示", variant = AppButtonVariant.FilledTonal, onClick = { showAlertDialog = true })
                AppButton(text = "输入", variant = AppButtonVariant.Text, onClick = { showPromptDialog = true })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppButton(text = "选择", variant = AppButtonVariant.Outlined, onClick = { showSelectionDialog = true })
                AppButton(text = "更新", variant = AppButtonVariant.Outlined, onClick = { showUpdateDialog = true })
                AppButton(text = "动作面板", variant = AppButtonVariant.Outlined, onClick = { showActionSheet = true })
            }
            // 次级动作：等宽两列网格，左对齐；右列起始 x 不随左列文字宽度漂移
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "表单", variant = AppButtonVariant.Text, onClick = { showFormDialog = true })
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "权限", variant = AppButtonVariant.Text, onClick = { showPermissionDialog = true })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "成功", variant = AppButtonVariant.Text, onClick = { showSuccessDialog = true })
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "多选", variant = AppButtonVariant.Text, onClick = { showMultiSelectDialog = true })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "倒计时防误", variant = AppButtonVariant.Text, onClick = { showCountdownDialog = true })
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AppButton(text = "阻塞遮罩", variant = AppButtonVariant.Text, onClick = { showLoadingOverlay = true })
                }
            }
        }

        Section("分子组件族 · 表单 & 检索") {
            AppSegmentedToggle(
                options = listOf("全部", "进行中", "已完成"),
                selectedIndex = segmentedIndex,
                onSelect = { segmentedIndex = it },
            )
            AppSwitchRow(
                title = "自动同步",
                subtitle = "开启后在后台自动同步远端变更",
                checked = switchOn,
                onCheckedChange = { switchOn = it },
            )
            AppStepper(
                value = stepperValue,
                onValueChange = { stepperValue = it },
                min = 0,
                max = 10,
            )
            AppSearchBar(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "搜索项目 / 命令 / 会话…",
                onClear = { searchText = "" },
            )
            // 可搜索下拉 · 本地数据源：组件内前缀优先过滤，支持键盘 ↑↓·Enter·Esc
            Text(
                "可搜索下拉 · 本地数据源（前缀优先 · 键盘 ↑↓·Enter·Esc）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppSearchableDropdown(
                value = localPick,
                onSelect = { localPick = it.label },
                label = "选择城市",
                placeholder = "输入城市名…",
                leadingIcon = Icons.Rounded.Search,
                onClear = { localPick = null },
                options = remember {
                    listOf(
                        AppSearchableOption("北京", subtitle = "Beijing · 华北", trailing = "2189万"),
                        AppSearchableOption("上海", subtitle = "Shanghai · 华东", trailing = "2487万"),
                        AppSearchableOption("广州", subtitle = "Guangzhou · 华南"),
                        AppSearchableOption("深圳", subtitle = "Shenzhen · 华南"),
                        AppSearchableOption("杭州", subtitle = "Hangzhou · 华东"),
                        AppSearchableOption("成都", subtitle = "Chengdu · 西南"),
                        AppSearchableOption("南京", subtitle = "Nanjing · 华东"),
                        AppSearchableOption("武汉", subtitle = "Wuhan · 华中"),
                        AppSearchableOption("西安", subtitle = "Xi'an · 西北"),
                        AppSearchableOption("苏州", subtitle = "Suzhou · 华东"),
                        AppSearchableOption("重庆", subtitle = "Chongqing · 西南"),
                        AppSearchableOption("长沙", subtitle = "Changsha · 华中"),
                    )
                },
            )
            // 可搜索下拉 · 远程数据源：150ms 假延迟 + loading 转圈 + 无结果空态
            Text(
                "可搜索下拉 · 远程数据源（150ms 假延迟 · 输入无结果显示空态）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val remoteCatalog = remember {
                listOf(
                    "Anthropic", "OpenAI", "Google Gemini", "DeepSeek", "Qwen", "Llama",
                    "Mistral", "Grok", "Gemma", "Claude", "Yi Large", "Baichuan",
                    "Doubao", "Skylark", "Hunyuan",
                ).map { AppSearchableOption(it, subtitle = "远程模型") }
            }
            // 调用方自行防抖：150ms 后产出远程结果，期间 loading=true
            LaunchedEffect(remoteQ) {
                if (remoteQ.isBlank()) {
                    remoteResults = emptyList()
                    remoteLoading = false
                    return@LaunchedEffect
                }
                remoteLoading = true
                delay(150)
                val needle = remoteQ.trim()
                remoteResults = remoteCatalog.filter { it.label.contains(needle, ignoreCase = true) }
                remoteLoading = false
            }
            AppSearchableDropdown(
                value = remotePick,
                onSelect = { remotePick = it.label },
                label = "远端模型检索",
                placeholder = "输入 ≥1 个字触发远程…",
                leadingIcon = Icons.Rounded.Search,
                options = emptyList(),
                remoteQuery = { q ->
                    if (q.isBlank() || q == remotePick) remoteCatalog else remoteResults
                },
                loading = remoteLoading,
                onQueryChange = { remoteQ = it },
                onClear = { remotePick = null },
            )
        }

        Section("分子组件族 · 反馈高动效") {
            AppProgressBar(progress = progressBar)
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppShimmerBox(Modifier.size(56.dp))
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                    AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth(0.7f))
                    AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth())
                    AppShimmerBox(Modifier.height(AppSpacing.Md).fillMaxWidth())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xl)) {
                Box {
                    AppIcon(icon = Icons.Rounded.Notifications)
                    // 角标锚定图标右上角并向外偏移（半压角），不压住图标中心
                    AppBadge(
                        count = 12,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 5.dp, y = (-5).dp),
                    )
                }
                AppBadge(count = 999, maxShow = 99)
                AppBadgeDot()
            }
        }

        Section("分子组件族 · 数据 / 导航 / 操作") {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                AppAvatar(text = "MiniMe-core", online = true)
                AppAvatar(text = "AI", online = false)
            }
            // 短路径：不触发折叠
            AppBreadcrumb(items = listOf("工作区", "remote", "agents", "prompts"))
            // 长路径（>4 段）：首项 + … + 末 2 项折叠演示
            AppBreadcrumb(
                items = listOf(
                    AppCrumb(label = "根目录"),
                    AppCrumb(label = "projects"),
                    AppCrumb(label = "mini-me"),
                    AppCrumb(label = "src"),
                    AppCrumb(label = "main"),
                    AppCrumb(label = "kotlin"),
                    AppCrumb(label = "designsystem"),
                    AppCrumb(label = "BreadcrumbDemo.kt"),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                AppStatCard(
                    label = "已执行命令",
                    value = 1284.0,
                    icon = Icons.Rounded.Code,
                    trend = "+12.5%",
                    modifier = Modifier.weight(1f),
                )
                AppStatCard(
                    label = "完成率",
                    value = 86.0,
                    icon = Icons.Rounded.Home,
                    trend = "+3.2%",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                AppFAB(
                    icon = Icons.Rounded.Add,
                    text = "新建",
                    expanded = fabExpanded,
                    onClick = { fabExpanded = !fabExpanded },
                )
                AppButton(
                    text = if (toastVisible) "隐藏 Toast" else "显示 Toast",
                    onClick = { toastVisible = !toastVisible },
                )
            }
            AppToast(
                message = "操作成功，已保存更改",
                visible = toastVisible,
                icon = Icons.Rounded.Notifications,
            )
        }

        Section("分子组件族 · 数据可视化") {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xl), verticalAlignment = Alignment.CenterVertically) {
                AppRingProgress(progress = ringProgress)
                AppRingProgress(progress = ringProgress, boxSize = 64.dp, strokeWidth = 6.dp)
            }
            AppSparkline(
                data = sparkData,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        }

        Section("分子组件族 · 表单增强") {
            AppRatingBar(value = rating, onValueChange = { rating = it })
            AppSlider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 0f..100f)
            AppCheckRow(
                title = "始终显示输出面板",
                subtitle = "执行命令后自动展开运行区",
                checked = checkOn,
                onCheckedChange = { checkOn = it },
            )
        }

        Section("分子组件族 · 分级 / 导航 / 检索") {
            AppTabs(
                tabs = listOf("会话", "工具", "进程"),
                selectedIndex = tabIndex,
                onSelect = { tabIndex = it },
            )
            AppFilterChips(
                options = listOf("全部", "未读", "已收藏", "归档"),
                selectedIndices = filterSet,
                onToggle = { i ->
                    filterSet = if (i in filterSet) filterSet - i else filterSet + i
                },
            )
            AppAccordion(
                title = if (accordionOpen) "展开的高级选项" else "折叠的高级选项",
                subtitle = "点击展开 / 收起",
                expanded = accordionOpen,
                onToggle = { accordionOpen = !accordionOpen },
            ) {
                AppCheckRow(title = "启用沙箱隔离", checked = checkOn, onCheckedChange = { checkOn = it })
            }
        }

        Section("分子组件族 · 反馈层") {
            AppSkeletonList(rows = 2)
            AppInlineAlert(tone = AppAlertTone.Success, title = "配置已保存", message = "更改已同步到远端仓库。")
            AppInlineAlert(tone = AppAlertTone.Warning, message = "该命令需要容器运行时，请先启动 PRoot。")
            AppInlineAlert(tone = AppAlertTone.Danger, message = "数据目录不可写，请检查权限。", onDismiss = {})
        }

        Section("分子组件族 · AI 对话流") {
            // 完整剧情演示入口：独立全屏页，多轮对话串起对话流全部组件
            AppButton(
                text = "打开完整对话流演示 →",
                onClick = onOpenChatFlow,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "一段多轮任务对话，串联气泡 / 思考 / 计划审批 / 工具 / MCP / 技能 / 附件 / 终端 / 摘要等全部组件，支持自动播放、单步与卡片交互。",
                style = MaterialTheme.typography.bodySmall,
                color = appPalette().labelSecondary,
                modifier = Modifier.padding(top = AppSpacing.Xs),
            )
            Spacer(Modifier.height(AppSpacing.Md))
            // 控制条：触发流式回复 / 失败重试 / 工具卡 / 审批 / MCP / 技能 / 思考 / 计划 / 附件 / 链 / 摘要
            // 横向可滑动，不换行堆叠；按钮自身 wrapContentWidth，避免被强制均分宽度。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                AppButton(text = "AI 流式回复", onClick = { chatStream() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "模拟失败", onClick = { chatFail() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "工具调用", onClick = { chatTool() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "MCP 工具", onClick = { chatMcpTool() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "待审批", onClick = { chatToolApproval() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "审批超时", onClick = { chatToolApprovalExpired() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "MCP App", onClick = { chatMcpApp() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "技能调用", onClick = { chatSkill() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "思考过程", onClick = { chatThinking() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "计划审批", onClick = { chatPlan() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "附件", onClick = { chatAttachment() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "工具链", onClick = { chatToolChain() }, variant = AppButtonVariant.Outlined)
                AppButton(text = "结果摘要", onClick = { chatToolSummary() }, variant = AppButtonVariant.Outlined)
            }
            AppMessageScroller(
                modifier = Modifier.height(440.dp),
                newMessageKey = chatSeq,
                onLoadHistory = { /* 演示占位：真实场景拉取更早消息 */ },
            ) {
                items(items = chatList.asReversed(), key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.Marker -> AppChatMarker(
                            text = item.text,
                            kind = item.kind,
                            running = item.running,
                            tone = item.tone,
                        )
                        is ChatItem.Tool -> AppToolCallCard(
                            title = item.title,
                            summary = item.summary,
                            state = item.state,
                            serverPrefix = item.serverPrefix,
                            durationMs = item.durationMs,
                            input = item.input,
                            output = item.output,
                            streamOutput = item.streamOutput,
                            approvalHint = item.approvalHint,
                            onApprove = item.onApprove,
                            onReject = item.onReject,
                            onChoice = item.onChoice,
                            alwaysDisabled = item.alwaysDisabled,
                            alwaysDisabledReason = item.alwaysDisabledReason,
                            approvalExpired = item.approvalExpired,
                            approvalRemembered = item.approvalRemembered,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.McpApp -> AppMcpAppCard(
                            title = item.title,
                            state = item.state,
                            serverPrefix = item.serverPrefix,
                            resourceUri = item.resourceUri,
                            onReload = item.onReload,
                            onExpand = item.onExpand,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.Skill -> AppSkillCallCard(
                            name = item.name,
                            args = item.args,
                            state = item.state,
                            description = item.description,
                            durationMs = item.durationMs,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.Thinking -> AppThinkingBlock(
                            text = item.text,
                            isStreaming = item.isStreaming,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.Plan -> AppPlanCard(
                            title = item.title,
                            steps = item.steps,
                            state = item.state,
                            pendingSelection = item.pendingSelection,
                            reason = item.reason,
                            onApprove = item.onApprove,
                            onRefine = item.onRefine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.Attachment -> AppAttachmentCard(
                            fileName = item.fileName,
                            mimeType = item.mimeType,
                            sizeBytes = item.sizeBytes,
                            containerPath = item.containerPath,
                            isImage = item.isImage,
                            onClick = item.onClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.ToolChain -> AppToolChainTimeline(
                            steps = item.steps,
                            label = item.label,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.ToolSummary -> AppToolSummaryCard(
                            text = item.text,
                            state = item.state,
                            toolCount = item.toolCount,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        is ChatItem.Msg -> AppMessageRow(
                            text = item.text,
                            state = item.state,
                            isUser = item.isUser,
                            avatarLabel = if (item.isUser) "你" else "AI",
                            name = if (item.isUser) "你" else "MiniMe Agent",
                            timestamp = "09:4${item.key % 10}",
                            grouped = item.grouped,
                            onCopy = { /* 演示占位：写入剪贴板 */ },
                            onRetry = if (item.state == AppChatMessageState.Error) {
                                { chatRetry(item.key) }
                            } else {
                                null
                            },
                            onDelete = { chatList = chatList.filterNot { it.key == item.key } },
                            swipeEnabled = true,
                            swipeIndex = item.key,
                            swipeExpandedIndex = swipeExpanded,
                            onSwipeExpanded = { swipeExpanded = it },
                        )
                    }
                }
            }
            // 分子直出：AppChatBubble 四态 / AppTypingIndicator / AppMarkdownText
            Spacer(Modifier.height(AppSpacing.Lg))
            AppSectionHeader(title = "分子直出 · 气泡 / 指示器 / Markdown")
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
                AppChatBubble(
                    text = "用户侧气泡 · 品牌色胶囊",
                    state = AppChatMessageState.Complete,
                    isUser = true,
                )
                AppChatBubble(
                    text = "AI 侧气泡 · 表面卡片描边",
                    state = AppChatMessageState.Complete,
                )
                AppChatBubble(
                    text = "",
                    state = AppChatMessageState.Pending,
                )
                AppChatBubble(
                    text = "正在生成代码…",
                    state = AppChatMessageState.Streaming,
                )
                AppChatBubble(
                    text = "连接 Provider 超时，请重试。",
                    state = AppChatMessageState.Error,
                    onRetry = { },
                )
                AppMarkdownText(
                    text = "## 标题与引用\n\n> 引用块：`AgentTool` 经 `ToolRegistry` 注册。\n\n1. 有序列表第一项\n2. 有序列表第二项\n\n| 工具 | 状态 |\n| --- | --- |\n| FileTools | 就绪 |\n| ExecuteCommandTool | 就绪 |\n\n- 无序列表：粗体 **关键词**、行内代码 `ToolRegistry`\n- 行内链接：[查看文档](https://example.com)",
                )
            }
        }

        Section("分子组件族 · 滑扫操作") {
            val swipeRows = listOf("会话 A · minime-agent", "会话 B · settings refactor", "会话 C · terminal local")
            swipeRows.forEachIndexed { index, title ->
                Column {
                    AppSwipeAction(
                        index = index,
                        expandedIndex = swipeExpanded,
                        onExpanded = { swipeExpanded = it },
                        actionWidth = 128.dp,
                        actions = {
                            AppSwipeButton(
                                icon = Icons.Rounded.Archive,
                                label = "归档",
                                background = AppColor.StatusInfo,
                                onClick = { /* 预留接入归档逻辑 */ },
                            )
                            AppSwipeButton(
                                icon = Icons.Rounded.Delete,
                                label = "删除",
                                background = AppColor.StatusDanger,
                                onClick = { /* 预留接入删除逻辑 */ },
                            )
                        },
                    ) {
                        AppMenuRow(title = title, subtitle = "左滑露出操作 · 点击内容收起 · 同批只开一项", icon = Icons.Rounded.Code)
                    }
                    if (index != swipeRows.lastIndex) {
                        Spacer(Modifier.height(AppSpacing.Sm))
                    }
                }
            }
        }

        Section("分子组件族 · 高动效展示") {
            var progress by remember { mutableStateOf(0f) }
            var rolling by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    rolling = (24..180).random()
                    progress = 0f
                    delay(2000)
                    progress = 1f
                    delay(2400)
                }
            }
            // 进度条补标签：满进度时单独一条全宽蓝线语义不明，标签让其成为"执行进度"控件。
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("执行进度", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(AppSpacing.Sm))
                AppScrollProgress(fraction = progress, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(AppSpacing.Sm))

            Row(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // weight(1f) 占剩余空间：窄屏上压缩文本列，避免与 AppDock 互相叠压。
                Column(Modifier.weight(1f)) {
                    Text("令牌速率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AppRollingNumber(value = rolling, style = MaterialTheme.typography.headlineMedium, color = appPalette().primary)
                }
                AppDock(
                    selectedIndex = 0,
                    items = listOf(
                        AppDockItem(Icons.Rounded.Home, "工作台"),
                        AppDockItem(Icons.Rounded.Code, "代码"),
                        AppDockItem(Icons.Rounded.Settings, "设置"),
                        AppDockItem(Icons.Rounded.Notifications, "通知"),
                    ),
                )
            }
            Spacer(Modifier.height(AppSpacing.Xs))

            AppTerminalLog()
            Spacer(Modifier.height(AppSpacing.Md))

            AppMarquee(background = appPalette().surfaceDim) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("容器已就绪", style = MaterialTheme.typography.bodySmall, color = appPalette().primary)
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("MCP 服务器监听 0.0.0.0:9898", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("PRoot + Alpine 3.21 · arm64", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(AppSpacing.Md))

            AppGradientBorder(modifier = Modifier.fillMaxWidth()) {
                AppMenuRow(title = "渐变描边卡", subtitle = "边缘锥形渐变缓慢流淌", icon = Icons.Rounded.Palette)
            }
            Spacer(Modifier.height(AppSpacing.Md))

            AppTypewriterText(text = "正在生成 tool_call → 执行 shell 构建…")
            Spacer(Modifier.height(AppSpacing.Md))

            AppScrambleText(text = "AGENT_RUN_0.0.0.2")
            Spacer(Modifier.height(AppSpacing.Md))

            AppSpotlightCard(modifier = Modifier.fillMaxWidth().height(112.dp)) {
                // 内容垂直居中：卡高 112dp 只有两行文案，顶对齐会留下大片空白且高光在底部孤悬。
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth()) {
                    Text("聚光高亮卡", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(AppSpacing.Xs))
                    Text("舞台高光沿卡片缓慢游弋", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(AppSpacing.Md))

            Box(Modifier.fillMaxWidth().height(88.dp)) {
                AppConfetti(Modifier.fillMaxSize())
                Column(
                    Modifier.align(Alignment.Center).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("发版成功", style = MaterialTheme.typography.titleMedium, color = AppColor.StatusSuccess)
                    Text("v0.0.0.2 · 庆祝彩带", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Section("分子组件族 · 时序 / 流程") {
            AppTimeline(
                items = listOf(
                    AppTimelineItem(title = "创建会话", subtitle = "初始化 AI Agent 上下文", time = "10:02", icon = Icons.Rounded.Add),
                    AppTimelineItem(title = "执行构建", subtitle = "assembleDebug 通过", time = "10:05", tone = AppTimelineTone.Success),
                    AppTimelineItem(title = "推送提交", subtitle = "feat(agent): 流式工具调用", time = "10:11", icon = Icons.Rounded.Code),
                    AppTimelineItem(title = "检测远端变更", subtitle = "合入前需解决冲突", time = "10:23", tone = AppTimelineTone.Danger),
                ),
            )
            AppProgressSteps(steps = listOf("解析", "授权", "执行", "完成"), currentIndex = stepIndex)
        }

        Section("分子组件族 · 标签 / 分页") {
            AppTagInput(
                tags = tags,
                onAdd = { if (it !in tags) tags = tags + it },
                onRemove = { tags = tags - it },
                placeholder = "输入标签后回车添加…",
            )
            AppPagination(
                page = page,
                pageCount = 9,
                onPageChange = { page = it },
            )
        }

        Section("分子组件族 · 快捷键 / 通知") {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
                AppKeyCombo(keys = listOf("⌘", "K"))
                AppKeyCombo(keys = listOf("Ctrl", "⇧", "P"))
                AppKeyCap(label = "Esc")
            }
            AppNotificationItem(
                title = "容器启动完成",
                body = "PRoot Alpine 已就绪，可执行终端命令。",
                time = "刚刚",
                icon = Icons.Rounded.Notifications,
                unread = true,
            )
            AppNotificationItem(
                title = "会员权限已更新",
                time = "5 分钟前",
                icon = Icons.Rounded.Settings,
            )
        }

        Section("分子组件族 · 数据可视化 / 文件") {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                AppMiniBarChart(
                    values = barData,
                    highlightIndex = 3,
                    modifier = Modifier.weight(1f).height(72.dp),
                )
                AppMiniBarChart(
                    values = barData,
                    barColor = AppColor.StatusSuccess,
                    modifier = Modifier.weight(1f).height(72.dp),
                )
            }
            AppFileCard(fileName = "README.md", fileSize = "4.1 KB", state = AppFileState.Ready)
            AppFileCard(
                fileName = "tokens.json",
                fileSize = "—",
                state = fileState,
                progress = fileProgress,
                icon = Icons.Rounded.Code,
            )
            AppFileCard(fileName = "secrets.local", fileSize = "12 B", state = AppFileState.Error)
        }

        Section("三态") {
            Box(Modifier.fillMaxWidth().size(96.dp), contentAlignment = Alignment.Center) {
                AppLoadingState()
            }
            AppEmptyState(
                icon = Icons.Rounded.Home,
                title = "暂无内容",
                description = "这是空态示例，展示空状态占位。",
            )
            AppCard(modifier = Modifier.fillMaxWidth()) {
                AppErrorState(message = "网络异常，请重试。")
            }
        }
    }

    if (showDialog) {
        AppConfirmDialog(
            visible = true,
            title = "删除会话？",
            message = "该操作不可撤销，会话及其历史将永久删除。",
            onDismiss = { showDialog = false },
            confirmText = "删除",
            onConfirm = { showDialog = false },
            tone = AppDialogTone.Danger,
        )
    }
    if (showAlertDialog) {
        AppAlertDialog(
            visible = true,
            title = "容器未启动",
            message = "执行 Shell 前请先在终端启动 PRoot 容器。",
            onDismiss = { showAlertDialog = false },
            tone = AppDialogTone.Warning,
        )
    }
    if (showPromptDialog) {
        AppPromptDialog(
            visible = true,
            title = "新建会话",
            onDismiss = { showPromptDialog = false },
            onConfirm = { showPromptDialog = false },
            placeholder = "给会话起个名字…",
            confirmText = "创建",
        )
    }
    if (showSelectionDialog) {
        AppSelectionDialog(
            visible = true,
            title = "移动到分组",
            options = listOf("会话", "收藏", "归档"),
            selectedIndex = singlePick,
            onSelect = { singlePick = it },
            onDismiss = { showSelectionDialog = false },
        )
    }
    if (showUpdateDialog) {
        AppUpdateDialog(
            visible = true,
            title = "发现新版本",
            version = "0.0.0.3",
            notes = listOf("重构滑扫操作系统", "新增列表菜单与弹窗组件族", "修复若干崩溃"),
            onDismiss = { showUpdateDialog = false },
            onUpdate = { showUpdateDialog = false },
            tone = AppDialogTone.Info,
        )
    }
    AppActionSheet(
        visible = showActionSheet,
        title = "对“minime-agent”执行",
        items = listOf(
            AppActionSheetItem(Icons.Rounded.Delete, "删除", danger = true),
            AppActionSheetItem(Icons.Rounded.Archive, "归档"),
        ),
        onItemClick = { showActionSheet = false },
        onDismiss = { showActionSheet = false },
    )
    // 补充弹窗类型渲染
    if (showFormDialog) {
        AppFormDialog(
            visible = true,
            title = "新建反馈",
            onDismiss = { showFormDialog = false },
            onConfirm = { _, _ -> showFormDialog = false },
            subjectLabel = "主题",
            bodyLabel = "详情",
            confirmText = "提交",
            tone = AppDialogTone.Info,
        )
    }
    if (showPermissionDialog) {
        AppPermissionDialog(
            visible = true,
            title = "开启通知权限？",
            message = "开启后我们会在构建完成、会话超时等关键节点提醒你，不会推送无关信息。",
            onDismiss = { showPermissionDialog = false },
            onAllow = { showPermissionDialog = false },
            permissionName = "通知权限",
            allowText = "允许",
            deniedText = "暂不",
        )
    }
    if (showSuccessDialog) {
        AppSuccessDialog(
            visible = true,
            title = "导出成功",
            message = "设计令牌已导出为打包产物。",
            detail = listOf("tokens.json", "AppTokens.kt", "style.css"),
            onDismiss = { showSuccessDialog = false },
            confirmText = "完成",
        )
    }
    if (showMultiSelectDialog) {
        AppMultiSelectDialog(
            visible = true,
            title = "选择批量导出字段",
            options = listOf("会话", "消费", "工具", "文件", "凭据"),
            selected = multiSelData,
            onToggle = { i -> multiSelData = if (i in multiSelData) multiSelData - i else multiSelData + i },
            onDismiss = { showMultiSelectDialog = false },
            onConfirm = { showMultiSelectDialog = false },
        )
    }
    // 命令面板：全屏浮层，须置于滚动内容之外的同级覆盖
    if (paletteOpen) {
        AppCommandPalette(
            visible = true,
            groups = listOf(
                AppCommandGroup("文件", listOf(
                    AppMenuAction("打开文件", Icons.Rounded.InsertDriveFile),
                    AppMenuAction("复制路径", Icons.Rounded.Code),
                    AppMenuAction("新建会话", Icons.Rounded.Add),
                )),
                AppCommandGroup("操作", listOf(
                    AppMenuAction("归档", Icons.Rounded.Archive),
                    AppMenuAction("删除", Icons.Rounded.Delete, danger = true),
                )),
            ),
            query = paletteQuery,
            onQueryChange = { paletteQuery = it },
            onSelect = { paletteOpen = false },
            onDismiss = { paletteOpen = false },
        )
    }
    // 阻塞加载遮罩：演示为 3.2 秒后自动收起
    LaunchedEffect(showLoadingOverlay) {
        if (showLoadingOverlay) {
            delay(3200)
            showLoadingOverlay = false
        }
    }
    // 阻塞遮罩必须包在全屏 Dialog 里，否则在滚动 Column 中只占当前视口、且不拦截穿透点击
    if (showLoadingOverlay) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* 阻塞遮罩不可点外/返回关闭，3.2s 后自动收起 */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            AppLoadingOverlay(
                visible = true,
                message = "正在同步工作区…",
            )
        }
    }
    // 倒计时防误弹窗：删除操作前强制读秒
    if (showCountdownDialog) {
        AppCountdownDialog(
            visible = true,
            title = "清空回收站",
            message = "回收站内的 12 个会话将被彻底清除，该操作无法撤销。",
            onDismiss = { showCountdownDialog = false },
            onConfirm = { showCountdownDialog = false },
            seconds = 3,
            confirmText = "清空",
            cancelText = "取消",
            tone = AppDialogTone.Danger,
        )
    }
}

/** iOS 简约分组：紧凑灰标题 + 白色圆角卡片分组（去顶部分隔线，以留白分层）。 */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.Md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Z0),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                content()
            }
        }
    }
}

/** 间距可视化：两个色块中间的留白宽度即该档令牌实际值。 */
@Composable
private fun SpacingBar(label: String, gap: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().primary, RoundedCornerShape(AppRadius.None)))
        Spacer(Modifier.width(gap))
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().accent, RoundedCornerShape(AppRadius.None)))
        Spacer(Modifier.width(AppSpacing.Sm))
        Text("$label · $gap", style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 圆角可视化：实块照搬令牌圆角，直观对比 Sm / Md / Lg / Pill。 */
@Composable
private fun RadiusTile(label: String, radius: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().primary, RoundedCornerShape(radius)))
        Text(label, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 阴影可视化：白卡片按令牌档位加阴影，直观对比 Z0 / Z1 / Z2 / Z4。 */
@Composable
private fun ElevationTile(label: String, elevation: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Box(
            Modifier
                .size(AppSizing.TouchTarget)
                .shadow(elevation, RoundedCornerShape(AppRadius.Md), clip = false)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(AppRadius.Md)),
        )
        Text(label, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 参数速查：大小/布局令牌的文本值。 */
@Composable
private fun MetricParam(name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = AppType.Caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(value, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ColorRow(list: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        list.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(color, shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.Md)),
                )
                Text(text = name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}