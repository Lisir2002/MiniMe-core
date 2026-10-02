package com.mini.me_core.feature.proxy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import com.mini.me_core.datalayer.store.ProxyTrafficRepository
import com.mini.me_core.datalayer.store.ProxyConnectionLogRepository
import com.mini.me_core.datalayer.store.ProxyConnectionEntry
import com.mini.me_core.datalayer.store.TrafficUsage
import com.mini.me_core.feature.proxy.domain.ClashConfigSummary
import com.mini.me_core.feature.proxy.domain.ClashProxiesSnapshot
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.proxy.domain.ProxyConnectivityTester
import com.mini.me_core.feature.proxy.domain.ProxyNodeHealthMonitor
import com.mini.me_core.feature.proxy.domain.ProxyConnectionLogger
import com.mini.me_core.feature.proxy.domain.ProxyTrafficSampler
import com.mini.me_core.feature.proxy.domain.ProxyDiagnosticResult
import com.mini.me_core.feature.proxy.domain.ProxyRuntimeState
import com.mini.me_core.feature.proxy.domain.ProxySubscription
import com.mini.me_core.feature.proxy.domain.ProxyTraffic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** 导入配置的预检结果（仅展示，不落盘）。[resolvedYaml] 供保存时作为来源。 */
data class ProxyPreview(
    val ok: Boolean,
    val summary: String,
    val nodeCount: Int = 0,
    val groupCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val resolvedYaml: String = "",
)

/**
 * 某 profile 展开后的节点列表视图（对齐 Clash 的「节点列表 + 测速状态」）。
 * [latencies]：节点名 → 毫秒延迟；值为 null 表示已测但超时/失败；不含该 key 表示未测。
 */
data class ProfileNodesView(
    val profileId: String,
    val profileName: String,
    val summary: ClashConfigSummary,
    val latencies: Map<String, Long?> = emptyMap(),
    val loading: Boolean = false,
    val testing: Boolean = false,
    val error: String? = null,
)

/**
 * 展开区「分组 · 流量」视图：分组树 + 选中项 + 实时流量。
 * 与 `network_proxy list_proxies / flow` 共用 manager 的 REST(/proxies) 与 WS(/traffic) 数据源。
 */
data class ProxyGroupsView(
    val profileId: String,
    val snapshot: ClashProxiesSnapshot? = null,
    val traffic: ProxyTraffic? = null,
    val running: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * 网络代理配置页的 ViewModel：对接 [ProxySettingsRepository] 与 [ClashProxyManager]。
 *
 * 只做 App 侧「播种 + 开关」的 UI 交互；日常切换/测速/监控仍由模型驱动 `network_proxy` 工具完成
 * （《网络代理 v1.0》§3 / §11）。页面与工具落到同一运行链路（manager/repository），避免两套实现。
 */
@HiltViewModel
class ProxyViewModel @Inject constructor(
    private val repository: ProxySettingsRepository,
    private val manager: ClashProxyManager,
    private val connectivityTester: ProxyConnectivityTester,
    private val trafficSampler: ProxyTrafficSampler,
    private val trafficRepo: ProxyTrafficRepository,
    private val nodeHealthMonitor: ProxyNodeHealthMonitor,
    private val connectionLogger: ProxyConnectionLogger,
    private val connLogRepo: ProxyConnectionLogRepository,
) : ViewModel() {

    /** 已播种的订阅/manual/list（脱敏，cipher 不解密返回）。 */
    val profiles: StateFlow<List<ProxySubscription>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全局代理开关。 */
    val enabled: StateFlow<Boolean> = repository.proxyEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 网络层优化 C5：AI 接口直连分流开关（默认关）。 */
    val aiHostsDirect: StateFlow<Boolean> = repository.aiHostsDirectFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 当前活跃 profile id。 */
    val activeProfileId: StateFlow<String?> = repository.activeProfileIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 运行态（含 mode / controller 可达性）。 */
    val runtime: StateFlow<ProxyRuntimeState> = manager.state

    /** P1-9：最近一次连接诊断结果（交通灯数据源）。 */
    val diagnostic: StateFlow<ProxyDiagnosticResult?> = connectivityTester.result

    /** P2-13：节点健康状态（nodeName -> 是否健康）。 */
    val nodeHealth: StateFlow<Map<String, Boolean>> = nodeHealthMonitor.health

    /** P2-13：自动切换到最快节点开关（默认关）。 */
    private val _autoSwitchFastest = MutableStateFlow(false)
    val autoSwitchFastest: StateFlow<Boolean> = _autoSwitchFastest
    fun setAutoSwitchFastest(enabled: Boolean) {
        _autoSwitchFastest.value = enabled
        nodeHealthMonitor.autoSwitchFastest = enabled
    }

    /** P2-15：实际代理端口（冲突避让后）。 */
    val proxyPort: Int get() = manager.mixedPort()

    /** P3-18：预热实例状态。 */
    val warmupState = manager.warmupState

    // ── P3-19：连接审计日志 ──
    val connLogEnabled = connectionLogger.enabled
    private val _connLogs = MutableStateFlow<List<ProxyConnectionEntry>>(emptyList())
    val connLogs: StateFlow<List<ProxyConnectionEntry>> = _connLogs.asStateFlow()

    fun setConnLogEnabled(on: Boolean) {
        connectionLogger.setEnabled(on)
        refreshConnLogs()
    }

    fun refreshConnLogs(query: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _connLogs.value = if (query.isBlank()) connLogRepo.recent() else connLogRepo.search(query)
        }
    }

    /** 导出全部日志为 CSV 字符串。 */
    fun exportConnLogsCsv(): String {
        val sb = StringBuilder("timestamp,host,ip,port,protocol,up_bytes,down_bytes,duration_ms,status\n")
        connLogRepo.all().forEach { e ->
            sb.appendLine(listOf(
                e.timestamp, e.host ?: "", e.ip ?: "", e.port ?: "", e.protocol ?: "",
                e.upBytes, e.downBytes, e.durationMs, e.status ?: ""
            ).joinToString(","))
        }
        return sb.toString()
    }

    /** P1-8：今日/本周/累计流量用量。 */
    private val _trafficToday = MutableStateFlow(TrafficUsage(0, 0))
    val trafficToday: StateFlow<TrafficUsage> = _trafficToday
    private val _trafficWeek = MutableStateFlow(TrafficUsage(0, 0))
    val trafficWeek: StateFlow<TrafficUsage> = _trafficWeek
    private val _trafficTotal = MutableStateFlow(TrafficUsage(0, 0))
    val trafficTotal: StateFlow<TrafficUsage> = _trafficTotal

    /** P1-8：刷新流量用量展示（进入页面 / 手动刷新时调）。 */
    fun refreshTrafficUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            _trafficToday.value = trafficRepo.todayUsage()
            _trafficWeek.value = trafficRepo.weekUsage()
            _trafficTotal.value = trafficRepo.totalUsage()
        }
    }

    /** 最近一次预检结果。 */
    private val _preview = MutableStateFlow<ProxyPreview?>(null)
    val preview: StateFlow<ProxyPreview?> = _preview.asStateFlow()

    /** 当前展开 profile 的节点列表视图（对齐 Clash：节点列表 + 测速状态）。 */
    private val _profileNodes = MutableStateFlow<ProfileNodesView?>(null)
    val profileNodes: StateFlow<ProfileNodesView?> = _profileNodes.asStateFlow()

    /** 当前展开 profile 的「分组 · 流量」视图（对齐 Clash：分组树 + 选中项 + 实时流量）。 */
    private val _groups = MutableStateFlow<ProxyGroupsView?>(null)
    val groups: StateFlow<ProxyGroupsView?> = _groups.asStateFlow()

    /** /traffic WS 订阅协程（打开分组视图时启动，关闭时取消）。 */
    private var trafficJob: Job? = null

    /** 一次性提示事件（Snackbar）。 */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 切换全局开关：开需至少有 1 个已播种 profile。 */
    fun toggleEnabled(desired: Boolean) {
        viewModelScope.launch {
            if (!desired) {
                manager.off()
                _events.send("代理已关闭")
                return@launch
            }
            val list = profiles.value
            if (list.isEmpty()) {
                _events.send("请先导入配置")
                return@launch
            }
            val id = activeProfileId.value ?: list.first().id
            val result = manager.on(id, null)
            if (result == "ok") {
                _events.send("代理已启用")
                connectivityTester.runDiagnostics()
            } else _events.send(result)
        }
    }

    /** 网络层优化 C5：切换 AI 接口直连分流（持久化，ClashProxyManager 同步到网络路由）。 */
    fun toggleAiHostsDirect(desired: Boolean) {
        viewModelScope.launch {
            repository.setAiHostsDirect(desired)
            _events.send(if (desired) "模型接口将直连（跳过代理）" else "模型接口恢复走代理")
        }
    }

    /** 把某 profile 设为活跃（并以其启用环境注入）。 */
    fun activate(id: String) {
        viewModelScope.launch {
            val result = manager.on(id, null)
            _events.send(if (result == "ok") "已切换活跃配置" else result)
            if (result == "ok") connectivityTester.runDiagnostics()
        }
    }

    /** 删除某 profile；若它正是活跃则一并清空活跃标记（不自动关开关）。 */
    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteSubscription(id)
            if (activeProfileId.value == id) repository.setActiveProfile(null)
            _events.send("已删除")
        }
    }

    /** P1-9：手动重跑连接诊断。 */
    fun retestConnectivity() {
        viewModelScope.launch { connectivityTester.runDiagnostics() }
    }

    /** P1-7：手动刷新某订阅型 profile（拉最新 YAML，活跃则热重载）。 */
    fun refreshSubscription(id: String) {
        viewModelScope.launch {
            val ok = manager.refreshSubscriptionNow(id)
            _events.send(if (ok) "订阅已刷新" else "刷新失败（仅订阅型可手动刷新，或网络异常）")
        }
    }

    /** 预检：订阅 URL 或手动 YAML → 解析概览（不落盘不启用）。 */
    fun runPreview(url: String?, yaml: String?) {
        viewModelScope.launch {
            _preview.value = null
            val resolved: String? = when {
                !url.isNullOrBlank() -> {
                    withContext(Dispatchers.IO) { manager.fetchSubscriptionYaml(url.trim()) }
                }
                !yaml.isNullOrBlank() -> yaml
                else -> null
            }
            if (resolved.isNullOrBlank()) {
                _preview.value = ProxyPreview(false, "预检失败：无法读取配置内容", warnings = emptyList())
                return@launch
            }
            val summary = manager.parseClashConfig(resolved)
            if (!summary.ok) {
                _preview.value = ProxyPreview(
                    ok = false,
                    summary = "预检失败：${summary.error ?: "YAML 解析失败"}",
                    warnings = runCatching { dangerScan(resolved) }.getOrDefault(emptyList())
                )
                return@launch
            }
            val nodeCount = summary.nodes.size
            val groupCount = summary.groups.size
            val providerNote = if (summary.providerCount > 0) " · provider ${summary.providerCount}" else ""
            val warnings = runCatching { dangerScan(resolved) }.getOrDefault(emptyList()) +
                if (summary.providerCount > 0) {
                    listOf("含 ${summary.providerCount} 个 proxy-provider：节点由内核动态加载，此处仅统计内联节点")
                } else {
                    emptyList()
                }
            _preview.value = ProxyPreview(
                ok = true,
                summary = "解析 OK · 节点 $nodeCount · 分组 $groupCount$providerNote · mode rule",
                nodeCount = nodeCount,
                groupCount = groupCount,
                warnings = warnings,
                resolvedYaml = resolved
            )
        }
    }

    /** 收起展开的节点列表。 */
    fun closeInspect() {
        _profileNodes.value = null
    }

    /**
     * 展开某 profile 并加载其节点列表（对齐 Clash 配置详情）：
     * 订阅型拉最新 YAML，手动型用已存 YAML；不落盘不启用。
     */
    fun inspectProfile(id: String) {
        viewModelScope.launch {
            val current = _profileNodes.value
            if (current != null && current.profileId == id && current.loading) return@launch
            val profile = profiles.value.firstOrNull { it.id == id }
            val name = profile?.name?.ifBlank { profile.id } ?: id
            _profileNodes.value = ProfileNodesView(id, name, ClashConfigSummary(ok = true), loading = true)
            val revealed = repository.revealSecret(id)
            if (revealed == null) {
                _profileNodes.value = ProfileNodesView(
                    id, name, ClashConfigSummary(ok = false),
                    error = "无法读取该配置（凭据解密失败）"
                )
                return@launch
            }
            val trimmed = revealed.trim()
            val source = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                withContext(Dispatchers.IO) { manager.fetchSubscriptionYaml(trimmed) }
            } else {
                trimmed
            }
            if (source.isNullOrBlank()) {
                _profileNodes.value = ProfileNodesView(
                    id, name, ClashConfigSummary(ok = false),
                    error = "订阅拉取失败或内容为空"
                )
                return@launch
            }
            val summary = manager.parseClashConfig(source)
            _profileNodes.value = ProfileNodesView(id, name, summary)
        }
    }

    /**
     * 对展开 profile 的所有节点测速（对齐 Clash：只走内核真实出口，不做 App 直连近似）。
     *
     * 若被测配置不是当前运行配置（代理未启用 / 活跃的是别的 profile），自动临时用该配置启用内核
     * 再测，测完恢复原状态。Clash 的测速本来就是针对「当前加载配置」，节点延迟只能由内核在真实出口
     * 上测出；旧实现「未启用时直连 server:port」在节点几乎都在海外时必然 connect 超时 → 假「全部超时」。
     * 内核始终不可达则给出明确错误，不再逐个标超时。
     */
    fun testProfileLatency(id: String) {
        val current = _profileNodes.value ?: return
        if (current.profileId != id || current.testing) return
        _profileNodes.value = current.copy(testing = true, latencies = emptyMap(), error = null)
        viewModelScope.launch {
            val nodes = current.summary.nodes
            if (nodes.isEmpty()) {
                _profileNodes.update { v ->
                    if (v == null || v.profileId != id) v
                    else v.copy(
                        testing = false,
                        error = "没有可测的内联节点（节点可能全部来自 proxy-provider，需先启用代理后经「分组 · 流量」查看实时延迟）"
                    )
                }
                return@launch
            }
            val wasEnabled = manager.state.value.enabled
            val wasActive = manager.state.value.activeProfileId
            // 被测配置未在运行 → 临时启用，确保节点已加载进内核
            if (!(wasEnabled && wasActive == id)) {
                val r = manager.on(id, null)
                if (r != "ok") {
                    _profileNodes.update { v ->
                        if (v == null || v.profileId != id) v
                        else v.copy(testing = false, error = "启用配置以测速失败：$r")
                    }
                    return@launch
                }
            }
            // 等内核控制面就绪（根侧拉启 mihomo 是异步的，短轮询）
            val ready = withContext(Dispatchers.IO) {
                var ok = false
                repeat(6) {
                    if (manager.controllerRequest("GET", "/configs") != null) {
                        ok = true
                        return@withContext true
                    }
                    delay(500)
                }
                ok
            }
            if (!ready) {
                restoreAfterTest(wasEnabled, wasActive)
                _profileNodes.update { v ->
                    if (v == null || v.profileId != id) v
                    else v.copy(testing = false, error = "mihomo 内核未运行（控制面不可达），无法测速；请先「启用代理」后再试")
                }
                return@launch
            }
            val semaphore = Semaphore(6)
            coroutineScope {
                nodes.forEach { node ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            val d = manager.testNodeLatency(node)
                            _profileNodes.update { v ->
                                if (v == null || v.profileId != id) v
                                else v.copy(latencies = v.latencies + (node.name to d))
                            }
                        }
                    }
                }
            }
            restoreAfterTest(wasEnabled, wasActive)
            _profileNodes.update { v ->
                if (v == null || v.profileId != id) v else v.copy(testing = false)
            }
        }
    }

    /** 测速结束后恢复代理运行状态：测前未启用→关；测前是其它活跃配置→恢复原活跃。 */
    private suspend fun restoreAfterTest(wasEnabled: Boolean, wasActive: String?) {
        if (!wasEnabled) {
            manager.off()
        } else if (wasActive != null && wasActive != manager.state.value.activeProfileId) {
            manager.on(wasActive, null)
        }
    }

    /**
     * 智能选优：对当前活跃配置的所有节点并发测速，自动切换到延迟最低的可用节点。
     * 与 testProfileLatency 的区别：测完后自动 select 最快节点，并发送事件通知。
     */
    fun smartSelectBestNode(id: String) {
        val current = _profileNodes.value ?: return
        if (current.profileId != id || current.testing) return
        _profileNodes.value = current.copy(testing = true, latencies = emptyMap(), error = null)
        viewModelScope.launch {
            val nodes = current.summary.nodes
            if (nodes.isEmpty()) {
                _profileNodes.update { v ->
                    if (v == null || v.profileId != id) v
                    else v.copy(testing = false, error = "没有可测的内联节点（节点可能全部来自 proxy-provider）")
                }
                return@launch
            }
            // 智能选优要求代理必须在运行（要切节点）
            if (!manager.state.value.enabled) {
                val r = manager.on(id, null)
                if (r != "ok") {
                    _profileNodes.update { v ->
                        if (v == null || v.profileId != id) v
                        else v.copy(testing = false, error = "启用代理失败：$r")
                    }
                    return@launch
                }
            }
            // 等控制面就绪
            val ready = withContext(Dispatchers.IO) {
                var ok = false
                repeat(6) {
                    if (manager.controllerRequest("GET", "/configs") != null) { ok = true; return@withContext true }
                    delay(500)
                }
                ok
            }
            if (!ready) {
                _profileNodes.update { v ->
                    if (v == null || v.profileId != id) v
                    else v.copy(testing = false, error = "mihomo 控制面不可达")
                }
                return@launch
            }
            // 并发测速（6 并发）
            val semaphore = Semaphore(6)
            val latencies = mutableMapOf<String, Long?>()
            coroutineScope {
                nodes.forEach { node ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            val d = manager.testNodeLatency(node)
                            latencies[node.name] = d
                            _profileNodes.update { v ->
                                if (v == null || v.profileId != id) v
                                else v.copy(latencies = v.latencies + (node.name to d))
                            }
                        }
                    }
                }
            }
            // 选最快可用节点
            val best = latencies.entries
                .filter { it.value != null && it.value!! > 0 }
                .minByOrNull { it.value!! }
            if (best != null) {
                // 找第一个 select 分组并切换
                val proxiesResp = manager.controllerRequest("GET", "/proxies")
                var group: String? = null
                if (proxiesResp != null) {
                    val parsed = runCatching {
                        kotlinx.serialization.json.Json.parseToJsonElement(proxiesResp)
                    }.getOrNull()
                    val proxiesObj = parsed?.jsonObject?.get("proxies")?.jsonObject
                    if (proxiesObj != null) {
                        for ((gName, gValue) in proxiesObj) {
                            val gType = gValue.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                            if (gType == "Selector") {
                                group = gName
                                break
                            }
                        }
                    }
                }
                if (group != null) {
                    val encodedGroup = java.net.URLEncoder.encode(group, "UTF-8").replace("+", "%20")
                    manager.controllerRequest("PUT", "/proxies/$encodedGroup", """{"name":"${best.key}"}""")
                    _events.send("已智能选优：${best.key}（${best.value}ms）")
                } else {
                    _events.send("测速完成：最快节点 ${best.key}（${best.value}ms），但未找到可切换的分组")
                }
            } else {
                _events.send("所有节点均超时，无法智能选优")
            }
            _profileNodes.update { v ->
                if (v == null || v.profileId != id) v else v.copy(testing = false)
            }
        }
    }

    /** 打开「分组 · 流量」视图：读 /proxies 快照并订阅 /traffic WS 实时流（需代理运行中）。 */
    fun openGroups(id: String) {
        val current = _groups.value
        if (current?.profileId == id && !current.loading) return
        trafficJob?.cancel()
        _groups.value = ProxyGroupsView(id, loading = true)
        viewModelScope.launch {
            if (!manager.state.value.enabled) {
                _groups.value = ProxyGroupsView(id, running = false)
                return@launch
            }
            val snap = manager.fetchProxiesSnapshot()
            _groups.value = ProxyGroupsView(
                id,
                snapshot = snap,
                running = true,
                error = if (snap == null) "无法读取分组（控制器不可达）" else null
            )
            trafficJob = viewModelScope.launch {
                manager.trafficFlow().collect { t ->
                    _groups.update { v -> if (v?.profileId == id) v.copy(traffic = t) else v }
                }
            }
        }
    }

    /** 关闭「分组 · 流量」视图并停止 /traffic WS 订阅。 */
    fun closeGroups() {
        trafficJob?.cancel()
        trafficJob = null
        _groups.value = null
    }

    /** 切换某分组内选中节点（用户 UI 直点，与 Clash 一致；走同一 REST）。 */
    fun selectGroupNode(group: String, node: String) {
        viewModelScope.launch {
            val ok = manager.selectProxyNode(group, node)
            if (ok) {
                val snap = manager.fetchProxiesSnapshot()
                _groups.update { v -> if (v != null) v.copy(snapshot = snap ?: v.snapshot) else v }
                _events.send("已切换 $group → $node")
            } else {
                _events.send("切换失败（节点无效或控制器不可达）")
            }
        }
    }

    /** 保存一个 profile（播种）。[kind] 取 [ProxySubscription.KIND_SUBSCRIPTION]/[KIND_MANUAL]。 */
    fun commitProfile(name: String, kind: String, secret: String, enableNow: Boolean) {
        viewModelScope.launch {
            if (name.isBlank() || secret.isBlank()) {
                _events.send("名称/内容不能为空")
                return@launch
            }
            val id = generateId()
            repository.upsertSubscription(id, name.trim(), kind, secret)
            _events.send("已保存「${name.trim()}」")
            if (enableNow) {
                val result = manager.on(id, null)
                _events.send(if (result == "ok") "已启用「${name.trim()}」" else result)
            }
        }
    }

    private fun generateId(): String =
        "prof-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8)

    /** 危险/覆盖键扫描：提示哪些字段将从配置中剥离（与 synthesizeConfig 实际剥离的键集保持一致）。 */
    private fun dangerScan(yaml: String): List<String> {
        // P0-2：dns/script/profile/geodata-mode 已并入 OVERRIDDEN_KEYS，直接复用，
        // 避免「提示要剥离但实际没剥」与「实际剥离但没提示」的不一致。
        val danger = ClashProxyManager.OVERRIDDEN_KEYS
        return yaml.lines().map { it.trimStart() }
            .filter { it.isNotEmpty() }
            .distinct()
            .mapNotNull { line ->
                val key = danger.firstOrNull { line.startsWith("$it:") || line.startsWith("$it :") }
                key?.let { "将剥离顶层字段：$it" }
            }
    }
}