package com.mini.me_core.feature.agent.domain.execution.tool.mode

import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 管理计划审查流程的挂起/恢复：
 * 当 AI 从 PLAN 模式切回 BUILD 模式时，workflow 调用 [awaitApproval] 挂起，
 * 等 UI 上的计划审查面板得到用户决策后恢复。
 */
@Singleton
class PlanApprovalManager @Inject constructor(
    private val v2Agent: V2AgentRepository,
) {
    private val _pendingApproval = MutableStateFlow<PlanApprovalRequest?>(null)
    val pendingApproval: StateFlow<PlanApprovalRequest?> = _pendingApproval.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentDecision: CompletableDeferred<PlanApprovalChoice>? = null
    private var currentSessionId: String? = null

    private suspend fun getSession(sid: String): V2AgentSession? =
        v2Agent.getSessionById(sid)

    private suspend fun upsertMode(sid: String, session: V2AgentSession, mode: String) {
        v2Agent.upsertSession(
            id = session.id, title = session.title, mode = mode, model = session.model, status = session.status,
            createdAtMs = session.created_at, updatedAtMs = session.updated_at,
            workspacePath = session.workspace_path, reasoningEffort = session.reasoning_effort,
            providerId = session.provider_id, totalInputTokens = session.total_input_tokens,
            totalOutputTokens = session.total_output_tokens, lastInputTokens = session.last_input_tokens,
        )
    }

    /** 挂起等待用户在计划审查面板中的决策。 */
    suspend fun awaitApproval(reason: String, sessionId: String? = null): PlanApprovalChoice {
        val decision = CompletableDeferred<PlanApprovalChoice>()
        currentDecision = decision
        currentSessionId = sessionId
        _pendingApproval.value = PlanApprovalRequest(reason = reason, sessionId = sessionId)

        try {
            return decision.await()
        } finally {
            currentDecision = null
            currentSessionId = null
            _pendingApproval.value = null
        }
    }

    /** UI 回传用户选择，唤醒挂起的 [awaitApproval]。 */
    fun resolve(choice: PlanApprovalChoice) {
        _pendingApproval.value = null

        // 用户拒绝时，回滚数据库中的模式到 PLAN（SwitchModeTool 已写入 BUILD）
        if (choice == PlanApprovalChoice.REFINE) {
            val sid = currentSessionId
            if (sid != null) {
                scope.launch { rollbackModeToPlan(sid) }
            }
        }

        currentDecision?.complete(choice)
    }

    /**
     * 主动结束待决计划审批（对话停止 / 结束 / 切换会话时调用），防止审批卡残留。
     *
     * 语义与 [com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager.cancelPending] 一致：
     * @param sessionId 要清理的会话 id；传 null 表示清理「任意会话」的待决审批
     *   （例如 stopAllAgents 全停）。没有匹配的待决审批时为空操作。
     * @param choice 以何种选择结束挂起的等待：默认 REFINE（留在 PLAN 模式，
     *   与「用户拒绝计划」的落地语义一致，同时把数据库模式回滚到 PLAN）。
     */
    @Synchronized
    fun cancelPending(sessionId: String?, choice: PlanApprovalChoice = PlanApprovalChoice.REFINE) {
        val decision = currentDecision ?: return
        if (sessionId != null && currentSessionId != null && currentSessionId != sessionId) return
        val sid = currentSessionId
        _pendingApproval.value = null
        currentDecision = null
        currentSessionId = null
        if (choice == PlanApprovalChoice.REFINE && sid != null) {
            scope.launch { rollbackModeToPlan(sid) }
        }
        decision.complete(choice)
    }

    /** 把会话模式回滚到 PLAN（SwitchModeTool 已写入 BUILD，需纠正）。 */
    private suspend fun rollbackModeToPlan(sid: String) {
        val session = getSession(sid)
        if (session != null && session.mode != "PLAN") {
            upsertMode(sid, session, "PLAN")
        }
    }
}

data class PlanApprovalRequest(
    val reason: String,
    /** 发起审批的 AI 会话 id。UI 只展示当前会话的待决审批，切换/停止会话时按会话清理。 */
    val sessionId: String? = null
)

enum class PlanApprovalChoice {
    /** 批准计划，切换到 BUILD 模式执行 */
    APPROVE,
    /** 拒绝，留在 PLAN 模式继续反馈 */
    REFINE
}
