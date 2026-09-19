package com.mini.me_core.feature.agent.domain.job

import com.mini.me_core.feature.agent.data.local.entity.JobEntity

interface JobPort {
    suspend fun getById(id: String): JobEntity?
    suspend fun upsert(entity: JobEntity)
    suspend fun list(sessionId: String): List<JobEntity>
    suspend fun listRunning(): List<JobEntity>
    suspend fun updateResult(id: String, status: String, exitCode: Long?, finishedAtMs: Long, updatedAtMs: Long)
}
