package com.mini.me_core.feature.qqbot.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.mini.me_core.feature.qqbot.domain.QBotMessage
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
                onStart = viewModel::startBot,
                onStop = viewModel::stopBot,
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
                    onUpdate = { newConfig -> viewModel.updateConfig { newConfig } },
                    onGenerateToken = viewModel::generateToken,
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
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    val isActive = botState == QBotState.RUNNING || botState == QBotState.STARTING

    AppCard(
        modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg, vertical = PrimitiveSpacing.Md),
    ) {
        Column(modifier = Modifier.padding(PrimitiveSpacing.Lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(botState = botState)
                Spacer(Modifier.weight(1f))
                ConnectionBadge(wsState = wsState)
            }

            Spacer(Modifier.height(PrimitiveSpacing.Md))

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

@Composable
private fun SettingsPanel(
    config: QBotConfig,
    onUpdate: (QBotConfig) -> Unit,
    onGenerateToken: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors

    var qqText by remember(config.botQq) {
        mutableStateOf(if (config.botQq != 0L) config.botQq.toString() else "")
    }
    var portText by remember(config.wsPort) { mutableStateOf(config.wsPort.toString()) }
    var tokenText by remember(config.wsToken) { mutableStateOf(config.wsToken) }
    var modelText by remember(config.defaultModel) { mutableStateOf(config.defaultModel) }
    var ctxText by remember(config.contextLength) { mutableStateOf(config.contextLength.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PrimitiveSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Md),
    ) {
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

        OutlinedTextField(
            value = modelText,
            onValueChange = {
                modelText = it
                onUpdate(config.copy(defaultModel = it))
            },
            label = { Text(stringResource(R.string.qqbot_default_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = ctxText,
            onValueChange = {
                ctxText = it
                onUpdate(config.copy(contextLength = it.trim().toIntOrNull() ?: 20))
            },
            label = { Text(stringResource(R.string.qqbot_context_length)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(PrimitiveSpacing.Lg))
    }
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
