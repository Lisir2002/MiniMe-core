package com.mini.me_core.feature.proxy.presentation.component
import com.mini.me_core.core.theme.tokens.LocalCornerRadius
import androidx.compose.ui.res.stringResource
import com.mini.me_core.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mini.me_core.core.theme.components.AppTopAppBar
import com.mini.me_core.core.theme.Radius
import com.mini.me_core.core.theme.Spacing
import com.mini.me_core.core.ui.rememberPersistentLazyListState
import com.mini.me_core.feature.proxy.domain.ProxySubscription
import com.mini.me_core.feature.proxy.presentation.ProxyPreview
import com.mini.me_core.feature.proxy.presentation.ProxyViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入来源模式。 */
private enum class ImportMode { SUBSCRIPTION, MANUAL, FILE, DIRECT }

/**
 * 网络代理配置/导入页：管理已播种 profile、导入订阅/手动/文件、预检、开关。
 * 日常切节点/测速/监控由模型驱动 `network_proxy` 工具负责，本页只做播种 + 开关（§11）。
 */
@Composable
fun ProxyConfigScreen(
    viewModel: ProxyViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNodes: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val aiHostsDirect by viewModel.aiHostsDirect.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val diagnostic by viewModel.diagnostic.collectAsStateWithLifecycle()
    val trafficToday by viewModel.trafficToday.collectAsStateWithLifecycle()
    val trafficWeek by viewModel.trafficWeek.collectAsStateWithLifecycle()
    val connLogEnabled by viewModel.connLogEnabled.collectAsStateWithLifecycle()
    val connLogs by viewModel.connLogs.collectAsStateWithLifecycle()
    val runtime by viewModel.runtime.collectAsStateWithLifecycle()

    val activeProfileName = remember(profiles, activeProfileId) {
        val id = activeProfileId ?: return@remember null
        profiles.firstOrNull { it.id == id }?.name?.ifBlank { id }
    }

    var tab by remember { mutableStateOf(0) }   // 0=概览 1=订阅 2=高级
    var deletingProfile by remember { mutableStateOf<ProxySubscription?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            if (msg.isNotBlank()) snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshTrafficUsage() }

    var showImport by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.ui______d3ec5010),
                onNavigateBack = onNavigateBack,
                navigationIcon = Icons.AutoMirrored.Rounded.ArrowBack,
                navigationContentDescription = stringResource(R.string.ui____5f411223)
            )
        }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 3 Tab：概览 / 订阅 / 高级
                ProxyTabBar(tab = tab, onTabChange = { tab = it })

                val listState = rememberPersistentLazyListState("proxy_config_$tab")
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // ── Tab 0 · 概览 ──
                    if (tab == 0) {
                        item {
                            MasterToggle(
                                enabled = enabled,
                                reachable = runtime.controllerReachable,
                                activeProfileName = activeProfileName,
                                port = viewModel.proxyPort,
                                onToggle = viewModel::toggleEnabled
                            )
                        }
                        if (enabled) {
                            item {
                                DiagnosticCard(
                                    diagnostic = diagnostic,
                                    onRetest = viewModel::retestConnectivity,
                                    todayTotal = trafficToday.upBytes + trafficToday.downBytes
                                )
                            }
                        }
                        // 进入节点管理独立页
                        item {
                            NodesEntryCard(
                                enabled = enabled,
                                profileCount = profiles.size,
                                onClick = onNavigateToNodes
                            )
                        }
                    }

                    // ── Tab 1 · 订阅 ──
                    if (tab == 1) {
                        item {
                            Button(
                                onClick = { showImport = !showImport },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (showImport) "收起导入" else "导入配置（订阅 / 手动 / 直接代理）") }
                        }

                        if (showImport) {
                            item {
                                ImportEditor(
                                    preview = preview,
                                    onPreview = viewModel::runPreview,
                                    onCommit = { name, kind, secret, enableNow ->
                                        viewModel.commitProfile(name, kind, secret, enableNow)
                                        showImport = false
                                    }
                                )
                            }
                        }

                        item { OverrideCard() }

                        if (profiles.isEmpty()) {
                            item {
                                Text(
                                    text = "还没有已保存的配置。先「导入配置」播种一次，之后模型可用 network_proxy 接管启用/切节点/测速。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        item {
                            Text(
                                text = "已保存配置（${profiles.size}）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // ── Tab 2 · 高级 ──
                    if (tab == 2) {
                        item { SectionHeader("流量统计") }
                        item {
                            TrafficUsageCard(today = trafficToday, week = trafficWeek, port = viewModel.proxyPort)
                        }
                        item { SectionHeader("连接审计") }
                        item {
                            ConnLogCard(
                                enabled = connLogEnabled,
                                logs = connLogs,
                                onToggle = viewModel::setConnLogEnabled,
                                onRefresh = { viewModel.refreshConnLogs() }
                            )
                        }
                        item { SectionHeader("分流与监控") }
                        item {
                            AiHostsDirectToggle(
                                checked = aiHostsDirect,
                                enabled = enabled,
                                onToggle = viewModel::toggleAiHostsDirect
                            )
                        }
                        item { SectionHeader("系统级代理（TUN）") }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LocalCornerRadius.current.xl),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(Modifier.padding(Spacing.md)) {
                                    Text("TUN 全局模式（暂不可用）",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text("当前为独立 mihomo 子进程，FD 跨进程传递受 CLOEXEC 限制。待内核改为 gomobile AAR 库后支持系统级 VPN 接管。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // profile 列表仅在「订阅」Tab 渲染。
                    if (tab == 1) {
                        items(profiles, key = { it.id }) { p ->
                    ProfileRow(
                        profile = p,
                        isActive = p.id == activeProfileId,
                        onActivate = { viewModel.activate(p.id) },
                        onDelete = { deletingProfile = p },
                        onRefresh = { viewModel.refreshSubscription(p.id) }
                    )
                    }
                }
            }
        }
    }

    // 删除二次确认对话框
    deletingProfile?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("删除配置") },
            text = { Text("确定要删除「${target.name.ifBlank { target.id }}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deletingProfile = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) { Text("取消") }
            }
        )
    }
}

// ─────────────────────────── 3 Tab 切换 ───────────────────────────

@Composable
private fun ProxyTabBar(tab: Int, onTabChange: (Int) -> Unit) {
    val labels = listOf("概览", "订阅", "高级")
    Row(
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(LocalCornerRadius.current.xl)
            )
            .padding(Spacing.xs)
    ) {
        labels.forEachIndexed { idx, label ->
            val selected = tab == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(LocalCornerRadius.current.map(9.dp)))
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else Modifier)
                    .clickable { onTabChange(idx) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────── 区块分组标题 ───────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = 2.dp)
    )
}

/**
 * 全局开关卡片：顶部状态卡。
 * 显示 运行状态灯 + 活跃 profile 名（而非 UUID）+ 实际代理端口。
 */
@Composable
private fun MasterToggle(
    enabled: Boolean,
    reachable: Boolean,
    activeProfileName: String?,
    port: Int,
    onToggle: (Boolean) -> Unit
) {
    val statusColor = when {
        enabled && reachable -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) stringResource(R.string.ui_______4d1f56d6) else stringResource(R.string.ui_______b5fb1ee7),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (!activeProfileName.isNullOrBlank()) {
                        "${activeProfileName} · 127.0.0.1:$port"
                    } else {
                        stringResource(R.string.ui________ae157e74)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** 网络层优化 C5：模型接口直连/代理分流开关。需代理启用态才可切换（直连仅在代理链路下有意义）。 */
@Composable
private fun AiHostsDirectToggle(
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.proxy_ai_hosts_direct),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.proxy_ai_hosts_direct_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

/** 节点管理独立页入口卡：分组 / 节点 / 状态 / 测速 / 切换 一站式。 */
@Composable
private fun NodesEntryCard(
    enabled: Boolean,
    profileCount: Int,
    onClick: () -> Unit
) {
    val enabledClick = profileCount > 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabledClick, onClick = onClick),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(LocalCornerRadius.current.xl)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui______b26d228a),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        profileCount == 0 -> stringResource(R.string.ui_______98d7e010)
                        enabled -> stringResource(R.string.ui____d934fb06)
                        else -> stringResource(R.string.ui_______20fa5aa5)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** P1-9：连接诊断交通灯卡片。 */
/** P3-19：连接审计日志卡片（开关 + 最近记录列表）。 */
@Composable
private fun ConnLogCard(
    enabled: Boolean,
    logs: List<com.mini.me_core.datalayer.store.ProxyConnectionEntry>,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("连接日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("审计每个连接的域名/流量/时长（默认关，隐私优先）",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    OutlinedButton(onClick = onRefresh) { Text("刷新") }
                }
                Spacer(Modifier.height(Spacing.xs))
                if (logs.isEmpty()) {
                    Text("暂无结束连接记录", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    logs.take(20).forEach { e ->
                        Text(
                            "${e.host ?: e.ip ?: "?"}:${e.port ?: "?"} · ${e.protocol ?: ""} · " +
                                "↓${formatBytes(e.downBytes)} ↑${formatBytes(e.upBytes)} · ${e.durationMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    diagnostic: com.mini.me_core.feature.proxy.domain.ProxyDiagnosticResult?,
    onRetest: () -> Unit,
    todayTotal: Long? = null,
) {
    val light = diagnostic?.light
    val dotColor = when (light) {
        com.mini.me_core.feature.proxy.domain.TrafficLight.GREEN -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        com.mini.me_core.feature.proxy.domain.TrafficLight.YELLOW -> androidx.compose.ui.graphics.Color(0xFFF9A825)
        com.mini.me_core.feature.proxy.domain.TrafficLight.RED -> androidx.compose.ui.graphics.Color(0xFFC62828)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                drawCircle(dotColor)
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                val title = when (light) {
                    com.mini.me_core.feature.proxy.domain.TrafficLight.GREEN -> "已连接"
                    com.mini.me_core.feature.proxy.domain.TrafficLight.YELLOW -> "部分异常"
                    com.mini.me_core.feature.proxy.domain.TrafficLight.RED -> "连接不通"
                    null -> "未诊断"
                }
                // P2-16：状态行合一：状态 · 延迟 · 今日用量。
                val lat = diagnostic?.outboundLatencyMs
                val summary = buildString {
                    append(title)
                    if (lat != null && light == com.mini.me_core.feature.proxy.domain.TrafficLight.GREEN) append(" · ${lat}ms")
                    todayTotal?.let { append(" · 今日 ${formatBytes(it)}") }
                }
                Text(summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (diagnostic != null) {
                    Text(
                        "进程:${if (diagnostic.processAlive) "✓" else "✗"} 控制面:${if (diagnostic.controllerReachable) "✓" else "✗"} " +
                            "出口:${lat?.let { "${it}ms" } ?: "✗"} DNS:${if (diagnostic.dnsOk) "✓" else "✗"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onRetest) { Text("重测") }
        }
    }
}

/** P1-8：流量用量卡片（今日 / 本周）。 */
@Composable
private fun TrafficUsageCard(
    today: com.mini.me_core.datalayer.store.TrafficUsage,
    week: com.mini.me_core.datalayer.store.TrafficUsage,
    port: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("流量用量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacing.xs))
            Text("今日：↑ ${formatBytes(today.upBytes)}  ↓ ${formatBytes(today.downBytes)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("本周：↑ ${formatBytes(week.upBytes)}  ↓ ${formatBytes(week.downBytes)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            val portNote = if (port != 7890) "代理端口：$port（7890 被占用）" else "代理端口：$port"
            Text(portNote, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ImportEditor(
    preview: ProxyPreview?,    onPreview: (String?, String?) -> Unit,
    onCommit: (String, String, String, Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(ImportMode.SUBSCRIPTION) }
    var nameField by remember { mutableStateOf("") }
    var urlField by remember { mutableStateOf("") }
    var yamlField by remember { mutableStateOf("") }
    // P2-12：直接代理表单字段。
    var dpProtocol by remember { mutableStateOf("socks5") }
    var dpHost by remember { mutableStateOf("") }
    var dpPort by remember { mutableStateOf("") }
    var dpUser by remember { mutableStateOf("") }
    var dpPass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                busy = true
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                busy = false
                if (text.isNullOrBlank()) return@launch
                mode = ImportMode.MANUAL
                yamlField = text
                if (nameField.isBlank()) {
                    nameField = (uri.lastPathSegment ?: "").substringAfterLast('/').ifBlank { "file" }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.ui______04522145),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ModeChip(stringResource(R.string.ui____701515e9), selected = mode == ImportMode.SUBSCRIPTION, enabled = !busy) { mode = ImportMode.SUBSCRIPTION }
                ModeChip(stringResource(R.string.ui____601a29b5), selected = mode == ImportMode.MANUAL, enabled = !busy) { mode = ImportMode.MANUAL }
                ModeChip("直接代理", selected = mode == ImportMode.DIRECT, enabled = !busy) { mode = ImportMode.DIRECT }
                ModeChip(stringResource(R.string.ui_______d9a6706f), selected = false, enabled = !busy) { filePicker.launch("*/*") }
            }

            Spacer(Modifier.height(Spacing.sm))

            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                label = { Text(stringResource(R.string.ui____8d9d1723)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(Spacing.sm))

            when (mode) {
                ImportMode.SUBSCRIPTION -> {
                    OutlinedTextField(
                        value = urlField,
                        onValueChange = { urlField = it },
                        label = { Text(stringResource(R.string.ui____3208b62d)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(
                        onClick = { onPreview(urlField.trim(), null) },
                        enabled = urlField.isNotBlank() && !busy
                    ) { Text(stringResource(R.string.ui____bb872a0c)) }
                    SaveHint()
                }
                ImportMode.MANUAL -> {
                    OutlinedTextField(
                        value = yamlField,
                        onValueChange = { yamlField = it },
                        label = { Text(stringResource(R.string.ui______f388b5eb)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(
                        onClick = { onPreview(null, yamlField) },
                        enabled = yamlField.isNotBlank() && !busy
                    ) { Text(stringResource(R.string.ui____bb872a0c_2)) }
                    SaveHint()
                }
                ImportMode.FILE -> {
                    Text(
                        text = stringResource(R.string.ui_____24992b8d),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ImportMode.DIRECT -> {
                    // 协议选择
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        listOf("socks5", "http").forEach { p ->
                            FilterChip(selected = dpProtocol == p, onClick = { dpProtocol = p },
                                label = { Text(p, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = dpHost, onValueChange = { dpHost = it },
                        label = { Text("主机") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = dpPort, onValueChange = { dpPort = it },
                        label = { Text("端口") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = dpUser, onValueChange = { dpUser = it },
                        label = { Text("用户名（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(value = dpPass, onValueChange = { dpPass = it },
                        label = { Text("密码（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(
                        onClick = { onPreview(null, buildDirectYaml(dpProtocol, dpHost, dpPort, dpUser, dpPass)) },
                        enabled = dpHost.isNotBlank() && dpPort.toIntOrNull() != null && !busy
                    ) { Text(stringResource(R.string.ui____bb872a0c_2)) }
                    SaveHint()
                }
            }

            preview?.let { p ->
                Spacer(Modifier.height(Spacing.sm))
                PreviewPanel(preview = p)
            }

            preview?.takeIf { it.ok }?.let { p ->
                Spacer(Modifier.height(Spacing.md))
                // P2-12：按当前导入模式决定 kind/secret；direct 存合成后的最小 YAML。
                val kind = when (mode) {
                    ImportMode.SUBSCRIPTION -> ProxySubscription.KIND_SUBSCRIPTION
                    ImportMode.DIRECT -> ProxySubscription.KIND_DIRECT
                    else -> ProxySubscription.KIND_MANUAL
                }
                val secret = when (mode) {
                    ImportMode.SUBSCRIPTION -> urlField.trim()
                    ImportMode.DIRECT -> buildDirectYaml(dpProtocol, dpHost, dpPort, dpUser, dpPass)
                    else -> yamlField
                }
                val resolvedSource = !p.resolvedYaml.isBlank()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        enabled = resolvedSource && !busy,
                        onClick = { onCommit(nameField, kind, secret, false) }
                    ) { Text(stringResource(R.string.ui_____ee99388d)) }
                    Button(
                        enabled = resolvedSource && !busy,
                        onClick = { onCommit(nameField, kind, secret, true) }
                    ) { Text(stringResource(R.string.ui_______3eb4be1b)) }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = label,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/** 预检按钮下方的保存提示：避免用户找不到只在预检通过后出现的保存按钮。 */
@Composable
private fun SaveHint() {
    Text(
        text = stringResource(R.string.ui___________717dcdaa),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PreviewPanel(preview: ProxyPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                (if (preview.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error).copy(alpha = 0.12f),
                RoundedCornerShape(LocalCornerRadius.current.md)
            )
            .padding(Spacing.sm)
    ) {
        Text(
            text = preview.summary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        preview.warnings.forEach { w ->
            Text(
                text = w,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun OverrideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            // 标题行：图标 + 标题 + 状态标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(LocalCornerRadius.current.sm)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.ui_______79cf38fa),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "系统锁定",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            RoundedCornerShape(LocalCornerRadius.current.sm)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            // 简短说明
            Text(
                text = "这是应用内置的默认代理配置，用于保证核心功能在任何情况下都能正常运行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            // 关键特性列表
            val features = listOf(
                "模型不可修改" to "由系统自动生成和维护，用户无法编辑",
                "优先于用户配置" to "当所有自定义配置均不可用时自动兜底",
                "内网直连" to "局域网流量始终走 DIRECT，不受代理规则影响",
                "本地控制面" to "127.0.0.1:9090 控制端口，随机会话密钥"
            )
            features.forEach { (title, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProxySubscription,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() },
        shape = RoundedCornerShape(LocalCornerRadius.current.xl),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧选中指示器
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            // 中间信息区
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name.ifBlank { profile.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.ui____fe32def4),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when (profile.kind) {
                        ProxySubscription.KIND_SUBSCRIPTION -> stringResource(R.string.ui____adfb869d)
                        ProxySubscription.KIND_MANUAL -> stringResource(R.string.ui____9c4530f5)
                        ProxySubscription.KIND_DIRECT -> "直接代理"
                        else -> "来源：${profile.kind}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (profile.kind == ProxySubscription.KIND_SUBSCRIPTION && profile.updatedAt > 0L) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "上次更新：${relativeAgo(profile.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            // 右侧按钮区
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Button(
                    onClick = onActivate,
                    enabled = !isActive,
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        if (isActive) stringResource(R.string.ui____fe32def4_2)
                        else stringResource(R.string.ui______f67c4924),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (profile.kind == ProxySubscription.KIND_SUBSCRIPTION) {
                        OutlinedButton(
                            onClick = onRefresh,
                            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("更新", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.ui____2f4aaddd), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** P1-7：把时间戳格式化为「x分钟前/x小时前/x天前」。 */
private fun relativeAgo(ts: Long): String {    val diff = System.currentTimeMillis() - ts
    if (diff < 60_000L) return "刚刚"
    val minutes = diff / 60_000L
    if (minutes < 60) return "${minutes}分钟前"
    val hours = minutes / 60
    if (hours < 24) return "${hours}小时前"
    val days = hours / 24
    return "${days}天前"
}

/** P1-8：字节格式化为人类可读。 */
private fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b >= 1024.0 * 1024 * 1024 -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
        b >= 1024.0 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
        b >= 1024 -> "%.1f KB".format(b / 1024.0)
        else -> "${bytes} B"
    }
}

/** P2-12：由 socks5/http 单跳参数合成最小 Clash YAML。 */
private fun buildDirectYaml(protocol: String, host: String, port: String, user: String, pass: String): String {
    val sb = StringBuilder()
    sb.appendLine("proxies:")
    sb.appendLine("  - name: direct-proxy")
    sb.appendLine("    type: $protocol")
    sb.appendLine("    server: \"${host.trim()}\"")
    sb.appendLine("    port: ${port.trim().toIntOrNull() ?: 0}")
    if (user.isNotBlank()) sb.appendLine("    username: \"${user.trim()}\"")
    if (pass.isNotBlank()) sb.appendLine("    password: \"${pass.trim()}\"")
    sb.appendLine("proxy-groups:")
    sb.appendLine("  - name: PROXY")
    sb.appendLine("    type: select")
    sb.appendLine("    proxies: [direct-proxy]")
    sb.appendLine("rules:")
    sb.appendLine("  - MATCH,PROXY")
    return sb.toString()
}
