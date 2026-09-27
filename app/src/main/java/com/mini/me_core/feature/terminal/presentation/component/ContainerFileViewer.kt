package com.mini.me_core.feature.terminal.presentation.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.viewer.code.CodeViewerScreen
import com.mini.me_core.core.viewer.code.CodeViewerViewModel
import com.mini.me_core.core.viewer.code.ENCODING_LABELS
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 容器文件查看器：从容器内读取文件内容写入临时文件，再用 NativeCodeViewer 高亮显示。
 *
 * 支持文本/代码文件的只读预览，带语法高亮、行号、搜索、大纲、编码切换。
 * 非文本文件或读取失败时显示错误提示。
 *
 * 顶栏设计遵循 UI 规范「顶栏唯一」：
 * - 普通模式：文件名 + 搜索/大纲/编码 actions
 * - 搜索模式：顶栏替换为搜索输入框 + 上下匹配导航 + 匹配计数
 *
 * @param entry 容器文件条目
 * @param access 容器文件访问层
 * @param onDismiss 返回回调
 */
@Composable
fun ContainerFileViewer(
    entry: ContainerFileEntry,
    access: ContainerFileAccess,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val codeViewerVM: CodeViewerViewModel = viewModel()
    val ui by codeViewerVM.ui.collectAsStateWithLifecycle()
    var tempPath by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    var showEncodingMenu by remember { mutableStateOf(false) }
    val encLabel = remember(ui.currentEncoding) {
        ENCODING_LABELS.firstOrNull { it.first == ui.currentEncoding }?.second ?: "UTF-8"
    }

    // 读取容器文件到临时目录，然后交给 CodeViewerViewModel 打开
    LaunchedEffect(entry.path) {
        val path = withContext(Dispatchers.IO) {
            runCatching {
                val content = access.readFileText(entry.path).getOrThrow()
                val dir = File(context.cacheDir, "container_viewer").apply { mkdirs() }
                val tmp = File(dir, "${System.currentTimeMillis()}_${entry.name}")
                tmp.writeText(content, Charsets.UTF_8)
                tmp.absolutePath
            }.getOrNull()
        }
        tempPath = path
        if (path != null) {
            codeViewerVM.open(path)
        }
    }

    // 返回键处理：搜索模式下退出搜索，大纲模式下关闭大纲，否则返回
    androidx.activity.compose.BackHandler(enabled = ui.searchMode || ui.showOutline) {
        when {
            ui.searchMode -> codeViewerVM.toggleSearchMode()
            ui.showOutline -> codeViewerVM.closeOutline()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (ui.searchMode) {
            // 搜索模式：顶栏替换为搜索输入框
            AppTopAppBar(
                title = "",
                onNavigateBack = { codeViewerVM.toggleSearchMode() },
                navigationIcon = Icons.Rounded.Close,
                navigationContentDescription = stringResource(R.string.common_close),
                titleContent = {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    OutlinedTextField(
                        value = ui.searchQuery,
                        onValueChange = { codeViewerVM.onSearchQuery(it) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .focusRequester(focusRequester),
                        placeholder = { Text("搜索…", style = MaterialTheme.typography.bodyMedium) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (ui.searchResults.isNotEmpty()) {
                                    Text(
                                        text = "${ui.currentMatchIndex + 1}/${ui.searchResults.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                                IconButton(onClick = { codeViewerVM.prevMatch() }) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "上一个")
                                }
                                IconButton(onClick = { codeViewerVM.nextMatch() }) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "下一个")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                },
            )
        } else {
            // 普通模式：文件名 + 搜索/大纲/编码 actions
            AppTopAppBar(
                title = entry.name,
                onNavigateBack = onDismiss,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.common_back),
                actions = {
                    IconButton(onClick = { codeViewerVM.toggleSearchMode() }) {
                        Icon(Icons.Rounded.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { codeViewerVM.toggleOutline() }) {
                        Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "大纲")
                    }
                    // 编码切换
                    Box {
                        IconButton(onClick = { showEncodingMenu = true }) {
                            Text(
                                text = encLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = showEncodingMenu,
                            onDismissRequest = { showEncodingMenu = false },
                        ) {
                            ENCODING_LABELS.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        codeViewerVM.setEncoding(code)
                                        showEncodingMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                tempPath != null -> {
                    CodeViewerScreen(path = tempPath!!, viewModel = codeViewerVM)
                }
                ui.error != null -> {
                    // 读取失败时显示错误
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.fm_preview_failed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    // 加载中
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载中…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
