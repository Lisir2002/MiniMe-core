package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.settings.data.repository.SettingsChangeHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F5.3 变更历史 ViewModel。
 *
 * 负责：暴露历史列表、单条/批量回滚、清空、回滚结果提示。
 */
@HiltViewModel
class ChangeHistoryViewModel @Inject constructor(
    private val history: SettingsChangeHistory,
) : ViewModel() {

    /** 单条回滚结果提示（一次性事件）。 */
    data class RollbackResult(
        val restoredCount: Int,
        val failed: Boolean,
    )

    private val _records = MutableStateFlow<List<SettingsChangeHistory.ChangeRecord>>(emptyList())
    val records: StateFlow<List<SettingsChangeHistory.ChangeRecord>> = _records.asStateFlow()

    /** 批量模式下选中待回滚的记录 id 集合。 */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _batchMode = MutableStateFlow(false)
    val batchMode: StateFlow<Boolean> = _batchMode.asStateFlow()

    private val _resultEvent = MutableStateFlow<RollbackResult?>(null)
    val resultEvent: StateFlow<RollbackResult?> = _resultEvent.asStateFlow()

    init {
        viewModelScope.launch {
            history.history.collect { list -> _records.value = list }
        }
    }

    fun consumeResultEvent() { _resultEvent.value = null }

    fun setBatchMode(enabled: Boolean) {
        _batchMode.value = enabled
        if (!enabled) _selected.value = emptySet()
    }

    fun toggleSelect(id: Long) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (id in this) remove(id) else add(id)
        }
    }

    /** 单条回滚。 */
    fun rollback(record: SettingsChangeHistory.ChangeRecord) {
        viewModelScope.launch {
            val ok = history.rollback(record)
            _resultEvent.value = RollbackResult(restoredCount = if (ok) 1 else 0, failed = !ok)
        }
    }

    /** 批量回滚：选中记录按时间从新到旧依次回滚。 */
    fun rollbackSelected() {
        viewModelScope.launch {
            val ids = _selected.value
            val targets = _records.value.filter { it.id in ids }
                .sortedByDescending { it.timestamp }
            var ok = 0
            for (r in targets) {
                if (history.rollback(r)) ok++
            }
            _selected.value = emptySet()
            _batchMode.value = false
            _resultEvent.value = RollbackResult(restoredCount = ok, failed = ok == 0 && targets.isNotEmpty())
        }
    }

    fun clearAll() {
        viewModelScope.launch { history.clear() }
    }
}
