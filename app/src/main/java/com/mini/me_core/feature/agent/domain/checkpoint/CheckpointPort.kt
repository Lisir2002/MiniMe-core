package com.mini.me_core.feature.agent.domain.checkpoint

import com.mini.me_core.feature.agent.data.local.entity.CheckpointEntity
import com.mini.me_core.feature.agent.data.local.entity.CheckpointFileSnapshotEntity

interface CheckpointPort {
    suspend fun insertCheckpointFull(id: String, sessionId: String, userMessageId: String, promptSnippet: String, createdAtMs: Long)
    suspend fun countSnapshot(checkpointId: String, filePath: String): Long
    suspend fun insertSnapshot(entity: CheckpointFileSnapshotEntity)
    suspend fun listCheckpoints(sessionId: String): List<CheckpointEntity>
    suspend fun listSnapshots(checkpointId: String): List<CheckpointFileSnapshotEntity>
    suspend fun deleteSnapshotsBySession(sessionId: String)
    suspend fun deleteCheckpointsBySession(sessionId: String)
}
