package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B2 每轮工具清单组装策略（Deferred Loading 的装载器）。
 *
 * 规则：
 * 1. 灰度开关（[NormFlowSettingsRepository] deferred_tool_loading）关闭 → 原样返回全量清单，
 *    与存量行为逐字节一致（灰度期安全垫）；
 * 2. 开启 → CORE 工具按 [CANONICAL_CORE_ORDER] 规范序在前（跨会话逐字节稳定，保 prompt 缓存前缀），
 *    已展开的 DEFERRED 工具按输入序追加尾部（展开只增尾部，不重排 CORE，缓存前缀不漂移）；
 * 3. sessionId 为空（无会话）时视为无展开集，仅返回 CORE。
 *
 * 与 [ToolRegistry] 的关系：registry 仍是唯一事实源（执行/权限/依赖调度都走它）；
 * 本策略只决定「哪些工具的定义进入本轮模型上下文」，不改变可执行集合。
 */
@Singleton
class ToolLoadingPolicy @Inject constructor(
    private val expansionStore: DeferredToolExpansion,
    private val normFlowSettingsRepository: NormFlowSettingsRepository
) {

    /**
     * 组装本轮请求视图的工具清单。
     *
     * @param sessionId 会话 id（展开集的键；为空表示无会话上下文，仅返回 CORE）
     * @param allTools 当前 registry 全量工具（由调用方每轮现取，反映 MCP 动态注册）
     */
    suspend fun assembleRoundTools(sessionId: String?, allTools: List<AgentTool>): List<AgentTool> {
        if (!isActive()) return allTools
        val expanded = sessionId?.let { expansionStore.getExpanded(it) } ?: emptySet()
        return assemble(allTools, expanded)
    }

    /**
     * 纯函数组装（灰度开启路径）：CORE 规范序 + 已展开 DEFERRED 尾部追加。
     * 暴露为 public 供测试与 SubAgent 收窄路径复用。
     */
    fun assemble(allTools: List<AgentTool>, expanded: Set<String>): List<AgentTool> {
        val core = allTools
            .filter { it.exposure == ToolExposure.CORE }
            .sortedWith(CORE_COMPARATOR)
        val deferredExpanded = allTools.filter {
            it.exposure == ToolExposure.DEFERRED && it.name in expanded
        }
        return core + deferredExpanded
    }

    /**
     * B4 SubAgent 收窄：CORE + tool_catalog 常驻，不消费任何展开集（子代理经 catalog
     * 猜名调用同样可执行——registry 是执行事实源；发现记录进主会话展开集惠及主代理）。
     * 灰度关闭时退化为全量（存量行为）。
     */
    suspend fun assembleCoreTools(allTools: List<AgentTool>): List<AgentTool> {
        if (!isActive()) return allTools
        return allTools
            .filter { it.exposure == ToolExposure.CORE }
            .sortedWith(CORE_COMPARATOR)
    }

    // TooGenericExceptionCaught：KVStore 读取故障类型不可预知（IO/解析/并发），统一按
    //   「关闭」降级是安全方向（全量行为）；FileLogger.w 记录不丢失，CancellationException 先行重抛。
    @Suppress("TooGenericExceptionCaught")
    private suspend fun isActive(): Boolean = try {
        normFlowSettingsRepository.isDeferredToolLoadingActive()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        FileLogger.w(TAG, "读取延迟加载灰度开关失败，按关闭处理（全量行为）", e)
        false
    }

    private companion object {
        const val TAG = "ToolLoadingPolicy"

        /**
         * CORE 规范顺序（prompt 缓存前缀稳定化 A1 的 tools 侧落实）：
         * 跨会话/跨轮逐字节一致。未列入的 CORE 工具（未来新增）按名称序排在末尾，
         * 不破坏已列出工具的相对位置。
         */
        val CANONICAL_CORE_ORDER = listOf(
            "readFile", "writeFile", "editFile", "list", "search", "Bash",
            "todo", "plan", "askUserQuestion", "switchMode", "tool_catalog"
        )

        val CORE_COMPARATOR: Comparator<AgentTool> = Comparator { a, b ->
            val ia = CANONICAL_CORE_ORDER.indexOf(a.name).let { if (it >= 0) it else Int.MAX_VALUE }
            val ib = CANONICAL_CORE_ORDER.indexOf(b.name).let { if (it >= 0) it else Int.MAX_VALUE }
            if (ia != ib) ia - ib else a.name.compareTo(b.name)
        }
    }
}
