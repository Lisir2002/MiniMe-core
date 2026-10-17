package com.mini.me_core.feature.browser.presentation
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.AnimationScaleHolder
import com.mini.me_core.core.theme.ScaledAnimation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppConfirmDialog
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.browser.domain.AgentActionRecord
import com.mini.me_core.feature.browser.domain.BrowserBookmark
import com.mini.me_core.feature.browser.domain.BrowserController
import com.mini.me_core.feature.browser.domain.BrowserCredentialStore
import com.mini.me_core.feature.browser.domain.BrowserDownloadInfo
import com.mini.me_core.feature.browser.domain.BrowserHistoryEntry
import com.mini.me_core.feature.browser.domain.BrowserLoginPromptManager
import com.mini.me_core.feature.browser.domain.BrowserTabInfo
import com.mini.me_core.feature.browser.domain.BrowserTakeoverManager
import com.mini.me_core.feature.browser.domain.LoginPromptAnswer
import com.mini.me_core.feature.browser.domain.TakeoverAnswer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 浏览器 UI 偏好持久化：地址栏位置（顶部/底部），纯 UI 偏好，不涉及 BrowserController 状态。
 */
private object BrowserUiPrefs {
    private const val PREFS_NAME = "browser_ui_prefs"
    private const val KEY_ADDRESS_BAR_POSITION = "address_bar_position"
    const val POSITION_TOP = "top"
    const val POSITION_BOTTOM = "bottom"

    fun getAddressBarPosition(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ADDRESS_BAR_POSITION, POSITION_TOP) ?: POSITION_TOP
    }

    fun setAddressBarPosition(context: Context, position: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ADDRESS_BAR_POSITION, position).apply()
    }
}

/**
 * 内置服务浏览器页。
 *
 * 布局（R3 重构）：顶部两层 = 标签栏(40dp) + 地址栏(56dp，底部 2dp 加载进度)；
 * 底部一层 = 工具栏(56dp，主页/收藏/历史/下载/AI助手)。用户与模型共享同一个 WebView 会话。
 *
 * 同时承载三类异步交互弹窗：
 *  - 页面 alert/confirm（[BrowserController.pendingDialog]）；
 *  - 登录凭据输入（[BrowserLoginPromptManager.pendingPrompt]）；
 *  - 用户接管提示（[BrowserTakeoverManager.pending]）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceBrowserScreen(
    browserController: BrowserController,
    loginPromptManager: BrowserLoginPromptManager,
    takeoverManager: BrowserTakeoverManager,
    credentialStore: BrowserCredentialStore,
    previewManager: com.mini.me_core.feature.browser.domain.BrowserPreviewManager,
    operationController: com.mini.me_core.feature.browser.domain.BrowserOperationController,
    initialUrl: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by browserController.uiState.collectAsStateWithLifecycle()
    val tabs by browserController.tabsState.collectAsStateWithLifecycle()
    val agentStatus by browserController.agentStatus.collectAsStateWithLifecycle()
    val agentHistory by browserController.agentActionHistory.collectAsStateWithLifecycle()
    val pendingDialog by browserController.pendingDialog.collectAsStateWithLifecycle()
    val pendingHttpAuth by browserController.pendingHttpAuth.collectAsStateWithLifecycle()

    // 网页全屏（视频/游戏）：页面经 onShowCustomView 请求全屏时，控制器持有全屏 View，
    // 这里把它渲染到独立 Dialog 窗口；返回键优先退出网页全屏而非直接退出浏览器页。
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    DisposableEffect(browserController) {
        browserController.onFullscreenChange = { entering ->
            fullscreenView = if (entering) browserController.fullscreenView else null
        }
        onDispose { browserController.onFullscreenChange = null }
    }
    BackHandler(enabled = fullscreenView != null) {
        browserController.exitFullscreen()
    }
    val pendingLoginPrompt by loginPromptManager.pendingPrompt.collectAsStateWithLifecycle()
    val pendingTakeover by takeoverManager.pending.collectAsStateWithLifecycle()
    val downloads by browserController.downloads.collectAsStateWithLifecycle()
    val activeDownloadCount by browserController.activeDownloadCount.collectAsStateWithLifecycle()
    val pendingDownload by browserController.pendingDownload.collectAsStateWithLifecycle()
    val dialogDownloadId by browserController.dialogDownloadId.collectAsStateWithLifecycle()

    // F4.3 密码管理器（在现有加密凭据存储之上）
    val passwordManager = remember(credentialStore) {
        com.mini.me_core.feature.browser.domain.BrowserPasswordManager(credentialStore)
    }
    val pwmLocked by passwordManager.locked.collectAsStateWithLifecycle()
    val blockedCount by browserController.blockedCount.collectAsStateWithLifecycle()
    val consoleLogs by browserController.consoleLogs.collectAsStateWithLifecycle()
    val gestureSettings by browserController.gestureSettings.collectAsStateWithLifecycle()

    // 实时预览面板状态
    val previewFrame by previewManager.previewState.collectAsStateWithLifecycle()
    val operationActive by operationController.operationActive.collectAsStateWithLifecycle()
    val interruptRequested by operationController.interruptRequested.collectAsStateWithLifecycle()
    var previewVisible by remember { mutableStateOf(true) }
    var showInterruptedToast by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var addressText by remember { mutableStateOf(uiState.currentUrl) }

    // 「更多」菜单与各功能面板开关
    var showMore by remember { mutableStateOf(false) }
    var findVisible by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    var pendingCredentialDelete by remember { mutableStateOf<String?>(null) }
    var showZoom by remember { mutableStateOf(false) }
    var showAiPanel by remember { mutableStateOf(false) }
    var aiPaused by remember { mutableStateOf(false) }
    var showTabManager by remember { mutableStateOf(false) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    var confirmCloseOthers by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showDevTools by remember { mutableStateOf(false) }
    var devNetwork by remember { mutableStateOf(listOf<com.mini.me_core.feature.browser.domain.BrowserNetworkRecord>()) }
    var devDom by remember { mutableStateOf("") }
    var devStorage by remember { mutableStateOf("") }

    // 地址栏位置偏好（SharedPreferences 持久化）
    var addressBarAtBottom by remember {
        mutableStateOf(BrowserUiPrefs.getAddressBarPosition(context) == BrowserUiPrefs.POSITION_BOTTOM)
    }
    fun toggleAddressBarPosition() {
        addressBarAtBottom = !addressBarAtBottom
        BrowserUiPrefs.setAddressBarPosition(
            context,
            if (addressBarAtBottom) BrowserUiPrefs.POSITION_BOTTOM else BrowserUiPrefs.POSITION_TOP
        )
    }

    // 页面 URL 变化时同步地址栏
    LaunchedEffect(uiState.currentUrl) {
        if (addressText != uiState.currentUrl) addressText = uiState.currentUrl
    }

    // 首次进入：预创建首个激活标签，再按需导航
    LaunchedEffect(Unit) {
        browserController.ensureActiveTab()
        if (!initialUrl.isNullOrBlank()) {
            browserController.navigate(initialUrl)
        }
    }

    // 卸载时解除绑定（保留 WebView 与登录态）
    DisposableEffect(Unit) {
        // WebView 的 <select> 等原生弹层由 Chromium 用 WebView 自身 context 创建：
        // BrowserController 注入的是 Application context，没有窗口令牌与 Activity 主题，
        // 弹层会静默失败。页面可见期间把 context base 指向宿主 Activity，卸载时回退
        // （既恢复弹层，又避免持有已销毁的 Activity）。
        context.findActivity()?.let { browserController.attachHost(it) }
        onDispose {
            browserController.detachHost()
            browserController.unbind()
            previewManager.stopCapturing()
        }
    }

    // 模型操作浏览器时启动实时预览捕获，空闲时停止
    LaunchedEffect(agentStatus.active, uiState.activeTabId) {
        if (agentStatus.active && uiState.activeTabId.isNotBlank()) {
            val webView = browserController.getActiveWebView() ?: return@LaunchedEffect
            previewManager.startCapturing(webView)
        } else {
            previewManager.stopCapturing()
        }
    }

    // 中断后显示「已中断」提示 2 秒
    LaunchedEffect(interruptRequested) {
        if (interruptRequested) {
            showInterruptedToast = true
            kotlinx.coroutines.delay(2000)
            showInterruptedToast = false
        }
    }

    fun navigate() {
        val url = addressText.trim()
        if (url.isBlank()) return
        aiPaused = false
        scope.launch { browserController.navigate(url) }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) return
        scope.launch { browserController.navigate(url) }
    }

    fun switchTab(id: String) {
        if (id == uiState.activeTabId) return
        scope.launch { browserController.switchTab(id) }
    }

    fun closeTab(id: String) {
        scope.launch { browserController.closeTab(id) }
    }

    fun newTab() {
        scope.launch { browserController.newTab(null) }
    }

    fun closeOthersTabs(keepId: String) {
        scope.launch { browserController.closeOtherTabs(keepId) }
    }

    fun closeRightTabs(fromId: String) {
        scope.launch { browserController.closeRightTabs(fromId) }
    }

    fun closeAllTabs() {
        scope.launch { browserController.closeAllTabs() }
    }

    fun shareCurrent() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, uiState.title)
            putExtra(Intent.EXTRA_TEXT, uiState.currentUrl.ifBlank { uiState.title })
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    fun copyCurrentLink() {
        val url = uiState.currentUrl
        if (url.isBlank()) return
        clipboard.setText(AnnotatedString(url))
        Toast.makeText(context, context.getString(R.string.browser_link_copied), Toast.LENGTH_SHORT).show()
    }

    fun openDownload(info: BrowserDownloadInfo) {
        val file = browserController.downloadHostFile(info) ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, context.getString(R.string.browser_open_failed), Toast.LENGTH_SHORT).show() }
    }

    fun retryDownload(info: BrowserDownloadInfo) {
        browserController.retryDownload(info)
    }

    fun shareDownload(info: BrowserDownloadInfo) {
        val file = browserController.downloadHostFile(info) ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null)) }
    }

    // ===== 顶部栏（标签栏 + 地址栏） =====
    // 地址栏公共渲染：顶部模式置于 topBar（标签栏下方），底部模式置于 bottomBar（工具栏上方）。
    // 0.0.1.5 修复：此前仅有 topBar 承载，切到「底部」后顶部栏被动画隐藏、
    // 底部无渲染点，导致地址栏整体消失（用户报告的严重 bug）。
    val renderAddressBar: @Composable () -> Unit = {
        BrowserAddressBar(
            canGoBack = uiState.canGoBack,
            canGoForward = uiState.canGoForward,
            currentUrl = uiState.currentUrl,
            isLoading = uiState.isLoading,
            progress = uiState.progress,
            addressText = addressText,
            onAddressTextChange = { addressText = it },
            onNavigate = { navigate() },
            onGoBack = { browserController.goBack() },
            onNavigateBack = { onNavigateBack() },
            onGoForward = { browserController.goForward() },
            onStopLoading = { browserController.stopLoading() },
            onReload = { browserController.reload() },
            showMore = showMore,
            onShowMoreChange = { showMore = it },
            bookmarked = uiState.currentUrl.isNotBlank() && browserController.isBookmarked(uiState.currentUrl),
            incognito = uiState.incognito,
            desktopMode = uiState.desktopMode,
            addressBarAtBottom = addressBarAtBottom,
            onToggleAddressBar = { toggleAddressBarPosition() },
            onFind = { showMore = false; findVisible = true; findText = "" },
            onToggleBookmark = {
                showMore = false
                if (uiState.currentUrl.isNotBlank()) {
                    if (browserController.isBookmarked(uiState.currentUrl)) {
                        browserController.removeBookmark(uiState.currentUrl)
                    } else {
                        browserController.addBookmark()
                    }
                }
            },
            onCredentials = { showMore = false; showCredentials = true },
            onShare = { showMore = false; shareCurrent() },
            onCopyLink = { showMore = false; copyCurrentLink() },
            onIncognito = { browserController.setIncognito(!uiState.incognito) },
            onDesktopMode = { browserController.toggleDesktopMode() },
            onZoom = { showMore = false; showZoom = true },
            onInspect = { showMore = false; showDevTools = true },
            blockedCount = blockedCount,
            onPrivacy = { showMore = false; showPrivacy = true }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                Column {
                // 第1层：标签栏（40dp），始终在顶部
                BrowserTabBar(
                    tabs = tabs,
                    activeTabId = uiState.activeTabId,
                    incognito = uiState.incognito,
                    onSelect = { switchTab(it) },
                    onClose = { closeTab(it) },
                    onCloseOthers = { closeOthersTabs(it) },
                    onCloseRight = { closeRightTabs(it) },
                    onCloseAll = { confirmCloseAll = true },
                    onNewTab = { newTab() },
                    onOpenManager = { showTabManager = true }
                )
                // 地址栏：顶部模式时显示在标签栏下方，底部模式时隐藏（由 bottomBar 承载）
                AnimatedVisibility(
                    visible = !addressBarAtBottom,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    renderAddressBar()
                }
                // 页内查找条：始终在顶部区域，动画展开/收起
                AnimatedVisibility(visible = findVisible, enter = fadeIn(), exit = fadeOut()) {
                    FindOnPageBar(
                        text = findText,
                        onTextChange = { findText = it; browserController.findOnPage(it) },
                        onPrev = { browserController.findNextOnPage(false) },
                        onNext = { browserController.findNextOnPage(true) },
                        onClose = {
                            findVisible = false
                            findText = ""
                            browserController.clearFindOnPage()
                        }
                    )
                }
                // 模型操作状态条（AI 操作中时顶部细条提示）
                AnimatedVisibility(
                    visible = agentStatus.active,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = if (showInterruptedToast) "已中断"
                                else agentStatus.text.ifBlank { stringResource(R.string.browser_agent_working) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (operationActive) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    IconButton(
                                        onClick = { operationController.requestInterrupt() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "中断操作",
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        },
        bottomBar = {
            // 地址栏底部模式：地址栏 + 底部工具栏（主页/书签/历史/下载/AI）。
            // 修复前此处缺失：切到「底部」后地址栏无任何渲染点而整体消失。
            if (addressBarAtBottom) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        renderAddressBar()
                        BrowserBottomToolbar(
                            agentActive = agentStatus.active,
                            activeDownloadCount = activeDownloadCount,
                            onHome = { newTab() },
                            onBookmarks = { showBookmarks = true },
                            onHistory = { showHistory = true },
                            onDownloads = { showDownloads = true },
                            onAi = { showAiPanel = true }
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // WebView 容器：按激活标签 key 切换，每个标签独占一个 WebView 实例。
            if (uiState.activeTabId.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    key(uiState.activeTabId) {
                        val webView = remember { browserController.bind() }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .browserEdgeGesture(
                                    enabled = gestureSettings.edgeSwipe,
                                    sensitivity = gestureSettings.sensitivity,
                                    onBack = { browserController.goBack() },
                                    onForward = { browserController.goForward() }
                                )
                                .pullToRefreshGesture(
                                    enabled = gestureSettings.pullToRefresh,
                                    sensitivity = gestureSettings.sensitivity,
                                    atTop = { browserController.activeWebViewScrollY() == 0 },
                                    onRefresh = { browserController.reload() }
                                )
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            AndroidView(
                                factory = { webView },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                        // 新标签页主页：当前标签尚无 URL 时展示搜索/快捷方式/最近访问
                    if (uiState.currentUrl.isBlank()) {
                        BrowserHomePage(
                            addressText = addressText,
                            onAddressChange = { addressText = it },
                            onNavigate = { navigate() },
                            bookmarks = remember { browserController.bookmarks() },
                            recentVisits = remember { browserController.history().take(6) },
                            onOpen = { openUrl(it) }
                        )
                    }
                    // 主框架加载失败：呈现错误页 + 重试。
                    // 默认实现只写日志，用户看到的是永久白屏（与 <select> 同类的静默失效）。
                    uiState.loadError?.let { err ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(Spacing.xl)
                            ) {
                                Text(
                                    text = stringResource(R.string.browser_error_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                Button(onClick = { browserController.reload() }) {
                                    Text(stringResource(R.string.browser_retry))
                                }
                            }
                        }
                    }
                    // 实时浏览器预览面板：模型操作时右下角悬浮显示当前页面截图
                    if (agentStatus.active && previewVisible) {
                        BrowserPreviewPanel(
                            previewFrame = previewFrame,
                            onDismiss = { previewVisible = false },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Spacing.sm)
                        )
                    }
                }
            }
        }
    }

    // ── 页面 alert/confirm/prompt 对话框 ──
    // prompt 必须带输入框：默认实现丢弃 prompt 时 JS 侧永远拿不到输入值，
    // 页面表现为「点了没反应」，与 <select> 属同一类宿主侧静默失效。
    pendingDialog?.let { d ->
        var promptText by remember(d.id) { mutableStateOf(d.defaultValue ?: "") }
        AlertDialog(
            // 返回键 = 取消（对齐 Chrome）：alert 只有「确定」一个出口，保持不可绕过的
            // 模态；confirm/prompt 允许返回取消。
            onDismissRequest = {
                if (d.type != "alert") {
                    scope.launch { browserController.handleDialog(false) }
                }
            },
            title = {
                Text(
                    when (d.type) {
                        "prompt" -> stringResource(R.string.browser_dialog_prompt_title)
                        "alert" -> stringResource(R.string.browser_dialog_alert)
                        else -> stringResource(R.string.browser_dialog_confirm_title)
                    }
                )
            },
            text = {
                if (d.type == "prompt") {
                    Column {
                        Text(d.message)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = {
                                promptText = it
                                browserController.setPromptInput(it)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Text(d.message)
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { browserController.handleDialog(true) } }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                if (d.type != "alert") {
                    TextButton(onClick = { scope.launch { browserController.handleDialog(false) } }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        )
    }

    // ── 网页全屏渲染（视频全屏 / 游戏全屏） ──
    // 不接管时全屏请求被静默丢弃，页面表现为「点了全屏没反应」。
    fullscreenView?.let { fsView ->
        Dialog(
            // 全屏窗口内的返回键 = 退出网页全屏（Dialog 窗口会先消费返回键，
            // 不接这条路径用户按返回毫无反应）。
            onDismissRequest = { browserController.exitFullscreen() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AndroidView(
                factory = { fsView },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }

    // ── HTTP 认证对话框（401 Basic/Digest） ──
    // 不接管时 WebView 静默取消认证，页面表现为「输入网址后一片空白」。
    pendingHttpAuth?.let { auth ->
        var authUser by remember(auth.id) { mutableStateOf("") }
        var authPass by remember(auth.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { browserController.resolveHttpAuth(false) },
            title = { Text(stringResource(R.string.browser_http_auth_title, auth.host ?: "")) },
            text = {
                Column {
                    if (!auth.realm.isNullOrBlank()) {
                        Text(auth.realm)
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }
                    OutlinedTextField(
                        value = authUser,
                        onValueChange = { authUser = it },
                        label = { Text(stringResource(R.string.browser_http_auth_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = authPass,
                        onValueChange = { authPass = it },
                        label = { Text(stringResource(R.string.browser_http_auth_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { browserController.resolveHttpAuth(true, authUser, authPass) }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { browserController.resolveHttpAuth(false) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── 登录凭据输入对话框 ──
    pendingLoginPrompt?.let { p ->
        LoginCredentialDialog(
            host = p.host,
            onConfirm = { username, password ->
                loginPromptManager.resolve(
                    p.requestId,
                    LoginPromptAnswer(username = username, password = password, cancelled = false)
                )
            },
            onCancel = { loginPromptManager.cancel(p.requestId) }
        )
    }

    // ── 用户接管提示对话框 ──
    pendingTakeover?.let { p ->
        AlertDialog(
            onDismissRequest = { takeoverManager.cancel(p.requestId) },
            title = { Text(p.title) },
            text = { Text(p.message) },
            confirmButton = {
                TextButton(onClick = { takeoverManager.resolve(p.requestId, TakeoverAnswer(confirmed = true)) }) {
                    Text(stringResource(R.string.browser_takeover_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { takeoverManager.cancel(p.requestId) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── 通用下载确认弹窗（WebView 触发下载） ──
    pendingDownload?.let { pd ->
        com.mini.me_core.core.download.DownloadConfirmDialog(
            fileName = pd.fileName,
            fileSizeText = null,
            sourceUrl = pd.url,
            onConfirm = { browserController.confirmPendingDownload() },
            onDismiss = { browserController.cancelPendingDownload() },
        )
    }

    // ── 通用下载进度弹窗 ──
    dialogDownloadId?.let { id ->
        val dl = downloads.firstOrNull { it.id == id }
        if (dl != null) {
            val status = when (dl.status) {
                "downloading" -> com.mini.me_core.core.download.DownloadStatus.DOWNLOADING
                "done" -> com.mini.me_core.core.download.DownloadStatus.COMPLETED
                "error" -> com.mini.me_core.core.download.DownloadStatus.FAILED
                "cancelled" -> com.mini.me_core.core.download.DownloadStatus.CANCELLED
                else -> com.mini.me_core.core.download.DownloadStatus.DOWNLOADING
            }
            com.mini.me_core.core.download.DownloadProgressDialog(
                task = com.mini.me_core.core.download.DownloadTask(
                    id = dl.id,
                    title = dl.fileName,
                    url = dl.url,
                    totalBytes = dl.totalBytes,
                    downloadedBytes = dl.downloadedBytes,
                    speedBytesPerSec = dl.speedBps,
                    status = status,
                    errorMessage = dl.error.ifBlank { null },
                    localPath = dl.path.ifBlank { null },
                ),
                onCancel = {
                    browserController.cancelDownload(dl.id)
                    browserController.dismissDialogDownload()
                },
                onOpen = {
                    openDownload(dl)
                    browserController.dismissDialogDownload()
                },
                onRetry = {
                    browserController.retryDownload(dl)
                },
                onDismiss = { browserController.dismissDialogDownload() },
            )
        }
    }

    // ── 历史记录面板（BottomSheet） ──
    if (showHistory) {
        HistoryBottomSheet(
            entries = remember { browserController.history() },
            onOpen = { url -> showHistory = false; openUrl(url) },
            onClear = {
                browserController.clearHistory()
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    // ── 收藏夹面板（BottomSheet） ──
    if (showBookmarks) {
        BookmarksBottomSheet(
            bookmarks = remember { browserController.bookmarks() },
            onOpen = { url -> showBookmarks = false; openUrl(url) },
            onRemove = { browserController.removeBookmark(it) },
            onDismiss = { showBookmarks = false }
        )
    }

    // ── 下载管理面板（F4.2） ──
    if (showDownloads) {
        DownloadsBottomSheet(
            downloads = downloads,
            onOpen = { openDownload(it) },
            onShare = { shareDownload(it) },
            onRetry = { retryDownload(it) },
            onPause = { browserController.pauseDownload(it.id) },
            onResume = { browserController.resumeDownload(it.id) },
            onCancel = { browserController.cancelDownload(it.id) },
            onDelete = { browserController.deleteDownload(it.id) },
            onClear = { browserController.clearDownloads() },
            onDismiss = { showDownloads = false }
        )
    }

    // ── 标签管理面板（F4.1：网格缩略图 + 批量操作） ──
    if (showTabManager) {
        TabManagerSheet(
            tabs = tabs,
            activeTabId = uiState.activeTabId,
            incognito = uiState.incognito,
            onSelect = { id -> showTabManager = false; switchTab(id) },
            onClose = { closeTab(it) },
            onCloseOthers = { showTabManager = false; closeOthersTabs(it) },
            onCloseAll = { confirmCloseAll = true },
            onDismiss = { showTabManager = false }
        )
    }

    // ── 全部关闭 / 关闭其他 确认对话框（F4.1） ──
    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text(stringResource(R.string.browser_tab_close_all)) },
            text = { Text(stringResource(R.string.browser_tab_close_all_confirm, tabs.size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloseAll = false
                    closeAllTabs()
                }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloseAll = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
    if (confirmCloseOthers) {
        AlertDialog(
            onDismissRequest = { confirmCloseOthers = false },
            title = { Text(stringResource(R.string.browser_tab_close_others)) },
            text = { Text(stringResource(R.string.browser_tab_close_others_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCloseOthers = false
                    closeOthersTabs(uiState.activeTabId)
                }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloseOthers = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ── AI 操作面板（BottomSheet） ──
    if (showAiPanel) {
        AiActionPanel(
            history = agentHistory,
            agentActive = agentStatus.active,
            paused = aiPaused,
            onPause = {
                browserController.clearAgentActionHistory()
                aiPaused = true
            },
            onTakeover = {
                aiPaused = false
                showAiPanel = false
            },
            onDismiss = { showAiPanel = false }
        )
    }

    // ── 开发者工具面板（F4.7） ──
    if (showDevTools) {
        DevToolsPanel(
            consoleLogs = consoleLogs,
            network = devNetwork,
            domSummary = devDom,
            storageDump = devStorage,
            onEval = { browserController.evalJs(it) {} },
            onRefreshNetwork = { browserController.networkRecords { devNetwork = it } },
            onRefreshDom = { browserController.domSummary { devDom = it } },
            onRefreshStorage = { browserController.storageDump { devStorage = it } },
            onClearConsole = { browserController.clearConsole() },
            onDismiss = { showDevTools = false }
        )
    }

    // ── 隐私与广告拦截面板（F4.5） ──
    if (showPrivacy) {
        PrivacySettingsSheet(
            privacy = browserController.privacy(),
            blockedCount = blockedCount,
            gesture = gestureSettings,
            onGesture = { browserController.setGestureSettings(it) },
            onDismiss = { showPrivacy = false }
        )
    }

    // ── 密码管理面板（F4.3） ──
    if (showCredentials) {
        PasswordListScreen(
            passwords = remember(showCredentials, pwmLocked) { passwordManager.all() },
            locked = pwmLocked,
            onUnlock = { passwordManager.unlock() },
            onFill = { sp ->
                browserController.autoFillCredentials(sp.username, sp.password)
                showCredentials = false
            },
            onDelete = { host -> pendingCredentialDelete = host },
            onDismiss = { showCredentials = false }
        )
    }

    // ── 删除凭据二次确认（不可逆） ──
    pendingCredentialDelete?.let { host ->
        AppConfirmDialog(
            title = stringResource(R.string.browser_credentials_delete),
            message = stringResource(R.string.browser_credentials_delete_confirm, host),
            confirmText = stringResource(R.string.common_confirm),
            cancelText = stringResource(R.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                passwordManager.delete(host)
                pendingCredentialDelete = null
            },
            onDismiss = { pendingCredentialDelete = null },
        )
    }

    // ── 页面缩放面板（保留 AlertDialog） ──
    if (showZoom) {
        ZoomDialog(
            percent = uiState.textZoom,
            onLess = { browserController.setTextZoom(uiState.textZoom - 10) },
            onMore = { browserController.setTextZoom(uiState.textZoom + 10) },
            onReset = { browserController.setTextZoom(100) },
            onDismiss = { showZoom = false }
        )
    }
}

// ===== 顶部标签栏（第1层） =====

/**
 * 沿 ContextWrapper 链找出宿主 Activity。
 *
 * Compose 的 `LocalContext.current` 不保证直接是 Activity（可能是 ContextThemeWrapper
 * 等包装），而 WebView 原生弹层（`<select>` 下拉等）必须拿到真正的 Activity context。
 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

