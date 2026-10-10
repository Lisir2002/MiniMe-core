package com.mini.me_core.feature.proxy.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * P2-16 拆分产物：mihomo external-controller 控制端口 API 调用。
 *
 * 逻辑原样搬迁自 [ClashProxyManager]：secret Bearer 鉴权、127.0.0.1 回环、能拿到任意 HTTP
 * 响应即把 [ProxyRuntimeState.controllerReachable] 翻 true、连接失败翻 false。
 */
internal class ClashControlClient(
    private val okHttp: OkHttpClient,
    private val ports: ClashProxyPorts,
    private val secretProvider: () -> String,
    private val stateFlow: MutableStateFlow<ProxyRuntimeState>,
    private val ensureSecretLoaded: suspend () -> Unit,
) {
    private val tag = "ClashProxyManager"

    /** 打 mimomo external-controller REST；成功返回 body，失败返回 null（视为未运行/网络错）。 */
    suspend fun controllerRequest(
        method: String,
        path: String,
        body: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        ensureSecretLoaded()
        val url = "http://${ClashProxyManager.CONTROLLER_HOST}:${ports.controller}$path"
        val requestBody = body?.toRequestBody("application/json".toMediaType())
            ?: ByteArray(0).toRequestBody(null)
        val req = when (method) {
            "GET" -> Request.Builder().url(url).header("Authorization", "Bearer ${secretProvider()}").get().build()
            "PUT" -> Request.Builder().url(url).header("Authorization", "Bearer ${secretProvider()}").put(requestBody).build()
            "PATCH" -> Request.Builder().url(url).header("Authorization", "Bearer ${secretProvider()}").patch(requestBody).build()
            else -> return@withContext null
        }
        runCatching {
            okHttp.newCall(req).execute().use { resp ->
                // 能拿到任何 HTTP 响应（即使 4xx/5xx）都说明控制器进程在监听 → reachable=true。
                stateFlow.update { it.copy(controllerReachable = true) }
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.onFailure {
            // 连接被拒/超时/网络错 → 控制器不可达，及时翻为 false（否则状态会残留为上一次的 true）。
            stateFlow.update { it.copy(controllerReachable = false) }
        }.getOrNull()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** P1-9：经内核真实出口测 generate_204 延迟（ms）；控制器不可达/超时返回 null。 */
    suspend fun testOutboundLatency(): Long? {
        ensureSecretLoaded()
        val resp = controllerRequest(
            "GET",
            "/proxies/GLOBAL/delay?url=http://www.gstatic.com/generate_204&timeout=5000"
        ) ?: return null
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject["delay"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        }.getOrNull()
    }

    /**
     * P1-8：读 mihomo 累计流量计数 [uploadTotal, downloadTotal]（/connections 顶层字段，自内核启动起累计）。
     * 控制器不可达返回 null。差值由调用方（采样器）计算。
     */
    suspend fun readTrafficCounters(): Pair<Long, Long>? {
        ensureSecretLoaded()
        val resp = controllerRequest("GET", "/connections") ?: return null
        return runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject
            val up = obj["uploadTotal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val down = obj["downloadTotal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            up to down
        }.getOrNull()
    }

    /**
     * 拉取 mihomo /proxies 全量快照（分组树：类型/当前选中项/成员/健康检查延迟）。
     * 与 `network_proxy list_proxies` 共用同一 REST 数据源；未运行/不可达返回 null。
     */
    suspend fun fetchProxiesSnapshot(): ClashProxiesSnapshot? {
        val resp = controllerRequest("GET", "/proxies") ?: return null
        return runCatching {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(resp).jsonObject
            val proxies = root["proxies"]?.jsonObject ?: return null
            val groups = proxies.mapNotNull { (name, el) ->
                val obj = el.jsonObject
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                    ?: return@mapNotNull null
                if (type !in ClashProxyManager.GROUP_TYPES) return@mapNotNull null
                val now = obj["now"]?.jsonPrimitive?.contentOrNull
                val all = obj["all"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val delay = obj["history"]?.jsonArray?.lastOrNull()
                    ?.jsonObject?.get("delay")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ProxyGroupInfo(name, type, now, all, delay)
            }
            ClashProxiesSnapshot(groups = groups, nodeCount = proxies.size)
        }.getOrNull()
    }

    /**
     * 切换某分组内选中节点（对齐 Clash 点击切换）。走同一 REST：PUT /proxies/{group}。
     * 成功返回 true。
     */
    suspend fun selectProxyNode(group: String, node: String): Boolean {
        val resp = controllerRequest("PUT", "/proxies/${urlEncode(group)}", """{"name":"$node"}""")
        return resp != null
    }

    /**
     * P1-11：热重载配置（不杀进程、不断连）。
     *
     * mihomo external-controller `PUT /configs?force=true` 接受 JSON：
     *   - `path`：从文件加载（这里传空，走 payload）；
     *   - `payload`：内联 YAML 原文（**不是 base64**，mihomo 直接按 YAML 文本解析）。
     * 切换 profile / 订阅更新后用本方法让内核重新加载 proxies/rules，避免 restart 造成的端口抖动与连接中断。
     *
     * @return true=内核已接受新配置；false=控制面不可达或内核拒绝（调用方应回退重启）。
     */
    suspend fun reloadConfig(configYaml: String): Boolean {
        ensureSecretLoaded()
        // payload 必须是合法 JSON 字符串（YAML 原文里可能含引号/换行），用 JsonPrimitive 序列化转义。
        val payload = kotlinx.serialization.json.JsonPrimitive(configYaml).toString()
        val body = """{"path":"","payload":$payload,"force":true}"""
        val resp = controllerRequest("PUT", "/configs?force=true", body)
        // 成功返回 204（空 body）→ controllerRequest 返回 ""（非 null）；失败返回 null。
        val ok = resp != null
        FileLogger.i(tag, if (ok) "mihomo 热重载配置成功" else "mihomo 热重载配置失败")
        return ok
    }

    /**
     * 单节点测速（对齐 Clash：只走内核真实出口，不做 App 直连近似）。
     * 走 mihomo REST `/proxies/{node}/delay?url=generate_204&timeout=5000`。
     */
    suspend fun testNodeLatency(node: ProxyNodeInfo): Long? = withContext(Dispatchers.IO) {
        val encoded = urlEncode(node.name)
        val resp = controllerRequest(
            "GET",
            "/proxies/$encoded/delay?url=http://www.gstatic.com/generate_204&timeout=5000"
        )
        resp?.let { body ->
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(body)
                    .jsonObject["delay"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            }.getOrNull()
        }
    }

    /**
     * /traffic WS 实时推流（零轮询，与 mihomo/CMA 的 flow 数据源一致）。
     * 每次 collect 建立一条 WS；collect 取消即关闭（awaitClose 里 ws.cancel()），失败自动结束流。
     */
    fun trafficFlow(): Flow<ProxyTraffic> = callbackFlow {
        ensureSecretLoaded()
        val req = Request.Builder()
            .url("http://${ClashProxyManager.CONTROLLER_HOST}:${ports.controller}/traffic")
            .header("Authorization", "Bearer ${secretProvider()}")
            .build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val el = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
                    val up = el["up"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val down = el["down"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    trySend(ProxyTraffic(up, down))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        }
        val ws = okHttp.newWebSocket(req, listener)
        awaitClose { ws.cancel() }
    }
}
