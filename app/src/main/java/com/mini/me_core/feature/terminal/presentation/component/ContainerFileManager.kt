package com.mini.me_core.feature.terminal.presentation.component

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mini.me_core.R
import com.mini.me_core.core.ui.components.FileBrowserItem
import com.mini.me_core.core.ui.components.fileIconVisual
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import com.mini.me_core.feature.terminal.domain.ContainerFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 容器内文件管理器主组件（P0/P1 重构后）：
 * - 无嵌套 Scaffold，由调用方提供外层容器；本组件返回 Column。
 * - 面包屑 + 上级按钮 + 列表/网格 + 底部工具栏 + 各类 BottomSheet。
 */
@Composable
fun ContainerFileManager(
    access: ContainerFileAccess,
    modifier: Modifier = Modifier,
    clipboardPaths: List<String> = emptyList(),
    clipboardCut: Boolean = false,
    onClipboard: (paths: List<String>, cut: Boolean) -> Unit = { _, _ -> },
    onClearClipboard: () -> Unit = {},
    onOpenEditor: (ContainerFileEntry) -> Unit = {},
) {
    var currentPath by remember { mutableStateOf("/root") }
    var initialized by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ContainerFileEntry>>(emptyList()) }
    var childCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf(FileSortBy.NAME) }
    var ascending by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionTarget by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var propsTarget by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<List<ContainerFileEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(showSearch) {
        if (showSearch) searchFocusRequester.requestFocus()
    }

    // 搜索过滤结果（同时影响列表与网格视图）
    val searching = showSearch && searchQuery.isNotBlank()
    val displayEntries = remember(entries, searchQuery, showSearch) {
        if (!showSearch || searchQuery.isBlank()) entries
        else entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        val home = runCatching { access.homeDir() }.getOrNull()
        if (!home.isNullOrBlank()) currentPath = home
        initialized = true
    }

    fun reload() {
        if (!initialized) return
        loading = true
        scope.launch {
            val list = access.listDir(currentPath, showHidden)
            val dirs = list.filter { it.isDir }.map { it.path }
            childCounts = access.countDirChildren(dirs)
            entries = list.sortedWith(
                compareByDescending<ContainerFileEntry> { it.isDir }.thenComparator { a, b ->
                    val cmp = when (sortBy) {
                        FileSortBy.NAME -> a.name.lowercase().compareTo(b.name.lowercase())
                        FileSortBy.TIME -> a.modifiedAt.compareTo(b.modifiedAt)
                        FileSortBy.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                    }
                    if (ascending) cmp else -cmp
                }
            )
            loading = false
        }
    }

    LaunchedEffect(currentPath, showHidden, sortBy, ascending, initialized) { reload() }

    val isRoot = currentPath == "/" || currentPath.isEmpty()
    fun goUp() {
        if (isRoot) return
        val parent = currentPath.substringBeforeLast('/').ifEmpty { "/" }
        currentPath = parent
        selected = emptySet()
    }

    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
    }
    BackHandler(enabled = !showSearch && (!isRoot || selected.isNotEmpty())) {
        if (selected.isNotEmpty()) selected = emptySet() else goUp()
    }

    // 文件选择器：导入一个或多个文件到当前目录
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.fm_importing, uris.size))
            var success = 0
            var lastError: String? = null
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val fileName = queryDisplayName(context, uri)
                            ?: uri.lastPathSegment?.substringAfterLast('/')
                            ?: "imported_${System.currentTimeMillis()}"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("无法打开 $uri")
                        val target = if (currentPath.endsWith("/")) currentPath + fileName else "$currentPath/$fileName"
                        access.writeFileBytes(target, bytes)
                            .onSuccess { success++ }
                            .onFailure { lastError = it.message ?: it.toString() }
                    } catch (t: Throwable) {
                        lastError = t.message ?: t.toString()
                    }
                }
            }
            reload()
            if (success > 0) {
                snackbar.showSnackbar(context.getString(R.string.fm_import_success, success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.fm_import_failed, lastError ?: "unknown"))
            }
        }
    }

    fun startImport() {
        runCatching {
            importLauncher.launch(arrayOf("*/*"))
        }.onFailure {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.fm_import_failed, it.message ?: it.toString()))
            }
        }
    }

    fun shareEntry(entry: ContainerFileEntry) {
        if (entry.isDir) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.fm_share_dir_not_supported)) }
            return
        }
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    access.readFileBytes(entry.path).getOrThrow()
                }
                val cacheFile = withContext(Dispatchers.IO) {
                    File(context.cacheDir, entry.name).apply { writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", cacheFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = guessMimeByName(entry.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.fm_share_title))
                )
            } catch (t: Throwable) {
                snackbar.showSnackbar(context.getString(R.string.fm_share_failed, t.message ?: t.toString()))
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goUp() }, enabled = !isRoot) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.fm_navigate_up),
                    tint = if (isRoot) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            BreadcrumbRow(path = currentPath, onNavigate = { currentPath = it },
                modifier = Modifier.weight(1f))
        }
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .focusRequester(searchFocusRequester),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.fm_search_hint)) }
            )
        }
        if (searching) {
            Text(
                text = stringResource(R.string.fm_search_result_count, displayEntries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        if (loading && !searching) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        }

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = currentPath,
                transitionSpec = {
                    slideInHorizontally { it / 4 } togetherWith slideOutHorizontally { -it / 4 }
                },
                label = "fileNav"
            ) { path ->
                if (searching && displayEntries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.fm_search_no_result),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (entries.isEmpty() && !loading) {
                    EmptyState(onNew = { showNewDialog = true })
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayEntries, key = { it.path }) { entry ->
                            val type = ContainerFileType.classify(entry)
                            GridFileItem(
                                entry = entry, type = type, selected = entry.path in selected,
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
                                    } else if (entry.isDir) {
                                        currentPath = entry.path
                                    } else {
                                        onOpenEditor(entry)
                                    }
                                },
                                onLongClick = { actionTarget = entry }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(displayEntries, key = { it.path }) { entry ->
                            val type = ContainerFileType.classify(entry)
                            val visual = fileIconVisual(
                                name = entry.name,
                                isDir = entry.isDir,
                                executable = type == ContainerFileType.EXECUTABLE,
                            )
                            val subtitle = when {
                                entry.isDir -> childCounts[entry.path]?.let {
                                    "$it ${stringResource(R.string.fm_items_suffix)}"
                                } ?: "-"

                                else -> "${formatSize(entry.sizeBytes)} · ${formatSmartTime(entry.modifiedAt)}"
                            }
                            FileBrowserItem(
                                name = entry.name,
                                subtitle = subtitle,
                                icon = visual.icon,
                                iconBg = visual.iconBg,
                                iconFg = visual.iconFg,
                                selected = entry.path in selected,
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
                                    } else if (entry.isDir) {
                                        currentPath = entry.path
                                    } else {
                                        onOpenEditor(entry)
                                    }
                                },
                                onLongClick = { actionTarget = entry },
                                trailing = if (entry.path in selected) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }

        FileBrowserToolbar(
            multiSelect = selected.isNotEmpty(),
            selectedCount = selected.size,
            clipboardCount = clipboardPaths.size,
            onSearch = {
                showSearch = !showSearch
                if (!showSearch) searchQuery = ""
            },
            onOpenSortMenu = { showSortMenu = true },
            onToggleView = { gridView = !gridView },
            onNew = { showNewDialog = true },
            onImport = { startImport() },
            onPaste = {
                if (clipboardPaths.isEmpty()) return@FileBrowserToolbar
                scope.launch {
                    val r = if (clipboardCut) access.move(clipboardPaths, currentPath)
                    else access.copy(clipboardPaths, currentPath)
                    r.onSuccess { snackbar.showSnackbar(context.getString(R.string.fm_pasted)); onClearClipboard(); reload() }
                        .onFailure { snackbar.showSnackbar(context.getString(R.string.fm_copy_failed)) }
                }
            },
            onSelectAll = { selected = entries.map { it.path }.toSet() },
            onCopy = { onClipboard(selected.toList(), false); selected = emptySet() },
            onCut = { onClipboard(selected.toList(), true); selected = emptySet() },
            onDelete = { pendingDelete = entries.filter { it.path in selected } },
            onExitMultiSelect = { selected = emptySet() }
        )
    }

    actionTarget?.let { entry ->
        FileActionSheet(
            entry = entry, type = ContainerFileType.classify(entry),
            onDismiss = { actionTarget = null },
            onOpen = { if (entry.isDir) currentPath = entry.path else onOpenEditor(entry) },
            onEdit = { onOpenEditor(entry) },
            onShare = { actionTarget = null; shareEntry(entry) },
            onCopy = { onClipboard(listOf(entry.path), false) },
            onCut = { onClipboard(listOf(entry.path), true) },
            onRename = { pendingRename = entry },
            onProperties = { propsTarget = entry },
            onDelete = { pendingDelete = listOf(entry) },
            onMultiSelect = { selected = setOf(entry.path) }
        )
    }

    propsTarget?.let { entry ->
        FilePropertiesSheet(
            entry = entry, type = ContainerFileType.classify(entry),
            childCount = childCounts[entry.path],
            onDismiss = { propsTarget = null },
            onCopyPath = {
                scope.launch {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("path", entry.path))
                    snackbar.showSnackbar(context.getString(R.string.fm_path_copied))
                }
            }
        )
    }

    if (showSortMenu) {
        FileSortMenu(
            sortBy = sortBy, ascending = ascending, showHidden = showHidden, gridView = gridView,
            onSortBy = { sortBy = it; showSortMenu = false },
            onAscending = { ascending = it },
            onShowHidden = { showHidden = it },
            onGridView = { gridView = it },
            onDismiss = { showSortMenu = false }
        )
    }

    if (showNewDialog) {
        var name by remember { mutableStateOf("") }
        var isDir by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text(stringResource(R.string.fm_new_here)) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isDir, onClick = { isDir = true },
                            label = { Text(stringResource(R.string.fm_new_folder)) })
                        FilterChip(selected = !isDir, onClick = { isDir = false },
                            label = { Text(stringResource(R.string.fm_new_file)) })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.fm_name_hint)) },
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val target = if (currentPath.endsWith("/")) currentPath + name else "$currentPath/$name"
                scope.launch {
                    val r = if (isDir) access.mkdir(target) else access.createFile(target)
                    r.onSuccess { showNewDialog = false; reload() }
                }
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("Cancel") } }
        )
    }

    pendingRename?.let { entry ->
        var newName by remember { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.fm_rename)) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it },
                singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = {
                if (newName.isBlank() || newName == entry.name) return@TextButton
                val parent = entry.path.substringBeforeLast('/').ifEmpty { "/" }
                val target = if (parent.endsWith("/")) parent + newName else "$parent/$newName"
                scope.launch {
                    access.rename(entry.path, target).onSuccess { pendingRename = null; reload() }
                }
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancel") } }
        )
    }

    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text(stringResource(R.string.fm_confirm_delete_title)) },
            text = { Text(stringResource(R.string.fm_confirm_delete_msg, pendingDelete.size)) },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    access.delete(pendingDelete.map { it.path }).onSuccess {
                        pendingDelete = emptyList(); selected = emptySet(); reload()
                    }
                }
            }) { Text(stringResource(R.string.fm_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = emptyList() }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.FolderOpen, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.fm_empty_dir), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onNew) {
                Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.fm_new_folder))
            }
        }
    }
}

@Composable
private fun GridFileItem(
    entry: ContainerFileEntry, type: ContainerFileType, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val visual = fileIconVisual(
        name = entry.name,
        isDir = entry.isDir,
        executable = type == ContainerFileType.EXECUTABLE,
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(visual.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(visual.icon, null, tint = visual.iconFg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(entry.name, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BreadcrumbRow(path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val segments = remember(path) {
        if (path == "/") listOf("/")
        else path.trim('/').split('/').filter { it.isNotEmpty() }
    }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var acc = ""
        segments.forEachIndexed { idx, seg ->
            acc = if (acc.isEmpty()) "/$seg" else "$acc/$seg"
            Text(
                text = if (seg == "/") "/" else seg,
                style = MaterialTheme.typography.labelLarge,
                color = if (idx == segments.lastIndex) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onNavigate(if (seg == "/") "/" else acc) }
            )
            if (idx != segments.lastIndex) {
                Icon(Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 从 ContentResolver 查询 URI 对应的显示文件名。 */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            val idx = c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()
}

/** 根据文件名扩展名猜测 MIME 类型，用于分享 Intent。 */
private fun guessMimeByName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "txt", "md", "log", "sh", "conf", "ini", "properties", "yaml", "yml" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "zip" -> "application/zip"
        "gz", "tgz" -> "application/gzip"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}
