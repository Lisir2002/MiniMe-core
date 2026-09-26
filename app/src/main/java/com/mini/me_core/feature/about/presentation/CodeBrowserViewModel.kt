package com.mini.me_core.feature.about.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.about.data.GitHubCodeRepository
import com.mini.me_core.feature.about.data.RepoFileNode
import com.mini.me_core.feature.about.data.RepoType
import com.mini.me_core.feature.about.data.SourceDownloadProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 代码浏览器 UI 状态。 */
data class CodeBrowserUiState(
    val owner: String = "",
    val repo: String = "",
    val branch: String = "main",
    val loading: Boolean = true,
    val error: String? = null,
    val root: RepoFileNode? = null,
    /** 当前所在目录的路径栈（含根，空串表示根目录）。 */
    val pathStack: List<String> = listOf(""),
    /** 正在打开的本地临时文件路径（非空时触发 CodeViewer）。 */
    val openingLocalPath: String? = null,
    /** 下载 zip 进度；null 表示未在下载。 */
    val zipDownload: ZipDownloadUi? = null,
    /** 下载完成的 zip 路径；非空时弹出操作菜单。 */
    val zipReadyPath: String? = null,
)

data class ZipDownloadUi(
    val percent: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
)

@HiltViewModel
class CodeBrowserViewModel @Inject constructor(
    private val repo: GitHubCodeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CodeBrowserUiState())
    val ui: StateFlow<CodeBrowserUiState> = _ui.asStateFlow()

    fun open(owner: String, repoName: String, branch: String) {
        if (_ui.value.root != null &&
            _ui.value.owner == owner &&
            _ui.value.repo == repoName &&
            _ui.value.branch == branch
        ) {
            return
        }
        _ui.value = CodeBrowserUiState(owner = owner, repo = repoName, branch = branch)
        loadTree()
    }

    private fun loadTree() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            runCatching {
                repo.fetchRepoTree(_ui.value.owner, _ui.value.repo, _ui.value.branch)
            }.onSuccess { root ->
                _ui.value = _ui.value.copy(loading = false, root = root, pathStack = listOf(""))
            }.onFailure { e ->
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "unknown")
            }
        }
    }

    fun retry() = loadTree()

    /** 当前目录下的直接子节点列表。 */
    fun currentChildren(): List<RepoFileNode> {
        val root = _ui.value.root ?: return emptyList()
        val currentPath = _ui.value.pathStack.lastOrNull() ?: ""
        if (currentPath.isEmpty()) return root.children
        // 在树中定位 currentPath 节点
        var cursor = root
        for (seg in currentPath.split('/').filter { it.isNotEmpty() }) {
            cursor = cursor.children.firstOrNull { it.type == RepoType.DIR && it.name == seg }
                ?: return emptyList()
        }
        return cursor.children
    }

    fun enterDir(dir: RepoFileNode) {
        if (dir.type != RepoType.DIR) return
        _ui.value = _ui.value.copy(pathStack = _ui.value.pathStack + dir.path)
    }

    fun breadcrumbClick(index: Int) {
        if (index < 0 || index >= _ui.value.pathStack.size) return
        _ui.value = _ui.value.copy(pathStack = _ui.value.pathStack.subList(0, index + 1).toList())
    }

    fun onFileClick(node: RepoFileNode) {
        if (node.type != RepoType.FILE) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            runCatching {
                repo.downloadFileToTemp(_ui.value.owner, _ui.value.repo, node.path, _ui.value.branch)
            }.onSuccess { localPath ->
                _ui.value = _ui.value.copy(loading = false, openingLocalPath = localPath)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "unknown")
            }
        }
    }

    fun consumeOpenedFile() {
        _ui.value = _ui.value.copy(openingLocalPath = null)
    }

    fun downloadZip() {
        if (_ui.value.zipDownload != null) return
        viewModelScope.launch {
            runCatching {
                repo.downloadRepoZip(_ui.value.owner, _ui.value.repo, _ui.value.branch) { p: SourceDownloadProgress ->
                    _ui.value = _ui.value.copy(
                        zipDownload = ZipDownloadUi(p.percent, p.downloadedBytes, p.totalBytes, p.speedBytesPerSec)
                    )
                }
            }.onSuccess { path ->
                _ui.value = _ui.value.copy(zipDownload = null, zipReadyPath = path)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(zipDownload = null, error = e.message ?: "download failed")
            }
        }
    }

    fun dismissZipReady() {
        _ui.value = _ui.value.copy(zipReadyPath = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /** 格式化字节数（供 UI 复用）。 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
