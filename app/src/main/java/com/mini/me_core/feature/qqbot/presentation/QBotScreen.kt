package com.mini.me_core.feature.qqbot.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import com.mini.me_core.core.theme.components.AppTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonColor
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.feature.qqbot.domain.QBotConfig
import com.mini.me_core.feature.qqbot.domain.QBotLoginState
import com.mini.me_core.feature.qqbot.domain.QBotMessage
import com.mini.me_core.feature.qqbot.domain.QBotModelOption
import com.mini.me_core.feature.qqbot.domain.QBotSession
import com.mini.me_core.feature.qqbot.domain.QBotSessionType
import com.mini.me_core.feature.qqbot.domain.QBotState
import com.mini.me_core.feature.qqbot.domain.QBotWsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * QQ 机器人第一阶段管理页。
 *
 * 三个 Tab：会话列表 / 聊天窗口 / 基础设置；顶部为运行状态卡片。
 */
@Composable
fun QBotScreen(
    viewModel: QBotViewModel,
    onNavigateBack: () -> Unit,
) {
    val botState by viewModel.botState.collectAsStateWithLifecycle()
    val wsState by viewModel.wsState.collectAsStateWithLifecycle()
    val uptimeMs by viewModel.uptimeMs.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val currentSession by viewModel.currentSession.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val qrCodeData by viewModel.qrCodeData.collectAsStateWithLifecycle()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    val loggedBotQq by viewModel.loggedBotQq.collectAsStateWithLifecycle()
    val loggedBotNickname by viewModel.loggedBotNickname.collectAsStateWithLifecycle()
    val loginErrorMessage by viewModel.loginErrorMessage.collectAsStateWithLifecycle()
    val modelOptions by viewModel.modelOptions.collectAsStateWithLifecycle()
    val selectedModelCtx by viewModel.selectedModelContextTokens.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // 长按会话弹出删除确认
    var pendingDeleteSession by remember { mutableStateOf<QBotSession?>(null) }

    Scaffold(
        containerColor = LocalAppTheme.current.colors.surfacePage,
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.qqbot_title),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            QBotStatusCard(
                botState = botState,
                wsState = wsState,
                uptimeMs = uptimeMs,
                config = config,
                loginState = loginState,
                qrCodeData = qrCodeData,
                loggedBotQq = loggedBotQq,
                loggedBotNickname = loggedBotNickname,
                loginErrorMessage = loginErrorMessage,
                onStart = viewModel::startBot,
                onStop = viewModel::stopBot,
                onRelogin = viewModel::relogin,
                onRetry = viewModel::retryLogin,
                onClearQr = viewModel::clearQrCode,
            )

            val tabTitles = listOf(
                stringResource(R.string.qqbot_tab_sessions),
                stringResource(R.string.qqbot_tab_chat),
                stringResource(R.string.qqbot_tab_settings),
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = LocalAppTheme.current.colors.surfaceCard,
                contentColor = LocalAppTheme.current.colors.brandPrimary,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal) },
                    )
                }
            }

            when (selectedTab) {
                0 -> SessionList(
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                    onSelect = {
                        viewModel.selectSession(it)
                        selectedTab = 1
                    },
                    onLongClick = { pendingDeleteSession = it },
                )
                1 -> ChatView(
                    messages = messages,
                    session = currentSession,
                    onClear = { currentSession?.let { viewModel.clearMessages(it.sessionId) } },
                )
                else -> SettingsPanel(
                    config = config,
                    modelOptions = modelOptions,
                    selectedModelContextTokens = selectedModelCtx,
                    loginState = loginState,
                    onUpdate = { newConfig -> viewModel.updateConfig { newConfig } },
                    onGenerateToken = viewModel::generateToken,
                    onSelectModel = viewModel::selectModel,
                    onSetLoginType = viewModel::setLoginType,
                    onSetPassword = viewModel::setPassword,
                    savedPassword = viewModel.savedPasswordPlain(),
                    onRelogin = viewModel::relogin,
                )
            }
        }
    }

    // 删除会话确认对话框
    pendingDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text(stringResource(R.string.qqbot_delete_session)) },
            text = { Text(stringResource(R.string.qqbot_confirm_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.sessionId)
                    pendingDeleteSession = null
                }) { Text(stringResource(R.string.qqbot_delete_session)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 状态卡片
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun QBotStatusCard(
    botState: QBotState,
    wsState: QBotWsState,
    uptimeMs: Long,
    config: QBotConfig,
    loginState: QBotLoginState,
    qrCodeData: ByteArray?,
    loggedBotQq: Long,
    loggedBotNickname: String,
    loginErrorMessage: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelogin: () -> Unit,
    onRetry: () -> Unit,
    onClearQr: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val isActive = botState == QBotState.RUNNING || botState == QBotState.STARTING

    AppCard(
        modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
    ) {
        Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(botState = botState)
                Spacer(Modifier.width(PrimitiveSpacing.Sm))
                LoginBadge(loginState = loginState)
                Spacer(Modifier.weight(1f))
                ConnectionBadge(wsState = wsState)
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

            // 登录二维码区块：仅在等待扫码时展示。
            if (loginState == QBotLoginState.QR_SHOWN ||
                (loginState == QBotLoginState.SCANNING && qrCodeData != null)
            ) {
                QrCodeDisplay(
                    qrCodeData = qrCodeData,
                    onRefresh = onClearQr,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Md))
            }

            // 登录成功：显示 QQ 号 + 昵称。
            if (loginState == QBotLoginState.LOGGED_IN) {
                val qq = loggedBotQq.takeIf { it != 0L } ?: config.botQq
                InfoRow(
                    label = stringResource(R.string.qqbot_login_state),
                    value = stringResource(
                        R.string.qqbot_logged_in_as,
                        loggedBotNickname.ifBlank { "-" },
                        qq,
                    ),
                )
            }

            // 登录失败 / 掉线：显示错误与操作按钮。
            if (loginState == QBotLoginState.LOGIN_FAILED) {
                Text(
                    text = stringResource(R.string.qqbot_login_failed) +
                        (loginErrorMessage.ifBlank { "" }.let { if (it.isBlank()) "" else ": $it" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Sm))
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.qqbot_login_retry))
                }
            }
            if (loginState == QBotLoginState.OFFLINE) {
                Spacer(Modifier.height(PrimitiveSpacing.Sm))
                OutlinedButton(onClick = onRelogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.qqbot_relogin))
                }
            }

            InfoRow(
                label = stringResource(R.string.qqbot_bot_qq),
                value = if (config.botQq != 0L) config.botQq.toString()
                else stringResource(R.string.qqbot_not_configured),
            )
            InfoRow(
                label = stringResource(R.string.qqbot_uptime_label),
                value = formatUptime(uptimeMs),
            )
            InfoRow(
                label = stringResource(R.string.qqbot_connection_status),
                value = when (wsState) {
                    QBotWsState.CONNECTED -> stringResource(R.string.qqbot_ws_connected)
                    QBotWsState.CONNECTING -> stringResource(R.string.qqbot_ws_connecting)
                    QBotWsState.DISCONNECTED -> stringResource(R.string.qqbot_ws_disconnected)
                },
            )

            Spacer(Modifier.height(PrimitiveSpacing.Lg))

            AppButton(
                text = if (isActive) stringResource(R.string.qqbot_stop)
                else stringResource(R.string.qqbot_start),
                onClick = { if (isActive) onStop() else onStart() },
                modifier = Modifier.fillMaxWidth(),
                size = AppButtonSize.Large,
                buttonColor = if (isActive) AppButtonColor.Error else AppButtonColor.Success,
                variant = AppButtonVariant.Filled,
                icon = if (isActive) Icons.Rounded.Power else Icons.Rounded.SmartToy,
            )
        }
    }
}

/** 登录状态徽章，与进程运行徽章并排。 */
@Composable
private fun LoginBadge(loginState: QBotLoginState) {
    val colors = LocalAppTheme.current.colors
    val (bg, fg, textRes) = when (loginState) {
        QBotLoginState.LOGGED_IN -> Triple(
            colors.successContainer, colors.onSuccessContainer, R.string.qqbot_login_logged_in,
        )
        QBotLoginState.QR_SHOWN, QBotLoginState.SCANNING, QBotLoginState.LOGGING_IN -> Triple(
            colors.warningContainer, colors.onWarningContainer, R.string.qqbot_login_qr_shown,
        )
        QBotLoginState.LOGIN_FAILED -> Triple(
            colors.errorContainer, colors.onErrorContainer, R.string.qqbot_login_failed,
        )
        QBotLoginState.OFFLINE -> Triple(
            colors.errorContainer, colors.onErrorContainer, R.string.qqbot_login_offline,
        )
        QBotLoginState.LOGGED_OUT -> Triple(
            colors.surfaceSunken, colors.textSecondary, R.string.qqbot_login_logged_out,
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = PrimitiveSpacing.Md, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(textRes),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 登录二维码展示：把 PNG 字节解码为 Bitmap 居中显示，下方提示扫码。 */
@Composable
private fun QrCodeDisplay(
    qrCodeData: ByteArray?,
    onRefresh: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val bitmap = remember(qrCodeData) {
        qrCodeData?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.qqbot_login_qr),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = stringResource(R.string.qqbot_login_qr_shown),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(PrimitiveSpacing.Sm))
        Text(
            text = stringResource(R.string.qqbot_qr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun StatusBadge(botState: QBotState) {
    val colors = LocalAppTheme.current.colors
    val (bg, fg, textRes) = when (botState) {
        QBotState.RUNNING -> Triple(
            colors.successContainer, colors.onSuccessContainer, R.string.qqbot_status_running,
        )
        QBotState.STARTING, QBotState.STOPPING -> Triple(
            colors.warningContainer, colors.onWarningContainer, R.string.qqbot_status_starting,
        )
        QBotState.STOPPED -> Triple(
            colors.surfaceSunken, colors.textSecondary, R.string.qqbot_status_stopped,
        )
        QBotState.ERROR -> Triple(
            colors.errorContainer, colors.onErrorContainer, R.string.qqbot_status_error,
        )
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = PrimitiveSpacing.Md, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(textRes),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConnectionBadge(wsState: QBotWsState) {
    val colors = LocalAppTheme.current.colors
    val connected = wsState == QBotWsState.CONNECTED
    val bg = if (connected) colors.successContainer else colors.surfaceSunken
    val fg = if (connected) colors.onSuccessContainer else colors.textTertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = PrimitiveSpacing.Md, vertical = 4.dp),
    ) {
        Text(
            text = when (wsState) {
                QBotWsState.CONNECTED -> stringResource(R.string.qqbot_ws_connected)
                QBotWsState.CONNECTING -> stringResource(R.string.qqbot_ws_connecting)
                QBotWsState.DISCONNECTED -> stringResource(R.string.qqbot_ws_disconnected)
            },
            color = fg,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 会话列表
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionList(
    sessions: List<QBotSession>,
    selectedSessionId: String?,
    onSelect: (String) -> Unit,
    onLongClick: (QBotSession) -> Unit,
) {
    if (sessions.isEmpty()) {
        EmptyState(message = stringResource(R.string.qqbot_no_sessions))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sessions, key = { it.sessionId }) { session ->
            SessionRow(
                session = session,
                selected = session.sessionId == selectedSessionId,
                onClick = { onSelect(session.sessionId) },
                onLongClick = { onLongClick(session) },
            )
            HorizontalDivider(thickness = 0.5.dp, color = LocalAppTheme.current.colors.borderMuted)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: QBotSession,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.surfaceHover else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
    ) {
        QBotAvatar(name = session.displayName, size = 48.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                TypeLabel(type = session.type)
            }
            val lastMessage = session.lastMessage
            if (!lastMessage.isNullOrBlank()) {
                Text(
                    text = lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            session.lastMessageTime?.let { ts ->
                Text(
                    text = formatSessionTime(ts),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
            if (session.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                UnreadBadge(count = session.unreadCount)
            }
        }
    }
}

@Composable
private fun TypeLabel(type: QBotSessionType) {
    val colors = LocalAppTheme.current.colors
    val (bg, fg, icon) = when (type) {
        QBotSessionType.GROUP -> Triple(colors.brandContainer, colors.onBrandContainer, Icons.Rounded.Group)
        QBotSessionType.PRIVATE -> Triple(colors.skyContainer, colors.onSkyContainer, Icons.Rounded.Person)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(11.dp))
        Text(
            text = stringResource(
                if (type == QBotSessionType.GROUP) R.string.qqbot_session_group
                else R.string.qqbot_session_private,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    val colors = LocalAppTheme.current.colors
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(colors.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = colors.onError,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 聊天窗口
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ChatView(
    messages: List<QBotMessage>,
    session: QBotSession?,
    onClear: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    if (session == null) {
        EmptyState(message = stringResource(R.string.qqbot_no_session_selected))
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // 聊天头部：会话名 + 清空消息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = session.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.qqbot_clear_messages),
                    tint = colors.textSecondary,
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderMuted)

        if (messages.isEmpty()) {
            EmptyState(message = stringResource(R.string.qqbot_no_messages))
            return
        }
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, session.sessionId) {
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = PrimitiveSpacing.Md),
        ) {
            items(messages, key = { it.messageId }) { msg ->
                MessageBubble(
                    message = msg,
                    showSenderName = session.type == QBotSessionType.GROUP,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: QBotMessage, showSenderName: Boolean) {
    val colors = LocalAppTheme.current.colors
    val isBot = message.isBot
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PrimitiveSpacing.Lg, vertical = 3.dp),
        horizontalArrangement = if (isBot) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        if (isBot) {
            QBotAvatar(name = message.senderName, size = 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                BubbleSurface(text = message.content, bg = colors.surfaceCard, fg = colors.textPrimary)
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
                BubbleSurface(text = message.content, bg = colors.brandContainer, fg = colors.onBrandContainer)
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            QBotAvatar(name = message.senderName, size = 36.dp)
        }
    }
}

@Composable
private fun BubbleSurface(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

// ─────────────────────────────────────────────────────────────────────
// 设置面板
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPanel(
    config: QBotConfig,
    modelOptions: List<QBotModelOption>,
    selectedModelContextTokens: Int,
    loginState: QBotLoginState,
    onUpdate: (QBotConfig) -> Unit,
    onGenerateToken: () -> Unit,
    onSelectModel: (QBotModelOption) -> Unit,
    onSetLoginType: (String) -> Unit,
    onSetPassword: (String) -> Unit,
    savedPassword: String,
    onRelogin: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    var qqText by remember(config.botQq) {
        mutableStateOf(if (config.botQq != 0L) config.botQq.toString() else "")
    }
    var portText by remember(config.wsPort) { mutableStateOf(config.wsPort.toString()) }
    var tokenText by remember(config.wsToken) { mutableStateOf(config.wsToken) }
    var passwordText by remember(savedPassword) { mutableStateOf(savedPassword) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PrimitiveSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
    ) {
        // ── 账号登录区 ──────────────────────────────────────────────
        SectionHeader(text = stringResource(R.string.qqbot_login_section))

        Text(
            text = stringResource(R.string.qqbot_login_type),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
            FilterChip(
                selected = config.loginType == "qrcode",
                onClick = { onSetLoginType("qrcode") },
                label = { Text(stringResource(R.string.qqbot_login_type_qrcode)) },
            )
            FilterChip(
                selected = config.loginType == "password",
                onClick = { onSetLoginType("password") },
                label = { Text(stringResource(R.string.qqbot_login_type_password)) },
            )
        }

        if (config.loginType == "password") {
            OutlinedTextField(
                value = passwordText,
                onValueChange = {
                    passwordText = it
                    onSetPassword(it)
                },
                label = { Text(stringResource(R.string.qqbot_login_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedButton(
            onClick = onRelogin,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            Text(stringResource(R.string.qqbot_relogin))
        }

        HorizontalDivider(color = colors.borderMuted)

        // ── 连接配置区 ──────────────────────────────────────────────
        OutlinedTextField(
            value = qqText,
            onValueChange = {
                qqText = it
                onUpdate(config.copy(botQq = it.trim().toLongOrNull() ?: 0L))
            },
            label = { Text(stringResource(R.string.qqbot_bot_qq)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = portText,
            onValueChange = {
                portText = it
                onUpdate(config.copy(wsPort = it.trim().toIntOrNull() ?: 3001))
            },
            label = { Text(stringResource(R.string.qqbot_ws_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = tokenText,
                onValueChange = {
                    tokenText = it
                    onUpdate(config.copy(wsToken = it))
                },
                label = { Text(stringResource(R.string.qqbot_ws_token)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(PrimitiveSpacing.Sm))
            OutlinedButton(onClick = onGenerateToken) {
                Text(stringResource(R.string.qqbot_generate_token))
            }
        }

        // ── 模型选择（下拉）─────────────────────────────────────────
        ModelDropdown(
            selectedModelId = config.defaultModel,
            options = modelOptions,
            onSelect = onSelectModel,
        )

        // 上下文长度：只读显示，沿用模型设置。
        InfoRow(
            label = stringResource(R.string.qqbot_context_length_readonly),
            value = if (selectedModelContextTokens > 0) {
                stringResource(
                    R.string.qqbot_model_context_follow,
                    formatTokens(selectedModelContextTokens),
                )
            } else {
                config.contextLength.toString()
            },
        )

        AppListItem(
            icon = Icons.Rounded.Power,
            title = stringResource(R.string.qqbot_auto_start),
            checked = config.enabled,
            onCheckedChange = { onUpdate(config.copy(enabled = it)) },
        )
        AppListItem(
            icon = Icons.Rounded.ChatBubbleOutline,
            title = stringResource(R.string.qqbot_private_trigger),
            checked = config.privateTriggerEnabled,
            onCheckedChange = { onUpdate(config.copy(privateTriggerEnabled = it)) },
        )
        AppListItem(
            icon = Icons.Rounded.Group,
            title = stringResource(R.string.qqbot_group_at_trigger),
            checked = config.groupAtTriggerEnabled,
            onCheckedChange = { onUpdate(config.copy(groupAtTriggerEnabled = it)) },
        )

        Spacer(Modifier.height(PrimitiveSpacing.Lg))
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalAppTheme.current.colors
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.brandPrimary,
    )
}

/** 模型下拉选择器：从已有供应商模型列表中选择，不允许自由输入。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selectedModelId: String,
    options: List<QBotModelOption>,
    onSelect: (QBotModelOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.modelId == selectedModelId }
    val displayText = selected?.displayLabel
        ?: selectedModelId.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.qqbot_model_picker_hint)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        AppTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.qqbot_default_model)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.qqbot_no_models_available)) },
                    onClick = { expanded = false },
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayLabel) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun formatTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}

// ─────────────────────────────────────────────────────────────────────
// 通用小组件
// ─────────────────────────────────────────────────────────────────────

/** 无 Coil 依赖时的圆形占位头像：按名称哈希取色，显示首字符。 */
@Composable
private fun QBotAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val bg = remember(name) {
        val hash = (name.hashCode() % 360 + 360) % 360
        Color.hsl(hash / 360f, 0.45f, 0.55f)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.toString()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.6f).sp,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    val colors = LocalAppTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PrimitiveSpacing.Xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textTertiary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// 时间格式化
// ─────────────────────────────────────────────────────────────────────

private fun formatUptime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L)
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun formatSessionTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < 24 * 60 * 60 * 1000L ->
            SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
        diff < 7 * 24 * 60 * 60 * 1000L ->
            SimpleDateFormat("MM-dd", Locale.US).format(Date(ts))
        else ->
            SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(ts))
    }
}

private fun formatMessageTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
