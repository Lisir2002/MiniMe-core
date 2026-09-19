package com.mini.me_core.feature.agent.domain.session

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.feature.agent.data.local.entity.ChatSessionEntity
import com.mini.me_core.feature.agent.presentation.MessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionUseCase @Inject constructor(
    private val sessionPort: AgentSessionPort,
    private val messagePort: AgentMessagePort,
) {
    companion object {
        private const val TAG = "SessionUseCase"
        const val TITLE_MAX = 20
        /** 工具占位行前缀：标记「执行中、结果未回」的孤儿，UI 与回放据此识别。 */
        const val PENDING_TOOL_MARKER = "[running]"
        /** 历史版本 emoji 前缀；冷启动收尾与回放仍需识别。 */
        const val LEGACY_PENDING_TOOL_MARKER = "\u23F3"
        /** 历史版本停止 emoji 前缀；UI 剥离结果文本时兼容。 */
        const val LEGACY_STOPPED_TOOL_MARKER = "\u23F9"
        const val INTERRUPTED_TOOL_TEXT = "执行被中断（应用已关闭）"
    }

    /** 冷启动收尾：上次进程被杀时若有工具正在执行，其占位行会永久显示「执行中」。 */
    suspend fun initColdStartCleanup() {
        runCatching {
            val n = listOf(PENDING_TOOL_MARKER, LEGACY_PENDING_TOOL_MARKER).sumOf { marker ->
                sessionPort.markPendingToolsInterrupted(MessageRole.TOOL.name, "$marker%", INTERRUPTED_TOOL_TEXT)
            }
            if (n > 0) FileLogger.i(TAG, "冷启动收尾 $n 条残留「执行中」工具行为已中断")
        }.onFailure { FileLogger.e(TAG, "回收残留执行中工具行失败", it) }
    }

    fun newSessionEntity(workspacePath: String, workspaceId: String): ChatSessionEntity {
        val now = System.currentTimeMillis()
        return ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = "新会话",
            workspacePath = workspacePath,
            workspaceId = workspaceId,
            createdAtMs = now,
            updatedAtMs = now
        )
    }

    fun deriveTitle(request: String): String {
        val clean = request.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= TITLE_MAX) clean.ifBlank { "新对话" }
        else clean.take(TITLE_MAX) + "…"
    }

    /**
     * 删除会话，返回需要清理的状态 id 集合，由 ViewModel 执行状态清理。
     *
     * 级联清理覆盖 10 张关联表（设计文档 chat-session-list-refactor-design C2 + D2-3 轨迹表）：
     * todo / hunk / 模式切换历史 / 技能会话态 / 唤醒队列(该会话行) / 任务编排 4 表 / 运行轨迹表。
     * 审计类（zth_*、hallucination_fuses）与全局项（wake_queue 空 session）明确保留。
     *
     * V2 分支：SQLDelight [V2Tx] 事务封装（单 driver 顺序执行，语义对齐 Room 事务）。
     */
    suspend fun deleteSession(id: String): String {
        sessionPort.deleteSessionCascade(id)
        return id
    }

    suspend fun getFirstSessionOfWorkspace(workspaceId: String): ChatSessionEntity? {
        return sessionPort.listByWorkspace(workspaceId).firstOrNull()
    }

    /** 最近一条「未绑定工作台」的会话（按更新时间降序，工作台绑定在首条消息时自动发生，此前会话处于未绑定态）。 */
    suspend fun getFirstUnboundSession(): ChatSessionEntity? {
        return sessionPort.listUnbound().firstOrNull()
    }

    /** 全局最近一条会话（任意工作台，删当前会话后重选兜底）。 */
    suspend fun getMostRecentSession(): ChatSessionEntity? {
        return sessionPort.getMostRecent()
    }

    /** 绑定/解绑会话工作台。绑定即一次性的（会话中途不可切换工作台）：仅未绑定会话可绑定，已绑定则忽略。 */
    suspend fun bindWorkspace(sessionId: String, workspaceId: String, workspacePath: String) {
        if (workspaceId.isBlank()) return
        val current = sessionPort.getWorkspaceId(sessionId)
        if (current.isNotBlank()) return
        sessionPort.setWorkspaceBinding(sessionId, workspaceId, workspacePath)
    }

    suspend fun upsertSession(entity: ChatSessionEntity) {
        sessionPort.upsert(entity)
    }

    /** 重命名会话标题。仅更新 title，不改 updatedAt，列表顺序保持不变。长度兜底截断对齐 TITLE_MAX。 */
    suspend fun updateTitle(sessionId: String, title: String) {
        sessionPort.updateTitle(sessionId, title.trim().take(TITLE_MAX))
    }

    suspend fun touch(sessionId: String, timestamp: Long) {
        sessionPort.touch(sessionId, timestamp)
    }

    suspend fun getSessionById(id: String): ChatSessionEntity? {
        return sessionPort.getSessionById(id)
    }

    suspend fun updateMode(sessionId: String, mode: String) {
        val s = getSessionById(sessionId) ?: return
        sessionPort.upsert(s.copy(mode = mode))
    }

    suspend fun updateProviderModel(sessionId: String, providerId: String?, model: String?) {
        sessionPort.updateProviderModel(sessionId, providerId, model)
    }

    suspend fun updateReasoningEffort(sessionId: String, effort: String) {
        sessionPort.updateReasoningEffort(sessionId, effort)
    }

    suspend fun isSessionEmpty(sessionId: String): Boolean {
        return messagePort.listBySession(sessionId).isEmpty()
    }
}
