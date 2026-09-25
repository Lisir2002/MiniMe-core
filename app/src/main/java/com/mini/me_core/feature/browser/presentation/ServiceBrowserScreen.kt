package com.mini.me_core.feature.browser.presentation
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.sp
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
    initialUrl: String? = null,
    onNavigateBack: () -> Unit
) {
    val uiState by browserController.uiState.collectAsStateWithLifecycle()
    val tabs by browserController.tabsState.collectAsStateWithLifecycle()
    val agentStatus by browserController.agentStatus.collectAsStateWithLifecycle()
    val agentHistory by browserController.agentActionHistory.collectAsStateWithLifecycle()
    val pendingDialog by browserController.pendingDialog.collectAsStateWithLifecycle()
    val pendingLoginPrompt by loginPromptManager.pendingPrompt.collectAsStateWithLifecycle()
    val pendingTakeover by takeoverManager.pending.collectAsStateWithLifecycle()
    val downloads by browserController.downloads.collectAsStateWithLifecycle()
    val activeDownloadCount by browserController.activeDownloadCount.collectAsStateWithLifecycle()

    // F4.3 密码管理器（在现有加密凭据存储之上）
    val passwordManager = remember(credentialStore) {
        com.mini.me_core.feature.browser.domain.BrowserPasswordManager(credentialStore)
    }
    val pwmLocked by passwordManager.locked.collectAsStateWithLifecycle()
    val blockedCount by browserController.blockedCount.collectAsStateWithLifecycle()
    val consoleLogs by browserController.consoleLogs.collectAsStateWithLifecycle()
    val gestureSettings by browserController.gestureSettings.collectAsStateWithLifecycle()

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
        onDispose { browserController.unbind() }
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
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
                // 地址栏：顶部模式时显示在标签栏下方，底部模式时隐藏（移至 bottomBar）
                AnimatedVisibility(
                    visible = !addressBarAtBottom,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
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
                        onInspect = { showMore = false; showDevTools = true }
                    )
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
            }
        },
        // ===== 底部工具栏（56dp） + 可选底部地址栏 =====
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
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
                                text = agentStatus.text.ifBlank { stringResource(R.string.browser_agent_working) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // 地址栏：底部模式时显示在工具栏上方
                AnimatedVisibility(
                    visible = addressBarAtBottom,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
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
                        onInspect = { showMore = false; showDevTools = true }
                    )
                }
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
                        // F4.4 阅读模式入口 + F4.5 隐私盾牌（右上角悬浮）
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm),
                            horizontalAlignment = Alignment.End
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    IconButton(onClick = { showPrivacy = true }) {
                                        Icon(
                                            Icons.Rounded.Shield,
                                            contentDescription = stringResource(R.string.browser_privacy_title),
                                            tint = if (blockedCount > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    if (blockedCount > 0) {
                                        Text(
                                            text = blockedCount.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .padding(top = 4.dp, end = 4.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
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
                }
            }
        }
    }

    // ── 页面 alert/confirm 对话框 ──
    pendingDialog?.let { d ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (d.type == "alert") stringResource(R.string.browser_dialog_alert) else stringResource(R.string.browser_dialog_confirm_title)) },
            text = { Text(d.message) },
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
            onDelete = { host -> passwordManager.delete(host) },
            onDismiss = { showCredentials = false }
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

/** 浏览器标签栏：40dp 高，横向滚动，标签可切换 / 关闭 / 新建；长按弹出批量操作；>5 个标签显示管理入口。 */
@Composable
private fun BrowserTabBar(
    tabs: List<BrowserTabInfo>,
    activeTabId: String,
    incognito: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseRight: (String) -> Unit,
    onCloseAll: () -> Unit,
    onNewTab: () -> Unit,
    onOpenManager: () -> Unit
) {
    var menuTabId by remember { mutableStateOf<String?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabChip(
                    tab = tab,
                    active = tab.id == activeTabId,
                    incognito = incognito,
                    menuExpanded = menuTabId == tab.id,
                    onMenuDismiss = { menuTabId = null },
                    onClick = { onSelect(tab.id) },
                    onLongClick = { menuTabId = tab.id },
                    onClose = { onClose(tab.id) },
                    onCloseOthers = { menuTabId = null; onCloseOthers(tab.id) },
                    onCloseRight = { menuTabId = null; onCloseRight(tab.id) },
                    onCloseAll = { menuTabId = null; onCloseAll() }
                )
            }
        }
        // 标签数量 >5 时显示标签管理入口（数字角标）
        if (tabs.size > 5) {
            Box(contentAlignment = Alignment.TopEnd) {
                IconButton(onClick = onOpenManager, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.GridView,
                        contentDescription = stringResource(R.string.browser_tab_overview),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, end = 6.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabs.size.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        IconButton(onClick = onNewTab, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.browser_new_tab),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 单个标签胶囊：激活高亮 + 底部指示条；支持单击切换 / 长按菜单。 */
@Composable
private fun TabChip(
    tab: BrowserTabInfo,
    active: Boolean,
    incognito: Boolean,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseRight: () -> Unit,
    onCloseAll: () -> Unit
) {
    Box {
        Column(
            modifier = Modifier
                .width(120.dp)
                .height(40.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (incognito) Icons.Rounded.PrivacyTip else Icons.Rounded.Public,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = tab.title.ifBlank { hostOf(tab.url).ifBlank { stringResource(R.string.browser_tab_empty) } },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.browser_close_tab),
                        modifier = Modifier.size(16.dp),
                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 激活标签底部 2dp 主色指示条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_close_tab)) },
                onClick = onClose
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_tab_close_others)) },
                onClick = onCloseOthers
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_tab_close_right)) },
                onClick = onCloseRight
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_tab_close_all)) },
                onClick = onCloseAll
            )
        }
    }
}

/** 从 URL 中提取 host（用于标签/列表展示）。 */
private fun hostOf(url: String): String = runCatching {
    val u = url.trim()
    val start = if (u.startsWith("http://")) 7 else if (u.startsWith("https://")) 8 else 0
    u.substring(start).substringBefore('/').substringBefore(':').ifBlank { u }
}.getOrDefault(url)

// ===== 底部工具栏 =====

/** 底部工具栏：56dp，5 个等分按钮。AI 操作中时图标外圈脉冲 + 底部圆点。下载按钮带未完成数角标。 */
@Composable
private fun BrowserBottomToolbar(
    agentActive: Boolean,
    activeDownloadCount: Int,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onAi: () -> Unit
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.browser_bottom_home),
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
            ToolbarButton(
                icon = Icons.Rounded.BookmarkBorder,
                label = stringResource(R.string.browser_bottom_bookmarks),
                onClick = onBookmarks,
                modifier = Modifier.weight(1f)
            )
            ToolbarButton(
                icon = Icons.Rounded.History,
                label = stringResource(R.string.browser_bottom_history),
                onClick = onHistory,
                modifier = Modifier.weight(1f)
            )
            ToolbarButton(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.browser_bottom_downloads),
                onClick = onDownloads,
                badgeCount = activeDownloadCount,
                modifier = Modifier.weight(1f)
            )
            AiToolbarButton(
                active = agentActive,
                label = stringResource(R.string.browser_bottom_ai),
                onClick = onAi,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** AI 助手按钮：操作中时图标脉冲动画 + 底部主色圆点。 */
@Composable
private fun AiToolbarButton(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "aiPulse")
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aiScale"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.SmartToy,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            if (active) {
                Icon(
                    Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size((22 * scale).dp)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (active) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

// ===== 地址栏（可放置于顶部或底部） =====

/**
 * 浏览器地址栏：后退/前进 + 地址输入框（含锁图标、停止/刷新） + 更多菜单 + 底部加载进度线。
 * 可在 topBar（标签栏下方）或 bottomBar（工具栏上方）中条件性放置。
 */
@Composable
private fun BrowserAddressBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    currentUrl: String,
    isLoading: Boolean,
    progress: Int,
    addressText: String,
    onAddressTextChange: (String) -> Unit,
    onNavigate: () -> Unit,
    onGoBack: () -> Unit,
    onNavigateBack: () -> Unit,
    onGoForward: () -> Unit,
    onStopLoading: () -> Unit,
    onReload: () -> Unit,
    showMore: Boolean,
    onShowMoreChange: (Boolean) -> Unit,
    bookmarked: Boolean,
    incognito: Boolean,
    desktopMode: Boolean,
    addressBarAtBottom: Boolean,
    onToggleAddressBar: () -> Unit,
    onFind: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCredentials: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onIncognito: () -> Unit,
    onDesktopMode: () -> Unit,
    onZoom: () -> Unit,
    onInspect: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // 后退按钮：可后退则 goBack，否则退出浏览器页
            IconButton(
                onClick = { if (canGoBack) onGoBack() else onNavigateBack() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onGoForward,
                enabled = canGoForward,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.browser_forward),
                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            // 地址 pill（48dp 高）：左侧锁图标 + 右侧 停止/刷新 二合一
            val isHttps = currentUrl.startsWith("https://")
            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressTextChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.browser_address_hint)) },
                leadingIcon = {
                    Icon(
                        if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isHttps) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { if (isLoading) onStopLoading() else onReload() }
                    ) {
                        Icon(
                            if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                            contentDescription = if (isLoading)
                                stringResource(R.string.browser_stop_loading)
                            else stringResource(R.string.browser_refresh),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                shape = RoundedCornerShape(LocalCornerRadius.current.lg),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            // 「更多」菜单入口
            Box {
                IconButton(
                    onClick = { onShowMoreChange(true) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.browser_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                BrowserMoreMenu(
                    expanded = showMore,
                    onDismiss = { onShowMoreChange(false) },
                    bookmarked = bookmarked,
                    incognito = incognito,
                    desktopMode = desktopMode,
                    addressBarAtBottom = addressBarAtBottom,
                    onToggleAddressBar = onToggleAddressBar,
                    onFind = onFind,
                    onToggleBookmark = onToggleBookmark,
                    onCredentials = onCredentials,
                    onShare = onShare,
                    onCopyLink = onCopyLink,
                    onIncognito = onIncognito,
                    onDesktopMode = onDesktopMode,
                    onZoom = onZoom,
                    onInspect = onInspect
                )
            }
        }
        // 2dp 加载进度线（不占额外高度）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { (progress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ===== 更多菜单（精简为 8 项） =====

/** 地址栏「更多」下拉菜单：页内查找 / 收藏本页 / 凭据 / 分享 / 复制链接 / 无痕 / 桌面版 / 缩放 / 地址栏位置。 */
@Composable
private fun BrowserMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    bookmarked: Boolean,
    incognito: Boolean,
    desktopMode: Boolean,
    addressBarAtBottom: Boolean,
    onToggleAddressBar: () -> Unit,
    onFind: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCredentials: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onIncognito: () -> Unit,
    onDesktopMode: () -> Unit,
    onZoom: () -> Unit,
    onInspect: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_find)) },
            onClick = onFind,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_devtools_inspect)) },
            onClick = onInspect,
            leadingIcon = { Icon(Icons.Rounded.Article, null) }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (bookmarked) stringResource(R.string.browser_remove_bookmark)
                    else stringResource(R.string.browser_add_bookmark)
                )
            },
            onClick = onToggleBookmark,
            leadingIcon = {
                Icon(
                    if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    null
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_credentials)) },
            onClick = onCredentials,
            leadingIcon = { Icon(Icons.Rounded.Key, null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_share)) },
            onClick = onShare,
            leadingIcon = { Icon(Icons.Rounded.Share, null) }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_copy_link)) },
            onClick = onCopyLink,
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_incognito)) },
            onClick = onIncognito,
            leadingIcon = { Icon(Icons.Rounded.PrivacyTip, null) },
            trailingIcon = {
                Switch(checked = incognito, onCheckedChange = { onIncognito() }, modifier = Modifier.size(32.dp))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_desktop_mode)) },
            onClick = onDesktopMode,
            leadingIcon = { Icon(Icons.Rounded.DesktopWindows, null) },
            trailingIcon = {
                Switch(checked = desktopMode, onCheckedChange = { onDesktopMode() }, modifier = Modifier.size(32.dp))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_zoom)) },
            onClick = onZoom,
            leadingIcon = { Icon(Icons.Rounded.ZoomIn, null) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.browser_address_bar_position)) },
            onClick = onToggleAddressBar,
            leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
            trailingIcon = {
                Text(
                    if (addressBarAtBottom) stringResource(R.string.browser_address_bar_bottom)
                    else stringResource(R.string.browser_address_bar_top),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

// ===== 页内查找条 =====

/** 页内查找条：输入框 + 上/下一个 + 关闭。 */
@Composable
private fun FindOnPageBar(
    text: String,
    onTextChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.browser_find_hint)) },
            shape = RoundedCornerShape(LocalCornerRadius.current.lg),
            textStyle = MaterialTheme.typography.bodySmall
        )
        IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.browser_find_prev)
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.browser_find_next)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.browser_find_close)
            )
        }
    }
}

// ===== 新标签页主页 =====

/** 新标签页主页：大搜索框 + 快捷方式网格 + 最近访问卡片 + 收藏列表。 */
@Composable
private fun BrowserHomePage(
    addressText: String,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    bookmarks: List<BrowserBookmark>,
    recentVisits: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg),
        contentPadding = PaddingValues(vertical = Spacing.md)
    ) {
        item {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = addressText,
                onValueChange = onAddressChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.browser_home_search_hint)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onNavigate() }),
                shape = RoundedCornerShape(LocalCornerRadius.current.pill),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        // 快捷方式（收藏夹前 8 个，4 列网格）
        item {
            Text(
                text = stringResource(R.string.browser_home_shortcuts),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        val shortcuts = bookmarks.take(8)
        if (shortcuts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.browser_home_empty_shortcuts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.lg))
            }
        } else {
            shortcuts.chunked(4).forEach { rowItems ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowItems.forEach { bm ->
                            ShortcutCell(
                                title = bm.title.ifBlank { bm.url },
                                onClick = { onOpen(bm.url) },
                                modifier = Modifier.width(64.dp)
                            )
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.width(64.dp)) }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
        }

        // 最近访问（横向滚动卡片）
        item {
            Text(
                text = stringResource(R.string.browser_home_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        if (recentVisits.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.browser_home_empty_recent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.lg))
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(recentVisits, key = { it.id }) { entry ->
                        RecentCard(
                            title = entry.title.ifBlank { entry.url },
                            url = entry.url,
                            onClick = { onOpen(entry.url) }
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        // 收藏列表
        item {
            Text(
                text = stringResource(R.string.browser_bookmarks),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        if (bookmarks.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.browser_home_empty_bookmarks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(bookmarks, key = { it.id }) { bm ->
                HomeLinkRow(
                    title = bm.title.ifBlank { bm.url },
                    subtitle = bm.url,
                    onClick = { onOpen(bm.url) }
                )
            }
        }
    }
}

/** 主页快捷方式单元格：64dp 宽，首字母圆形图标 + 文字。 */
@Composable
private fun ShortcutCell(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 主页最近访问卡片：120dp 宽。 */
@Composable
private fun RecentCard(
    title: String,
    url: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Icon(
                Icons.Rounded.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 主页单条快捷入口。 */
@Composable
private fun HomeLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ===== 历史 / 收藏 / 下载 BottomSheet =====

/** 历史记录面板：按今天/昨天/更早分组，列表 + 回跳 + 清空。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(
    entries: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var confirmClear by remember { mutableStateOf(false) }

    // 按日期分组：0=今天 1=昨天 2=更早
    val groups = remember(entries) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
        entries.groupBy { e ->
            when {
                e.timestamp >= todayStart -> 0
                e.timestamp >= yesterdayStart -> 1
                else -> 2
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.browser_history),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.browser_clear_history))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.browser_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    listOf(
                        0 to R.string.browser_history_today,
                        1 to R.string.browser_history_yesterday,
                        2 to R.string.browser_history_earlier
                    ).forEach { (group, titleRes) ->
                        val items = groups[group].orEmpty()
                        if (items.isNotEmpty()) {
                            item(key = "header_$group") {
                                Text(
                                    stringResource(titleRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                                )
                            }
                            items(items, key = { it.id }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpen(entry.url) }
                                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title.ifBlank { entry.url },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.browser_clear_history)) },
            text = { Text(stringResource(R.string.browser_clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 收藏夹面板：列表 + 回跳 + 删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksBottomSheet(
    bookmarks: List<BrowserBookmark>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var removeTarget by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                stringResource(R.string.browser_bookmarks),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.sm))
            if (bookmarks.isEmpty()) {
                Text(
                    stringResource(R.string.browser_bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(bm.url) }
                                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bm.title.ifBlank { bm.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bm.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { removeTarget = bm.url }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.common_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.browser_remove_bookmark)) },
            text = { Text(removeTarget!!) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(removeTarget!!)
                    removeTarget = null
                }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 下载管理面板（F4.2）：分组列表 + 进度/速度 + 暂停/继续/取消/打开/分享/删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsBottomSheet(
    downloads: List<BrowserDownloadInfo>,
    onOpen: (BrowserDownloadInfo) -> Unit,
    onShare: (BrowserDownloadInfo) -> Unit,
    onRetry: (BrowserDownloadInfo) -> Unit,
    onPause: (BrowserDownloadInfo) -> Unit,
    onResume: (BrowserDownloadInfo) -> Unit,
    onCancel: (BrowserDownloadInfo) -> Unit,
    onDelete: (BrowserDownloadInfo) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // 分组：0 进行中（下载中/暂停） 1 已完成 2 失败/取消
    val active = downloads.filter { it.status == "downloading" || it.status == "paused" }
    val done = downloads.filter { it.status == "done" }
    val failed = downloads.filter { it.status == "error" || it.status == "cancelled" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.browser_downloads),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.browser_downloads_clear))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (downloads.isEmpty()) {
                Text(
                    stringResource(R.string.browser_downloads_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    if (active.isNotEmpty()) {
                        item(key = "h_active") {
                            SectionHeader(stringResource(R.string.browser_download_status_downloading))
                        }
                        items(active, key = { it.id }) { info ->
                            ActiveDownloadRow(
                                info = info,
                                onPause = { onPause(info) },
                                onResume = { onResume(info) },
                                onCancel = { onCancel(info) },
                                onDelete = { onDelete(info) }
                            )
                        }
                    }
                    if (done.isNotEmpty()) {
                        item(key = "h_done") {
                            SectionHeader(stringResource(R.string.browser_download_status_done))
                        }
                        items(done, key = { it.id }) { info ->
                            DoneDownloadRow(
                                info = info,
                                onOpen = { onOpen(info) },
                                onShare = { onShare(info) },
                                onDelete = { onDelete(info) }
                            )
                        }
                    }
                    if (failed.isNotEmpty()) {
                        item(key = "h_failed") {
                            SectionHeader(stringResource(R.string.browser_download_status_failed))
                        }
                        items(failed, key = { it.id }) { info ->
                            FailedDownloadRow(
                                info = info,
                                onRetry = { onRetry(info) },
                                onDelete = { onDelete(info) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    )
}

/** 进行中下载行：文件名 + 进度条 + 速度 + 暂停/取消。 */
@Composable
private fun ActiveDownloadRow(
    info: BrowserDownloadInfo,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val isPaused = info.status == "paused"
    val fraction = if (info.totalBytes > 0) (info.downloadedBytes.toFloat() / info.totalBytes).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.fileName.ifBlank { info.url },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.browser_download_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { if (isPaused) fraction else fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sizeText = if (info.totalBytes > 0) {
                stringResource(R.string.browser_download_size_of, formatBytes(info.downloadedBytes), formatBytes(info.totalBytes))
            } else formatBytes(info.downloadedBytes)
            Text(
                text = if (isPaused) stringResource(R.string.browser_download_status_paused)
                else "$sizeText  " + stringResource(R.string.browser_download_speed, formatBytes(info.speedBps)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isPaused) {
                TextButton(onClick = onResume) { Text(stringResource(R.string.browser_download_resume)) }
            } else {
                TextButton(onClick = onPause) { Text(stringResource(R.string.browser_download_pause)) }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.browser_download_cancel)) }
        }
    }
}

/** 已完成下载行：文件名 + 大小 + 打开/分享/删除。 */
@Composable
private fun DoneDownloadRow(
    info: BrowserDownloadInfo,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.fileName.ifBlank { info.url },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBytes(info.downloadedBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onOpen) { Text(stringResource(R.string.browser_open)) }
        IconButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.browser_download_share), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.browser_download_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** 失败下载行：错误原因 + 重试/删除。 */
@Composable
private fun FailedDownloadRow(
    info: BrowserDownloadInfo,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = info.fileName.ifBlank { info.url },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = info.error.ifBlank { stringResource(R.string.browser_download_status_failed) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRetry) {
            Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.browser_retry), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.browser_download_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** 字节大小格式化。 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

// ===== 标签管理面板（F4.1） =====

/** 标签管理面板：2 列网格缩略图，显示标题/域名/关闭；顶部批量操作。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabManagerSheet(
    tabs: List<BrowserTabInfo>,
    activeTabId: String,
    incognito: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseOthers: (String) -> Unit,
    onCloseAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.browser_tab_manager_title) + " (${tabs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onCloseOthers(activeTabId) }) {
                    Text(stringResource(R.string.browser_tab_close_others))
                }
                TextButton(onClick = onCloseAll) {
                    Text(stringResource(R.string.browser_tab_close_all))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                tabs.chunked(2).forEach { rowItems ->
                    item(key = "row_${rowItems.first().id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            rowItems.forEach { tab ->
                                TabGridCard(
                                    tab = tab,
                                    active = tab.id == activeTabId,
                                    incognito = incognito,
                                    onClick = { onSelect(tab.id) },
                                    onClose = { onClose(tab.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 标签管理网格卡片：surface 背景，favicon + 标题 + 域名 + 关闭。 */
@Composable
private fun TabGridCard(
    tab: BrowserTabInfo,
    active: Boolean,
    incognito: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(LocalCornerRadius.current.md),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (incognito) Icons.Rounded.PrivacyTip else Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = tab.title.ifBlank { stringResource(R.string.browser_tab_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = hostOf(tab.url),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.browser_close_tab),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ===== AI 操作面板 =====

/** AI 浏览器助手面板：操作时间线 + 暂停 / 接管。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiActionPanel(
    history: List<AgentActionRecord>,
    agentActive: Boolean,
    paused: Boolean,
    onPause: () -> Unit,
    onTakeover: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    val statusText = when {
        paused -> stringResource(R.string.browser_ai_panel_status_paused)
        agentActive -> stringResource(R.string.browser_ai_panel_status_active)
        else -> stringResource(R.string.browser_ai_panel_status_idle)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.browser_ai_panel_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (agentActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.browser_ai_panel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xl)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(history) { rec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Text(
                                text = timeFormat.format(Date(rec.timestamp)),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = rememberActionIcon(rec.action),
                                contentDescription = null,
                                tint = if (rec.success) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = rec.description.ifBlank { rec.action },
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = if (rec.success) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.browser_ai_panel_pause))
                }
                Button(
                    onClick = onTakeover,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.browser_ai_panel_takeover))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** 动作类型 → 图标映射。 */
private fun rememberActionIcon(action: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (action.lowercase()) {
        "navigate" -> Icons.Rounded.OpenInBrowser
        "click" -> Icons.Rounded.TouchApp
        "type", "press_key", "press", "fill_form" -> Icons.Rounded.Keyboard
        "select", "submit" -> Icons.Rounded.CheckBox
        "scroll" -> Icons.Rounded.ArrowDownward
        "wait" -> Icons.Rounded.HourglassEmpty
        "screenshot" -> Icons.Rounded.PhotoCamera
        "extract" -> Icons.Rounded.Description
        "snapshot" -> Icons.Rounded.Article
        "back" -> Icons.AutoMirrored.Rounded.ArrowBack
        "forward" -> Icons.AutoMirrored.Rounded.ArrowForward
        "reload" -> Icons.Rounded.Refresh
        "new_tab" -> Icons.Rounded.Add
        "hover" -> Icons.Rounded.OpenWith
        else -> Icons.Rounded.SmartToy
    }
}

// ===== 凭据 / 缩放 / 登录对话框（保留 AlertDialog） =====

/** 凭据管理面板：已存登录凭据列表（明文查看受保护）+ 删除。 */
@Composable
private fun CredentialsDialog(
    credentialStore: BrowserCredentialStore,
    onDismiss: () -> Unit
) {
    val hosts = remember { credentialStore.hosts() }
    val revealed = remember { mutableStateOf<String?>(null) }
    var deleteHost by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_credentials)) },
        text = {
            if (hosts.isEmpty()) {
                Text(stringResource(R.string.browser_credentials_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(hosts, key = { it }) { host ->
                        val cred = credentialStore.find(host)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (revealed.value == host && cred != null) {
                                        stringResource(R.string.browser_credential_detail, cred.username, cred.password)
                                    } else {
                                        stringResource(R.string.browser_credential_username, cred?.username.orEmpty())
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { revealed.value = if (revealed.value == host) null else host }) {
                                Icon(
                                    if (revealed.value == host) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deleteHost = host }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.browser_credentials_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )

    if (deleteHost != null) {
        AlertDialog(
            onDismissRequest = { deleteHost = null },
            title = { Text(stringResource(R.string.browser_credentials_delete)) },
            text = { Text(stringResource(R.string.browser_credentials_delete_confirm, deleteHost!!)) },
            confirmButton = {
                TextButton(onClick = {
                    credentialStore.delete(deleteHost!!)
                    deleteHost = null
                }) {
                    Text(stringResource(R.string.workspace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteHost = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 页面缩放面板：textZoom 百分比调整（50–200）。 */
@Composable
private fun ZoomDialog(
    percent: Int,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_zoom)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    IconButton(onClick = onLess) {
                        Icon(Icons.Rounded.ZoomOut, contentDescription = stringResource(R.string.browser_zoom_less))
                    }
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.browser_zoom_reset))
                    }
                    IconButton(onClick = onMore) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = stringResource(R.string.browser_zoom_more))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

// ===== 隐私与广告拦截面板（F4.5） =====

/** 隐私设置面板：显示本页拦截数 + 各拦截开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySettingsSheet(
    privacy: com.mini.me_core.feature.browser.domain.AdBlocker,
    blockedCount: Int,
    gesture: com.mini.me_core.feature.browser.domain.BrowserController.GestureSettings,
    onGesture: (com.mini.me_core.feature.browser.domain.BrowserController.GestureSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var adBlock by remember { mutableStateOf(privacy.adBlockEnabled) }
    var tracker by remember { mutableStateOf(privacy.trackerBlockEnabled) }
    var popup by remember { mutableStateOf(privacy.popupBlockEnabled) }
    var dnt by remember { mutableStateOf(privacy.doNotTrack) }
    var thirdCookie by remember { mutableStateOf(privacy.blockThirdPartyCookies) }
    var edgeSwipe by remember { mutableStateOf(gesture.edgeSwipe) }
    var pullRefresh by remember { mutableStateOf(gesture.pullToRefresh) }
    var sensitivity by remember { mutableStateOf(gesture.sensitivity) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                stringResource(R.string.browser_privacy_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.browser_privacy_blocked_count, blockedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.sm))
            PrivacySwitch(stringResource(R.string.browser_privacy_adblock), adBlock) {
                adBlock = it; privacy.adBlockEnabled = it
            }
            PrivacySwitch(stringResource(R.string.browser_privacy_tracker), tracker) {
                tracker = it; privacy.trackerBlockEnabled = it
            }
            PrivacySwitch(stringResource(R.string.browser_privacy_popup), popup) {
                popup = it; privacy.popupBlockEnabled = it
            }
            PrivacySwitch(stringResource(R.string.browser_privacy_dnt), dnt) {
                dnt = it; privacy.doNotTrack = it
            }
            PrivacySwitch(stringResource(R.string.browser_privacy_3p_cookie), thirdCookie) {
                thirdCookie = it; privacy.blockThirdPartyCookies = it
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(R.string.browser_gesture_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            PrivacySwitch(stringResource(R.string.browser_gesture_edge), edgeSwipe) {
                edgeSwipe = it; onGesture(gesture.copy(edgeSwipe = it))
            }
            PrivacySwitch(stringResource(R.string.browser_gesture_pull), pullRefresh) {
                pullRefresh = it; onGesture(gesture.copy(pullToRefresh = it))
            }
            // 灵敏度三段选择
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.browser_gesture_sensitivity), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = sensitivity == 0,
                    onClick = { sensitivity = 0; onGesture(gesture.copy(sensitivity = 0)) },
                    label = { Text(stringResource(R.string.browser_gesture_sens_low)) }
                )
                Spacer(Modifier.width(Spacing.xs))
                FilterChip(
                    selected = sensitivity == 1,
                    onClick = { sensitivity = 1; onGesture(gesture.copy(sensitivity = 1)) },
                    label = { Text(stringResource(R.string.browser_gesture_sens_mid)) }
                )
                Spacer(Modifier.width(Spacing.xs))
                FilterChip(
                    selected = sensitivity == 2,
                    onClick = { sensitivity = 2; onGesture(gesture.copy(sensitivity = 2)) },
                    label = { Text(stringResource(R.string.browser_gesture_sens_high)) }
                )
            }
        }
    }
}

@Composable
private fun PrivacySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 登录凭据输入对话框。 */
@Composable
private fun LoginCredentialDialog(
    host: String,
    onConfirm: (username: String, password: String) -> Unit,
    onCancel: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.browser_login_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = stringResource(R.string.browser_login_hint, host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.common_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.browser_login_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(username.trim(), password) },
                enabled = username.isNotBlank() && password.isNotBlank()
            ) {
                Text(stringResource(R.string.browser_login_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

// ===== F4.8 手势操作 =====

/**
 * 边缘滑动手势：从左边缘右滑前进、从右边缘左滑后退。
 * 仅在触摸起始点落在屏幕左右边缘带内时才识别，避免与页面横向滚动冲突。
 */
private fun Modifier.browserEdgeGesture(
    enabled: Boolean,
    sensitivity: Int,
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier = this.pointerInput(enabled, sensitivity) {
    if (!enabled) return@pointerInput
    val edgeWidth = when (sensitivity) { 0 -> 56.dp.toPx(); 2 -> 20.dp.toPx(); else -> 36.dp.toPx() }
    val threshold = when (sensitivity) { 0 -> 140.dp.toPx(); 2 -> 50.dp.toPx(); else -> 90.dp.toPx() }
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            val startX = down.position.x
            val fromLeft = startX < edgeWidth
            val fromRight = startX > size.width - edgeWidth
            if (!fromLeft && !fromRight) { waitForUpOrCancellation(); continue }
            var accumulated = 0f
            var fired = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (change.changedToUp()) break
                accumulated += change.positionChange().x
                if (!fired && kotlin.math.abs(accumulated) > threshold) {
                    fired = true
                    if (fromLeft && accumulated > 0) onForward()
                    else if (fromRight && accumulated < 0) onBack()
                }
            }
        }
    }
}

/**
 * 下拉刷新手势：仅当页面滚动到顶部（atTop 为真）时，向下拖动超过阈值触发刷新。
 */
private fun Modifier.pullToRefreshGesture(
    enabled: Boolean,
    sensitivity: Int,
    atTop: () -> Boolean,
    onRefresh: () -> Unit
): Modifier = this.pointerInput(enabled, sensitivity) {
    if (!enabled) return@pointerInput
    val threshold = when (sensitivity) { 0 -> 160.dp.toPx(); 2 -> 70.dp.toPx(); else -> 110.dp.toPx() }
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            val startY = down.position.y
            var accumulated = 0f
            var armed = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (change.changedToUp()) {
                    if (armed && accumulated > threshold) onRefresh()
                    break
                }
                val dy = change.positionChange().y
                accumulated += dy
                // 仅在页面处于顶部且向下拉时武装
                if (!armed && startY < size.height * 0.4 && atTop() && accumulated > threshold * 0.4f && dy > 0) {
                    armed = true
                }
            }
        }
    }
}
