@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")

package com.mini.me_core.feature.packager.presentation.component

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.viewer.code.CodeViewerScreen
import com.mini.me_core.core.viewer.code.CodeViewerViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 打包器独立文件查看页（本地文件直接读写）。
 *
 * 与工作区 [com.mini.me_core.feature.workspace.presentation.component.FileReaderScreen] 行为一致：
 * SoraCodeViewer 直接读取本地文件路径渲染，查看/编辑通过 [editable] 无感切换。
 * 保存时从 [CodeEditor] 读文本，直接写回原文件。编辑模式每 10 秒自动保存一次。
 */
@Composable
fun PackagerFileViewerScreen(
    filePath: String,
    onNavigateBack: () -> Unit,
) {
    val codeViewerVM: CodeViewerViewModel = viewModel()
    val scope = rememberCoroutineScope()

    val file = remember(filePath) { File(filePath) }
    val fileName = remember(filePath) { file.name.ifBlank { "file" } }
    val exists = remember(filePath) { file.exists() }

    var isEditing by remember { mutableStateOf(true) } // 默认进入编辑模式
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var editorInstance by remember { mutableStateOf<CodeEditor?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 拦截返回键：编辑模式有未保存修改时弹确认
    BackHandler {
        if (isEditing && hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }

    // 保存：从编辑器读文本，写回原文件
    suspend fun saveFile(): Boolean {
        val editor = editorInstance ?: return false
        val content = editor.text.toString()
        isSaving = true
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                file.writeText(content, Charsets.UTF_8)
            }.isSuccess
        }
        isSaving = false
        if (ok) hasUnsavedChanges = false
        return ok
    }

    fun enterEditMode() {
        isEditing = true
        hasUnsavedChanges = false
    }

    fun exitEditModeWithSave() {
        scope.launch {
            if (saveFile()) isEditing = false
        }
    }

    // 编辑模式每 10 秒自动保存
    LaunchedEffect(isEditing) {
        if (!isEditing) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            if (hasUnsavedChanges && isEditing) {
                saveFile()
            }
        }
    }

    fun discardAndExit() {
        hasUnsavedChanges = false
        isEditing = false
        showDiscardDialog = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = fileName,
                onNavigateBack = {
                    if (isEditing && hasUnsavedChanges) {
                        showDiscardDialog = true
                    } else {
                        onNavigateBack()
                    }
                },
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = "返回",
                actions = {
                    if (exists) {
                        if (isEditing) {
                            IconButton(
                                onClick = { exitEditModeWithSave() },
                                enabled = !isSaving,
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "保存",
                                        tint = if (hasUnsavedChanges)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { enterEditMode() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "编辑",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!exists) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "文件不存在",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        filePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CodeViewerScreen(
                    path = filePath,
                    viewModel = codeViewerVM,
                    editable = isEditing,
                    onEditorReady = { editor -> editorInstance = editor },
                    onModified = { if (isEditing) hasUnsavedChanges = true },
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改？") },
            text = { Text("文件有未保存的修改，确定要放弃吗？") },
            confirmButton = {
                TextButton(onClick = { discardAndExit() }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            },
        )
    }
}
