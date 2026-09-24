package com.mini.logs.data

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.util.LogLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 日志页 ViewModel。管理日志加载、筛选、搜索、尾随、标记等状态。
 */
class LogsViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    private val repository = LogRepository(app, settings.logSource)
    private val safManager = SafDirectoryManager(app)

    /** 切换日志来源并重新加载。 */
    fun setLogSource(source: LogDirResolver.LogSource) {
        settings.logSource = source
        repository.setLogSource(source)
        refreshFiles()
    }

    /** 一次性状态消息（UI 收集后显示 Toast/Snackbar）。 */
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()
    fun consumeStatusMessage() { _statusMessage.value = null }

    /** SAF 目录选择完成后调用：校验路径、保存授权、刷新、反馈结果。 */
    fun onSafDirectorySelected(uri: android.net.Uri, grantedFlags: Int = 0) {
        // 校验是否选了正确的主应用日志目录
        if (!safManager.isExpectedLogDir(uri)) {
            viewModelScope.launch {
                _statusMessage.value = "未选择主应用日志目录，请选 Documents/MiniMe-core/logs/（当前目录已忽略）"
            }
            return
        }
        val persisted = safManager.saveTreeUri(uri, grantedFlags)
        viewModelScope.launch {
            _isLoading.value = true
            val refs = repository.listAllLogRefs(safManager)
            _logFileRefs.value = refs
            if (_selectedFiles.value.isEmpty() && refs.isNotEmpty()) {
                val today = refs.firstOrNull { it.fileName.contains(todayString()) } ?: refs.first()
                _selectedFiles.value = setOf(today.fileName)
            }
            loadLogsSuspend()
            _isLoading.value = false
            // 通知全局：日志源已变更，统计/崩溃等页面同步刷新
            AppDataSession.notifyDataSourceChanged()

            _statusMessage.value = when {
                refs.isNotEmpty() -> "已切换日志目录，找到 ${refs.size} 个日志文件" +
                    if (persisted) "" else "（持久化授权失败，重启后可能需重新选择）"
                else -> "该目录中未找到日志文件（log-*.txt），请确认选择的是日志目录"
            }
        }
    }

    /** 清除 SAF 目录。 */
    fun clearSafDirectory() {
        safManager.clearTreeUri()
        refreshFiles()
    }

    /** 是否配置了 SAF 目录。 */
    fun hasSafDirectory(): Boolean = safManager.getSavedTreeUri() != null

    /** 获取目录诊断结果（用于 UI 显示具体原因）。 */
    fun getDiagnostics(): List<DirScanStatus> = repository.diagnoseDirs()

    /** 获取每个文件的实际读取诊断（用于"有文件但无日志"时精确定位）。 */
    suspend fun getFileLoadDiagnostics(): List<LogRepository.FileLoadDiagnostic> =
        repository.diagnoseRefLoad(safManager)

    // ── 核心状态 ──
    private val _allEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val allEntries: StateFlow<List<LogEntry>> = _allEntries.asStateFlow()

    private val _filteredEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val filteredEntries: StateFlow<List<LogEntry>> = _filteredEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _logFileRefs = MutableStateFlow<List<LogFileRef>>(emptyList())
    val logFileRefs: StateFlow<List<LogFileRef>> = _logFileRefs.asStateFlow()

    /** 兼容 UI 的文件名列表。 */
    val logFileNames: StateFlow<List<String>> =
        _logFileRefs
            .map { refs -> refs.map { it.fileName } }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    // ── 筛选状态 ──
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    // ── 视图状态 ──
    private val _viewMode = MutableStateFlow(settings.viewMode)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _tailingState = MutableStateFlow(TailingState())
    val tailingState: StateFlow<TailingState> = _tailingState.asStateFlow()

    // ── 搜索状态 ──
    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchMatchIndex = MutableStateFlow(-1)
    val searchMatchIndex: StateFlow<Int> = _searchMatchIndex.asStateFlow()

    private val _searchMatchCount = MutableStateFlow(0)
    val searchMatchCount: StateFlow<Int> = _searchMatchCount.asStateFlow()

    // ── 展开行 ──
    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())
    val expandedKeys: StateFlow<Set<String>> = _expandedKeys.asStateFlow()

    // ── 等级数量徽章 ──
    private val _levelCounts = MutableStateFlow<Map<LogLevel, Int>>(emptyMap())
    val levelCounts: StateFlow<Map<LogLevel, Int>> = _levelCounts.asStateFlow()

    private val _tagCounts = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val tagCounts: StateFlow<List<Pair<String, Int>>> = _tagCounts.asStateFlow()

    // ── 跳转请求（UI 层收集后执行滚动）──
    private val _scrollRequest = MutableStateFlow<ScrollRequest?>(null)
    val scrollRequest: StateFlow<ScrollRequest?> = _scrollRequest.asStateFlow()

    // ── 上下文查看锚点（"查看前后上下文"时设置当前行 index，-1=无）──
    private val _contextAnchorIndex = MutableStateFlow(-1)
    val contextAnchorIndex: StateFlow<Int> = _contextAnchorIndex.asStateFlow()

    fun clearContextAnchor() {
        _contextAnchorIndex.value = -1
    }

    private var tailJob: Job? = null

    init {
        refreshFiles()
    }

    /** 刷新日志文件列表（直接路径 + SAF 合并）。 */
    fun refreshFiles() {
        viewModelScope.launch {
            val refs = repository.listAllLogRefs(safManager)
            _logFileRefs.value = refs
            if (_selectedFiles.value.isEmpty() && refs.isNotEmpty()) {
                // 默认选中今天的文件
                val today = refs.firstOrNull { it.fileName.contains(todayString()) } ?: refs.first()
                _selectedFiles.value = setOf(today.fileName)
            }
            loadLogs()
        }
    }

    /** 加载选中文件的日志（suspend 版本，供尾随等需要同步结果的场景调用）。 */
    private suspend fun loadLogsSuspend() {
        val refs = _logFileRefs.value.filter { it.fileName in _selectedFiles.value }
        val entries = repository.loadEntriesFromRefs(refs, settings.maxLoadLines)

        val highlights = settings.highlights
        val bookmarks = settings.bookmarks
        val enriched = entries.map { entry ->
            val key = "${entry.sourceFile}:${entry.lineNumber}"
            entry.copy(
                isHighlighted = highlights.containsKey(key),
                highlightColor = highlights[key] ?: -1,
                bookmarkNote = bookmarks[key],
            )
        }

        _allEntries.value = enriched.reversed()  // 倒序：最新日志在顶部
        _levelCounts.value = repository.countByLevel(enriched)
        _tagCounts.value = repository.countByTag(enriched)
        applyFilter()
    }

    /** 加载选中文件的日志。 */
    fun loadLogs() {
        viewModelScope.launch {
            loadLogsSuspend()
            _isLoading.value = false
        }
    }

    /** 应用筛选，更新 filteredEntries。 */
    private fun applyFilter() {
        val filter = _filterState.value
        val all = _allEntries.value

        // 先筛选主行，再保留其堆栈行
        val result = mutableListOf<LogEntry>()
        var lastMainPassed = false
        for (entry in all) {
            if (entry.isStackTraceLine) {
                if (lastMainPassed) result.add(entry)
            } else {
                val passed = filter.matches(entry)
                if (passed) {
                    result.add(entry)
                    lastMainPassed = true
                } else {
                    lastMainPassed = false
                }
            }
        }

        // 处理等级折叠：被折叠的等级只保留第一条作为计数占位（UI 层处理展示）
        _filteredEntries.value = result
        updateSearchMatches()
    }

    /** 更新搜索匹配计数。 */
    private fun updateSearchMatches() {
        val query = _searchQuery.value
        if (query.isEmpty()) {
            _searchMatchCount.value = 0
            _searchMatchIndex.value = -1
            return
        }
        val count = _filteredEntries.value.count {
            it.rawLine.contains(query, ignoreCase = true)
        }
        _searchMatchCount.value = count
        if (_searchMatchIndex.value >= count) {
            _searchMatchIndex.value = if (count > 0) 0 else -1
        }
    }

    // ── 等级筛选 ──

    /** 单击等级：只显示该等级及以上。 */
    fun selectLevelAndAbove(level: LogLevel) {
        val levels = LogLevel.entries.filter { it.ordinal >= level.ordinal && it != LogLevel.NONE }.toSet()
        _filterState.value = _filterState.value.copy(selectedLevels = levels)
        applyFilter()
    }

    /** 切换等级选中（多选模式）。 */
    fun toggleLevel(level: LogLevel) {
        val current = _filterState.value.selectedLevels.toMutableSet()
        if (level in current) current.remove(level) else current.add(level)
        _filterState.value = _filterState.value.copy(selectedLevels = current)
        applyFilter()
    }

    /** 清除等级筛选（显示全部）。 */
    fun clearLevelFilter() {
        _filterState.value = _filterState.value.copy(selectedLevels = emptySet())
        applyFilter()
    }

    /** 折叠/展开某等级。 */
    fun toggleLevelCollapse(level: LogLevel) {
        val current = _filterState.value.levelCollapsed.toMutableSet()
        if (level in current) current.remove(level) else current.add(level)
        _filterState.value = _filterState.value.copy(levelCollapsed = current)
        applyFilter()
    }

    // ── Tag 筛选 ──

    fun toggleTag(tag: String) {
        val current = _filterState.value.selectedTags.toMutableSet()
        if (tag in current) current.remove(tag) else current.add(tag)
        _filterState.value = _filterState.value.copy(selectedTags = current)
        applyFilter()
    }

    fun toggleExcludeTag(tag: String) {
        val current = _filterState.value.excludedTags.toMutableSet()
        if (tag in current) current.remove(tag) else current.add(tag)
        _filterState.value = _filterState.value.copy(excludedTags = current)
        applyFilter()
    }

    fun clearTagFilter() {
        _filterState.value = _filterState.value.copy(selectedTags = emptySet(), excludedTags = emptySet())
        applyFilter()
    }

    // ── 文件选择 ──

    fun selectFile(name: String, selected: Boolean) {
        val current = _selectedFiles.value.toMutableSet()
        if (selected) current.add(name) else current.remove(name)
        _selectedFiles.value = current
        settings.lastSelectedFiles = current
        loadLogs()
    }

    fun selectAllFiles() {
        _selectedFiles.value = _logFileRefs.value.map { it.fileName }.toSet()
        loadLogs()
    }

    fun selectQuickRange(days: Int) {
        val all = _logFileRefs.value
        val selected = if (days <= 0) {
            all.take(1).map { it.fileName }.toSet()
        } else {
            all.take(days).map { it.fileName }.toSet()
        }
        _selectedFiles.value = selected
        loadLogs()
    }

    // ── 搜索 ──

    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
        if (!active) {
            _searchQuery.value = ""
            _filterState.value = _filterState.value.copy(searchQuery = "")
            applyFilter()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _filterState.value = _filterState.value.copy(searchQuery = query)
        applyFilter()
    }

    fun setRegexSearch(enabled: Boolean) {
        _filterState.value = _filterState.value.copy(isRegexSearch = enabled)
        applyFilter()
    }

    /** 跳转到下一个搜索匹配。 */
    fun nextSearchMatch() {
        val count = _searchMatchCount.value
        if (count <= 0) return
        val next = (_searchMatchIndex.value + 1) % count
        _searchMatchIndex.value = next
        scrollToSearchMatch(next)
    }

    /** 跳转到上一个搜索匹配。 */
    fun prevSearchMatch() {
        val count = _searchMatchCount.value
        if (count <= 0) return
        val prev = if (_searchMatchIndex.value <= 0) count - 1 else _searchMatchIndex.value - 1
        _searchMatchIndex.value = prev
        scrollToSearchMatch(prev)
    }

    private fun scrollToSearchMatch(index: Int) {
        val query = _searchQuery.value
        val matches = _filteredEntries.value.withIndex()
            .filter { it.value.rawLine.contains(query, ignoreCase = true) }
        if (index in matches.indices) {
            _scrollRequest.value = ScrollRequest.ToIndex(matches[index].index)
        }
    }

    // ── 导航 ──

    fun jumpToLine(lineNumber: Int) {
        val index = _filteredEntries.value.indexOfFirst { it.lineNumber == lineNumber && !it.isStackTraceLine }
        if (index >= 0) _scrollRequest.value = ScrollRequest.ToIndex(index)
    }

    fun jumpToTime(hour: Int, minute: Int) {
        val target = String.format("%02d:%02d", hour, minute)
        val index = _filteredEntries.value.indexOfFirst {
            it.time.startsWith(target) && !it.isStackTraceLine
        }
        if (index >= 0) _scrollRequest.value = ScrollRequest.ToIndex(index)
    }

    fun nextError() {
        val currentIdx = (_scrollRequest.value as? ScrollRequest.ToIndex)?.index ?: -1
        val index = _filteredEntries.value.withIndex()
            .drop(currentIdx + 1)
            .firstOrNull { it.value.level == LogLevel.ERROR || it.value.level == LogLevel.FATAL }
            ?.index
        if (index != null) _scrollRequest.value = ScrollRequest.ToIndex(index)
    }

    fun scrollToTop() {
        _scrollRequest.value = ScrollRequest.ToTop
    }

    fun scrollToBottom() {
        _scrollRequest.value = ScrollRequest.ToBottom
    }

    fun consumeScrollRequest() {
        _scrollRequest.value = null
    }

    // ── 行展开 ──

    fun toggleExpand(entry: LogEntry) {
        val key = "${entry.sourceFile}:${entry.lineNumber}"
        val current = _expandedKeys.value.toMutableSet()
        if (key in current) current.remove(key) else current.add(key)
        _expandedKeys.value = current
    }

    fun isExpanded(entry: LogEntry): Boolean {
        val key = "${entry.sourceFile}:${entry.lineNumber}"
        return key in _expandedKeys.value || entry.isExpanded
    }

    // ── 高亮与书签 ──

    fun setHighlight(entry: LogEntry, colorIndex: Int) {
        val key = "${entry.sourceFile}:${entry.lineNumber}"
        val highlights = settings.highlights.toMutableMap()
        if (colorIndex >= 0) {
            highlights[key] = colorIndex
        } else {
            highlights.remove(key)
        }
        settings.highlights = highlights
        // 更新内存中的条目
        _allEntries.value = _allEntries.value.map {
            if ("${it.sourceFile}:${it.lineNumber}" == key) {
                it.copy(isHighlighted = colorIndex >= 0, highlightColor = colorIndex)
            } else it
        }
        applyFilter()
    }

    fun setBookmark(entry: LogEntry, note: String?) {
        val key = "${entry.sourceFile}:${entry.lineNumber}"
        val bookmarks = settings.bookmarks.toMutableMap()
        if (note != null) {
            bookmarks[key] = note
        } else {
            bookmarks.remove(key)
        }
        settings.bookmarks = bookmarks
        _allEntries.value = _allEntries.value.map {
            if ("${it.sourceFile}:${it.lineNumber}" == key) {
                it.copy(bookmarkNote = note)
            } else it
        }
        applyFilter()
    }

    // ── 长按菜单统一处理入口 ──

    /**
     * 统一处理 LogActionSheet 发起的所有操作。
     *
     * 复制 / 分享需要 Application Context（从 ViewModel 内启动，加 NEW_TASK flag）。
     * 过滤 / 搜索直接复用现有方法；高亮 / 书签复用 setHighlight / setBookmark。
     * Bookmark(note=null) 由 UI 层拦截（弹输入框），不应走到这里。
     */
    fun onLogAction(entry: LogEntry, action: LogAction) {
        val app = getApplication<Application>()
        when (action) {
            LogAction.CopyRaw -> copyToClipboard(app, entry.rawLine, "已复制原始行")

            LogAction.CopyMessage -> copyToClipboard(app, entry.message, "已复制消息")

            is LogAction.CopyAs -> {
                val (text, toast) = when (action.field) {
                    LogAction.CopyAs.CopyField.TIMESTAMP -> entry.time to "已复制时间"
                    LogAction.CopyAs.CopyField.TAG -> entry.tag to "已复制 Tag"
                    LogAction.CopyAs.CopyField.LEVEL ->
                        (entry.level?.name ?: entry.levelLetter) to "已复制等级"
                    LogAction.CopyAs.CopyField.STACKTRACE -> entry.message to "已复制堆栈"
                }
                copyToClipboard(app, text, toast)
            }

            is LogAction.FilterTagOnly -> {
                val current = _filterState.value.selectedTags.toMutableSet()
                current.add(action.tag)
                _filterState.value = _filterState.value.copy(selectedTags = current)
                applyFilter()
            }

            is LogAction.FilterTagHide -> {
                val current = _filterState.value.excludedTags.toMutableSet()
                current.add(action.tag)
                _filterState.value = _filterState.value.copy(excludedTags = current)
                applyFilter()
            }

            is LogAction.FilterLevelOnly -> selectLevelAndAbove(action.level)

            is LogAction.FilterSimilar -> {
                setSearchActive(true)
                updateSearchQuery(action.message.take(30))
            }

            is LogAction.SearchWith -> {
                setSearchActive(true)
                updateSearchQuery(action.query)
            }

            is LogAction.Highlight -> setHighlight(entry, action.colorIndex)

            is LogAction.Bookmark -> {
                // note != null 表示来自书签输入框保存；note==null 由 UI 层拦截
                if (action.note != null) setBookmark(entry, action.note)
            }

            LogAction.ClearMarks -> {
                setHighlight(entry, -1)
                setBookmark(entry, null)
            }

            LogAction.ShareLine -> shareText(app, entry.rawLine)

            LogAction.ShareContext -> {
                val idx = _filteredEntries.value.indexOfFirst {
                    it.sourceFile == entry.sourceFile && it.lineNumber == entry.lineNumber
                }
                if (idx >= 0) {
                    val from = (idx - 5).coerceAtLeast(0)
                    val to = (idx + 6).coerceAtMost(_filteredEntries.value.size)
                    val text = _filteredEntries.value.subList(from, to)
                        .joinToString("\n") { it.rawLine }
                    shareText(app, text)
                } else {
                    Toast.makeText(app, "未在当前列表中找到该行", Toast.LENGTH_SHORT).show()
                }
            }

            LogAction.ViewContext -> {
                val idx = _filteredEntries.value.indexOfFirst {
                    it.sourceFile == entry.sourceFile && it.lineNumber == entry.lineNumber
                }
                if (idx >= 0) {
                    _contextAnchorIndex.value = idx
                    _scrollRequest.value = ScrollRequest.ToIndex(idx)
                }
            }

            is LogAction.JumpToTime -> {
                val parts = action.time.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (h != null && m != null) jumpToTime(h, m)
                }
            }
        }
    }

    private fun copyToClipboard(context: Context, text: String, toast: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("log", text))
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "分享日志"))
    }

    // ── 实时尾随 ──

    fun toggleTailing(enabled: Boolean) {
        _tailingState.value = _tailingState.value.copy(isEnabled = enabled, isPaused = false)
        if (enabled) {
            startTailing()
        } else {
            stopTailing()
        }
    }

    fun pauseTailing() {
        _tailingState.value = _tailingState.value.copy(isPaused = true)
    }

    fun resumeTailing() {
        _tailingState.value = _tailingState.value.copy(isPaused = false, newLinesCount = 0)
        viewModelScope.launch {
            loadLogsSuspend()
            _scrollRequest.value = ScrollRequest.ToTop
        }
    }

    private fun startTailing() {
        tailJob?.cancel()
        tailJob = viewModelScope.launch {
            var lastCount = _allEntries.value.size
            var lastTick = System.currentTimeMillis()
            var linesInWindow = 0
            while (true) {
                delay(1000)
                val before = _allEntries.value.size
                // 同步加载，确保 after 反映真实加载结果
                loadLogsSuspend()
                val after = _allEntries.value.size
                val newLines = (after - before).coerceAtLeast(0)
                linesInWindow += newLines

                val now = System.currentTimeMillis()
                if (now - lastTick >= 3000) {
                    val lps = linesInWindow * 1000 / (now - lastTick).coerceAtLeast(1)
                    _tailingState.value = _tailingState.value.copy(linesPerSecond = lps.toInt())
                    linesInWindow = 0
                    lastTick = now
                }

                if (!_tailingState.value.isPaused && newLines > 0) {
                    // 倒序显示：最新在顶部，新日志到达时滚动到顶部
                    _scrollRequest.value = ScrollRequest.ToTop
                } else if (_tailingState.value.isPaused && newLines > 0) {
                    _tailingState.value = _tailingState.value.copy(
                        newLinesCount = _tailingState.value.newLinesCount + newLines
                    )
                }
                lastCount = after
            }
        }
    }

    private fun stopTailing() {
        tailJob?.cancel()
        tailJob = null
    }

    // ── 视图模式 ──

    fun toggleViewMode() {
        val next = if (_viewMode.value == ViewMode.COMFORTABLE) ViewMode.COMPACT else ViewMode.COMFORTABLE
        _viewMode.value = next
        settings.viewMode = next
    }

    // ── 重置筛选 ──

    fun resetFilter() {
        _filterState.value = FilterState()
        applyFilter()
    }

    private fun todayString(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    override fun onCleared() {
        stopTailing()
        super.onCleared()
    }
}

/** 滚动请求。 */
sealed class ScrollRequest {
    data object ToTop : ScrollRequest()
    data object ToBottom : ScrollRequest()
    data class ToIndex(val index: Int) : ScrollRequest()
}
