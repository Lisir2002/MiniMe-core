package com.mini.me_core.feature.agent.presentation.component
import com.mini.me_core.core.theme.tokens.IconSizeTokens
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.ScaledAnimation
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.presentation.util.MessageSpeechReader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.agent.domain.execution.tool.question.UserQuestionAnswer
import com.mini.me_core.feature.agent.presentation.AgentUIMessage
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.agent.presentation.AIAgentViewModel
import com.mini.me_core.feature.agent.presentation.ConversationSkillsViewModel
import com.mini.me_core.feature.agent.presentation.MessageRole
import com.mini.me_core.feature.agent.presentation.TaskSubGroupType
import com.mini.me_core.feature.agent.presentation.hasVisibleContent
import com.mini.me_core.feature.agent.presentation.component.agentfirst.AgentFirstFeatureFlags
import com.mini.me_core.feature.agent.presentation.component.agentfirst.TaskCard
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import com.mini.me_core.feature.workspace.presentation.WorkspaceViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf

/**
 * 品牌渐变色 Brush。读取当前主题的 primary 色，构造 primary → primary(alpha=0.6f) 的渐变。
 * 跟随主题预设变化，不再硬编码 Brand.Blue/Brand.Sky。
 */
@Composable
internal fun brandGradient(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.6f)))
}

/**
 * 流式尾巴的三种状态，用于 [when] 分支分发。
 *
 * 早先用 [androidx.compose.animation.Crossfade] 做淡入，但 Crossfade 按 targetState
 * 缓存 content 子组合——流式期间 targetState 一直不变，文本增长时不会重新调用 content，
 * 导致 [StreamingBubble] 收不到后续文本、停在首句。故改用枚举 + 直接 [when] 分发。
 */
private enum class TailKind { THINKING, STREAMING, COMPACTING, RETRYING, NONE }

private data class AutoScrollSignal(
    val streamingTextLength: Int,
    val streamingReasoningLength: Int,
    val runningToolMessageId: String?,
    val runningToolTextLength: Int,
    val isCompacting: Boolean,
    val messageCount: Int,
    val thinkingTail: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToNormFlow: () -> Unit = {},
    onOpenRightSidebar: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null,
    workspaceViewModel: WorkspaceViewModel? = null,
    drawerState: DrawerState,
    currentFile: String? = null,
    selectedCode: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val messages = messagesState.messages
    val taskGroups by viewModel.taskGroups.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()

    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSession = sessions.find { it.id == currentSessionId }
    val sessionTitle = currentSession?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_new_session_btn)
    val sessionInputTokens = currentSession?.totalInputTokens ?: 0
    val sessionOutputTokens = currentSession?.totalOutputTokens ?: 0
    val sessionLastInputTokens = currentSession?.lastInputTokens ?: 0
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsStateWithLifecycle()
    val environmentSnapshots by viewModel.environmentSnapshots.collectAsStateWithLifecycle()
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
            // 与 workflow.resolveProviderConfig 保持一致：绑定 provider 须启用且已填 apiKey，否则回退全局
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

    // 问题17：草稿状态按 currentSessionId 隔离，切换/新建会话时自动重置为空
    var inputText by remember(currentSessionId) { mutableStateOf("") }
    val inputDraft by viewModel.inputDraft.collectAsStateWithLifecycle()
    LaunchedEffect(inputDraft) {
        if (inputText != inputDraft) inputText = inputDraft
    }
    // 问题17：会话切换时清空 ViewModel 中的旧草稿，防止上一会话未发送文字串到新会话
    LaunchedEffect(currentSessionId) {
        viewModel.clearInputDraft()
    }
    var pendingAttachments by remember { mutableStateOf<List<PendingUploadAttachment>>(emptyList()) }
    // 编辑态：正在编辑的用户消息 id。发送时若非空则走「截断重发」而非普通发送。
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var fileDiffsForSheet by remember { mutableStateOf<TaskChangesSheetData?>(null) }
    // 对话技能面板（D5/D10）：输入栏「技能」按钮唤出，管理对话级添加/禁用。
    var showConversationSkills by remember { mutableStateOf(false) }
    // F2.7：对话模板面板
    var showTemplatePanel by remember { mutableStateOf(false) }
    val conversationSkillsViewModel: ConversationSkillsViewModel = hiltViewModel()
    val listState = rememberLazyListState()
    val markdownCache = remember { MarkdownRenderCache() }
    // F2.1：MiniMe 自研 Markdown 渲染器的解析缓存（LRU 50 条，按内容 hash）
    val miniMeMarkdownCache = remember { com.mini.me_core.feature.agent.presentation.component.markdown.MiniMeMarkdownCache() }

    // 消息朗读（TTS）：引擎随组合生命周期创建/释放，isSpeaking 驱动菜单里的「停止朗读」态。
    val speechReader = remember { MessageSpeechReader(context) }
    var isSpeaking by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { speechReader.release() }
    }
    val messageActionBus = MessageActionBus(
        onRegenerate = { messageId -> viewModel.regenerateMessage(messageId) },
        onDelete = { messageId -> viewModel.deleteMessage(messageId) },
        onShare = { text -> shareMessageText(context, text) },
        onReadAloud = { text ->
            speechReader.speak(
                text,
                onStart = { isSpeaking = true },
                onDone = { isSpeaking = false }
            )
        },
        isSpeaking = isSpeaking,
        onStopSpeaking = {
            speechReader.stop()
            isSpeaking = false
        }
    )

    // F2.5：待确认的模型切换 (providerId, modelName)
    var pendingModelSwitch by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming
    val activeModel = activeProvider?.effectiveModel.orEmpty()
    val activeModelMetadata = modelMetadata[activeModel]
    val canUploadFiles = projectRoot.isNotBlank() && activeModelMetadata?.supportsTools == true
    val canUploadImages = projectRoot.isNotBlank()
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

    /** 编辑用户消息：把内容填入输入框，让用户修改后发送（发送时截断该消息之后的对话）。 */
    fun startEditMessage(message: AgentUIMessage) {
        if (message.role != MessageRole.USER) return
        editingMessageId = message.id
        inputText = message.content
        viewModel.updateInputDraft(message.content)
        focusManager.clearFocus()
    }

    /** 取消编辑态：清空编辑标记与输入框草稿。 */
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

    // 拍照：输出到 cache 临时文件（FileProvider 授权 uri），拍完按图片附件处理。
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

    // 自动滚动跟随
    var positionedSession by remember { mutableStateOf<String?>(null) }
    var followBottom by remember { mutableStateOf(true) }

    // 是否在底部：主判断用 canScrollForward（更稳定），辅以末项位置校验，
    // 阈值 16dp 避免滚动中频繁切换状态
    val isAtBottom by remember {
        derivedStateOf {
            if (!listState.canScrollForward) return@derivedStateOf true
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val lastIndex = layout.totalItemsCount - 1
            val viewportBottom = layout.viewportEndOffset
            lastVisible.index >= lastIndex &&
                (lastVisible.offset + lastVisible.size) <= viewportBottom + 16
        }
    }

    // 显示防抖：离开底部 200ms 后才显示（避免 fling 过程中闪烁），
    // 到达底部 100ms 后再隐藏
    var showScrollToBottomRaw by remember { mutableStateOf(false) }
    // derivedStateOf 的计算 lambda 只在首次组合时创建并被永久记住，若直接闭包捕获
    // messages / messagesReady 这类「每次重组都会重新赋值的局部 val」，会锁定首帧的空列表，
    // 导致「回到底部」按钮永不显示。用 rememberUpdatedState 包装成稳定 State 引用后再读，
    // 即可每次读取都拿到最新值，同时保留 derivedStateOf 的去重组收益。
    val messagesSnapshot by rememberUpdatedState(messages)
    val messagesReadySnapshot by rememberUpdatedState(messagesReady)
    val showScrollToBottom by remember {
        derivedStateOf { messagesReadySnapshot && messagesSnapshot.isNotEmpty() && showScrollToBottomRaw }
    }
    LaunchedEffect(isAtBottom, messagesReady) {
        if (!isAtBottom && messagesReady && messages.isNotEmpty()) {
            kotlinx.coroutines.delay(200)
            if (!isAtBottom) showScrollToBottomRaw = true
        } else {
            kotlinx.coroutines.delay(100)
            if (isAtBottom) showScrollToBottomRaw = false
        }
    }

    // 「跳到上一条用户消息」：向上按钮可见性 + 点击行为。
    // 语义完全无状态（可预测）：目标 = 视口顶部之上最近的一个用户消息组；
    // 视口顶正落在某用户消息组上时，再点击天然跳到上一条；上方无用户消息
    // （已位于最顶的用户消息处）时返回 null，按钮隐藏消失。
    // 同上：taskGroups 每帧重建，必须经 rememberUpdatedState 包装后再被 derivedStateOf / 闭包读取，
    // 否则锁定首帧空分组，上箭头永不显示。
    val taskGroupsSnapshot by rememberUpdatedState(taskGroups)
    val userMessageGroupIndices: () -> List<Int> = {
        taskGroupsSnapshot.mapIndexedNotNull { index, group ->
            index.takeIf { group.subGroups.any { it.type == TaskSubGroupType.USER } }
        }
    }
    val showJumpToUserMessage by remember {
        derivedStateOf {
            if (taskGroupsSnapshot.isEmpty()) return@derivedStateOf false
            val top = listState.firstVisibleItemIndex.coerceAtMost(taskGroupsSnapshot.size - 1)
            previousUserMessageGroupIndex(userMessageGroupIndices(), top) != null
        }
    }
    val jumpToUserMessage: () -> Unit = {
        scope.launch {
            if (taskGroupsSnapshot.isEmpty()) return@launch
            val top = listState.firstVisibleItemIndex.coerceIn(0, taskGroupsSnapshot.size - 1)
            val dest = previousUserMessageGroupIndex(userMessageGroupIndices(), top) ?: return@launch
            // 跳转即离开底部：停止流式贴底跟随，「回到底部」按钮随之出现
            followBottom = false
            listState.animateScrollToItem(dest)
        }
    }

    val autoScrollSignal by rememberUpdatedState(
        AutoScrollSignal(
            streamingTextLength = streamingText?.length ?: 0,
            streamingReasoningLength = streamingReasoning?.length ?: 0,
            runningToolMessageId = runningTool.firstOrNull()?.messageId,
            runningToolTextLength = runningTool.firstOrNull()?.text?.length ?: 0,
            isCompacting = isCompacting,
            messageCount = messages.size,
            // 思考阶段：__reasoning__ 气泡存在且无正文流式时，尾部 __active__ 是空 Box。
            thinkingTail = streamingReasoning?.isNotEmpty() == true && streamingText?.hasVisibleContent() != true
        )
    )

    // 用户开始拖拽：停止跟随；拖拽停止时若已到底则恢复跟随。
    // 额外：流式输出时内容持续增长，用户可能松手后又被「顶」离底部——
    // 用 snapshotFlow { isAtBottom } 持续监测，只要滑到底部就恢复跟随。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    followBottom = false
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    // 拖拽结束后等待一帧让布局稳定，再判断是否在底部
                    withFrameNanos { }
                    if (isAtBottom) {
                        followBottom = true
                    }
                }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom }.collect { atBottom ->
            if (atBottom) followBottom = true
        }
    }

    fun lastItemBottomOffset(index: Int): Int {
        val layout = listState.layoutInfo
        val viewportH = layout.viewportEndOffset - layout.viewportStartOffset
        val size = layout.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
        return size - viewportH
    }

    suspend fun ensureLastItemMeasured(index: Int) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
            withFrameNanos { }
        }
    }

    // 贴底跟随：与正文流式一致，用 animateScrollToItem 平滑滚动，collectLatest 自动取消
    // 上一个未跑完的动画、新动画从当前位置平滑接管，视觉连续。
    // 末条流式消息可能高于一屏，必须滚到末项底部；只 animateScrollToItem(lastIndex)
    // 会把末项顶部对齐到视口顶部。
    val snapToBottom: suspend (Int) -> Unit = { index ->
        if (index >= 0) {
            ensureLastItemMeasured(index)
            listState.animateScrollToItem(index, lastItemBottomOffset(index))
        }
    }

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() || pendingAttachments.isNotEmpty()) {
            val editingId = editingMessageId
            val attachments = pendingAttachments
            val modelRequest = appendAttachmentsToRequest(context, text, attachments)
            // 判断是否把 pendingAttachments 中的图片作为 vision 输入传给模型。
            // 规则（多层防线，避免误杀用户配置的自定义多模态模型）：
            //   - metadata == null：还没解析过，信任用户 → 发图片；
            //   - metadata.supportsVision=true → 发；
            //   - metadata.source=INFERRED：模型不在 models.dev catalog 内（自建/兼容端点/新模型），
            //     信任用户的意图 → 发图片；即便真的是文本模型 API 报 400，错误也会正常显示，
            //     远比「静默把图片置空，模型回复文本，用户以为模型不识字」要好。
            //   - 仅当 metadata 明确来自 MODELS_DEV 且 supportsVision=false（收录的纯文本模型）→ 不发
            val sendImages = when (val m = activeModelMetadata) {
                null -> true
                else -> m.supportsVision || m.source == com.mini.me_core.feature.settings.domain.model.ModelMetadata.Source.INFERRED
            }
            val images = if (sendImages) attachments.toAgentImages() else emptyList()
            if (editingId != null) {
                // 编辑重发：截断该消息之后的对话，以新内容重新执行（上下文干净）
                viewModel.editAndResend(editingId, text)
            } else {
                // 统一走队列：AI 忙时入队（等本轮结束后自动发送下一条），空闲时直接发送。
                // 斜杠命令在 ViewModel 内（agent workflow 之前）分流执行，无需在此区分。
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
            followBottom = true
            scope.launch {
                kotlinx.coroutines.delay(0)
                snapToBottom(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    // 切换会话：把列表定位到最新一条，并恢复跟随。
    LaunchedEffect(currentSessionId, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        val target = messages.size - 1
        if (target < 0) {
            positionedSession = currentSessionId
            followBottom = true
            return@LaunchedEffect
        }
        if (positionedSession != currentSessionId) {
            snapToBottom(listState.layoutInfo.totalItemsCount - 1)
            positionedSession = currentSessionId
            followBottom = true
        }
    }

    // 流式贴底跟随（聊天标准做法）。
    // 监听 (流式文本长度/思考长度, 消息条数) 元组：每个吐字 delta（length 变）和每次落库
    // （size 变）都触发一次瞬时贴底（scrollToItem）。思考与正文都按字符粒度触发，
    // 内容增长多少立即滚多少，气泡始终贴底，无动画滞后与周期感。
    // 注意：不能用 distinctUntilChanged() 包布尔谓词，只触发一次后去重，不再跟随（旧根因）。
    // 注意：不能删 __active__ item（让 totalItemsCount 突减），anchor clamp 上跳（旧根因）。
    LaunchedEffect(listState, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        snapshotFlow { autoScrollSignal }.collectLatest { signal ->
            if (!followBottom) return@collectLatest
            // 等一帧让新文本/新落库消息完成测量，scrollToItem 读到正确布局。
            kotlinx.coroutines.delay(0)
            val lastIndex = listState.layoutInfo.totalItemsCount - 1
            // 思考阶段：尾部 __active__ 是空 Box，跟随目标改为思考内容（倒数第二），
            // 让持续增长的思考文本始终贴底可见，避免跟随被顶出视口的空 Box 反复预跳抖动。
            val target = if (lastIndex > 0 && signal.thinkingTail) lastIndex - 1 else lastIndex
            snapToBottom(target)
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // 长对话分页：向上加载更早消息时必须保持视口。
    // 旧消息插入列表头部会把原有内容整体下移，若不做补偿，LazyColumn 以 anchor 为基线
    // 会让用户看到画面跳变（长对话最典型的「翻历史时画面乱跳」缺陷）。
    // 做法：触发前记下当前锚点 item 的 **key**（稳定标识，不随插入而变）与其相对视口顶部的
    // 像素偏移；新数据到达后按 key 重新定位并补偿新增 item 数，使同一消息回到原屏幕位置。
    var pendingAnchor by remember { mutableStateOf<String?>(null) }
    var pendingAnchorOffset by remember { mutableIntStateOf(0) }
    // 上一次已还原的 item 总数，用于算出本次分页新增了多少 item。
    var previousTotalItems by remember { mutableIntStateOf(0) }
    LaunchedEffect(firstVisibleItemIndex, messagesReady, messagesState.hasMore, messagesState.isLoadingMore) {
        if (messagesReady && firstVisibleItemIndex <= 3 && messagesState.hasMore && !messagesState.isLoadingMore) {
            val layout = listState.layoutInfo
            val anchor = layout.visibleItemsInfo.firstOrNull()
            if (anchor != null) {
                pendingAnchor = anchor.key?.toString()
                // 记录锚点顶边相对视口顶边的距离（含被裁掉的负值），补偿后据此还原。
                pendingAnchorOffset = anchor.offset
            }
            viewModel.loadMoreMessages()
        }
    }
    // 消息集合变化后执行一次视口还原：把锚点 item 滚回「原位置 - 新增 item 数」。
    LaunchedEffect(taskGroups.size) {
        val anchorKey = pendingAnchor ?: return@LaunchedEffect
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.key?.toString() == anchorKey }
        val added = (layout.totalItemsCount - previousTotalItems)
        if (info != null && added > 0) {
            listState.scrollToItem(info.index + added, pendingAnchorOffset)
        }
        previousTotalItems = layout.totalItemsCount
        pendingAnchor = null
    }

    val executionMode = settingsViewModel?.executionMode?.collectAsStateWithLifecycle()?.value
    val connectionState = settingsViewModel?.connectionState?.collectAsStateWithLifecycle()?.value
    val isRemote = executionMode == com.mini.me_core.feature.settings.data.repository.ExecutionMode.REMOTE_SSH

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
                onOpenRightSidebar = onOpenRightSidebar,
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
                    // 远程模式连接未就绪时显示连接状态占位，避免空白或旧工作区记录闪烁
                    if (isRemote && connectionState != null && connectionState != com.mini.me_core.core.container.api.ConnectionState.CONNECTED) {
                        RemoteConnectingPlaceholder(state = connectionState)
                    }
                } else if (messages.isEmpty()) {
                    WelcomeState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // 问题7b：收紧消息列表间距（上下 padding 8→4dp，item 间距 8→4dp）
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 4.dp
                        ),
                        // 问题7b：消息间间距收紧为 4dp（连续消息分组内更紧凑）
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(taskGroups, key = { it.taskId }, contentType = { "task" }) { group ->
                          // 消息长按快捷操作总线：重新生成 / 删除 / 分享 / 朗读
                          CompositionLocalProvider(LocalMessageActionBus provides messageActionBus) {
                            if (AgentFirstFeatureFlags.isTaskCardEnabled()) {
                                TaskCard(
                                    group = group,
                                    agentState = agentState,
                                    runningTools = runningTool,
                                    environmentSnapshots = environmentSnapshots,
                                    changes = changes,
                                    pendingPermission = pendingPermission,
                                    onToggleTask = { viewModel.toggleTask(it) },
                                    onToggleSubGroup = { taskId, subGroupId -> viewModel.toggleSubGroup(taskId, subGroupId) },
                                    onStop = { viewModel.stopAgent() },
                                    onViewChanges = {},
                                    onRetryTool = { messageId -> viewModel.retryTool(messageId) },
                                    onRetryTask = { taskId -> viewModel.retryTask(taskId) }
                                )
                            } else {
                                TaskAccordion(
                                    group = group,
                                    markdownCache = markdownCache,
                                    miniMeMarkdownCache = miniMeMarkdownCache,
                                    onToggleTask = { viewModel.toggleTask(it) },
                                    onToggleSubGroup = { taskId, subGroupId -> viewModel.toggleSubGroup(taskId, subGroupId) },
                                    onEditClick = { message -> startEditMessage(message) },
                                    onNewChatClick = { message -> viewModel.newChatAndSend(message.content) },
                                    onViewChanges = { fileDiffsForSheet = it },
                                    runningTool = runningTool,
                                    environmentSnapshots = environmentSnapshots,
                                    agentState = agentState
                                )
                            }
                          }
                        }
                        val reasoning = streamingReasoning
                        val showReasoning = reasoning != null && reasoning.isNotEmpty()
                        val streaming = streamingText
                        val showStreaming = streaming != null && streaming.hasVisibleContent()
                        // 问题23：思考动画连续性——思考阶段（有思考流、正文未开始）在思考气泡尾部打点；
                        // 正文一开始流式，live 翻转为 false，点位移交 StreamingBubble，全程不中断。
                        val reasoningLive = showReasoning && !showStreaming
                        if (showReasoning) {
                            item(key = "__reasoning__", contentType = "tail") {
                                // 流式实时：短文本默认展开边想边看，过长（超 REASONING_COLLAPSE_LINE_LIMIT）时由气泡内部自动折叠，不刷屏
                                ReasoningBubble(text = reasoning.orEmpty(), initiallyExpanded = true, cache = markdownCache, live = reasoningLive)
                            }
                        }
                        val showThinking = !showReasoning && !showStreaming && !isCompacting && isBusy && runningTool.isEmpty() && pendingPermission == null && pendingQuestion == null
                        val showRetrying = retryState != null && isBusy && !isCompacting && !showStreaming && !showReasoning
                        val tailKind = when {
                            showStreaming -> TailKind.STREAMING
                            isCompacting -> TailKind.COMPACTING
                            showRetrying -> TailKind.RETRYING
                            showThinking -> TailKind.THINKING
                            else -> TailKind.NONE
                        }
                        // 尾巴气泡：永远挂载 item，NONE 时为空 Box（0 高度）。
                        // 注意：不能按 tailKind 增删 item；流结束时 __active__ 移除会让 totalItemsCount
                        // 突减，LazyColumn 把 firstVisibleItemIndex 向下 clamp → 视口上跳（旧症状2根因）。
                        // 永远挂载则 item 数量稳定，只在 StreamingBubble 和空 Box 间切换，
                        // anchor 不会被 clamp。流结束落库后跟随 effect 会把新消息贴底。
                        item(key = "__active__", contentType = "tail") {
                            when (tailKind) {
                                TailKind.THINKING -> ThinkingBubble()
                                TailKind.STREAMING -> StreamingBubble(text = streaming ?: "")
                                TailKind.COMPACTING -> CompactionProgressBubble()
                                TailKind.RETRYING -> {
                                    val rs = retryState
                                    if (rs != null) RetryingBubble(rs.attempt, rs.maxRetries, rs.reason) else Box(Modifier)
                                }
                                TailKind.NONE -> Box(Modifier)
                            }
                        }
                    }
                }

                // 浮动按钮组（右下角贴边半透明胶囊，两图标 + 分隔线）：
                //  - 上箭头：跳到上一条用户消息（上方无用户消息时隐藏）；
                //  - 下箭头：回到底部（贴底时隐藏）。
                // 显隐由 fabReveal 统一管控：仅当用户主动上滑、滚动累计超约 0.4 屏才显示，
                // 停止交互 3 秒后自动隐藏；流式输出也照此规则（不特判常显）。
                val fabReveal = rememberScrollFabRevealState(listState) { isAtBottom }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = Spacing.sm, bottom = Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = fabReveal.revealed.value && (showJumpToUserMessage || showScrollToBottom),
                        enter = fadeIn(tween(200)) +
                            slideInVertically(tween(200)) { it / 2 } +
                            scaleIn(tween(200)),
                        exit = fadeOut(tween(150)) +
                            slideOutVertically(tween(150)) { it / 3 } +
                            scaleOut(tween(150))
                    ) {
                        ChatScrollFabPill(
                            showUp = showJumpToUserMessage,
                            showDown = showScrollToBottom,
                            onUp = jumpToUserMessage,
                            onDown = {
                                scope.launch {
                                    val targetIndex = listState.layoutInfo.totalItemsCount - 1
                                    snapToBottom(targetIndex)
                                    if (isAtBottom) followBottom = true
                                }
                            },
                            onInteract = fabReveal.notifyInteraction
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = changes.isNotEmpty(),
                enter = fadeIn(ScaledAnimation.tween(200)),
                exit = fadeOut()
            ) {
                ChangePreviewPanel(
                    changes = changes,
                    onApply = { viewModel.applyChanges(changes) },
                    onReject = { viewModel.rejectChanges() }
                )
            }

            StatusBanner(state = agentState)

            AnimatedVisibility(
                visible = pendingPermission != null,
                enter = fadeIn(ScaledAnimation.tween(200)),
                exit = fadeOut()
            ) {
                pendingPermission?.let { request ->
                    ToolPermissionPanel(
                        request = request,
                        onChoice = { choice -> viewModel.resolveToolPermission(request.id, choice) }
                    )
                }
            }

            AnimatedVisibility(
                visible = pendingQuestion != null,
                enter = fadeIn(ScaledAnimation.tween(200)),
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

            val planApproval by viewModel.pendingPlanApproval.collectAsStateWithLifecycle()
            AnimatedVisibility(
                visible = planApproval != null,
                enter = fadeIn(ScaledAnimation.tween(200)),
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

            // 编辑态提示条：显示正在编辑哪条消息，支持取消编辑
            editingMessageId?.let { editingId ->
                val editingMsg = messages.find { it.id == editingId }
                if (editingMsg != null) {
                    EditingMessageBanner(
                        snippet = editingMsg.content.take(80),
                        onCancel = { cancelEditMessage() }
                    )
                }
            }

            NormFlowIndicatorBar(onNavigateToNormFlow = onNavigateToNormFlow)

            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it; viewModel.updateInputDraft(it) },
                onSend = sendMessage,
                onStop = {
                    viewModel.stopAgent()
                    Toast.makeText(context, com.mini.me_core.R.string.chat_stopped, Toast.LENGTH_SHORT).show()
                },
                isBusy = isBusy,
                activeProvider = activeProvider,
                providers = providers,
                onSelectModel = { p, m ->
                    // F2.5：切换模型前弹出确认对话框，选择保留上下文 / 清空上下文
                    if (m != activeModel) pendingModelSwitch = p to m
                    else viewModel.setSessionProviderModel(p, m)
                },
                onNavigateToSettings = onNavigateToSettings,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) },
                reasoningEffort = reasoningEffort,
                onReasoningEffortChange = { viewModel.setSessionReasoningEffort(it) },
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = ::removePendingAttachment,
                canUploadFiles = canUploadFiles,
                canUploadImages = canUploadImages,
                onUploadFile = { filePicker.launch(arrayOf("*/*")) },
                onUploadImage = { imagePicker.launch(arrayOf("image/*")) },
                onTakePhoto = ::takePhoto,
                slashCommands = viewModel.slashCommands,
                queuedRequests = queuedRequests,
                onRemoveQueued = { viewModel.removeQueuedRequest(it) },
                tokenProgress = run {
                    val contextLimit = activeModelMetadata?.contextTokens ?: 0
                    if (contextLimit > 0) {
                        sessionLastInputTokens.toFloat() / contextLimit
                    } else 0f
                },
                onOpenSkills = { showConversationSkills = true },
                onOpenTemplates = { showTemplatePanel = true }
            )

            val skillsSessionId = currentSessionId
            if (showConversationSkills && skillsSessionId != null) {
                ConversationSkillsSheet(
                    viewModel = conversationSkillsViewModel,
                    sessionId = skillsSessionId,
                    onDismiss = { showConversationSkills = false }
                )
            }

            // F2.7：对话模板面板（点击模板填入输入框，不自动发送）
            if (showTemplatePanel) {
                ChatTemplatePanel(
                    onDismiss = { showTemplatePanel = false },
                    onUseTemplate = { content ->
                        inputText = content
                        viewModel.updateInputDraft(content)
                    }
                )
            }

            // F2.5：模型切换确认对话框
            pendingModelSwitch?.let { (p, m) ->
                val contextLimit = activeModelMetadata?.contextTokens ?: 0
                val usageRatio = if (contextLimit > 0) sessionLastInputTokens.toFloat() / contextLimit else 0f
                val overLong = usageRatio > 0.8f
                AlertDialog(
                    onDismissRequest = { pendingModelSwitch = null },
                    title = { Text(stringResource(R.string.model_switch_title)) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.model_switch_current_to_new, activeModel.ifBlank { "—" }, m),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.model_switch_context_tokens, formatTokenCount(sessionLastInputTokens)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (overLong) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.model_switch_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Column {
                            TextButton(onClick = {
                                viewModel.setSessionProviderModel(p, m)
                                pendingModelSwitch = null
                            }) { Text(stringResource(R.string.model_switch_keep)) }
                            TextButton(onClick = {
                                // 清空上下文：新会话显式绑定用户所选模型。
                                // 旧实现 setSessionProviderModel + newSession 两步走存在竞态：
                                // 非空会话时 setSessionProviderModel 不写默认模型，
                                // 新会话仍读旧默认模型，用户选的模型丢失。
                                viewModel.newSessionWithModel(p, m)
                                pendingModelSwitch = null
                            }) { Text(stringResource(R.string.model_switch_clear)) }
                            TextButton(onClick = { pendingModelSwitch = null }) {
                                Text(stringResource(R.string.chat_action_cancel))
                            }
                        }
                    },
                    dismissButton = null
                )
            }

            fileDiffsForSheet?.let { sheetData ->
                FileDiffSheet(
                    fileDiffs = sheetData.fileDiffs,
                    logs = sheetData.logs,
                    onDismiss = { fileDiffsForSheet = null }
                )
            }
        }
    }
}

/**
 * 编辑态提示条：展示正在编辑的消息摘要，提供取消编辑入口。
 * 出现在输入框上方，提示用户当前发送将「截断重发」。
 */
@Composable
private fun EditingMessageBanner(
    snippet: String,
    onCancel: () -> Unit
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(LocalCornerRadius.current.lg),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(IconSizeTokens.small)
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
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_edit_cancel),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(IconSizeTokens.small)
                )
            }
        }
    }
}

/**
 * 滚动按钮组显隐状态机：
 * - 仅当用户主动离开底部、向上浏览并使滚动累计位移超过约 [FAB_SCROLL_REVEAL_RATIO] 屏时才显示；
 * - 程序自动贴底滚动（isAtBottom 为 true）不累计，避免误触发；
 * - 任意一次滚动都会刷新最近交互时间；停止交互超过 [FAB_IDLE_HIDE_MS] 后自动隐藏。
 * notifyInteraction 供按钮点击时调用，避免点击后随即被闲置计时收起。
 */
private const val FAB_IDLE_HIDE_MS = 3000L
private const val FAB_SCROLL_REVEAL_RATIO = 0.4f

private data class ScrollFabRevealState(
    val revealed: androidx.compose.runtime.MutableState<Boolean>,
    val notifyInteraction: () -> Unit
)

@Composable
private fun rememberScrollFabRevealState(
    listState: LazyListState,
    isAtBottom: () -> Boolean
): ScrollFabRevealState {
    val revealed = remember { mutableStateOf(false) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }
    var scrollAccum by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(listState) {
        var prevIndex = listState.firstVisibleItemIndex
        var prevOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (idx, off) ->
            val delta = kotlin.math.abs((idx - prevIndex) * 100_000f + (off - prevOffset))
            prevIndex = idx
            prevOffset = off
            if (!isAtBottom()) {
                scrollAccum += delta
                lastInteractionAt = System.currentTimeMillis()
                val threshold = (listState.layoutInfo.viewportEndOffset * FAB_SCROLL_REVEAL_RATIO)
                    .coerceAtLeast(120f)
                if (scrollAccum >= threshold) {
                    revealed.value = true
                    scrollAccum = 0f
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lastInteractionAt }
            .debounce(FAB_IDLE_HIDE_MS)
            .collect { revealed.value = false }
    }

    return ScrollFabRevealState(
        revealed = revealed,
        notifyInteraction = { lastInteractionAt = System.currentTimeMillis() }
    )
}

/**
 * 右下角贴边半透明胶囊：上箭头（跳到上一条用户消息）+ 下箭头（回到底部），
 * 中间分隔线仅在两枚同时可见时出现。取代原两枚独立圆形 FAB，弱化视觉重量、减少遮挡。
 */
@Composable
private fun ChatScrollFabPill(
    showUp: Boolean,
    showDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shadowElevation = 2.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showUp,
                enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                PillIconButton(
                    icon = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.chat_jump_to_user_message),
                    onClick = onUp,
                    onInteract = onInteract
                )
            }
            if (showUp && showDown) {
                HorizontalDivider(
                    modifier = Modifier.width(20.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                )
            }
            AnimatedVisibility(
                visible = showDown,
                enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150))
            ) {
                PillIconButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.chat_scroll_to_bottom),
                    onClick = onDown,
                    onInteract = onInteract
                )
            }
        }
    }
}

@Composable
private fun PillIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pressBounce = rememberPressBounce()
    IconButton(
        onClick = {
            pressBounce.trigger()
            onInteract()
            onClick()
        },
        modifier = modifier
            .size(40.dp)
            .scale(pressBounce.scale),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 按钮按压回弹状态：按下时缩放到 0.92f，150ms 后自动复位。
 * 抽离为独立 rememberer，避免 [ScrollToBottomButton] 承载按压动画的协程与状态逻辑。
 * 复位协程由 [LaunchedEffect] 管理，随组合释放，不使用 GlobalScope。
 */
@Composable
private fun rememberPressBounce(): PressBounceState {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "pressBounce"
    )
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(150)
            pressed = false
        }
    }
    return PressBounceState(
        scale = scale,
        trigger = { pressed = true }
    )
}

private data class PressBounceState(
    val scale: Float,
    val trigger: () -> Unit
)


/**
 * 分享消息正文：走系统 ACTION_SEND，由用户选择目标应用。
 * 无可处理分享的 Activity 时静默忽略（不弹 Toast，避免打断对话流）。
 */
private fun shareMessageText(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(intent, null)
    if (chooser.resolveActivity(context.packageManager) != null) {
        context.startActivity(chooser)
    } else {
        FileLogger.e("AIChatPanel", "无可用的分享目标应用")
    }
}
