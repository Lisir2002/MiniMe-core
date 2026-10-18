package com.mini.me_core.feature.agent.domain.execution.tool

import com.mini.me_core.feature.agent.domain.core.model.AgentContext
import dagger.Lazy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B2 `tool_catalog` 元工具：DEFERRED 工具的「查菜单」入口，常驻上下文（CORE）。
 *
 * 工作方式：模型给出关键词 → [ToolCatalog] 对当前 registry 全量工具检索 →
 * 命中工具名刷新进 [DeferredToolExpansion]（下一轮起这些工具进入上下文）→
 * 结果携带命中工具的名称/描述/关键参数，供模型当轮决策、下轮直接调用。
 *
 * 与「模型直接猜工具名调用」的关系：猜错会得到 TOOL_NOT_FOUND（registry 无此工具），
 * 猜对则调用路径本身也会刷新展开集——两条发现路径都收敛到同一 LRU。
 *
 * sessionId 为空（无会话上下文）时仍返回检索结果，仅跳过展开集刷新（不阻断主流程）。
 */
@Singleton
class ToolCatalogTool @Inject constructor(
    private val toolCatalog: ToolCatalog,
    private val expansionStore: DeferredToolExpansion,
    /**
     * Lazy 注入 [ToolRegistry]：本工具要注册进 registry（provideToolRegistry 依赖本类），
     * 构成 DI 构造环；检索发生在执行期（远晚于图构建），Lazy 延迟取用打破环
     * （对齐 SubAgentRunner 的 Lazy 注入先例）。
     */
    private val toolRegistry: Lazy<ToolRegistry>
) : AbstractContextualTool() {

    override val name = "tool_catalog"

    override val description =
        "检索可用工具目录。当前只暴露核心工具；当你需要的能力不在已列出工具中时，" +
            "用本工具按关键词（中英文均可）检索完整工具清单，命中后相关工具将在下一轮可用。"

    /** B1：目录入口必须常驻，否则模型永远发现不了 deferred 工具。 */
    override val exposure = ToolExposure.CORE

    override val parameters = mapOf(
        "query" to ToolParameter(
            "query",
            ParameterType.STRING,
            "检索关键词，描述你想做的事（如「网页搜索」「后台任务」「定时提醒」「git 提交」）。中英文均可。",
            required = true
        )
    )

    override suspend fun executeWithContext(
        args: Map<String, JsonElement>,
        context: AgentContext
    ): ToolResult {
        val query = (args["query"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (query.isEmpty()) {
            return ToolResult.Error("缺少 query 参数（检索关键词）", "MISSING_QUERY")
        }
        val hits = toolCatalog.search(toolRegistry.get().getAvailableTools(), query, ToolCatalog.DEFAULT_LIMIT)
        // 刷新展开集：仅 deferred 命中需要展开（CORE 本就常驻，记入只会浪费 LRU 配额）。
        val deferredHits = hits.filter { it.exposure == ToolExposure.DEFERRED }
        context.sessionId?.let { sid ->
            if (deferredHits.isNotEmpty()) expansionStore.recordHit(sid, deferredHits.map { it.name })
        }
        return ToolResult.Success(
            buildJsonObject {
                put("query", query)
                put("hit_count", hits.size)
                putJsonArray("tools") {
                    hits.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                putJsonArray("parameters") {
                                    tool.parameters.values.forEach { param ->
                                        add(
                                            buildJsonObject {
                                                put("name", param.name)
                                                put("type", param.type.name.lowercase())
                                                put("required", param.required)
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
                put(
                    "hint",
                    if (hits.isEmpty()) {
                        "未命中任何工具。可换个关键词重试（如用更通用的能力词），或检查是否已有工具可完成该任务。"
                    } else {
                        "命中工具已加入本会话可用集（下一轮起可直接调用）。若需要的工具未命中，可更换关键词再检索。"
                    }
                )
            }
        )
    }
}
