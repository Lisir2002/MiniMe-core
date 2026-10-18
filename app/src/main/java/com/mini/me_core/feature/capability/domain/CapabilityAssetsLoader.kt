package com.mini.me_core.feature.capability.domain

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpPromptDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.McpResourceDescriptor
import com.mini.me_core.feature.agent.domain.execution.mcp.OptionalCapability
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** G4：能力资产的两类——对应 MCP 的两项可选能力。 */
public enum class AssetKind { PROMPT, RESOURCE }

/**
 * G4：一个可选能力列表在 UI 上的状态。
 *
 * **必须是五态而不是「列表 + 是否加载中」二态**：后者会把 [Unsupported] 和 [Failed]
 * 都渲成「暂无」，用户既不知道该不该点重试，也不知道是 server 没这功能还是网络断了。
 * 这正是 [OptionalCapability] 存在的理由，这里是它通向 UI 的最后一段。
 */
public sealed interface AssetListState<out T> {

    /** 尚未发起枚举。也用作「该分组从详情页切走后上一轮结果的保留态」。 */
    public data object Idle : AssetListState<Nothing>

    public data object Loading : AssetListState<Nothing>

    /** 枚举成功；[items] 为空就是真的没有，不得再当成失败渲染。 */
    public data class Ready<out T>(val items: List<T>) : AssetListState<T>

    /** server 不具备该能力（未连接、未声明、回 -32601）。 */
    public data object Unsupported : AssetListState<Nothing>

    /** 枚举失败。[reason] 仅用于日志与错误行，不保证对用户友好。 */
    public data class Failed(val reason: String) : AssetListState<Nothing>
}

/**
 * G4：单条资产正文的取值状态。
 *
 * 与 [AssetListState] 分开是因为二者的重试语义不同：列表失败通常意味着整个能力不可用，
 * 而单条正文失败（缺参数、该条被删）一般只影响这一条，不必把整页顶掉。
 */
public sealed interface AssetBodyState {

    public data object Idle : AssetBodyState

    public data object Loading : AssetBodyState

    /** [text] 可能为空串——那时是「这条资产正文确实为空」，UI 应如实说明而非报错。 */
    public data class Ready(val text: String) : AssetBodyState

    public data class Failed(val reason: String) : AssetBodyState
}

/**
 * G4：能力资产（prompts / resources）的加载器。
 *
 * 放 domain 层而非 ViewModel，是为了让「三态怎么映射」这段最容易写错的逻辑可被纯 JVM
 * 单测覆盖——本仓目前没有 ViewModel 测试所需的 `kotlinx-coroutines-test`
 * （`Dispatchers.setMain` 不可用），ViewModel 里的逻辑无法可靠地断言。
 * 因此所有方法都写成**普通 suspend**：由调用方（ViewModel）在自己的 scope 里 launch，
 * 测试侧用 `runBlocking` 直接跑，两边都不需要 Main dispatcher。
 *
 * **按 server 名 + 资产标识分桶缓存 StateFlow**，而不是一个「当前 server」的单一态：
 * 详情页在分组间来回切时，切回来的那一页若每次都从头加载，用户刚看过的正文会丢；
 * 保留上次结果则由「下拉/重试」显式刷新。
 *
 * @param mcpManager 唯一的协议面入口；本类不做任何重试之外的编排。
 */
@Singleton
public class CapabilityAssetsLoader @Inject constructor(
    private val mcpManager: McpManager
) {
    private val promptLists = ConcurrentHashMap<String, MutableStateFlow<AssetListState<McpPromptDescriptor>>>()
    private val resourceLists = ConcurrentHashMap<String, MutableStateFlow<AssetListState<McpResourceDescriptor>>>()
    private val bodies = ConcurrentHashMap<String, MutableStateFlow<AssetBodyState>>()

    /** 某 server 的 prompts 列表状态流，首次访问时为 [AssetListState.Idle]。 */
    public fun promptsOf(serverName: String): StateFlow<AssetListState<McpPromptDescriptor>> =
        promptLists.getOrPut(serverName) { MutableStateFlow(AssetListState.Idle) }.asStateFlow()

    /** 某 server 的 resources 列表状态流。 */
    public fun resourcesOf(serverName: String): StateFlow<AssetListState<McpResourceDescriptor>> =
        resourceLists.getOrPut(serverName) { MutableStateFlow(AssetListState.Idle) }.asStateFlow()

    /**
     * 某条资产的正文状态流。
     *
     * [ref] 对 prompt 是其 name、对 resource 是其 uri——两者都是各自协议面里的主键。
     */
    public fun bodyOf(kind: AssetKind, serverName: String, ref: String): StateFlow<AssetBodyState> =
        bodies.getOrPut(bodyKey(kind, serverName, ref)) { MutableStateFlow(AssetBodyState.Idle) }.asStateFlow()

    /**
     * 枚举某 server 的 prompts。
     *
     * @param force true 时忽略 [AssetListState.Loading] 去重。用于“重试”按钮：
     *   正常情况下 Loading 中重复发起只会多打一次 `prompts/list`；但上一轮若卡住
     *   （远端不响应且当前实现无请求超时），不强制就再也重来不了。
     */
    public suspend fun loadPrompts(serverName: String, force: Boolean = false) {
        val target = promptLists.getOrPut(serverName) { MutableStateFlow(AssetListState.Idle) }
        if (!force && target.value is AssetListState.Loading) return
        target.value = AssetListState.Loading
        val result = runCatching { mcpManager.listServerPromptsDetailed(serverName) }
            .getOrElse { e ->
                FileLogger.w(TAG, "[$serverName] 枚举 prompts 异常: ${e.message}")
                OptionalCapability.Failed(e.message.orEmpty())
            }
        target.value = result.toListState()
    }

    /** 枚举某 server 的 resources；语义与 [loadPrompts] 相同。 */
    public suspend fun loadResources(serverName: String, force: Boolean = false) {
        val target = resourceLists.getOrPut(serverName) { MutableStateFlow(AssetListState.Idle) }
        if (!force && target.value is AssetListState.Loading) return
        target.value = AssetListState.Loading
        val result = runCatching { mcpManager.listServerResourcesDetailed(serverName) }
            .getOrElse { e ->
                FileLogger.w(TAG, "[$serverName] 枚举 resources 异常: ${e.message}")
                OptionalCapability.Failed(e.message.orEmpty())
            }
        target.value = result.toListState()
    }

    /**
     * 取一条 asset 的正文。
     *
     * 失败**必须**落到 [AssetBodyState.Failed] 而不是吞掉：`prompts/get` 最常见的失败
     * 是缺必填参数，若静默变回「未展开」，用户只会以为没点上，反复点同一处。
     * 同理 [McpException] 的 message 直接透给用户——它是唯一能指出缺哪个参数的信息。
     */
    public suspend fun fetchBody(
        kind: AssetKind,
        serverName: String,
        ref: String,
        arguments: Map<String, String> = emptyMap()
    ) {
        val target = bodies.getOrPut(bodyKey(kind, serverName, ref)) { MutableStateFlow(AssetBodyState.Idle) }
        target.value = AssetBodyState.Loading
        val text = runCatching {
            when (kind) {
                AssetKind.PROMPT -> mcpManager.getServerPrompt(serverName, ref, arguments)
                AssetKind.RESOURCE -> mcpManager.readServerResource(serverName, ref)
            }
        }.getOrElse { e ->
            FileLogger.w(TAG, "[$serverName] 取 ${kind.name} 正文失败 ($ref): ${e.message}")
            target.value = AssetBodyState.Failed(e.message.orEmpty())
            return
        }
        target.value = AssetBodyState.Ready(text)
    }

    /** 正文 key：`kind|server|ref` 三段拼接，过 kind 防止同名 prompt 与 resource 撞键。 */
    private fun bodyKey(kind: AssetKind, serverName: String, ref: String): String =
        "${kind.ordinal}|$serverName|$ref"

    private fun <T> OptionalCapability<T>.toListState(): AssetListState<T> = when (this) {
        is OptionalCapability.Listed -> AssetListState.Ready(items)
        is OptionalCapability.Unsupported -> AssetListState.Unsupported
        is OptionalCapability.Failed -> AssetListState.Failed(reason)
    }

    private companion object {
        const val TAG = "CapabilityAssetsLoader"
    }
}
