package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Skill_conversation_state
import com.mini.mecore.datalayer.sqldelight.agent.Skill_state
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 技能存储：skill_state（全局启用/版本/scope 覆盖）与 skill_conversation_state（会话级开关）。
 */
internal class AgentSkillStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    suspend fun upsertSkillState(
        id: String, enabled: Long, version: String, source: String, installedAtMs: Long,
        scopeOverride: String?, agentTypeOverride: String?,
    ) = withContext(Dispatchers.IO) {
        q.insertSkillState(id, enabled, version, source, installedAtMs, scopeOverride, agentTypeOverride)
    }

    suspend fun getSkillState(id: String): Skill_state? =
        withContext(Dispatchers.IO) { q.selectSkillStateById(id).executeAsOneOrNull() }

    suspend fun listSkillStates(): List<Skill_state> =
        withContext(Dispatchers.IO) { q.selectAllSkillStates().executeAsList() }

    fun observeAllSkillStates(): Flow<List<Skill_state>> =
        q.selectAllSkillStates().asFlow().mapToList(Dispatchers.IO)

    suspend fun upsertSkillConversationState(skillId: String, sessionId: String, enabled: Long) =
        withContext(Dispatchers.IO) { q.upsertSkillConversationState(skillId, sessionId, enabled) }

    suspend fun getSkillConversationState(skillId: String, sessionId: String): Skill_conversation_state? =
        withContext(Dispatchers.IO) { q.selectSkillConversationState(skillId, sessionId).executeAsOneOrNull() }

    suspend fun listSkillConversationStates(sessionId: String): List<Skill_conversation_state> =
        withContext(Dispatchers.IO) { q.selectSkillConversationStatesBySession(sessionId).executeAsList() }

    suspend fun listEnabledSkillIds(sessionId: String): List<String> =
        withContext(Dispatchers.IO) { q.selectSkillConversationStatesEnabledBySession(sessionId).executeAsList() }

    suspend fun listDisabledSkillIds(sessionId: String): List<String> =
        withContext(Dispatchers.IO) { q.selectSkillConversationStatesDisabledBySession(sessionId).executeAsList() }

    fun observeSkillConversationStatesBySession(sessionId: String): Flow<List<Skill_conversation_state>> =
        q.observeSkillConversationStatesBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun setSkillStateEnabled(id: String, enabled: Long) {
        withContext(Dispatchers.IO) { q.updateSkillStateEnabled(enabled, id) }
    }

    suspend fun setSkillStateScopeOverride(id: String, scope: String?, agentType: String?) {
        withContext(Dispatchers.IO) { q.updateSkillStateScopeOverride(scope, agentType, id) }
    }

    suspend fun listScopedSkillStates(): List<Skill_state> =
        withContext(Dispatchers.IO) { q.selectScopedSkillStates().executeAsList() }
            .map { row ->
                Skill_state(
                    id = row.id,
                    enabled = row.enabled,
                    version = row.version,
                    source = row.source,
                    installed_at_ms = row.installed_at_ms,
                    scope_override = row.scope_override,
                    agent_type_override = row.agent_type_override,
                )
            }

    suspend fun deleteSkillStateById(id: String) {
        withContext(Dispatchers.IO) { q.deleteSkillStateById(id) }
    }

    suspend fun deleteSkillConversationState(skillId: String, sessionId: String) {
        withContext(Dispatchers.IO) { q.deleteSkillConversationState(skillId, sessionId) }
    }

    suspend fun deleteSkillConversationStatesBySkill(skillId: String) {
        withContext(Dispatchers.IO) { q.deleteSkillConversationStatesBySkill(skillId) }
    }

    suspend fun deleteSkillConversationStatesBySession(sessionId: String) {
        withContext(Dispatchers.IO) { q.deleteSkillConversationStatesBySession(sessionId) }
    }
}
