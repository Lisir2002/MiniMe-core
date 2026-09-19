package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Checkpoint_file_snapshots
import com.mini.mecore.datalayer.sqldelight.agent.Session_checkpoints
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.CheckpointEntity
import com.mini.me_core.feature.agent.data.local.entity.CheckpointFileSnapshotEntity
import com.mini.me_core.feature.agent.domain.checkpoint.CheckpointPort
import javax.inject.Inject

class CheckpointAdapter @Inject constructor(private val v2: AgentRepository) : CheckpointPort {
    override suspend fun insertCheckpointFull(id: String, sessionId: String, userMessageId: String, promptSnippet: String, createdAtMs: Long) {
        v2.insertCheckpointFull(id, sessionId, userMessageId, promptSnippet, createdAtMs)
    }
    override suspend fun countSnapshot(checkpointId: String, filePath: String): Long =
        v2.countCheckpointFileSnapshot(checkpointId, filePath)
    override suspend fun insertSnapshot(entity: CheckpointFileSnapshotEntity) {
        v2.insertCheckpointFileSnapshot(entity.id, entity.checkpointId, entity.filePath,
            entity.snapshotRelativePath, entity.changeType, entity.createdAt)
    }
    override suspend fun listCheckpoints(sessionId: String): List<CheckpointEntity> =
        v2.listCheckpointsForSession(sessionId).map { it.toEntity() }
    override suspend fun listSnapshots(checkpointId: String): List<CheckpointFileSnapshotEntity> =
        v2.listCheckpointFileSnapshots(checkpointId).map { it.toEntity() }
    override suspend fun deleteSnapshotsBySession(sessionId: String) { v2.deleteCheckpointFileSnapshotsBySession(sessionId) }
    override suspend fun deleteCheckpointsBySession(sessionId: String) { v2.deleteCheckpointsBySession(sessionId) }
    private fun Session_checkpoints.toEntity() = CheckpointEntity(
        id, session_id, user_message_id, prompt_snippet, created_at_ms
    )
    private fun Checkpoint_file_snapshots.toEntity() = CheckpointFileSnapshotEntity(
        id, checkpoint_id, file_path, snapshot_relative_path, change_type, created_at
    )
}
