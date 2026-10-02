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



// ===== 凭据 / 缩放 / 登录对话框（保留 AlertDialog） =====

/** 凭据管理面板：已存登录凭据列表（明文查看受保护）+ 删除。 */
@Composable
internal fun CredentialsDialog(
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
        val host = deleteHost ?: return
        AlertDialog(
            onDismissRequest = { deleteHost = null },
            title = { Text(stringResource(R.string.browser_credentials_delete)) },
            text = { Text(stringResource(R.string.browser_credentials_delete_confirm, host)) },
            confirmButton = {
                TextButton(onClick = {
                    credentialStore.delete(host)
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
internal fun ZoomDialog(
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
internal fun PrivacySettingsSheet(
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
internal fun PrivacySwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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
internal fun LoginCredentialDialog(
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
