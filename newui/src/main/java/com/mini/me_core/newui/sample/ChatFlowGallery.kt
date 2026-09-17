package com.mini.me_core.newui.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.primitive.AppIconButton as AppCircleIconButton
import com.mini.me_core.newui.designsystem.component.AppApprovalChoice
import com.mini.me_core.newui.designsystem.component.AppAccordion
import com.mini.me_core.newui.designsystem.component.AppAttachmentCard
import com.mini.me_core.newui.designsystem.component.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.AppChatMarker
import com.mini.me_core.newui.designsystem.component.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppAcceptChangesBar
import com.mini.me_core.newui.designsystem.component.AppComposer
import com.mini.me_core.newui.designsystem.component.AppComposerMode
import com.mini.me_core.newui.designsystem.component.AppComposerReasoning
import com.mini.me_core.newui.designsystem.component.AppComposerSlashCommand
import com.mini.me_core.newui.designsystem.component.AppModelOption
import com.mini.me_core.newui.designsystem.component.AppModelPickerSheet
import com.mini.me_core.newui.designsystem.component.AppCitationCard
import com.mini.me_core.newui.designsystem.component.AppCitationSource
import com.mini.me_core.newui.designsystem.component.AppClarifyCard
import com.mini.me_core.newui.designsystem.component.AppCommitChip
import com.mini.me_core.newui.designsystem.component.AppDiffCard
import com.mini.me_core.newui.designsystem.component.AppErrorCard
import com.mini.me_core.newui.designsystem.component.AppFollowUpChips
import com.mini.me_core.newui.designsystem.component.AppModelBadge
import com.mini.me_core.newui.designsystem.component.AppTestResultCard
import com.mini.me_core.newui.designsystem.component.AppTurnSummaryBar
import com.mini.me_core.newui.designsystem.component.AppWebHit
import com.mini.me_core.newui.designsystem.component.AppWebSearchCard
import com.mini.me_core.newui.designsystem.component.AppDiffLine
import com.mini.me_core.newui.designsystem.component.AppDiffLineType
import com.mini.me_core.newui.designsystem.component.AppFileCard
import com.mini.me_core.newui.designsystem.component.AppFileState
import com.mini.me_core.newui.designsystem.component.AppGitStatusChip
import com.mini.me_core.newui.designsystem.component.AppIconButton
import com.mini.me_core.newui.designsystem.component.AppMcpAppCard
import com.mini.me_core.newui.designsystem.component.AppMcpAppState
import com.mini.me_core.newui.designsystem.component.AppMessageRow
import com.mini.me_core.newui.designsystem.component.AppMessageScroller
import com.mini.me_core.newui.designsystem.component.AppPlanCard
import com.mini.me_core.newui.designsystem.component.AppPlanState
import com.mini.me_core.newui.designsystem.component.AppPlanStep
import com.mini.me_core.newui.designsystem.component.AppPlanStepStatus
import com.mini.me_core.newui.designsystem.component.AppSkillCallCard
import com.mini.me_core.newui.designsystem.component.AppSkillCallState
import com.mini.me_core.newui.designsystem.component.AppTerminalLog
import com.mini.me_core.newui.designsystem.component.AppThinkingBlock
import com.mini.me_core.newui.designsystem.component.AppToolCallCard
import com.mini.me_core.newui.designsystem.component.AppToolCallState
import com.mini.me_core.newui.designsystem.component.AppToolChainStep
import com.mini.me_core.newui.designsystem.component.AppToolChainStepState
import com.mini.me_core.newui.designsystem.component.AppToolChainTimeline
import com.mini.me_core.newui.designsystem.component.AppToolSummaryCard
import com.mini.me_core.newui.designsystem.component.AppToolSummaryState
import com.mini.me_core.newui.designsystem.component.AppTodoCard
import com.mini.me_core.newui.designsystem.component.AppTodoItem
import com.mini.me_core.newui.designsystem.component.AppTodoStatus
import com.mini.me_core.newui.designsystem.component.AppTypingIndicator
import com.mini.me_core.newui.designsystem.layout.AppShell
import com.mini.me_core.newui.designsystem.theme.AppTheme
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 对话流完整演示页（样板页子页）：
 *
 * 与 [DesignGallery] 中「点一个按钮塞一种组件」的组件调试台不同，本页用一段有起承转合的
 * 多轮任务对话（"给后端加登录接口并跑通测试"），在剧情推进中把对话流的**全部分子组件**
 * 自然用一遍：日期/阶段标记、用户与 AI 气泡（Markdown/流式/失败重试）、打字指示、思考过程、
 * 计划审批、技能调用、MCP App、MCP 工具、工具链时间线、普通工具卡（流式输出/审批三选一/
 * 审批超时）、附件、终端日志、结果摘要。
 *
 * 演出方式：可重播的逐步推进——▶ 自动播放 / ⏵ 单步 / ⏩ 一键展开全部 / ↺ 重置；
 * 审批、计划、失败重试等节点是真实可交互的本地闭环（点击卡片按钮推进剧情）。
 */
@Composable
fun ChatFlowGallery(onNavigateBack: (() -> Unit)? = null) {
    val state = rememberChatFlow()
    var showIconGallery by remember { mutableStateOf(false) }
    AppTheme {
        AppShell(
            title = "AI 对话流 · 完整演示",
            onNavigateBack = onNavigateBack,
            topBarActions = {
                Text(
                    "图标",
                    style = MaterialTheme.typography.labelMedium,
                    color = appPalette().primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .clickable { showIconGallery = true }
                        .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Tiny),
                )
            },
            bottomBar = { FlowPlayerBar(state) },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppMessageScroller(
                    modifier = Modifier.fillMaxSize(),
                    newMessageKey = state.items.size,
                    onLoadHistory = if (!state.historyLoaded) ({ state.loadHistory() }) else null,
                    loadingHistory = state.loadingHistory,
                ) {
                    items(items = state.items.asReversed(), key = { it.key }) { item ->
                        FlowNode(item, state)
                    }
                }
                if (state.items.isEmpty()) {
                    EmptyHint()
                }
            }
        }
        if (showIconGallery) {
            AppIconGalleryScreen(onBack = { showIconGallery = false })
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            modifier = Modifier.padding(AppSpacing.Xl),
        ) {
            Text(
                text = "完整对话流演示",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = appPalette().ink,
            )
            Text(
                text = "底部「自动播放」连续演出，「单步」逐轮推进；\n审批与重试可直接在卡片上操作。",
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary,
            )
        }
    }
}

@Composable
private fun FlowPlayerBar(state: ChatFlowState) {
    val canAdvance = !state.busy && !state.finished
    Surface(color = appPalette().surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            // 输入框演示：固定示例状态，按钮仅占位交互，不接真实 Agent。
            var input by remember { mutableStateOf("") }
            var mode by remember { mutableStateOf(AppComposerMode.BUILD) }
            var reasoning by remember { mutableStateOf(AppComposerReasoning.MEDIUM) }
            var atts by remember { mutableStateOf<List<String>>(emptyList()) }
            var modelLabel by remember { mutableStateOf("Claude Sonnet") }
            var modelId by remember { mutableStateOf("claude-sonnet") }
            var showPicker by remember { mutableStateOf(false) }
            AppComposer(
                value = input,
                onValueChange = { input = it },
                attachments = atts,
                onRemoveAttachment = { atts = atts.toMutableList().apply { removeAt(it) } },
                mode = mode,
                onCycleMode = { mode = mode.next() },
                reasoning = reasoning,
                onCycleReasoning = { reasoning = reasoning.next() },
                modelLabel = modelLabel,
                onPickModel = { showPicker = true },
                tokenProgress = 0.62f,
                slashCommands = listOf(
                    AppComposerSlashCommand("/mode", "切换行为模式"),
                    AppComposerSlashCommand("/compress", "压缩对话上下文"),
                    AppComposerSlashCommand("/agent", "调整 Agent 配置"),
                    AppComposerSlashCommand("/playbook", "启动剧本"),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (showPicker) {
                AppModelPickerSheet(
                    providers = listOf(
                        "Anthropic" to listOf(
                            AppModelOption("claude-sonnet", "Claude Sonnet", "Anthropic", caption = "平衡速度与质量，日常默认", supportsVision = true, supportsTools = true),
                            AppModelOption("claude-opus", "Claude Opus", "Anthropic", badge = "强推理", caption = "复杂任务优先", supportsVision = true, supportsTools = true, supportsReasoning = true),
                            AppModelOption("claude-haiku", "Claude Haiku", "Anthropic", caption = "快速响应", supportsVision = true, supportsTools = true),
                        ),
                        "OpenAI" to listOf(
                            AppModelOption("gpt", "GPT-4.1", "OpenAI", caption = "通用", supportsVision = true, supportsTools = true, supportsReasoning = true),
                        ),
                    ),
                    selectedId = modelId,
                    onSelect = { modelLabel = it.name; modelId = it.id; showPicker = false },
                    onDismiss = { showPicker = false },
                )
            }
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = appPalette().labelSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            // 主操作独占一行：最宽、最高视觉权重，标签始终横排。
            AppIconButton(
                text = "自动播放",
                onClick = state::play,
                enabled = canAdvance,
                leadingIcon = {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // 次要操作一行：单步 / 展开全部 等宽分担，重置为低频操作收为纯图标钮。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    text = "单步",
                    onClick = state::step,
                    enabled = canAdvance,
                    variant = AppButtonVariant.FilledTonal,
                    leadingIcon = {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                )
                AppIconButton(
                    text = "展开全部",
                    onClick = state::expandAll,
                    enabled = !state.busy,
                    variant = AppButtonVariant.Outlined,
                    leadingIcon = {
                        Icon(Icons.Rounded.FastForward, contentDescription = null)
                    },
                    modifier = Modifier.weight(1f),
                )
                AppCircleIconButton(
                    onClick = state::reset,
                    icon = Icons.Rounded.RestartAlt,
                    enabled = state.turnIndex > 0 || state.items.isNotEmpty(),
                    contentDescription = "重置",
                )
            }
        }
    }
}

/** 单条对话节点：复用样板页同一套分子组件，新增打字指示与终端日志两类节点。 */
@Composable
private fun FlowNode(item: FlowItem, state: ChatFlowState) {
    // 统一水平边距：AppMessageScroller 默认仅带 vertical contentPadding，卡片会贴屏幕左右边缘。
    // 与底部操作条一致采用 AppSpacing.Lg 作为页面水平留白。
    Column(modifier = Modifier.padding(horizontal = AppSpacing.Lg)) {
        when (item) {
            is FlowItem.Marker -> AppChatMarker(
                text = item.text,
                kind = item.kind,
                running = item.running,
                tone = item.tone,
            )

            is FlowItem.Typing -> Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.Pill))
                        .background(appPalette().card)
                        .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
                ) {
                    AppTypingIndicator()
                }
            }

            is FlowItem.Tool -> AppToolCallCard(
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

            is FlowItem.McpApp -> AppMcpAppCard(
                title = item.title,
                state = item.state,
                serverPrefix = item.serverPrefix,
                resourceUri = item.resourceUri,
                onReload = item.onReload,
                onExpand = item.onExpand,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Skill -> AppSkillCallCard(
                name = item.name,
                args = item.args,
                state = item.state,
                description = item.description,
                durationMs = item.durationMs,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Thinking -> AppThinkingBlock(
                text = item.text,
                isStreaming = item.isStreaming,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Plan -> AppPlanCard(
                title = item.title,
                steps = item.steps,
                state = item.state,
                pendingSelection = item.pendingSelection,
                reason = item.reason,
                onApprove = item.onApprove,
                onRefine = item.onRefine,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Attachment -> AppAttachmentCard(
                fileName = item.fileName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                containerPath = item.containerPath,
                isImage = item.isImage,
                onClick = item.onClick,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.ToolChain -> AppToolChainTimeline(
                steps = item.steps,
                label = item.label,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.ToolSummary -> AppToolSummaryCard(
                text = item.text,
                state = item.state,
                toolCount = item.toolCount,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.ChangedFiles -> {
                var expanded by remember { mutableStateOf(false) }
                AppAccordion(
                    title = "本次改动的文件",
                    subtitle = "${item.files.size} 个文件",
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // 展开后最多露出约 3 张，超出在卡片内部纵向滚动，不把整屏撑长。
                    Column(
                        modifier = Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        item.files.forEach { f ->
                            AppFileCard(
                                fileName = f.name,
                                fileSize = f.detail,
                                state = AppFileState.Downloaded,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(AppSpacing.Sm))
                        }
                    }
                }
            }

            is FlowItem.Terminal -> AppTerminalLog(modifier = Modifier.fillMaxWidth())

            is FlowItem.Diff -> AppDiffCard(
                filePath = item.filePath,
                additions = item.additions,
                deletions = item.deletions,
                lines = item.lines,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Todo -> AppTodoCard(
                items = item.items,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Citation -> AppCitationCard(
                sources = item.sources,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.GitChip -> AppGitStatusChip(
                branch = item.branch,
                dirtyCount = item.dirtyCount,
                modifier = Modifier,
            )

            is FlowItem.TestResult -> AppTestResultCard(
                passed = item.passed,
                failed = item.failed,
                onRetry = { /* 演示：真实接入时由 Agent 重跑测试 */ },
                onOpenFailure = { /* 演示：真实接入时跳转对应失败用例 */ },
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.ErrorCard -> AppErrorCard(
                title = item.title,
                location = item.location,
                message = item.message,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.FollowUps -> AppFollowUpChips(
                suggestions = item.suggestions,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.TurnSummary -> AppTurnSummaryBar(
                filesChanged = item.filesChanged,
                toolsRun = item.toolsRun,
                duration = item.duration,
                tokens = item.tokens,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.AcceptBar -> AppAcceptChangesBar(
                changedCount = item.changedCount,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Clarify -> AppClarifyCard(
                question = item.question,
                options = item.options,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.Commit -> AppCommitChip(
                hash = item.hash,
                modifier = Modifier,
            )

            is FlowItem.WebSearch -> AppWebSearchCard(
                hits = item.hits,
                modifier = Modifier.fillMaxWidth(),
            )

            is FlowItem.ModelTag -> AppModelBadge(
                model = item.model,
                modifier = Modifier,
            )

            // 消息组：头像/姓名行 → 思考过程（头像下方、与气泡同列）→ 气泡 → 附件（用户气泡下方）。
            // 三者同属一个 Msg 节点，不再拆成独立列表项，避免视觉分离。
            is FlowItem.Msg -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
            ) {
                AppMessageRow(
                    text = item.text,
                    state = item.state,
                    isUser = item.isUser,
                    avatarLabel = if (item.isUser) "你" else "AI",
                    name = if (item.isUser) "你" else "MiniMe Agent",
                    timestamp = item.ts,
                    grouped = item.grouped,
                    leadingContent = if (!item.isUser && !item.thinking.isNullOrEmpty()) {
                        {
                            AppThinkingBlock(
                                text = item.thinking,
                                isStreaming = item.thinkingStreaming,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        null
                    },
                    onCopy = { /* 演示占位：写入剪贴板 */ },
                    onRetry = item.onRetry,
                    onDelete = { state.items.removeAll { it.key == item.key } },
                    onRegenerate = item.onRegenerate,
                    onStop = item.onStop,
                    swipeEnabled = true,
                    swipeIndex = item.key,
                    swipeExpandedIndex = state.swipeExpanded,
                    onSwipeExpanded = { state.swipeExpanded = it },
                )
                if (item.attachments.isNotEmpty()) {
                    item.attachments.forEach { att ->
                        AppAttachmentCard(
                            fileName = att.fileName,
                            mimeType = att.mimeType,
                            sizeBytes = att.sizeBytes,
                            containerPath = att.containerPath,
                            isImage = att.isImage,
                            onClick = att.onClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================================
// 演示数据模型（与 DesignGallery 的私有 ChatItem 同构，额外含打字指示 / 终端日志节点）
// =====================================================================================

private sealed interface FlowItem {
    val key: Int

    data class Msg(
        override val key: Int,
        val text: String,
        val state: AppChatMessageState,
        val isUser: Boolean,
        val grouped: Boolean = false,
        val ts: String = "",
        val onRetry: (() -> Unit)? = null,
        val onRegenerate: (() -> Unit)? = null,
        val onStop: (() -> Unit)? = null,
        val attachments: List<AttachmentData> = emptyList(),
        val thinking: String? = null,
        val thinkingStreaming: Boolean = false,
    ) : FlowItem

    data class AttachmentData(
        val fileName: String,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val containerPath: String? = null,
        val isImage: Boolean = false,
        val onClick: (() -> Unit)? = null,
    )

    data class Marker(
        override val key: Int,
        val text: String,
        val kind: AppChatMarkerKind = AppChatMarkerKind.Tool,
        val running: Boolean = false,
        val tone: Color = AppColor.StatusSuccess,
    ) : FlowItem

    data class Typing(override val key: Int) : FlowItem

    data class Terminal(override val key: Int) : FlowItem

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
    ) : FlowItem

    data class McpApp(
        override val key: Int,
        val title: String,
        val state: AppMcpAppState,
        val serverPrefix: String? = null,
        val resourceUri: String? = null,
        val onReload: (() -> Unit)? = null,
        val onExpand: (() -> Unit)? = null,
    ) : FlowItem

    data class Skill(
        override val key: Int,
        val name: String,
        val state: AppSkillCallState,
        val args: String? = null,
        val description: String? = null,
        val durationMs: Long? = null,
    ) : FlowItem

    data class Thinking(
        override val key: Int,
        val text: String,
        val isStreaming: Boolean = false,
    ) : FlowItem

    data class Plan(
        override val key: Int,
        val title: String,
        val steps: List<AppPlanStep>,
        val state: AppPlanState,
        val pendingSelection: String? = null,
        val reason: String? = null,
        val onApprove: (() -> Unit)? = null,
        val onRefine: (() -> Unit)? = null,
    ) : FlowItem

    data class Attachment(
        override val key: Int,
        val fileName: String,
        val mimeType: String? = null,
        val sizeBytes: Long? = null,
        val containerPath: String? = null,
        val isImage: Boolean = false,
        val onClick: (() -> Unit)? = null,
    ) : FlowItem

    data class ToolChain(
        override val key: Int,
        val steps: List<AppToolChainStep>,
        val label: String = "工具链",
    ) : FlowItem

    data class ToolSummary(
        override val key: Int,
        val text: String,
        val state: AppToolSummaryState = AppToolSummaryState.Done,
        val toolCount: Int = 1,
    ) : FlowItem

    /** 本次改动的文件（结尾折叠卡）：折叠态只露一行标题，展开后逐张出文件卡。 */
    data class ChangedFiles(
        override val key: Int,
        val files: List<ChangedFile>,
    ) : FlowItem

    data class ChangedFile(
        val name: String,
        val detail: String,
    )

    /** 文件 Diff：路径 + 增删行统计 + 行级 diff。 */
    data class Diff(
        override val key: Int,
        val filePath: String,
        val additions: Int,
        val deletions: Int,
        val lines: List<AppDiffLine>,
    ) : FlowItem

    /** 实时任务清单。 */
    data class Todo(
        override val key: Int,
        val items: List<AppTodoItem>,
    ) : FlowItem

    /** 引用来源。 */
    data class Citation(
        override val key: Int,
        val sources: List<AppCitationSource>,
    ) : FlowItem

    /** Git 分支 chip。 */
    data class GitChip(
        override val key: Int,
        val branch: String,
        val dirtyCount: Int,
    ) : FlowItem

    /** 测试结果卡。 */
    data class TestResult(
        override val key: Int,
        val passed: Int,
        val failed: List<String>,
    ) : FlowItem

    /** 编译 / 运行错误卡。 */
    data class ErrorCard(
        override val key: Int,
        val title: String,
        val location: String?,
        val message: String,
    ) : FlowItem

    /** 建议追问 chips。 */
    data class FollowUps(
        override val key: Int,
        val suggestions: List<String>,
    ) : FlowItem

    /** 回合总结条。 */
    data class TurnSummary(
        override val key: Int,
        val filesChanged: Int,
        val toolsRun: Int,
        val duration: String,
        val tokens: String,
    ) : FlowItem

    /** 批量接受/拒绝条。 */
    data class AcceptBar(
        override val key: Int,
        val changedCount: Int,
    ) : FlowItem

    /** 反问/澄清卡。 */
    data class Clarify(
        override val key: Int,
        val question: String,
        val options: List<String>,
    ) : FlowItem

    /** 提交/回滚 chip。 */
    data class Commit(
        override val key: Int,
        val hash: String,
    ) : FlowItem

    /** 联网搜索命中。 */
    data class WebSearch(
        override val key: Int,
        val hits: List<AppWebHit>,
    ) : FlowItem

    /** 模型徽章。 */
    data class ModelTag(
        override val key: Int,
        val model: String,
    ) : FlowItem
}

// =====================================================================================
// 播放器状态机：自动播放 / 单步 / 展开全部 / 重置；审批与重试为可交互 gate
// =====================================================================================

private enum class FlowMode { Auto, Manual, Instant }

@Composable
private fun rememberChatFlow(): ChatFlowState {
    val scope = rememberCoroutineScope()
    return remember { ChatFlowState(scope) }
}

private class ChatFlowState(val scope: CoroutineScope) {
    val items = mutableStateListOf<FlowItem>()

    var seq by mutableIntStateOf(0)
        private set
    var turnIndex by mutableIntStateOf(0)
        private set
    var mode by mutableStateOf(FlowMode.Auto)
        private set
    var busy by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set
    var swipeExpanded by mutableStateOf<Int?>(null)
    var loadingHistory by mutableStateOf(false)
        private set
    var historyLoaded by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var histKey = -100

    val totalTurns get() = flowTurns.size

    fun statusText(): String = when {
        finished && turnIndex >= totalTurns -> "演示完成 · 共 $totalTurns 轮，可「重置」重播"
        busy && mode == FlowMode.Auto -> "自动播放中… 第 ${(turnIndex + 1).coerceAtMost(totalTurns)} / $totalTurns 轮"
        busy -> "第 ${(turnIndex + 1).coerceAtMost(totalTurns)} / $totalTurns 轮 · 请在卡片上完成操作"
        items.isEmpty() -> "就绪 · 共 $totalTurns 轮"
        else -> "已演 $turnIndex / $totalTurns 轮"
    }

    // ---- 基础原语 ----
    fun nextKey(): Int = ++seq

    fun put(item: FlowItem) {
        items.add(item)
    }

    internal inline fun <reified T : FlowItem> patch(key: Int, transform: (T) -> T) {
        val idx = items.indexOfFirst { it.key == key }
        val cur = items.getOrNull(idx)
        if (cur is T) items[idx] = transform(cur)
    }

    fun removeKey(key: Int) {
        items.removeAll { it.key == key }
    }

    suspend fun tick(ms: Long) {
        if (mode != FlowMode.Instant) delay(ms)
    }

    /** 交互检查点：自动/单步等待用户在卡片上操作；展开全部时取默认决策。 */
    suspend fun <T> gate(deferred: CompletableDeferred<T>, instantDefault: () -> T): T =
        if (mode == FlowMode.Instant) instantDefault() else deferred.await()

    suspend fun typewrite(
        full: String,
        chunk: Int = 3,
        perMs: Long = 15L,
        onText: (String) -> Unit,
    ) {
        if (mode == FlowMode.Instant) {
            onText(full)
            return
        }
        var n = 0
        while (n <= full.length) {
            onText(full.take(n))
            n += chunk
            delay(perMs)
        }
    }

    /** 先显示打字指示，停顿后移除再演后续（展开全部时跳过指示）。 */
    suspend fun withTyping(waitMs: Long = 650L, block: suspend () -> Unit) {
        if (mode == FlowMode.Instant) {
            block()
            return
        }
        val k = nextKey()
        put(FlowItem.Typing(k))
        tick(waitMs)
        removeKey(k)
        block()
    }

    suspend fun userSays(text: String, ts: String) {
        put(FlowItem.Msg(nextKey(), text, AppChatMessageState.Complete, isUser = true, ts = ts))
    }

    suspend fun aiReply(
        full: String,
        ts: String,
        chunk: Int = 3,
        perMs: Long = 14L,
        onStop: (() -> Unit)? = null,
        onRegenerate: (() -> Unit)? = null,
    ) {
        val k = nextKey()
        put(
            FlowItem.Msg(
                k, "", AppChatMessageState.Streaming, isUser = false, ts = ts,
                onStop = onStop ?: {
                    patch<FlowItem.Msg>(k) { it.copy(state = AppChatMessageState.Complete, onStop = null) }
                },
                onRegenerate = onRegenerate ?: {
                    patch<FlowItem.Msg>(k) { it.copy(text = full, state = AppChatMessageState.Complete) }
                },
            ),
        )
        typewrite(full, chunk, perMs) { s -> patch<FlowItem.Msg>(k) { it.copy(text = s) } }
        patch<FlowItem.Msg>(k) { it.copy(state = AppChatMessageState.Complete, onStop = null) }
    }

    // ---- 播放控制 ----
    fun play() = start(FlowMode.Auto, continuous = true)

    fun step() = start(FlowMode.Manual, continuous = false)

    private fun start(m: FlowMode, continuous: Boolean) {
        if (busy || finished) return
        mode = m
        busy = true
        job = scope.launch {
            val from = turnIndex
            val end = if (continuous) totalTurns else (from + 1).coerceAtMost(totalTurns)
            for (i in from until end) {
                flowTurns[i]()
                turnIndex = i + 1
            }
            if (turnIndex >= totalTurns) finished = true
            busy = false
        }
    }

    fun reset() {
        job?.cancel()
        items.clear()
        seq = 0
        turnIndex = 0
        finished = false
        busy = false
        swipeExpanded = null
        loadingHistory = false
        historyLoaded = false
        histKey = -100
    }

    fun expandAll() {
        if (busy) return
        job?.cancel()
        items.clear()
        seq = 0
        swipeExpanded = null
        mode = FlowMode.Instant
        busy = true
        turnIndex = totalTurns
        finished = true
        job = scope.launch {
            flowTurns.forEach { it() }
            busy = false
        }
    }

    fun loadHistory() {
        if (historyLoaded || loadingHistory) return
        loadingHistory = true
        scope.launch {
            delay(500)
            val older = listOf(
                FlowItem.Marker(histKey--, "昨天 · 18:20", kind = AppChatMarkerKind.Date),
                FlowItem.Msg(
                    histKey--,
                    "上次我们把 `feature/auth` 的路由骨架搭好了，今天接着补登录。",
                    AppChatMessageState.Complete,
                    isUser = true,
                    ts = "昨天 18:20",
                ),
                FlowItem.Msg(
                    histKey--,
                    "好的，路由骨架已经在 `feature/auth` 上了。那我接着补登录接口和 token 鉴权，跑完测试再把结果给你。",
                    AppChatMessageState.Complete,
                    isUser = false,
                    ts = "昨天 18:21",
                ),
            )
            items.addAll(0, older)
            historyLoaded = true
            loadingHistory = false
        }
    }
}

// =====================================================================================
// 剧情脚本：围绕「给后端加登录接口（token 鉴权）并跑通测试」的多轮任务
// =====================================================================================

private val flowTurns: List<suspend ChatFlowState.() -> Unit> = listOf(
    // T0 开场：用户提需求（需求文档与原型已在上下文里，不在对话流顶部铺文件卡片；
    // 改动产物统一收敛到结尾的「本次改动的文件」折叠卡）。
    {
        put(FlowItem.Marker(nextKey(), "今天 · 10:02", kind = AppChatMarkerKind.Date))
        put(
            FlowItem.Msg(
                nextKey(),
                "帮我给后端加一个**登录接口**，要带 token 鉴权，最后把测试跑通。",
                AppChatMessageState.Complete,
                isUser = true,
                ts = "10:02",
            ),
        )
    },
    // T1 思考过程 + T2 正文回复合并为同一 AI 消息组：先流式输出思考过程，再流式输出正文
    {
        val k = nextKey()
        put(
            FlowItem.Msg(
                k,
                "",
                AppChatMessageState.Streaming,
                isUser = false,
                ts = "10:03",
                thinking = "",
                thinkingStreaming = true,
            ),
        )
        val reasoning = "用户要新增登录接口并带 token 鉴权。\n\n我需要先确认现有 `feature/auth` 的路由与依赖注入方式，" +
            "再决定 token 用无状态 JWT 还是服务端会话；随后按「路由 → Service → 鉴权中间件 → 测试」推进，" +
            "最后跑编译与单测验证。附件里的需求文档先读一遍，对齐字段与错误码。"
        typewrite(reasoning, chunk = 4, perMs = 13L) { s ->
            patch<FlowItem.Msg>(k) { it.copy(thinking = s) }
        }
        patch<FlowItem.Msg>(k) { it.copy(thinkingStreaming = false) }
        val reply = "收到。我先读一下现有鉴权代码和需求文档，再给你一份实现计划确认。"
        typewrite(reply, chunk = 3, perMs = 14L) { s ->
            patch<FlowItem.Msg>(k) { it.copy(text = s) }
        }
        patch<FlowItem.Msg>(k) { it.copy(state = AppChatMessageState.Complete) }
        put(FlowItem.Marker(nextKey(), "读取鉴权模块 · feature/auth", running = true))
    },
    // T3 计划审批（gate：批准 / 继续细化）
    {
        val k = nextKey()
        var refined = false
        while (true) {
            val decision = CompletableDeferred<PlanDecision>()
            patchOrPutPlan(
                k,
                refined = refined,
                onApprove = { decision.complete(PlanDecision.Approve) },
                onRefine = { decision.complete(PlanDecision.Refine) },
            )
            if (gate(decision) { PlanDecision.Approve } == PlanDecision.Approve) break
            refined = true
        }
        patch<FlowItem.Plan>(k) { it.copy(state = AppPlanState.InProgress, onApprove = null, onRefine = null) }
        tick(1200)
        patch<FlowItem.Plan>(k) { plan ->
            plan.copy(
                state = AppPlanState.Approved,
                steps = plan.steps.map { step ->
                    if (step.status == AppPlanStepStatus.Pending) {
                        step.copy(status = AppPlanStepStatus.InProgress)
                    } else {
                        step
                    }
                },
            )
        }
    },
    // T3.5 进入工作分支 + 实时任务清单（Pending→Running→Done 逐条推进）
    {
        put(FlowItem.GitChip(nextKey(), branch = "feature/auth-login", dirtyCount = 0))
        val tk = nextKey()
        put(
            FlowItem.Todo(
                tk,
                items = listOf(
                    AppTodoItem("读取项目结构与鉴权现状", AppTodoStatus.Running),
                    AppTodoItem("新增 POST /auth/login 路由", AppTodoStatus.Pending),
                    AppTodoItem("实现 JWT 签发与校验中间件", AppTodoStatus.Pending),
                    AppTodoItem("补单测并跑通", AppTodoStatus.Pending),
                ),
            ),
        )
        tick(900)
        patch<FlowItem.Todo>(tk) {
            it.copy(items = it.items.mapIndexed { i, s ->
                when (i) {
                    0 -> s.copy(status = AppTodoStatus.Done)
                    1 -> s.copy(status = AppTodoStatus.Running)
                    else -> s
                }
            })
        }
        tick(900)
        patch<FlowItem.Todo>(tk) {
            it.copy(items = it.items.mapIndexed { i, s ->
                when (i) {
                    1 -> s.copy(status = AppTodoStatus.Done)
                    2 -> s.copy(status = AppTodoStatus.Running)
                    else -> s
                }
            })
        }
    },
    // T4 技能调用：代码审查
    {
        val k = nextKey()
        put(
            FlowItem.Skill(
                key = k,
                name = "review-code",
                args = "--scope feature/auth",
                state = AppSkillCallState.Running,
                description = "审查登录接口改动：检查 JWT 签发/校验、密钥存放、鉴权中间件顺序与错误处理，并给出建议。",
            ),
        )
        tick(1000)
        patch<FlowItem.Skill>(k) { it.copy(state = AppSkillCallState.Success, durationMs = 1000) }
    },
    // T5 MCP App：构建耗时分析沙箱卡（可点重新加载）
    {
        val k = nextKey()
        fun reload() {
            patch<FlowItem.McpApp>(k) { it.copy(state = AppMcpAppState.Loading) }
            scope.launch {
                delay(600)
                patch<FlowItem.McpApp>(k) { it.copy(state = AppMcpAppState.Ready) }
            }
        }
        put(
            FlowItem.McpApp(
                key = k,
                title = "登录接口构建耗时分析",
                state = AppMcpAppState.Loading,
                serverPrefix = "github",
                resourceUri = "ui://analytics/auth-build-duration",
                onReload = { reload() },
                onExpand = { },
            ),
        )
        tick(900)
        patch<FlowItem.McpApp>(k) { it.copy(state = AppMcpAppState.Ready) }
    },
    // T6 工具链时间线：读结构 → 写接口 → 编译 → 测试
    {
        val k = nextKey()
        put(
            FlowItem.ToolChain(
                key = k,
                label = "工具链",
                steps = listOf(
                    AppToolChainStep("读取项目结构", summary = "scan feature/auth", state = AppToolChainStepState.Success, durationMs = 320),
                    AppToolChainStep("新增登录路由", summary = "write AuthApi.kt", state = AppToolChainStepState.Success, durationMs = 540),
                    AppToolChainStep("实现 token 鉴权", summary = "edit AuthMiddleware", state = AppToolChainStepState.Success, durationMs = 760),
                    AppToolChainStep("编译并运行测试", summary = "./gradlew test", state = AppToolChainStepState.Running),
                ),
            ),
        )
        tick(1500)
        patch<FlowItem.ToolChain>(k) { chain ->
            chain.copy(
                steps = chain.steps.mapIndexed { index, step ->
                    if (index == chain.steps.lastIndex) {
                        step.copy(state = AppToolChainStepState.Success, durationMs = 1320)
                    } else {
                        step
                    }
                },
            )
        }
    },
    // T7 MCP 工具：经 github server 拉取相关 issue 参考
    {
        val k = nextKey()
        put(
            FlowItem.Tool(
                key = k,
                title = "列出会话文件",
                serverPrefix = "github",
                state = AppToolCallState.Running,
                input = """{"query": "repo:minime/minime-core issues/auth-token"}""",
            ),
        )
        tick(1200)
        patch<FlowItem.Tool>(k) {
            it.copy(
                state = AppToolCallState.Success,
                durationMs = 1200,
                output = """{"total": 2, "items": ["auth-token-draft.md", "error-codes.md"]}""",
            )
        }
    },
    // T7.5 联网检索 JWT 最佳实践 → 引用来源卡
    {
        put(
            FlowItem.Citation(
                nextKey(),
                sources = listOf(
                    AppCitationSource(
                        title = "JWT 最佳实践 · OWASP",
                        url = "https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html",
                        snippet = "使用强签名算法（RS256/ES256），禁止 none；密钥长度充足并定期轮换。",
                    ),
                    AppCitationSource(
                        title = "Stateless JWT 鉴权实践",
                        url = "https://example.dev/blog/jwt-vs-session",
                        snippet = "无状态 JWT 适合横向扩展；登出/吊销需配合短期 access token + 黑名单。",
                    ),
                ),
            ),
        )
        tick(600)
    },
    // T8 普通工具：读取现有鉴权文件（流式输出 → 成功）
    {
        val k = nextKey()
        put(
            FlowItem.Tool(
                key = k,
                title = "读取文件",
                summary = "feature/auth/AuthApi.kt",
                state = AppToolCallState.Running,
                input = """{"path": "feature/auth/AuthApi.kt"}""",
                streamOutput = "// routing: /auth/login (not found)",
            ),
        )
        tick(800)
        patch<FlowItem.Tool>(k) {
            it.copy(streamOutput = "// routing: /auth/login (not found)\n// TODO: add JWT verify middleware")
        }
        tick(800)
        patch<FlowItem.Tool>(k) {
            it.copy(
                state = AppToolCallState.Success,
                durationMs = 1600,
                streamOutput = null,
                output = """{"lines": 86, "hasMiddleware": false}""",
            )
        }
    },
    // T9 普通工具：执行编译命令（流式输出 → 成功）
    {
        val k = nextKey()
        put(
            FlowItem.Tool(
                key = k,
                title = "执行命令",
                summary = "./gradlew :app:compileDebugKotlin",
                state = AppToolCallState.Running,
                input = """{"command": "./gradlew :app:compileDebugKotlin", "cwd": "/workspace"}""",
                streamOutput = "> Task :app:compileDebugKotlin",
            ),
        )
        tick(900)
        patch<FlowItem.Tool>(k) {
            it.copy(streamOutput = "> Task :app:compileDebugKotlin\n> Task :app:compileDebugKotlin UP-TO-DATE")
        }
        tick(900)
        patch<FlowItem.Tool>(k) {
            it.copy(
                state = AppToolCallState.Success,
                durationMs = 1800,
                streamOutput = null,
                output = """{"exitCode": 0, "tookMs": 1800}""",
            )
        }
    },
    // T10 终端日志（自驱动控制台）
    {
        put(FlowItem.Terminal(nextKey()))
        tick(900)
    },
    // T11 失败气泡 → 等待用户点重试 → 流式恢复成功（gate）
    {
        val k = nextKey()
        val retried = CompletableDeferred<Unit>()
        put(
            FlowItem.Msg(
                key = k,
                text = "连接测试 Provider 时超时了，登录链路的集成测试没能跑完，请重试。",
                state = AppChatMessageState.Error,
                isUser = false,
                ts = "10:09",
                onRetry = { retried.complete(Unit) },
            ),
        )
        gate(retried) { }
        patch<FlowItem.Msg>(k) { it.copy(state = AppChatMessageState.Streaming, text = "", onRetry = null) }
        val ok = "重试成功，已重新建立连接。`AuthApiTest` 与 `TokenMiddlewareTest` 全部通过：\n\n" +
            "- 登录成功签发 JWT（200）\n- 错误密码返回 401\n- 过期/伪造 token 返回 401"
        typewrite(ok, chunk = 4, perMs = 15L) { s -> patch<FlowItem.Msg>(k) { it.copy(text = s) } }
        patch<FlowItem.Msg>(k) { it.copy(state = AppChatMessageState.Complete) }
    },
    // T12 审批超时（Intervention 降级终态，静态警示）
    {
        put(
            FlowItem.Tool(
                key = nextKey(),
                title = "执行命令",
                summary = "rm -rf ./build/auth-tmp",
                state = AppToolCallState.AwaitingApproval,
                input = """{"command": "rm -rf ./build/auth-tmp", "force": true}""",
                approvalHint = "该命令将删除鉴权模块的临时构建目录",
                approvalExpired = true,
            ),
        )
    },
    // T13 部署命令人工审批（gate：拒绝 / 本次允许 / 始终允许并记忆）
    {
        val k = nextKey()
        val choice = CompletableDeferred<AppApprovalChoice>()
        put(
            FlowItem.Tool(
                key = k,
                title = "执行命令",
                summary = "curl -X POST https://api.example.com/deploy/auth",
                state = AppToolCallState.AwaitingApproval,
                input = """{"command": "curl -X POST https://api.example.com/deploy/auth"}""",
                approvalHint = "该命令将向生产环境部署鉴权服务",
                onChoice = { choice.complete(it) },
            ),
        )
        when (gate(choice) { AppApprovalChoice.Once }) {
            AppApprovalChoice.Reject -> patch<FlowItem.Tool>(k) {
                it.copy(
                    state = AppToolCallState.Error,
                    summary = "已拒绝执行 · 用户取消",
                    approvalHint = null,
                    onChoice = null,
                )
            }

            AppApprovalChoice.Once -> {
                patch<FlowItem.Tool>(k) {
                    it.copy(state = AppToolCallState.Running, approvalHint = null, onChoice = null)
                }
                tick(1300)
                patch<FlowItem.Tool>(k) {
                    it.copy(
                        state = AppToolCallState.Success,
                        durationMs = 1300,
                        output = """{"deployId": "auth-20260916-01", "status": "ok"}""",
                    )
                }
            }

            AppApprovalChoice.Always -> {
                patch<FlowItem.Tool>(k) {
                    it.copy(
                        state = AppToolCallState.Success,
                        durationMs = 820,
                        output = """{"deployId": "auth-20260916-02", "status": "ok"}""",
                        approvalHint = null,
                        onChoice = null,
                    )
                }
                put(
                    FlowItem.Tool(
                        key = nextKey(),
                        title = "执行命令",
                        summary = "curl -X POST https://api.example.com/rollback/auth",
                        state = AppToolCallState.AwaitingApproval,
                        input = """{"command": "curl -X POST https://api.example.com/rollback/auth"}""",
                        approvalHint = "同类部署命令已记忆为始终允许",
                        approvalRemembered = true,
                    ),
                )
            }
        }
    },
    // T14 结果摘要（流式总结 → 完成）
    {
        val k = nextKey()
        put(FlowItem.ToolSummary(k, "", state = AppToolSummaryState.Summarizing, toolCount = 6))
        val summary = "已按计划完成 6 次工具调用：新增登录路由与 JWT 鉴权中间件，编译 0 错误，" +
            "鉴权相关单测全部通过，鉴权服务已部署到生产。"
        typewrite(summary, chunk = 3, perMs = 15L) { s -> patch<FlowItem.ToolSummary>(k) { it.copy(text = s) } }
        patch<FlowItem.ToolSummary>(k) { it.copy(state = AppToolSummaryState.Done) }
    },
    // T14.5 挑一个核心文件展示行级 diff
    {
        put(
            FlowItem.Diff(
                nextKey(),
                filePath = "feature/auth/TokenMiddleware.kt",
                additions = 24,
                deletions = 6,
                lines = listOf(
                    AppDiffLine(AppDiffLineType.Context, "fun install(pipeline: Routing) {"),
                    AppDiffLine(AppDiffLineType.Context, "    pipeline.intercept(Call::respond)"),
                    AppDiffLine(AppDiffLineType.Remove, "-    pipeline.send(\"no auth\")"),
                    AppDiffLine(AppDiffLineType.Add, "+    val token = call.request.bearerAuthToken()"),
                    AppDiffLine(AppDiffLineType.Add, "+    if (token == null) return call.respondError(401, \"AUTH_MISSING\")"),
                    AppDiffLine(AppDiffLineType.Add, "+    if (!jwt.verify(token)) return call.respondError(401, \"AUTH_INVALID\")"),
                    AppDiffLine(AppDiffLineType.Context, "}"),
                ),
            ),
        )
        tick(500)
    },
    // T15 打字指示 → 最终 Markdown 长回复
    {
        withTyping {
            aiReply(
                """## 登录接口已完成 ✅

鉴权采用**无状态 JWT**，接入在路由中间件层，业务代码无侵入。

### 接口约定

| 项 | 值 |
| --- | --- |
| 路径 | `POST /auth/login` |
| 入参 | `{ username, password }` |
| 成功 | `200` + `{ token, expiresIn }` |
| 失败 | `401` + 统一错误码 |

### 本次改动

1. `AuthApi.kt`：新增登录路由与参数校验
2. `TokenMiddleware.kt`：签发/校验 JWT，拦截未授权请求
3. `AuthApiTest.kt` / `TokenMiddlewareTest.kt`：覆盖成功、错密、过期、伪造四类用例

```kotlin
post("/auth/login") {
    val token = authService.login(req.username, req.password)
        ?: return@post call.respondError(401, "AUTH_INVALID")
    call.respond(LoginResponse(token, expiresIn = 3600))
}
```

> 密钥通过环境变量注入，未硬编码；部署前请确认生产环境的 `JWT_SECRET` 已配置。

需要我把刷新 token（refresh token）流程也补上吗？""",
                "10:14",
                chunk = 4,
                perMs = 10L,
            )
        }
    },
    // T16 结尾：折叠卡收拢本次改动的文件，展开才逐张出现改动文件卡片（不在开头铺文件）
    {
        put(
            FlowItem.ChangedFiles(
                key = nextKey(),
                files = listOf(
                    FlowItem.ChangedFile("AuthApi.kt", "新增登录路由与参数校验 · +120 行"),
                    FlowItem.ChangedFile("TokenMiddleware.kt", "JWT 签发/校验中间件 · +86 行"),
                    FlowItem.ChangedFile("AuthApiTest.kt", "覆盖成功/错密用例 · +64 行"),
                    FlowItem.ChangedFile("TokenMiddlewareTest.kt", "覆盖过期/伪造用例 · +58 行"),
                ),
            ),
        )
    },
    // 扩展组件展示轮：测试失败 → 修复 → 全绿 → 总结/提交/建议追问。
    {
        delay(900)
        put(
            FlowItem.Clarify(
                key = nextKey(),
                question = "登录失败时的错误提示要多详细？",
                options = listOf("统一模糊提示", "区分账号/密码错误"),
            ),
        )
        delay(1200)
        put(
            FlowItem.WebSearch(
                key = nextKey(),
                hits = listOf(
                    AppWebHit("OWASP 认证最佳实践", "cheatsheetseries.owasp.org", "密码勿明文存储，登录失败应使用泛化提示以降低枚举风险。"),
                    AppWebHit("JWT 过期与刷新设计", "auth0.com", "access token 短期有效，配合 refresh token 轮换。"),
                ),
            ),
        )
        delay(1100)
        put(FlowItem.ModelTag(key = nextKey(), model = "深度模型"))
        delay(800)
        put(FlowItem.TestResult(key = nextKey(), passed = 18, failed = listOf("AuthApiTest 登录失败用例期望 401 实际 200")))
        delay(1100)
        put(
            FlowItem.ErrorCard(
                key = nextKey(),
                title = "AuthApiTest 未通过",
                location = "AuthApiTest.kt:42",
                message = "登录失败用例期望返回 401，但当前实现对错误密码也返回了 200，缺少状态码断言。",
            ),
        )
        delay(1400)
        put(FlowItem.TestResult(key = nextKey(), passed = 24, failed = emptyList()))
        delay(1000)
        put(FlowItem.TurnSummary(key = nextKey(), filesChanged = 4, toolsRun = 7, duration = "38s", tokens = "4.2k"))
        delay(900)
        put(FlowItem.AcceptBar(key = nextKey(), changedCount = 4))
        delay(900)
        put(FlowItem.Commit(key = nextKey(), hash = "a1b2c3d"))
        delay(900)
        put(
            FlowItem.FollowUps(
                key = nextKey(),
                suggestions = listOf("再写个集成测试", "解释 JWT 过期逻辑", "加 refresh token"),
            ),
        )
    },
)

private enum class PlanDecision { Approve, Refine }

/** 首次放入或在「继续细化」后刷新计划卡，使其按钮回调绑定到当前等待的决策。 */
private suspend fun ChatFlowState.patchOrPutPlan(
    key: Int,
    refined: Boolean,
    onApprove: () -> Unit,
    onRefine: () -> Unit,
) {
    val existing = items.firstOrNull { it.key == key } as? FlowItem.Plan
    val baseSteps = listOf(
        AppPlanStep("梳理现有 feature/auth 路由与依赖注入", AppPlanStepStatus.Done),
        AppPlanStep("新增 POST /auth/login 路由与参数校验", AppPlanStepStatus.InProgress),
        AppPlanStep("实现 JWT 签发与 TokenMiddleware 鉴权", AppPlanStepStatus.Pending),
        AppPlanStep("补齐登录/鉴权单测并跑通全量测试", AppPlanStepStatus.Pending),
    )
    val steps = if (refined) {
        baseSteps + AppPlanStep("补充 refresh token 与密钥轮换的回滚策略", AppPlanStepStatus.Pending)
    } else {
        baseSteps
    }
    val plan = FlowItem.Plan(
        key = key,
        title = "登录接口 + JWT 鉴权落地计划",
        steps = steps,
        state = AppPlanState.AwaitingApproval,
        pendingSelection = if (refined) null else "token 采用无状态 JWT（密钥走环境变量），可以吗？",
        reason = if (refined) "已按你的要求补充细化步骤，请再次确认" else "计划待你确认后开始执行",
        onApprove = onApprove,
        onRefine = onRefine,
    )
    if (existing == null) {
        put(plan)
    } else {
        items[items.indexOfFirst { it.key == key }] = plan
    }
}
