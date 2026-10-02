package com.mini.me_core.feature.workspace.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.viewer.code.CodeViewerScreen
import com.mini.me_core.core.viewer.code.CodeViewerViewModel
import com.mini.me_core.feature.workspace.presentation.FileReaderViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 独立文件阅读页：工作区文件统一走 SoraCodeViewer，支持查看/编辑无感切换。
 *
 * 入口：侧边栏「工作目录 → 当前工作台」点击某个文件。读取容器路径（`~/workspace/...`），
 * 本地/远程模式行为一致。内容写入临时文件后交由 SoraCodeViewer 渲染，
 * 与容器文件查看器共用同一套语法高亮、行号、搜索能力。
 *
 * 编辑模式：顶栏「编辑」按钮切换，编辑器实例不重建，仅切换 isEditable。
 * 保存时通过 CodeEditor 读取当前文本，经 DelegatingFileAccess 写回原文件路径。
 */
@Composable
fun FileReaderScreen(
    viewModel: FileReaderViewModel,
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val codeViewerVM: CodeViewerViewModel = viewModel()
    var tempPath by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 拦截系统返回键：编辑模式有未保存修改时提示确认
    BackHandler {
        if (isEditing && hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(filePath) {
        viewModel.load(filePath)
    }

    // 内容加载完成后写入临时文件，供 SoraCodeViewer 读取高亮
    LaunchedEffect(state.content, state.loading) {
        if (!state.loading && state.content.isNotBlank() && state.error == null) {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "workspace_viewer").apply { mkdirs() }
                    val safeName = state.fileName.ifBlank { "file.txt" }
                    val tmp = File(dir, "${System.currentTimeMillis()}_$safeName")
                    tmp.writeText(state.content, Charsets.UTF_8)
                    tmp.absolutePath
                }.getOrNull()
            }
            tempPath = path
        }
    }

    // 保存文件：从编辑器读取当前文本，写回原文件路径
    suspend fun saveFile(): Boolean {
        val editor = editorInstance ?: return false
        val content = editor.text.toString()
        isSaving = true
        val result = viewModel.save(state.path, content)
        isSaving = false
        return result.isSuccess.also { success ->
            if (success) {
                hasUnsavedChanges = false
                // 同步更新临时文件，保持编辑器内容与文件一致
                tempPath?.let { path ->
                    withContext(Dispatchers.IO) {
                        runCatching { File(path).writeText(content, Charsets.UTF_8) }
                    }
                }
            }
        }
    }

    // 切换到编辑模式
    fun enterEditMode() {
        isEditing = true
        hasUnsavedChanges = false
    }

    // 退出编辑模式（保存并退出）
    fun exitEditModeWithSave() {
        scope.launch {
            if (saveFile()) {
                isEditing = false
            }
        }
    }

    // 放弃修改并退出编辑模式
    fun discardAndExit() {
        hasUnsavedChanges = false
        isEditing = false
        showDiscardDialog = false
        // 重新加载原文件内容，覆盖临时文件
        tempPath?.let { path ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { File(path).writeText(state.content, Charsets.UTF_8) }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = state.fileName.ifBlank { stringResource(R.string.file_reader_title) },
                onNavigateBack = {
                    if (isEditing && hasUnsavedChanges) {
                        showDiscardDialog = true
                    } else {
                        onNavigateBack()
                    }
                },
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.file_reader_back),
                actions = {
                    if (tempPath != null && state.error == null && state.content.isNotBlank()) {
                        if (isEditing) {
                            // 编辑模式：保存并完成按钮（有未保存修改时高亮）
                            IconButton(
                                onClick = { exitEditModeWithSave() },
                                enabled = !isSaving
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "保存并完成",
                                        tint = if (hasUnsavedChanges)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            // 查看模式：编辑按钮
                            IconButton(onClick = { enterEditMode() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading || (state.content.isNotBlank() && tempPath == null) -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.file_reader_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = state.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                state.content.isBlank() && !state.loading -> {
                    Text(
                        text = stringResource(R.string.file_reader_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Spacing.lg)
                    )
                }
                tempPath != null -> {
                    CodeViewerScreen(
                        path = tempPath!!,
                        viewModel = codeViewerVM,
                        editable = isEditing,
                        onEditorReady = { editor -> editorInstance = editor },
                        onModified = { if (isEditing) hasUnsavedChanges = true },
                    )
                }
            }
        }
    }

    // 放弃修改确认对话框
    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改？") },
            text = { Text("文件有未保存的修改，确定要放弃吗？") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { discardAndExit() }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}
