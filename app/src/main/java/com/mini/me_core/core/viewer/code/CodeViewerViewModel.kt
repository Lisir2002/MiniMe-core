package com.mini.me_core.core.viewer.code

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.viewer.FileTypeRegistry
import com.mini.me_core.core.viewer.native.NativeCodeViewer
import com.mini.me_core.core.viewer.native.dto.HighlightSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CodeViewerUiState(
    val fileName: String = "",
    val lineCount: Int = 0,
    val lines: List<String> = emptyList(),
    val spans: List<HighlightSpan> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Int> = emptyList(),
    val showOutline: Boolean = false,
    val folds: Set<Int> = emptySet(),  // 被折叠的起始行
)

class CodeViewerViewModel(app: Application) : AndroidViewModel(app) {
    private var viewer: NativeCodeViewer? = null

    private val _ui = MutableStateFlow(CodeViewerUiState())
    val ui: StateFlow<CodeViewerUiState> = _ui.asStateFlow()

    fun open(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lang = FileTypeRegistry.languageFor(path)
                    val v = NativeCodeViewer.open(path, lang)
                    val all = v.readLines(0, Long.MAX_VALUE)
                    Triple(v, all, v.spans)
                }
            }
            result.onSuccess { (v, lines, spans) ->
                viewer = v
                _ui.value = _ui.value.copy(
                    fileName = path.substringAfterLast('/'),
                    lineCount = lines.size,
                    lines = lines,
                    spans = spans,
                    loading = false,
                )
            }.onFailure { e ->
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "打开失败")
            }
        }
    }

    fun onSearchQuery(q: String) {
        _ui.value = _ui.value.copy(searchQuery = q)
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(searchResults = emptyList())
            return
        }
        val matches = _ui.value.lines.mapIndexedNotNull { i, line ->
            if (line.contains(q, ignoreCase = true)) i else null
        }
        _ui.value = _ui.value.copy(searchResults = matches)
    }

    fun toggleOutline() {
        _ui.value = _ui.value.copy(showOutline = !_ui.value.showOutline)
    }

    fun toggleFold(startLine: Int) {
        val cur = _ui.value.folds
        _ui.value = _ui.value.copy(
            folds = if (startLine in cur) cur - startLine else cur + startLine,
        )
    }

    override fun onCleared() {
        super.onCleared()
        viewer?.close()
        viewer = null
    }
}
