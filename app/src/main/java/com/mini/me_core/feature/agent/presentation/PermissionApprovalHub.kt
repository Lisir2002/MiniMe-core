package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.feature.agent.domain.execution.permission.PermissionChoice
import com.mini.me_core.feature.agent.domain.execution.tool.PendingToolPermission
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.tool.question.AskUserQuestionManager
import com.mini.me_core.feature.agent.domain.execution.tool.question.PendingUserQuestion
import com.mini.me_core.feature.agent.domain.execution.tool.question.UserQuestionAnswer
import com.mini.me_core.feature.agent.domain.execution.tool.mode.PlanApprovalChoice
import com.mini.me_core.feature.agent.domain.execution.tool.mode.PlanApprovalManager
import com.mini.me_core.feature.agent.domain.execution.tool.mode.PlanApprovalRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 权限/审批中继枢纽（ 从 AIAgentViewModel 提取，行为保持搬迁）。
 *
 * 仅做「请求流编排 + 审批回调转发」：
 * - 把引擎层 [ToolPermissionManager] / [AskUserQuestionManager] / [PlanApprovalManager]
 *   暴露的待处理请求，按「当前会话」过滤后中继给 UI；
 * - 把 UI 的 resolve/approve/refine 回调原样转发给对应 manager。
 *
 * ⚠️ 权限 ASK/ALLOW/DENY、审批超时/拒绝/取消的判定仍在引擎层 manager 内部，本类一字不改，
 *    只搬运请求-回调编排。作用域复用 ViewModel 传入的同一 [scope]（即 viewModelScope），
 *    不新建独立 scope，生命周期不超过 ViewModel。
 */
internal class PermissionApprovalHub(
    private val scope: CoroutineScope,
    currentSessionId: StateFlow<String?>,
    private val toolPermissionManager: ToolPermissionManager,
    private val askUserQuestionManager: AskUserQuestionManager,
    private val planApprovalManager: PlanApprovalManager,
) {

    // 只暴露「当前会话」的权限确认请求：避免对话已结束/切换会话后，旧会话残留的确认卡仍弹出。
    val pendingToolPermission: StateFlow<PendingToolPermission?> = toolPermissionManager.pendingRequest
        .combine(currentSessionId) { pending, currentSid ->
            if (pending?.sessionId == null || pending.sessionId == currentSid) pending else null
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // 同上：ask_user_question 提问卡同样只暴露当前会话，防止旧会话残留提问卡弹出。
    val pendingUserQuestion: StateFlow<PendingUserQuestion?> = askUserQuestionManager.pendingQuestion
        .combine(currentSessionId) { pending, currentSid ->
            if (pending?.sessionId == null || pending.sessionId == currentSid) pending else null
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // 同上：计划审批卡同样只暴露当前会话。
    val pendingPlanApproval: StateFlow<PlanApprovalRequest?> = planApprovalManager.pendingApproval
        .combine(currentSessionId) { pending, currentSid ->
            if (pending?.sessionId == null || pending.sessionId == currentSid) pending else null
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun resolveToolPermission(id: String, choice: PermissionChoice) {
        toolPermissionManager.resolve(id, choice)
    }

    fun resolveUserQuestion(id: String, answer: UserQuestionAnswer) {
        askUserQuestionManager.resolve(id, answer)
    }

    /** 用户批准计划，唤醒 workflow 继续在 BUILD 模式执行。 */
    fun approvePlanAndBuild() {
        planApprovalManager.resolve(PlanApprovalChoice.APPROVE)
    }

    /** 用户选择继续反馈，唤醒 workflow 回滚到 PLAN 模式。 */
    fun refinePlan() {
        planApprovalManager.resolve(PlanApprovalChoice.REFINE)
    }

    /** 清理残留的权限确认请求（sessionId 为 null 时表示任意会话）。仅转发，判定不变。 */
    fun cancelPending(sessionId: String?) {
        toolPermissionManager.cancelPending(sessionId)
    }

    /**
     * 清理残留的提问与计划审批请求（sessionId 为 null 时表示任意会话）。
     * 与 [cancelPending] 同批调用：权限/提问/审批三类待决卡在会话停止/切换时一并清理，
     * 避免任一类型残留弹出。仅转发，判定不变。
     */
    fun cancelPendingInteractions(sessionId: String?) {
        askUserQuestionManager.cancelPending(sessionId)
        planApprovalManager.cancelPending(sessionId)
    }
}
