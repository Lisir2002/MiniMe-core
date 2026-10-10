package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_jobs
import com.mini.mecore.datalayer.sqldelight.agent.Agent_schedules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 后台任务与定时调度存储：agent_jobs、agent_schedules 两表。
 */
internal class AgentJobScheduleStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── Jobs ───────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertJob(
        jobId: String, sessionId: String, kind: String, title: String, status: String,
        exitCode: Long?, outputLocator: String, createdAtMs: Long, finishedAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertJob(jobId, sessionId, kind, title, status, exitCode, outputLocator, createdAtMs, finishedAtMs, updatedAtMs)
    }

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertJob(
        jobId: String, sessionId: String, kind: String, title: String, status: String,
        exitCode: Long?, outputLocator: String, createdAtMs: Long, finishedAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertJob(jobId, sessionId, kind, title, status, exitCode, outputLocator, createdAtMs, finishedAtMs, updatedAtMs)
    }

    suspend fun getJobById(jobId: String): Agent_jobs? =
        withContext(Dispatchers.IO) { q.selectJobById(jobId).executeAsOneOrNull() }

    suspend fun listJobs(sessionId: String): List<Agent_jobs> =
        withContext(Dispatchers.IO) { q.selectJobsBySession(sessionId).executeAsList() }

    fun observeJobsBySession(sessionId: String): Flow<List<Agent_jobs>> =
        q.selectJobsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun listRunningJobs(): List<Agent_jobs> =
        withContext(Dispatchers.IO) { q.selectRunningJobs().executeAsList() }

    suspend fun updateJobStatus(
        jobId: String, status: String, exitCode: Long?, outputLocator: String,
        finishedAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.updateJobStatus(status, exitCode, outputLocator, finishedAtMs, updatedAtMs, jobId)
    }

    suspend fun updateJobResult(
        jobId: String, status: String, exitCode: Long?, finishedAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.updateJobResult(status, exitCode, finishedAtMs, updatedAtMs, jobId)
    }

    suspend fun deleteJob(jobId: String) = withContext(Dispatchers.IO) { q.deleteJob(jobId) }

    suspend fun deleteJobsBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteJobsBySession(sessionId) }

    // ── Schedules ──────────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertSchedule(
        scheduleId: String, sessionId: String, rule: String, args: String, status: String,
        enabled: Long, createdAtMs: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertSchedule(scheduleId, sessionId, rule, args, status, enabled, createdAtMs, lastFiredAtMs, updatedAtMs)
    }

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun upsertSchedule(
        scheduleId: String, sessionId: String, rule: String, args: String, status: String,
        enabled: Long, createdAtMs: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.upsertSchedule(scheduleId, sessionId, rule, args, status, enabled, createdAtMs, lastFiredAtMs, updatedAtMs)
    }

    suspend fun getScheduleById(scheduleId: String): Agent_schedules? =
        withContext(Dispatchers.IO) { q.selectScheduleById(scheduleId).executeAsOneOrNull() }

    suspend fun listSchedules(sessionId: String): List<Agent_schedules> =
        withContext(Dispatchers.IO) { q.selectSchedulesBySession(sessionId).executeAsList() }

    fun observeSchedulesBySession(sessionId: String): Flow<List<Agent_schedules>> =
        q.selectSchedulesBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun getPendingSchedules(): List<Agent_schedules> =
        withContext(Dispatchers.IO) { q.selectPendingSchedules().executeAsList() }

    suspend fun updateScheduleStatus(scheduleId: String, status: String, lastFiredAtMs: Long?, updatedAtMs: Long) =
        withContext(Dispatchers.IO) { q.updateScheduleStatus(status, lastFiredAtMs, updatedAtMs, scheduleId) }

    suspend fun updateScheduleState(
        scheduleId: String, status: String, enabled: Long, lastFiredAtMs: Long?, updatedAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.updateScheduleState(status, enabled, lastFiredAtMs, updatedAtMs, scheduleId)
    }

    suspend fun deleteSchedule(scheduleId: String) = withContext(Dispatchers.IO) { q.deleteSchedule(scheduleId) }

    suspend fun deleteSchedulesBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteSchedulesBySession(sessionId) }
}
