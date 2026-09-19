package com.mini.me_core.feature.agent.domain.skill

import com.mini.me_core.feature.agent.data.local.entity.SkillConversationStateEntity
import com.mini.me_core.feature.agent.data.local.entity.SkillStateEntity
import kotlinx.coroutines.flow.Flow

interface SkillPort {
    suspend fun list(): List<SkillStateEntity>
    fun observeAll(): Flow<List<SkillStateEntity>>
    suspend fun listConversation(sessionId: String): List<SkillConversationStateEntity>
    suspend fun get(id: String): SkillStateEntity?
    suspend fun upsert(entity: SkillStateEntity)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun delete(id: String)
    suspend fun setScopeOverride(id: String, scope: String?, agentType: String?)
    suspend fun upsertConversation(skillId: String, sessionId: String, enabled: Boolean)
    suspend fun deleteConversation(skillId: String, sessionId: String)
    suspend fun getConversation(skillId: String, sessionId: String): SkillConversationStateEntity?
}
