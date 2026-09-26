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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.mini.me_core.R
import com.mini.me_core.core.ui.components.FileBrowserItem
import com.mini.me_core.core.ui.components.fileIconVisual
import com.mini.me_core.feature.about.data.RepoType
import java.io.File

/**
 * 源码浏览器内容区（无 Scaffold / 无顶栏）。
 *
 * 顶栏（返回 / 仓库标题 / 下载）由宿主（关于页）统一提供，遵循 UI 规范「顶栏唯一」。
 * 打开文件后由宿主切换为 [com.mini.me_core.core.viewer.code.CodeViewerScreen]。
 */
@Composable
fun CodeBrowserScreen(
    owner: String,
    repo: String,
    branch: String,
    viewModel: CodeBrowserViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(owner, repo, branch) {
        viewModel.open(owner, repo, branch)
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp, vertical = 4.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(children, key = { it.path }) { node ->
                            val visual = fileIconVisual(
                                name = node.name,
                                isDir = node.type == RepoType.DIR,
                            )
                            val subtitle = if (node.type == RepoType.DIR) {
                                stringResource(R.string.code_browser_items, node.children.size)
                            } else viewModel.formatSize(node.size)
                            FileBrowserItem(
                                name = node.name,
                                subtitle = subtitle,
                                icon = visual.icon,
                                iconBg = visual.iconBg,
                                iconFg = visual.iconFg,
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

    // ── 通用下载确认弹窗 ──
    if (ui.showDownloadConfirm) {
        com.mini.me_core.core.download.DownloadConfirmDialog(
            fileName = "${ui.repo}-${ui.branch}.zip",
            fileSizeText = null,
            sourceUrl = "https://github.com/${ui.owner}/${ui.repo}/archive/refs/heads/${ui.branch}.zip",
            onConfirm = { viewModel.confirmDownloadZip() },
            onDismiss = { viewModel.dismissDownloadConfirm() },
        )
    }

    // ── 通用下载进度弹窗 ──
    ui.zipDownload?.let { zip ->
        com.mini.me_core.core.download.DownloadProgressDialog(
            task = com.mini.me_core.core.download.DownloadTask(
                id = "repo-zip",
                title = "${ui.repo}-${ui.branch}.zip",
                url = "https://github.com/${ui.owner}/${ui.repo}/archive/refs/heads/${ui.branch}.zip",
                totalBytes = zip.totalBytes,
                downloadedBytes = zip.downloadedBytes,
                speedBytesPerSec = zip.speedBytesPerSec,
                status = com.mini.me_core.core.download.DownloadStatus.DOWNLOADING,
            ),
            onCancel = { viewModel.dismissDownloadResult() },
            onDismiss = { viewModel.dismissDownloadResult() },
        )
    }
    ui.zipReadyPath?.let { path ->
        com.mini.me_core.core.download.DownloadProgressDialog(
            task = com.mini.me_core.core.download.DownloadTask(
                id = "repo-zip",
                title = File(path).name,
                url = "",
                status = com.mini.me_core.core.download.DownloadStatus.COMPLETED,
                localPath = path,
            ),
            onCancel = {},
            onOpen = {
                openFile(context, path)
                viewModel.dismissDownloadResult()
            },
            onDismiss = { viewModel.dismissDownloadResult() },
        )
    }
    ui.zipDownloadError?.let { err ->
        com.mini.me_core.core.download.DownloadProgressDialog(
            task = com.mini.me_core.core.download.DownloadTask(
                id = "repo-zip",
                title = "${ui.repo}-${ui.branch}.zip",
                url = "",
                status = com.mini.me_core.core.download.DownloadStatus.FAILED,
                errorMessage = err,
            ),
            onCancel = {},
            onRetry = { viewModel.confirmDownloadZip() },
            onDismiss = { viewModel.dismissDownloadResult() },
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
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (index == pathStack.lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
