package com.mini.me_core.feature.settings.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.settings.data.repository.SettingsExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F5.2 设置导出/导入 ViewModel。
 *
 * 负责：分类选择状态、导出内容生成、导入解析、冲突计算、应用导入。
 * 文件选择/保存（SAF）由 Compose 层通过 ActivityResultContracts 完成。
 */
@HiltViewModel
class SettingsExportImportViewModel @Inject constructor(
    private val exportManager: SettingsExportManager,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsExportVM"
    }

    /** 导出页状态。 */
    data class ExportUiState(
        val selectedCategories: Set<String> = SettingsExportManager.CATEGORIES.map { it.id }.toSet(),
        val encrypt: Boolean = false,
        val password: String = "",
        val exportContent: String? = null,
        val isEncrypted: Boolean = false,
    )

    /** 导入页状态。 */
    sealed interface ImportUiState {
        data object Idle : ImportUiState
        data class Parsed(
            val itemCount: Int,
            val categoryCount: Int,
            val conflicts: List<SettingsExportManager.ConflictItem>,
            val keepKeys: Set<String> = emptySet(),
            val needsPassword: Boolean = false,
        ) : ImportUiState
        data class Result(
            val applied: Int,
            val skipped: Int,
            val failed: Int,
        ) : ImportUiState
        data class Error(val message: String) : ImportUiState
    }

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private var parsedSettings: SettingsExportManager.ParsedSettings? = null

    // ── 导出 ─────────────────────────────────────────────────────

    fun toggleCategory(id: String) {
        _exportState.value = _exportState.value.let { s ->
            val newSet = if (id in s.selectedCategories) {
                s.selectedCategories - id
            } else {
                s.selectedCategories + id
            }
            s.copy(selectedCategories = newSet)
        }
    }

    fun selectAllCategories() {
        _exportState.value = _exportState.value.copy(
            selectedCategories = SettingsExportManager.CATEGORIES.map { it.id }.toSet()
        )
    }

    fun selectNoCategories() {
        _exportState.value = _exportState.value.copy(selectedCategories = emptySet())
    }

    fun setEncrypt(enabled: Boolean) {
        _exportState.value = _exportState.value.copy(encrypt = enabled)
    }

    fun setPassword(pw: String) {
        _exportState.value = _exportState.value.copy(password = pw)
    }

    /** 生成导出内容（在协程中执行，完成后 exportContent 可用）。 */
    fun generateExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val state = _exportState.value
            val pw = if (state.encrypt) state.password else null
            val content = exportManager.export(state.selectedCategories, pw)
            _exportState.value = state.copy(exportContent = content, isEncrypted = pw != null)
            onReady(content)
        }
    }

    // ── 导入 ──────────────────────────────────────────────────────

    /** 解析导入文件内容。 */
    fun parseImport(raw: String, password: String = "") {
        viewModelScope.launch {
            runCatching {
                exportManager.parseImport(raw, password.ifBlank { null })
            }.onSuccess { parsed ->
                parsedSettings = parsed
                val conflicts = exportManager.computeConflicts(parsed)
                val itemCount = parsed.categories.values.sumOf { it.size }
                _importState.value = ImportUiState.Parsed(
                    itemCount = itemCount,
                    categoryCount = parsed.categories.size,
                    conflicts = conflicts,
                )
            }.onFailure { e ->
                Log.w(TAG, "parseImport failed: ${e.message}")
                _importState.value = when (e.message) {
                    "encrypted_required" -> ImportUiState.Parsed(0, 0, emptyList(), needsPassword = true)
                    else -> ImportUiState.Error(e.message ?: "invalid_format")
                }
            }
        }
    }

    /** 切换某个冲突项的保留/覆盖。 */
    fun toggleConflictKeep(key: String, keep: Boolean) {
        val current = _importState.value as? ImportUiState.Parsed ?: return
        val newKeep = if (keep) current.keepKeys + key else current.keepKeys - key
        _importState.value = current.copy(keepKeys = newKeep)
    }

    /** 全部保留。 */
    fun keepAll() {
        val current = _importState.value as? ImportUiState.Parsed ?: return
        _importState.value = current.copy(keepKeys = current.conflicts.map { it.key }.toSet())
    }

    /** 全部覆盖（清空保留集合）。 */
    fun overwriteAll() {
        val current = _importState.value as? ImportUiState.Parsed ?: return
        _importState.value = current.copy(keepKeys = emptySet())
    }

    /** 执行导入。 */
    fun applyImport() {
        val parsed = parsedSettings ?: return
        val current = _importState.value as? ImportUiState.Parsed ?: return
        viewModelScope.launch {
            val result = exportManager.applyImport(parsed, current.keepKeys)
            _importState.value = ImportUiState.Result(
                applied = result.applied,
                skipped = result.skipped,
                failed = result.failed,
            )
        }
    }

    fun resetImport() {
        _importState.value = ImportUiState.Idle
        parsedSettings = null
    }
}
