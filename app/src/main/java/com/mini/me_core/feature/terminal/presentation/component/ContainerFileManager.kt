package com.mini.me_core.feature.terminal.presentation.component

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
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ClipboardState(
    val paths: List<String>,
    val cut: Boolean,
)

/**
 * 容器内文件管理器主组件。
 *
 * 通过 [ContainerFileAccess] 在容器内执行 ls/stat/cp/mv/rm 等命令完成文件浏览与操作。
 * 文本文件走 [ContainerFileEditor]，图片走 [ContainerImageViewer]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerFileManager(
    access: ContainerFileAccess,
    modifier: Modifier = Modifier,
) {
    var currentPath by remember { mutableStateOf("/root") }
    var entries by remember { mutableStateOf<List<ContainerFileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    var sortDesc by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<ClipboardState?>(null) }
    var editingFile by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var viewingImage by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingNewDialog by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<ContainerFileEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<List<ContainerFileEntry>>(emptyList()) }
    var longPressed by remember { mutableStateOf<ContainerFileEntry?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun reload() {
        loading = true
        scope.launch {
            entries = access.listDir(currentPath, showHidden)
                .let { list ->
                    if (sortDesc) list.sortedByDescending { it.name.lowercase() }
                    else list.sortedBy { it.name.lowercase() }
                }
            loading = false
        }
    }

    LaunchedEffect(currentPath, showHidden, sortDesc) { reload() }

    fun navTo(dir: ContainerFileEntry) { currentPath = dir.path; selected = emptySet() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                // 面包屑
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showHidden = !showHidden }) {
                        Icon(Icons.Rounded.Visibility, contentDescription = "显示隐藏",
                            tint = if (showHidden) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sortDesc = !sortDesc }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "排序",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            // 多选操作栏 / 普通操作栏
            if (selected.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        clipboard = ClipboardState(selected.toList(), cut = false); selected = emptySet()
                    }) { Icon(Icons.Rounded.ContentCopy, "复制") }
                    IconButton(onClick = {
                        clipboard = ClipboardState(selected.toList(), cut = true); selected = emptySet()
                    }) { Icon(Icons.Rounded.ContentCut, "剪切") }
                    IconButton(onClick = { pendingDelete = entries.filter { it.path in selected } }) {
                        Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { selected = emptySet() }) { Text("取消") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { pendingNewDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text("新建")
                    }
                    if (clipboard != null) {
                        TextButton(onClick = {
                            val clip = clipboard ?: return@TextButton
                            scope.launch {
                                val r = if (clip.cut) access.move(clip.paths, currentPath)
                                else access.copy(clip.paths, currentPath)
                                r.onSuccess { snackbar.showSnackbar("已粘贴"); clipboard = null; reload() }
                                    .onFailure { snackbar.showSnackbar("粘贴失败: ${it.message}") }
                            }
                        }) { Text("粘贴 (${clipboard?.paths?.size ?: 0})") }
                    } else {
                        TextButton(onClick = { pendingNewDialog = true }) {
                            Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("导入")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(entries, key = { it.path }) { entry ->
                FileRow(
                    entry = entry,
                    selected = entry.path in selected,
                    onClick = {
                        if (selected.isNotEmpty()) {
                            selected = if (entry.path in selected) selected - entry.path else selected + entry.path
                        } else if (entry.isDir) {
                            navTo(entry)
                        } else {
                            scope.launch {
                                openFile(entry, access, onReadImage = { viewingImage = entry; imageBytes = it },
                                    onEdit = { editingFile = entry }, onTooLarge = {
                                        scope.launch { snackbar.showSnackbar("文件超过 1MB，建议用终端编辑") }
                                    })
                            }
                        }
                    },
                    onLongClick = {
                        if (selected.isEmpty()) longPressed = entry else selected = selected + entry.path
                    }
                )
            }
        }
    }

    // 新建对话框
    if (pendingNewDialog) {
        var name by remember { mutableStateOf("") }
        var isDir by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { pendingNewDialog = false },
            title = { Text(if (isDir) "新建文件夹" else "新建文件") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FilterChip(selected = isDir, onClick = { isDir = true }, label = { Text("文件夹") })
                        FilterChip(selected = !isDir, onClick = { isDir = false }, label = { Text("文件") })
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isBlank()) return@TextButton
                    val target = if (currentPath.endsWith("/")) currentPath + name else "$currentPath/$name"
                    scope.launch {
                        val r = if (isDir) access.mkdir(target) else access.createFile(target)
                        r.onSuccess { pendingNewDialog = false; reload() }
                            .onFailure { snackbar.showSnackbar("创建失败: ${it.message}") }
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { pendingNewDialog = false }) { Text("取消") } }
        )
    }

    // 长按菜单
    longPressed?.let { entry ->
        AlertDialog(
            onDismissRequest = { longPressed = null },
            title = { Text(entry.name) },
            text = { Text("选择操作") },
            confirmButton = {},
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingRename = entry; longPressed = null }) { Text("重命名") }
                    TextButton(onClick = { pendingDelete = listOf(entry); longPressed = null }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { longPressed = null; selected = setOf(entry.path) }) { Text("多选") }
                }
            }
        )
    }

    // 重命名
    pendingRename?.let { entry ->
        var newName by remember { mutableStateOf(entry.name) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank() || newName == entry.name) return@TextButton
                    val parent = entry.path.substringBeforeLast('/').ifEmpty { "/" }
                    val target = if (parent.endsWith("/")) parent + newName else "$parent/$newName"
                    scope.launch {
                        access.rename(entry.path, target)
                            .onSuccess { pendingRename = null; reload() }
                            .onFailure { snackbar.showSnackbar("重命名失败: ${it.message}") }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("取消") } }
        )
    }

    // 删除确认
    if (pendingDelete.isNotEmpty()) {
        val totalSize = pendingDelete.sumOf { it.sizeBytes }
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text("删除 ${pendingDelete.size} 项？") },
            text = { Text("将删除这些文件（共 ${formatSize(totalSize)}），不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        access.delete(pendingDelete.map { it.path })
                            .onSuccess { pendingDelete = emptyList(); selected = emptySet(); reload() }
                            .onFailure { snackbar.showSnackbar("删除失败: ${it.message}") }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = emptyList() }) { Text("取消") } }
        )
    }

    // 编辑器
    editingFile?.let { entry ->
        ContainerFileEditor(filePath = entry.path, access = access, onDismiss = { editingFile = null })
    }

    // 图片查看
    viewingImage?.let { entry ->
        imageBytes?.let { bytes ->
            ContainerImageViewer(title = entry.name, bytes = bytes, onDismiss = {
                viewingImage = null; imageBytes = null
            })
        }
    }
}

private suspend fun openFile(
    entry: ContainerFileEntry,
    access: ContainerFileAccess,
    onReadImage: suspend (ByteArray) -> Unit,
    onEdit: () -> Unit,
    onTooLarge: suspend () -> Unit,
) {
    val lower = entry.name.lowercase()
    val size = access.fileSize(entry.path)
    when {
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") -> {
            access.readFileBytes(entry.path).getOrNull()?.let { onReadImage(it) }
        }
        size > ContainerFileAccess.MAX_EDIT_BYTES -> onTooLarge()
        else -> onEdit()
    }
}

@Composable
private fun FileRow(
    entry: ContainerFileEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
            contentDescription = null,
            tint = if (entry.isDir) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSize(entry.sizeBytes) + " · " + formatTime(entry.modifiedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(epochSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochSec * 1000))
