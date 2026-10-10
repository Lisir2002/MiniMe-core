package com.mini.me_core.datalayer.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mini.mecore.datalayer.sqldelight.AgentDb
import com.mini.mecore.datalayer.sqldelight.agent.Agent_checkpoint
import com.mini.mecore.datalayer.sqldelight.agent.Checkpoint_file_snapshots
import com.mini.mecore.datalayer.sqldelight.agent.File_edit_hunks
import com.mini.mecore.datalayer.sqldelight.agent.Mode_switch_history
import com.mini.mecore.datalayer.sqldelight.agent.Session_checkpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 会话快照/审计存储：checkpoint（V1 列表 + V2 session_checkpoints）、checkpoint 文件快照、
 * 文件编辑 hunk、模式切换历史。这些表均围绕「会话状态快照与回放」成组。
 */
internal class AgentCheckpointStore(private val db: AgentDb) {

    private val q get() = db.agentQueries

    // ── V1 checkpoint 列表（Agent_checkpoint）──────────────────────────

    suspend fun listCheckpoints(sessionId: String): List<Agent_checkpoint> =
        withContext(Dispatchers.IO) { q.selectCheckpointsBySession(sessionId).executeAsList() }

    fun observeCheckpointsBySession(sessionId: String): Flow<List<Agent_checkpoint>> =
        q.selectCheckpointsBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    // ── V2 session_checkpoints ──────────────────────────────────────────

    suspend fun insertCheckpointFull(
        id: String, sessionId: String, userMessageId: String, promptSnippet: String, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertCheckpointFull(id, sessionId, userMessageId, promptSnippet, createdAtMs)
    }

    suspend fun getCheckpointById(checkpointId: String): Session_checkpoints? =
        withContext(Dispatchers.IO) { q.selectCheckpointById(checkpointId).executeAsOneOrNull() }

    suspend fun getCheckpointByMessageId(messageId: String): Session_checkpoints? =
        withContext(Dispatchers.IO) { q.selectCheckpointByMessageId(messageId).executeAsOneOrNull() }

    suspend fun listCheckpointsForSession(sessionId: String): List<Session_checkpoints> =
        withContext(Dispatchers.IO) { q.selectSessionCheckpoints(sessionId).executeAsList() }

    suspend fun deleteCheckpointsBySession(sessionId: String) {
        withContext(Dispatchers.IO) { q.deleteCheckpointsBySession(sessionId) }
    }

    suspend fun deleteCheckpointsBefore(cutoffTimestamp: Long) {
        withContext(Dispatchers.IO) { q.deleteCheckpointsBefore(cutoffTimestamp) }
    }

    // ── checkpoint 文件快照 ────────────────────────────────────────────

    suspend fun insertCheckpointFileSnapshot(
        id: String, checkpointId: String, filePath: String, snapshotRelativePath: String,
        changeType: String, createdAt: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertCheckpointFileSnapshot(id, checkpointId, filePath, snapshotRelativePath, changeType, createdAt)
    }

    suspend fun listCheckpointFileSnapshots(checkpointId: String): List<Checkpoint_file_snapshots> =
        withContext(Dispatchers.IO) { q.selectCheckpointFileSnapshots(checkpointId).executeAsList() }

    suspend fun countCheckpointFileSnapshot(checkpointId: String, filePath: String): Long =
        withContext(Dispatchers.IO) { q.countCheckpointFileSnapshot(checkpointId, filePath).executeAsOne() }

    suspend fun deleteCheckpointFileSnapshotsBySession(sessionId: String) {
        withContext(Dispatchers.IO) { q.deleteCheckpointFileSnapshotsBySession(sessionId) }
    }

    // ── 文件编辑 hunk ───────────────────────────────────────────────────

    @Suppress("LongParameterList") // 签名对应表结构，行为保持
    suspend fun insertFileEditHunk(
        id: String, sessionId: String, filePath: String, operation: String, hunk: String,
        oldContent: String, newContent: String, createdAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        q.insertFileEditHunk(id, sessionId, filePath, operation, hunk, oldContent, newContent, createdAtMs)
    }

    suspend fun listFileEditHunks(sessionId: String): List<File_edit_hunks> =
        withContext(Dispatchers.IO) { q.selectFileEditHunksBySession(sessionId).executeAsList() }

    fun observeFileEditHunksBySession(sessionId: String): Flow<List<File_edit_hunks>> =
        q.selectFileEditHunksBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun deleteFileEditHunksBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteFileEditHunksBySession(sessionId) }

    // ── 模式切换历史 ────────────────────────────────────────────────────

    suspend fun insertModeSwitch(
        sessionId: String, fromMode: String, toMode: String, reason: String, timestampMs: Long,
    ) = withContext(Dispatchers.IO) { q.insertModeSwitch(sessionId, fromMode, toMode, reason, timestampMs) }

    suspend fun listModeSwitches(sessionId: String): List<Mode_switch_history> =
        withContext(Dispatchers.IO) { q.selectModeSwitchesBySession(sessionId).executeAsList() }

    fun observeModeSwitchesBySession(sessionId: String): Flow<List<Mode_switch_history>> =
        q.selectModeSwitchesBySession(sessionId).asFlow().mapToList(Dispatchers.IO)

    suspend fun deleteModeSwitchesBySession(sessionId: String) =
        withContext(Dispatchers.IO) { q.deleteModeSwitchesBySession(sessionId) }
}
