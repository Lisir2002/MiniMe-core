package com.mini.me_core.feature.capability.domain

import com.mini.me_core.feature.agent.domain.execution.mcp.McpConfigRepository
import com.mini.me_core.feature.agent.domain.execution.mcp.McpManager
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerConfig
import com.mini.me_core.feature.agent.domain.execution.mcp.McpServerStatus
import com.mini.me_core.feature.agent.domain.execution.mcp.McpTool
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import com.mini.me_core.feature.agent.domain.execution.tool.AgentTool
import com.mini.me_core.feature.agent.domain.execution.tool.BuiltinGroup
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCapability
import com.mini.me_core.feature.agent.domain.execution.tool.ToolExposure
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionPolicy
import com.mini.me_core.feature.agent.domain.execution.tool.ToolRegistry
import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillStateRepository
import com.mini.me_core.feature.settings.data.repository.ExecutionMode
import com.mini.me_core.feature.settings.data.repository.ExecutionModeHolder
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * F2 能力卡片 UI 模型。
 *
 * 相比旧 `CapabilityCenterViewModel.ToolUiModel` 多了三枚徽章所需的字段：
 * [exposure]（常驻 / 按需）、[requiresApproval]（需审批）、[builtinGroup]（来源分组）。
 */
data class CapabilityToolCard(
    val name: String,
    val description: String,
    val permissionPolicy: ToolPermissionPolicy,
    val capabilities: List<ToolCapability>,
    val parameters: List<CapabilityParamCard>,
    /** 常驻（CORE）/ 按需（DEFERRED）——批次 B 分级加载的 UI 投影。 */
    val exposure: ToolExposure,
    /** 是否需要用户审批方可执行（本地策略判定，不采信 MCP annotations）。 */
    val requiresApproval: Boolean,
    /** 所属内置分组；远端 MCP 工具为 null。 */
    val builtinGroup: BuiltinGroup?,
    /** 提供该工具的远端 server 名；内置工具为 null。 */
    val sourceServerName: String?
)

/** 工具参数 UI 模型。 */
data class CapabilityParamCard(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean
)

/**
 * 一个能力分组（内置分组或远端 server）在列表中的呈现。
 *
 * 内置分组与远端 server **同构**：二者都产出「一组 MCP 工具 + 连接状态」，
 * 因此单页列表可以用同一个 Composable 渲染，不必为内置能力另写一套页面。
 */
data class CapabilityGroupCard(
    val id: String,
    /** 展示名（内置分组用 `BuiltinGroup.displayName`，远端用 server 名）。 */
    val displayName: String,
    val tools: List<CapabilityToolCard>,
    /** 仅远端 server 有连接状态；内置分组恒为 null。 */
    val status: McpServerStatus?,
    /** 内置分组为 true，远端 server 为 false。 */
    val isBuiltin: Boolean,
    /** 是否允许被外部 MCP 客户端触达。 */
    val remotelyExposed: Boolean
)

/** 顶部全局状态卡的五个展示位（D4：原「Agent」Tab 升格）。 */
data class CapabilityStatusCard(
    val executionMode: ExecutionMode,
    val activeProviderName: String?,
    val activeProviderModel: String?,
    val permissionRuleCount: Int,
    val toolCount: Int,
    val skillCount: Int
)

/**
 * F2 统一能力页的聚合状态。
 *
 * [groups] 把内置三分组与远端 server 拍平成**同一个列表**（这就是「页面合并」的数据形态），
 * 顺序稳定：内置分组按 [BuiltinGroup] 声明序，远端 server 按名称排序——保证重组不抖动。
 */
data class CapabilityOverview(
    val groups: List<CapabilityGroupCard> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val statusCard: CapabilityStatusCard? = null,
    /** `unified_capability_page_enabled` 灰度开关；false 时页面回退到旧三Tab 形态。 */
    val unifiedPageEnabled: Boolean = false
)

/**
 * F2 聚合器的数据源集合。
 *
 * 把这组依赖显式命名成「数据源清单」，而不是在聚合器构造函数里平铺 8 个参数：
 * 一是让「这个类依赖什么」一目了然，新增数据源的位置也固定；二是避免以后悄悄往
 * 构造函数里插参数而不自知。
 *
 * 8 个参数本身不构成设计问题——它们是「统一能力页」这一个页面所需数据源的**完整**
 * 清单，删掉任何一个都会让页面少一块信息。刻意不再拆分（如按工具/技能各出一个源类）：
 * 那只会把同一批 Flow 的订阅点分散到多处，重算时机彼此不同步，反而制造「某些数字
 * 更新、另一些不更新」的不一致中间态。故按 detekt 建议显式豁免并说明理由。
 */
@Singleton
@Suppress("LongParameterList")
class CapabilitySources @Inject constructor(
    val toolRegistry: ToolRegistry,
    val mcpManager: McpManager,
    val mcpConfigRepository: McpConfigRepository,
    val skillStateRepository: SkillStateRepository,
    val executionModeHolder: ExecutionModeHolder,
    val aiProviderRepository: AIProviderRepository,
    val permissionRulesRepository: PermissionRulesRepository,
    val normFlowSettingsRepository: NormFlowSettingsRepository
)

/**
 * F2 统一能力聚合器。
 *
 * 存在的理由是旧 `CapabilityCenterViewModel` 的一次性快照缺陷：它在**构造期**读
 * `ToolRegistry.getAvailableTools()` 存成普通 `List`，而远端 MCP 工具是运行期动态
 * 注册/注销的——进入页面后连接或断开任一 server，工具列表都不会变化。
 *
 * 这里改为**全响应式**：以 [ToolRegistry.version] 作为变更信号（`ToolRegistry` 内部是
 * `ConcurrentHashMap`，无变更通知，故需显式版本号），与其余数据源 `combine` 成单一
 * [StateFlow]。UI 只订阅一个流，避免多源各自 collect 造成的不一致中间态。
 *
 * 单一事实源对照：
 * - 工具/分组：[ToolRegistry]
 * - server 配置：[McpConfigRepository.serversFlow]（**不是** `McpManager`，后者只管连接态）
 * - 连接态：`McpManager.statuses`
 * - 执行模式 / Provider / 权限规则 / 技能：各自仓储的 Flow
 */
@Singleton
class CapabilityRegistry @Inject constructor(
    private val sources: CapabilitySources
) {
    private val toolRegistry: ToolRegistry get() = sources.toolRegistry
    private val skillStateRepository: SkillStateRepository get() = sources.skillStateRepository
    private val executionModeHolder: ExecutionModeHolder get() = sources.executionModeHolder
    private val aiProviderRepository: AIProviderRepository get() = sources.aiProviderRepository
    private val permissionRulesRepository: PermissionRulesRepository get() = sources.permissionRulesRepository
    private val normFlowSettingsRepository: NormFlowSettingsRepository get() = sources.normFlowSettingsRepository

    /**
     * 统一能力页的完整聚合状态。
     *
     * 八个数据源 `combine`；kotlinx 最多支持 5 参重载，故分两层：先合成
     * 「工具分组 + server 分组」，再与状态卡 / 技能 / 开关相乘。
     */
    val overview: StateFlow<CapabilityOverview> =
        combine(
            combine(
                toolRegistry.version,
                sources.mcpConfigRepository.serversFlow,
                sources.mcpManager.statuses
            ) { _, servers, statuses -> buildGroups(servers, statuses) },
            skillStateRepository.skillsFlow,
            normFlowSettingsRepository.unifiedCapabilityPageEnabledFlow
        ) { groups, skills, unified ->
            CapabilityOverview(groups = groups, skills = skills, unifiedPageEnabled = unified)
        }.let { base ->
            combine(
                base,
                executionModeHolder.mode,
                aiProviderRepository.getActiveProvider(),
                combine(
                    permissionRulesRepository.globalRulesFlow,
                    permissionRulesRepository.currentProjectRulesFlow
                ) { global, project -> global.size + project.size }
            ) { overview, mode, provider, ruleCount ->
                overview.copy(
                    statusCard = CapabilityStatusCard(
                        executionMode = mode,
                        activeProviderName = provider?.name,
                        activeProviderModel = provider?.effectiveModel,
                        permissionRuleCount = ruleCount,
                        // 走注册表全量而非分组求和：远端 server 配置被删但工具尚未注销的
                        // 短暂窗口内，该工具不属于任何分组，按分组求和会漏计。
                        // 本函数已由 toolRegistry.version 触发，读的是重算后的快照。
                        toolCount = toolRegistry.getAvailableTools().size,
                        skillCount = overview.skills.size
                    )
                )
            }
        }.stateIn(
            scope = CoroutineScopeHolder.scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CapabilityOverview()
        )

    /**
     * 把内置三分组与远端 server 拍平成同构列表。
     *
     * 内置分组恒排在远端 server 之前：它们是代码内置、必然存在的基础能力；
     * 远端 server 按名称排序，保证跨进程的稳定次序（重组不抖动、diff 可用）。
     */
    /**
     * 远端 server 名 -> 其注册的工具名。
     *
     * **每次分组重算时重建，绝不缓存**：工具注册表是运行期可变的（远端 MCP server
     * 连接/断开即增删），若用 `by lazy` 缓存这张映射，那么第一次算出它之后所有新增的远端
     * 工具都不会出现在任何分组卡片里——而工具数量、状态卡计数却会变，页面自相矛盾。
     * 重建成本是O(工具数)，远低于一次重组。
     */
    private fun remoteToolsByServer(): Map<String, List<String>> =
        toolRegistry.getAvailableTools()
            .mapNotNull { tool -> tool.remoteServerName?.let { it to tool.name } }
            .groupBy({ it.first }, { it.second })

    private fun buildGroups(
        servers: List<McpServerConfig>,
        statuses: List<McpServerStatus>
    ): List<CapabilityGroupCard> {
        val statusByName = statuses.associateBy { it.name }

        // 内置分组：直接读 ToolRegistry 的分组表（执行面真源），不依赖 MCP 开关是否开启——
        // 即使 `builtin_as_mcp_enabled` 关闭，内置能力也照样存在，只是没有协议面 server。
        val builtinCards = BuiltinGroup.entries.map { group ->
            val tools = toolRegistry.getToolsByBuiltinGroup(group).map { it.toCard(group, null) }
            CapabilityGroupCard(
                id = group.serverName,
                displayName = group.displayName,
                tools = tools,
                status = null,
                isBuiltin = true,
                remotelyExposed = group.remotelyExposedByDefault
            )
        }

        // 远端 server：工具来自其协议面工具名在 ToolRegistry 的登记结果。
        val remoteCards = servers.filterNot { it.isBuiltin }
            .sortedBy { it.name }
            .map { cfg ->
                val tools = remoteToolsByServer()[cfg.name].orEmpty()
                    .mapNotNull { toolRegistry.getTool(it) }
                    .map { it.toCard(null, cfg.name) }
                CapabilityGroupCard(
                    id = cfg.name,
                    displayName = cfg.name,
                    tools = tools,
                    status = statusByName[cfg.name],
                    isBuiltin = false,
                    remotelyExposed = cfg.remotelyExposed
                )
            }

        return builtinCards + remoteCards
    }
}

/** 工具是否需要用户审批方可执行。 */
private fun AgentTool.requiresApproval(): Boolean = permissionPolicy == ToolPermissionPolicy.ASK

/** 远端 MCP 工具专属：返回提供它的 server 名；内置工具返回 null。 */
private val AgentTool.remoteServerName: String?
    get() = (this as? McpTool)?.serverName

private fun AgentTool.toCard(group: BuiltinGroup?, server: String?) = CapabilityToolCard(
    name = name,
    description = description,
    permissionPolicy = permissionPolicy,
    capabilities = capabilities.toList(),
    parameters = parameters.values.map {
        CapabilityParamCard(
            name = it.name,
            type = it.type.name.lowercase(),
            description = it.description,
            required = it.required
        )
    },
    exposure = exposure,
    requiresApproval = requiresApproval(),
    builtinGroup = group,
    sourceServerName = server
)

/**
 * 聚合器的进程级 scope。
 *
 * `stateIn` 需要一个长期存在的 scope，而 `@Singleton` 聚合器不随 ViewModel 消亡——
 * 用独立 scope 而非某个 ViewModel 的 `viewModelScope`，避免页面关闭后聚合就此停摆，
 * 下次进入页面又要重建全量快照。
 */
private object CoroutineScopeHolder {
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() +
            kotlinx.coroutines.Dispatchers.Default +
            kotlinx.coroutines.CoroutineName("CapabilityRegistry")
    )
}
