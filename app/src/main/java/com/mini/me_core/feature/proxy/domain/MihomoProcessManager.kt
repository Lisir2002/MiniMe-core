package com.mini.me_core.feature.proxy.domain

import android.os.Build
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.feature.proxy.data.PROXY_NS
import com.mini.me_core.feature.proxy.data.ProxySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * P2-16 拆分产物：mihomo / clash 进程启停、生命周期、端口与二进制资源。
 *
 * 逻辑原样搬迁自 [ClashProxyManager]：
 *  - 二进制按 ABI 挑 android 构建资产，下载即算 SHA256（官方校验和），走 [directClient]（NO_PROXY）自举；
 *  - 子进程以 `-d <dir> -f config.yaml` 拉起，绑定 127.0.0.1；waitFor 监视异常退出并指数退避自恢复
 *    （1s→2s→4s，最多 3 次，耗尽则标记不可用）；
 *  - 端口冲突自动 +1 避让并持久化；
 *  - P3-18 预热池：独立端口/配置目录/进程后台预热，命中即零冷启动提升为主进程。
 */
@Suppress("LongParameterList", "TooManyFunctions") // 行为保持搬迁：LongParameterList 为协作者装配签名；TooManyFunctions 因进程生命周期/二进制下载/端口避让/崩溃恢复/预热池内聚在同一类，仅机械提取不拆分职责
internal class MihomoProcessManager(
    private val okHttp: OkHttpClient,
    private val repository: ProxySettingsRepository,
    private val routeHolder: ProxyRouteHolder,
    private val kv: KVStore,
    private val ports: ClashProxyPorts,
    private val stateFlow: MutableStateFlow<ProxyRuntimeState>,
    private val configDir: () -> File,
    private val synthesizer: ClashConfigSynthesizer,
    private val controlClient: ClashControlClient,
    private val subscriptionService: ClashSubscriptionService,
    private val ensureSecretLoaded: suspend () -> Unit,
    private val isEnabledCache: () -> Boolean,
    private val setEnabledCache: (Boolean) -> Unit,
) {
    private val tag = "ClashProxyManager"

    /** 串行化内核的启动/停止，避免 on() 与启动时自动恢复并发双拉。 */
    private val kernelMutex = Mutex()

    /** 当前 mihomo 内核子进程（App 子进程，绑定 127.0.0.1；App 进程存活则内核存活）。 */
    @Volatile
    var mihomoProcess: Process? = null
        private set

    /** 连续崩溃自动重启次数（成功拉起后清零；超过 MAX_CRASH_RESTARTS 放弃并标记不可用）。 */
    @Volatile
    private var crashRestartAttempts: Int = 0

    /** 是否正在执行崩溃自动重启循环，防止退出监视协程重复调度。 */
    @Volatile
    private var crashRestartInProgress: Boolean = false

    /**
     * 下载 mihomo 二进制用的**直连** OkHttp：强制 Proxy.NO_PROXY 覆盖共享 client 的 ProxySelector。
     * 内核二进制属于基础设施，必须绕过代理自举（代理未起/代理本身被墙都不能成为下载失败原因）。
     */
    private val directClient: OkHttpClient by lazy {
        okHttp.newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
    }

    // ── P3-18：多实例预热池状态 ──────────────────────────────────────────────
    @Volatile
    private var warmProfileId: String? = null

    @Volatile
    private var warmProcess: Process? = null

    @Volatile
    private var warmMixedPort: Int = 0

    @Volatile
    private var warmControllerPort: Int = 0

    /** 上一次活跃 profile id（on() 成功后由门面记录，供下次预热）。 */
    @Volatile
    var lastActiveProfileId: String? = null

    private val _warmupState = MutableStateFlow(WarmupState())
    val warmupState: kotlinx.coroutines.flow.StateFlow<WarmupState> = _warmupState.asStateFlow()

    /** P1-9：mihomo 内核进程是否存活（诊断用，同步非挂起）。 */
    fun isKernelAlive(): Boolean = mihomoProcess?.isAlive == true

    /** P0-3：用户手动拉起成功视为新会话，清零崩溃退避计数。 */
    fun resetCrashAttempts() {
        crashRestartAttempts = 0
    }

    // ============================ mihomo 二进制与目录 ============================

    private fun mihomoDir(): File = File(configDir(), "mihomo").apply { mkdirs() }

    fun mihomoBinary(): File = File(mihomoDir(), "mihomo")

    /** 按宿主 ABI 挑选 android 构建资产（+ 官方 SHA256）；不支持的 ABI 返回 null。 */
    private fun pickMihomoAsset(): Pair<String, String>? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.startsWith("arm64") -> ClashProxyManager.MIHOMO_ARM64_ASSET to ClashProxyManager.MIHOMO_ARM64_GZ_SHA256
            abi.startsWith("x86_64") -> ClashProxyManager.MIHOMO_AMD64_ASSET to ClashProxyManager.MIHOMO_AMD64_GZ_SHA256
            else -> null
        }
    }

    /**
     * 下载并校验 mihomo 内核二进制（首次使用触发，已存在且体积正常则跳过）。
     * 走 [directClient]（Proxy.NO_PROXY）**自举**。
     * @return null=就绪；非 null=失败原因（供调用方回显）。
     */
    /** 下载资产到 gz 临时文件并流式算 SHA-256，不一致/HTTP 失败即抛（由调用方清理 gz）。 */
    /** 把 HTTP 响应体流式写入 gz，同时逐块更新 SHA-256 摘要（64KB 缓冲，逐字搬迁原拷贝循环）。 */
    private fun streamBodyToGz(body: okhttp3.ResponseBody?, gz: File, md: MessageDigest) {
        body?.byteStream()?.use { input ->
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
    }

    /** 比对最终摘要与期望 SHA-256，不一致即抛 IOException（由调用方清理 gz）。 */
    private fun verifyShaOrThrow(md: MessageDigest, expectedSha: String) {
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(expectedSha, ignoreCase = true)) {
            throw IOException("SHA256 校验失败（期望 $expectedSha，实际 $hex）")
        }
    }

    private fun downloadAndVerifyGz(gz: File, asset: String, expectedSha: String) {
        val req = Request.Builder().url(ClashProxyManager.MIHOMO_BASE_URL + asset).get().build()
        directClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful || resp.body == null) {
                throw IOException("HTTP ${resp.code}")
            }
            val md = MessageDigest.getInstance("SHA-256")
            streamBodyToGz(resp.body, gz, md)
            verifyShaOrThrow(md, expectedSha)
        }
    }

    /** 解压 gz 到可执行二进制、删除 gz、补 setExecutable（失败由调用方清理 bin）。 */
    private fun decompressGzToBin(gz: File, bin: File) {
        GZIPInputStream(gz.inputStream().buffered()).use { gzip ->
            bin.outputStream().buffered().use { out -> gzip.copyTo(out, 64 * 1024) }
        }
        gz.delete()
        if (!bin.setExecutable(true, false)) {
            FileLogger.w(tag, "mihomo setExecutable 返回 false（可能仍可执行）")
        }
        FileLogger.i(tag, "mihomo 内核就绪：${bin.absolutePath}")
    }

    private suspend fun downloadMihomoIfNeeded(): String? = withContext(Dispatchers.IO) {
        val (asset, expectedSha) = pickMihomoAsset()
            ?: return@withContext "不支持的 CPU 架构：${Build.SUPPORTED_ABIS.firstOrNull()}"
        val bin = mihomoBinary()
        if (bin.isFile && bin.length() > 1_000_000) return@withContext null
        val dir = mihomoDir()
        val gz = File(dir, asset)
        runCatching {
            downloadAndVerifyGz(gz, asset, expectedSha)
        }.onFailure {
            gz.delete()
            FileLogger.w(tag, "下载 mihomo 失败: ${it.message}")
            return@withContext "下载 mihomo 失败：${it.message}"
        }
        // 校验通过 → 解压 gz
        runCatching {
            decompressGzToBin(gz, bin)
        }.onFailure {
            bin.delete()
            FileLogger.w(tag, "解压 mihomo 失败: ${it.message}")
            return@withContext "解压 mihomo 失败：${it.message}"
        }
        null
    }

    // ============================ 端口避让 ============================

    /**
     * P2-15：在启动 mihomo 前选定空闲端口。
     * 优先用上次持久化的端口；若仍被占用则从默认 7890/9090 起 +1 试绑（最多 10 次）。
     */
    fun ensurePortsSelected() {
        // 1. 先读上次持久化
        val savedMixed = kv.getInt(PROXY_NS, ClashProxyManager.MIXED_PORT_KEY)?.toInt() ?: ClashProxyManager.MIXED_PORT
        val savedCtrl = kv.getInt(PROXY_NS, ClashProxyManager.CONTROLLER_PORT_KEY)?.toInt() ?: ClashProxyManager.CONTROLLER_PORT

        val mixed = pickFreePort(savedMixed, ClashProxyManager.MIXED_PORT)
        val ctrl = pickFreePort(savedCtrl, ClashProxyManager.CONTROLLER_PORT)
        ports.mixed = mixed
        ports.controller = ctrl
        ports.mixedWasAdjusted = (mixed != ClashProxyManager.MIXED_PORT)

        kv.putInt(PROXY_NS, ClashProxyManager.MIXED_PORT_KEY, mixed.toLong())
        kv.putInt(PROXY_NS, ClashProxyManager.CONTROLLER_PORT_KEY, ctrl.toLong())
        FileLogger.i(tag, "端口选定: mixed=$mixed ctrl=$ctrl (adjusted=${ports.mixedWasAdjusted})")
    }

    /** 从 preferred 起尝试绑定，失败则 +1，最多 PORT_RETRY_MAX 次；都失败回退默认。 */
    @Suppress("LoopWithTooManyJumpStatements") // 行为保持搬迁：端口探测必须在绑到空闲端口时立即 return，循环语义等价
    private fun pickFreePort(preferred: Int, fallback: Int): Int {
        var port = preferred
        repeat(ClashProxyManager.PORT_RETRY_MAX) {
            if (isPortFree(port)) return port
            port++
        }
        // 全部被占，回到 fallback 附近再试一轮（极端情况）
        port = fallback
        repeat(ClashProxyManager.PORT_RETRY_MAX) {
            if (isPortFree(port)) return port
            port++
        }
        return fallback
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        java.net.ServerSocket().use { s ->
            s.bind(java.net.InetSocketAddress("127.0.0.1", port))
            true
        }
    }.getOrDefault(false)

    // ============================ 进程启停 ============================

    /** 拉启 mihomo 子进程（App 子进程，绑定 127.0.0.1；App 进程存活则内核存活）。 */
    private fun startKernelProcess(): Boolean {
        val bin = mihomoBinary()
        if (!bin.isFile) return false
        return runCatching {
            val dir = configDir()
            val cfgFile = File(dir, ClashProxyManager.CONFIG_FILE)
            // 配置必须先就绪再启动：mihomo 找不到 config.yaml 会瞬时退出（code=1）。
            if (!cfgFile.isFile) {
                FileLogger.w(tag, "config.yaml 不存在（${cfgFile.absolutePath}），跳过启动 mihomo")
                return@runCatching false
            }
            // 内容校验：落盘文件必须能解析为 YAML 映射，否则 mihomo 必然 FATAL 秒退（code=1）。
            if (!synthesizer.validateConfigFile(cfgFile)) {
                FileLogger.w(tag, "config.yaml 内容非法（不可解析为 YAML 映射），跳过启动 mihomo")
                return@runCatching false
            }
            val logFile = File(dir, "mihomo.log")
            val pb = ProcessBuilder(bin.absolutePath, "-d", dir.absolutePath, "-f", cfgFile.absolutePath)
            pb.redirectErrorStream(true)
            pb.redirectOutput(logFile)
            val p = pb.start()
            mihomoProcess = p
            FileLogger.i(tag, "mihomo 内核已启动（log=${logFile.absolutePath}）")
            // 监视退出（waitFor 阻塞一个 IO 线程，进程存活期间让渡；进程意外退出时清空句柄并告警，
            // 避免残留「伪运行」状态。不能用 Process.onExit()——Android 未提供该 Java 9 API。）
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                p.waitFor()
                if (mihomoProcess === p) {
                    mihomoProcess = null
                    FileLogger.w(tag, "mihomo 内核进程退出 code=${p.exitValue()}（详见 mihomo.log）")
                    logKernelLogTail()
                    // P0-3：用户仍要求代理开启时，指数退避自动重启（1s→2s→4s，最多 3 次）。
                    // 主动 off()/on() 重启路径会先把 mihomoProcess 置 null，故此处 mihomoProcess===p
                    // 即「非主动关闭的异常退出」，才进入自恢复。
                    if (isEnabledCache() && !crashRestartInProgress) {
                        scheduleCrashRestart()
                    }
                }
            }
            true
        }.onFailure {
            FileLogger.w(tag, "启动 mihomo 内核失败: ${it.message}")
            false
        }.getOrDefault(false)
    }

    /** 把 mihomo.log 尾部打进 FileLogger，便于在日志里直接看到内核报错。 */
    private fun logKernelLogTail(maxChars: Int = 4000) {
        runCatching {
            val f = File(configDir(), "mihomo.log")
            if (!f.isFile) {
                FileLogger.w(tag, "mihomo.log 不存在，无法读取内核退出原因")
                return
            }
            val bytes = f.readBytes()
            val start = (bytes.size - maxChars).coerceAtLeast(0)
            // takeLast 会退回 List<Byte>，无 toString(Charset)；用 copyOfRange 保持 ByteArray。
            val tail = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
            if (tail.isBlank()) {
                FileLogger.w(tag, "mihomo.log 为空（log-level 静默或内核未写任何日志），无法定位退出原因")
            } else {
                FileLogger.w(tag, "--- mihomo.log tail ---\n$tail")
            }
        }
    }

    /** 轮询 external-controller 直至可达（[retries] 次 × 500ms）；内核进程已死则立即失败。 */
    suspend fun waitControllerReady(retries: Int): Boolean {
        repeat(retries) {
            if (mihomoProcess?.isAlive == false) return false
            if (controlClient.controllerRequest("GET", "/configs") != null) return true
            delay(500)
        }
        return false
    }

    /** 在 [kernelMutex] 内确保内核运行；[restart]=true 时先停旧进程再起（加载新配置）。 */
    suspend fun ensureKernelRunning(restart: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            kernelMutex.withLock {
                if (restart) stopKernelLocked()
                val alive = mihomoProcess?.isAlive == true
                if (!alive) {
                    // 首次使用需先自举下载并校验内核二进制（已存在则直接跳过）
                    val err = downloadMihomoIfNeeded()
                    if (err != null) {
                        FileLogger.w(tag, err)
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

    suspend fun stopKernel() = withContext(Dispatchers.IO) {
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
        FileLogger.i(tag, "mihomo 内核已停止")
    }

    /**
     * P0-3：mihomo 内核异常退出后的指数退避自动重启。
     * 退避序列 1s → 2s → 4s，最多 MAX_CRASH_RESTARTS 次；成功则清零计数并恢复 `stateFlow`；
     * 连续失败耗尽则把代理标记为不可用（enabled=false + routeHolder 切直连兜底）。
     * 全程持 [kernelMutex]，与 on()/off() 互斥。
     */
    /** 单次崩溃重启尝试结果：成功 / 下一轮退避 / 退避窗口内用户已关闭（放弃）。 */
    private enum class CrashAttempt { SUCCESS, RETRY, ABORTED }

    /** P0-3：第 n 次退避毫秒数（1s→2s→4s），与原内联 when 逐字等价。 */
    private fun crashBackoffMs(attempt: Int): Long = when (attempt) {
        1 -> 1000L
        2 -> 2000L
        else -> 4000L
    }

    /** 耗尽重试后的统一收尾：标记代理不可用（enabled=false + routeHolder 切直连兜底）。 */
    private suspend fun markKernelUnavailable() {
        FileLogger.w(tag, "mihomo 连续 ${ClashProxyManager.MAX_CRASH_RESTARTS} 次崩溃自动重启失败，标记为不可用")
        runCatching { repository.setProxyEnabled(false) }
        setEnabledCache(false)
        stateFlow.update {
            it.copy(enabled = false, recovering = false, recoveryAttempt = 0, controllerReachable = false)
        }
        routeHolder.update(false, "127.0.0.1:${ports.mixed}")
    }

    /** 执行一轮崩溃重启尝试：退避 → 必要时重建配置 → 起进程 → 等控制面就绪。 */
    private suspend fun runCrashRestartAttempt(): CrashAttempt {
        crashRestartAttempts++
        val backoffMs = crashBackoffMs(crashRestartAttempts)
        stateFlow.update { it.copy(recovering = true, recoveryAttempt = crashRestartAttempts) }
        FileLogger.w(tag, "mihomo 崩溃自动重启：第 $crashRestartAttempts/${ClashProxyManager.MAX_CRASH_RESTARTS} 次，${backoffMs}ms 后拉起")
        delay(backoffMs)

        // 退避窗口内用户关闭了代理 → 放弃
        if (!isEnabledCache()) {
            FileLogger.i(tag, "崩溃恢复：退避期间代理已被用户关闭，放弃自动重启")
            stateFlow.update { it.copy(recovering = false, recoveryAttempt = 0) }
            return CrashAttempt.ABORTED
        }

        // 配置丢失则尽力重建（订阅拉取失败则跳过，startKernelProcess 会因配置缺失而失败进入下一轮）
        val cfgFile = File(configDir(), ClashProxyManager.CONFIG_FILE)
        if (!cfgFile.isFile) {
            runCatching { subscriptionService.rebuildConfigFromActiveProfile() }
        }

        // 拉起后统一判活：起进程失败 → 直接退避；起来但秒退 → 读日志后退避。两者都 RETRY，但日志/秒退保护时序不同。
        val started = startKernelProcess()
        if (started) delay(300)
        if (!started || mihomoProcess?.isAlive != true) {
            if (started) {
                logKernelLogTail()
                FileLogger.w(tag, "mihomo 崩溃自动重启：进程秒退，进入下一轮退避")
            } else {
                FileLogger.w(tag, "mihomo 崩溃自动重启：startKernelProcess 失败，进入下一轮退避")
            }
            return CrashAttempt.RETRY
        }
        if (waitControllerReady(20)) {
            // 成功：清零计数，通知 UI 恢复正常
            crashRestartAttempts = 0
            stateFlow.update {
                it.copy(recovering = false, recoveryAttempt = 0, controllerReachable = true)
            }
            FileLogger.i(tag, "mihomo 崩溃自动重启成功")
            return CrashAttempt.SUCCESS
        }
        FileLogger.w(tag, "mihomo 崩溃自动重启：进程存活但控制面未就绪，进入下一轮退避")
        // 进程可能稍后才崩，先停掉再重试，避免端口占用
        stopKernelLocked()
        return CrashAttempt.RETRY
    }

    /**
     * P0-3：mihomo 内核异常退出后的指数退避自动重启。
     * 全程持 [kernelMutex]，与 on()/off() 互斥；退避期间用户关闭代理会被 [isEnabledCache] 检查拦下。
     */
    @Suppress("TooGenericExceptionCaught") // 行为保持搬迁：进程状态机必须隔离任意异常（含协程/IO），避免自恢复崩溃传播；cause 经 t.message 记入 FileLogger
    private suspend fun scheduleCrashRestart() {
        crashRestartInProgress = true
        try {
            kernelMutex.withLock {
                // 双重确认：加锁期间用户可能已手动关闭/重开
                if (!isEnabledCache()) return@withLock
                if (mihomoProcess?.isAlive == true) return@withLock

                while (crashRestartAttempts < ClashProxyManager.MAX_CRASH_RESTARTS) {
                    when (runCrashRestartAttempt()) {
                        CrashAttempt.SUCCESS, CrashAttempt.ABORTED -> return@withLock
                        CrashAttempt.RETRY -> Unit
                    }
                }

                markKernelUnavailable()
            }
        } catch (t: Throwable) {
            FileLogger.w(tag, "崩溃自动重启异常: ${t.message}")
        } finally {
            crashRestartInProgress = false
        }
    }

    // ── P3-18：多实例预热 ────────────────────────────────────────────────────────

    private fun warmupDir(): File = File(configDir(), "warmup").apply { mkdirs() }

    /** 解析预热 profile 的源 YAML：订阅型拉远端，手动型返回原文；不可用返回 null。 */
    private suspend fun resolveWarmupSource(profileId: String): String? {
        ensureSecretLoaded()
        val revealed = repository.revealSecret(profileId) ?: return null
        val trimmed = revealed.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            subscriptionService.fetchSubscriptionYaml(trimmed)
        } else trimmed
    }

    /** 为预热实例分配独立端口、合成配置（临时切端口再切回）、写盘并拉起独立进程。 */
    private fun launchWarmupProcess(profileId: String, source: String) {
        // 为预热实例分配独立端口（避开主实例已占用的端口）。
        val wMixed = pickFreePort(ports.mixed + 1, ClashProxyManager.MIXED_PORT + 1)
        val wCtrl = pickFreePort(ports.controller + 1, ClashProxyManager.CONTROLLER_PORT + 1)
        // 合成配置时临时切到预热端口，再切回（synthesizeConfig 读 ports 字段）。
        val savedM = ports.mixed
        val savedC = ports.controller
        ports.mixed = wMixed
        ports.controller = wCtrl
        val config = synthesizer.synthesizeConfig(source)
        ports.mixed = savedM
        ports.controller = savedC

        val dir = warmupDir()
        val cfgFile = File(dir, "config.yaml")
        cfgFile.writeText(config, Charsets.UTF_8)
        val bin = mihomoBinary()
        if (!bin.isFile) return
        val logFile = File(dir, "mihomo.log")
        val pb = ProcessBuilder(bin.absolutePath, "-d", dir.absolutePath, "-f", cfgFile.absolutePath)
        pb.redirectErrorStream(true)
        pb.redirectOutput(logFile)
        val p = pb.start()
        warmProcess = p
        warmProfileId = profileId
        warmMixedPort = wMixed
        warmControllerPort = wCtrl
        _warmupState.value = WarmupState(profileId, warming = true)
        FileLogger.i(tag, "预热实例启动: profile=$profileId port=$wMixed ctrl=$wCtrl")
    }

    /**
     * 预热某个 profile：独立配置目录 + 独立端口 + 独立进程，后台跑起来但不接 App 流量。
     * 最多保留 1 个（新预热前先停旧的）。失败静默（预热是优化，失败不影响主链路）。
     */
    suspend fun warmupProfile(profileId: String) = withContext(Dispatchers.IO) {
        if (profileId == lastActiveProfileId) return@withContext
        stopWarmup()
        runCatching {
            val source = resolveWarmupSource(profileId) ?: return@withContext
            if (source.isBlank()) return@withContext
            launchWarmupProcess(profileId, source)
        }.onFailure { FileLogger.w(tag, "预热失败: ${it.message}") }
    }

    /** 停止并清理预热实例。 */
    fun stopWarmup() {
        val p = warmProcess
        warmProcess = null
        warmProfileId = null
        _warmupState.value = WarmupState()
        if (p != null && p.isAlive) runCatching { p.destroy() }
    }

    /**
     * P3-18：切换时若目标正是预热实例，直接提升为主进程（零冷启动）。
     * @return true=已提升（调用方跳过正常启动流程）。
     */
    fun promoteWarmIfMatches(profileId: String?): Boolean {
        if (profileId == null) return false
        val p = warmProcess
        if (profileId != warmProfileId || p == null || !p.isAlive) return false
        // 提升：预热进程变主进程，端口/控制器/配置目录全部接管。
        mihomoProcess = p
        ports.mixed = warmMixedPort
        ports.controller = warmControllerPort
        // 预热配置已在 warmup/config.yaml；复制为主 config.yaml（主目录）。
        runCatching {
            val src = File(warmupDir(), "config.yaml")
            if (src.isFile) src.copyTo(File(configDir(), ClashProxyManager.CONFIG_FILE), overwrite = true)
        }
        FileLogger.i(tag, "预热实例提升为主进程: profile=$profileId port=$warmMixedPort")
        warmProcess = null
        warmProfileId = null
        _warmupState.value = WarmupState()
        return true
    }
}
