package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.me_core.feature.agent.presentation.MessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionUseCase @Inject constructor(
    private val v2Agent: V2AgentRepository,
) {
    companion object {
        private const val TAG = "SessionUseCase"
        const val TITLE_MAX = 20
        const val PENDING_TOOL_MARKER = "[running]"
        const val LEGACY_PENDING_TOOL_MARKER = "⏳"
        const val LEGACY_STOPPED_TOOL_MARKER = "⏹"
        const val INTERRUPTED_TOOL_TEXT = "执行被中断（应用已关闭）"
    }

    /** 冷启动收尾：上次进程被杀时若有工具正在执行，其占位行会永久显示「执行中」。 */
    suspend fun initColdStartCleanup() {
        runCatching {
            val n = listOf(PENDING_TOOL_MARKER, LEGACY_PENDING_TOOL_MARKER).sumOf { marker ->
                v2Agent.markPendingToolsInterrupted(
                    toolRole = MessageRole.TOOL.name,
                    pendingPrefix = "$marker%",
                    interruptedContent = INTERRUPTED_TOOL_TEXT
                )
            }
            if (n > 0) FileLogger.i(TAG, "冷启动收尾 $n 条残留「执行中」工具行已中断")
        }.onFailure { FileLogger.e(TAG, "回收残留执行中工具行失败", it) }
    }

    fun newSession(workspacePath: String): V2AgentSession {
        val now = System.currentTimeMillis()
        return V2AgentSession(
            id = UUID.randomUUID().toString(),
            title = "新会话",
            mode = "BUILD",
            model = null,
            status = "active",
            created_at = now,
            updated_at = now,
            workspace_path = workspacePath,
            reasoning_effort = "MEDIUM",
            provider_id = null,
            total_input_tokens = 0L,
            total_output_tokens = 0L,
            last_input_tokens = 0L
        )
    }

    fun deriveTitle(request: String): String {
        val clean = request.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= TITLE_MAX) clean.ifBlank { "新对话" }
        else clean.take(TITLE_MAX) + "…"
    }

    /**
     * 删除会话，返回需要清理的状态 id 集合，由 ViewModel 执行状态清理。
     */
    suspend fun deleteSession(sessionId: String) {
        v2Agent.deleteSession(sessionId)
    }

    suspend fun deleteSessionsByWorkspace(workspacePath: String): Int {
        val sessions = v2Agent.getAllSessionsByWorkspaceOnce(workspacePath)
        sessions.forEach { v2Agent.deleteSession(it.id) }
        FileLogger.i(TAG, "工作区级联删除 ${sessions.size} 个会话（workspacePath=$workspacePath）")
        return sessions.size
    }

    suspend fun getFirstSessionOfWorkspace(workspacePath: String): V2AgentSession? {
        return v2Agent.getAllSessionsByWorkspaceOnce(workspacePath).firstOrNull()
    }

    suspend fun getFirstUnboundSession(): V2AgentSession? {
        return v2Agent.getUnboundSessionsOnce().firstOrNull()
    }

    suspend fun getMostRecentSession(): V2AgentSession? {
        return v2Agent.getMostRecentOnce()
    }

    suspend fun bindWorkspace(sessionId: String, workspacePath: String) {
        if (workspacePath.isBlank()) return
        val current = v2Agent.getSessionById(sessionId)?.workspace_path ?: ""
        if (current.isNotBlank()) return
        v2Agent.setWorkspacePath(sessionId, workspacePath)
    }

    suspend fun upsertSession(session: V2AgentSession) {
        v2Agent.upsertSession(
            id = session.id, title = session.title, mode = session.mode, model = session.model, status = session.status,
            createdAtMs = session.created_at, updatedAtMs = session.updated_at,
            workspacePath = session.workspace_path, reasoningEffort = session.reasoning_effort,
            providerId = session.provider_id, totalInputTokens = session.total_input_tokens,
            totalOutputTokens = session.total_output_tokens, lastInputTokens = session.last_input_tokens,
        )
    }

    suspend fun updateTitle(sessionId: String, title: String) {
        v2Agent.updateTitle(sessionId, title.trim().take(TITLE_MAX))
    }

    suspend fun touch(sessionId: String, timestamp: Long) {
        v2Agent.touch(sessionId, timestamp)
    }

    suspend fun getSessionById(id: String): V2AgentSession? {
        return v2Agent.getSessionById(id)
    }

    suspend fun updateMode(sessionId: String, mode: String) {
        val s = getSessionById(sessionId) ?: return
        v2Agent.upsertSession(
            id = s.id, title = s.title, mode = mode, model = s.model, status = s.status,
            createdAtMs = s.created_at, updatedAtMs = s.updated_at,
            workspacePath = s.workspace_path, reasoningEffort = s.reasoning_effort,
            providerId = s.provider_id, totalInputTokens = s.total_input_tokens,
            totalOutputTokens = s.total_output_tokens, lastInputTokens = s.last_input_tokens,
        )
    }

    suspend fun updateProviderModel(sessionId: String, providerId: String?, model: String?) {
        v2Agent.updateProviderModel(sessionId, providerId, model)
    }

    suspend fun updateReasoningEffort(sessionId: String, effort: String) {
        v2Agent.updateReasoningEffort(sessionId, effort)
    }

    suspend fun isSessionEmpty(sessionId: String): Boolean {
        return v2Agent.getMessagesBySessionOnce(sessionId).isEmpty()
    }
}
