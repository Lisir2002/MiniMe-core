@file:Suppress("FunctionNaming", "LongMethod") // Compose @Composable PascalCase 命名约定；长 Composable 逐字搬迁，行为保持
package com.mini.me_core.feature.agent.presentation.component

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.core.theme.tokens.IconSizeTokens
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import com.mini.me_core.core.theme.components.AppDropdownMenu
import com.mini.me_core.feature.agent.domain.core.model.ChatSession
import com.mini.me_core.feature.workspace.domain.FileEntry
import com.mini.me_core.feature.workspace.domain.model.Workspace
import com.mini.me_core.feature.workspace.presentation.WorkspaceFileViewModel
import com.mini.me_core.feature.workspace.presentation.WorkspaceViewModel
import androidx.compose.ui.res.stringResource

/**
 * 侧边栏「工作目录」tab：内嵌子 tab（当前工作台 / 所有工作台）。
 * - 当前工作台：以 [WorkspaceFileViewModel] 浏览当前工作区文件树，点击文件跳转独立阅读页；
 * - 所有工作台：工作区列表管理（切换 / 新建 / 删除）。
 * 工作区数据源复用 WorkspaceViewModel，与输入框底部工作区弹窗一致。
 */
@Composable
internal fun WorkspaceDirPanel(
    viewModel: WorkspaceViewModel,
    fileViewModel: WorkspaceFileViewModel,
    hasRunningSessions: () -> Boolean,
    onSwitchConfirmed: () -> Unit,
    onOpenFile: (String) -> Unit,
    boundSessionsForWorkspace: suspend (String) -> List<ChatSession>,
    modifier: Modifier = Modifier
) {
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    val current by viewModel.current.collectAsStateWithLifecycle()

    var subTab by rememberSaveable { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Workspace?>(null) }
    var pendingSwitch by remember { mutableStateOf<Workspace?>(null) }
    var pendingRename by remember { mutableStateOf<Workspace?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerNavIcon(
                icon = Icons.Rounded.Folder,
                iconBgLight = Color(0xFFF59E0B),
                iconBgDark = Color(0xFF92400E)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_drawer_tab_workspace),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = current?.name ?: stringResource(R.string.workspace_select),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { showCreateDialog = true }) {
                Icon(
                    Icons.Rounded.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(IconSizeTokens.medium)
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.workspace_new))
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )

        // 内嵌子 tab：当前工作台（文件浏览） / 所有工作台（列表管理）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            WorkspaceSubTab(
                text = stringResource(R.string.workspace_tab_current),
                selected = subTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { subTab = 0 }
            )
            WorkspaceSubTab(
                text = stringResource(R.string.workspace_tab_all),
                selected = subTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { subTab = 1 }
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        Box(modifier = Modifier.weight(1f)) {
            if (subTab == 0) {
                CurrentWorkspacePanel(
                    viewModel = fileViewModel,
                    onOpenFile = onOpenFile,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AllWorkspacesPanel(
                    workspaces = workspaces,
                    currentName = current?.name,
                    onSwitchRequest = { ws ->
                        if (hasRunningSessions()) {
                            pendingSwitch = ws
                        } else {
                            onSwitchConfirmed()
                            viewModel.selectWorkspace(ws.name)
                        }
                    },
                    onDeleteRequest = { pendingDelete = it },
                    onRenameRequest = { pendingRename = it },
                    boundSessionsForWorkspace = boundSessionsForWorkspace,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // 切换确认：当前工作区存在运行中的 AI 会话/终端时需用户确认
    pendingSwitch?.let { ws ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text(stringResource(R.string.workspace_switch)) },
            text = { Text(stringResource(R.string.workspace_switch_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onSwitchConfirmed()
                    viewModel.selectWorkspace(ws.name)
                    pendingSwitch = null
                }) { Text(stringResource(R.string.workspace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitch = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 删除确认：打开时查询绑定会话数量，提示用户将级联删除 N 个对话
    pendingDelete?.let { ws ->
        var boundCount by remember(ws.path) { mutableStateOf<Int?>(null) }
        LaunchedEffect(ws.path) {
            boundCount = runCatching { boundSessionsForWorkspace(ws.path).size }.getOrNull()
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.workspace_delete)) },
            text = {
                val count = boundCount
                if (count != null && count > 0) {
                    Text(stringResource(R.string.workspace_delete_confirm_with_sessions, ws.name, count))
                } else {
                    Text(stringResource(R.string.workspace_delete_confirm, ws.name))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkspace(ws.name)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 重命名工作区
    pendingRename?.let { ws ->
        var renameText by remember(ws.name) { mutableStateOf(ws.name) }
        val existingNames = workspaces.map { it.name } - ws.name
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.workspace_rename)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.workspace_rename_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (renameText.isNotBlank() && renameText.trim() in existingNames) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.workspace_name_exists),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank() &&
                        renameText.trim() != ws.name &&
                        renameText.trim() !in existingNames,
                    onClick = {
                        viewModel.renameWorkspace(ws.name, renameText.trim())
                        pendingRename = null
                    }
                ) { Text(stringResource(R.string.workspace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // 新建工作区
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        val existingNames = workspaces.map { it.name }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.workspace_new_workspace)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.workspace_new_workspace)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && name.trim() !in existingNames,
                    onClick = {
                        viewModel.createWorkspace(name.trim())
                        showCreateDialog = false
                    }
                ) { Text(stringResource(R.string.workspace_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/** 「当前工作台」子 tab 的 tab 项：圆角选中高亮的文字按钮。 */
@Composable
private fun WorkspaceSubTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 「当前工作台」子 tab：当前工作区文件树浏览器。
 * 数据源 [WorkspaceFileViewModel] 维护目录导航栈，目录可逐级进入，点击文件回调 [onOpenFile] 跳转独立阅读页。
 */
@Composable
private fun CurrentWorkspacePanel(
    viewModel: WorkspaceFileViewModel,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val dirStack by viewModel.dirStack.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        // 路径栏：返回上级按钮 + 当前相对路径
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = viewModel::goUp,
                enabled = dirStack.isNotEmpty(),
                modifier = Modifier.size(IconSizeTokens.large)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.workspace_file_up),
                    tint = if (dirStack.isNotEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(IconSizeTokens.medium)
                )
            }
            Text(
                text = if (dirStack.isEmpty()) {
                    stringResource(R.string.workspace_file_root)
                } else {
                    dirStack.joinToString("/")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            entries.isEmpty() -> Text(
                text = stringResource(R.string.workspace_file_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(entries, key = { it.name }) { entry ->
                    WorkspaceFileRow(
                        entry = entry,
                        onClick = {
                            if (entry.isDirectory) {
                                viewModel.enterDirectory(entry.name)
                            } else {
                                onOpenFile(viewModel.containerPathFor(entry))
                            }
                        }
                    )
                }
            }
        }
    }
}

/** 文件树行：文件夹用文件夹图标，文件用文件图标 + 大小，点击整行触发。 */
@Composable
private fun WorkspaceFileRow(
    entry: FileEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Rounded.Folder else fileTypeIcon(entry.name),
            contentDescription = null,
            tint = if (entry.isDirectory) Color(0xFFF59E0B) else fileTypeIconTint(entry.name, MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!entry.isDirectory && entry.size > 0) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = formatFileSize(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 「所有工作台」子 tab：工作区列表。点击行弹出下拉菜单（切换 / 重命名 / 删除 / 查看对话绑定），
 * 不再直接切换；「查看对话绑定」以手风琴形式展开该工作区绑定的会话列表。
 */
@Composable
private fun AllWorkspacesPanel(
    workspaces: List<Workspace>,
    currentName: String?,
    onSwitchRequest: (Workspace) -> Unit,
    onDeleteRequest: (Workspace) -> Unit,
    onRenameRequest: (Workspace) -> Unit,
    boundSessionsForWorkspace: suspend (String) -> List<ChatSession>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (workspaces.isEmpty()) {
            Text(
                stringResource(R.string.workspace_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                items(workspaces, key = { it.name }) { ws ->
                    WorkspaceDirRow(
                        workspace = ws,
                        selected = ws.name == currentName,
                        canDelete = workspaces.size > 1,
                        onSwitch = { onSwitchRequest(ws) },
                        onRename = { onRenameRequest(ws) },
                        onDelete = { onDeleteRequest(ws) },
                        boundSessions = { boundSessionsForWorkspace(ws.path) }
                    )
                }
            }
        }
    }
}

/** 文件大小人类可读格式化（B / KB / MB）。 */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

/** 提取文件小写扩展名（无扩展名返回空串）。 */
private fun fileExtension(name: String): String {
    val idx = name.lastIndexOf('.')
    return if (idx > 0 && idx < name.length - 1) name.substring(idx + 1).lowercase() else ""
}

/**
 * 按文件后缀返回对应图标，覆盖代码 / 文档 / 图片 / 压缩包 / 表格 / 数据库 / 配置 / 脚本等常见类型，
 * 无法识别时回退通用文件图标。目录走文件夹图标，不进入本函数。
 */
private fun fileTypeIcon(name: String): ImageVector {
    return when (fileExtension(name)) {
        // 图片
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "heic", "avif" -> Icons.Rounded.Image
        // PDF / 文档
        "pdf" -> Icons.Rounded.PictureAsPdf
        "doc", "docx", "odt", "rtf", "pages" -> Icons.Rounded.Description
        "md", "markdown", "txt", "rst", "adoc", "log", "text" -> Icons.Rounded.Article
        // 表格 / 数据
        "xls", "xlsx", "ods", "csv", "tsv" -> Icons.Rounded.TableChart
        // 数据库
        "db", "sqlite", "sqlite3", "sql" -> Icons.Rounded.Storage
        // 压缩包
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "jar", "apk", "aab" -> Icons.Rounded.FolderZip
        // Web / 标记 / 配置
        "html", "htm", "css", "scss", "sass", "less", "xml", "json", "yml", "yaml", "toml",
        "ini", "conf", "env", "properties", "gradle", "gradle.kts", "editorconfig" -> Icons.Rounded.Language
        // 脚本 / 终端
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1" -> Icons.Rounded.Terminal
        // 代码
        "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "jsx", "tsx", "go", "rs", "c", "h",
        "cpp", "cc", "cxx", "hpp", "hxx", "swift", "rb", "php", "lua", "scala", "groovy", "dart",
        "ex", "exs", "erl", "hs", "clj", "cljs", "cljc", "vue", "svelte", "proto" -> Icons.Rounded.Code
        // 通用
        else -> Icons.Rounded.InsertDriveFile
    }
}

/** 文件图标配色：按类型给浅色着色，便于识别；未知类型回退 [fallback]。 */
private fun fileTypeIconTint(name: String, fallback: Color): Color {
    return when (fileExtension(name)) {
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "heic", "avif" -> Color(0xFF8B5CF6)
        "pdf" -> Color(0xFFEF4444)
        "md", "markdown", "txt", "rst", "adoc", "log", "text" -> Color(0xFF64748B)
        "doc", "docx", "odt", "rtf", "pages" -> Color(0xFF2563EB)
        "xls", "xlsx", "ods", "csv", "tsv" -> Color(0xFF16A34A)
        "db", "sqlite", "sqlite3", "sql" -> Color(0xFF0EA5E9)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "jar", "apk", "aab" -> Color(0xFFF59E0B)
        "html", "htm", "css", "scss", "sass", "less", "xml", "json", "yml", "yaml", "toml",
        "ini", "conf", "env", "properties", "gradle", "gradle.kts", "editorconfig" -> Color(0xFF0EA5E9)
        "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1" -> Color(0xFF22C55E)
        "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "jsx", "tsx", "go", "rs", "c", "h",
        "cpp", "cc", "cxx", "hpp", "hxx", "swift", "rb", "php", "lua", "scala", "groovy", "dart",
        "ex", "exs", "erl", "hs", "clj", "cljs", "cljc", "vue", "svelte", "proto" -> Color(0xFF6366F1)
        else -> fallback
    }
}

/**
 * 工作区列表行：点击整行弹出下拉菜单（切换 / 重命名 / 删除 / 查看对话绑定）。
 * 「查看对话绑定」手风琴式展开：点击后加载并展示该工作区绑定的会话列表。
 */
@Suppress("LongParameterList") // 签名保持
@Composable
private fun WorkspaceDirRow(
    workspace: Workspace,
    selected: Boolean,
    canDelete: Boolean,
    onSwitch: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    boundSessions: suspend () -> List<ChatSession>,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var bindingsExpanded by remember { mutableStateOf(false) }
    var boundSessionsState by remember { mutableStateOf<List<ChatSession>?>(null) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                .clickable { menuExpanded = true }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.workspace_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.xs)
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = stringResource(R.string.workspace_menu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizeTokens.medium)
            )
        }

        AppDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.width(260.dp)
        ) {
            if (!selected) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workspace_switch_to)) },
                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                    onClick = {
                        menuExpanded = false
                        onSwitch()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_rename)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.workspace_view_bindings)) },
                leadingIcon = { Icon(Icons.Rounded.Forum, null) },
                trailingIcon = {
                    Icon(
                        if (bindingsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null
                    )
                },
                onClick = { bindingsExpanded = !bindingsExpanded }
            )
            if (bindingsExpanded) {
                LaunchedEffect(workspace.path) {
                    boundSessionsState = boundSessions()
                }
                val sessions = boundSessionsState
                when {
                    sessions == null -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_loading)) },
                        enabled = false,
                        onClick = {}
                    )
                    sessions.isEmpty() -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.workspace_no_bound_sessions)) },
                        enabled = false,
                        onClick = {}
                    )
                    else -> sessions.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = s.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

/** 侧边栏「更多配置」tab：占位空白板块，后续按需填充。 */
@Composable
internal fun MoreConfigPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSizeTokens.xLarge)
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.chat_drawer_tab_more_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 侧边栏「更多配置」tab：工作台绑定等配置项。
 *
 * 工作台绑定：会话与工作台一一绑定、不可中途切换。新会话创建时未绑定，用户发送首条消息时
 * 自动绑定当前工作台；也可在本面板把当前（未绑定）会话手动绑定到任一工作台。
 */
@Composable
internal fun MoreConfigPanel(
    workspaceViewModel: WorkspaceViewModel?,
    currentSession: ChatSession?,
    onBindWorkspace: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val workspaces = workspaceViewModel?.workspaces?.collectAsStateWithLifecycle()?.value ?: emptyList()
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    val currentWorkspacePath = currentWorkspace?.path.orEmpty()

    val sessionWorkspacePath = currentSession?.workspacePath.orEmpty()
    val isBound = sessionWorkspacePath.isNotBlank()
    val boundWorkspaceName = workspaces.firstOrNull { it.path == sessionWorkspacePath }?.name
        ?: sessionWorkspacePath.substringAfterLast('/').ifBlank { sessionWorkspacePath }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        item {
            Column(Modifier.padding(horizontal = Spacing.xs)) {
                // 板块标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = Spacing.md)
                ) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSizeTokens.medium)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.workspace_bind_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = stringResource(R.string.workspace_bind_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))

                if (isBound) {
                    // 已绑定：展示绑定目标，绑定一次性、不可中途切换
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = Spacing.md, vertical = Spacing.md)
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.workspace_bind_bound, boundWorkspaceName),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.workspace_bind_bound_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // 未绑定：展示可绑定的工作台列表
                    Text(
                        text = stringResource(R.string.workspace_bind_unbound_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    when {
                        currentSession == null -> Text(
                            text = stringResource(R.string.workspace_bind_no_session),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.sm)
                        )
                        workspaces.isEmpty() -> Text(
                            text = stringResource(R.string.workspace_bind_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.sm)
                        )
                        else -> workspaces.forEach { ws ->
                            WorkspaceBindRow(
                                workspace = ws,
                                isCurrent = ws.path == currentWorkspacePath,
                                onClick = { onBindWorkspace(ws.path) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 手动绑定面板中的工作台选择行：点击即把当前会话绑定到该工作台。 */
@Composable
private fun WorkspaceBindRow(
    workspace: Workspace,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCornerRadius.current.md))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md)
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = workspace.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.workspace_current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.xs)
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            Icons.Rounded.Link,
            contentDescription = stringResource(R.string.workspace_bind_action),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(IconSizeTokens.medium)
        )
    }
}
