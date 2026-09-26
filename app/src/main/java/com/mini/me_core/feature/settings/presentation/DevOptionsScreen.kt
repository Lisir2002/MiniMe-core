package com.mini.me_core.feature.settings.presentation

import android.app.Activity
import android.view.Choreographer
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.theme.components.AppCard
import com.mini.me_core.core.theme.components.AppCardVariant
import com.mini.me_core.core.theme.components.AppConfirmDialog
import com.mini.me_core.core.theme.components.AppListItem
import com.mini.me_core.core.theme.components.AppSectionHeader
import com.mini.me_core.core.theme.components.AppSegmentedControl
import com.mini.me_core.core.theme.tokens.LocalAppTheme
import com.mini.me_core.core.theme.tokens.PrimitiveSpacing
import kotlinx.coroutines.cancel

/**
 * 开发者选项屏幕（仅 debug 构建有意义）。
 *
 * 四 Tab 架构：性能 / 调试 / 监控 / 数据。
 * 每个 Tab 独立 LazyColumn，状态各自管理。
 */
@Composable
fun DevOptionsScreen(
    onNavigateBack: () -> Unit,
    onOpenLogViewer: () -> Unit = {},
    viewModel: DevOptionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val toggles by viewModel.toggles.collectAsStateWithLifecycle()
    val kvEntries by viewModel.kvEntries.collectAsStateWithLifecycle()
    val memory by viewModel.memory.collectAsStateWithLifecycle()

    // 启动阶段快照
    val startupStages = remember { com.mini.me_core.core.performance.StartupTracer.snapshot() }

    // 崩溃历史
    var crashReports by remember {
        mutableStateOf(com.mini.me_core.core.performance.CrashReporter.listReports())
    }
    var crashDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showClearCrashConfirm by remember { mutableStateOf(false) }

    // ANR 监控
    var anrRecords by remember {
        mutableStateOf(com.mini.me_core.core.performance.AnrMonitor.snapshot())
    }
    var anrDetail by remember {
        mutableStateOf<com.mini.me_core.core.performance.AnrMonitor.AnrRecord?>(null)
    }
    LaunchedEffect(Unit) {
        com.mini.me_core.core.performance.AnrMonitor.start()
    }

    // 网络监控
    var netRecords by remember {
        mutableStateOf(com.mini.me_core.core.performance.NetworkMonitor.snapshot())
    }
    var netStats by remember {
        mutableStateOf(com.mini.me_core.core.performance.NetworkMonitor.stats())
    }
    var netDetail by remember {
        mutableStateOf<com.mini.me_core.core.performance.NetworkMonitor.RequestRecord?>(null)
    }
    var showClearNetConfirm by remember { mutableStateOf(false) }
    fun refreshNet() {
        netRecords = com.mini.me_core.core.performance.NetworkMonitor.snapshot()
        netStats = com.mini.me_core.core.performance.NetworkMonitor.stats()
    }

    // 保持屏幕常亮
    LaunchedEffect(toggles.keepScreenOn) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (toggles.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // StrictMode：检测主线程磁盘/网络违规
    LaunchedEffect(toggles.strictMode) {
        if (toggles.strictMode) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectAll().penaltyLog().build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectAll().penaltyLog().build()
            )
        } else {
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.LAX)
            android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.LAX)
        }
    }

    // WebView 远程调试
    LaunchedEffect(toggles.webViewDebug) {
        android.webkit.WebView.setWebContentsDebuggingEnabled(toggles.webViewDebug)
    }

    val tabs = listOf(
        stringResource(R.string.dev_tab_performance),
        stringResource(R.string.dev_tab_debug),
        stringResource(R.string.dev_tab_monitor),
        stringResource(R.string.dev_tab_data),
    )
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { m ->
                if (toggles.composeLayoutInspector) {
                    m.border(1.dp, LocalAppTheme.current.colors.error)
                } else m
            },
    ) {
        AppSegmentedControl(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )

        when (selectedTab) {
            0 -> PerformanceTab(
                memory = memory,
                startupStages = startupStages,
            )
            1 -> DebugTab(
                toggles = toggles,
                onSetKeepScreenOn = viewModel::setKeepScreenOn,
                onSetComposeLayoutInspector = viewModel::setComposeLayoutInspector,
                onSetStrictMode = viewModel::setStrictMode,
                onSetWebViewDebug = viewModel::setWebViewDebug,
                onOpenLogViewer = onOpenLogViewer,
            )
            2 -> MonitorTab(
                netRecords = netRecords,
                netStats = netStats,
                onRefreshNet = {
                    refreshNet()
                    anrRecords = com.mini.me_core.core.performance.AnrMonitor.snapshot()
                },
                onClearNet = {
                    showClearNetConfirm = true
                },
                onNetDetail = { netDetail = it },
                crashReports = crashReports,
                onViewCrash = { file ->
                    crashDetail = file.name to
                        com.mini.me_core.core.performance.CrashReporter.readReport(file).orEmpty()
                },
                onClearCrash = { showClearCrashConfirm = true },
                anrRecords = anrRecords,
                onViewAnr = { anrDetail = it },
                onClearAnr = {
                    com.mini.me_core.core.performance.AnrMonitor.clear()
                    anrRecords = emptyList()
                },
            )
            3 -> DataTab(
                kvEntries = kvEntries,
            )
        }
    }

    // 崩溃详情 BottomSheet
    crashDetail?.let { (title, content) ->
        com.mini.me_core.core.theme.components.AppBottomSheet(
            onDismiss = { crashDetail = null },
        ) {
            com.mini.me_core.core.theme.components.AppSheetHeader(
                title = stringResource(R.string.crash_detail),
                onClose = { crashDetail = null },
            )
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            Column(
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppTheme.current.colors.textSecondary,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Sm))
                Text(
                    text = content,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalAppTheme.current.colors.textPrimary,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(
                            android.content.ClipData.newPlainText("crash", content)
                        )
                    }) {
                        Text(stringResource(R.string.crash_copy))
                    }
                }
            }
        }
    }

    // 网络请求详情 BottomSheet
    netDetail?.let { rec ->
        com.mini.me_core.core.theme.components.AppBottomSheet(
            onDismiss = { netDetail = null },
        ) {
            com.mini.me_core.core.theme.components.AppSheetHeader(
                title = stringResource(R.string.net_detail),
                onClose = { netDetail = null },
            )
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            Column(
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
            ) {
                Text(
                    text = stringResource(
                        R.string.net_item, rec.method, rec.url, rec.statusCode, rec.durationMs,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppTheme.current.colors.textPrimary,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Md))
                Text(
                    stringResource(R.string.net_req_headers),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppTheme.current.colors.textSecondary,
                )
                rec.requestHeaders.forEach { (k, v) ->
                    Text("$k: $v", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(PrimitiveSpacing.Md))
                Text(
                    stringResource(R.string.net_resp_headers),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppTheme.current.colors.textSecondary,
                )
                rec.responseHeaders.forEach { (k, v) ->
                    Text("$k: $v", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // ANR 详情 BottomSheet
    anrDetail?.let { record ->
        com.mini.me_core.core.theme.components.AppBottomSheet(
            onDismiss = { anrDetail = null },
        ) {
            com.mini.me_core.core.theme.components.AppSheetHeader(
                title = stringResource(R.string.dev_monitor_anr_detail),
                onClose = { anrDetail = null },
            )
            Spacer(Modifier.height(PrimitiveSpacing.Sm))
            Column(
                modifier = Modifier.padding(horizontal = PrimitiveSpacing.Lg),
            ) {
                Text(
                    text = "${record.formattedTime()}  ·  " +
                        stringResource(R.string.dev_monitor_anr_block_duration, record.blockDurationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalAppTheme.current.colors.textPrimary,
                )
                Spacer(Modifier.height(PrimitiveSpacing.Md))
                Text(
                    text = record.mainThreadStack,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    color = LocalAppTheme.current.colors.textPrimary,
                )
            }
        }
    }

    // 清空网络确认
    if (showClearNetConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.net_clear_confirm_title),
            message = stringResource(R.string.net_clear_confirm_message),
            confirmText = stringResource(R.string.common_confirm),
            cancelText = stringResource(R.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                com.mini.me_core.core.performance.NetworkMonitor.clear()
                refreshNet()
            },
            onDismiss = { showClearNetConfirm = false },
        )
    }

    // 清空崩溃确认
    if (showClearCrashConfirm) {
        AppConfirmDialog(
            title = stringResource(R.string.crash_clear_confirm_title),
            message = stringResource(R.string.crash_clear_confirm_message),
            confirmText = stringResource(R.string.common_confirm),
            cancelText = stringResource(R.string.common_cancel),
            isDestructive = true,
            onConfirm = {
                com.mini.me_core.core.performance.CrashReporter.clearAll()
                crashReports = emptyList()
            },
            onDismiss = { showClearCrashConfirm = false },
        )
    }
}

// ══════════════════════════════════════════════
// 性能 Tab
// ══════════════════════════════════════════════

@Composable
private fun PerformanceTab(
    memory: com.mini.me_core.core.performance.MemoryMonitor.MemorySnapshot,
    startupStages: List<com.mini.me_core.core.performance.StartupTracer.Stage>,
) {
    // FPS 历史（最近 60 秒）
    val fpsHistory = remember { mutableStateListOf<Int>() }
    var currentFps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var frameCount = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(timeNanos: Long) {
                frameCount++
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        try {
            while (true) {
                kotlinx.coroutines.delay(1000)
                currentFps = frameCount
                fpsHistory.add(frameCount)
                if (fpsHistory.size > 60) fpsHistory.removeAt(0)
                frameCount = 0
            }
        } finally {
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    // 内存历史（最近 ~60 秒，2s 采样 = 30 点）
    val memHistory = remember { mutableStateListOf<Long>() }
    LaunchedEffect(memory.usedHeapMb) {
        memHistory.add(memory.usedHeapMb)
        if (memHistory.size > 30) memHistory.removeAt(0)
    }

    val colors = LocalAppTheme.current.colors
    val avgFps = if (fpsHistory.isNotEmpty()) fpsHistory.average().toInt() else 0
    val minFps = if (fpsHistory.isNotEmpty()) fpsHistory.minOrNull() ?: 0 else 0
    val fpsColor = when {
        currentFps < 30 -> colors.error
        currentFps < 50 -> colors.warning
        else -> colors.success
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // FPS 概览 + 曲线
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_perf_fps_chart),
                icon = Icons.Rounded.Speed,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_current_fps),
                            value = currentFps.toString(),
                            valueColor = fpsColor,
                        )
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_avg_fps),
                            value = avgFps.toString(),
                        )
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_min_fps),
                            value = minFps.toString(),
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    LineChart(
                        data = fpsHistory.map { it.toFloat() },
                        lineColor = fpsColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    )
                }
            }
        }

        // 内存曲线 + 详情
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_perf_memory_chart),
                icon = Icons.Rounded.Memory,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_used_heap),
                            value = "${memory.usedHeapMb}",
                            unit = "MB",
                        )
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_max_heap),
                            value = "${memory.maxHeapMb}",
                            unit = "MB",
                        )
                        PerfMetric(
                            label = stringResource(R.string.dev_perf_avail_sys),
                            value = "${memory.availMemMb}",
                            unit = "MB",
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    LineChart(
                        data = memHistory.map { it.toFloat() },
                        lineColor = colors.brandPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    )
                    if (memory.highPressure) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(R.string.dev_options_memory_pressure_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                }
            }
        }

        // 启动耗时（可视化进度条）
        if (startupStages.isNotEmpty()) {
            item {
                val total = startupStages.last().elapsedMs
                AppSectionHeader(
                    title = stringResource(R.string.dev_options_startup),
                    icon = Icons.Rounded.Speed,
                )
                AppCard {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(R.string.dev_options_startup_total, total),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.brandPrimary,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        startupStages.forEach { s ->
                            val progress = if (total > 0) s.deltaMs.toFloat() / total else 0f
                            Text(
                                text = "${s.label}  ${s.deltaMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = PrimitiveSpacing.Xxs),
                                color = colors.brandPrimary,
                                trackColor = colors.surfaceSunken,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Canvas 自绘折线图，不引入第三方图表库。 */
@Composable
private fun LineChart(
    data: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = LocalAppTheme.current.colors.surfaceSunken
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxY = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minY = 0f
        val rangeY = maxY - minY
        val stepX = size.width / (data.size - 1)

        // 背景网格线
        drawRect(color = surfaceColor, size = size)

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - minY) / rangeY * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun PerfMetric(
    label: String,
    value: String,
    unit: String = "",
    valueColor: Color = LocalAppTheme.current.colors.brandPrimary,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (unit.isNotEmpty()) "$value $unit" else value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppTheme.current.colors.textSecondary,
        )
    }
}

// ══════════════════════════════════════════════
// 调试 Tab
// ══════════════════════════════════════════════

@Composable
private fun DebugTab(
    toggles: DevOptionsViewModel.DevToggles,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetComposeLayoutInspector: (Boolean) -> Unit,
    onSetStrictMode: (Boolean) -> Unit,
    onSetWebViewDebug: (Boolean) -> Unit,
    onOpenLogViewer: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 系统类
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_debug_system_group),
                icon = Icons.Rounded.Build,
            )
            AppCard {
                AppListItem(
                    icon = Icons.Rounded.Lightbulb,
                    title = stringResource(R.string.dev_options_keep_screen_on),
                    subtitle = stringResource(R.string.dev_keep_screen_on_subtitle),
                    checked = toggles.keepScreenOn,
                    onCheckedChange = onSetKeepScreenOn,
                )
                AppListItem(
                    icon = Icons.Rounded.Build,
                    title = stringResource(R.string.dev_debug_strict_mode),
                    subtitle = stringResource(R.string.dev_debug_strict_mode_subtitle),
                    checked = toggles.strictMode,
                    onCheckedChange = onSetStrictMode,
                    showDivider = false,
                )
            }
        }

        // 显示类
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_debug_display_group),
                icon = Icons.Rounded.Memory,
            )
            AppCard {
                AppListItem(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.dev_debug_compose_layout),
                    subtitle = stringResource(R.string.dev_debug_compose_layout_subtitle),
                    checked = toggles.composeLayoutInspector,
                    onCheckedChange = onSetComposeLayoutInspector,
                    showDivider = false,
                )
            }
        }

        // 网络类
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_debug_network_group),
                icon = Icons.Rounded.Wifi,
            )
            AppCard {
                AppListItem(
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.dev_debug_webview_debug),
                    subtitle = stringResource(R.string.dev_debug_webview_debug_subtitle),
                    checked = toggles.webViewDebug,
                    onCheckedChange = onSetWebViewDebug,
                    showDivider = false,
                )
            }
        }

        // 日志查看器
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_options_log_viewer),
                icon = Icons.AutoMirrored.Rounded.Article,
            )
            AppCard {
                AppListItem(
                    icon = Icons.AutoMirrored.Rounded.Article,
                    title = stringResource(R.string.dev_options_log_viewer),
                    onViewClick = onOpenLogViewer,
                    showDivider = false,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════
// 监控 Tab
// ══════════════════════════════════════════════

@Composable
private fun MonitorTab(
    netRecords: List<com.mini.me_core.core.performance.NetworkMonitor.RequestRecord>,
    netStats: com.mini.me_core.core.performance.NetworkMonitor.Stats,
    onRefreshNet: () -> Unit,
    onClearNet: () -> Unit,
    onNetDetail: (com.mini.me_core.core.performance.NetworkMonitor.RequestRecord) -> Unit,
    crashReports: List<java.io.File>,
    onViewCrash: (java.io.File) -> Unit,
    onClearCrash: () -> Unit,
    anrRecords: List<com.mini.me_core.core.performance.AnrMonitor.AnrRecord>,
    onViewAnr: (com.mini.me_core.core.performance.AnrMonitor.AnrRecord) -> Unit,
    onClearAnr: () -> Unit,
) {
    val colors = LocalAppTheme.current.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ── 网络监控 ──
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_options_network_monitor),
                icon = Icons.Rounded.Wifi,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    if (netRecords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dev_options_network_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        // 统计卡片行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatMini(stringResource(R.string.net_total_requests), netStats.total.toString())
                            StatMini(stringResource(R.string.net_success_rate), "${netStats.successRate}%")
                            StatMini(stringResource(R.string.net_avg_duration), "${netStats.avgDurationMs}ms")
                            StatMini(stringResource(R.string.net_slowest_req),
                                netStats.slowestTop5.firstOrNull()?.let { "${it.durationMs}ms" } ?: "-")
                        }
                        Spacer(Modifier.height(Spacing.md))
                        // 请求列表
                        netRecords.take(20).forEach { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MethodChip(rec.method)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = rec.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = Spacing.sm),
                                    maxLines = 1,
                                    color = colors.textPrimary,
                                )
                                StatusCodeText(rec.statusCode)
                                Spacer(Modifier.width(Spacing.sm))
                                Text(
                                    text = "${rec.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                                TextButton(onClick = { onNetDetail(rec) }) {
                                    Text(stringResource(R.string.common_open))
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onClearNet) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null,
                                    modifier = Modifier.padding(end = Spacing.xs))
                                Text(stringResource(R.string.net_clear))
                            }
                            TextButton(onClick = onRefreshNet) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null,
                                    modifier = Modifier.padding(end = Spacing.xs))
                                Text(stringResource(R.string.net_refresh))
                            }
                        }
                    }
                }
            }
        }

        // ── 崩溃历史 ──
        item {
            AppSectionHeader(
                title = stringResource(R.string.crash_history),
                icon = Icons.Rounded.BugReport,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    if (crashReports.isEmpty()) {
                        Text(
                            text = stringResource(R.string.crash_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        crashReports.forEach { file ->
                            val raw = com.mini.me_core.core.performance.CrashReporter.readReport(file).orEmpty()
                            val exceptionLine = raw.lineSequence()
                                .firstOrNull { it.startsWith("exception=") }
                                ?.removePrefix("exception=").orEmpty()
                            val exceptionType = exceptionLine.substringBefore(":").substringAfterLast(".")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exceptionType.ifEmpty { file.name },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = file.name.removePrefix("crash-").removeSuffix(".txt"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                TextButton(onClick = { onViewCrash(file) }) {
                                    Text(stringResource(R.string.crash_view))
                                }
                            }
                        }
                        HorizontalDivider()
                        TextButton(onClick = onClearCrash) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null,
                                modifier = Modifier.padding(end = Spacing.xs))
                            Text(stringResource(R.string.crash_clear))
                        }
                    }
                }
            }
        }

        // ── ANR 监控 ──
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_monitor_anr),
                icon = Icons.Rounded.BugReport,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    if (anrRecords.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dev_monitor_anr_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        anrRecords.forEach { record ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewAnr(record) }
                                    .padding(vertical = Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.formattedTime(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.dev_monitor_anr_block_duration,
                                            record.blockDurationMs,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.error,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        TextButton(onClick = onClearAnr) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null,
                                modifier = Modifier.padding(end = Spacing.xs))
                            Text(stringResource(R.string.common_clear))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = LocalAppTheme.current.colors.brandPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = LocalAppTheme.current.colors.textSecondary)
    }
}

@Composable
private fun MethodChip(method: String) {
    val colors = LocalAppTheme.current.colors
    val bg = when (method.uppercase()) {
        "GET" -> colors.successContainer
        "POST" -> colors.brandContainer
        "PUT" -> colors.warningContainer
        "DELETE" -> colors.errorContainer
        else -> colors.surfaceSunken
    }
    val fg = when (method.uppercase()) {
        "GET" -> colors.onSuccessContainer
        "POST" -> colors.onBrandContainer
        "PUT" -> colors.onWarningContainer
        "DELETE" -> colors.onErrorContainer
        else -> colors.textSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(PrimitiveSpacing.Xxs))
            .background(bg)
            .padding(horizontal = PrimitiveSpacing.Sm, vertical = PrimitiveSpacing.Xxs),
    ) {
        Text(method, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun StatusCodeText(statusCode: Int) {
    val colors = LocalAppTheme.current.colors
    val color = when {
        statusCode in 200..399 -> colors.success
        statusCode in 400..499 -> colors.warning
        statusCode >= 500 -> colors.error
        else -> colors.error
    }
    Text("$statusCode", style = MaterialTheme.typography.labelSmall, color = color)
}

// ══════════════════════════════════════════════
// 数据 Tab
// ══════════════════════════════════════════════

@Composable
private fun DataTab(
    kvEntries: List<Pair<String, String>>,
) {
    val context = LocalContext.current
    val colors = LocalAppTheme.current.colors
    var searchQuery by remember { mutableStateOf("") }

    // 过滤
    val filtered = remember(kvEntries, searchQuery) {
        if (searchQuery.isBlank()) kvEntries
        else kvEntries.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    // 按前缀分组
    val grouped = remember(filtered) {
        filtered.groupBy { entry ->
            val idx = entry.first.indexOf('_')
            if (idx > 0) entry.first.substring(0, idx) else "other"
        }.toSortedMap()
    }

    // 数据库文件信息
    val dbInfo = remember {
        context.databaseList().filter { it.endsWith(".db") }.map { dbName ->
            val dbFile = context.getDatabasePath(dbName)
            val sizeKb = if (dbFile.exists()) dbFile.length() / 1024 else 0L
            dbName to sizeKb
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 搜索框
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_options_kv_store),
                icon = Icons.Rounded.Storage,
            )
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.dev_data_kv_search_hint)) },
                singleLine = true,
            )
        }

        // 数据库信息
        item {
            AppSectionHeader(
                title = stringResource(R.string.dev_data_db_info),
                icon = Icons.Rounded.Storage,
            )
            AppCard {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    dbInfo.forEach { (name, sizeKb) ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.dev_data_db_size) + ": ${sizeKb} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Text(
                        text = stringResource(R.string.dev_data_db_todo),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        // 分组 KV 列表
        grouped.forEach { (prefix, entries) ->
            stickyHeader(key = "header_$prefix") {
                AppSectionHeader(
                    title = "$prefix (${entries.size})",
                    icon = Icons.Rounded.Storage,
                )
            }
            items(entries, key = { it.first }) { (key, value) ->
                var expanded by remember { mutableStateOf(false) }
                AppCard(variant = AppCardVariant.Outlined) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (value.length > 100) expanded = !expanded },
                                onLongClick = {
                                    val cm = context.getSystemService(
                                        android.content.ClipboardManager::class.java
                                    )
                                    cm?.setPrimaryClip(
                                        android.content.ClipData.newPlainText("kv_value", value)
                                    )
                                },
                            )
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    ) {
                        Text(
                            key,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dev_options_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
        }
    }
}
