package com.mini.me_core.feature.agent.domain.knowledge.skill

import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state as V2ConvState
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * 技能解析结果（RC74 新增）：技能 + 其依赖解析状态。
 */
data class SkillResolution(
    val skill: Skill,
    val dependencies: List<Skill> = emptyList(),   // 已解析的依赖（依赖序，先依赖后自身）
    val missingDependencies: List<String> = emptyList(), // 缺失的依赖 id
    val disabledDependencies: List<String> = emptyList() // 被禁用的依赖 id
) {
    val isResolvable: Boolean
        get() = missingDependencies.isEmpty() && disabledDependencies.isEmpty()
}

/**
 * 技能状态仓库端口（RC74 新增，v47 扩展作用域覆盖 + 对话级双向控制）。
 * 实现（磁盘技能扫描 + SQLDelight 状态读写）位于 data 层。
 */
@Suppress("TooManyFunctions") // 端口面按全部调用点聚合，行为保持搬迁，不拆分接口
interface SkillStateRepository {

    companion object {
        private const val DEFAULT_AGENT_TYPE = "coding"
    }

    /** 响应式技能列表：磁盘扫描 + Room 启用状态与作用域覆盖合并。 */
    val skillsFlow: Flow<List<Skill>>

    /** 一次性技能列表（含启用状态与作用域覆盖）。 */
    suspend fun listSkills(): List<Skill>

    /** 技能根目录（导入/导出/编辑器定位目录用）。 */
    fun skillsRoot(): File

    /** 同步一次性技能列表（含启用状态与作用域覆盖）。 */
    fun listSkillsSync(): List<Skill>

    /** 启用/禁用技能（即时生效，写 DB）。 */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /** 安装技能：复制到技能目录 + 写 DB 状态。 */
    suspend fun install(sourceDir: File): Skill?

    /** 卸载技能：删除目录 + 删除 DB 状态（含对话级绑定）。 */
    suspend fun uninstall(id: String): Boolean

    /** 更新技能：覆盖目录 + 更新 DB 版本。 */
    suspend fun update(id: String, sourceDir: File): Skill?

    /** 设置作用域用户覆盖（NULL=清除覆盖，跟随 frontmatter 声明）。AGENT 级可同时设置绑定的 agentType。 */
    suspend fun setScopeOverride(id: String, scope: SkillScope?, agentType: String? = null)

    /** 对话级双向控制：设置技能在某对话内的生效状态（true=添加/启用，false=本对话临时禁用）。 */
    suspend fun setConversationEnabled(skillId: String, sessionId: String, enabled: Boolean)

    /** 移除技能在某对话的绑定记录（恢复跟随声明）。 */
    suspend fun removeConversationBinding(skillId: String, sessionId: String)

    /** 某对话内全部技能关系（供对话技能面板展示）。 */
    suspend fun listConversationStates(sessionId: String): List<V2ConvState>

    /** 某对话内某技能的绑定状态（无绑定返回 null = 跟随声明）。 */
    suspend fun getConversationState(skillId: String, sessionId: String): V2ConvState?

    /**
     * 作用域严格隐藏过滤：返回在「当前 agent + 当前会话」下可见的技能。
     */
    suspend fun filterVisibleSkills(
        skills: List<Skill>,
        sessionId: String?,
        agentType: String = DEFAULT_AGENT_TYPE
    ): List<Skill>

    /** 同步版作用域过滤（供非协程上下文）。 */
    fun filterVisibleSkillsSync(
        skills: List<Skill>,
        sessionId: String?,
        agentType: String = DEFAULT_AGENT_TYPE
    ): List<Skill>

    /**
     * 依赖解析：返回 [id] 技能及其依赖（依赖序，先依赖后自身）。
     */
    suspend fun resolveSkillWithDependencies(id: String): SkillResolution?
}
