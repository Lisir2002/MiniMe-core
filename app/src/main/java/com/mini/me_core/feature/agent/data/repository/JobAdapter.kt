package com.mini.me_core.feature.agent.data.repository

import com.mini.mecore.datalayer.sqldelight.agent.Agent_jobs
import com.mini.me_core.datalayer.repository.AgentRepository
import com.mini.me_core.feature.agent.data.local.entity.JobEntity
import com.mini.me_core.feature.agent.domain.job.JobPort
import javax.inject.Inject

class JobAdapter @Inject constructor(private val v2: AgentRepository) : JobPort {
    override suspend fun getById(id: String): JobEntity? = v2.getJobById(id)?.toEntity()
    override suspend fun upsert(entity: JobEntity) {
        v2.upsertJob(entity.jobId, entity.sessionId, entity.kind, entity.title, entity.status,
            entity.exitCode?.toLong(), entity.outputLocator, entity.createdAtMs, entity.finishedAtMs, entity.updatedAtMs)
    }
    override suspend fun list(sessionId: String): List<JobEntity> = v2.listJobs(sessionId).map { it.toEntity() }
    override suspend fun listRunning(): List<JobEntity> = v2.listRunningJobs().map { it.toEntity() }
    override suspend fun updateResult(id: String, status: String, exitCode: Long?, finishedAtMs: Long, updatedAtMs: Long) {
        v2.updateJobResult(id, status, exitCode, finishedAtMs, updatedAtMs)
    }
    private fun Agent_jobs.toEntity() = JobEntity(
        job_id, session_id, kind, title, status, exit_code?.toInt(), output_locator, created_at_ms, finished_at_ms, updated_at_ms
    )
}
