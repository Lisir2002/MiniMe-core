package com.mini.me_core.core.viewer.code

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.viewer.FileTypeRegistry
import com.mini.me_core.core.viewer.native.NativeCodeViewer
import com.mini.me_core.core.viewer.native.dto.HighlightSpan
import com.mini.me_core.core.viewer.native.dto.SymbolNode
import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val searchMode: Boolean = false,
    val currentMatchIndex: Int = -1,  // 在 searchResults 中的下标，-1 表示无
    val showOutline: Boolean = false,
    val outline: List<SymbolNode> = emptyList(),
    val currentEncoding: Int = 0,  // 0=UTF8, 1=UTF16LE, 2=UTF16BE, 3=GB18030, 4=LATIN1
    val folds: Set<Int> = emptySet(),  // 被折叠的起始行
)

/** 编码代号 → 显示名映射（与 native setEncoding 对齐）。 */
val ENCODING_LABELS = listOf(
    0 to "UTF-8",
    1 to "UTF-16LE",
    2 to "UTF-16BE",
    3 to "GB18030",
    4 to "LATIN1",
)

class CodeViewerViewModel(app: Application) : AndroidViewModel(app) {
    private var viewer: NativeCodeViewer? = null

    private val _ui = MutableStateFlow(CodeViewerUiState())
    val ui: StateFlow<CodeViewerUiState> = _ui.asStateFlow()

    /** 滚动到指定行的一次性事件（行号从 0 开始）。 */
    private val _scrollToLine = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToLine: SharedFlow<Int> = _scrollToLine.asSharedFlow()

    fun open(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val lang = FileTypeRegistry.languageFor(path)
                    val v = NativeCodeViewer.open(path, lang)
                    // 下载的文件总是 UTF-8，强制设置编码避免 native 误判
                    v.setEncoding(0)
                    val all = v.readLines(0, Long.MAX_VALUE)
                    Quad(v, all, v.spans, v.outline)
                }
            }
            result.onSuccess { (v, lines, spans, outline) ->
                viewer = v
                // 重新执行搜索（行内容可能因编码变化而改变）
                val q = _ui.value.searchQuery
                val matches = if (q.isBlank()) emptyList()
                    else lines.mapIndexedNotNull { i, line ->
                        if (line.contains(q, ignoreCase = true)) i else null
                    }
                _ui.value = _ui.value.copy(
                    fileName = path.substringAfterLast('/'),
                    lineCount = lines.size,
                    lines = lines,
                    spans = spans,
                    outline = outline,
                    currentEncoding = 0,
                    loading = false,
                    searchResults = matches,
                    currentMatchIndex = if (matches.isEmpty()) -1 else 0,
                )
            }.onFailure { e ->
                FileLogger.e("CodeViewer", "open failed: $path", e)
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "打开失败")
            }
        }
    }

    fun onSearchQuery(q: String) {
        _ui.value = _ui.value.copy(searchQuery = q)
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(searchResults = emptyList(), currentMatchIndex = -1)
            return
        }
        val matches = _ui.value.lines.mapIndexedNotNull { i, line ->
            if (line.contains(q, ignoreCase = true)) i else null
        }
        _ui.value = _ui.value.copy(
            searchResults = matches,
            currentMatchIndex = if (matches.isEmpty()) -1 else 0,
        )
        // 自动跳到第一个匹配
        if (matches.isNotEmpty()) _scrollToLine.tryEmit(matches.first())
    }

    fun toggleSearchMode() {
        val newMode = !_ui.value.searchMode
        _ui.value = _ui.value.copy(searchMode = newMode)
        if (!newMode) {
            // 退出搜索时清空结果
            _ui.value = _ui.value.copy(searchQuery = "", searchResults = emptyList(), currentMatchIndex = -1)
        }
    }

    fun nextMatch() {
        val results = _ui.value.searchResults
        if (results.isEmpty()) return
        val next = (_ui.value.currentMatchIndex + 1).coerceAtMost(results.size - 1)
        _ui.value = _ui.value.copy(currentMatchIndex = next)
        _scrollToLine.tryEmit(results[next])
    }

    fun prevMatch() {
        val results = _ui.value.searchResults
        if (results.isEmpty()) return
        val prev = (_ui.value.currentMatchIndex - 1).coerceAtLeast(0)
        _ui.value = _ui.value.copy(currentMatchIndex = prev)
        _scrollToLine.tryEmit(results[prev])
    }

    fun toggleOutline() {
        _ui.value = _ui.value.copy(showOutline = !_ui.value.showOutline)
    }

    fun closeOutline() {
        _ui.value = _ui.value.copy(showOutline = false)
    }

    /** 大纲项点击：关闭抽屉并跳转。 */
    fun onOutlineItemClick(line: Int) {
        _ui.value = _ui.value.copy(showOutline = false)
        _scrollToLine.tryEmit(line)
    }

    fun setEncoding(encoding: Int) {
        val v = viewer ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { v.setEncoding(encoding) }
            if (ok) {
                val lines = withContext(Dispatchers.IO) { v.readLines(0, Long.MAX_VALUE) }
                // 重新搜索
                val q = _ui.value.searchQuery
                val matches = if (q.isBlank()) emptyList()
                    else lines.mapIndexedNotNull { i, line ->
                        if (line.contains(q, ignoreCase = true)) i else null
                    }
                _ui.value = _ui.value.copy(
                    lines = lines,
                    lineCount = lines.size,
                    spans = v.spans,
                    outline = v.outline,
                    currentEncoding = encoding,
                    searchResults = matches,
                    currentMatchIndex = if (matches.isEmpty()) -1 else 0,
                )
            }
        }
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

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
