package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.datalayer.toChatSession
import com.mini.me_core.feature.agent.domain.core.model.ChatSession
import com.mini.me_core.feature.agent.presentation.AgentUIState
import com.mini.me_core.feature.settings.data.repository.AppThemeMode
import com.mini.me_core.feature.workspace.presentation.WorkspaceFileViewModel
import com.mini.me_core.feature.workspace.presentation.WorkspaceViewModel
import com.mini.mecore.datalayer.sqldelight.agent.SelectAllSessionsWithCount as V2SessionWithCount
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * 侧边栏内容：内部设有 tab 菜单（对话列表 / 工作目录 / 更多配置），
 * 底部保留主题切换与设置两个图标按钮（贴右排列）。
 * 由 MainActivity 的 ModalNavigationDrawer 承载，支持左上角按钮点击或右滑打开。
 *
 * 图标规范：彩色圆角图标块 + 白色图标（Material You 风格），日夜间各一套背景色，
 * 随 LocalAppDarkMode 自动切换。
 */
@Composable
fun ChatDrawerContent(
    sessionsWithCount: List<V2SessionWithCount>,
    currentSessionId: String?,
    agentStates: Map<String, AgentUIState>,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onRename: (ChatSession, String) -> Unit,
    onExport: (ChatSession) -> Unit,
    onUndoDelete: () -> Unit = {},
    currentThemeMode: AppThemeMode,
    onCycleTheme: () -> Unit,
    workspaceViewModel: WorkspaceViewModel? = null,
    workspaceFileViewModel: WorkspaceFileViewModel? = null,
    hasRunningSessions: () -> Boolean = { false },
    onSwitchWorkspaceConfirmed: () -> Unit = {},
    onOpenFile: (String) -> Unit = {},
    /** 查询某工作区绑定的会话（供「所有工作台 → 查看对话绑定」展示）。 */
    boundSessionsForWorkspace: suspend (String) -> List<ChatSession> = { emptyList() },
    /** 当前会话（供「更多配置 → 工作台绑定」展示绑定状态）。 */
    currentSession: ChatSession? = null,
    /** 手动绑定当前会话到指定工作台（「更多配置 → 工作台绑定」）。 */
    onBindWorkspace: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var pendingRename by remember { mutableStateOf<ChatSession?>(null) }
    var menuSession by remember { mutableStateOf<ChatSession?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.lg)
    ) {
        // 顶部 tab 菜单：对话列表 / 工作目录 / 更多配置。
        // 高度固定 44dp，与全局 AppTopAppBar 标题栏高度完全一致，保持视觉对齐。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            DrawerTopTab(
                text = stringResource(R.string.chat_drawer_tab_chats),
                selected = selectedTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 }
            )
            DrawerTopTab(
                text = stringResource(R.string.chat_drawer_tab_workspace),
                selected = selectedTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 1 }
            )
            DrawerTopTab(
                text = stringResource(R.string.chat_drawer_tab_more),
                selected = selectedTab == 2,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 2 }
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        when (selectedTab) {
            0 -> ChatSessionListPanel(
                sessions = sessionsWithCount,
                currentSessionId = currentSessionId,
                agentStates = agentStates,
                onSelect = onSelect,
                onLongPress = { menuSession = it },
                onDirectDelete = { swiped ->
                    onDelete(swiped.toChatSession())
                    val deletedMsg = context.getString(R.string.chat_deleted_snackbar, swiped.title)
                    val undoLabel = context.getString(R.string.chat_undo)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = deletedMsg,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndoDelete()
                        }
                    }
                },
                onSwipeRename = { pendingRename = it.toChatSession() },
                modifier = Modifier.weight(1f)
            )
            1 -> if (workspaceViewModel != null && workspaceFileViewModel != null) {
                WorkspaceDirPanel(
                    viewModel = workspaceViewModel,
                    fileViewModel = workspaceFileViewModel,
                    hasRunningSessions = hasRunningSessions,
                    onSwitchConfirmed = onSwitchWorkspaceConfirmed,
                    onOpenFile = onOpenFile,
                    boundSessionsForWorkspace = boundSessionsForWorkspace,
                    modifier = Modifier.weight(1f)
                )
            } else {
                MoreConfigPlaceholder(modifier = Modifier.weight(1f))
            }
            else -> MoreConfigPanel(
                workspaceViewModel = workspaceViewModel,
                currentSession = currentSession,
                onBindWorkspace = onBindWorkspace,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )
        // 底部仅保留「主题切换」纯图标按钮，贴右排列；
        // 设置与打包器入口已收敛到右侧工具抽屉（避免两侧重复）。
        // 两侧各留 Spacing.md 边距，与侧边栏内容左右对齐，视觉更舒适。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerBottomIconButton(
                icon = when (currentThemeMode) {
                    AppThemeMode.DARK -> Icons.Rounded.DarkMode
                    AppThemeMode.LIGHT -> Icons.Rounded.LightMode
                    AppThemeMode.AUTO -> Icons.Rounded.BrightnessAuto
                },
                contentDescription = stringResource(
                    when (currentThemeMode) {
                        AppThemeMode.DARK -> R.string.theme_dark
                        AppThemeMode.LIGHT -> R.string.theme_light
                        AppThemeMode.AUTO -> R.string.theme_auto
                    }
                ),
                iconBgLight = Color(0xFFF59E0B),
                iconBgDark = Color(0xFF92400E),
                onClick = onCycleTheme
            )
        }

        // 删除后的 Snackbar（含「撤销」动作），位于侧边栏底部导航下方。
        SnackbarHost(hostState = snackbarHostState)
    }

    pendingDelete?.let { session ->
        val isExecuting = agentStates[session.id] is AgentUIState.Loading ||
            agentStates[session.id] is AgentUIState.Streaming
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_session)) },
            text = {
                Column {
                    Text(stringResource(R.string.chat_delete_session_confirm, session.title))
                    if (isExecuting) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.chat_delete_session_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                val deletedMsg = stringResource(R.string.chat_deleted_snackbar, session.title)
                val undoLabel = stringResource(R.string.chat_undo)
                TextButton(onClick = {
                    onDelete(session)
                    pendingDelete = null
                    // 删除成功后弹 Snackbar「已删除 · 撤销」；点「撤销」恢复会话+消息
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = deletedMsg,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndoDelete()
                        }
                    }
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    menuSession?.let { session ->
        SessionActionSheet(
            session = session,
            onRename = {
                menuSession = null
                pendingRename = session
            },
            onExport = {
                menuSession = null
                onExport(session)
            },
            onDelete = {
                menuSession = null
                pendingDelete = session
            },
            onDismiss = { menuSession = null }
        )
    }

    pendingRename?.let { session ->
        var renameText by remember(session.id) { mutableStateOf(session.title) }
        val canSubmit = renameText.isNotBlank() && renameText.trim() != session.title
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.chat_rename_session)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_session_name)) },
                    // IME 回车 = 提交（与确认按钮等效）
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canSubmit) {
                            onRename(session, renameText)
                            pendingRename = null
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(session, renameText)
                        pendingRename = null
                    },
                    enabled = canSubmit
                ) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
