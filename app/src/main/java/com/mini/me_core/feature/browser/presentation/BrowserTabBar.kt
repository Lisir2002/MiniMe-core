package com.mini.me_core.feature.browser.presentation
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


/** 浏览器标签栏：40dp 高，横向滚动，标签可切换 / 关闭 / 新建；长按弹出批量操作；>5 个标签显示管理入口。 */
@Composable
internal fun BrowserTabBar(
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
internal fun TabChip(
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
internal fun hostOf(url: String): String = runCatching {
    val u = url.trim()
    val start = if (u.startsWith("http://")) 7 else if (u.startsWith("https://")) 8 else 0
    u.substring(start).substringBefore('/').substringBefore(':').ifBlank { u }
}.getOrDefault(url)

