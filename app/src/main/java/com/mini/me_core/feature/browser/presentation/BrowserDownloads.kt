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



/** 下载管理面板（F4.2）：分组列表 + 进度/速度 + 暂停/继续/取消/打开/分享/删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsBottomSheet(
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
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    )
}

/** 进行中下载行：文件名 + 进度条 + 速度 + 暂停/取消。 */
@Composable
internal fun ActiveDownloadRow(
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
internal fun DoneDownloadRow(
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
internal fun FailedDownloadRow(
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
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
