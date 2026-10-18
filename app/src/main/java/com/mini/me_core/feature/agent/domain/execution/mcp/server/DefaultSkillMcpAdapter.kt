package com.mini.me_core.feature.agent.domain.execution.mcp.server

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.domain.knowledge.skill.Skill
import com.mini.me_core.feature.agent.domain.knowledge.skill.SkillStateRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F4：技能 → MCP prompts / resources 的适配器（D3 的「按 type 分派」落到实现）。
 *
 * 分派规则只有两条，刻意不引入第三种映射：
 * - [com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType.PROMPT] → `prompts`：
 *   正文即指令模板，用户显式选用后注入上下文。与规范对 prompts「user-controlled」的
 *   定位完全一致。
 * - [com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType.SCRIPT] → `resources`：
 *   `minime://skill/{id}` 寻址的只读内容。
 * - [com.mini.me_core.feature.agent.domain.knowledge.skill.SkillType.MCP] → **不暴露**。
 *   它是「调用某个已连 MCP 工具」的包装，属能力的**消费方**；若同时把它当供给方暴露，
 *   会在协议层形成「技能暴露技能」的环。
 *
 * 四个方法共用同一条分派谓词（[asPromptOrNull] / [asResourceOrNull]），确保
 * `list` 与 `get` 的可见性口径**完全一致**——否则会出现「list 里没有、get 却能取到」
 * 的悬空条目。
 *
 * 数据源是 [SkillStateRepository.listSkillsSync] 而非 `skillsFlow`：MCP 请求是
 * 同步一问一答，取不到也等不来 Flow；且技能列表规模有限（几十条），同步读盘可接受。
 */
@Singleton
class DefaultSkillMcpAdapter @Inject constructor(
    private val skillStateRepository: SkillStateRepository
) : SkillMcpAdapter {

    /**
     * 语义是「**当前**是否有可暴露技能」（prompt 或 resource 任一非空），
     * 而非「技能源里有没有条目」——全部技能都是 MCP 型或都被禁用时，
     * 对外既不声明 prompts 也不声明 resources。
     *
     * 若声明了能力却恒返回空列表，等于对外承诺一个永远空实现的接口，
     * 客户端会反复重试。故此判定必须与 list 口径共用同一批分派谓词。
     */
    override fun hasSkills(): Boolean = listPrompts().isNotEmpty() || listResources().isNotEmpty()

    override fun listPrompts(): List<Skill> = exposeableSkills().mapNotNull { it.asPromptOrNull() }

    override fun listResources(): List<Skill> = exposeableSkills().mapNotNull { it.asResourceOrNull() }

    override fun getPrompt(id: String): Skill? =
        exposeableSkills().firstOrNull { it.id == id }?.asPromptOrNull()

    override fun getResource(uri: String): Skill? {
        val id = SkillResourceUri.parseId(uri) ?: return null
        return exposeableSkills().firstOrNull { it.id == id }?.asResourceOrNull()
    }

    /**
     * 读取技能列表，失败回退空列表。
     *
     * **绝不抛出**：本方法位于 MCP 请求处理路径上，技能仓库出错（如目录权限异常）
     * 不应让整个 server 变成 500——退化成「当前无技能暴露」才是正确降级。
     */
    private fun exposeableSkills(): List<Skill> = runCatching { skillStateRepository.listSkillsSync() }
        .onFailure { FileLogger.w(TAG, "读取技能列表失败，MCP 侧降级为不暴露技能: ${it.message}") }
        .getOrDefault(emptyList())

    private companion object {
        const val TAG = "DefaultSkillMcpAdapter"
    }
}
