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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.components.AppStatusDot
import com.mini.me_core.core.theme.components.AppStatusDotColor
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel
import com.mini.me_core.feature.settings.presentation.DateRangeMode
import com.mini.me_core.feature.settings.presentation.LogViewerUiState
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.Dispatchers
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

    val isExpanded = LocalConfiguration.current.screenWidthDp >= 600
    val entries = remember(state.content) { buildLogEntries(state.content.split("\n")) }

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
                onExport = {
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

            if (isExpanded) {
                // 横屏分栏
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ControlPanel(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.width(280.dp).verticalScroll(rememberScrollState()),
                    )
                    LogContent(
                        entries = entries,
                        state = state,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // 竖屏堆叠
                ControlPanel(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(horizontal = PrimitiveSpacing.Sm),
                )
                LogContent(
                    entries = entries,
                    state = state,
                    modifier = Modifier.weight(1f),
                )
            }

            LogBottomStatusBar(
                shownLines = state.shownLines,
                totalLines = state.totalLines,
                dateRangeLabel = quickRangeLabel(state.dateRangeMode),
                fileCount = state.files.size,
                liveTailEnabled = state.liveTailEnabled,
                onJump = { /* Stage 4: 跳转菜单 */ },
            )
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
                onCustomRange = { /* 打开底部弹窗（父级控制） */ },
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
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { item ->
                    when (item) {
                        is LogListItem.Entry -> LogLineItem(
                            entry = item,
                            searchQuery = state.searchQuery,
                        )
                        is LogListItem.Loose -> LooseLogLine(line = item.line)
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
    DateRangeMode.SINGLE_FILE -> stateLabelFallback
    DateRangeMode.CUSTOM -> "自定义"
}

private const val stateLabelFallback = ""
