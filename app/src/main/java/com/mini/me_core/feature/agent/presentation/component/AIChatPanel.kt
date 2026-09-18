package com.mini.me_core.feature.agent.presentation.component

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.ui.rememberImeBottomInset
import com.mini.me_core.feature.agent.domain.model.AgentMode
import com.mini.me_core.feature.agent.domain.model.ReasoningEffort
import com.mini.me_core.feature.agent.domain.tool.question.UserQuestionAnswer
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AIAgentViewModel
import com.mini.me_core.feature.agent.presentation.ConversationSkillsViewModel
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.hasVisibleContent
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import com.mini.me_core.feature.workspace.presentation.WorkspaceViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import com.mini.me_core.newui.designsystem.component.AppChatMarker
import com.mini.me_core.newui.designsystem.component.AppChatMarkerKind
import com.mini.me_core.newui.designsystem.component.AppChatMessageState
import com.mini.me_core.newui.designsystem.component.AppComposer
import com.mini.me_core.newui.designsystem.component.AppComposerMode
import com.mini.me_core.newui.designsystem.component.AppComposerReasoning
import com.mini.me_core.newui.designsystem.component.AppComposerSlashCommand
import com.mini.me_core.newui.designsystem.component.AppMessageRow
import com.mini.me_core.newui.designsystem.component.AppMessageScroller
import com.mini.me_core.newui.designsystem.component.AppThinkingBlock
import com.mini.me_core.newui.designsystem.component.AppTypingIndicator
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import java.io.File
import kotlinx.coroutines.launch

internal val brandGradient = androidx.compose.ui.graphics.Brush.linearGradient(
    listOf(com.mini.me_core.core.theme.Brand.Blue, com.mini.me_core.core.theme.Brand.Sky)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null,
    workspaceViewModel: WorkspaceViewModel? = null,
    drawerState: DrawerState,
    currentFile: String? = null,
    selectedCode: String? = null,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val messages = messagesState.messages

    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSession = sessions.find { it.id == currentSessionId }
    val sessionTitle = currentSession?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_new_session_btn)
    val sessionInputTokens = currentSession?.totalInputTokens ?: 0
    val sessionOutputTokens = currentSession?.totalOutputTokens ?: 0
    val sessionLastInputTokens = currentSession?.lastInputTokens ?: 0
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId

    val isCompacting by viewModel.isCompacting.collectAsStateWithLifecycle()
    val retryState by viewModel.retryState.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingUserQuestion.collectAsStateWithLifecycle()
    val queuedRequests by viewModel.queuedRequests.collectAsStateWithLifecycle()

    val globalActiveProvider = settingsViewModel?.activeProvider?.collectAsStateWithLifecycle()?.value
    val providers = (settingsViewModel?.providers?.collectAsStateWithLifecycle()?.value ?: emptyList()).filter { it.isEnabled }
    val modelMetadata = settingsViewModel?.modelMetadata?.collectAsStateWithLifecycle()?.value.orEmpty()
    val sessionProviderModel by viewModel.currentSessionProviderModel.collectAsStateWithLifecycle()
    val activeProvider = run {
        val (boundProviderId, boundModel) = sessionProviderModel
        if (!boundProviderId.isNullOrBlank()) {
            providers.find { it.id == boundProviderId }?.takeIf { it.apiKey.isNotBlank() }?.let {
                if (!boundModel.isNullOrBlank()) it.copy(selectedModel = boundModel) else it
            } ?: globalActiveProvider
        } else {
            globalActiveProvider
        }
    }
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    val projectRoot = currentWorkspace?.path ?: ""
    val currentMode by viewModel.currentSessionMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val inputDraft by viewModel.inputDraft.collectAsStateWithLifecycle()
    LaunchedEffect(inputDraft) {
        if (inputText != inputDraft) inputText = inputDraft
    }
    var pendingAttachments by remember { mutableStateOf<List<PendingUploadAttachment>>(emptyList()) }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var showConversationSkills by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    val conversationSkillsViewModel: ConversationSkillsViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming
    val activeModel = activeProvider?.effectiveModel.orEmpty()
    val activeModelMetadata = modelMetadata[activeModel]
    val reasoningEffort by viewModel.currentSessionReasoningEffort.collectAsStateWithLifecycle()

    LaunchedEffect(activeProvider?.type, activeModel) {
        val provider = activeProvider ?: return@LaunchedEffect
        if (activeModel.isNotBlank()) {
            settingsViewModel?.resolveModelMetadata(provider.type, listOf(activeModel))
        }
    }

    fun removePendingAttachment(index: Int) {
        pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index }
    }

    fun startEditMessage(message: AgentUIMessage) {
        if (message.role != MessageRole.USER) return
        editingMessageId = message.id
        inputText = message.content
        viewModel.updateInputDraft(message.content)
        focusManager.clearFocus()
    }

    fun cancelEditMessage() {
        editingMessageId = null
        inputText = ""
        viewModel.clearInputDraft()
    }

    fun handlePickedAttachments(uris: List<Uri>, images: Boolean) {
        if (uris.isEmpty()) return
        if (projectRoot.isBlank()) {
            Toast.makeText(context, emptyWorkspaceMessage(context), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAttachmentSlots(pendingAttachments.size)) {
            Toast.makeText(context, maxAttachmentMessage(context, MAX_PENDING_ATTACHMENTS), Toast.LENGTH_SHORT).show()
            return
        }
        val selected = selectedAttachments(uris, pendingAttachments.size)
        scope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()
            selected.forEach { uri ->
                runCatching {
                    copyUriToWorkspace(context, uri, projectRoot, includeImageData = images)
                }.onSuccess { uploaded ->
                    pendingAttachments = pendingAttachments + uploaded.toPendingAttachment()
                    successCount += 1
                }.onFailure { error ->
                    failures += (error.message ?: uploadFallbackError(context))
                }
            }

            when {
                successCount > 0 && failures.isEmpty() && uris.size <= remainingAttachmentSlots(pendingAttachments.size - successCount) ->
                    Toast.makeText(context, uploadSuccessMessage(context, successCount), Toast.LENGTH_SHORT).show()
                successCount > 0 ->
                    Toast.makeText(context, partialUploadMessage(context, successCount), Toast.LENGTH_LONG).show()
                failures.isNotEmpty() ->
                    Toast.makeText(context, failures.first(), Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedAttachments(uris, images = false)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedAttachments(uris, images = true)
    }

    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraPhotoUri
        cameraPhotoUri = null
        if (success && uri != null) {
            handlePickedAttachments(listOf(uri), images = true)
        }
    }
    fun takePhoto() {
        val photoFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, unreadableFileMessage(context), Toast.LENGTH_SHORT).show()
            return
        }
        cameraPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() || pendingAttachments.isNotEmpty()) {
            val editingId = editingMessageId
            val attachments = pendingAttachments
            val modelRequest = appendAttachmentsToRequest(context, text, attachments)
            val sendImages = when (val m = activeModelMetadata) {
                null -> true
                else -> m.supportsVision || m.source == com.mini.me_core.feature.settings.domain.model.ModelMetadata.Source.INFERRED
            }
            val images = if (sendImages) attachments.toAgentImages() else emptyList()
            if (editingId != null) {
                viewModel.editAndResend(editingId, text)
            } else {
                viewModel.enqueueAgentRequest(
                    request = text,
                    modelRequest = modelRequest,
                    currentFile = currentFile,
                    selectedCode = selectedCode,
                    projectRoot = projectRoot,
                    inputImages = images,
                    inputAttachments = attachments.toAgentAttachments()
                )
            }
            editingMessageId = null
            inputText = ""
            viewModel.clearInputDraft()
            pendingAttachments = emptyList()
        }
    }

    val executionMode = settingsViewModel?.executionMode?.collectAsStateWithLifecycle()?.value
    val connectionState = settingsViewModel?.connectionState?.collectAsStateWithLifecycle()?.value
    val isRemote = executionMode == com.mini.me_core.feature.settings.data.repository.ExecutionMode.REMOTE_SSH

    // 流式尾巴派生状态
    val reasoning = streamingReasoning
    val showReasoning = reasoning != null && reasoning.isNotEmpty()
    val streaming = streamingText
    val showStreaming = streaming != null && streaming.hasVisibleContent()
    val showThinking = !showReasoning && !showStreaming && !isCompacting && isBusy &&
        pendingPermission == null && pendingQuestion == null
    val showRetrying = retryState != null && isBusy && !isCompacting && !showStreaming && !showReasoning
    // 流式尾巴是否有任何可见内容：首条消息模型必然先输出 thinking，
    // 此时历史 messages 仍为空，但流式思考块必须照常渲染（与后续消息一致），不能退回欢迎页。
    val showStreamingTail = showReasoning || showStreaming || showThinking || isCompacting || showRetrying
    // 历史消息块（相邻 TOOL 折叠为工具链）：在 @Composable 外层算好，再喂给非 composable 的 LazyListScope content。
    val historyBlocks = remember(messages) { messages.toChatBlocks().asReversed() }

    // 常驻任务条：取消息流里最新一条 todo 快照（结果优先，回退入参）。
    val todoItems = remember(messages) {
        messages.lastOrNull {
            it.toolName == "todo" || it.toolName == "todowrite" || it.toolName == "todo_list"
        }?.let { m -> parseTodoResult(m.content) ?: parseTodoArgs(m.toolArgs) }?.items.orEmpty()
    }
    val todoAllDone = todoItems.isNotEmpty() && todoItems.all { it.status == "completed" }
    // 全部完成后横条停留 2 秒自动收起；新任务到来时复位。
    var todoBarDismissed by remember { mutableStateOf(false) }
    var showTodoSheet by remember { mutableStateOf(false) }
    LaunchedEffect(todoItems) { todoBarDismissed = false }
    LaunchedEffect(todoAllDone) {
        if (todoAllDone) {
            kotlinx.coroutines.delay(2000)
            todoBarDismissed = true
        }
    }
    val showTodoBar = todoItems.isNotEmpty() && !todoBarDismissed

    val planApproval by viewModel.pendingPlanApproval.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                sessionTitle = sessionTitle,
                modelName = activeProvider?.effectiveModel,
                inputTokens = sessionInputTokens,
                outputTokens = sessionOutputTokens,
                onOpenDrawer = {
                    keyboardController?.hide()
                    scope.launch { drawerState.open() }
                },
                onNewChat = { viewModel.newSession() },
                connectionState = connectionState?.takeIf { isRemote }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (!messagesReady) {
                    if (isRemote && connectionState != null && connectionState != com.mini.me_core.feature.agent.domain.container.ConnectionState.CONNECTED) {
                        RemoteConnectingPlaceholder(state = connectionState)
                    }
                } else if (messages.isEmpty() && !showStreamingTail) {
                    WelcomeState(modifier = Modifier.fillMaxSize())
                } else {
                    // 新版对话流：AppMessageScroller（reverseLayout）+ 扁平节点 ChatMessageNode。
                    // reverseLayout 把最新内容锚定视觉底部，流式增长自动贴底；新落库消息靠 newMessageKey 回底。
                    // 消息行统一左右外边距 AppLayout.PageHorizontal(16dp)，思考块/气泡/工具卡同一水平带，不顶屏幕边缘。
                    AppMessageScroller(
                        modifier = Modifier.fillMaxSize(),
                        newMessageKey = "$currentSessionId:${messages.size}",
                        onLoadHistory = {
                            if (messagesState.hasMore && !messagesState.isLoadingMore) {
                                viewModel.loadMoreMessages()
                            }
                        },
                        loadingHistory = messagesState.isLoadingMore,
                    ) {
                        // 流式尾巴位于视觉底部（index 0）。
                        if (showReasoning) {
                            item(key = "__reasoning__") {
                                AppThinkingBlock(
                                    text = reasoning.orEmpty(),
                                    initiallyExpanded = true,
                                    isStreaming = true,
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                            }
                        }
                        if (showStreaming) {
                            item(key = "__stream__") {
                                AppMessageRow(
                                    text = streaming ?: "",
                                    isUser = false,
                                    state = AppChatMessageState.Streaming,
                                    onStop = { viewModel.stopAgent() },
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                            }
                        } else if (showThinking) {
                            item(key = "__thinking__") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppLayout.PageHorizontal, vertical = Spacing.sm),
                                    contentAlignment = Alignment.CenterStart,
                                ) { AppTypingIndicator() }
                            }
                        } else if (isCompacting) {
                            item(key = "__compacting__") {
                                AppChatMarker(
                                    text = "正在压缩上下文…",
                                    kind = AppChatMarkerKind.System,
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                            }
                        } else if (showRetrying) {
                            val rs = retryState
                            item(key = "__retrying__") {
                                AppChatMarker(
                                    text = "重试中（第 ${rs?.attempt ?: 0}/${rs?.maxRetries ?: 0} 次）…",
                                    kind = AppChatMarkerKind.Tool,
                                    running = true,
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                            }
                        }
                        // 历史消息按时间倒序传入（reverseLayout 下最新持久消息紧贴流式尾巴上方）。
                        // 相邻 TOOL 消息折叠成一条工具链块（ChatBlock.ToolGroup），其余单条渲染。
                        items(
                            items = historyBlocks,
                            key = { block ->
                                when (block) {
                                    is ChatBlock.Single -> block.msg.id
                                    is ChatBlock.ToolGroup -> "toolchain:${block.msgs.first().id}..${block.msgs.last().id}"
                                }
                            },
                        ) { block ->
                            when (block) {
                                is ChatBlock.Single -> ChatMessageNode(
                                    msg = block.msg,
                                    onOpenAttachment = { att -> openAttachment(context, att) },
                                    onEditMessage = { startEditMessage(it) },
                                    onNewChatFromMessage = { viewModel.newChatAndSend(it.content) },
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                                is ChatBlock.ToolGroup -> ToolCallGroupBlock(
                                    group = block,
                                    modifier = Modifier.padding(horizontal = AppLayout.PageHorizontal),
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = changes.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ChangePreviewPanel(
                    changes = changes,
                    onApply = { viewModel.applyChanges(changes) },
                    onReject = { viewModel.rejectChanges() }
                )
            }

            StatusBanner(state = agentState)

            // 工具审批不再以浮层卡片形式出现在消息流里，改为吸附到输入框上方常驻条（见下方审批 DockBar），
            // 未处理审批始终有可见入口；点批准/拒绝后自动消失。

            AnimatedVisibility(
                visible = pendingQuestion != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                pendingQuestion?.let { question ->
                    AskUserQuestionPanel(
                        question = question,
                        onConfirm = { answer -> viewModel.resolveUserQuestion(question.id, answer) },
                        onSkip = { viewModel.resolveUserQuestion(question.id, UserQuestionAnswer(emptyList())) }
                    )
                }
            }

            AnimatedVisibility(
                visible = planApproval != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                planApproval?.let { state ->
                    PlanApprovalPanel(
                        state = state,
                        onApprove = { viewModel.approvePlanAndBuild() },
                        onRefine = { viewModel.refinePlan() }
                    )
                }
            }

            editingMessageId?.let { editingId ->
                val editingMsg = messages.find { it.id == editingId }
                if (editingMsg != null) {
                    EditingMessageBanner(
                        snippet = editingMsg.content.take(80),
                        onCancel = { cancelEditMessage() }
                    )
                }
            }

            // 常驻条垂直顺序（从上到下）：审批条 → 任务清单条 → 输入框。均紧凑单行。
            // 审批吸附条：未处理审批始终常驻可见，紧凑单行；批准/拒绝后自动消失。
            pendingPermission?.let { request ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppLayout.PageHorizontal, vertical = AppSpacing.Xs)
                        .clip(RoundedCornerShape(AppRadius.Md))
                        .background(appPalette().card)
                        .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "待审批：${request.toolName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = appPalette().ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.chat_perm_deny),
                        style = MaterialTheme.typography.labelMedium,
                        color = appPalette().labelSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppRadius.Sm))
                            .clickable { viewModel.resolveToolPermission(request.id, PermissionChoice.REJECT) }
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                    Text(
                        text = stringResource(R.string.common_allow),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appPalette().onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppRadius.Sm))
                            .background(appPalette().primary)
                            .clickable { viewModel.resolveToolPermission(request.id, PermissionChoice.ONCE) }
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                    )
                }
            }

            // 常驻任务条：吸附在输入框上方，随 todo 流实时更新；无任务/自动收起时不渲染。
            if (showTodoBar) {
                TodoDockBar(
                    items = todoItems,
                    allDone = todoAllDone,
                    onClick = { showTodoSheet = true },
                )
            }
            if (showTodoSheet) {
                TodoReadonlySheet(items = todoItems, onDismiss = { showTodoSheet = false })
            }

            // 新版输入栏：AppComposer 接管模式/思考/模型/技能/附件/斜杠/排队/发送。
            Box(modifier = Modifier.padding(bottom = rememberImeBottomInset())) {
                AppComposer(
                    value = inputText,
                    onValueChange = { inputText = it; viewModel.updateInputDraft(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    attachments = pendingAttachments.map { it.fileName },
                    onRemoveAttachment = ::removePendingAttachment,
                    queued = queuedRequests.map { it.request },
                    onRemoveQueued = { i -> queuedRequests.getOrNull(i)?.let { viewModel.removeQueuedRequest(it.id) } },
                    mode = currentMode.toComposerMode(),
                    onCycleMode = {
                        viewModel.setSessionMode(
                            when (currentMode) {
                                AgentMode.BUILD -> AgentMode.PLAN
                                AgentMode.PLAN -> AgentMode.AUTO
                                AgentMode.AUTO -> AgentMode.BUILD
                            }
                        )
                    },
                    reasoning = reasoningEffort.toComposerReasoning(),
                    onCycleReasoning = {
                        viewModel.setSessionReasoningEffort(
                            when (reasoningEffort) {
                                ReasoningEffort.LOW -> ReasoningEffort.MEDIUM
                                ReasoningEffort.MEDIUM -> ReasoningEffort.HIGH
                                ReasoningEffort.HIGH -> ReasoningEffort.LOW
                            }
                        )
                    },
                    modelLabel = activeProvider?.effectiveModel.orEmpty(),
                    onPickModel = { showModelSheet = true },
                    onOpenSkills = { showConversationSkills = true },
                    tokenProgress = run {
                        val contextLimit = activeModelMetadata?.contextTokens ?: 0
                        if (contextLimit > 0) sessionLastInputTokens.toFloat() / contextLimit else 0f
                    },
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                    onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                    onTakePhoto = ::takePhoto,
                    slashCommands = viewModel.slashCommands.map { AppComposerSlashCommand(it.trigger, it.description) },
                    onRunSlash = { cmd -> inputText = cmd.trigger; viewModel.updateInputDraft(cmd.trigger) },
                    streaming = isBusy,
                    onSend = sendMessage,
                    onStop = { viewModel.stopAgent() },
                )
            }

            if (showModelSheet && activeProvider != null) {
                ModelSheet(
                    providers = providers,
                    currentProviderId = activeProvider.id,
                    currentModel = activeProvider.effectiveModel,
                    onSelect = { pId, model ->
                        viewModel.setSessionProviderModel(pId, model)
                        showModelSheet = false
                    },
                    onManage = {
                        showModelSheet = false
                        onNavigateToSettings()
                    },
                    onDismiss = { showModelSheet = false },
                )
            }

            val skillsSessionId = currentSessionId
            if (showConversationSkills && skillsSessionId != null) {
                ConversationSkillsSheet(
                    viewModel = conversationSkillsViewModel,
                    sessionId = skillsSessionId,
                    onDismiss = { showConversationSkills = false }
                )
            }
        }
    }
}

private fun AgentMode.toComposerMode(): AppComposerMode = when (this) {
    AgentMode.BUILD -> AppComposerMode.BUILD
    AgentMode.PLAN -> AppComposerMode.PLAN
    AgentMode.AUTO -> AppComposerMode.AUTO
}

private fun ReasoningEffort.toComposerReasoning(): AppComposerReasoning = when (this) {
    ReasoningEffort.LOW -> AppComposerReasoning.LOW
    ReasoningEffort.MEDIUM -> AppComposerReasoning.MEDIUM
    ReasoningEffort.HIGH -> AppComposerReasoning.HIGH
}

/**
 * 编辑态提示条：展示正在编辑的消息摘要，提供取消编辑入口。
 */
@Composable
private fun EditingMessageBanner(
    snippet: String,
    onCancel: () -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.mini.me_core.core.theme.Radius.md),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconXs)
            )
            Text(
                text = stringResource(R.string.chat_editing_banner, snippet),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm)
            )
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconL)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_edit_cancel),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(com.mini.me_core.newui.designsystem.token.generated.AppSizing.IconXs)
                )
            }
        }
    }
}
