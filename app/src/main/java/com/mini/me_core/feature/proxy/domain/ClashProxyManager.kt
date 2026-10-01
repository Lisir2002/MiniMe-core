package com.mini.me_core.feature.proxy.domain

import android.content.Context
import android.os.Build
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import java.io.IOException
import com.mini.me_core.feature.proxy.data.PROXY_NS
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.yaml.snakeyaml.Yaml
import javax.inject.Inject
import javax.inject.Singleton

/** 当前代理运行状态（供 UI 与工具 status 共用）。 */
data class ProxyRuntimeState(
    val enabled: Boolean = false,
    val mode: String = "rule",
    val activeProfileId: String? = null,
    val mixedHost: String = "127.0.0.1",
    val mixedPort: Int = ClashProxyManager.MIXED_PORT,
    val controllerHost: String = "127.0.0.1",
    val controllerPort: Int = ClashProxyManager.CONTROLLER_PORT,
    /** 控制器最近一次是否连得上 mihomo。 */
    val controllerReachable: Boolean = false,
    /** P0-3：内核崩溃后正在指数退避自动恢复中（UI 显示「内核异常恢复中」）。 */
    val recovering: Boolean = false,
    /** P0-3：当前是第几次崩溃自动重启（1 起；recovering=true 时有效）。 */
    val recoveryAttempt: Int = 0,
)

/** 单个代理节点（解析自 Clash 配置，仅展示用）。 */
data class ProxyNodeInfo(
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
)

/** 一份 Clash 配置的解析概览（供预检 / 节点列表展示）。 */
data class ClashConfigSummary(
    val ok: Boolean,
    val nodes: List<ProxyNodeInfo> = emptyList(),
    val groups: List<String> = emptyList(),
    val providerCount: Int = 0,
    val error: String? = null,
)

/** mihomo /proxies 运行态中的一个分组（对齐 Clash 分组树：类型/选中项/成员/健康检查延迟）。 */
data class ProxyGroupInfo(
    val name: String,
    val type: String,
    val now: String?,
    val all: List<String>,
    val delay: Long?,
)

/** /proxies 全量快照（分组树 + 节点数），UI 与工具共用。 */
data class ClashProxiesSnapshot(
    val groups: List<ProxyGroupInfo>,
    val nodeCount: Int,
)

/** /traffic WS 推流的一条速率（字节/秒），实时流量展示用。 */
data class ProxyTraffic(
    val up: Long,
    val down: Long,
)

/**
 * 容器内 mihomo 代理引擎管理器（生命周期仿 [com.mini.me_core.feature.agent.domain.container.bridge.RcbBridge]）。
 *
 * 职责分层（《网络代理设计 v1.0》§2.3 / §5）：
 *  - **配置合成**：订阅/手动 YAML → `synthesizeConfig()` 叠加**固定覆盖块**（mixed-port 7890、
 *    external-controller 127.0.0.1:9090 + secret、allow-lan false、mode rule、DIRECT 兜底），并剥掉
 *    源配置里的危险键（listen/port 等），产物写 `filesDir/minime/proxy/config.yaml`。
 *  - **env 注入**：`exportContainerEnv()` 供 [LinuxContainerEngine.buildContainerEnv] 并入，让容器内
 *    进程的 http/https/all_proxy 指向 127.0.0.1:7890（容器与宿主共享 loopback，同一 mihomo 实例）。
 *  - **控制面**：`controllerRequest()` 打 mihomo external-controller REST（secret 鉴权），供
 *    `status/list_proxies/select/latency` 用；连不上即视为未运行。
 *
 * 说明：mihomo 二进制本身放入容器并在容器内拉起属于容器初始化层（根侧），本管理器只负责
 * 状态机、配置产物与 env/clc control 面；容器内新终端会因 env 注入直接吃到代理。
 */
@Singleton
class ClashProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    private val repository: ProxySettingsRepository,
    private val routeHolder: ProxyRouteHolder,
    private val credentialEncryptor: CredentialEncryptor,
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "ClashProxyManager"
        const val MIXED_PORT = 7890
        const val CONTROLLER_PORT = 9090
        const val CONTROLLER_HOST = "127.0.0.1"
        private const val CONFIG_DIR = "proxy"
        private const val CONFIG_FILE = "config.yaml"
        private const val SECRET_FILE = "secret"

        /** P0-5：control secret 在 KVStore 中的键（密文形式，与订阅 secret 同一套 CredentialEncryptor）。 */
        private const val CONTROL_SECRET_KEY = "control_secret"

        /** 被覆盖块接管、需从源配置剥离的顶层键（避免与 fixed override 冲突或被恶意夹带）。 */
        val OVERRIDDEN_KEYS = listOf(
            "mixed-port", "port", "socks-port", "redir-port",
            "tproxy-port", "external-controller", "external-ui",
            "secret", "allow-lan", "bind-address", "mode",
            // P0-2：危险/不可信顶层键补全剥离。dns 由固定防泄露块接管；script 可执行内联脚本；
            // profile 控制持久化行为；geodata-mode 影响 geoip 判定。这些键此前只在 dangerScan
            // 提示而未实际剥离，订阅可夹带恶意配置绕过沙箱，现统一在合成阶段剥离。
            "dns", "script", "profile", "geodata-mode"
        )

        /** /proxies 中被视为「分组」的 type：mihomo 五类策略组 + 内置直达/拒绝等（对齐 Clash 分组树）。 */
        val GROUP_TYPES = setOf(
            "Selector", "URLTest", "Fallback", "LoadBalance", "Relay",
            "Direct", "Reject", "RejectDrop", "Compatible", "Pass"
        )

        /**
         * 拉取订阅用的 User-Agent。实测该订阅商（nginx）按 UA 白名单放行：
         * 非 Clash 系 UA（如通用浏览器/curl/自研 UA）直接回 406 Not Acceptable（HTML 错误页），
         * Clash 系 UA（clash.meta / ClashMetaForAndroid/...）才回 200 + 完整 YAML。
         * 故伪装成 mihomo 自身默认订阅 UA，保证订阅在程序内与 Clash 表现一致。
         */
        const val SUBSCRIPTION_USER_AGENT = "clash.meta"

        /**
         * mihomo 内核（Clash Meta）发布版本与二进制资产。
         *
         * 选择 **android 构建**（与 ClashMetaForAndroid 同源）：可直接作为 App 子进程运行，
         * 绑定 127.0.0.1:7890 —— App 网络栈与容器进程共享 loopback，同一实例两侧共用。
         * SHA256 为对应 `.gz` 的官方校验和（发布页 sha256sums 同值），版本固定避免运行时
         * 探测 GitHub API 引入不确定性与降级风险；旧版本资产长期保留，故固定版本安全。
         *
         * 资产命名：arm64 → `mihomo-android-arm64-v8-…`；x86_64 → `mihomo-android-amd64-…`
         * （`-amd64-v1/-v2/-v3` 只是 **linux** 构建的 CPU 档位命名，android 构建统一用 `-amd64-`）。
         */
        const val MIHOMO_VERSION = "v1.19.13"
        const val MIHOMO_BASE_URL = "https://github.com/MetaCubeX/mihomo/releases/download/$MIHOMO_VERSION/"
        const val MIHOMO_ARM64_ASSET = "mihomo-android-arm64-v8-$MIHOMO_VERSION.gz"
        const val MIHOMO_ARM64_GZ_SHA256 = "c896cbe91344124da0c8e0b93d77a11fae53fc16f49b1b8cd238b5008e336e5b"
        const val MIHOMO_AMD64_ASSET = "mihomo-android-amd64-$MIHOMO_VERSION.gz"
        const val MIHOMO_AMD64_GZ_SHA256 = "f930e62c24f6f6ae18790282963d47eadaeed61346a8d869ca899acdb8c7cf29"

        /** P0-3：崩溃后最多自动重启次数（退避 1s→2s→4s 共 3 次，之后标记不可用）。 */
        private const val MAX_CRASH_RESTARTS = 3
    }

    private val _state = MutableStateFlow(ProxyRuntimeState())
    val state: StateFlow<ProxyRuntimeState> = _state.asStateFlow()

    /** 串行化内核的启动/停止，避免 on() 与启动时自动恢复并发双拉。 */
    private val kernelMutex = Mutex()

    /** 当前 mihomo 内核子进程（App 子进程，绑定 127.0.0.1:7890；App 进程存活则内核存活）。 */
    @Volatile
    private var mihomoProcess: Process? = null

    /** P0-3：连续崩溃自动重启次数（成功拉起后清零；超过 [MAX_CRASH_RESTARTS] 放弃并标记不可用）。 */
    @Volatile
    private var crashRestartAttempts: Int = 0

    /** P0-3：是否正在执行崩溃自动重启循环，防止退出监视协程重复调度。 */
    @Volatile
    private var crashRestartInProgress: Boolean = false

    /**
     * 下载 mihomo 二进制用的**直连** OkHttp：强制 Proxy.NO_PROXY 覆盖共享 client 的 ProxySelector。
     * 内核二进制属于基础设施，必须绕过代理自举（代理未起/代理本身被墙都不能成为下载失败原因）。
     */
    private val directClient: OkHttpClient by lazy {
        okHttp.newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
    }

    /** env 注入/控制器读取的安全鉴权令牌（app 运行时生成并落盘，跨进程一致）。 */
    @Volatile
    private var secret: String = ""

    /** P0-5：secret 异步加载完成信号（加密存储需在协程中解密；suspend 调用方据此等待）。 */
    private val secretReady = CompletableDeferred<Unit>()

    /** 全局开关缓存：buildContainerEnv 是同步方法，不能在其中挂起读 DataStore，故用 flow 预热。 */
    @Volatile
    private var enabledCache: Boolean = false

    init {
        // P0-5：secret 改为加密存储（CredentialEncryptor + KVStore），需在协程中异步解密。
        // 必须先于任何内核拉起/控制面请求完成——on()/controllerRequest 等 suspend 入口会 await secretReady。
        secret = ""
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { loadOrCreateSecretEncrypted() }
                .onSuccess { s -> secret = s }
                .onFailure { FileLogger.w(TAG, "加载加密 control secret 失败: ${it.message}") }
            secretReady.complete(Unit)

            // 首帧：上次若为启用态，先拉起内核、确认控制面就绪，再同步开关。
            // 顺序不可反——先置 enabled 会让共享 OkHttp 把流量打进还没人监听的 7890（对应
            // ModelMetadataService 那类 `Failed to connect to /127.0.0.1:7890`），必须内核先起。
            val initiallyEnabled = repository.isProxyEnabled()
            if (initiallyEnabled) {
                // 内核以 -f config.yaml 启动，配置缺失会瞬时退出（code=1）——先兜底重建再拉起。
                val cfgFile = java.io.File(configDir(), CONFIG_FILE)
                if (!cfgFile.isFile) {
                    val rebuilt = rebuildConfigFromActiveProfile()
                    FileLogger.i(
                        TAG,
                        if (rebuilt) "启动时重写丢失的 config.yaml"
                        else "config.yaml 缺失且无法从活跃 profile 重建，跳过自动恢复"
                    )
                }
                val ok = ensureKernelRunning(restart = false)
                enabledCache = ok
                _state.update { it.copy(enabled = ok) }
                routeHolder.update(ok, "127.0.0.1:$MIXED_PORT")
                FileLogger.i(
                    TAG,
                    if (ok) "启动时自动恢复 mihomo 内核成功"
                    else "启动时自动恢复 mihomo 内核失败（保持代理关闭，避免把网络流量打进未监听的端口）"
                )
            } else {
                routeHolder.update(false, "127.0.0.1:$MIXED_PORT")
            }
            // 之后的开关变化继续由 flow 驱动
            repository.proxyEnabledFlow.drop(1).collect { e ->
                enabledCache = e
                _state.update { it.copy(enabled = e) }
                // 同步给 App 网络层的路由开关写位，让共享 OkHttp 的 ProxySelector 感知（§4.2）
                routeHolder.update(e, "127.0.0.1:$MIXED_PORT")
            }
        }
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            repository.activeProfileIdFlow.collect { id ->
                _state.update { it.copy(activeProfileId = id) }
            }
        }
        // 网络层优化 C5：AI 接口直连/代理分流开关 → 同步给 App 网络层路由写位（默认关 = 全走代理）
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            repository.aiHostsDirectFlow.collect { direct ->
                routeHolder.setAiHostsDirect(direct)
            }
        }
        // P1-7：订阅自动更新周期检查（每 15 分钟扫描一次，按各 profile 的 updateIntervalHours 决定是否到期）。
        // 沿用 appScope 协程周期循环模式（与 scheduleScheduler 一致）；mihomo 仅在本进程存活时运行，
        // 进程死亡时热重载无意义，故不引入 WorkManager。
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            // 启动后先等 secret 就绪，再开始周期扫描
            secretReady.await()
            while (true) {
                delay(15 * 60 * 1000L)
                runCatching { autoUpdateDueSubscriptions() }
                    .onFailure { FileLogger.w(TAG, "订阅自动更新周期扫描异常: ${it.message}") }
            }
        }
    }

    /** 供容器/上层读取的启用态（同步、非挂起）。 */
    fun isEnabled(): Boolean = enabledCache

    /** P1-9：mihomo 内核进程是否存活（诊断用，同步非挂起）。 */
    fun isKernelAlive(): Boolean = mihomoProcess?.isAlive == true

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

    /** 控制器地址 "127.0.0.1:port"。 */
    fun controllerAddress(): String = "$CONTROLLER_HOST:$CONTROLLER_PORT"

    fun controllerSecret(): String = secret

    /** P0-5：等待加密 secret 加载完成（所有用到 [secret] 的 suspend 入口先调用）。 */
    private suspend fun ensureSecretLoaded() {
        if (secret.isEmpty()) secretReady.await()
    }

    private fun configDir(): java.io.File =
        java.io.File(java.io.File(context.filesDir, "minime"), CONFIG_DIR)

    /**
     * P0-5：加载或创建 control secret，加密存储在 KVStore（与订阅 secret 同一套 CredentialEncryptor）。
     *
     * 迁移路径：
     *  1. KVStore 已有密文 → 解密返回；
     *  2. 否则旧版明文文件 `filesDir/minime/proxy/secret` 仍在 → 读明文、加密写 KV、删明文文件；
     *  3. 都没有 → 生成新随机 secret，加密写 KV。
     * 避免把鉴权令牌以明文落在 filesDir（任意拿到 App 沙箱读权限的进程/备份可读）。
     */
    private suspend fun loadOrCreateSecretEncrypted(): String {
        // 1. 已迁移到加密存储
        kv.getString(PROXY_NS, CONTROL_SECRET_KEY)?.takeIf { it.isNotBlank() }?.let { cipher ->
            runCatching { credentialEncryptor.decrypt(cipher, "proxy_control_secret") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            FileLogger.w(TAG, "KV 中的 control secret 解密失败，回退到明文文件/重建")
        }
        // 2. 旧版明文文件迁移
        val dir = configDir().apply { mkdirs() }
        val legacy = java.io.File(dir, SECRET_FILE)
        if (legacy.isFile) {
            val plain = runCatching { legacy.readText().trim() }.getOrNull().orEmpty()
            if (plain.isNotBlank()) {
                runCatching {
                    val cipher = credentialEncryptor.encrypt(plain)
                    kv.putString(PROXY_NS, CONTROL_SECRET_KEY, cipher)
                    legacy.delete()
                }
                FileLogger.i(TAG, "已把旧明文 control secret 迁移到加密存储并删除明文文件")
                return plain
            }
        }
        // 3. 生成新 secret
        val s = generateRandomSecret()
        runCatching {
            val cipher = credentialEncryptor.encrypt(s)
            kv.putString(PROXY_NS, CONTROL_SECRET_KEY, cipher)
        }.onFailure { FileLogger.w(TAG, "加密保存 control secret 失败: ${it.message}") }
        FileLogger.i(TAG, "已生成新的代理 control secret（加密存储）")
        return s
    }

    private fun generateRandomSecret(): String {
        val pool = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (0 until 24).joinToString("") { pool[kotlin.random.Random.nextInt(pool.length)].toString() }
    }

    /**
     * 合成 mihomo 配置：固定覆盖块头 + 清洗后的源配置体。
     *
     * 用真实 YAML 解析而非逐行正则清洗：旧实现按「行首命中 OVERRIDDEN_KEYS 就删整行」，
     * 会把**块式**代理节点里的嵌套键（如 `    port: 443`、`mode:`、`secret:`）误删，导致
     * mihomo 加载配置 FATAL 秒退（code=1，且 log-level 静默时无任何日志可查）。
     * 改为解析成 Map 后仅移除顶层危险键再 dump 回 YAML，嵌套结构不受损。
     *
     * 源不是 YAML 映射（订阅回 HTML/裸文本等）时，退化为仅 DIRECT 兜底的空配置并告警，
     * 避免 mihomo 因配置不可解析而秒退。
     */
    fun synthesizeConfig(sourceYaml: String): String {
        val clean = LinkedHashMap<Any?, Any?>()
        try {
            val root = Yaml().load<Any?>(sourceYaml)
            if (root is Map<*, *>) {
                root.forEach { (k, v) ->
                    val key = k?.toString() ?: return@forEach
                    if (key !in OVERRIDDEN_KEYS) clean[key] = v
                }
            } else {
                FileLogger.w(
                    TAG,
                    "订阅源不是 YAML 映射（实际为 ${root?.javaClass?.simpleName ?: "null"}），仅生成 DIRECT 兜底配置"
                )
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "订阅源 YAML 解析失败（${e.message}），仅生成 DIRECT 兜底配置")
        }
        if (clean["rules"] == null) clean["rules"] = listOf("MATCH,DIRECT")
        // DNS 防泄露（P0-1）：无论订阅自带什么 dns 配置，一律覆盖为 fake-ip + 国内 DoH 主 / 海外 DoH
        // fallback。fake-ip 模式下命中代理规则的域名由远端代理解析，本机不再发出明文 DNS 查询；
        // fallback-filter 按 geoip=CN 分流：国内域名走国内 DoH，海外域名走 Cloudflare DoH，
        // 避免「订阅商 DNS 被劫持 / 本机 DNS 泄露真实访问目标」。
        clean["dns"] = buildFixedDnsConfig()
        val body = Yaml().dump(clean)
        return buildString {
            appendLine("mixed-port: $MIXED_PORT")
            appendLine("allow-lan: false")
            appendLine("mode: rule")
            // info 而非 silent：内核启动期 FATAL/错误必须落到 mihomo.log，否则秒退原因完全不可见。
            appendLine("log-level: info")
            appendLine("external-controller: $CONTROLLER_HOST:$CONTROLLER_PORT")
            appendLine("secret: \"$secret\"")
            appendLine()
            append(body.trimEnd('\n'))
            appendLine()
        }
    }

    /**
     * 固定 DNS 配置（P0-1 防泄露）。
     *
     * - enhanced-mode=fake-ip：命中代理规则的域名直接返回 198.18.0.0/16 假地址，真实域名由远端代理
     *   出口解析，本机不发明文 DNS；
     * - default-nameserver：纯 IP 明文 DNS，仅用于引导 DoH 域名本身的解析（bootstrap），
     *   用国内可达的阿里/腾讯 DoH 前置解析；
     * - nameserver（主）：国内 DoH（阿里 223.5.5.5 / 腾讯 1.12.12.12），国内域名低延迟；
     * - fallback（备）：海外 DoH（Cloudflare 1.1.1.1 / Google 8.8.8.8），国内主 DNS 被污染时兜底；
     * - fallback-filter.geoip-code=CN：仅当解析结果地理归属不在 CN 时采用 fallback 答案，
     *   实现「国内域名国内解、海外域名海外解」的分流，避免 DNS 泄露。
     * - ipv6=false：关闭 IPv6 DNS，避免 IPv6 隧道绕过代理造成泄露。
     */
    private fun buildFixedDnsConfig(): Map<String, Any?> = linkedMapOf(
        "enable" to true,
        "ipv6" to false,
        "enhanced-mode" to "fake-ip",
        "fake-ip-range" to "198.18.0.1/16",
        "default-nameserver" to listOf("223.5.5.5", "119.29.29.29"),
        "nameserver" to listOf(
            "https://223.5.5.5/dns-query",
            "https://1.12.12.12/dns-query",
        ),
        "fallback" to listOf(
            "https://1.1.1.1/dns-query",
            "https://8.8.8.8/dns-query",
        ),
        "fallback-filter" to linkedMapOf(
            "geoip" to true,
            "geoip-code" to "CN",
            "ipcidr" to listOf("240.0.0.0/4"),
        ),
        "fake-ip-filter" to listOf(
            "*.lan",
            "*.local",
            "+.internal",
            "localhost.ptlogin2.qq.com",
        ),
    )

    /** 订阅 URL 全文抓取（拉取远端订阅 YAML）。失败返回 null。 */
    suspend fun fetchSubscriptionYaml(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url)
                .header("User-Agent", SUBSCRIPTION_USER_AGENT)
                .get().build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful || resp.body == null) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    /**
     * P1-7：手动/自动刷新某个订阅型 profile。
     *
     * 拉取远端最新 YAML → 更新 updatedAt（URL 不变故 cipher 不换）→ 若该 profile 正是当前活跃且内核
     * 在跑，热重载新配置（不杀进程）。manual 型 / 拉取失败返回 false，不影响其他 profile。
     */
    suspend fun refreshSubscriptionNow(id: String): Boolean {
        ensureSecretLoaded()
        val sub = repository.subscriptionsFlow.first().firstOrNull { it.id == id } ?: return false
        if (sub.kind != ProxySubscription.KIND_SUBSCRIPTION) return false
        val url = repository.revealSecret(id)?.trim() ?: return false
        val fresh = fetchSubscriptionYaml(url) ?: run {
            FileLogger.w(TAG, "刷新订阅 $id 失败：拉取 YAML 返回空")
            return false
        }
        repository.refreshSubscription(id, url)
        val activeId = repository.activeProfileIdFlow.first()
        if (activeId == id && enabledCache && mihomoProcess?.isAlive == true) {
            val config = synthesizeConfig(fresh)
            writeConfigFile(config)
            reloadConfig(config)
            FileLogger.i(TAG, "活跃订阅 $id 已热重载最新配置")
        }
        FileLogger.i(TAG, "订阅 $id 更新成功（updatedAt=${System.currentTimeMillis()}）")
        return true
    }

    /** P1-7：周期检查所有 autoUpdate 订阅型 profile 是否到期，逐个刷新（失败隔离，不影响其他）。 */
    private suspend fun autoUpdateDueSubscriptions() {
        val now = System.currentTimeMillis()
        val subs = runCatching { repository.subscriptionsFlow.first() }.getOrDefault(emptyList())
        subs.filter { it.kind == ProxySubscription.KIND_SUBSCRIPTION && it.autoUpdate }.forEach { sub ->
            val elapsed = if (sub.updatedAt == 0L) Long.MAX_VALUE else now - sub.updatedAt
            val due = elapsed >= sub.updateIntervalHours * 3600_000L
            if (due) {
                runCatching { refreshSubscriptionNow(sub.id) }
                    .onFailure { FileLogger.w(TAG, "自动更新订阅 ${sub.id} 失败: ${it.message}") }
            }
        }
    }

    /**
     * 用 SnakeYAML 解析 Clash 配置，统计内联节点 / 分组 / proxy-provider。
     *
     * 修复「解析 OK 但节点 0」：旧实现用正则 `^\s*-\s+name:` 只匹配块式 `- name: …`；
     * 现实订阅多为**流式** `- {name: …, type: ss, …}` 或直接走 **proxy-provider**（动态节点池），
     * 正则都数不出来，导致节点数恒为 0。改为真实 YAML 解析后块式/流式/provider 一并对齐 Clash。
     */
    fun parseClashConfig(yaml: String): ClashConfigSummary {
        return try {
            val root = Yaml().load<Any?>(yaml)
            if (root !is Map<*, *>) {
                return ClashConfigSummary(ok = false, error = "不是合法的 YAML 映射")
            }
            val proxies = root["proxies"] as? List<*> ?: emptyList<Any?>()
            val groups = root["proxy-groups"] as? List<*> ?: emptyList<Any?>()
            val providers = root["proxy-providers"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val nodes = proxies.mapNotNull { item ->
                if (item !is Map<*, *>) return@mapNotNull null
                val name = item["name"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                ProxyNodeInfo(
                    name = name,
                    type = item["type"]?.toString() ?: "unknown",
                    server = item["server"]?.toString() ?: "",
                    port = (item["port"] as? Number)?.toInt()
                        ?: item["port"]?.toString()?.toIntOrNull() ?: 0,
                )
            }
            val groupNames = groups.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
            ClashConfigSummary(ok = true, nodes = nodes, groups = groupNames, providerCount = providers.size)
        } catch (e: Exception) {
            ClashConfigSummary(ok = false, error = e.message ?: "YAML 解析失败")
        }
    }

    /**
     * 单节点测速（对齐 Clash：只走内核真实出口，不做 App 直连近似）。
     *
     * 走 mihomo REST `/proxies/{node}/delay?url=generate_204&timeout=5000`，与 ClashMetaForAndroid
     * 同源：由节点在运行配置下真实访问目标 URL 得出延迟。
     *  - 成功 → 毫秒延迟；
     *  - 节点超时（mihomo 返回 504）/ 控制器不可达 → null，由调用方按「超时」展示。
     *
     * 注意：旧实现里「代理未启用时用 Socket 直连 server:port 作近似」在节点服务器几乎都在海外时
     * 必然 connect 超时，导致「测速全部超时」；且该近似测得的是 TCP 握手而非代理出口延迟，与 Clash
     * 语义不符，故移除。调用方必须先把被测配置加载进内核（[on]）再测，否则 REST 会因节点不存在报错。
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

    /** 把合成配置落盘到 filesDir/minime/proxy/config.yaml。 */
    suspend fun writeConfigFile(configYaml: String) = withContext(Dispatchers.IO) {
        runCatching {
            val dir = configDir().apply { mkdirs() }
            java.io.File(dir, CONFIG_FILE).writeText(configYaml, Charsets.UTF_8)
            FileLogger.i(TAG, "proxy config 已写入 ${java.io.File(dir, CONFIG_FILE).absolutePath}")
        }.onFailure { FileLogger.w(TAG, "写 proxy config 失败: ${it.message}") }
    }

    /**
     * 启动时自动恢复用：config.yaml 缺失时，从活跃 profile 重新合成并落盘（尽力而为）。
     * 订阅型需重新拉远端 YAML，网络不可达时返回 false，自动恢复跳过。
     */
    private suspend fun rebuildConfigFromActiveProfile(): Boolean {
        ensureSecretLoaded()
        val activeId = repository.activeProfileIdFlow.first() ?: return false
        return try {
            val revealed = repository.revealSecret(activeId) ?: return false
            val trimmed = revealed.trim()
            val source = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                fetchSubscriptionYaml(trimmed) ?: return false
            } else {
                revealed
            }
            if (source.isBlank()) return false
            writeConfigFile(synthesizeConfig(source))
            true
        } catch (t: Throwable) {
            FileLogger.w(TAG, "启动重建 config.yaml 失败: ${t.message}")
            false
        }
    }

    /** 打 mimomo external-controller REST；成功返回 body，失败返回 null（视为未运行/网络错）。 */
    suspend fun controllerRequest(
        method: String,
        path: String,
        body: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        ensureSecretLoaded()
        val url = "http://$CONTROLLER_HOST:$CONTROLLER_PORT$path"
        val requestBody = body?.toRequestBody("application/json".toMediaType())
            ?: ByteArray(0).toRequestBody(null)
        val req = when (method) {
            "GET" -> Request.Builder().url(url).header("Authorization", "Bearer $secret").get().build()
            "PUT" -> Request.Builder().url(url).header("Authorization", "Bearer $secret").put(requestBody).build()
            "PATCH" -> Request.Builder().url(url).header("Authorization", "Bearer $secret").patch(requestBody).build()
            else -> return@withContext null
        }
        runCatching {
            okHttp.newCall(req).execute().use { resp ->
                // 能拿到任何 HTTP 响应（即使 4xx/5xx）都说明控制器进程在监听 → reachable=true。
                _state.update { it.copy(controllerReachable = true) }
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.onFailure {
            // 连接被拒/超时/网络错 → 控制器不可达，及时翻为 false（否则状态会残留为上一次的 true）。
            _state.update { it.copy(controllerReachable = false) }
        }.getOrNull()
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

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
                if (type !in GROUP_TYPES) return@mapNotNull null
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
     * 仅当 mixed-port/external-controller/secret 等固定覆盖块变化（本实现里这些恒不变）或热重载失败时，
     * 才需要回退到重启内核。
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
        FileLogger.i(TAG, if (ok) "mihomo 热重载配置成功" else "mihomo 热重载配置失败")
        return ok
    }

    /**
     * /traffic WS 实时推流（零轮询，与 mihomo/CMA 的 flow 数据源一致）。
     * 每次 collect 建立一条 WS；collect 取消即关闭（awaitClose 里 ws.cancel()），失败自动结束流。
     */
    fun trafficFlow(): Flow<ProxyTraffic> = callbackFlow {
        ensureSecretLoaded()
        val req = Request.Builder()
            .url("http://$CONTROLLER_HOST:$CONTROLLER_PORT/traffic")
            .header("Authorization", "Bearer $secret")
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

    // ============================ mihomo 内核生命周期 ============================

    private fun mihomoDir(): java.io.File = java.io.File(configDir(), "mihomo").apply { mkdirs() }

    private fun mihomoBinary(): java.io.File = java.io.File(mihomoDir(), "mihomo")

    /** 按宿主 ABI 挑选 android 构建资产（+ 官方 SHA256）；不支持的 ABI 返回 null。 */
    private fun pickMihomoAsset(): Pair<String, String>? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.startsWith("arm64") -> MIHOMO_ARM64_ASSET to MIHOMO_ARM64_GZ_SHA256
            abi.startsWith("x86_64") -> MIHOMO_AMD64_ASSET to MIHOMO_AMD64_GZ_SHA256
            else -> null
        }
    }

    /**
     * 下载并校验 mihomo 内核二进制（首次使用触发，已存在且体积正常则跳过）。
     * 走 [directClient]（Proxy.NO_PROXY）**自举**：内核属于基础设施，代理未起/代理自身不可达
     * 都不能成为下载失败的原因，故必须绕过代理直接拉 GitHub。
     * @return null=就绪；非 null=失败原因（供调用方回显）。
     */
    private suspend fun downloadMihomoIfNeeded(): String? = withContext(Dispatchers.IO) {
        val (asset, expectedSha) = pickMihomoAsset()
            ?: return@withContext "不支持的 CPU 架构：${Build.SUPPORTED_ABIS.firstOrNull()}"
        val bin = mihomoBinary()
        if (bin.isFile && bin.length() > 1_000_000) return@withContext null
        val dir = mihomoDir()
        val gz = java.io.File(dir, asset)
        runCatching {
            val req = Request.Builder().url(MIHOMO_BASE_URL + asset).get().build()
            directClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful || resp.body == null) {
                    throw IOException("HTTP ${resp.code}")
                }
                val md = MessageDigest.getInstance("SHA-256")
                resp.body?.byteStream()?.use { input ->
                    gz.outputStream().buffered().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            md.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (!hex.equals(expectedSha, ignoreCase = true)) {
                    throw IOException("SHA256 校验失败（期望 $expectedSha，实际 $hex）")
                }
            }
        }.onFailure {
            gz.delete()
            FileLogger.w(TAG, "下载 mihomo 失败: ${it.message}")
            return@withContext "下载 mihomo 失败：${it.message}"
        }
        // 校验通过 → 解压 gz
        runCatching {
            GZIPInputStream(gz.inputStream().buffered()).use { gzip ->
                bin.outputStream().buffered().use { out -> gzip.copyTo(out, 64 * 1024) }
            }
            gz.delete()
            if (!bin.setExecutable(true, false)) {
                FileLogger.w(TAG, "mihomo setExecutable 返回 false（可能仍可执行）")
            }
            FileLogger.i(TAG, "mihomo 内核就绪：${bin.absolutePath}")
        }.onFailure {
            bin.delete()
            FileLogger.w(TAG, "解压 mihomo 失败: ${it.message}")
            return@withContext "解压 mihomo 失败：${it.message}"
        }
        null
    }

    /** 拉启 mihomo 子进程（App 子进程，绑定 127.0.0.1:7890；App 进程存活则内核存活）。 */
    private fun startKernelProcess(): Boolean {
        val bin = mihomoBinary()
        if (!bin.isFile) return false
        return runCatching {
            val dir = configDir()
            val cfgFile = java.io.File(dir, CONFIG_FILE)
            // 配置必须先就绪再启动：mihomo 找不到 config.yaml 会瞬时退出（code=1）。
            // 时序上 on() 是先落盘再启内核，此处守卫主要拦「启动时自动恢复」路径的陈旧/缺失配置。
            if (!cfgFile.isFile) {
                FileLogger.w(TAG, "config.yaml 不存在（${cfgFile.absolutePath}），跳过启动 mihomo")
                return@runCatching false
            }
            // 内容校验：落盘文件必须能解析为 YAML 映射，否则 mihomo 必然 FATAL 秒退（code=1）。
            // 自动恢复路径加载的是旧会话残留的 config.yaml，可能已损坏，启前拦截并给出明确提示。
            if (!validateConfigFile(cfgFile)) {
                FileLogger.w(TAG, "config.yaml 内容非法（不可解析为 YAML 映射），跳过启动 mihomo")
                return@runCatching false
            }
            val logFile = java.io.File(dir, "mihomo.log")
            val pb = ProcessBuilder(bin.absolutePath, "-d", dir.absolutePath, "-f", cfgFile.absolutePath)
            pb.redirectErrorStream(true)
            pb.redirectOutput(logFile)
            val p = pb.start()
            mihomoProcess = p
            FileLogger.i(TAG, "mihomo 内核已启动（log=${logFile.absolutePath}）")
            // 监视退出（waitFor 阻塞一个 IO 线程，进程存活期间让渡；进程意外退出时清空句柄并告警，
            // 避免残留「伪运行」状态。不能用 Process.onExit()——Android 未提供该 Java 9 API。）
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                p.waitFor()
                if (mihomoProcess === p) {
                    mihomoProcess = null
                    FileLogger.w(TAG, "mihomo 内核进程退出 code=${p.exitValue()}（详见 mihomo.log）")
                    logKernelLogTail()
                    // P0-3：用户仍要求代理开启时，指数退避自动重启（1s→2s→4s，最多 3 次）。
                    // 主动 off()/on() 重启路径会先把 mihomoProcess 置 null，故此处 mihomoProcess===p
                    // 即「非主动关闭的异常退出」，才进入自恢复。
                    if (enabledCache && !crashRestartInProgress) {
                        scheduleCrashRestart()
                    }
                }
            }
            true
        }.onFailure {
            FileLogger.w(TAG, "启动 mihomo 内核失败: ${it.message}")
            false
        }.getOrDefault(false)
    }

    /** 落盘 config.yaml 是否能解析为 YAML 映射（mihomo 加载前的最后一道闸，防「配损坏 → 秒退」）。 */
    private fun validateConfigFile(f: java.io.File): Boolean = runCatching {
        val root = Yaml().load<Any?>(f.readText(Charsets.UTF_8))
        root is Map<*, *>
    }.getOrDefault(false)

    /** 把 mihomo.log 尾部打进 FileLogger，便于在日志里直接看到内核报错（config 解析/端口占用/geodata 缺失等）。 */
    private fun logKernelLogTail(maxChars: Int = 4000) {
        runCatching {
            val f = java.io.File(configDir(), "mihomo.log")
            if (!f.isFile) {
                FileLogger.w(TAG, "mihomo.log 不存在，无法读取内核退出原因")
                return
            }
            val bytes = f.readBytes()
            val start = (bytes.size - maxChars).coerceAtLeast(0)
            // takeLast 会退回 List<Byte>，无 toString(Charset)；用 copyOfRange 保持 ByteArray。
            val tail = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
            if (tail.isBlank()) {
                FileLogger.w(TAG, "mihomo.log 为空（log-level 静默或内核未写任何日志），无法定位退出原因")
            } else {
                FileLogger.w(TAG, "--- mihomo.log tail ---\n$tail")
            }
        }
    }

    /** 轮询 external-controller 直至可达（[retries] 次 × 500ms）；内核进程已死则立即失败。 */
    private suspend fun waitControllerReady(retries: Int): Boolean {
        repeat(retries) {
            if (mihomoProcess?.isAlive == false) return false
            if (controllerRequest("GET", "/configs") != null) return true
            delay(500)
        }
        return false
    }

    /** 在 [kernelMutex] 内确保内核运行；[restart]=true 时先停旧进程再起（加载新配置）。 */
    private suspend fun ensureKernelRunning(restart: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            kernelMutex.withLock {
                if (restart) stopKernelLocked()
                val alive = mihomoProcess?.isAlive == true
                if (!alive) {
                    // 首次使用需先自举下载并校验内核二进制（已存在则直接跳过）
                    val err = downloadMihomoIfNeeded()
                    if (err != null) {
                        FileLogger.w(TAG, err)
                        return@withLock false
                    }
                    if (!startKernelProcess()) return@withLock false
                    // 秒退保护：进程启动后瞬时退出（config 缺失/解析失败/端口占用/geodata 缺失）
                    // 直接读日志并失败返回，不再空轮询 10s 等一个已死的进程（也避免死占 kernelMutex
                    // 阻塞后续 on()）。
                    delay(300)
                    if (mihomoProcess?.isAlive != true) {
                        logKernelLogTail()
                        return@withLock false
                    }
                }
                waitControllerReady(20)
            }
        }

    private suspend fun stopKernel() = withContext(Dispatchers.IO) {
        kernelMutex.withLock { stopKernelLocked() }
    }

    private fun stopKernelLocked() {
        val p = mihomoProcess ?: return
        mihomoProcess = null
        if (!p.isAlive) return
        runCatching { p.destroy() }
        val deadline = System.currentTimeMillis() + 1500
        while (p.isAlive && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (p.isAlive) runCatching { p.destroyForcibly() }
        FileLogger.i(TAG, "mihomo 内核已停止")
    }

    /**
     * P0-3：mihomo 内核异常退出后的指数退避自动重启。
     *
     * 退避序列 1s → 2s → 4s，最多 [MAX_CRASH_RESTARTS] 次；每次拉起后轮询控制面就绪，
     * 成功则清零计数并恢复 `_state`；连续失败耗尽则把代理标记为不可用（enabled=false +
     * routeHolder 切直连兜底），避免把 App 流量持续打进无人监听的 7890。
     *
     * 全程持 [kernelMutex]，与 on()/off() 互斥；退避期间用户关闭代理会被 `enabledCache` 检查拦下。
     */
    private suspend fun scheduleCrashRestart() {
        crashRestartInProgress = true
        try {
            kernelMutex.withLock {
                // 双重确认：加锁期间用户可能已手动关闭/重开
                if (!enabledCache) return@withLock
                if (mihomoProcess?.isAlive == true) return@withLock

                while (crashRestartAttempts < MAX_CRASH_RESTARTS) {
                    crashRestartAttempts++
                    val backoffMs = when (crashRestartAttempts) {
                        1 -> 1000L
                        2 -> 2000L
                        else -> 4000L
                    }
                    _state.update { it.copy(recovering = true, recoveryAttempt = crashRestartAttempts) }
                    FileLogger.w(TAG, "mihomo 崩溃自动重启：第 $crashRestartAttempts/$MAX_CRASH_RESTARTS 次，${backoffMs}ms 后拉起")
                    delay(backoffMs)

                    // 退避窗口内用户关闭了代理 → 放弃
                    if (!enabledCache) {
                        FileLogger.i(TAG, "崩溃恢复：退避期间代理已被用户关闭，放弃自动重启")
                        _state.update { it.copy(recovering = false, recoveryAttempt = 0) }
                        return@withLock
                    }

                    // 配置丢失则尽力重建（订阅拉取失败则跳过，startKernelProcess 会因配置缺失而失败进入下一轮）
                    val cfgFile = java.io.File(configDir(), CONFIG_FILE)
                    if (!cfgFile.isFile) {
                        runCatching { rebuildConfigFromActiveProfile() }
                    }

                    if (!startKernelProcess()) {
                        FileLogger.w(TAG, "mihomo 崩溃自动重启：startKernelProcess 失败，进入下一轮退避")
                        continue
                    }
                    delay(300)
                    if (mihomoProcess?.isAlive != true) {
                        logKernelLogTail()
                        FileLogger.w(TAG, "mihomo 崩溃自动重启：进程秒退，进入下一轮退避")
                        continue
                    }
                    if (waitControllerReady(20)) {
                        // 成功：清零计数，通知 UI 恢复正常
                        crashRestartAttempts = 0
                        _state.update {
                            it.copy(recovering = false, recoveryAttempt = 0, controllerReachable = true)
                        }
                        FileLogger.i(TAG, "mihomo 崩溃自动重启成功")
                        return@withLock
                    } else {
                        FileLogger.w(TAG, "mihomo 崩溃自动重启：进程存活但控制面未就绪，进入下一轮退避")
                        // 进程可能稍后才崩，先停掉再重试，避免端口占用
                        stopKernelLocked()
                    }
                }

                // 耗尽重试：标记不可用
                FileLogger.w(TAG, "mihomo 连续 $MAX_CRASH_RESTARTS 次崩溃自动重启失败，标记为不可用")
                runCatching { repository.setProxyEnabled(false) }
                enabledCache = false
                _state.update {
                    it.copy(enabled = false, recovering = false, recoveryAttempt = 0, controllerReachable = false)
                }
                routeHolder.update(false, "127.0.0.1:$MIXED_PORT")
            }
        } catch (t: Throwable) {
            FileLogger.w(TAG, "崩溃自动重启异常: ${t.message}")
        } finally {
            crashRestartInProgress = false
        }
    }

    /** 拉起代理：由已播种 profile（[profileId]）或临时 inline（[inlineYaml]）合成配置、落盘、拉起内核、置开关。 */
    suspend fun on(profileId: String?, inlineYaml: String?): String {
        ensureSecretLoaded()
        if (profileId == null && inlineYaml == null) return "需要 profile_id 或 inline yaml"
        val source = when {
            inlineYaml != null -> inlineYaml
            else -> {
                val id = profileId ?: return "需要 profile_id 或 inline yaml"
                val revealed = repository.revealSecret(id)
                    ?: return "未找到已播种 profile：$id"
                // 订阅型 profile 存的是订阅 URL：需先拉取远端 YAML；手动型存的就是 YAML 原文。
                val trimmed = revealed.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    fetchSubscriptionYaml(trimmed) ?: return "订阅拉取失败：$profileId"
                } else {
                    revealed
                }
            }
        }
        if (source.isBlank()) return "配置为空"
        val config = synthesizeConfig(source)
        writeConfigFile(config)
        // 内核必须实际跑起来并让控制面就绪，才能把开关置为 enabled——
        // 否则 App 流量会被 routeHolder 打进无人监听的 7890（`Failed to connect to /127.0.0.1:7890`）。
        // P1-11：内核已在运行时优先热重载（不杀进程、不断连）；热重载失败才回退到 restart。
        val wasAlive = mihomoProcess?.isAlive == true
        val kernelOk = if (wasAlive) {
            val reloaded = reloadConfig(config)
            if (reloaded) true else {
                FileLogger.w(TAG, "on(): 热重载失败，回退到重启内核")
                ensureKernelRunning(restart = true)
            }
        } else {
            ensureKernelRunning(restart = false)
        }
        if (!kernelOk) {
            FileLogger.w(TAG, "on(): mihomo 内核未就绪，代理保持关闭")
            return "mihomo 内核启动失败（下载或启动异常，详见日志），代理未启用"
        }
        if (profileId != null) repository.setActiveProfile(profileId)
        repository.setProxyEnabled(true)
        // 同步写位：不等 DataStore flow 的异步 emit，消除「on() 返回后立刻发请求仍走直连」的竞态窗口。
        // 后续 flow 收集器也会写同一组值（幂等）。
        enabledCache = true
        // P0-3：用户手动拉起成功视为新会话，清零崩溃退避计数并清除恢复中状态。
        crashRestartAttempts = 0
        routeHolder.update(true, "127.0.0.1:$MIXED_PORT")
        _state.update { it.copy(enabled = true, activeProfileId = profileId, recovering = false, recoveryAttempt = 0) }
        // P2-14：进入前台保活通知。
        runCatching { ProxyForegroundService.start(context, MIXED_PORT) }
        FileLogger.i(TAG, "network_proxy ON (profile=$profileId inline=${inlineYaml != null})")
        return "ok"
    }

    /** 关闭代理：先停内核，再翻转开关（env 也随之不再注入）。 */
    suspend fun off() {
        stopKernel()
        repository.setProxyEnabled(false)
        // 同步写位（同 on()），不等 flow 异步 emit。
        enabledCache = false
        // P0-3：用户主动关闭，重置崩溃退避计数与恢复中状态。
        crashRestartAttempts = 0
        routeHolder.update(false, "127.0.0.1:$MIXED_PORT")
        _state.update { it.copy(enabled = false, recovering = false, recoveryAttempt = 0) }
        // P2-14：退出前台保活。
        runCatching { ProxyForegroundService.stop(context) }
        FileLogger.i(TAG, "network_proxy OFF")
    }

    /**
     * 容器内进程的代理环境变量。仅启用时非空；NO_PROXY 保护 loopback 与内网，避免代理接管后
     * 阻断本地服务（含 RCB_BRIDGE、容器内网、工作区同步）。
     */
    fun exportContainerEnv(): Map<String, String> {
        if (!enabledCache) return emptyMap()
        val proxy = "http://$CONTROLLER_HOST:$MIXED_PORT"
        return mapOf(
            "http_proxy" to proxy,
            "https_proxy" to proxy,
            "all_proxy" to proxy,
            "no_proxy" to "127.0.0.1,localhost,.localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16",
            "CLASH_MIXED_PORT" to "$MIXED_PORT",
            "CLASH_CONTROLLER_ADDR" to controllerAddress(),
            "CLASH_CONTROLLER_TOKEN" to secret,
        )
    }
}