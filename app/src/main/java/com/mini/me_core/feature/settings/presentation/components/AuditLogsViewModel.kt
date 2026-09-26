package com.mini.me_core.feature.settings.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.workspace.data.local.entity.RemoteAuditLogEntity
import com.mini.me_core.feature.workspace.domain.repository.RemoteAuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * 操作审计页面 ViewModel（MiniMe）。
 *
 * 管理统计卡片、分类 Tab、搜索、分页列表、导出与清理。
 */
@HiltViewModel
class AuditLogsViewModel @Inject constructor(
    private val repo: RemoteAuditLogRepository,
    private val exporter: AuditCsvExporter,
) : ViewModel() {

    /** 分类 Tab 键，顺序即 UI 顺序。 */
    val tabKeys: List<String> = listOf(
        RemoteAuditLogRepository.TAB_ALL,
        RemoteAuditLogRepository.TAB_CONNECT,
        RemoteAuditLogRepository.TAB_CREDENTIAL,
        RemoteAuditLogRepository.TAB_BACKUP,
        RemoteAuditLogRepository.TAB_SECURITY,
        RemoteAuditLogRepository.TAB_SKILL,
    )

    data class Stats(val total: Int = 0, val failed: Int = 0, val today: Int = 0)

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val stats: Stats = Stats(),
        val selectedTab: Int = 0,
        val searchActive: Boolean = false,
        val searchQuery: String = "",
        val logs: List<RemoteAuditLogEntity> = emptyList(),
        val endReached: Boolean = false,
        val exporting: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** 一次性事件（Toast）：导出成功 / 导出失败 / 清理完成。 */
    private val _events = MutableSharedFlow<AuditEvent>()
    val events: SharedFlow<AuditEvent> = _events.asSharedFlow()

    private val pageSize = 50
    private var page = 0

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching { loadStats(); loadList(reset = true) }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private suspend fun loadStats() {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        _ui.update {
            it.copy(
                stats = Stats(
                    total = repo.countAll(),
                    failed = repo.countFailed(),
                    today = repo.countSince(todayStart),
                )
            )
        }
    }

    fun selectTab(index: Int) {
        if (index == _ui.value.selectedTab && !_ui.value.searchActive) return
        _ui.update { it.copy(selectedTab = index, searchActive = false, searchQuery = "") }
        viewModelScope.launch {
            runCatching {
                loadList(reset = true)
                loadStats()
            }.onFailure { e -> _ui.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun toggleSearch(active: Boolean) {
        _ui.update { it.copy(searchActive = active, searchQuery = "") }
        if (!active) {
            viewModelScope.launch {
                runCatching { loadList(reset = true) }
            }
        }
    }

    fun onSearchQueryChange(q: String) {
        _ui.update { it.copy(searchQuery = q) }
        viewModelScope.launch {
            runCatching { loadList(reset = true) }
        }
    }

    fun loadMore() {
        if (_ui.value.endReached || _ui.value.searchActive || _ui.value.loading) return
        viewModelScope.launch {
            runCatching {
                page += 1
                val next = repo.pageDescByCategory(tabKeys[_ui.value.selectedTab], page, pageSize)
                _ui.update {
                    it.copy(
                        logs = it.logs + next,
                        endReached = next.size < pageSize,
                    )
                }
            }
        }
    }

    private suspend fun loadList(reset: Boolean) {
        if (reset) page = 0
        val state = _ui.value
        if (state.searchActive && state.searchQuery.isNotBlank()) {
            val results = searchLogs(state.searchQuery.trim())
            _ui.update {
                it.copy(loading = false, error = null, logs = results, endReached = true)
            }
            return
        }
        val key = tabKeys[state.selectedTab]
        val rows = repo.pageDescByCategory(key, page, pageSize)
        val merged = if (reset) rows else state.logs + rows
        _ui.update {
            it.copy(loading = false, error = null, logs = merged, endReached = rows.size < pageSize)
        }
    }

    /** 全局搜索：SQL 命中主机/连接名/消息，叠加中文动作名命中，按时间去重排序。 */
    private suspend fun searchLogs(q: String): List<RemoteAuditLogEntity> {
        val sqlHits = repo.search(q, 0, 200)
        val recent = repo.pageDesc(0, 500)
        val nameHits = recent.filter {
            AuditActionMapper.displayName(it.action).contains(q, ignoreCase = true)
        }
        return (sqlHits + nameHits)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
    }

    /** 清理保留期外的日志。 */
    fun purgeExpired() {
        viewModelScope.launch {
            runCatching {
                repo.enforceRetentionIfNeeded()
                loadList(reset = true)
                loadStats()
            }
        }
    }

    /** 清空全部审计日志（确认弹窗后调用）。 */
    fun clearAll() {
        viewModelScope.launch {
            runCatching {
                repo.clearAll()
                loadList(reset = true)
                loadStats()
                _events.emit(AuditEvent.Cleared)
            }.onFailure { _events.emit(AuditEvent.ExportFailed) }
        }
    }

    /** 导出全部日志为 CSV 到下载目录。 */
    fun exportCsv() {
        viewModelScope.launch {
            _ui.update { it.copy(exporting = true) }
            runCatching {
                val all = repo.pageDesc(0, 5000)
                when (exporter.export(all)) {
                    is AuditCsvExporter.Result.Success -> _events.emit(AuditEvent.ExportDone)
                    is AuditCsvExporter.Result.Failure -> _events.emit(AuditEvent.ExportFailed)
                }
            }.onFailure { _events.emit(AuditEvent.ExportFailed) }
            _ui.update { it.copy(exporting = false) }
        }
    }
}

/** 一次性 UI 事件。 */
sealed interface AuditEvent {
    data object ExportDone : AuditEvent
    data object ExportFailed : AuditEvent
    data object Cleared : AuditEvent
}
