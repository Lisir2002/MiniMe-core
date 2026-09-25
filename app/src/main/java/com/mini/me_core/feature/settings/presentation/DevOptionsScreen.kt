package com.mini.me_core.feature.settings.presentation

import android.app.Activity
import android.view.Choreographer
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.R
import kotlin.math.max

/**
 * F5.6 开发者选项屏幕（仅 debug 构建有意义）。
 *
 * - 性能监控卡片：FPS（Choreographer 统计 1 秒帧数）+ 内存（Runtime 已用 MB）
 * - 调试选项开关：保持屏幕常亮 / 强制深色模式 / 模拟慢速网络 / 布局边界 / GPU 过度绘制
 * - 日志查看器入口、设置存储浏览（只读）、网络监控空态
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsStateWithLifecycle()
    val batterySaverActive by viewModel.batterySaverActive.collectAsStateWithLifecycle()

    // F6.1：冷启动各阶段快照（release 下为空列表）。
    val startupStages = remember { com.mini.me_core.core.performance.StartupTracer.snapshot() }

    // F6.4：崩溃历史列表 + 当前查看的详情。
    var crashReports by remember { mutableStateOf(com.mini.me_core.core.performance.CrashReporter.listReports()) }
    var crashDetail by remember { mutableStateOf<Pair<String, String>?>(null) }

    // F6.6：网络监控记录 + 统计 + 当前查看的请求详情。
    var netRecords by remember { mutableStateOf(com.mini.me_core.core.performance.NetworkMonitor.snapshot()) }
    var netStats by remember { mutableStateOf(com.mini.me_core.core.performance.NetworkMonitor.stats()) }
    var netDetail by remember { mutableStateOf<com.mini.me_core.core.performance.NetworkMonitor.RequestRecord?>(null) }
    fun refreshNet() {
        netRecords = com.mini.me_core.core.performance.NetworkMonitor.snapshot()
        netStats = com.mini.me_core.core.performance.NetworkMonitor.stats()
    }

    // FPS 统计：每秒刷新一次
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frameCount = 0
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(timeNanos: Long) {
                frameCount++
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        Choreographer.getInstance().postFrameCallback(callback)
        // 每秒采样一次
        while (true) {
            kotlinx.coroutines.delay(1000)
            fps = frameCount
            frameCount = 0
        }
    }

    // 保持屏幕常亮：作用于 Activity Window
    LaunchedEffect(toggles.keepScreenOn) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (toggles.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val runtime = Runtime.getRuntime()
    val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dev_options_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 性能监控
            item {
                SectionHeader(stringResource(R.string.dev_options_perf))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        PerfMetric(
                            label = stringResource(R.string.dev_options_fps),
                            value = fps.toString(),
                        )
                        PerfMetric(
                            label = stringResource(R.string.dev_options_memory),
                            value = "${usedMemMb} MB",
                        )
                    }
                }
            }

            // F6.1 启动耗时追踪
            if (startupStages.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(R.string.dev_options_startup))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.dev_options_startup_total,
                                    startupStages.last().elapsedMs,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            startupStages.forEach { s ->
                                Text(
                                    text = stringResource(
                                        R.string.dev_options_startup_stage,
                                        s.label, s.elapsedMs, s.deltaMs,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // F6.2 内存详情
            item {
                SectionHeader(stringResource(R.string.dev_options_memory_detail))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                R.string.dev_options_memory_heap,
                                memory.usedHeapMb, memory.maxHeapMb,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.dev_options_memory_system,
                                memory.availMemMb, memory.totalMemMb,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (memory.highPressure) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.dev_options_memory_pressure_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // 调试选项
            item {
                SectionHeader(stringResource(R.string.dev_options_debug_toggles))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    ToggleRow(
                        title = stringResource(R.string.dev_options_keep_screen_on),
                        checked = toggles.keepScreenOn,
                        onChange = viewModel::setKeepScreenOn,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = stringResource(R.string.dev_options_force_dark),
                        checked = toggles.forceDark,
                        onChange = viewModel::setForceDark,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = stringResource(R.string.dev_options_slow_network),
                        checked = toggles.slowNetwork,
                        onChange = viewModel::setSlowNetwork,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = stringResource(R.string.dev_options_layout_bounds),
                        checked = toggles.layoutBounds,
                        onChange = viewModel::setLayoutBounds,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = stringResource(R.string.dev_options_gpu_overdraw),
                        checked = toggles.gpuOverdraw,
                        onChange = viewModel::setGpuOverdraw,
                    )
                    HorizontalDivider()
                    ToggleRow(
                        title = stringResource(R.string.dev_options_battery_saver) +
                            if (batterySaverActive) "\n" + stringResource(R.string.dev_options_battery_saver_active) else "",
                        checked = batterySaverEnabled,
                        onChange = viewModel::setBatterySaverEnabled,
                    )
                }
            }

            // 日志查看器入口
            item {
                SectionHeader(stringResource(R.string.dev_options_log_viewer))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.dev_options_log_viewer),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButtonLike(onClick = onOpenLogViewer) {
                            Text(stringResource(R.string.common_open))
                        }
                    }
                }
            }

            // F6.6 网络监控
            item {
                SectionHeader(stringResource(R.string.dev_options_network_monitor))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (netRecords.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dev_options_network_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    R.string.net_stats,
                                    netStats.total, netStats.successRate, netStats.avgDurationMs,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            netRecords.take(20).forEach { rec ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.net_item,
                                            rec.method, rec.url, rec.statusCode, rec.durationMs,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                    )
                                    androidx.compose.material3.TextButton(onClick = { netDetail = rec }) {
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
                                androidx.compose.material3.TextButton(onClick = {
                                    com.mini.me_core.core.performance.NetworkMonitor.clear()
                                    refreshNet()
                                }) {
                                    Text(stringResource(R.string.net_clear))
                                }
                                androidx.compose.material3.TextButton(onClick = { refreshNet() }) {
                                    Text(stringResource(R.string.common_open))
                                }
                            }
                        }
                    }
                }
            }

            // F6.4 崩溃历史
            item {
                SectionHeader(stringResource(R.string.crash_history))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (crashReports.isEmpty()) {
                            Text(
                                text = stringResource(R.string.crash_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            crashReports.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = file.name.removePrefix("crash-").removeSuffix(".txt"),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 4.dp),
                                    )
                                    androidx.compose.material3.TextButton(onClick = {
                                        crashDetail = file.name to com.mini.me_core.core.performance.CrashReporter.readReport(file).orEmpty()
                                    }) {
                                        Text(stringResource(R.string.crash_view))
                                    }
                                }
                            }
                            HorizontalDivider()
                            androidx.compose.material3.TextButton(onClick = {
                                com.mini.me_core.core.performance.CrashReporter.clearAll()
                                crashReports = emptyList()
                            }) {
                                Text(stringResource(R.string.crash_clear))
                            }
                        }
                    }
                }
            }

            // 数据库/设置存储浏览
            item {
                SectionHeader(stringResource(R.string.dev_options_db_viewer))
                Text(
                    text = stringResource(R.string.dev_options_kv_store),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(kvEntries) { (key, value) ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(key, style = MaterialTheme.typography.bodySmall)
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // F6.4 崩溃详情弹窗：展示全文，支持复制到剪贴板。
    crashDetail?.let { (title, content) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { crashDetail = null },
            title = { Text(stringResource(R.string.crash_detail)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(
                        android.content.ClipData.newPlainText("crash", content)
                    )
                }) {
                    Text(stringResource(R.string.crash_copied))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { crashDetail = null }) {
                    Text(stringResource(R.string.crash_dismiss))
                }
            },
        )
    }

    // F6.6 网络请求详情弹窗：展示方法/URL/状态/耗时与脱敏请求/响应头。
    netDetail?.let { rec ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { netDetail = null },
            title = { Text(stringResource(R.string.net_detail)) },
            text = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.net_item, rec.method, rec.url, rec.statusCode, rec.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.net_req_headers), style = MaterialTheme.typography.labelMedium)
                    rec.requestHeaders.forEach { (k, v) ->
                        Text("$k: $v", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.net_resp_headers), style = MaterialTheme.typography.labelMedium)
                    rec.responseHeaders.forEach { (k, v) ->
                        Text("$k: $v", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { netDetail = null }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun PerfMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextButtonLike(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) { content() }
}
