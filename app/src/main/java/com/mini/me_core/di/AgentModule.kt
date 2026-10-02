package com.mini.me_core.di

import com.mini.me_core.feature.agent.data.CodeChangeTracker
import com.mini.me_core.feature.agent.domain.execution.tool.todo.TodoTool
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import com.mini.me_core.feature.agent.data.remote.anthropic.AnthropicApi
import com.mini.me_core.feature.agent.data.remote.gemini.GeminiApi
import com.mini.me_core.feature.agent.data.remote.openai.OpenAIApi
import com.mini.me_core.feature.agent.domain.container.CommandEngine
import com.mini.me_core.feature.agent.domain.container.DelegatingCommandEngine
import com.mini.me_core.feature.agent.domain.container.LinuxContainerEngine
import com.mini.me_core.feature.agent.domain.container.RemoteSshConnection
import com.mini.me_core.feature.agent.domain.container.RemoteSshEngine
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder
import com.mini.me_core.feature.agent.domain.execution.tool.file.ReadFileTool
import com.mini.me_core.feature.agent.domain.execution.tool.file.SendFileTool
import com.mini.me_core.feature.agent.domain.execution.tool.file.ViewImageTool
import com.mini.me_core.feature.agent.domain.execution.tool.file.WriteFileTool
import com.mini.me_core.feature.agent.domain.execution.tool.editor.EditFileTool
import com.mini.me_core.feature.agent.domain.execution.tool.container.ExecuteCommandTool
import com.mini.me_core.feature.agent.domain.execution.tool.container.CheckEnvironmentTool
import com.mini.me_core.feature.agent.domain.execution.tool.container.EnsureAndroidEnvTool
import com.mini.me_core.feature.agent.domain.execution.tool.container.SwitchContainerArchTool
import com.mini.me_core.feature.agent.domain.execution.tool.container.TerminalSessionTool
import com.mini.me_core.feature.agent.domain.execution.tool.explorer.ListFilesTool
import com.mini.me_core.feature.agent.domain.execution.tool.explorer.SearchCodeTool
import com.mini.me_core.feature.agent.domain.execution.tool.skill.LoadSkillTool
import com.mini.me_core.feature.agent.domain.execution.tool.question.AskUserQuestionTool
import com.mini.me_core.feature.agent.domain.execution.tool.browser.BrowserAgentTool
import java.net.Proxy
import com.mini.me_core.feature.agent.domain.core.prompt.SystemPromptProvider
import com.mini.me_core.feature.agent.domain.execution.workflow.AgentWorkflow
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.permission.ToolPermissionPolicyEngine
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.execution.tool.intent.IntentAnalyzeTool
import com.mini.me_core.feature.agent.domain.execution.tool.ToolOutputStore
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.terminal.domain.DelegatingTerminalSessionProvider
import com.mini.me_core.feature.terminal.domain.RemoteTerminalSessionManager
import com.mini.me_core.feature.terminal.domain.TerminalSessionManager
import com.mini.me_core.feature.terminal.domain.TerminalSessionProvider
import com.mini.me_core.feature.workspace.domain.FileAccessProvider
import com.mini.me_core.feature.workspace.domain.DelegatingFileAccess
import com.mini.me_core.feature.workspace.domain.LocalFileAccess
import com.mini.me_core.feature.workspace.domain.RemoteSftpFileAccess
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    // ══════════════════════════════════════════════════════════
    // 数据层已整体迁移至 SQLDelight V2（六库拓扑，见 DataLayerModule），
    // 旧 Room 数据层（单巨库 AgentDatabase v49 / 按域拆分独立库、DAO、
    // DbSplitMigrator 一次性拆库）已全部移除。本模块只保留网络/工具/工作流绑定。
    // ══════════════════════════════════════════════════════════

    // ══ RC69 T2I：ImageGenerator（interface）→ OpenAiCompatibleImageGenerator（实现）绑定
    //   AgentModule 是 @Module object，不能用 @Binds，所以用 @Provides 包一层构造器注入的 impl。
    @Provides
    @Singleton
    fun provideImageGenerator(impl: com.mini.me_core.feature.t2i.data.remote.OpenAiCompatibleImageGenerator): com.mini.me_core.feature.t2i.data.remote.ImageGenerator {
        return impl
    }

    // T2I 专用探测服务：构造函数是 @Inject 所以 Hilt 本身会实例化，这里声明一个 @Provides
    //   只是保证 Module 对象里能显式声明为单例（与 ModelApiService 同生命周期），供后续 ViewModel/Repo 直接取。
    @Provides
    @Singleton
    fun provideT2IModelProbeService(impl: com.mini.me_core.feature.t2i.data.remote.T2IModelProbeService):
            com.mini.me_core.feature.t2i.data.remote.T2IModelProbeService = impl

    // ══════════════════════════════════════════════════════════
    // 阶段1：双 OkHttpClient 实例隔离
    //   - directClient（@Named("direct")）：直连，不走代理。用于国内模型、压缩/识图等子模型。
    //   - proxyClient（@Named("proxy")）：走 proxySelector。用于需要代理的国外模型（OpenAI/Anthropic/Gemini）。
    //   代理不通时由 ProxyHealthMonitor + ProxyRouteHolder 自动降级为直连（见阶段4）。
    // ══════════════════════════════════════════════════════════

    /** 直连 OkHttpClient：强制 NO_PROXY，所有请求直接出网，彻底隔绝系统代理污染。 */
    @Provides
    @Singleton
    @Named("direct")
    fun provideDirectOkHttpClient(
        httpWarningBridge: com.mini.me_core.core.network.HttpWarningBridge
    ): OkHttpClient {
        // 流式 SSE 下读超时是「相邻数据块之间」的等待上限；120s 给慢启动/长思考留足空间，
        // 真正卡死由上层阶梯重试（RetryPolicy）兜底。
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            // 代理隔离核心：强制直连，不依赖默认 ProxySelector。
            // 即使系统 WiFi 设置了全局代理、或应用内某处调用了 ProxySelector.setDefault，
            // directClient 也绝对不会走代理，确保国内模型接口零污染。
            .proxy(Proxy.NO_PROXY)
            // 安全审计 P2-7：探测非回环明文 http:// 请求并全局弹窗提示改用 HTTPS（仅上报不阻断）。
            .addInterceptor(com.mini.me_core.core.network.HttpWarningInterceptor(httpWarningBridge))
            // F6.6：仅 debug 构建加入网络监控拦截器（记录 URL/状态/耗时/大小，敏感头脱敏）；
            // release 不加入，零开销、零隐私泄漏。
            .apply {
                if (com.mini.me_core.BuildConfig.DEBUG) {
                    addInterceptor(com.mini.me_core.core.performance.NetworkMonitorInterceptor())
                }
            }
            // 网络层优化 C2：连接池调优（默认 5 连接 / 5min 保活）。模型接口常往返复用，
            // 放宽到 8 连接 / 15min 提升长连接复用率，降低首字节（TTFT）延迟。
            .connectionPool(okhttp3.ConnectionPool(8, 15, TimeUnit.MINUTES))
            // 网络层优化 C3：短 TTL DNS 缓存，避免每次连接走系统 DNS（弱网可省几十~几百 ms）。
            // 网络层优化 C5+：系统解析失败时回退公共 DNS（223.5.5.5 等）兜底，规避
            // "Unable to resolve host"（被网络分流/私人 DNS 劫持时系统查不到、公共 DNS 却可查）。
            .dns(com.mini.me_core.core.network.PublicDnsFallback(com.mini.me_core.core.network.CachingDns()))
            .build()
    }

    /** 代理 OkHttpClient：挂载 proxySelector，代理启用时走 mihomo mixed-port，未启用时直连。 */
    @Provides
    @Singleton
    @Named("proxy")
    fun provideProxyOkHttpClient(
        proxyRouteHolder: com.mini.me_core.feature.proxy.domain.ProxyRouteHolder,
        httpWarningBridge: com.mini.me_core.core.network.HttpWarningBridge
    ): OkHttpClient {
        // 配置与 directClient 完全一致，唯一区别是挂载了 proxySelector。
        // 后续阶段（阶段4）proxySelector 会感知 ProxyHealthMonitor，代理不通时自动降级直连。
        return OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(com.mini.me_core.core.network.HttpWarningInterceptor(httpWarningBridge))
            .apply {
                if (com.mini.me_core.BuildConfig.DEBUG) {
                    addInterceptor(com.mini.me_core.core.performance.NetworkMonitorInterceptor())
                }
            }
            // 网络代理（§4.2）：注入 ProxyRouteHolder 的路由选择器，启用时代理走 mihomo mixed-port，
            // 未启用直连；以 @Singleton 无依赖 Holder 避免与 ClashProxyManager 成环。
            .proxySelector(proxyRouteHolder.selector)
            .connectionPool(okhttp3.ConnectionPool(8, 15, TimeUnit.MINUTES))
            .dns(com.mini.me_core.core.network.PublicDnsFallback(com.mini.me_core.core.network.CachingDns()))
            .build()
    }

    /**
     * 匿名默认 OkHttpClient：委托给 direct client。
     * 保留双实例隔离前的裸注入兼容（BrowserController / BrowserDownloadManager /
     * WebTranslator / ClashProxyManager / ConnectionPrewarmer 等工具类），这些组件本应直连，
     * 且 ClashProxyManager 自身就是代理管理者，不能再挂载 proxySelector 否则成环。
     */
    @Provides
    @Singleton
    fun provideDefaultOkHttpClient(
        @Named("direct") direct: OkHttpClient
    ): OkHttpClient = direct

    @Provides
    @Singleton
    @Named("OpenAI")
    fun provideOpenAIRetrofit(@Named("direct") client: OkHttpClient): Retrofit {
        // 阶段1：暂时统一用 direct client，后续阶段按 needsProxy 配置路由到 proxyClient。
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("Anthropic")
    fun provideAnthropicRetrofit(@Named("direct") client: OkHttpClient): Retrofit {
        // 阶段1：暂时统一用 direct client，后续阶段按 needsProxy 配置路由到 proxyClient。
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(@Named("OpenAI") retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAnthropicApi(@Named("Anthropic") retrofit: Retrofit): AnthropicApi {
        return retrofit.create(AnthropicApi::class.java)
    }

    @Provides
    @Singleton
    @Named("Gemini")
    fun provideGeminiRetrofit(@Named("direct") client: OkHttpClient): Retrofit {
        // 阶段1：暂时统一用 direct client，后续阶段按 needsProxy 配置路由到 proxyClient。
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiApi(@Named("Gemini") retrofit: Retrofit): com.mini.me_core.feature.agent.data.remote.gemini.GeminiApi {
        return retrofit.create(com.mini.me_core.feature.agent.data.remote.gemini.GeminiApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCommandEngine(delegate: DelegatingCommandEngine): CommandEngine = delegate

    @Provides
    @Singleton
    fun provideFileAccess(delegate: DelegatingFileAccess): FileAccessProvider = delegate

    @Provides
    @Singleton
    fun provideTerminalSessionProvider(delegate: DelegatingTerminalSessionProvider): TerminalSessionProvider = delegate

    @Provides
    @Singleton
    fun provideDelegatingTerminalSessionProvider(
        modeHolder: com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder,
        local: TerminalSessionManager,
        remote: com.mini.me_core.feature.terminal.domain.RemoteTerminalSessionManager
    ): DelegatingTerminalSessionProvider = DelegatingTerminalSessionProvider(modeHolder, local, remote)

    @Provides
    @Singleton
    fun provideRemoteSftpFileAccess(
        connection: RemoteSshConnection,
        workspaceRepository: com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
    ): RemoteSftpFileAccess = RemoteSftpFileAccess(connection, workspaceRepository)

    @Provides
    @Singleton
    fun provideToolResultTypeRegistry(): com.mini.me_core.feature.agent.domain.execution.tool.ToolResultTypeRegistry {
        return com.mini.me_core.feature.agent.domain.execution.tool.ToolResultTypeRegistry()
    }

    @Provides
    @Singleton
    fun provideToolRegistry(
        readFileTool: ReadFileTool,
        sendFileTool: SendFileTool,
        viewImageTool: ViewImageTool,
        writeFileTool: WriteFileTool,
        editFileTool: EditFileTool,
        executeCommandTool: ExecuteCommandTool,
        runCodeTool: com.mini.me_core.feature.agent.domain.execution.tool.container.RunCodeTool,
        checkEnvironmentTool: CheckEnvironmentTool,
        ensureAndroidEnvTool: EnsureAndroidEnvTool,
        switchContainerArchTool: SwitchContainerArchTool,
        terminalSessionTool: TerminalSessionTool,
        listFilesTool: ListFilesTool,
        searchCodeTool: SearchCodeTool,
        loadSkillTool: LoadSkillTool,
        runSkillScriptTool: com.mini.me_core.feature.agent.domain.execution.tool.skill.RunSkillScriptTool,
        loadRuleTool: com.mini.me_core.feature.agent.domain.execution.tool.rule.LoadRuleTool,
        loadSopTool: com.mini.me_core.feature.agent.domain.execution.tool.sop.LoadSopTool,
        askUserQuestionTool: AskUserQuestionTool,
        manageMcpTool: com.mini.me_core.feature.agent.domain.execution.tool.mcp.ManageMcpTool,
        webSearchTool: com.mini.me_core.feature.agent.domain.execution.tool.search.WebSearchTool,
        webFetchTool: com.mini.me_core.feature.agent.domain.execution.tool.search.WebFetchTool,
        switchModeTool: com.mini.me_core.feature.agent.domain.execution.tool.mode.SwitchModeTool,
        todoTool: TodoTool,
        memoryTool: com.mini.me_core.feature.agent.domain.execution.tool.memory.MemoryTool,
        generateImageTool: com.mini.me_core.feature.agent.domain.execution.tool.image.GenerateImageTool,
        browserTool: BrowserAgentTool,
        storageTool: com.mini.me_core.feature.agent.domain.execution.tool.storage.StorageTool,
        networkProxyTool: com.mini.me_core.feature.agent.domain.execution.tool.proxy.NetworkProxyTool,
        goalTool: com.mini.me_core.feature.agent.domain.execution.tool.goal.GoalTool,
        jobStartTool: com.mini.me_core.feature.agent.domain.execution.tool.job.JobStartTool,
        jobStatusTool: com.mini.me_core.feature.agent.domain.execution.tool.job.JobStatusTool,
        jobKillTool: com.mini.me_core.feature.agent.domain.execution.tool.job.JobKillTool,
        jobLogTool: com.mini.me_core.feature.agent.domain.execution.tool.job.JobLogTool,
        scheduleTool: com.mini.me_core.feature.agent.domain.execution.tool.schedule.ScheduleTool,
        planTool: com.mini.me_core.feature.agent.domain.execution.tool.plan.PlanTool,
        intentAnalyzeTool: IntentAnalyzeTool,
        playbookStartTool: com.mini.me_core.feature.agent.domain.execution.tool.playbook.PlaybookStartTool,
        playbookAdvanceTool: com.mini.me_core.feature.agent.domain.execution.tool.playbook.PlaybookAdvanceTool,
        playbookStatusTool: com.mini.me_core.feature.agent.domain.execution.tool.playbook.PlaybookStatusTool,
        playbookAbortTool: com.mini.me_core.feature.agent.domain.execution.tool.playbook.PlaybookAbortTool,
        gitOpsTool: com.mini.me_core.feature.agent.domain.execution.tool.git.GitOpsTool,
        resultTypeRegistry: com.mini.me_core.feature.agent.domain.execution.tool.ToolResultTypeRegistry
    ): ToolRegistry {
        return ToolRegistry().apply {
            // L3 联动注册：工具注册到 ToolRegistry 时，同步把 provides 类型登记到中央注册表，
            // 供依赖调度（L4）、结果缓存（L5）、增量索引（L6）按类型消费。
            fun registerTool(name: String, tool: AgentTool) {
                register(name, tool)
                tool.provides.forEach { type ->
                    resultTypeRegistry.register(
                        type = type,
                        schema = com.mini.me_core.feature.agent.domain.execution.tool.TypeSchema(
                            type = type,
                            capability = tool.capabilities.firstOrNull()
                        ),
                        producer = name
                    )
                }
            }
            registerTool("readFile", readFileTool)
            registerTool("sendFile", sendFileTool)
            registerTool("viewImage", viewImageTool)
            registerTool("writeFile", writeFileTool)
            registerTool("editFile", editFileTool)
            registerTool("Bash", executeCommandTool)
            registerTool("run_code", runCodeTool)
            registerTool("check_environment", checkEnvironmentTool)
            registerTool("ensure_android_env", ensureAndroidEnvTool)
            registerTool("switch_container_arch", switchContainerArchTool)
            registerTool("terminal", terminalSessionTool)
            registerTool("list", listFilesTool)
            registerTool("search", searchCodeTool)
            registerTool("loadSkill", loadSkillTool)
            registerTool("runSkillScript", runSkillScriptTool)
            // ══ 分层规则正文按需加载（D3-3）：load_rule(rule_name) 取完整正文（摘要/正文两级形态）
            registerTool("load_rule", loadRuleTool)
            // ══ SOP 标准作业正文按需加载（D4-4）：loadSop(sop_name) 取完整编号步骤（摘要常驻 + 按需取正文）
            registerTool("loadSop", loadSopTool)
            registerTool("askUserQuestion", askUserQuestionTool)
            registerTool("manageMcp", manageMcpTool)
            registerTool("websearch", webSearchTool)
            registerTool("webfetch", webFetchTool)
            registerTool("switchMode", switchModeTool)
            registerTool("todo", todoTool)
            registerTool("memory", memoryTool)
            // ══ RC69 T2I 文生图工具：generateImage(prompt="...", width, height, steps, hd, model)
            registerTool("generateImage", generateImageTool)
            // ══ 内置服务浏览器：模型在共享 WebView 会话中浏览/操作网页（含容器服务与登录站点）
            registerTool("browser", browserTool)
            // ══ 设备存储护栏工具：结构化 list/read/write/delete + ASK 确认（见设计「护栏」）
            registerTool("device_storage", storageTool)
            // ══ 网络代理工具（VPN 形态）：模型自助管理容器内 mihomo 代理，ASK 确认 + MODIFY_NETWORK 能力隔离
            registerTool("network_proxy", networkProxyTool)
            // ══ 会话任务目标状态机（DSH goal）：set/get/update/done/abandon + 每轮注入
            registerTool("goal", goalTool)
            // ══ 后台任务（DSH jobs）：job_start/status/kill/log，长任务后台执行 + Room 持久化
            registerTool("job_start", jobStartTool)
            registerTool("job_status", jobStatusTool)
            registerTool("job_kill", jobKillTool)
            registerTool("job_log", jobLogTool)
            // ══ 定时提醒（DSH schedule）：create(after/at/every) + list + cancel，到点注入会话
            registerTool("schedule", scheduleTool)
            // ══ 计划协作（DSH plan + Claude Code Plan/Spec）：propose/get/approve/abandon + 每轮注入 pendingSelection
            registerTool("plan", planTool)
            // ══ 用户意图判定平台（D0）：规则预分类五形态 + behaviorMode，Parser 门控（task/command）建议调用
            registerTool("intent_analyze", intentAnalyzeTool)
            // ══ Playbook 剧本编排（D5-4，norm-chain §3.3.5）：start/advance/status/abort 四工具
            registerTool("playbook_start", playbookStartTool)
            registerTool("playbook_advance", playbookAdvanceTool)
            registerTool("playbook_status", playbookStatusTool)
            registerTool("playbook_abort", playbookAbortTool)
            // ══ GitOps 工程化工具：提交规范校验/建议、hooks 状态、发版前体检/打 Tag、版本日志生成
            registerTool("gitops", gitOpsTool)

            // ══ D2-3 轨迹摘要提取器登记（norm-chain §3.8.2：规则表挂 ToolResultTypeRegistry，
            //    仅少数工具定制，其余走通用截断）。提取器签名 (args, resultData) → 一行摘要。
            fun argText(args: Map<String, JsonElement>, key: String): String =
                (args[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            fun firstText(obj: JsonObject, vararg keys: String): String {
                for (key in keys) {
                    (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
                }
                return ""
            }
            // readFile：路径（来自 args）+ 行数（来自结果 total_lines/read_lines）。
            resultTypeRegistry.registerTrajectorySummarizer("readFile") { args, data ->
                val obj = data as? JsonObject
                val lines = obj?.get("read_lines")?.jsonPrimitive?.contentOrNull
                    ?: obj?.get("total_lines")?.jsonPrimitive?.contentOrNull
                    ?: "-"
                val truncated = obj?.get("truncated")?.jsonPrimitive?.contentOrNull == "true"
                "readFile(${argText(args, "path")}) lines=$lines${if (truncated) " [truncated]" else ""}"
            }
            // writeFile：目标文件 + 是否新建/覆盖（路径来自 args，结果 path 展示路径）。
            resultTypeRegistry.registerTrajectorySummarizer("writeFile") { args, data ->
                val path = argText(args, "path").ifBlank { firstText(data as? JsonObject ?: JsonObject(emptyMap()), "path") }
                "writeFile($path) 已写入"
            }
            // editFile：目标文件 + 变更状态（结果 status/path）。
            resultTypeRegistry.registerTrajectorySummarizer("editFile") { args, data ->
                val obj = data as? JsonObject ?: JsonObject(emptyMap())
                val path = argText(args, "path").ifBlank { firstText(obj, "path") }
                val status = firstText(obj, "status").ifBlank { "done" }
                "editFile($path) status=$status"
            }
            // run_code：exit code + stdout 尾部。
            resultTypeRegistry.registerTrajectorySummarizer("run_code") { _, data ->
                val obj = data as? JsonObject ?: JsonObject(emptyMap())
                val exit = obj["exitCode"]?.jsonPrimitive?.contentOrNull ?: "?"
                val tail = (obj["stdout"]?.jsonPrimitive?.contentOrNull ?: "").takeLast(80).replace('\n', ' ')
                "run_code exit=$exit stdout≈$tail"
            }
            // Bash：输出尾部（纯文本结果走通用截断，此处仅标注命令执行）。
            resultTypeRegistry.registerTrajectorySummarizer("Bash") { args, data ->
                val cmd = argText(args, "command").take(60)
                "Bash($cmd)"
            }
        }
    }

    @Provides
    @Singleton
    fun provideCodeChangeTracker(): CodeChangeTracker {
        return CodeChangeTracker()
    }

    @Provides
    @Singleton
    fun provideToolDependencyScheduler(): com.mini.me_core.feature.agent.domain.execution.tool.ToolDependencyScheduler {
        return com.mini.me_core.feature.agent.domain.execution.tool.ToolDependencyScheduler()
    }

    @Provides
    @Singleton
    fun provideToolResultCache(): com.mini.me_core.feature.agent.domain.execution.tool.ToolResultCache {
        return com.mini.me_core.feature.agent.domain.execution.tool.ToolResultCache()
    }

    @Provides
    @Singleton
    fun provideToolEventBus(): com.mini.me_core.feature.agent.domain.execution.tool.ToolEventBus {
        return com.mini.me_core.feature.agent.domain.execution.tool.ToolEventBus()
    }

    @Provides
    @Singleton
    fun provideIncrementalIndexStore(): com.mini.me_core.feature.agent.domain.execution.tool.IncrementalIndexStore {
        return com.mini.me_core.feature.agent.domain.execution.tool.IncrementalIndexStore()
    }

    @Provides
    @Singleton
    fun provideAgentWorkflow(
        toolRegistry: ToolRegistry,
        aiProviderRepository: AIProviderRepository,
        openAIApi: OpenAIApi,
        anthropicApi: AnthropicApi,
        geminiApi: GeminiApi,
        @Named("direct") okHttpClient: OkHttpClient,
        @Named("proxy") proxyOkHttpClient: OkHttpClient,
        proxyHealthMonitor: com.mini.me_core.feature.proxy.domain.ProxyHealthMonitor,
        promptProvider: SystemPromptProvider,
        permissionManager: ToolPermissionManager,
        policyEngine: ToolPermissionPolicyEngine,
        contextCompactor: com.mini.me_core.feature.agent.domain.execution.workflow.ContextCompactor,
        planApprovalManager: com.mini.me_core.feature.agent.domain.execution.tool.mode.PlanApprovalManager,
        toolOutputStore: ToolOutputStore,
        modelMetadataService: ModelMetadataService,
        visionModelSettingsRepository: com.mini.me_core.feature.settings.data.repository.VisionModelSettingsRepository,
        compactionModelSettingsRepository: com.mini.me_core.feature.settings.data.repository.CompactionModelSettingsRepository,
        compatibilityPolicyRepository: com.mini.me_core.feature.settings.data.repository.CompatibilityPolicyRepository,
        sessionUseCase: com.mini.me_core.feature.agent.domain.session.SessionUseCase,
        messagePersistenceUseCase: com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase,
        checkpointManager: com.mini.me_core.feature.agent.domain.session.checkpoint.CheckpointManager,
        dependencyScheduler: com.mini.me_core.feature.agent.domain.execution.tool.ToolDependencyScheduler,
        toolResultCache: com.mini.me_core.feature.agent.domain.execution.tool.ToolResultCache,
        toolEventBus: com.mini.me_core.feature.agent.domain.execution.tool.ToolEventBus,
        incrementalIndexStore: com.mini.me_core.feature.agent.domain.execution.tool.IncrementalIndexStore,
        skillStateRepository: com.mini.me_core.feature.agent.domain.knowledge.skill.SkillStateRepository,
        skillExecutor: com.mini.me_core.feature.agent.domain.knowledge.skill.SkillExecutor,
        skillRuntimeProbe: com.mini.me_core.feature.agent.domain.knowledge.skill.SkillRuntimeProbe,
        hookDispatcher: com.mini.me_core.feature.agent.domain.core.hook.HookDispatcher,
        wakeQueueManager: com.mini.me_core.feature.agent.domain.schedule.wake.WakeQueueManager,
        goalService: com.mini.me_core.feature.agent.domain.session.goal.GoalService,
        planService: com.mini.me_core.feature.agent.domain.session.plan.PlanService,
        toolGuards: Set<@JvmSuppressWildcards com.mini.me_core.feature.agent.domain.core.guard.ToolGuard>,
        fileObservationGuard: com.mini.me_core.feature.agent.domain.core.guard.FileObservationGuard,
        normFlowSettingsRepository: com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository,
        guardLogRepository: com.mini.me_core.feature.agent.domain.core.guard.GuardLogRepository,
        trajectoryService: com.mini.me_core.feature.agent.domain.session.trajectory.TrajectoryService,
        playbookExecutor: com.mini.me_core.feature.agent.domain.knowledge.playbook.PlaybookExecutor
    ): AgentWorkflow {
        return com.mini.me_core.feature.agent.domain.execution.workflow.StatefulAgentWorkflow(
            toolRegistry,
            aiProviderRepository,
            openAIApi,
            anthropicApi,
            geminiApi,
            okHttpClient,
            proxyOkHttpClient,
            proxyHealthMonitor,
            promptProvider,
            permissionManager,
            policyEngine,
            contextCompactor,
            planApprovalManager,
            toolOutputStore,
            modelMetadataService,
            visionModelSettingsRepository,
            compactionModelSettingsRepository,
            compatibilityPolicyRepository,
            sessionUseCase,
            messagePersistenceUseCase,
            checkpointManager,
            dependencyScheduler,
            toolResultCache,
            toolEventBus,
            incrementalIndexStore,
            skillStateRepository,
            skillExecutor,
            skillRuntimeProbe,
            hookDispatcher,
            wakeQueueManager,
            goalService,
            planService,
            toolGuards,
            fileObservationGuard,
            normFlowSettingsRepository,
            guardLogRepository,
            trajectoryService,
            playbookExecutor
        )
    }
}
