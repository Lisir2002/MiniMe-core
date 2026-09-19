package com.mini.me_core.feature.agent.domain.playbook

import com.mini.me_core.feature.agent.data.local.entity.PlaybookRunEntity

interface PlaybookPort {
    suspend fun latestBySession(sessionId: String): PlaybookRunEntity?
    suspend fun latestBySessionAndStatus(sessionId: String, status: String): PlaybookRunEntity?
    suspend fun getById(id: String): PlaybookRunEntity?
    suspend fun upsert(entity: PlaybookRunEntity)
}
