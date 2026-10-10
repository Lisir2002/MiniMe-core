package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.core.util.LogLineParser
import com.mini.me_core.feature.settings.data.repository.LogFilterSettingsRepository
import com.mini.me_core.feature.settings.data.repository.LogSettingsRepository
import com.mini.me_core.feature.settings.presentation.DateRangeMode
import com.mini.me_core.feature.settings.presentation.LogViewerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  行为保持搬迁：运行日志查看器域（日志读取 / 显示与日期筛选 / 实时尾随 / 等级与搜索状态）。
 * FetchState/ConnectionTestState/DateRangeMode/LogViewerUiState 类型本身保留在 presentation 包，
 * 此处只搬迁其状态与逻辑；协程仍由同一 viewModelScope 驱动。
 */
@Suppress("TooManyFunctions") // 行为保持搬迁：日志域公开/内部方法全部原样保留
internal class LogViewerSection(
    private val scope: CoroutineScope,
    private val logSettingsRepository: LogSettingsRepository,
    private val logFilterSettingsRepository: LogFilterSettingsRepository,
) {
    // ── 缓存：所有已读入的行（供局部过滤使用，避免重复读文件） ──
    private var _cachedRawLines: List<String> = emptyList()

    // ── 筛选防抖触发器：每次筛选变化递增，外层 debounce 300ms 后消费 ──
    private val _filterTrigger = MutableStateFlow(0L)

    // ── 实时尾随文件观察器 ──
    private var _liveTailFileObserver: android.os.FileObserver? = null

    private val _logLevel = MutableStateFlow(LogLevel.VERBOSE)
    val logLevel: StateFlow<LogLevel> = _logLevel.asStateFlow()

    private val _logViewerState = MutableStateFlow(LogViewerUiState())
    val logViewerState: StateFlow<LogViewerUiState> = _logViewerState.asStateFlow()

    init {
        scope.launch {
            logSettingsRepository.levelFlow.collectLatest {
                _logLevel.value = it
            }
        }

        // 恢复持久化的日志筛选偏好（等级 / Tag；日期范围默认「今天」，不持久化旧的列表/范围开关）
        scope.launch {
            val levels = logFilterSettingsRepository.readSelectedLevels()
            val tags = logFilterSettingsRepository.readSelectedTags()
            _logViewerState.update {
                it.copy(
                    selectedLevels = levels,
                    selectedTags = tags
                )
            }
        }

        // 记录等级（写入阈值）变化时同步进日志查看器 UiState，供顶部「记录等级」行展示
        scope.launch {
            _logLevel.collectLatest { level ->
                _logViewerState.update { it.copy(recordLevel = level) }
            }
        }

        // ── 筛选防抖消费者：筛选变化 300ms 后重新应用过滤 ──
        scope.launch {
            _filterTrigger.debounce(300).collectLatest { _ ->
                applyLocalFilters()
            }
        }
    }

    /** ViewModel.onCleared 回调：停止实时尾随。 */
    fun onCleared() {
        stopLiveTail()
    }

    fun setLogLevel(level: LogLevel) {
        scope.launch {
            logSettingsRepository.setLevel(level)
        }
    }

    fun refreshLogs(filterServerName: String? = _logViewerState.value.filterServerName) {
        loadLogs(
            filterServerName = filterServerName?.takeIf { it.isNotBlank() },
            preferredFileName = _logViewerState.value.selectedFileName
        )
    }

    fun selectLogFile(fileName: String) {
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = fileName
        )
    }

    // ── 筛选控制方法 ──

    fun toggleFilterPanel() {
        // 只打开，不关闭；关闭由 closeFilterPanel() 负责
        _logViewerState.update { it.copy(filterPanelExpanded = true) }
    }

    fun closeFilterPanel() {
        _logViewerState.update { it.copy(filterPanelExpanded = false) }
    }

    fun setSelectedDates(dates: Set<String>) {
        _logViewerState.update { it.copy(selectedDates = dates) }
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = _logViewerState.value.selectedFileName
        )
    }

    // ── 日期范围：快捷范围 / 单文件 / 自定义（设计文档 §7）──

    /**
     * 设置快捷日期范围（今天/昨天/近3天/近7天/全部）。
     * 翻译成 [selectedDates] / 自定义范围后复用现有 [filterFilesByDate] 逻辑重新加载。
     */
    fun setDateRangeMode(mode: DateRangeMode) {
        val (dates, rangeStart, rangeEnd) = resolveDateScope(mode, null)
        _logViewerState.update {
            it.copy(
                dateRangeMode = mode,
                selectedDates = dates,
                customDateStart = rangeStart,
                customDateEnd = rangeEnd,
                selectedFileName = null
            )
        }
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = null
        )
    }

    /** 选择单个日志文件：作用域收窄到该文件对应的日期。 */
    fun selectSingleFile(fileName: String) {
        // L1：用统一解析器取逻辑日期，兼容滚动件（log-<date>.N.txt 也解析为 <date>）。
        val fileDate = com.mini.me_core.core.util.LogFiles.parseAppLogDate(fileName)
            ?: fileName.removePrefix("log-").removeSuffix(".txt")
        _logViewerState.update {
            it.copy(
                dateRangeMode = DateRangeMode.SINGLE_FILE,
                selectedDates = setOf(fileDate),
                customDateStart = null,
                customDateEnd = null,
                selectedFileName = fileName
            )
        }
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = fileName
        )
    }

    /** 设置自定义日期范围（yyyy-MM-dd，含端点）；起止都为空时等价于「全部」。 */
    fun setCustomDateRange(start: String?, end: String?) {
        _logViewerState.update {
            it.copy(
                dateRangeMode = if (start == null && end == null) DateRangeMode.ALL else DateRangeMode.CUSTOM,
                selectedDates = emptySet(),
                customDateStart = start,
                customDateEnd = end,
                selectedFileName = null
            )
        }
        loadLogs(
            filterServerName = _logViewerState.value.filterServerName,
            preferredFileName = null
        )
    }

    /** 设置最低记录等级（写入阈值），持久化后由 FileLogger 实时生效。 */
    fun setRecordLevel(level: LogLevel) {
        scope.launch {
            logSettingsRepository.setLevel(level)
        }
    }

    // ── 显示筛选快捷操作（运行日志查看器重构）──

    /** 显示筛选「全部」：清空等级多选，展示所有等级。 */
    fun clearDisplayLevels() {
        _logViewerState.update { it.copy(selectedLevels = emptySet(), collapsedLevels = emptySet()) }
        scope.launch { logFilterSettingsRepository.saveSelectedLevels(emptySet()) }
        triggerDebouncedFilter()
    }

    /**
     * 快速筛选栏点击某等级：
     * - 若该等级已是唯一选中 → 折叠/展开该等级整组行；
     * - 否则 → 仅显示该等级（单选快速过滤）。
     */
    fun quickFilterLevel(level: LogLevel) {
        val current = _logViewerState.value
        if (current.selectedLevels == setOf(level)) {
            toggleLevelCollapse(level)
        } else {
            _logViewerState.update {
                it.copy(selectedLevels = setOf(level), collapsedLevels = emptySet())
            }
            scope.launch { logFilterSettingsRepository.saveSelectedLevels(setOf(level)) }
            triggerDebouncedFilter()
        }
    }

    /** 折叠/展开某等级整组行（设计文档 §5.3/§10.6）。 */
    fun toggleLevelCollapse(level: LogLevel) {
        _logViewerState.update {
            val newCollapsed = if (level in it.collapsedLevels) it.collapsedLevels - level else it.collapsedLevels + level
            it.copy(collapsedLevels = newCollapsed)
        }
    }

    /** 顶栏搜索展开/收起；收起时清空关键词恢复完整日志。 */
    fun setSearchExpanded(expanded: Boolean) {
        _logViewerState.update { it.copy(searchExpanded = expanded) }
        if (!expanded) {
            _logViewerState.update {
                it.copy(searchQuery = "", currentMatchIndex = 0, totalMatches = 0)
            }
            triggerDebouncedFilter()
        }
    }

    /** 搜索结果上一处/下一处跳转（Stage 4 完善滚动定位）。 */
    fun prevMatch() {
        val total = _logViewerState.value.totalMatches
        if (total <= 0) return
        _logViewerState.update { it.copy(currentMatchIndex = (it.currentMatchIndex - 1 + total) % total) }
    }

    fun nextMatch() {
        val total = _logViewerState.value.totalMatches
        if (total <= 0) return
        _logViewerState.update { it.copy(currentMatchIndex = (it.currentMatchIndex + 1) % total) }
    }

    /** 把快捷日期模式翻译成 [filterFilesByDate] 能理解的 (选中日期集合, 范围起止)。 */
    private fun resolveDateScope(mode: DateRangeMode, singleFileName: String?): Triple<Set<String>, String?, String?> {
        val today = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        return when (mode) {
            DateRangeMode.TODAY -> Triple(setOf(fmt.format(today)), null, null)
            DateRangeMode.YESTERDAY -> Triple(setOf(fmt.format(today.minusDays(1))), null, null)
            DateRangeMode.LAST_3_DAYS -> Triple((0L..2L).map { fmt.format(today.minusDays(it)) }.toSet(), null, null)
            DateRangeMode.LAST_7_DAYS -> Triple(emptySet(), fmt.format(today.minusDays(6)), fmt.format(today))
            DateRangeMode.ALL -> Triple(emptySet(), null, null)
            DateRangeMode.SINGLE_FILE -> {
                // L1：统一解析器，兼容滚动件。
                val date = singleFileName?.let { com.mini.me_core.core.util.LogFiles.parseAppLogDate(it) }
                    ?: singleFileName?.removePrefix("log-")?.removeSuffix(".txt")
                Triple(date?.let { setOf(it) } ?: emptySet(), null, null)
            }
            DateRangeMode.CUSTOM -> Triple(emptySet(), null, null) // 起止由调用方写入 state
        }
    }

    fun toggleLevel(level: LogLevel) {
        _logViewerState.update { current ->
            val newLevels = if (level in current.selectedLevels) {
                current.selectedLevels - level
            } else {
                current.selectedLevels + level
            }
            current.copy(selectedLevels = newLevels)
        }
        scope.launch {
            logFilterSettingsRepository.saveSelectedLevels(_logViewerState.value.selectedLevels)
        }
        // 局部过滤：不重新读文件
        triggerDebouncedFilter()
    }

    fun toggleTag(tag: String) {
        _logViewerState.update { current ->
            val newTags = if (tag in current.selectedTags) {
                current.selectedTags - tag
            } else {
                current.selectedTags + tag
            }
            current.copy(selectedTags = newTags)
        }
        scope.launch {
            logFilterSettingsRepository.saveSelectedTags(_logViewerState.value.selectedTags)
        }
        // 局部过滤：不重新读文件
        triggerDebouncedFilter()
    }

    fun setSearchQuery(query: String) {
        _logViewerState.update { it.copy(searchQuery = query) }
        // 局部过滤：不重新读文件
        triggerDebouncedFilter()
    }

    fun resetFilters() {
        _logViewerState.update {
            it.copy(
                selectedDates = emptySet(),
                selectedLevels = emptySet(),
                selectedTags = emptySet(),
                searchQuery = "",
                customDateStart = null,
                customDateEnd = null,
                collapsedLevels = emptySet()
            )
        }
        scope.launch {
            logFilterSettingsRepository.saveSelectedLevels(emptySet())
            logFilterSettingsRepository.saveSelectedTags(emptySet())
        }
        // 局部过滤：不重新读文件
        applyLocalFilters()
    }

    // ── 实时尾随 ──

    fun toggleLiveTail() {
        val current = _logViewerState.value
        if (current.liveTailEnabled) {
            stopLiveTail()
        } else {
            startLiveTail()
        }
    }

    private fun startLiveTail() {
        _logViewerState.update { it.copy(liveTailEnabled = true, hasNewLogs = false) }
        // 启动 FileObserver 监听日志目录变化
        val logDir = FileLogger.getLogDir() ?: return
        _liveTailFileObserver = object : android.os.FileObserver(logDir, android.os.FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path.startsWith("log-")) {
                    _logViewerState.update { it.copy(hasNewLogs = true) }
                }
            }
        }.apply { startWatching() }
        // 立即刷新一次
        refreshLogs()
    }

    private fun stopLiveTail() {
        _liveTailFileObserver?.stopWatching()
        _liveTailFileObserver = null
        _logViewerState.update { it.copy(liveTailEnabled = false, hasNewLogs = false) }
    }

    fun dismissNewLogs() {
        _logViewerState.update { it.copy(hasNewLogs = false) }
        refreshLogs()
    }

    // ── 防抖触发 ──

    private fun triggerDebouncedFilter() {
        _filterTrigger.update { it + 1 }
    }

    /** 局部过滤：仅对已缓存的行做内存过滤，不重新读文件。 */
    private fun applyLocalFilters() {
        val snapshot = _logViewerState.value
        val allLines = _cachedRawLines
        if (allLines.isEmpty()) return

        val filteredLines = allLines.filter { line ->
            matchesFilters(line, snapshot.filterServerName, snapshot)
        }
        val visibleLines = filteredLines.takeLast(MAX_LOG_LINES)

        val allTags = LogLineParser.extractTags(allLines)

        // 搜索匹配数：在可见行中统计命中搜索词的行数（大小写不敏感）
        val query = snapshot.searchQuery.trim()
        val totalMatches = if (query.isEmpty()) 0 else visibleLines.count { it.contains(query, ignoreCase = true) }

        _logViewerState.update {
            it.copy(
                content = visibleLines.joinToString("\n"),
                totalLines = filteredLines.size,
                shownLines = visibleLines.size,
                allAvailableTags = allTags,
                loading = false,
                totalMatches = totalMatches,
                currentMatchIndex = if (query.isEmpty()) 0 else 0,
            )
        }
    }

    /** 实时尾随是否自动贴底滚动（用户上滑置 false，点「返回最新」置 true）。 */
    fun setAutoScrolling(autoScrolling: Boolean) {
        _logViewerState.update { it.copy(isAutoScrolling = autoScrolling) }
    }

    // ── 核心加载逻辑 ──

    /** 流式读取日志：从文件尾部向前扫描，只读最后 N 行。 */
    private fun loadLogs(filterServerName: String?, preferredFileName: String?) {
        scope.launch {
            val snapshot = _logViewerState.value
            _logViewerState.update {
                it.copy(
                    loading = true,
                    filterServerName = filterServerName,
                    error = null
                )
            }
            val state = withContext(Dispatchers.IO) {
                runCatching {
                    readLogsIntoState(snapshot, filterServerName, preferredFileName)
                }.getOrElse { e ->
                    LogViewerUiState(
                        filterServerName = filterServerName,
                        error = "读取日志失败: ${e.message}"
                    )
                }
            }
            _logViewerState.value = state
        }
    }

    // 行为保持搬迁：原 loadLogs 内 runCatching 主体，控制流逐字等价（异常仍由外层 runCatching 兜底）。
    private fun readLogsIntoState(
        snapshot: LogViewerUiState,
        filterServerName: String?,
        preferredFileName: String?
    ): LogViewerUiState {
        val allFiles = FileLogger.listLogFiles()
        if (allFiles.isEmpty()) {
            _cachedRawLines = emptyList()
            return LogViewerUiState(
                filterServerName = filterServerName,
                error = "还没有日志文件"
            )
        }

        // 1. 按日期筛选文件列表
        val matchedFiles = filterFilesByDate(allFiles, snapshot)

        // 2. 流式读取：从每个文件尾部向前扫描，只读最后 MAX_LOG_LINES 行
        val allLines = readTailLines(matchedFiles, MAX_LOG_LINES * 2)

        // 3. 缓存原始行（供局部过滤使用）
        _cachedRawLines = allLines

        // 4. 提取所有可用 Tag
        val allTags = LogLineParser.extractTags(allLines)

        // 4.1 各等级行数统计（文件/日期作用域内、等级/Tag/搜索过滤前），供筛选栏数量徽章
        val levelCounts = countLevels(allLines)

        // 5. 多维度过滤
        val filteredLines = allLines.filter { line ->
            matchesFilters(line, filterServerName, snapshot)
        }

        // 6. 尾部截断
        val visibleLines = filteredLines.takeLast(MAX_LOG_LINES)

        // 7. 确定选中文件名
        val selectedName = preferredFileName
            ?: allFiles.firstOrNull { it.name == preferredFileName }?.name
            ?: matchedFiles.lastOrNull()?.name
            ?: allFiles.lastOrNull()?.name

        return LogViewerUiState(
            files = allFiles.map { it.name },
            selectedFileName = selectedName,
            filterServerName = filterServerName,
            content = visibleLines.joinToString("\n"),
            totalLines = filteredLines.size,
            shownLines = visibleLines.size,
            selectedDates = snapshot.selectedDates,
            selectedLevels = snapshot.selectedLevels,
            selectedTags = snapshot.selectedTags,
            allAvailableTags = allTags,
            dateRangeMode = snapshot.dateRangeMode,
            customDateStart = snapshot.customDateStart,
            customDateEnd = snapshot.customDateEnd,
            searchQuery = snapshot.searchQuery,
            liveTailEnabled = snapshot.liveTailEnabled,
            levelCounts = levelCounts,
            recordLevel = snapshot.recordLevel,
            searchExpanded = snapshot.searchExpanded,
            currentMatchIndex = snapshot.currentMatchIndex,
            totalMatches = snapshot.totalMatches,
            collapsedLevels = snapshot.collapsedLevels,
            isAutoScrolling = snapshot.isAutoScrolling
        )
    }

    /** 从多个文件尾部向前读取，收集最多 maxLines 行。 */
    private fun readTailLines(files: List<java.io.File>, maxLines: Int): List<String> {
        if (files.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        // 从最新文件开始读（文件列表按日期排序，最后一个是最近）
        for (file in files.reversed()) {
            if (result.size >= maxLines) break
            val lines = readFileTail(file, maxLines - result.size)
            result.addAll(0, lines) // 插到前面保持时间顺序
        }
        return result
    }

    /** 从单个文件尾部读取最后 n 行（UTF-8 安全）。 */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // 日志尾读兜底回退，行为保持搬迁；原 catch 未使用异常变量，保持原样
    private fun readFileTail(file: java.io.File, n: Int): List<String> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        return try {
            val raf = java.io.RandomAccessFile(file, "r")
            try {
                val fileLength = raf.length()
                // 估算读取字节数：每行按 200 字节估算，至少 8KB
                val bytesToRead = minOf(fileLength, (n * 200L).coerceAtLeast(8192L))
                val startPos = fileLength - bytesToRead
                raf.seek(startPos)
                val bytes = ByteArray(bytesToRead.toInt())
                raf.readFully(bytes)
                // 整块 UTF-8 解码（开头可能截断在多字节字符中间，但 String 解码器会正确处理）
                String(bytes, Charsets.UTF_8)
                    .lines()
                    .takeLast(n)
                    .map { it.trimEnd('\r') }
            } finally {
                raf.close()
            }
        } catch (e: Exception) {
            // 降级（L3）：用流式环形缓冲尾读，避免 readLines 把整个文件读进内存。
            readFileTailStreaming(file, n)
        }
    }

    /** 流式尾读：环形缓冲只保留最后 n 行，内存占用与文件大小无关。 */
    @Suppress("TooGenericExceptionCaught", "SwallowedException") // 日志尾读兜底回退，行为保持搬迁；原 catch 未使用异常变量，保持原样
    private fun readFileTailStreaming(file: java.io.File, n: Int): List<String> {
        val ring = ArrayDeque<String>(n)
        return try {
            file.bufferedReader(Charsets.UTF_8).useLines { seq ->
                seq.forEach { line ->
                    if (ring.size == n) ring.removeFirst()
                    ring.addLast(line.trimEnd('\r'))
                }
            }
            ring.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 按日期筛选条件匹配文件列表。 */
    private fun filterFilesByDate(
        allFiles: List<java.io.File>,
        snapshot: LogViewerUiState
    ): List<java.io.File> {
        val dates = snapshot.selectedDates
        if (dates.isNotEmpty()) {
            // 列表模式：按选中日期精确匹配（L1：统一解析器，滚动件也归入其逻辑日期）
            return allFiles.filter { f ->
                val fileDate = com.mini.me_core.core.util.LogFiles.parseAppLogDate(f.name)
                fileDate != null && fileDate in dates
            }
        }
        val rangeStart = snapshot.customDateStart
        val rangeEnd = snapshot.customDateEnd
        if (rangeStart != null || rangeEnd != null) {
            // 范围模式：按日期范围筛选
            return allFiles.filter { f ->
                val fileDate = com.mini.me_core.core.util.LogFiles.parseAppLogDate(f.name) ?: return@filter false
                val inRange = (rangeStart == null || fileDate >= rangeStart) &&
                    (rangeEnd == null || fileDate <= rangeEnd)
                inRange
            }
        }
        // 无日期筛选：返回所有文件
        return allFiles
    }

    /** 统计日志行中各等级的行数（附属行/堆栈不计入）。供显示筛选栏的数量徽章使用。 */
    /**
     * 统计**当前已展示行**中各等级的条数（供筛选栏数量徽章）。
     *
     * 与 `FileLogger.getStats()` / [com.mini.me_core.core.util.LogStats] 的分工（审计 A3）：
     *  - 这里统计的是「读进来、且已过筛选」的行 → 反映 UI 当前所见，必须逐行算，不能复用累计统计；
     *  - `LogStats` 是**写入侧**的进程级累计（按等级/Tag），供诊断与慢查询汇入使用，不随筛选变化。
     * 两者口径不同、不可互相替代，故不再视为"重复实现"。
     */
    @Suppress("LoopWithTooManyJumpStatements") // 逐行扫描跳过附属行/空等级，需提前 continue，行为保持搬迁
    private fun countLevels(lines: List<String>): Map<LogLevel, Int> {
        val counts = HashMap<LogLevel, Int>()
        for (line in lines) {
            val parsed = LogLineParser.parse(line) ?: continue
            val level = parsed.level ?: continue
            counts[level] = (counts[level] ?: 0) + 1
        }
        return counts
    }

    /** 判断单行是否通过所有筛选条件。 */
    @Suppress("ReturnCount") // 多条件守卫返回链，行为保持搬迁（逐条过滤短路判定，等价于原 early-return 链）
    private fun matchesFilters(
        line: String,
        filterServerName: String?,
        snapshot: LogViewerUiState
    ): Boolean {
        val parsed = LogLineParser.parse(line)
        if (parsed == null) {
            // 附属行（堆栈等）始终保留
            return true
        }

        // filterServerName 过滤（MCP 对话框跳转）
        if (!filterServerName.isNullOrBlank()) {
            if (!parsed.raw.contains("[$filterServerName]", ignoreCase = true) &&
                !parsed.raw.contains(filterServerName, ignoreCase = true)
            ) {
                return false
            }
        }

        // 等级过滤
        if (snapshot.selectedLevels.isNotEmpty()) {
            if (parsed.level == null || parsed.level !in snapshot.selectedLevels) {
                return false
            }
        }

        // Tag 来源过滤
        if (snapshot.selectedTags.isNotEmpty()) {
            if (parsed.tag !in snapshot.selectedTags) {
                return false
            }
        }

        // 搜索关键词过滤
        val query = snapshot.searchQuery.trim()
        if (query.isNotEmpty()) {
            if (!parsed.raw.contains(query, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    private companion object {
        const val MAX_LOG_LINES = 1200
    }
}
