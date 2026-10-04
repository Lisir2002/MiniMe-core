package com.mini.me_core.feature.browser.presentation
import com.mini.me_core.core.theme.components.AppDropdownMenu
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.AnimationScaleHolder
import com.mini.me_core.core.theme.ScaledAnimation

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



// ===== 地址栏（可放置于顶部或底部） =====

/**
 * 浏览器地址栏：后退/前进 + 地址输入框（含锁图标、停止/刷新） + 更多菜单 + 底部加载进度线。
 * 可在 topBar（标签栏下方）或 bottomBar（工具栏上方）中条件性放置。
 */
@Composable
internal fun BrowserAddressBar(
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
internal fun BrowserMoreMenu(
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
    AppDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
internal fun FindOnPageBar(
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
