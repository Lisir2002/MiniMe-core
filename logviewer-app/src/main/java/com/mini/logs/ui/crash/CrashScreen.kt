package com.mini.logs.ui.crash

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mini.logs.data.AppDataSession
import com.mini.logs.data.CrashAggregator
import com.mini.logs.data.CrashGroup
import com.mini.logs.data.LogRepository
import com.mini.logs.data.SettingsStore
import com.mini.logs.ui.components.CrashSkeleton
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/** 崩溃筛选条件。 */
private enum class CrashTab(val label: String) {
    ALL("全部"),
    UNFIXED("未修复"),
    FIXED("已修复"),
    IGNORED("已忽略");

    companion object {
        val options = entries.toList()
    }
}

/** 排序方式。 */
private enum class SortMode(val label: String) {
    OCCURRENCES("按次数"),
    LAST("按最近时间"),
    FIRST("按首次时间");
}

/**
 * 崩溃聚合页。按异常类型聚合 ERROR/FATAL，支持搜索、Tab 筛选、排序、标记已修复、忽略、全屏详情。
 */
@Composable
fun CrashScreen() {
    val colors = LocalAppTheme.current.colors
    val context = LocalContext.current
    val repository = remember { LogRepository(context) }
    val settings = remember { SettingsStore(context) }
    val clipboard = LocalClipboardManager.current

    var rawCrashes by remember { mutableStateOf<List<CrashGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    // 已修复 / 已忽略状态：启动时从 SharedPreferences 读取
    val fixedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val ignoredKeys = remember { mutableStateMapOf<String, Boolean>() }
    var tab by remember { mutableStateOf(CrashTab.ALL) }
    var sortMode by remember { mutableStateOf(SortMode.OCCURRENCES) }
    var detailCrash by remember { mutableStateOf<CrashGroup?>(null) }

    // 搜索状态
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 多选状态
    val selectedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val selectionMode = selectedKeys.any { it.value }

    // 初始化持久化状态
    LaunchedEffect(Unit) {
        settings.fixedCrashKeys.forEach { fixedKeys[it] = true }
        settings.ignoredCrashKeys.forEach { ignoredKeys[it] = true }
    }

    // 监听全局数据版本：日志目录切换时自动重新加载
    val dataVersion by AppDataSession.dataVersion.collectAsState()

    LaunchedEffect(dataVersion) {
        isLoading = true
        val files = repository.listLogFiles()
        val entries = repository.loadEntries(files, maxLines = settings.maxLoadLines)
        rawCrashes = CrashAggregator.aggregate(entries)
        isLoading = false
    }

    // Tab 计数
    val counts = remember(rawCrashes, fixedKeys, ignoredKeys) {
        val all = rawCrashes
        CrashTab.values().associateWith { t ->
            when (t) {
                CrashTab.ALL -> all.count { ignoredKeys[it.key] != true }
                CrashTab.UNFIXED -> all.count { fixedKeys[it.key] != true && ignoredKeys[it.key] != true }
                CrashTab.FIXED -> all.count { fixedKeys[it.key] == true && ignoredKeys[it.key] != true }
                CrashTab.IGNORED -> all.count { ignoredKeys[it.key] == true }
            }
        }
    }

    // 搜索 + 排序 + Tab 过滤
    val crashes = remember(rawCrashes, fixedKeys, ignoredKeys, tab, sortMode, searchQuery) {
        val withFixed = rawCrashes.map {
            it.copy(isFixed = fixedKeys[it.key] == true)
        }
        val q = searchQuery.trim().lowercase()
        val searched = if (q.isBlank()) {
            withFixed
        } else {
            withFixed.filter { g ->
                g.exceptionType.lowercase().contains(q) ||
                    g.message.lowercase().contains(q) ||
                    g.tags.any { it.lowercase().contains(q) } ||
                    g.fullStackTrace.lowercase().contains(q)
            }
        }
        // Tab 过滤
        val filtered = when (tab) {
            CrashTab.ALL -> searched.filter { ignoredKeys[it.key] != true }
            CrashTab.UNFIXED -> searched.filter { !it.isFixed && ignoredKeys[it.key] != true }
            CrashTab.FIXED -> searched.filter { it.isFixed && ignoredKeys[it.key] != true }
            CrashTab.IGNORED -> searched.filter { ignoredKeys[it.key] == true }
        }
        // 排序
        when (sortMode) {
            SortMode.OCCURRENCES -> filtered.sortedWith(
                compareBy({ it.isFixed }, { -it.occurrences })
            )
            SortMode.LAST -> filtered.sortedByDescending { it.lastOccurrence }
            SortMode.FIRST -> filtered.sortedByDescending { it.firstOccurrence }
        }
    }

    fun toggleFixed(group: CrashGroup) {
        if (fixedKeys[group.key] == true) {
            fixedKeys.remove(group.key)
        } else {
            fixedKeys[group.key] = true
        }
        settings.fixedCrashKeys = fixedKeys.filterValues { it }.keys
    }

    fun toggleIgnored(group: CrashGroup) {
        if (ignoredKeys[group.key] == true) {
            ignoredKeys.remove(group.key)
        } else {
            ignoredKeys[group.key] = true
        }
        settings.ignoredCrashKeys = ignoredKeys.filterValues { it }.keys
    }

    fun toggleSelect(key: String) {
        if (selectedKeys[key] == true) selectedKeys.remove(key)
        else selectedKeys[key] = true
    }

    fun clearSelection() {
        selectedKeys.clear()
    }

    fun selectAllVisible() {
        crashes.forEach { selectedKeys[it.key] = true }
    }

    fun batchMarkFixed() {
        val keys = selectedKeys.filterValues { it }.keys
        keys.forEach { fixedKeys[it] = true }
        settings.fixedCrashKeys = fixedKeys.filterValues { it }.keys
        clearSelection()
    }

    fun batchIgnore() {
        val keys = selectedKeys.filterValues { it }.keys
        keys.forEach { ignoredKeys[it] = true }
        settings.ignoredCrashKeys = ignoredKeys.filterValues { it }.keys
        clearSelection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage),
    ) {
        if (selectionMode) {
            // 多选模式顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceCard)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已选 ${selectedKeys.count { it.value }} 项",
                    fontSize = 15.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "全选",
                    fontSize = 13.sp,
                    color = colors.brandPrimary,
                    modifier = Modifier
                        .clickable { selectAllVisible() }
                        .padding(Spacing.sm),
                )
                Text(
                    text = "清除",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clickable { clearSelection() }
                        .padding(Spacing.sm),
                )
                Text(
                    text = "修复",
                    fontSize = 13.sp,
                    color = colors.success,
                    modifier = Modifier
                        .clickable { batchMarkFixed() }
                        .padding(Spacing.sm),
                )
                Text(
                    text = "忽略",
                    fontSize = 13.sp,
                    color = colors.error,
                    modifier = Modifier
                        .clickable { batchIgnore() }
                        .padding(Spacing.sm),
                )
            }
        } else if (searchActive) {
            // 搜索模式顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    searchActive = false
                    searchQuery = ""
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "退出搜索",
                        tint = colors.textSecondary,
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索类型/消息/Tag/堆栈", fontSize = 13.sp) },
                    singleLine = true,
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "清除",
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
        } else {
            AppTopAppBar(
                title = "崩溃聚合",
                actions = {
                    IconButton(onClick = { searchActive = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "搜索",
                            tint = colors.textSecondary,
                        )
                    }
                    SortDropdown(
                        sortMode = sortMode,
                        onSortChange = { sortMode = it },
                    )
                },
            )
        }

        // 筛选 Tab 行
        ScrollableTabRow(
            selectedTabIndex = CrashTab.options.indexOf(tab).coerceAtLeast(0),
            edgePadding = Spacing.md,
            indicator = { tabPositions ->
                if (CrashTab.options.indexOf(tab) < tabPositions.size) {
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[CrashTab.options.indexOf(tab)]),
                        color = colors.brandPrimary,
                    )
                }
            },
        ) {
            CrashTab.options.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = {
                        Text(
                            "${t.label} ${counts[t] ?: 0}",
                            fontSize = 13.sp,
                            color = if (tab == t) colors.brandPrimary else colors.textSecondary,
                        )
                    },
                )
            }
        }

        Crossfade(targetState = isLoading, label = "crash-content") { loading ->
            when {
                loading -> CrashSkeleton()
                rawCrashes.isEmpty() -> AppEmptyState(
                    title = "暂无崩溃记录",
                    subtitle = "✅ 运行日志中未发现 ERROR / FATAL 崩溃",
                    icon = Icons.Rounded.CheckCircleOutline,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item {
                        Text(
                            text = "${rawCrashes.size} 类崩溃 · 共 ${rawCrashes.sumOf { it.occurrences }} 次",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    items(crashes, key = { it.key }) { crash ->
                        CrashCard(
                            crash = crash,
                            onClick = { if (selectionMode) toggleSelect(crash.key) else detailCrash = crash },
                            onCopy = { clipboard.setText(AnnotatedString(crash.fullStackTrace)) },
                            onToggleFixed = { toggleFixed(crash) },
                            onIgnore = { toggleIgnored(crash) },
                            searchQuery = searchQuery.trim().ifBlank { null },
                            selectionMode = selectionMode,
                            isSelected = selectedKeys[crash.key] == true,
                            onLongPress = { toggleSelect(crash.key) },
                        )
                    }
                }
            }
        }
    }

    detailCrash?.let { crash ->
        // 展示时同步最新的已修复状态
        val effective = crash.copy(isFixed = fixedKeys[crash.key] == true)
        CrashDetailSheet(
            crash = effective,
            onDismiss = { detailCrash = null },
            onCopy = { clipboard.setText(AnnotatedString(it)) },
            onExport = { title, text ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "导出崩溃"))
            },
            onToggleFixed = { toggleFixed(effective) },
        )
    }
}

/** 排序下拉。 */
@Composable
private fun SortDropdown(sortMode: SortMode, onSortChange: (SortMode) -> Unit) {
    val colors = LocalAppTheme.current.colors
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.Sort,
                contentDescription = "排序",
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.values().forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(
                            m.label,
                            color = if (m == sortMode) colors.brandPrimary else colors.textPrimary,
                        )
                    },
                    onClick = {
                        onSortChange(m)
                        expanded = false
                    },
                )
            }
        }
    }
}
