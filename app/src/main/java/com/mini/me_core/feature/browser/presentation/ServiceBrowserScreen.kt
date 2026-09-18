package com.mini.me_core.feature.browser.presentation

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
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
import com.mini.me_core.newui.designsystem.component.AppBottomSheetList
import com.mini.me_core.newui.designsystem.component.AppButton
import com.mini.me_core.newui.designsystem.component.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.AppDialog
import com.mini.me_core.newui.designsystem.component.AppFilledTextField
import com.mini.me_core.newui.designsystem.component.AppMenu
import com.mini.me_core.newui.designsystem.component.AppMenuDivider
import com.mini.me_core.newui.designsystem.component.AppMenuItem
import com.mini.me_core.newui.designsystem.component.AppMenuRow
import com.mini.me_core.newui.designsystem.component.AppPasswordField
import com.mini.me_core.newui.designsystem.component.AppProgressBar
import com.mini.me_core.newui.designsystem.component.AppSearchBar
import com.mini.me_core.newui.designsystem.component.AppSwitch
import com.mini.me_core.newui.designsystem.layout.AppShell
import com.mini.me_core.newui.designsystem.primitive.AppIcon
import com.mini.me_core.newui.designsystem.primitive.AppIconButton
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlinx.coroutines.launch
import java.io.File

/**
 * 内置服务浏览器页（iOS Safari 风格 UI，收敛到 newui 组件体系）。
 *
 * 用户侧：顶部圆角 pill 地址栏（安全锁/URL/刷新/更多）+ 细进度条 + 底部五键工具栏
 * （后退/前进/书签/标签/更多）+ 新标签页主页 + WebView 容器；模型侧：
 * [BrowserController.agentStatus] 实时展示模型正在进行的操作。用户与模型共享同一个 WebView 会话。
 *
 * 同时承载三类异步交互弹窗：
 *  - 页面 alert/confirm（[BrowserController.pendingDialog]）——模型或页面发起，用户确认；
 *  - 登录凭据输入（[BrowserLoginPromptManager.pendingPrompt]）——模型登录时请求用户提供账号密码；
 *  - 用户接管提示（[BrowserTakeoverManager.pending]）——模型请求用户亲自完成验证码/支付等。
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
    val pendingDialog by browserController.pendingDialog.collectAsStateWithLifecycle()
    val pendingLoginPrompt by loginPromptManager.pendingPrompt.collectAsStateWithLifecycle()
    val pendingTakeover by takeoverManager.pending.collectAsStateWithLifecycle()
    val downloads by browserController.downloads.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var addressText by remember { mutableStateOf(uiState.currentUrl) }

    // 「更多」菜单、各功能面板与地址栏编辑态开关
    var showMore by remember { mutableStateOf(false) }
    var findVisible by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var addressEditing by remember { mutableStateOf(false) }
    val addressFocus = remember { FocusRequester() }

    // 页面 URL 变化时同步地址栏
    LaunchedEffect(uiState.currentUrl) {
        if (addressText != uiState.currentUrl) addressText = uiState.currentUrl
    }

    // 首次进入：预创建首个激活标签（避免组合期间改 activeTabId 引发 AndroidView 重复挂载崩溃），再按需导航
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

    // 进入编辑态时拉取地址栏焦点
    LaunchedEffect(addressEditing) {
        if (addressEditing) addressFocus.requestFocus()
    }

    fun navigate() {
        val url = addressText.trim()
        if (url.isBlank()) return
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
        scope.launch { browserController.retryDownload(info) }
    }

    val currentBookmarked = uiState.currentUrl.isNotBlank() && browserController.isBookmarked(uiState.currentUrl)

    AppShell(
        showTopBar = false,
        bottomBar = {
            Column {
                // 模型操作状态条
                AnimatedVisibility(
                    visible = agentStatus.active,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appPalette().primaryOverlay12)
                            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppSizing.IconS),
                            color = appPalette().primary,
                            strokeWidth = AppSpacing.Tiny
                        )
                        Text(
                            text = agentStatus.text.ifBlank { stringResource(R.string.browser_agent_working) },
                            style = MaterialTheme.typography.bodySmall,
                            color = appPalette().ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // iOS Safari 风格工具栏：一行五枚等分图标按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppLayout.BottomBarHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(
                        onClick = { browserController.goBack() },
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        modifier = Modifier.weight(1f),
                        contentDescription = stringResource(R.string.browser_back),
                        enabled = uiState.canGoBack
                    )
                    AppIconButton(
                        onClick = { browserController.goForward() },
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        modifier = Modifier.weight(1f),
                        contentDescription = stringResource(R.string.browser_forward),
                        enabled = uiState.canGoForward
                    )
                    AppIconButton(
                        onClick = {
                            if (uiState.currentUrl.isNotBlank()) {
                                if (browserController.isBookmarked(uiState.currentUrl)) {
                                    browserController.removeBookmark(uiState.currentUrl)
                                } else {
                                    browserController.addBookmark()
                                }
                            }
                        },
                        icon = if (currentBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        modifier = Modifier.weight(1f),
                        contentDescription = stringResource(
                            if (currentBookmarked) R.string.browser_remove_bookmark else R.string.browser_add_bookmark
                        )
                    )
                    AppIconButton(
                        onClick = { showTabs = true },
                        icon = Icons.Rounded.Layers,
                        modifier = Modifier.weight(1f),
                        contentDescription = stringResource(R.string.browser_more)
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppIconButton(
                            onClick = { showMore = true },
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.browser_more)
                        )
                        BrowserMoreMenu(
                            expanded = showMore,
                            onDismiss = { showMore = false },
                            bookmarked = currentBookmarked,
                            incognito = uiState.incognito,
                            desktopMode = uiState.desktopMode,
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
                            onHistory = { showMore = false; showHistory = true },
                            onBookmarks = { showMore = false; showBookmarks = true },
                            onDownloads = { showMore = false; showDownloads = true },
                            onCredentials = { showMore = false; showCredentials = true },
                            onShare = { showMore = false; shareCurrent() },
                            onCopyLink = { showMore = false; copyCurrentLink() },
                            onIncognito = { browserController.setIncognito(!uiState.incognito) },
                            onDesktopMode = { browserController.toggleDesktopMode() },
                            onZoom = { showMore = false; showZoom = true }
                        )
                    }
                }
            }
        }
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // 自定义顶部：iOS 浏览器无标题栏，自绘并自己消费状态栏 inset
            Column(Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.Xs, vertical = AppSpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左上角紧凑返回箭头（Android 无系统侧滑手势入口，需可见）
                    AppIconButton(
                        onClick = onNavigateBack,
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                    // iOS 风格圆角 pill 地址栏
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(AppSizing.TouchTarget)
                            .clip(RoundedCornerShape(AppRadius.Pill))
                            .background(appPalette().surfaceDim)
                            .clickable { addressEditing = true }
                            .padding(horizontal = AppSpacing.Lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val secure = uiState.currentUrl.startsWith("https://", ignoreCase = true)
                        AppIcon(
                            icon = if (secure) Icons.Rounded.Lock else Icons.Rounded.Public,
                            contentDescription = null,
                            tint = appPalette().labelTertiary,
                            size = AppSizing.IconS
                        )
                        Spacer(Modifier.width(AppSpacing.Sm))
                        if (addressEditing) {
                            BasicTextField(
                                value = addressText,
                                onValueChange = { addressText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(addressFocus),
                                singleLine = true,
                                textStyle = TextStyle(color = appPalette().ink),
                                cursorBrush = SolidColor(appPalette().primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(onGo = {
                                    navigate()
                                    addressEditing = false
                                })
                            )
                        } else {
                            Text(
                                text = addressText.ifBlank { stringResource(R.string.browser_address_hint) },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = appPalette().labelSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(AppSpacing.Sm))
                        AppIcon(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.browser_refresh),
                            tint = appPalette().primary,
                            size = AppSizing.IconL,
                            modifier = Modifier
                                .clip(RoundedCornerShape(AppRadius.Pill))
                                .clickable { browserController.reload() }
                                .padding(AppSpacing.Xs)
                        )
                    }
                }
                // 细进度条：仅加载中显示
                if (uiState.isLoading) {
                    AppProgressBar(progress = (uiState.progress.coerceIn(0, 100)) / 100f)
                }
                // 页内查找条
                AnimatedVisibility(visible = findVisible, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs)
                    ) {
                        AppSearchBar(
                            value = findText,
                            onValueChange = { findText = it; browserController.findOnPage(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = stringResource(R.string.browser_find_hint),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { }),
                            onClear = { findText = ""; browserController.clearFindOnPage() }
                        )
                        AppIconButton(
                            onClick = { browserController.findNextOnPage(false) },
                            icon = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.browser_find_prev)
                        )
                        AppIconButton(
                            onClick = { browserController.findNextOnPage(true) },
                            icon = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.browser_find_next)
                        )
                        AppIconButton(
                            onClick = {
                                findVisible = false
                                findText = ""
                                browserController.clearFindOnPage()
                            },
                            icon = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.browser_find_close)
                        )
                    }
                }
            }

            // WebView 容器：按激活标签 key 切换，每个标签独占一个 WebView 实例。
            // 首个标签尚未创建（activeTabId 为空）时先渲染占位，避免在组合期间创建标签引发 key 跳变导致崩溃。
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
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
                                        CircularProgressIndicator(color = appPalette().primary)
                                    }
                                }
                            }
                        }
                        // 新标签页主页：当前标签尚无 URL 时展示搜索/收藏/最近访问快捷入口
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
    }

    // ── 页面 alert/confirm 对话框 ──
    pendingDialog?.let { d ->
        val isAlert = d.type == "alert"
        AppDialog(
            title = stringResource(
                if (isAlert) R.string.browser_dialog_alert else R.string.browser_dialog_confirm_title
            ),
            text = d.message,
            confirmText = stringResource(R.string.workspace_confirm),
            dismissText = if (isAlert) null else stringResource(R.string.common_cancel),
            onConfirm = { scope.launch { browserController.handleDialog(true) } },
            onDismiss = {
                if (!isAlert) scope.launch { browserController.handleDialog(false) }
            }
        )
    }

    // ── 登录凭据输入对话框（模型登录时请求用户提供账号密码） ──
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

    // ── 用户接管提示对话框（模型请求用户亲自完成验证码/支付/二次认证等） ──
    pendingTakeover?.let { p ->
        AppDialog(
            title = p.title,
            text = p.message,
            confirmText = stringResource(R.string.browser_takeover_done),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = { takeoverManager.resolve(p.requestId, TakeoverAnswer(confirmed = true)) },
            onDismiss = { takeoverManager.cancel(p.requestId) }
        )
    }

    // ── 多标签列表（底部抽屉） ──
    if (showTabs) {
        TabsBottomSheet(
            tabs = tabs,
            activeTabId = uiState.activeTabId,
            onSelect = { showTabs = false; switchTab(it) },
            onClose = { closeTab(it) },
            onNewTab = { newTab() },
            onDismiss = { showTabs = false }
        )
    }

    // ── 历史记录面板 ──
    if (showHistory) {
        HistorySheet(
            entries = remember { browserController.history() },
            onOpen = { url -> showHistory = false; openUrl(url) },
            onClear = {
                browserController.clearHistory()
                showHistory = false
            },
            onDismiss = { showHistory = false }
        )
    }

    // ── 收藏夹面板 ──
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = remember { browserController.bookmarks() },
            onOpen = { url -> showBookmarks = false; openUrl(url) },
            onRemove = { browserController.removeBookmark(it) },
            onDismiss = { showBookmarks = false }
        )
    }

    // ── 下载管理面板 ──
    if (showDownloads) {
        DownloadsSheet(
            downloads = downloads,
            onOpen = { openDownload(it) },
            onRetry = { retryDownload(it) },
            onClear = { browserController.clearDownloads() },
            onDismiss = { showDownloads = false }
        )
    }

    // ── 凭据管理面板 ──
    if (showCredentials) {
        CredentialsSheet(
            credentialStore = credentialStore,
            onDismiss = { showCredentials = false }
        )
    }

    // ── 页面缩放面板 ──
    if (showZoom) {
        ZoomSheet(
            percent = uiState.textZoom,
            onLess = { browserController.setTextZoom(uiState.textZoom - 10) },
            onMore = { browserController.setTextZoom(uiState.textZoom + 10) },
            onReset = { browserController.setTextZoom(100) },
            onDismiss = { showZoom = false }
        )
    }
}

/** 地址栏「更多」下拉菜单：newui AppMenu 封装，用户侧功能统一入口。 */
@Composable
private fun BrowserMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    bookmarked: Boolean,
    incognito: Boolean,
    desktopMode: Boolean,
    onFind: () -> Unit,
    onToggleBookmark: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
    onCredentials: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onIncognito: () -> Unit,
    onDesktopMode: () -> Unit,
    onZoom: () -> Unit
) {
    AppMenu(expanded = expanded, onDismiss = onDismiss) {
        AppMenuItem(
            label = stringResource(R.string.browser_find),
            onClick = onFind,
            leadingIcon = Icons.Rounded.Search
        )
        AppMenuItem(
            label = stringResource(
                if (bookmarked) R.string.browser_remove_bookmark else R.string.browser_add_bookmark
            ),
            onClick = onToggleBookmark,
            leadingIcon = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder
        )
        AppMenuDivider()
        AppMenuItem(
            label = stringResource(R.string.browser_history),
            onClick = onHistory,
            leadingIcon = Icons.Rounded.History
        )
        AppMenuItem(
            label = stringResource(R.string.browser_bookmarks),
            onClick = onBookmarks,
            leadingIcon = Icons.Rounded.Bookmark
        )
        AppMenuItem(
            label = stringResource(R.string.browser_downloads),
            onClick = onDownloads,
            leadingIcon = Icons.Rounded.Download
        )
        AppMenuItem(
            label = stringResource(R.string.browser_credentials),
            onClick = onCredentials,
            leadingIcon = Icons.Rounded.Key
        )
        AppMenuDivider()
        AppMenuItem(
            label = stringResource(R.string.browser_share),
            onClick = onShare,
            leadingIcon = Icons.Rounded.Share
        )
        AppMenuItem(
            label = stringResource(R.string.browser_copy_link),
            onClick = onCopyLink,
            leadingIcon = Icons.Rounded.ContentCopy
        )
        AppMenuDivider()
        MenuSwitchRow(
            label = stringResource(R.string.browser_incognito),
            checked = incognito,
            onToggle = onIncognito,
            leadingIcon = Icons.Rounded.PrivacyTip
        )
        MenuSwitchRow(
            label = stringResource(R.string.browser_desktop_mode),
            checked = desktopMode,
            onToggle = onDesktopMode,
            leadingIcon = Icons.Rounded.DesktopWindows
        )
        AppMenuItem(
            label = stringResource(R.string.browser_zoom),
            onClick = onZoom,
            leadingIcon = Icons.Rounded.ZoomIn
        )
    }
}

/** 菜单内带尾接开关的行（无痕 / 桌面版）。 */
@Composable
private fun MenuSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    leadingIcon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            icon = leadingIcon,
            size = AppSizing.IconM,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = AppSpacing.Md)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** 新标签页主页：newui AppSearchBar + AppMenuRow 快捷入口。 */
@Composable
private fun BrowserHomePage(
    addressText: String,
    onAddressChange: (String) -> Unit,
    onNavigate: () -> Unit,
    bookmarks: List<BrowserBookmark>,
    recentVisits: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md)
    ) {
        Spacer(Modifier.height(AppSpacing.Md))
        AppSearchBar(
            value = addressText,
            onValueChange = onAddressChange,
            placeholder = stringResource(R.string.browser_home_search_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onNavigate() }),
            onClear = { onAddressChange("") }
        )
        if (recentVisits.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.Lg))
            Text(
                text = stringResource(R.string.browser_recent_visits),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            recentVisits.forEach { entry ->
                AppMenuRow(
                    title = entry.title.ifBlank { entry.url },
                    subtitle = entry.url,
                    icon = Icons.Rounded.History,
                    onClick = { onOpen(entry.url) }
                )
            }
        }
        if (bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.Lg))
            Text(
                text = stringResource(R.string.browser_bookmarks),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            bookmarks.take(8).forEach { bm ->
                AppMenuRow(
                    title = bm.title.ifBlank { bm.url },
                    subtitle = bm.url,
                    icon = Icons.Rounded.BookmarkBorder,
                    onClick = { onOpen(bm.url) }
                )
            }
        }
    }
}

/** 多标签底部抽屉：标题 + 新建钮 + 标签列表（可切换/关闭）。 */
@Composable
private fun TabsBottomSheet(
    tabs: List<BrowserTabInfo>,
    activeTabId: String,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_title)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.Sm),
            horizontalArrangement = Arrangement.End
        ) {
            AppButton(
                text = stringResource(R.string.browser_new_tab),
                variant = AppButtonVariant.Text,
                onClick = onNewTab
            )
        }
        if (tabs.isEmpty()) {
            Text(
                text = stringResource(R.string.browser_tab_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                tabs.forEach { tab ->
                    AppMenuRow(
                        title = tab.title.ifBlank { tab.url.ifBlank { stringResource(R.string.browser_tab_empty) } },
                        subtitle = tab.url,
                        trailing = {
                            AppIconButton(
                                onClick = { onClose(tab.id) },
                                icon = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.browser_close_tab)
                            )
                        },
                        onClick = { onSelect(tab.id) }
                    )
                }
            }
        }
    }
}

/** 历史记录底部抽屉：列表 + 回跳 + 清空（确认走 AppDialog）。 */
@Composable
private fun HistorySheet(
    entries: List<BrowserHistoryEntry>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_history)) {
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.Sm),
                horizontalArrangement = Arrangement.End
            ) {
                AppButton(
                    text = stringResource(R.string.browser_clear_history),
                    variant = AppButtonVariant.Text,
                    onClick = { confirmClear = true }
                )
            }
        }
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.browser_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    AppMenuRow(
                        title = entry.title.ifBlank { entry.url },
                        subtitle = entry.url,
                        onClick = { onOpen(entry.url) }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AppDialog(
            title = stringResource(R.string.browser_clear_history),
            text = stringResource(R.string.browser_clear_history_confirm),
            confirmText = stringResource(R.string.workspace_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/** 收藏夹底部抽屉：列表 + 回跳 + 删除（确认走 AppDialog）。 */
@Composable
private fun BookmarksSheet(
    bookmarks: List<BrowserBookmark>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var removeTarget by remember { mutableStateOf<String?>(null) }
    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_bookmarks)) {
        if (bookmarks.isEmpty()) {
            Text(
                text = stringResource(R.string.browser_bookmarks_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                bookmarks.forEach { bm ->
                    AppMenuRow(
                        title = bm.title.ifBlank { bm.url },
                        subtitle = bm.url,
                        trailing = {
                            AppIconButton(
                                onClick = { removeTarget = bm.url },
                                icon = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.common_delete)
                            )
                        },
                        onClick = { onOpen(bm.url) }
                    )
                }
            }
        }
    }

    if (removeTarget != null) {
        AppDialog(
            title = stringResource(R.string.browser_remove_bookmark),
            text = removeTarget,
            confirmText = stringResource(R.string.workspace_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                onRemove(removeTarget!!)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }
}

/** 下载管理底部抽屉：列表 / 打开 / 重试 / 清除。 */
@Composable
private fun DownloadsSheet(
    downloads: List<BrowserDownloadInfo>,
    onOpen: (BrowserDownloadInfo) -> Unit,
    onRetry: (BrowserDownloadInfo) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_downloads)) {
        if (downloads.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.Sm),
                horizontalArrangement = Arrangement.End
            ) {
                AppButton(
                    text = stringResource(R.string.browser_downloads_clear),
                    variant = AppButtonVariant.Text,
                    onClick = onClear
                )
            }
        }
        if (downloads.isEmpty()) {
            Text(
                text = stringResource(R.string.browser_downloads_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                downloads.forEach { info ->
                    AppMenuRow(
                        title = info.fileName.ifBlank { info.url },
                        subtitle = when (info.status) {
                            "done" -> info.path
                            "error" -> info.error.ifBlank { info.url }
                            else -> info.url
                        },
                        trailing = {
                            when (info.status) {
                                "done" -> AppButton(
                                    text = stringResource(R.string.browser_open),
                                    variant = AppButtonVariant.Text,
                                    onClick = { onOpen(info) }
                                )
                                "error" -> AppIconButton(
                                    onClick = { onRetry(info) },
                                    icon = Icons.Rounded.Replay,
                                    contentDescription = stringResource(R.string.browser_retry)
                                )
                                else -> CircularProgressIndicator(
                                    modifier = Modifier.size(AppSizing.IconM),
                                    color = appPalette().primary,
                                    strokeWidth = AppSpacing.Tiny
                                )
                            }
                        },
                        onClick = { }
                    )
                }
            }
        }
    }
}

/** 凭据管理底部抽屉：已存登录凭据列表（明文查看受保护）+ 删除。 */
@Composable
private fun CredentialsSheet(
    credentialStore: BrowserCredentialStore,
    onDismiss: () -> Unit
) {
    val hosts = remember { credentialStore.hosts() }
    val revealed = remember { mutableStateOf<String?>(null) }
    var deleteHost by remember { mutableStateOf<String?>(null) }

    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_credentials)) {
        if (hosts.isEmpty()) {
            Text(
                text = stringResource(R.string.browser_credentials_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = appPalette().labelSecondary
            )
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                hosts.forEach { host ->
                    val cred = credentialStore.find(host)
                    val subtitle = if (revealed.value == host && cred != null) {
                        stringResource(R.string.browser_credential_detail, cred.username, cred.password)
                    } else {
                        stringResource(R.string.browser_credential_username, cred?.username.orEmpty())
                    }
                    AppMenuRow(
                        title = host,
                        subtitle = subtitle,
                        trailing = {
                            Row {
                                AppIconButton(
                                    onClick = { revealed.value = if (revealed.value == host) null else host },
                                    icon = if (revealed.value == host) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null
                                )
                                AppIconButton(
                                    onClick = { deleteHost = host },
                                    icon = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.browser_credentials_delete)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (deleteHost != null) {
        AppDialog(
            title = stringResource(R.string.browser_credentials_delete),
            text = stringResource(R.string.browser_credentials_delete_confirm, deleteHost!!),
            confirmText = stringResource(R.string.workspace_confirm),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                credentialStore.delete(deleteHost!!)
                deleteHost = null
            },
            onDismiss = { deleteHost = null }
        )
    }
}

/** 页面缩放底部抽屉：textZoom 百分比调整（50–200）。 */
@Composable
private fun ZoomSheet(
    percent: Int,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AppBottomSheetList(onDismiss = onDismiss, title = stringResource(R.string.browser_zoom)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Md)
        ) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)
            ) {
                AppIconButton(
                    onClick = onLess,
                    icon = Icons.Rounded.ZoomOut,
                    contentDescription = stringResource(R.string.browser_zoom_less)
                )
                AppButton(
                    text = stringResource(R.string.browser_zoom_reset),
                    variant = AppButtonVariant.Text,
                    onClick = onReset
                )
                AppIconButton(
                    onClick = onMore,
                    icon = Icons.Rounded.ZoomIn,
                    contentDescription = stringResource(R.string.browser_zoom_more)
                )
            }
        }
    }
}

/** 登录凭据输入对话框：newui Dialog 容器，body 输入框/按钮全部走 newui 组件。 */
@Composable
private fun LoginCredentialDialog(
    host: String,
    onConfirm: (username: String, password: String) -> Unit,
    onCancel: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties()) {
        Surface(
            shape = RoundedCornerShape(AppRadius.Lg),
            color = appPalette().card
        ) {
            Column(modifier = Modifier.padding(AppSpacing.Lg)) {
                Text(
                    text = stringResource(R.string.browser_login_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = appPalette().ink
                )
                Spacer(Modifier.height(AppSpacing.Sm))
                Text(
                    text = stringResource(R.string.browser_login_hint, host),
                    style = MaterialTheme.typography.bodySmall,
                    color = appPalette().labelSecondary
                )
                Spacer(Modifier.height(AppSpacing.Md))
                AppFilledTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.common_username)
                )
                Spacer(Modifier.height(AppSpacing.Sm))
                AppPasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.browser_login_password)
                )
                Spacer(Modifier.height(AppSpacing.Lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppButton(
                        text = stringResource(R.string.common_cancel),
                        variant = AppButtonVariant.Text,
                        onClick = onCancel
                    )
                    Spacer(Modifier.width(AppSpacing.Sm))
                    AppButton(
                        text = stringResource(R.string.browser_login_confirm),
                        onClick = { onConfirm(username.trim(), password) },
                        enabled = username.isNotBlank() && password.isNotBlank()
                    )
                }
            }
        }
    }
}
