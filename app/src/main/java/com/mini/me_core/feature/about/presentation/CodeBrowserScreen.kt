package com.mini.me_core.feature.about.presentation

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.R
import com.mini.me_core.core.viewer.code.CodeViewerScreen
import com.mini.me_core.feature.about.data.RepoFileNode
import com.mini.me_core.feature.about.data.RepoType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeBrowserScreen(
    owner: String,
    repo: String,
    branch: String,
    onBack: () -> Unit,
    viewModel: CodeBrowserViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(owner, repo, branch) {
        viewModel.open(owner, repo, branch)
    }
    val context = LocalContext.current

    // 打开文件 -> 进入 Native CodeViewer
    ui.openingLocalPath?.let { localPath ->
        CodeViewerScreen(
            path = localPath,
            onBack = { viewModel.consumeOpenedFile() },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.code_browser_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "$owner/$repo · ${ui.branch}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.downloadZip() }) {
                        Icon(Icons.Rounded.FolderZip, contentDescription = stringResource(R.string.code_browser_download))
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // 下载进度条
            ui.zipDownload?.let { zip ->
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { zip.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.code_browser_downloading, zip.percent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 面包屑
            BreadcrumbRow(
                pathStack = ui.pathStack,
                rootLabel = stringResource(R.string.code_browser_root),
                onCrumbClick = { viewModel.breadcrumbClick(it) },
            )

            when {
                ui.loading && ui.root == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ui.error != null && ui.root == null -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.code_browser_error, ui.error ?: ""),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.retry() }) { Text(stringResource(R.string.update_retry)) }
                    }
                }
                else -> {
                    val children = viewModel.currentChildren()
                    if (children.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.code_browser_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(children, key = { it.path }) { node ->
                                RepoFileRow(
                                    node = node,
                                    sizeText = viewModel.formatSize(node.size),
                                    childCount = node.children.size,
                                    onClick = {
                                        if (node.type == RepoType.DIR) viewModel.enterDir(node)
                                        else viewModel.onFileClick(node)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 下载完成 -> 打开/分享/取消
    ui.zipReadyPath?.let { path ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissZipReady() },
            title = { Text(stringResource(R.string.code_browser_download_done)) },
            text = { Text(File(path).name) },
            confirmButton = {
                TextButton(onClick = {
                    openFile(context, path)
                    viewModel.dismissZipReady()
                }) { Text(stringResource(R.string.code_browser_open)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        shareFile(context, path)
                        viewModel.dismissZipReady()
                    }) { Text(stringResource(R.string.code_browser_share)) }
                    TextButton(onClick = { viewModel.dismissZipReady() }) { Text(stringResource(R.string.common_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun BreadcrumbRow(
    pathStack: List<String>,
    rootLabel: String,
    onCrumbClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pathStack.forEachIndexed { index, path ->
            val label = if (index == 0) rootLabel else path.substringAfterLast('/')
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onCrumbClick(index) },
            ) {
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (index == pathStack.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (index == pathStack.lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepoFileRow(
    node: RepoFileNode,
    sizeText: String,
    childCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = fileIconFor(node),
            contentDescription = null,
            tint = if (node.type == RepoType.DIR) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            val subtitle = if (node.type == RepoType.DIR) {
                stringResource(R.string.code_browser_items, childCount)
            } else sizeText
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fileIconFor(node: RepoFileNode): ImageVector {
    if (node.type == RepoType.DIR) return Icons.Rounded.Folder
    val ext = node.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "kt", "java", "kts" -> Icons.Rounded.Code
        "md", "markdown" -> Icons.Rounded.Article
        "json", "yaml", "yml", "toml", "xml" -> Icons.Rounded.DataObject
        "gradle" -> Icons.Rounded.Build
        "png", "jpg", "jpeg", "webp", "gif", "svg" -> Icons.Rounded.Image
        "zip", "gz", "tar" -> Icons.Rounded.FolderZip
        else -> Icons.Rounded.Description
    }
}

private fun openFile(context: android.content.Context, path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path),
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private fun shareFile(context: android.content.Context, path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
