package com.mini.logs.ui.logs

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mini.logs.data.LogEntry
import com.mini.logs.data.LogsViewModel
import com.mini.logs.data.ScrollRequest
import com.mini.logs.util.FormatUtils
import com.mini.me_core.core.theme.components.AppButton
import com.mini.me_core.core.theme.components.AppButtonSize
import com.mini.me_core.core.theme.components.AppButtonVariant
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import com.mini.me_core.core.util.LogLevel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 日志页入口。MainActivity 中 NavHost 引用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = viewModel(),
    storagePermissionGranted: Boolean? = null,
    onRequestPermission: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = LocalAppTheme.current.colors
    val clipboard = LocalClipboardManager.current

    // ── 收集状态 ──
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val tailingState by viewModel.tailingState.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchMatchIndex by viewModel.searchMatchIndex.collectAsStateWithLifecycle()
    val searchMatchCount by viewModel.searchMatchCount.collectAsStateWithLifecycle()
    val levelCounts by viewModel.levelCounts.collectAsStateWithLifecycle()
    val tagCounts by viewModel.tagCounts.collectAsStateWithLifecycle()
    val logFiles by viewModel.logFiles.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val scrollRequest by viewModel.scrollRequest.collectAsStateWithLifecycle()
    val expandedKeys by viewModel.expandedKeys.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // ── UI 局部状态 ──
    var showFileSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showJumpLineDialog by remember { mutableStateOf(false) }
    var showJumpTimeDialog by remember { mutableStateOf(false) }
    var pendingBookmarkEntry by remember { mutableStateOf<LogEntry?>(null) }
    var longPressEntry by remember { mutableStateOf<LogEntry?>(null) }
    var contextAnchorIndex by remember { mutableIntStateOf(-1) }
    val contextLines = remember { viewModel.settings.contextLines }

    val listState = rememberLazyListState()

    // ── 滚动请求处理 ──
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { req ->
            when (req) {
                is ScrollRequest.ToTop -> listState.animateScrollToItem(0)
                is ScrollRequest.ToBottom -> listState.animateScrollToItem(filteredEntries.lastIndex.coerceAtLeast(0))
                is ScrollRequest.ToIndex -> {
                    val idx = req.index.coerceAtMost(filteredEntries.lastIndex.coerceAtLeast(0))
                    listState.animateScrollToItem(idx)
                }
            }
            viewModel.consumeScrollRequest()
        }
    }

    // ── 计算堆栈行数 ──
    val stackTraceInfo = remember(filteredEntries) {
        // Map: main line index -> stack trace count
        val result = mutableMapOf<Int, Int>()
        var i = 0
        while (i < filteredEntries.size) {
            val entry = filteredEntries[i]
            if (!entry.isStackTraceLine) {
                var count = 0
                var j = i + 1
                while (j < filteredEntries.size && filteredEntries[j].isStackTraceLine) {
                    count++
                    j++
                }
                if (count > 0) result[i] = count
                i = j
            } else {
                i++
            }
        }
        result
    }

    // ── 检查是否滚动超过一屏（控制返回顶部按钮）──
    val showScrollTopButton by remember {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && info.visibleItemsInfo.firstOrNull()?.index ?: 0 > 5
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.surfacePage,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏 / 搜索栏 ──
            if (searchActive) {
                SearchTopBar(
                    query = searchQuery,
                    matchIndex = searchMatchIndex,
                    matchCount = searchMatchCount,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    onExitSearch = { viewModel.setSearchActive(false) },
                    onClearQuery = { viewModel.updateSearchQuery("") },
                    onPrevMatch = { viewModel.prevSearchMatch() },
                    onNextMatch = { viewModel.nextSearchMatch() },
                )
            } else {
                AppTopAppBar(
                    title = "MiniMe Logs",
                    actions = {
                        IconButton(onClick = { viewModel.setSearchActive(true) }) {
                            Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = colors.textSecondary)
                        }
                        IconButton(onClick = { showExportSheet = true }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "导出", tint = colors.textSecondary)
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = colors.textSecondary)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("跳转到行号") },
                                    onClick = { showMoreMenu = false; showJumpLineDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("跳转到时间") },
                                    onClick = { showMoreMenu = false; showJumpTimeDialog = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("下一个 ERROR") },
                                    onClick = { showMoreMenu = false; viewModel.nextError() },
                                )
                                DropdownMenuItem(
                                    text = { Text("切换视图模式 (${if (viewMode == com.mini.logs.data.ViewMode.COMPACT) "紧凑" else "舒适"})") },
                                    onClick = { showMoreMenu = false; viewModel.toggleViewMode() },
                                )
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    onClick = { showMoreMenu = false; viewModel.refreshFiles() },
                                )
                            }
                        }
                    },
                )
            }

            // ── 等级筛选栏 ──
            if (!searchActive) {
                LevelFilterBar(
                    levelCounts = levelCounts,
                    selectedLevels = filterState.selectedLevels,
                    collapsedLevels = filterState.levelCollapsed,
                    onLevelClick = { level ->
                        if (level in filterState.selectedLevels) {
                            viewModel.toggleLevelCollapse(level)
                        } else {
                            viewModel.selectLevelAndAbove(level)
                        }
                    },
                    onLevelLongClick = { level -> viewModel.toggleLevel(level) },
                    onClearFilter = { viewModel.clearLevelFilter() },
                )
            }

            // ── 文件/尾随栏 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrimitiveSpacing.SmPlus, vertical = PrimitiveSpacing.Xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // 文件选择按钮
                Row(
                    modifier = Modifier
                        .padding(horizontal = PrimitiveSpacing.Xs)
                        .clickableNoRipple { showFileSheet = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(PrimitiveSpacing.Xs))
                    val fileLabel = when {
                        selectedFiles.isEmpty() -> "全部文件"
                        selectedFiles.size == 1 -> selectedFiles.first()
                        else -> "${selectedFiles.size} 个文件"
                    }
                    Text(
                        text = fileLabel,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 尾随开关
                    val tailLabel = when {
                        !tailingState.isEnabled -> "尾随"
                        tailingState.isPaused -> "已暂停"
                        else -> "尾随中 ${tailingState.linesPerSecond}行/s"
                    }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = PrimitiveSpacing.Xs)
                            .clickableNoRipple {
                                viewModel.toggleTailing(!tailingState.isEnabled)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (tailingState.isPaused) Icons.Rounded.Pause
                            else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (tailingState.isEnabled && !tailingState.isPaused) colors.success else colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = tailLabel,
                            fontSize = 11.sp,
                            color = if (tailingState.isEnabled && !tailingState.isPaused) colors.success else colors.textTertiary,
                        )
                    }

                    // 筛选按钮
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "筛选",
                            tint = if (filterState.isFiltering) colors.brandPrimary else colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // 视图切换按钮
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = "切换视图",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── 日志列表 / 空状态 ──
            if (storagePermissionGranted == false && logFiles.isEmpty()) {
                // 权限被拒绝：显示权限引导
                AppEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "需要存储权限",
                    subtitle = "附属应用需要读取存储权限才能查看主应用的日志文件\n请点击下方按钮授权",
                    actionLabel = "授予权限",
                    onAction = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )
            } else if (logFiles.isEmpty() && !isLoading) {
                AppEmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "暂无日志文件",
                    subtitle = "请先在主应用中使用产生日志\n日志目录：Documents/MiniMe-core/logs/\n（若主应用未授予存储权限，日志会写入私有目录）",
                    actionLabel = "刷新",
                    onAction = { viewModel.refreshFiles() },
                    modifier = Modifier.weight(1f),
                )
            } else if (filteredEntries.isEmpty() && !isLoading) {
                AppEmptyState(
                    icon = Icons.Rounded.Search,
                    title = if (searchQuery.isNotEmpty()) "未找到匹配项" else "没有日志",
                    subtitle = if (searchQuery.isNotEmpty()) "关键词：\"$searchQuery\"" else "尝试调整筛选条件",
                    actionLabel = "清除筛选",
                    onAction = { viewModel.resetFilter() },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = filteredEntries,
                            key = { index, entry -> "${entry.sourceFile}_${entry.lineNumber}_$index" },
                        ) { index, entry ->
                            val hasStack = stackTraceInfo.containsKey(index)
                            val stackCount = stackTraceInfo[index] ?: 0
                            val expanded = viewModel.isExpanded(entry)
                            val inContext = contextAnchorIndex >= 0 &&
                                    index >= (contextAnchorIndex - contextLines) &&
                                    index <= (contextAnchorIndex + contextLines)

                            // 堆栈行（折叠状态下不显示原始堆栈行，由主行的 "+N行堆栈" 提示代替）
                            if (entry.isStackTraceLine && !expanded) {
                                return@itemsIndexed
                            }

                            LogListItem(
                                entry = entry,
                                viewMode = viewMode,
                                isExpanded = expanded,
                                hasStacktrace = hasStack,
                                stackTraceCount = stackCount,
                                searchQuery = searchQuery,
                                isSearchMatch = searchQuery.isNotEmpty() &&
                                    entry.rawLine.contains(searchQuery, ignoreCase = true),
                                inContextWindow = inContext && !entry.isStackTraceLine,
                                onClick = {
                                    if (entry.isMainLine) {
                                        viewModel.toggleExpand(entry)
                                        // 点击上下文锚点外区域清除上下文
                                        if (contextAnchorIndex >= 0 && index != contextAnchorIndex) {
                                            contextAnchorIndex = -1
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (entry.isMainLine) {
                                        longPressEntry = entry
                                    }
                                },
                            )
                        }
                    }

                    // ── 浮动按钮层 ──
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(PrimitiveSpacing.Lg),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(PrimitiveSpacing.Sm),
                    ) {
                        // 返回顶部
                        if (showScrollTopButton) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.scrollToTop() },
                                modifier = Modifier.size(40.dp),
                                containerColor = colors.surfaceCard,
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "返回顶部", tint = colors.textSecondary)
                            }
                        }

                        // 新日志提示
                        if (tailingState.isPaused && tailingState.newLinesCount > 0) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.resumeTailing() },
                                containerColor = colors.brandPrimary,
                            ) {
                                Text(
                                    text = "↓ 有 ${if (tailingState.newLinesCount > 99) "99+" else tailingState.newLinesCount} 条新日志",
                                    color = colors.onBrandPrimary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 长按操作菜单 ──
        longPressEntry?.let { entry ->
            LogActionMenu(
                expanded = true,
                onDismiss = { longPressEntry = null },
                onCopyFull = {
                    clipboard.setText(AnnotatedString(entry.rawLine))
                    Toast.makeText(context, "已复制全文", Toast.LENGTH_SHORT).show()
                },
                onCopyMessage = {
                    clipboard.setText(AnnotatedString(entry.message))
                    Toast.makeText(context, "已复制消息", Toast.LENGTH_SHORT).show()
                },
                onCopyTimestamp = {
                    clipboard.setText(AnnotatedString(entry.time))
                    Toast.makeText(context, "已复制时间戳", Toast.LENGTH_SHORT).show()
                },
                onCopyTag = {
                    clipboard.setText(AnnotatedString(entry.tag))
                    Toast.makeText(context, "已复制 Tag", Toast.LENGTH_SHORT).show()
                },
                onHighlight = { colorIdx ->
                    viewModel.setHighlight(entry, colorIdx)
                },
                onBookmark = {
                    pendingBookmarkEntry = entry
                },
                onViewContext = {
                    val idx = filteredEntries.indexOfFirst {
                        it.sourceFile == entry.sourceFile && it.lineNumber == entry.lineNumber
                    }
                    if (idx >= 0) contextAnchorIndex = idx
                },
                onNextError = { viewModel.nextError() },
            )
        }

        // ── Sheet 们 ──
        if (showFileSheet) {
            FileSelectorSheet(
                logFiles = logFiles,
                selectedFiles = selectedFiles,
                onSelectQuickRange = { days -> viewModel.selectQuickRange(days) },
                onToggleFile = { name, sel -> viewModel.selectFile(name, sel) },
                onSelectAll = { viewModel.selectAllFiles() },
                onReset = { viewModel.refreshFiles() },
                onDismiss = { showFileSheet = false },
            )
        }

        if (showFilterSheet) {
            FilterSheet(
                tagCounts = tagCounts,
                selectedTags = filterState.selectedTags,
                excludedTags = filterState.excludedTags,
                onToggleTag = { viewModel.toggleTag(it) },
                onToggleExcludeTag = { viewModel.toggleExcludeTag(it) },
                onClearTags = { viewModel.clearTagFilter() },
                onReset = { viewModel.resetFilter() },
                onDismiss = { showFilterSheet = false },
            )
        }

        if (showExportSheet) {
            ExportSheet(
                currentEntries = filteredEntries,
                allEntries = allEntries,
                onDismiss = { showExportSheet = false },
            )
        }

        // ── 书签备注对话框 ──
        pendingBookmarkEntry?.let { entry ->
            var noteText by remember { mutableStateOf(entry.bookmarkNote ?: "") }
            AlertDialog(
                onDismissRequest = { pendingBookmarkEntry = null },
                title = { Text("添加书签") },
                text = {
                    Column {
                        Text(
                            text = "${entry.time} ${entry.tag}",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(PrimitiveSpacing.Sm))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { noteText = it },
                            label = { Text("备注") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setBookmark(entry, noteText.ifBlank { null })
                        pendingBookmarkEntry = null
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.setBookmark(entry, null)
                        pendingBookmarkEntry = null
                    }) { Text("删除书签") }
                },
            )
        }

        // ── 跳转到行号对话框 ──
        if (showJumpLineDialog) {
            var lineText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showJumpLineDialog = false },
                title = { Text("跳转到行号") },
                text = {
                    OutlinedTextField(
                        value = lineText,
                        onValueChange = { lineText = it.filter { c -> c.isDigit() } },
                        label = { Text("行号") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        lineText.toIntOrNull()?.let { viewModel.jumpToLine(it) }
                        showJumpLineDialog = false
                    }) { Text("跳转") }
                },
                dismissButton = { TextButton(onClick = { showJumpLineDialog = false }) { Text("取消") } },
            )
        }

        // ── 跳转到时间对话框 ──
        if (showJumpTimeDialog) {
            var timeText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showJumpTimeDialog = false },
                title = { Text("跳转到时间") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { timeText = it },
                            label = { Text("HH:mm (如 14:30)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parts = timeText.split(":")
                        if (parts.size == 2) {
                            val h = parts[0].toIntOrNull()
                            val m = parts[1].toIntOrNull()
                            if (h != null && m != null) viewModel.jumpToTime(h, m)
                        }
                        showJumpTimeDialog = false
                    }) { Text("跳转") }
                },
                dismissButton = { TextButton(onClick = { showJumpTimeDialog = false }) { Text("取消") } },
            )
        }
    }
}

/** 无 ripple 的点击修饰符。 */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    ))
