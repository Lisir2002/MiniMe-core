package com.mini.logs.ui.crash

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.mini.logs.data.CrashAggregator
import com.mini.logs.data.CrashGroup
import com.mini.logs.data.LogRepository
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppEmptyState
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.tokens.LocalAppTheme

/** 崩溃筛选条件。 */
private enum class CrashFilter(val label: String) {
    ALL("全部"),
    UNFIXED("未修复"),
    FIXED("已修复");

    companion object {
        val options = entries.toList()
    }
}

/**
 * 崩溃聚合页。按异常类型聚合 ERROR/FATAL，支持筛选、标记已修复、全屏详情。
 */
@Composable
fun CrashScreen() {
    val colors = LocalAppTheme.current.colors
    val context = LocalContext.current
    val repository = remember { LogRepository(context) }
    val clipboard = LocalClipboardManager.current

    var rawCrashes by remember { mutableStateOf<List<CrashGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val fixedKeys = remember { mutableStateMapOf<String, Boolean>() }
    var filter by remember { mutableStateOf(CrashFilter.ALL) }
    var detailCrash by remember { mutableStateOf<CrashGroup?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val files = repository.listLogFiles()
        val entries = repository.loadEntries(files, maxLines = 60_000)
        rawCrashes = CrashAggregator.aggregate(entries)
        isLoading = false
    }

    // 应用已修复状态 + 排序（未修复在前，各自按次数降序）+ 筛选
    val crashes = remember(rawCrashes, fixedKeys, filter) {
        val withFixed = rawCrashes.map { it.copy(isFixed = fixedKeys[it.key] == true) }
        val sorted = withFixed.sortedWith(
            compareBy({ it.isFixed }, { -it.occurrences })
        )
        when (filter) {
            CrashFilter.ALL -> sorted
            CrashFilter.UNFIXED -> sorted.filter { !it.isFixed }
            CrashFilter.FIXED -> sorted.filter { it.isFixed }
        }
    }

    fun toggleFixed(group: CrashGroup) {
        if (fixedKeys[group.key] == true) fixedKeys.remove(group.key)
        else fixedKeys[group.key] = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfacePage),
    ) {
        AppTopAppBar(
            title = "崩溃聚合",
            actions = {
                FilterDropdown(
                    filter = filter,
                    onFilterChange = { filter = it },
                )
            },
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brandPrimary)
            }

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
                        onClick = { detailCrash = crash },
                        onCopy = { clipboard.setText(AnnotatedString(crash.fullStackTrace)) },
                        onToggleFixed = { toggleFixed(crash) },
                    )
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

/** 顶栏右侧筛选下拉。 */
@Composable
private fun FilterDropdown(filter: CrashFilter, onFilterChange: (CrashFilter) -> Unit) {
    val colors = LocalAppTheme.current.colors
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = "筛选",
                tint = colors.textSecondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CrashFilter.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            color = if (option == filter) colors.brandPrimary else colors.textPrimary,
                        )
                    },
                    onClick = {
                        onFilterChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
