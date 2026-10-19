package com.mini.me_core.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppDialog
import com.mini.me_core.core.theme.components.AppStatusDot
import com.mini.me_core.core.theme.components.AppStatusDotColor
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.feature.settings.presentation.DateRangeMode
import com.mini.me_core.feature.settings.presentation.LogViewerUiState
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 运行日志查看器主页面（设计文档 §4/§5/§6）。
 *
 * - 竖屏（<600dp）：控制栏 3 行堆叠在上，日志内容区占主体，底部状态栏。
 * - 横屏/平板（≥600dp）：左侧 280dp 控制栏，右侧日志内容区。
 */
@Composable
fun LogViewerScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.logViewerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showExportConfirm by remember { mutableStateOf(false) }
    val listState = rememberPersistentLazyListState("settings_logs")

    val isExpanded = LocalConfiguration.current.screenWidthDp >= 600
    val entries = remember(state.content, state.collapsedLevels) {
        applyCollapse(buildLogEntries(state.content.split("\n")), state.collapsedLevels)
    }

    // ── 实时尾随：自动贴底滚动 ──
    LaunchedEffect(state.liveTailEnabled, state.isAutoScrolling, entries.size) {
        if (state.liveTailEnabled && state.isAutoScrolling && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }
    // 用户上滑超过阈值 → 暂停尾随
    LaunchedEffect(listState, state.liveTailEnabled, entries.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (state.liveTailEnabled && entries.isNotEmpty() && firstVisible < entries.size - 3) {
                    viewModel.setAutoScrolling(false)
                }
            }
    }

    // ── 实时性：查看器在前台期间定时静默重载，保证看到最新日志 ──
    // 历史教训：旧实现依赖 FileObserver(logDir, CLOSE_WRITE) 感知新日志，而 FileLogger 的
    // 常驻 BufferedWriter 从不关闭文件，事件永不触发，内容停在进入页面那一刻。改为在
    // RESUMED 期间轮询：离开前台（HOME / 切页）自动停，回到前台立即补一次。
    LogViewerAutoRefresh(viewModel)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LogSearchTopBar(
                searchExpanded = state.searchExpanded,
                searchQuery = state.searchQuery,
                totalMatches = state.totalMatches,
                currentMatchIndex = state.currentMatchIndex,
                onNavigateBack = onNavigateBack,
                onToggleSearch = { viewModel.setSearchExpanded(it) },
                onSearchQuery = { viewModel.setSearchQuery(it) },
                onPrevMatch = { viewModel.prevMatch() },
                onNextMatch = { viewModel.nextMatch() },
                onOpenFilter = { showFilterSheet = true },
                onExport = { showExportConfirm = true },
            )

            if (isExpanded) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ControlPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.width(280.dp).verticalScroll(rememberScrollState()),
                    )
                    LogContent(
                        entries = entries,
                        state = state,
                        listState = listState,
                        modifier = Modifier.weight(1f),
                        onToggleCollapse = { viewModel.toggleLevelCollapse(it) },
                    )
                }
            } else {
                ControlPanel(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(horizontal = PrimitiveSpacing.Sm),
                )
                LogContent(
                    entries = entries,
                    state = state,
                    listState = listState,
                    modifier = Modifier.weight(1f),
                    onToggleCollapse = { viewModel.toggleLevelCollapse(it) },
                )
            }

            LogBottomStatusBar(
                shownLines = state.shownLines,
                totalLines = state.totalLines,
                dateRangeLabel = quickRangeLabel(state.dateRangeMode),
                fileCount = state.files.size,
                liveTailEnabled = state.liveTailEnabled,
                onNextError = {
                    val current = listState.firstVisibleItemIndex
                    val next = entries.indexOfFirst {
                        it is LogListItem.Entry && it.parsed.level == LogLevel.ERROR
                    }.let { idx ->
                        // 从当前位置之后找下一个 ERROR；没有则回到第一个
                        entries.indices.firstOrNull { i ->
                            i > current && (entries[i] as? LogListItem.Entry)?.parsed?.level == LogLevel.ERROR
                        } ?: idx.takeIf { it >= 0 }
                    }
                    if (next != null && next >= 0) {
                        scope.launch { listState.animateScrollToItem(next) }
                    }
                },
            )
        }

        // ── 「返回最新」悬浮按钮：尾随开启但用户上滑时显示 ──
        if (state.liveTailEnabled && !state.isAutoScrolling) {
            TextButton(
                onClick = {
                    viewModel.setAutoScrolling(true)
                    scope.launch { listState.animateScrollToItem(entries.size - 1) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = PrimitiveSpacing.Xl),
            ) {
                Text(stringResource(R.string.log_back_to_latest))
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))

        if (showFilterSheet) {
            LogFilterBottomSheet(
                state = state,
                onDismiss = { showFilterSheet = false },
                onQuickRange = { viewModel.setDateRangeMode(it) },
                onToggleLevel = { viewModel.toggleLevel(it) },
                onToggleTag = { viewModel.toggleTag(it) },
                onReset = { viewModel.resetFilters() },
            )
        }

        // 导出前的敏感信息提示：日志虽已经 LogSanitizer 脱敏，
        // 但导出文件写入公共 Download 目录，分享前仍需用户知情确认（安全修复清单 3.3）。
        if (showExportConfirm) {
            AppDialog(
                title = stringResource(R.string.logs_export_confirm_title),
                message = stringResource(R.string.logs_export_confirm_message),
                confirmText = stringResource(R.string.logs_export_confirm),
                cancelText = stringResource(R.string.logs_export_cancel),
                onDismiss = { showExportConfirm = false },
                onConfirm = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { com.mini.me_core.core.util.FileLogger.exportLogsToDownloads(context) }.getOrElse { emptyList() }
                        }
                        snackbarHostState.showSnackbar(
                            if (result.isEmpty()) context.getString(R.string.logs_export_none)
                            else context.getString(R.string.logs_export_toast)
                        )
                    }
                },
            )
        }
    }
}

/**
 * 查看器前台轮询：RESUMED 期间每 [LOG_VIEWER_POLL_INTERVAL_MS] 静默重载一次。
 *
 * 为什么轮询而不是 FileObserver：FileLogger 写文件用的是常驻 BufferedWriter，
 * 正常轮询期间从不 close，CLOSE_WRITE 事件永不触发（只有日志轮转/进程死亡才触发），
 * 旧监听实际上是死的。repeatOnLifecycle(RESUMED) 保证离开前台自动停、回前台立即补一次。
 */
@Composable
private fun LogViewerAutoRefresh(viewModel: SettingsViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(LOG_VIEWER_POLL_INTERVAL_MS)
                viewModel.refreshLogsSilently()
            }
        }
    }
}

/** 控制栏（竖屏堆叠 / 横屏左侧栏共用）。 */
@Composable
private fun ControlPanel(
    state: LogViewerUiState,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = PrimitiveSpacing.Sm), verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm)) {
        LogRecordLevelRow(
            currentLevel = state.recordLevel,
            onSelectLevel = { viewModel.setRecordLevel(it) },
        )
        HorizontalDivider()
        LogQuickFilterBar(
            levelCounts = state.levelCounts,
            selectedLevels = state.selectedLevels,
            collapsedLevels = state.collapsedLevels,
            onSelectAll = { viewModel.clearDisplayLevels() },
            onLevelClick = { viewModel.quickFilterLevel(it) },
        )
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogFileSelector(
                files = state.files,
                dateRangeMode = state.dateRangeMode,
                customDateStart = state.customDateStart,
                customDateEnd = state.customDateEnd,
                selectedFileName = state.selectedFileName,
                onQuickRange = { viewModel.setDateRangeMode(it) },
                onSelectFile = { viewModel.selectSingleFile(it) },
                onCustomRange = { },
            )
            Spacer(Modifier.weight(1f))
            LiveTailToggle(enabled = state.liveTailEnabled, onToggle = { viewModel.toggleLiveTail() })
        }
    }
}

/** 实时尾随开关：状态点 + 文字，开启时红点脉冲。 */
@Composable
private fun LiveTailToggle(enabled: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppTheme.current.colors
    Row(
        modifier = Modifier
            .padding(end = PrimitiveSpacing.Sm)
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppStatusDot(
            color = if (enabled) AppStatusDotColor.Error else AppStatusDotColor.Neutral,
            pulse = enabled,
            modifier = Modifier.padding(end = PrimitiveSpacing.Xxs),
        )
        Text(
            text = stringResource(R.string.log_live_tail),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) colors.error else colors.textSecondary,
        )
    }
}

/** 日志内容区：LazyColumn 虚拟化渲染结构化日志行。 */
@Composable
private fun LogContent(
    entries: List<LogListItem>,
    state: LogViewerUiState,
    listState: LazyListState,
    onToggleCollapse: (LogLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.loading -> Text(
                text = stringResource(R.string.log_reading),
                modifier = Modifier.padding(PrimitiveSpacing.Lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error != null -> Text(
                text = state.error,
                modifier = Modifier.padding(PrimitiveSpacing.Lg),
                color = MaterialTheme.colorScheme.error,
            )
            entries.isEmpty() -> Text(
                text = stringResource(R.string.log_no_match),
                modifier = Modifier.padding(PrimitiveSpacing.Lg),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(entries) { item ->
                    when (item) {
                        is LogListItem.Entry -> LogLineItem(
                            entry = item,
                            searchQuery = state.searchQuery,
                        )
                        is LogListItem.Loose -> LooseLogLine(line = item.line)
                        is LogListItem.Collapsed -> CollapsedRow(
                            level = item.level,
                            count = item.count,
                            onClick = { onToggleCollapse(item.level) },
                        )
                    }
                }
            }
        }
    }
}

/** 快捷范围的中文短标签（底部状态栏用）。 */
@Composable
private fun quickRangeLabel(mode: DateRangeMode): String = when (mode) {
    DateRangeMode.TODAY -> stringResource(R.string.log_quick_today)
    DateRangeMode.YESTERDAY -> stringResource(R.string.log_quick_yesterday)
    DateRangeMode.LAST_3_DAYS -> stringResource(R.string.log_quick_last3)
    DateRangeMode.LAST_7_DAYS -> stringResource(R.string.log_quick_last7)
    DateRangeMode.ALL -> stringResource(R.string.log_status_range_all)
    DateRangeMode.SINGLE_FILE -> stringResource(R.string.log_status_range_all)
    DateRangeMode.CUSTOM -> "自定义"
}

/**
 * 查看器前台轮询间隔（毫秒）。
 *
 * 2s 是「实时感」与开销的折中：FileLogger 的定时 flush 周期为 500ms，轮询前会先 flush，
 * 因此最多约 2s 的可见延迟；每轮只读文件尾部若干 KB，CPU/IO 开销可忽略。
 */
private const val LOG_VIEWER_POLL_INTERVAL_MS = 2_000L
