package com.mini.me_core.feature.agent.domain.execution.tool.proxy

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ParameterType
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCapability
import com.mini.me_core.feature.agent.domain.execution.tool.ToolParameter
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolResult
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import com.mini.me_core.feature.proxy.domain.ClashProxyManager
import com.mini.me_core.feature.proxy.domain.ProxyConnectivityTester
import com.mini.me_core.feature.proxy.domain.ProxyNodeHealthMonitor
import com.mini.me_core.datalayer.store.ProxyTrafficRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 网络代理工具（network_proxy）：让模型自助管理容器内的 mihomo 代理（VPN 形态）。
 *
 * 护栏（《网络代理设计 v1.0》§3 / §8）：
 *  - [ToolPermissionPolicy.ASK]：每次调用都弹确认卡，能力隔离 [ToolCapability.MODIFY_NETWORK]；
 *  - **播种后接管**：`on` 只能引用已播种 profile（[ProxySettingsRepository]）或用**临时 inline** YAML
 *    （仅本次会话、不新建长存订阅），模型不能凭空造新订阅；
 *  - 输出一律脱敏：订阅 URL / YAML / secret 不回显，只回 `id/name/kind` 与运行态。
 *
 * action ∈ { status, on, off, test, select, list_subscriptions, list_proxies, latency,
 *            diagnose, traffic, health, auto_fix }
 *  - status / list_subscriptions / list_proxies / latency / diagnose / traffic / health：只读；
 *  - on：`profile_id` 或 `inline_yaml`（临时）→ 合成配置；off：关；
 *  - test：给定 `url`（订阅）或 `yaml`（手动）做单次校验（拉取+统计，不落盘、不启用）；
 *  - select：`group` + `node` 切节点（node="auto" 自动选最快），或 `mode` 切运行模式；
 *  - latency：`node` 单节点测速，或 `group` 批量测该分组全部节点；
 *  - diagnose：四项连接诊断（进程/控制面/出口/DNS），返回 GREEN/YELLOW/RED；
 *  - traffic：今日/本周/累计流量；health：节点健康（不可用名单）；
 *  - auto_fix：诊断→若不通则自动切最快可用节点→复检，形成自愈闭环。
 *
 * 错误码（模型可据此决策）：MISSING_ACTION / MISSING_ARGS / UNSUPPORTED_ACTION /
 *  PROXY_NOT_RUNNING / PROXY_DISABLED / PROXY_ON_FAILED / FETCH_FAILED / PARSE_FAILED /
 *  MISSING_NODE / NO_HEALTHY_NODE / DIAG_FAILED / PROXY_FAILED。
 */
class NetworkProxyTool @Inject constructor(
    private val manager: ClashProxyManager,
    private val repository: ProxySettingsRepository,
    private val connectivityTester: ProxyConnectivityTester,
    private val trafficRepo: ProxyTrafficRepository,
    private val nodeHealthMonitor: ProxyNodeHealthMonitor,
) : AgentTool() {

    private companion object {
        const val TAG = "NetworkProxyTool"
    }

    override val name = "network_proxy"
    override val description = "管理容器内网络代理（mihomo，VPN 形态）。action ∈ {status, on, off, test, select, list_subscriptions, list_proxies, latency, diagnose, traffic, health, auto_fix}。on 用已播种 profile_id 或临时 inline_yaml；select 切 select 分组节点（node=auto 自动选最快）或 mode；latency 单节点或 group 批量；diagnose 四项连接诊断；auto_fix 自动切最快节点并复检。所有会改出口的操作都会请求用户确认。"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.MODIFY_NETWORK, ToolCapability.NETWORK_READ)

    override val parameters = mapOf(
        "action" to ToolParameter(
            "action", ParameterType.STRING,
            "要执行的操作：status / on / off / test / select / list_subscriptions / list_proxies / latency / diagnose / traffic / health / auto_fix",
            enum = listOf("status", "on", "off", "test", "select", "list_subscriptions", "list_proxies", "latency", "diagnose", "traffic", "health", "auto_fix")
        ),
        "profile_id" to ToolParameter("profile_id", ParameterType.STRING, "on 时引用一个已播种的 profile id（list_subscriptions 可得）", required = false),
        "inline_yaml" to ToolParameter("inline_yaml", ParameterType.STRING, "临时代理配置 YAML（仅本次会话，不建成长期订阅）。on 时可用", required = false),
        "url" to ToolParameter("url", ParameterType.STRING, "test 单个订阅 URL", required = false),
        "yaml" to ToolParameter("yaml", ParameterType.STRING, "test 单个手动 YAML", required = false),
        "group" to ToolParameter("group", ParameterType.STRING, "select 目标分组名；latency 批量测速时为分组名", required = false),
        "node" to ToolParameter("node", ParameterType.STRING, "select 目标节点名；传 \"auto\" 自动选分组内最快可用节点", required = false),
        "mode" to ToolParameter("mode", ParameterType.STRING, "select 时切换运行模式：rule / global / direct", required = false),
    )

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少 action", "MISSING_ACTION")
        return try {
            when (action) {
                "status" -> doStatus()
                "on" -> doOn(args)
                "off" -> doOff()
                "test" -> doTest(args)
                "select" -> doSelect(args)
                "list_subscriptions" -> doListSubscriptions()
                "list_proxies" -> doListProxies()
                "latency" -> doLatency(args)
                "diagnose" -> doDiagnose()
                "traffic" -> doTraffic()
                "health" -> doHealth()
                "auto_fix" -> doAutoFix()
                else -> ToolResult.Error("未知 action：$action", "UNSUPPORTED_ACTION")
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "network_proxy 失败: ${e.message}")
            ToolResult.Error("操作失败：${e.message}", "PROXY_FAILED")
        }
    }

    // ── 只读：status（含延迟/流量/连接数/节点健康摘要）──
    private suspend fun doStatus(): ToolResult {
        val s = manager.state.value
        val controllerCheck = if (s.enabled) manager.controllerRequest("GET", "/configs") else null
        val diag = connectivityTester.result.value
        val today = trafficRepo.todayUsage()
        val health = nodeHealthMonitor.health.value
        val unhealthy = health.filterValues { !it }.keys.toList()
        val connCount = runCatching {
            manager.controllerRequest("GET", "/connections")?.let {
                kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject["connections"]?.jsonArray?.size
            }
        }.getOrNull()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "enabled" to JsonPrimitive(s.enabled),
                    "mode" to JsonPrimitive(s.mode),
                    "active_profile_id" to (s.activeProfileId?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                    "mixed" to JsonPrimitive("${s.mixedHost}:${s.mixedPort}"),
                    "controller" to JsonPrimitive(manager.controllerAddress()),
                    "controller_reachable" to JsonPrimitive(controllerCheck != null),
                    "outbound_latency_ms" to (diag?.outboundLatencyMs?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1)),
                    "traffic_light" to JsonPrimitive(diag?.light?.name ?: "UNKNOWN"),
                    "today_up_bytes" to JsonPrimitive(today.upBytes),
                    "today_down_bytes" to JsonPrimitive(today.downBytes),
                    "active_connections" to JsonPrimitive(connCount ?: -1),
                    "unhealthy_nodes" to JsonArray(unhealthy.map { JsonPrimitive(it) }),
                )
            )
        )
    }

    // ── 写：on / off ──
    private suspend fun doOn(args: Map<String, JsonElement>): ToolResult {
        val profileId = args["profile_id"]?.jsonPrimitive?.contentOrNull
        val inlineYaml = args["inline_yaml"]?.jsonPrimitive?.contentOrNull
        val result = manager.on(profileId, inlineYaml)
        if (result != "ok") return ToolResult.Error(result, "PROXY_ON_FAILED")
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "enabled" to JsonPrimitive(true),
                    "profile_id" to (profileId?.let { JsonPrimitive(it) } ?: JsonPrimitive("")),
                    "inline" to JsonPrimitive(inlineYaml != null),
                    "note" to JsonPrimitive("代理已启用：新容器进程生效；inline 仅本次会话、不入订阅列表"),
                )
            )
        )
    }

    private suspend fun doOff(): ToolResult {
        manager.off()
        return ToolResult.Success(
            JsonObject(
                mapOf("ok" to JsonPrimitive(true), "enabled" to JsonPrimitive(false))
            )
        )
    }

    // ── 只读：list_subscriptions（脱敏）──
    private suspend fun doListSubscriptions(): ToolResult {
        val list = repository.subscriptionsFlow.first()
        val items = JsonArray(
            list.map { s ->
                JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(s.id),
                        "name" to JsonPrimitive(s.name),
                        "kind" to JsonPrimitive(s.kind),
                    )
                )
            }
        )
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "count" to JsonPrimitive(items.size),
                    "subscriptions" to items
                )
            )
        )
    }

    // ── 校验：test（单次拉取/校验，不落盘不启用）──
    private suspend fun doTest(args: Map<String, JsonElement>): ToolResult {
        val url = args["url"]?.jsonPrimitive?.contentOrNull
        val yaml = args["yaml"]?.jsonPrimitive?.contentOrNull
        val source = when {
            url != null -> manager.fetchSubscriptionYaml(url)
            yaml != null -> yaml
            else -> return ToolResult.Error("test 需要 url（订阅）或 yaml（手动）", "MISSING_ARGS")
        }
        if (source.isNullOrBlank()) return ToolResult.Error("拉取/解析失败（URL 不可达或内容为空）", "FETCH_FAILED")
        val summary = manager.parseClashConfig(source)
        if (!summary.ok) {
            return ToolResult.Error("YAML 解析失败：${summary.error}", "PARSE_FAILED")
        }
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "valid" to JsonPrimitive(true),
                    "node_count" to JsonPrimitive(summary.nodes.size),
                    "group_count" to JsonPrimitive(summary.groups.size),
                    "provider_count" to JsonPrimitive(summary.providerCount),
                    "nodes" to JsonArray(summary.nodes.map { JsonPrimitive(it.name) }),
                    "note" to JsonPrimitive(
                        if (summary.providerCount > 0) {
                            "含 proxy-provider：节点由内核动态加载，node_count 仅统计内联节点"
                        } else {
                            "本次仅校验，未启用未落盘"
                        }
                    ),
                )
            )
        )
    }

    // ── 写：select 切节点 / 切 mode ──
    private suspend fun doSelect(args: Map<String, JsonElement>): ToolResult {
        val mode = args["mode"]?.jsonPrimitive?.contentOrNull
        val group = args["group"]?.jsonPrimitive?.contentOrNull
        val node = args["node"]?.jsonPrimitive?.contentOrNull
        if (mode != null) {
            val body = """{"mode":"$mode"}"""
            val resp = manager.controllerRequest("PATCH", "/configs", body)
            if (resp == null) return selOffline()
            return ToolResult.Success(
                JsonObject(mapOf("ok" to JsonPrimitive(true), "mode" to JsonPrimitive(mode)))
            )
        }
        if (group == null || node == null) {
            return ToolResult.Error("select 需要 group+node（切节点，node=auto 自动选最快）或 mode（切模式）", "MISSING_ARGS")
        }
        // node="auto"：自动测速该分组，选最快可用节点。
        val targetNode = if (node == "auto") {
            pickFastestNode(group)
                ?: return ToolResult.Error("分组内无可用节点（全部超时）", "NO_HEALTHY_NODE")
        } else node
        val resp = manager.controllerRequest("PUT", "/proxies/${encoded(group)}", """{"name":"$targetNode"}""")
        if (resp == null) return selOffline()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "group" to JsonPrimitive(group),
                    "node" to JsonPrimitive(targetNode),
                    "auto" to JsonPrimitive(node == "auto"),
                )
            )
        )
    }

    /** 测分组内全部节点延迟，返回最快者（失败/超时节点跳过）。无可用返回 null。 */
    private suspend fun pickFastestNode(group: String): String? {
        val proxies = manager.controllerRequest("GET", "/proxies") ?: return null
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(proxies).jsonObject }.getOrNull()
            ?: return null
        val members = root["proxies"]?.jsonObject?.get(group)?.jsonObject?.get("all")?.jsonArray
            ?: return null
        var best: String? = null; var bestDelay = Long.MAX_VALUE
        members.forEach { el ->
            val name = el.jsonPrimitive.contentOrNull ?: return@forEach
            val d = testNodeDelayMs(name) ?: return@forEach
            if (d in 1..<bestDelay) { bestDelay = d; best = name }
        }
        return best
    }

    /** 单节点延迟（ms），失败返回 null。 */
    private suspend fun testNodeDelayMs(node: String): Long? {
        val resp = manager.controllerRequest("GET", "/proxies/${encoded(node)}/delay?url=http://www.gstatic.com/generate_204&timeout=5000")
            ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject["delay"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        }.getOrNull()
    }

    private fun selOffline(): ToolResult =
        ToolResult.Error("mihomo 未在运行（代理未启用或控制面不可达），无法 select", "PROXY_NOT_RUNNING")

    // ── 只读：list_proxies / latency（走 REST）──
    private suspend fun doListProxies(): ToolResult {
        val resp = manager.controllerRequest("GET", "/proxies")
            ?: return ToolResult.Error("mihomo 未在运行", "PROXY_NOT_RUNNING")
        val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject }.getOrNull()
        return ToolResult.Success(
            parsed ?: JsonObject(mapOf("ok" to JsonPrimitive(false), "raw" to JsonPrimitive(resp.take(2000))))
        )
    }

    private suspend fun doLatency(args: Map<String, JsonElement>): ToolResult {
        val node = args["node"]?.jsonPrimitive?.contentOrNull
        val group = args["group"]?.jsonPrimitive?.contentOrNull
        // 批量：group 给出则测该分组全部节点。
        if (group != null) {
            val proxies = manager.controllerRequest("GET", "/proxies")
                ?: return ToolResult.Error("mihomo 未在运行", "PROXY_NOT_RUNNING")
            val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(proxies).jsonObject }.getOrNull()
                ?: return ToolResult.Error("解析 /proxies 失败", "PROXY_FAILED")
            val members = root["proxies"]?.jsonObject?.get(group)?.jsonObject?.get("all")?.jsonArray
                ?: return ToolResult.Error("分组 $group 不存在", "MISSING_ARGS")
            val results = members.mapNotNull { el ->
                val name = el.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                val d = testNodeDelayMs(name)
                JsonObject(mapOf("node" to JsonPrimitive(name), "delay_ms" to JsonPrimitive(d ?: -1)))
            }
            return ToolResult.Success(
                JsonObject(mapOf("ok" to JsonPrimitive(true), "group" to JsonPrimitive(group), "results" to JsonArray(results)))
            )
        }
        // 单节点。
        val target = node ?: return ToolResult.Error("latency 需要 node 或 group", "MISSING_NODE")
        val resp = manager.controllerRequest("GET", "/proxies/${encoded(target)}/delay?url=http://www.gstatic.com/generate_204&timeout=5000")
            ?: return ToolResult.Error("mihomo 未在运行或节点无效", "PROXY_NOT_RUNNING")
        val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject }.getOrNull()
        return ToolResult.Success(
            parsed ?: JsonObject(mapOf("raw" to JsonPrimitive(resp.take(2000))))
        )
    }

    // ── 只读：diagnose / traffic / health ──
    private suspend fun doDiagnose(): ToolResult {
        if (!manager.state.value.enabled) return ToolResult.Error("代理未启用", "PROXY_DISABLED")
        connectivityTester.runDiagnostics()
        val r = connectivityTester.result.value
            ?: return ToolResult.Error("诊断执行失败", "DIAG_FAILED")
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "light" to JsonPrimitive(r.light.name),
                    "process_alive" to JsonPrimitive(r.processAlive),
                    "controller_reachable" to JsonPrimitive(r.controllerReachable),
                    "outbound_ok" to JsonPrimitive(r.outboundOk),
                    "outbound_latency_ms" to (r.outboundLatencyMs?.let { JsonPrimitive(it) } ?: JsonPrimitive(-1)),
                    "dns_ok" to JsonPrimitive(r.dnsOk),
                )
            )
        )
    }

    private suspend fun doTraffic(): ToolResult {
        val t = trafficRepo.todayUsage()
        val w = trafficRepo.weekUsage()
        val total = trafficRepo.totalUsage()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "today_up" to JsonPrimitive(t.upBytes),
                    "today_down" to JsonPrimitive(t.downBytes),
                    "week_up" to JsonPrimitive(w.upBytes),
                    "week_down" to JsonPrimitive(w.downBytes),
                    "total_up" to JsonPrimitive(total.upBytes),
                    "total_down" to JsonPrimitive(total.downBytes),
                )
            )
        )
    }

    private suspend fun doHealth(): ToolResult {
        val h = nodeHealthMonitor.health.value
        val unhealthy = h.filterValues { !it }.keys.toList()
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "monitored" to JsonPrimitive(h.size),
                    "unhealthy_count" to JsonPrimitive(unhealthy.size),
                    "unhealthy" to JsonArray(unhealthy.map { JsonPrimitive(it) }),
                )
            )
        )
    }

    // ── 自愈闭环：诊断→不通则切最快节点→复检 ──
    private suspend fun doAutoFix(): ToolResult {
        if (!manager.state.value.enabled) return ToolResult.Error("代理未启用", "PROXY_DISABLED")
        connectivityTester.runDiagnostics()
        val before = connectivityTester.result.value
            ?: return ToolResult.Error("诊断执行失败", "DIAG_FAILED")
        if (before.light == com.mini.me_core.feature.proxy.domain.TrafficLight.GREEN) {
            return ToolResult.Success(JsonObject(mapOf(
                "ok" to JsonPrimitive(true), "action" to JsonPrimitive("none"),
                "before" to JsonPrimitive("GREEN"), "note" to JsonPrimitive("连接正常，无需修复"))))
        }
        // 找一个 select 分组，切到最快节点。
        val proxies = manager.controllerRequest("GET", "/proxies")
            ?: return ToolResult.Error("控制面不可达", "PROXY_NOT_RUNNING")
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(proxies).jsonObject }.getOrNull()
            ?: return ToolResult.Error("解析 /proxies 失败", "PROXY_FAILED")
        val proxiesMap = root["proxies"]?.jsonObject ?: return ToolResult.Error("无分组", "PROXY_FAILED")
        val targetGroup = proxiesMap.entries.firstOrNull { it.value.jsonObject["all"] != null }?.key
            ?: return ToolResult.Error("无可切换分组", "NO_HEALTHY_NODE")
        val fastest = pickFastestNode(targetGroup)
            ?: return ToolResult.Error("分组内无可用节点", "NO_HEALTHY_NODE")
        manager.controllerRequest("PUT", "/proxies/${encoded(targetGroup)}", """{"name":"$fastest"}""")
        connectivityTester.runDiagnostics()
        val after = connectivityTester.result.value
        return ToolResult.Success(
            JsonObject(
                mapOf(
                    "ok" to JsonPrimitive(true),
                    "action" to JsonPrimitive("switch_node"),
                    "group" to JsonPrimitive(targetGroup),
                    "node" to JsonPrimitive(fastest),
                    "before" to JsonPrimitive(before.light.name),
                    "after" to JsonPrimitive(after?.light?.name ?: "UNKNOWN"),
                )
            )
        )
    }

    private fun encoded(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}