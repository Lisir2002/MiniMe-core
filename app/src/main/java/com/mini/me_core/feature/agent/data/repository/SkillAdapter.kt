package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state
import com.mini.mecore.datalayer.sqldelight.agent.Skill_state
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.SkillConversationStateEntity
import com.mini.me_core.feature.agent.data.local.entity.SkillStateEntity
import com.mini.me_core.feature.agent.domain.skill.SkillPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SkillAdapter @Inject constructor(private val v2: AgentRepository) : SkillPort {
    override suspend fun list(): List<SkillStateEntity> = v2.listSkillStates().map { it.toEntity() }
    override fun observeAll(): Flow<List<SkillStateEntity>> = v2.observeAllSkillStates().map { list -> list.map { it.toEntity() } }
    override suspend fun listConversation(sessionId: String): List<SkillConversationStateEntity> =
        v2.listSkillConversationStates(sessionId).map { it.toEntity() }
    override suspend fun get(id: String): SkillStateEntity? = v2.getSkillState(id)?.toEntity()
    override suspend fun upsert(entity: SkillStateEntity) {
        v2.upsertSkillState(entity.id, if (entity.enabled) 1L else 0L, entity.version, entity.source,
            entity.installedAtMs, entity.scopeOverride, entity.agentTypeOverride)
    }
    override suspend fun setEnabled(id: String, enabled: Boolean) { v2.setSkillStateEnabled(id, if (enabled) 1L else 0L) }
    override suspend fun delete(id: String) {
        v2.deleteSkillStateById(id); v2.deleteSkillConversationStatesBySkill(id)
    }
    override suspend fun setScopeOverride(id: String, scope: String?, agentType: String?) {
        v2.setSkillStateScopeOverride(id, scope, agentType)
    }
    override suspend fun upsertConversation(skillId: String, sessionId: String, enabled: Boolean) {
        v2.upsertSkillConversationState(skillId, sessionId, if (enabled) 1L else 0L)
    }
    override suspend fun deleteConversation(skillId: String, sessionId: String) {
        v2.deleteSkillConversationState(skillId, sessionId)
    }
    override suspend fun getConversation(skillId: String, sessionId: String): SkillConversationStateEntity? =
        v2.getSkillConversationState(skillId, sessionId)?.toEntity()
    private fun Skill_state.toEntity() = SkillStateEntity(id, enabled == 1L, version, source, installed_at_ms, scope_override, agent_type_override)
    private fun Skill_conversation_state.toEntity() = SkillConversationStateEntity(skill_id, session_id, enabled == 1L)
}
