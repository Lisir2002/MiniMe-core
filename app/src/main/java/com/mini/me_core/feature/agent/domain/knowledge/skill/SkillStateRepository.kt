package com.mini.me_core.feature.agent.domain.knowledge.skill

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.isEnabled
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state as V2ConvState
import com.mini.mecore.datalayer.sqldelight.agent.Skill_state as V2SkillState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

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
 * 技能状态仓库（RC74 新增，v47 扩展作用域覆盖 + 对话级双向控制）。
 */
@Singleton
class SkillStateRepository @Inject constructor(
    private val localDirectorySkillSource: LocalDirectorySkillSource,
    private val v2Agent: V2AgentRepository,
) {
    private companion object {
        const val TAG = "SkillStateRepository"
        const val DEFAULT_AGENT_TYPE = "coding"
    }

    private suspend fun getSkillStates(): List<V2SkillState> =
        v2Agent.listSkillStates()

    private fun getSkillStatesSync(): List<V2SkillState> =
        runBlocking(Dispatchers.IO) { v2Agent.listSkillStates() }

    private suspend fun getConvStates(sessionId: String): List<V2ConvState> =
        v2Agent.listSkillConversationStates(sessionId)

    private fun getConvStatesSync(sessionId: String): List<V2ConvState> =
        runBlocking(Dispatchers.IO) { v2Agent.listSkillConversationStates(sessionId) }

    /** 磁盘技能变更刷新触发器（UI 在安装/卸载/更新后自增以触发重扫）。 */
    private val refreshTrigger = MutableStateFlow(0)

    /** 响应式技能列表：磁盘扫描 + Room 启用状态与作用域覆盖合并。 */
    val skillsFlow: Flow<List<Skill>> =
        combine(
            refreshTrigger,
            v2Agent.observeAllSkillStates()
        ) { _, states ->
            mergeWithState(localDirectorySkillSource.listSkills(), states)
        }

    /** 一次性技能列表（含启用状态与作用域覆盖）。 */
    suspend fun listSkills(): List<Skill> {
        val states = getSkillStates()
        return mergeWithState(localDirectorySkillSource.listSkills(), states)
    }

    /** 技能根目录（导入/导出/编辑器定位目录用）。 */
    fun skillsRoot(): java.io.File = localDirectorySkillSource.skillsRoot

    /** 同步一次性技能列表（含启用状态与作用域覆盖）。 */
    fun listSkillsSync(): List<Skill> {
        val states = getSkillStatesSync()
        return mergeWithState(localDirectorySkillSource.listSkills(), states)
    }

    private fun mergeWithState(skills: List<Skill>, states: List<V2SkillState>): List<Skill> {
        val stateById = states.associateBy { it.id }
        return skills.map { skill ->
            val state = stateById[skill.id]
            if (state == null) {
                skill.copy(enabled = true)
            } else {
                skill.copy(
                    enabled = state.isEnabled,
                    scope = state.scope_override?.let { ov -> runCatching { SkillScope.valueOf(ov) }.getOrNull() }
                        ?: skill.scope,
                    agentType = state.agent_type_override ?: skill.agentType
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    /** 启用/禁用技能（即时生效，写 DB）。 */
    suspend fun setEnabled(id: String, enabled: Boolean) {
        runCatching {
            v2Agent.setSkillStateEnabled(id, if (enabled) 1L else 0L)
        }
            .onFailure { FileLogger.e(TAG, "更新技能启用状态失败: $id", it) }
        refreshTrigger.value++
    }

    /** 安装技能：复制到技能目录 + 写 DB 状态。 */
    suspend fun install(sourceDir: java.io.File): Skill? {
        val installed = localDirectorySkillSource.install(sourceDir) ?: return null
        v2Agent.upsertSkillState(
            id = installed.id, enabled = 1L, version = installed.version,
            source = SkillSourceType.LOCAL.name,
            installedAtMs = System.currentTimeMillis(), scopeOverride = null, agentTypeOverride = null,
        )
        refreshTrigger.value++
        return installed
    }

    /** 卸载技能：删除目录 + 删除 DB 状态（含对话级绑定）。 */
    suspend fun uninstall(id: String): Boolean {
        val ok = localDirectorySkillSource.uninstall(id)
        if (ok) {
            runCatching {
                v2Agent.deleteSkillStateById(id)
                v2Agent.deleteSkillConversationStatesBySkill(id)
            }
                .onFailure { FileLogger.e(TAG, "删除技能状态失败: $id", it) }
            refreshTrigger.value++
        }
        return ok
    }

    /** 更新技能：覆盖目录 + 更新 DB 版本。 */
    suspend fun update(id: String, sourceDir: java.io.File): Skill? {
        val updated = localDirectorySkillSource.update(id, sourceDir) ?: return null
        val existing = v2Agent.getSkillState(id)
        v2Agent.upsertSkillState(
            id = updated.id,
            enabled = if (existing?.isEnabled ?: true) 1L else 0L,
            version = updated.version,
            source = existing?.source ?: SkillSourceType.LOCAL.name,
            installedAtMs = existing?.installed_at_ms ?: System.currentTimeMillis(),
            scopeOverride = existing?.scope_override,
            agentTypeOverride = existing?.agent_type_override,
        )
        refreshTrigger.value++
        return updated
    }

    /** 设置作用域用户覆盖（NULL=清除覆盖，跟随 frontmatter 声明）。AGENT 级可同时设置绑定的 agentType。 */
    suspend fun setScopeOverride(id: String, scope: SkillScope?, agentType: String? = null) {
        runCatching {
            v2Agent.setSkillStateScopeOverride(id, scope?.name, if (scope == SkillScope.AGENT) agentType else null)
        }.onFailure { FileLogger.e(TAG, "更新技能作用域覆盖失败: $id", it) }
        refreshTrigger.value++
    }

    /** 对话级双向控制：设置技能在某对话内的生效状态（true=添加/启用，false=本对话临时禁用）。 */
    suspend fun setConversationEnabled(skillId: String, sessionId: String, enabled: Boolean) {
        runCatching {
            v2Agent.upsertSkillConversationState(skillId, sessionId, if (enabled) 1L else 0L)
        }.onFailure { FileLogger.e(TAG, "更新技能对话状态失败: $skillId / $sessionId", it) }
        refreshTrigger.value++
    }

    /** 移除技能在某对话的绑定记录（恢复跟随声明）。 */
    suspend fun removeConversationBinding(skillId: String, sessionId: String) {
        runCatching {
            v2Agent.deleteSkillConversationState(skillId, sessionId)
        }
            .onFailure { FileLogger.e(TAG, "移除技能对话绑定失败: $skillId / $sessionId", it) }
        refreshTrigger.value++
    }

    /** 某对话内全部技能关系（供对话技能面板展示）。 */
    suspend fun listConversationStates(sessionId: String): List<V2ConvState> =
        getConvStates(sessionId)

    /** 某对话内某技能的绑定状态（无绑定返回 null = 跟随声明）。 */
    suspend fun getConversationState(skillId: String, sessionId: String): V2ConvState? =
        v2Agent.getSkillConversationState(skillId, sessionId)

    /**
     * 作用域严格隐藏过滤：返回在「当前 agent + 当前会话」下可见的技能。
     */
    suspend fun filterVisibleSkills(
        skills: List<Skill>,
        sessionId: String?,
        agentType: String = DEFAULT_AGENT_TYPE
    ): List<Skill> {
        if (sessionId == null) return skills.filter { it.enabled }
        val convStates = getConvStates(sessionId).associateBy { it.skill_id }
        return filterByConvStates(skills, convStates, agentType)
    }

    /** 同步版作用域过滤（供非协程上下文）。 */
    fun filterVisibleSkillsSync(
        skills: List<Skill>,
        sessionId: String?,
        agentType: String = DEFAULT_AGENT_TYPE
    ): List<Skill> {
        if (sessionId == null) return skills.filter { it.enabled }
        val convStates = getConvStatesSync(sessionId).associateBy { it.skill_id }
        return filterByConvStates(skills, convStates, agentType)
    }

    private fun filterByConvStates(
        skills: List<Skill>,
        convStates: Map<String, V2ConvState>,
        agentType: String
    ): List<Skill> = skills.filter { skill ->
        if (!skill.enabled) return@filter false
        when (skill.scope) {
            SkillScope.AGENT -> skill.agentType == agentType
            SkillScope.CONVERSATION -> convStates[skill.id]?.isEnabled == true
            SkillScope.GLOBAL -> convStates[skill.id]?.isEnabled != false
        }
    }

    /**
     * 依赖解析：返回 [id] 技能及其依赖（依赖序，先依赖后自身）。
     */
    suspend fun resolveSkillWithDependencies(id: String): SkillResolution? {
        val all = listSkills()
        val byId = all.associateBy { it.id }
        val target = byId[id] ?: return null

        val ordered = mutableListOf<Skill>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val missing = mutableListOf<String>()
        val disabled = mutableListOf<String>()
        val cycleBroken = mutableListOf<String>()

        fun visit(skill: Skill) {
            if (skill.id in visited) return
            if (skill.id in visiting) {
                cycleBroken.add(skill.id)
                return
            }
            visiting.add(skill.id)
            for (depId in skill.dependencies) {
                val dep = byId[depId]
                if (dep == null) {
                    missing.add(depId)
                } else {
                    if (!dep.enabled) disabled.add(depId)
                    visit(dep)
                }
            }
            visiting.remove(skill.id)
            visited.add(skill.id)
            ordered.add(skill)
        }

        visit(target)

        for (c in cycleBroken) {
            byId[c]?.let { if (it.id !in visited) { visited.add(it.id); ordered.add(it) } }
        }

        return SkillResolution(
            skill = target,
            dependencies = ordered.filter { it.id != id },
            missingDependencies = missing.distinct(),
            disabledDependencies = disabled.distinct()
        )
    }
}
