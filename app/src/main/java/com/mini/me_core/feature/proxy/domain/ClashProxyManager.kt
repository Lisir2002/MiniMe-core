package com.mini.me_core.feature.proxy.domain

import android.content.Context
import com.mini.me_core.core.security.CredentialEncryptor
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
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

/** P3-18：预热实例状态（UI 在 profile 列表展示 运行中/预热中/未启动）。 */
data class WarmupState(
    val warmProfileId: String? = null,
    val warming: Boolean = false,
) {
    /** 该 profileId 当前是「运行中」（活跃）/「预热中」/否则「未启动」。 */
    fun statusOf(id: String, activeId: String?): String = when {
        id == activeId -> "运行中"
        id == warmProfileId && warming -> "预热中"
        else -> "未启动"
    }
}

/**
 * 容器内 mihomo 代理引擎管理器（生命周期仿 [com.mini.me_core.feature.agent.domain.container.bridge.RcbBridge]）。
 *
 * P2-16 拆分后本类为**薄门面（facade）**：类名、包名、构造函数签名与 Hilt 注解保持不变，
 * 原 public/internal 方法签名全部保留并委托给同包协作者，调用方零改动即可编译。
 *
 * 职责分层（《网络代理设计 v1.0》§2.3 / §5）：
 *  - **配置合成**：[ClashConfigSynthesizer]——订阅/手动 YAML → 叠加固定覆盖块（mixed-port、
 *    external-controller 127.0.0.1:9090 + secret、allow-lan false、mode rule、DIRECT 兜底），
 *    剥掉源配置危险键，产物写 `filesDir/minime/proxy/config.yaml`。
 *  - **secret**：[ClashSecretStore]——secret 生成 / CredentialEncryptor 加密 / KVStore 存取。
 *  - **订阅**：[ClashSubscriptionService]——远程订阅下载 / 手动自动刷新 / 周期更新。
 *  - **控制面**：[ClashControlClient]——打 mihomo external-controller REST（secret 鉴权）。
 *  - **进程**：[MihomoProcessManager]——mihomo 二进制下载校验、子进程启停、端口避让、崩溃自恢复、预热池。
 *  - **env 注入**：[exportContainerEnv] 供 [com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine.buildContainerEnv]
 *    并入，让容器内进程的 http/https/all_proxy 指向 127.0.0.1:7890。
 *
 * 说明：mihomo 二进制本身放入容器并在容器内拉起属于容器初始化层（根侧），本门面只负责
 * 编排状态机、配置产物与 env/control 面；容器内新终端会因 env 注入直接吃到代理。
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
    // 本类为大型单例拆分后的门面，companion 置于类首是既有结构（已建 baseline）；
    // 仅本轮新增的 newSafeYaml 触发 ClassOrdering，重排风险高，按规约局部抑制。
    @Suppress("ClassOrdering")
    companion object {
        private const val TAG = "ClashProxyManager"
        const val MIXED_PORT = 7890
        const val CONTROLLER_PORT = 9090
        const val CONTROLLER_HOST = "127.0.0.1"
        private const val CONFIG_DIR = "proxy"
        internal const val CONFIG_FILE = "config.yaml"
        internal const val SECRET_FILE = "secret"

        /** P0-5：control secret 在 KVStore 中的键（密文形式，与订阅 secret 同一套 CredentialEncryptor）。 */
        internal const val CONTROL_SECRET_KEY = "control_secret"

        /** P2-15：上次成功使用的端口持久化键。 */
        internal const val MIXED_PORT_KEY = "mixed_port"
        internal const val CONTROLLER_PORT_KEY = "controller_port"
        internal const val PORT_RETRY_MAX = 10

        /**
         * 用显式 [SafeConstructor] 构造 Yaml：远程订阅/任何外部 YAML 均不可信，
         * 必须禁用任意 Java 类型标签（!!type）反序列化，只接受标准标量/集合结构。
         * internal 以便单测断言恶意标签文档不会被实例化。
         */
        internal fun newSafeYaml(): Yaml = Yaml(SafeConstructor(LoaderOptions()))

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
        internal const val MAX_CRASH_RESTARTS = 3
    }

    private val _state = MutableStateFlow(ProxyRuntimeState())
    val state: StateFlow<ProxyRuntimeState> = _state.asStateFlow()

    /** 全局开关缓存：buildContainerEnv 是同步方法，不能在其中挂起读 DataStore，故用 flow 预热。 */
    @Volatile
    private var enabledCache: Boolean = false

    // ── P2-16：协作者装配（普通 internal 类，门面构造；依赖全部来自门面已注入对象） ──

    /** 配置产物目录 filesDir/minime/proxy（旧实现 configDir() 原样搬迁为 lambda）。 */
    private val configDir: () -> File = {
        File(File(context.filesDir, "minime"), CONFIG_DIR)
    }

    private val ports = ClashProxyPorts()

    private val secretStore = ClashSecretStore(kv, credentialEncryptor, configDir)

    private val synthesizer = ClashConfigSynthesizer(
        ports = ports,
        secretProvider = { secretStore.secret },
        configDir = configDir,
    )

    private val controlClient = ClashControlClient(
        okHttp = okHttp,
        ports = ports,
        secretProvider = { secretStore.secret },
        stateFlow = _state,
        ensureSecretLoaded = { secretStore.ensureLoaded() },
    )

    // 显式类型标注打断 Kotlin 对「subscriptionService 闭包引用后声明的 processManager、
    // processManager 构造又依赖 subscriptionService」的类型推断递归；闭包延迟到运行时调用，
    // 此时 processManager 已完成装配，运行时行为与原内联实现一致。
    private val subscriptionService: ClashSubscriptionService = ClashSubscriptionService(
        okHttp = okHttp,
        repository = repository,
        synthesizer = synthesizer,
        controlClient = controlClient,
        ensureSecretLoaded = { secretStore.ensureLoaded() },
        isKernelAlive = { processManager.isKernelAlive() },
        isEnabledCache = { enabledCache },
    )

    private val processManager: MihomoProcessManager = MihomoProcessManager(
        okHttp = okHttp,
        repository = repository,
        routeHolder = routeHolder,
        kv = kv,
        ports = ports,
        stateFlow = _state,
        configDir = configDir,
        synthesizer = synthesizer,
        controlClient = controlClient,
        subscriptionService = subscriptionService,
        ensureSecretLoaded = { secretStore.ensureLoaded() },
        isEnabledCache = { enabledCache },
        setEnabledCache = { enabledCache = it },
    )

    /** P3-18：预热实例状态（UI 显示 运行中/预热中/未启动）。 */
    val warmupState: StateFlow<WarmupState> = processManager.warmupState

    init {
        // P0-5：secret 改为加密存储（CredentialEncryptor + KVStore），需在协程中异步解密。
        // 必须先于任何内核拉起/控制面请求完成——on()/controllerRequest 等 suspend 入口会 await secretReady。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { secretStore.loadOrCreate() }
                .onSuccess { s -> secretStore.setLoadedSecret(s) }
                .onFailure { FileLogger.w(TAG, "加载加密 control secret 失败: ${it.message}") }
            secretStore.ready.complete(Unit)

            // 首帧：上次若为启用态，先拉起内核、确认控制面就绪，再同步开关。
            // 顺序不可反——先置 enabled 会让共享 OkHttp 把流量打进还没人监听的 7890（对应
            // ModelMetadataService 那类 `Failed to connect to /127.0.0.1:7890`），必须内核先起。
            val initiallyEnabled = repository.isProxyEnabled()
            if (initiallyEnabled) {
                // 内核以 -f config.yaml 启动，配置缺失会瞬时退出（code=1）——先兜底重建再拉起。
                val cfgFile = File(configDir(), CONFIG_FILE)
                if (!cfgFile.isFile) {
                    val rebuilt = subscriptionService.rebuildConfigFromActiveProfile()
                    FileLogger.i(
                        TAG,
                        if (rebuilt) "启动时重写丢失的 config.yaml"
                        else "config.yaml 缺失且无法从活跃 profile 重建，跳过自动恢复"
                    )
                }
                val ok = processManager.ensureKernelRunning(restart = false)
                enabledCache = ok
                _state.update { it.copy(enabled = ok) }
                routeHolder.update(ok, "127.0.0.1:${ports.mixed}")
                FileLogger.i(
                    TAG,
                    if (ok) "启动时自动恢复 mihomo 内核成功"
                    else "启动时自动恢复 mihomo 内核失败（保持代理关闭，避免把网络流量打进未监听的端口）"
                )
            } else {
                routeHolder.update(false, "127.0.0.1:${ports.mixed}")
            }
            // 之后的开关变化继续由 flow 驱动
            repository.proxyEnabledFlow.drop(1).collect { e ->
                enabledCache = e
                _state.update { it.copy(enabled = e) }
                // 同步给 App 网络层的路由开关写位，让共享 OkHttp 的 ProxySelector 感知（§4.2）
                routeHolder.update(e, "127.0.0.1:${ports.mixed}")
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            repository.activeProfileIdFlow.collect { id ->
                _state.update { it.copy(activeProfileId = id) }
            }
        }
        // 网络层优化 C5：AI 接口直连/代理分流开关 → 同步给 App 网络层路由写位（默认关 = 全走代理）
        CoroutineScope(Dispatchers.IO).launch {
            repository.aiHostsDirectFlow.collect { direct ->
                routeHolder.setAiHostsDirect(direct)
            }
        }
        // P1-7：订阅自动更新周期检查（每 15 分钟扫描一次，按各 profile 的 updateIntervalHours 决定是否到期）。
        // 沿用 appScope 协程周期循环模式（与 scheduleScheduler 一致）；mihomo 仅在本进程存活时运行，
        // 进程死亡时热重载无意义，故不引入 WorkManager。
        CoroutineScope(Dispatchers.IO).launch {
            // 启动后先等 secret 就绪，再开始周期扫描
            secretStore.ready.await()
            while (true) {
                delay(15 * 60 * 1000L)
                runCatching { subscriptionService.autoUpdateDueSubscriptions() }
                    .onFailure { FileLogger.w(TAG, "订阅自动更新周期扫描异常: ${it.message}") }
            }
        }
    }

    /** 供容器/上层读取的启用态（同步、非挂起）。 */
    fun isEnabled(): Boolean = enabledCache

    /** P1-9：mihomo 内核进程是否存活（诊断用，同步非挂起）。 */
    fun isKernelAlive(): Boolean = processManager.isKernelAlive()

    /** P1-9：经内核真实出口测 generate_204 延迟（ms）；控制器不可达/超时返回 null。 */
    suspend fun testOutboundLatency(): Long? = controlClient.testOutboundLatency()

    /**
     * P1-8：读 mihomo 累计流量计数 [uploadTotal, downloadTotal]（/connections 顶层字段，自内核启动起累计）。
     * 控制器不可达返回 null。差值由调用方（采样器）计算。
     */
    suspend fun readTrafficCounters(): Pair<Long, Long>? = controlClient.readTrafficCounters()

    /** 控制器地址 "127.0.0.1:port"。 */
    fun controllerAddress(): String = "$CONTROLLER_HOST:${ports.controller}"

    /** P2-15：当前实际 mixed 端口（供 UI 展示）。 */
    fun mixedPort(): Int = ports.mixed

    /** P3-19：公开的控制面请求包装（供连接审计日志读取 /connections）。 */
    suspend fun controllerRequestPublic(method: String, path: String): String? =
        controlClient.controllerRequest(method, path)

    /** P2-15：是否因冲突避让过默认端口。 */
    fun portAdjusted(): Boolean = ports.mixedWasAdjusted

    fun controllerSecret(): String = secretStore.secret

    /**
     * 合成 mihomo 配置：固定覆盖块头 + 清洗后的源配置体。委托 [ClashConfigSynthesizer]。
     */
    fun synthesizeConfig(sourceYaml: String): String = synthesizer.synthesizeConfig(sourceYaml)

    /** 订阅 URL 全文抓取（拉取远端订阅 YAML）。失败返回 null。委托 [ClashSubscriptionService]。 */
    suspend fun fetchSubscriptionYaml(url: String): String? =
        subscriptionService.fetchSubscriptionYaml(url)

    /** P1-7：手动/自动刷新某个订阅型 profile。委托 [ClashSubscriptionService]。 */
    suspend fun refreshSubscriptionNow(id: String): Boolean =
        subscriptionService.refreshSubscriptionNow(id)

    /** 用 SnakeYAML 解析 Clash 配置，统计内联节点 / 分组 / proxy-provider。委托 [ClashConfigSynthesizer]。 */
    fun parseClashConfig(yaml: String): ClashConfigSummary = synthesizer.parseClashConfig(yaml)

    /**
     * 单节点测速（对齐 Clash：只走内核真实出口，不做 App 直连近似）。
     * 委托 [ClashControlClient.testNodeLatency]。
     */
    suspend fun testNodeLatency(node: ProxyNodeInfo): Long? = controlClient.testNodeLatency(node)

    /** 把合成配置落盘到 filesDir/minime/proxy/config.yaml。委托 [ClashConfigSynthesizer]。 */
    suspend fun writeConfigFile(configYaml: String) = synthesizer.writeConfigFile(configYaml)

    /**
     * 打 mimomo external-controller REST；成功返回 body，失败返回 null（视为未运行/网络错）。
     * 委托 [ClashControlClient.controllerRequest]。
     */
    suspend fun controllerRequest(
        method: String,
        path: String,
        body: String? = null,
    ): String? = controlClient.controllerRequest(method, path, body)

    /**
     * 拉取 mihomo /proxies 全量快照（分组树：类型/当前选中项/成员/健康检查延迟）。
     * 委托 [ClashControlClient.fetchProxiesSnapshot]。
     */
    suspend fun fetchProxiesSnapshot(): ClashProxiesSnapshot? = controlClient.fetchProxiesSnapshot()

    /** 切换某分组内选中节点（对齐 Clash 点击切换）。委托 [ClashControlClient.selectProxyNode]。 */
    suspend fun selectProxyNode(group: String, node: String): Boolean =
        controlClient.selectProxyNode(group, node)

    /** P1-11：热重载配置（不杀进程、不断连）。委托 [ClashControlClient.reloadConfig]。 */
    suspend fun reloadConfig(configYaml: String): Boolean = controlClient.reloadConfig(configYaml)

    /** /traffic WS 实时推流（零轮询）。委托 [ClashControlClient.trafficFlow]。 */
    fun trafficFlow(): kotlinx.coroutines.flow.Flow<ProxyTraffic> = controlClient.trafficFlow()

    /** 拉起代理：由已播种 profile（[profileId]）或临时 inline（[inlineYaml]）合成配置、落盘、拉起内核、置开关。 */
    suspend fun on(profileId: String?, inlineYaml: String?): String {
        secretStore.ensureLoaded()
        if (profileId == null && inlineYaml == null) return "需要 profile_id 或 inline yaml"
        val source = if (inlineYaml != null) {
            inlineYaml
        } else {
            val id = profileId ?: return "需要 profile_id 或 inline yaml"
            val revealed = repository.revealSecret(id)
                ?: return "未找到已播种 profile：$id"
            // 订阅型 profile 存的是订阅 URL：需先拉取远端 YAML；手动型存的就是 YAML 原文。
            val trimmed = revealed.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                subscriptionService.fetchSubscriptionYaml(trimmed) ?: return "订阅拉取失败：$profileId"
            } else {
                revealed
            }
        }
        if (source.isBlank()) return "配置为空"
        val config = synthesizer.synthesizeConfig(source)
        synthesizer.writeConfigFile(config)
        // 内核必须实际跑起来并让控制面就绪，才能把开关置为 enabled——
        // 否则 App 流量会被 routeHolder 打进无人监听的 7890（`Failed to connect to /127.0.0.1:7890`）。
        // P1-11：内核已在运行时优先热重载（不杀进程、不断连）；热重载失败才回退到 restart。
        // P2-15：启动前检测/避让端口冲突（内核未在跑时才需要；已在跑则端口已定）。
        // P3-18：内核未在跑时，若目标 profile 已预热，直接提升预热进程为主进程（零冷启动）。
        val wasAlive = processManager.isKernelAlive()
        val kernelOk = when {
            wasAlive -> {
                val reloaded = controlClient.reloadConfig(config)
                if (reloaded) true else {
                    FileLogger.w(TAG, "on(): 热重载失败，回退到重启内核")
                    processManager.ensureKernelRunning(restart = true)
                }
            }
            processManager.promoteWarmIfMatches(profileId) -> {
                FileLogger.i(TAG, "on(): 命中预热实例，零冷启动切换")
                processManager.waitControllerReady(10)
            }
            else -> {
                processManager.ensurePortsSelected()
                processManager.ensureKernelRunning(restart = false)
            }
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
        processManager.resetCrashAttempts()
        routeHolder.update(true, "127.0.0.1:${ports.mixed}")
        _state.update { it.copy(enabled = true, activeProfileId = profileId, recovering = false, recoveryAttempt = 0) }
        // P2-14：进入前台保活通知。
        runCatching { ProxyForegroundService.start(context, ports.mixed) }
        // P3-18：记录本次活跃 profile；若有上一个不同的 profile，后台预热它（延迟 8s，不阻塞 on() 返回）。
        val previous = processManager.lastActiveProfileId
        processManager.lastActiveProfileId = profileId
        if (previous != null && previous != profileId) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(8_000)
                if (enabledCache && processManager.lastActiveProfileId == profileId) {
                    processManager.warmupProfile(previous)
                }
            }
        }
        FileLogger.i(TAG, "network_proxy ON (profile=$profileId inline=${inlineYaml != null})")
        return "ok"
    }

    /** 关闭代理：先停内核，再翻转开关（env 也随之不再注入）。 */
    suspend fun off() {
        processManager.stopKernel()
        processManager.stopWarmup()
        repository.setProxyEnabled(false)
        // 同步写位（同 on()），不等 flow 异步 emit。
        enabledCache = false
        // P0-3：用户主动关闭，重置崩溃退避计数与恢复中状态。
        processManager.resetCrashAttempts()
        routeHolder.update(false, "127.0.0.1:${ports.mixed}")
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
        val proxy = "http://$CONTROLLER_HOST:${ports.mixed}"
        return mapOf(
            "http_proxy" to proxy,
            "https_proxy" to proxy,
            "all_proxy" to proxy,
            "no_proxy" to "127.0.0.1,localhost,.localhost,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16",
            "CLASH_MIXED_PORT" to "$MIXED_PORT",
            "CLASH_CONTROLLER_ADDR" to controllerAddress(),
            "CLASH_CONTROLLER_TOKEN" to secretStore.secret,
        )
    }
}
