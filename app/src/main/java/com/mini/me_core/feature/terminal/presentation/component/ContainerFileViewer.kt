package com.mini.me_core.feature.terminal.presentation.component

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.viewer.code.CodeViewerScreen
import com.mini.me_core.core.viewer.code.CodeViewerViewModel
import com.mini.me_core.feature.terminal.domain.ContainerFileAccess
import com.mini.me_core.feature.terminal.domain.ContainerFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 容器文件查看器：从容器内读取文件内容写入临时文件，再用 NativeCodeViewer 高亮显示。
 *
 * 支持文本/代码文件的只读预览，带语法高亮、行号、搜索、大纲。
 * 非文本文件或读取失败时显示错误提示。
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

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopAppBar(
            title = entry.name,
            onNavigateBack = onDismiss,
            navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
            navigationContentDescription = stringResource(R.string.common_back),
            titleContent = {
                Column {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            if (tempPath != null) {
                CodeViewerScreen(path = tempPath!!, viewModel = codeViewerVM)
            } else if (ui.error != null) {
                // 读取失败时显示错误
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        text = stringResource(R.string.fm_preview_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
