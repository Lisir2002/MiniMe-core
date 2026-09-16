package com.mini.me_core.newui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.composite.AppApprovalChoice
import com.mini.me_core.newui.designsystem.component.AppAttachmentCard
import com.mini.me_core.newui.designsystem.component.AppButton
import com.mini.me_core.newui.designsystem.component.AppButtonVariant
import com.mini.me_core.newui.composite.AppChatBubble
import com.mini.me_core.newui.composite.AppChatMarker
import com.mini.me_core.newui.composite.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppMarkdownText
import com.mini.me_core.newui.composite.AppMcpAppCard
import com.mini.me_core.newui.composite.AppMcpAppState
import com.mini.me_core.newui.designsystem.component.AppMessageRow
import com.mini.me_core.newui.designsystem.component.AppMessageScroller
import com.mini.me_core.newui.composite.AppPlanCard
import com.mini.me_core.newui.composite.AppPlanState
import com.mini.me_core.newui.designsystem.component.AppPlanStep
import com.mini.me_core.newui.composite.AppPlanStepStatus
import com.mini.me_core.newui.designsystem.component.AppSectionHeader
import com.mini.me_core.newui.composite.AppSkillCallCard
import com.mini.me_core.newui.composite.AppSkillCallState
import com.mini.me_core.newui.designsystem.component.AppThinkingBlock
import com.mini.me_core.newui.composite.AppToolCallCard
import com.mini.me_core.newui.composite.AppToolCallState
import com.mini.me_core.newui.designsystem.component.AppToolChainStep
import com.mini.me_core.newui.composite.AppToolChainStepState
import com.mini.me_core.newui.composite.AppToolChainTimeline
import com.mini.me_core.newui.composite.AppToolSummaryCard
import com.mini.me_core.newui.composite.AppToolSummaryState
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 分子组件族 · AI 对话流：气泡 / 思考 / 计划审批 / 工具 / MCP / 技能 / 附件 / 链 / 摘要。
 * 状态机 + 流式演示自包含；[swipeExpanded]/[onSwipeExpanded] 与滑扫示例共享（同批只开一项）。
 */
@Composable
internal fun ChatGallerySection(
    onOpenChatFlow: () -> Unit,
    swipeExpanded: Int?,
    onSwipeExpanded: (Int?) -> Unit,
) {
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
                        onSwipeExpanded = onSwipeExpanded,
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
}
