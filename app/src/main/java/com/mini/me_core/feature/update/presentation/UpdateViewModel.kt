package com.mini.me_core.feature.update.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.R
import com.mini.me_core.feature.update.data.ApkInstaller
import com.mini.me_core.feature.update.data.DownloadProgress
import com.mini.me_core.feature.update.data.GitHubReleaseRepository
import com.mini.me_core.feature.update.domain.ReleaseInfo
import com.mini.me_core.feature.update.domain.UpdateAvailability
import com.mini.me_core.feature.update.domain.VersionComparator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 下载状态 UI 模型。 */
sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Downloading(val progress: DownloadProgress) : DownloadUiState
    data class Done(val filePath: String, val fileSize: Long) : DownloadUiState
    data class Failed(val message: String) : DownloadUiState
}

/** 版本更新页整体 UI 状态。 */
data class UpdateUiState(
    val checking: Boolean = false,
    val currentVersion: String = "",
    val latest: ReleaseInfo? = null,
    val lastCheckTime: Long = 0L,
    val history: List<ReleaseInfo> = emptyList(),
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val showPrerelease: Boolean = false,
    val autoCheck: Boolean = true,
    val download: DownloadUiState = DownloadUiState.Idle,
    /** 历史列表中展开完整日志的条目 tag。 */
    val expandedTag: String? = null,
    /** 最近下载完成的文件路径（用于历史条目各自展示安装按钮）。 */
    val downloadedFilePath: String? = null,
) {
    /** 最新版是否比当前版本更新。 */
    val hasUpdate: Boolean
        get() = latest != null && VersionComparator.isNewer(latest.versionName, currentVersion)

    /** 当前安装版本在历史列表中匹配的 tag（用于「当前版本」徽标）。 */
    fun isCurrent(release: ReleaseInfo): Boolean =
        VersionComparator.isUpToDate(release.versionName, currentVersion) &&
            VersionComparator.compare(release.versionName, currentVersion) == 0
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: GitHubReleaseRepository,
    @param:ApplicationContext private val app: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 一次性 Snackbar 事件。 */
    private val _snackbar = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null

    init {
        val current = runCatching {
            val pm = app.packageManager
            val info = pm.getPackageInfo(app.packageName, 0)
            info.versionName ?: "unknown"
        }.getOrDefault("unknown")
        _state.update { it.copy(currentVersion = current) }

        viewModelScope.launch {
            repository.autoCheckEnabled.collect { enabled ->
                _state.update { it.copy(autoCheck = enabled) }
            }
        }
        refresh(force = false)
    }

    /** 手动/自动检查：拉最新版 + 历史列表。 */
    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            runCatching {
                val latest = repository.fetchLatest(force = force)
                _state.update {
                    it.copy(latest = latest, lastCheckTime = repository.getLastCheckTime())
                }
            }.onFailure { e ->
                _snackbar.emit(e.message ?: app.getString(R.string.update_network_error))
            }
            loadHistory()
            _state.update { it.copy(checking = false) }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _state.update { it.copy(historyLoading = true, historyError = null) }
            runCatching {
                val list = repository.fetchHistory(includePrerelease = _state.value.showPrerelease)
                _state.update { it.copy(history = list, historyLoading = false) }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        historyLoading = false,
                        historyError = e.message ?: app.getString(R.string.update_network_error),
                    )
                }
            }
        }
    }

    fun togglePrerelease(show: Boolean) {
        _state.update { it.copy(showPrerelease = show) }
        loadHistory()
    }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoCheckEnabled(enabled)
            _state.update { it.copy(autoCheck = enabled) }
        }
    }

    fun toggleExpand(tag: String) {
        _state.update {
            it.copy(expandedTag = if (it.expandedTag == tag) null else tag)
        }
    }

    // ── 下载 ─────────────────────────────────────────────────────────

    fun download(release: ReleaseInfo) {
        if (_state.value.download is DownloadUiState.Downloading) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.update { it.copy(download = DownloadUiState.Idle) }
            runCatching {
                repository.downloadApk(release) { progress ->
                    _state.update { it.copy(download = DownloadUiState.Downloading(progress)) }
                }
            }.onSuccess { path ->
                val size = runCatching { java.io.File(path).length() }.getOrDefault(0L)
                _state.update {
                    it.copy(
                        download = DownloadUiState.Done(path, size),
                        downloadedFilePath = path,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        download = DownloadUiState.Failed(
                            e.message ?: app.getString(R.string.update_download_failed)
                        )
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        repository.cancelDownload()
        downloadJob?.cancel()
        _state.update { it.copy(download = DownloadUiState.Idle) }
    }

    fun install(filePath: String) {
        val ok = ApkInstaller.install(app, filePath)
        if (!ok) {
            viewModelScope.launch {
                _snackbar.emit(app.getString(R.string.update_install_failed))
            }
        }
    }

    fun openInBrowser(release: ReleaseInfo) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.onFailure {
            viewModelScope.launch { _snackbar.emit(app.getString(R.string.update_open_browser_failed)) }
        }
    }

    fun formatSize(bytes: Long): String = repository.formatFileSize(bytes)

    fun formatTime(epochMs: Long): String = repository.formatDateTime(epochMs)
}
