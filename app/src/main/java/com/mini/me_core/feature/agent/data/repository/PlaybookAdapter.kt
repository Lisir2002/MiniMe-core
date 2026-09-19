package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_playbook_runs
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.PlaybookRunEntity
import com.mini.me_core.feature.agent.domain.playbook.PlaybookPort
import javax.inject.Inject

class PlaybookAdapter @Inject constructor(private val v2: AgentRepository) : PlaybookPort {
    override suspend fun latestBySession(sessionId: String): PlaybookRunEntity? = v2.getLatestPlaybookBySession(sessionId)?.toEntity()
    override suspend fun latestBySessionAndStatus(sessionId: String, status: String): PlaybookRunEntity? =
        v2.getLatestPlaybookBySessionAndStatus(sessionId, status)?.toEntity()
    override suspend fun getById(id: String): PlaybookRunEntity? = v2.getPlaybookRunById(id)?.toEntity()
    override suspend fun upsert(entity: PlaybookRunEntity) {
        v2.upsertPlaybookRun(entity.playbookRunId, entity.sessionId, entity.playbookName,
            entity.currentStageIndex.toLong(), entity.stageStatuses, entity.status, entity.createdAtMs, entity.updatedAtMs)
    }
    private fun Agent_playbook_runs.toEntity() = PlaybookRunEntity(
        playbook_run_id, session_id, playbook_name, current_stage_index.toInt(), stage_statuses, status, created_at_ms, updated_at_ms
    )
}
