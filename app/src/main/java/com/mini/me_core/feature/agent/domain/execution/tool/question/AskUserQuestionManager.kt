package com.mini.me_core.feature.agent.domain.execution.tool.question

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 ask_user_question 工具的 park-and-resume 流程。
 *
 * 镜像 [com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager] 的设计：
 * 工具侧调用 [awaitAnswer] 挂起；UI 侧观察 [pendingQuestion] 渲染面板；
 * 用户选择后调用 [resolve] 唤醒工具侧。
 *
 * 同一时刻只允许一个待决问题（mutex 串行化），与权限弹窗互不干扰。
 */
@Singleton
class AskUserQuestionManager @Inject constructor() {

    private companion object {
        const val TAG = "AskUserQuestionManager"
        // Q-3：用户回答超时时间，超时放行避免会话无限阻塞
        const val ANSWER_TIMEOUT_MS = 3 * 60 * 1000L
    }

    private val requestMutex = Mutex()

    private val _pendingQuestion = MutableStateFlow<PendingUserQuestion?>(null)

    /** UI 观察此 StateFlow 来决定是否显示问题面板。 */
    val pendingQuestion: StateFlow<PendingUserQuestion?> = _pendingQuestion.asStateFlow()

    private var currentId: String? = null
    private var currentSessionId: String? = null
    private var currentDecision: CompletableDeferred<UserQuestionAnswer>? = null

    /**
     * 工具侧调用：发起一个问题请求，挂起等待用户回答。
     * 同一时刻只允许一个请求（由 [requestMutex] 保证串行）。
     *
     * @param sessionId 发起提问的 AI 会话 id；记录到 pending question 供 UI 按会话过滤、
     *   供 [cancelPending] 按会话清理，避免跨会话/对话结束后仍弹出提问卡。
     */
    suspend fun awaitAnswer(question: PendingUserQuestion, sessionId: String? = null): UserQuestionAnswer = requestMutex.withLock {
        val decision = CompletableDeferred<UserQuestionAnswer>()
        currentId = question.id
        currentSessionId = sessionId
        currentDecision = decision
        _pendingQuestion.value = if (sessionId != null) question.copy(sessionId = sessionId) else question

        try {
            // Q-3：加超时，用户长时间未回答时放行，防止会话被无限挂起阻塞
            withTimeout(ANSWER_TIMEOUT_MS) { decision.await() }
        } catch (e: TimeoutCancellationException) {
            FileLogger.w(TAG, "ask_user_question 等待用户回答超时(${ANSWER_TIMEOUT_MS}ms)，放行")
            UserQuestionAnswer(answers = emptyList())
        } finally {
            if (currentId == question.id) {
                currentId = null
                currentSessionId = null
                currentDecision = null
                _pendingQuestion.value = null
            }
        }
    }

    /**
     * UI 侧调用：用户完成选择后，将答案回传，唤醒挂起的 [awaitAnswer]。
     */
    @Synchronized
    fun resolve(id: String, answer: UserQuestionAnswer) {
        if (currentId != id) return
        _pendingQuestion.value = null
        currentDecision?.complete(answer)
    }

    /**
     * 主动结束待决提问（对话停止 / 结束 / 切换会话时调用），防止提问卡残留。
     *
     * 语义与 [com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager.cancelPending] 一致：
     * @param sessionId 要清理的会话 id；传 null 表示清理「任意会话」的待决提问
     *   （例如 stopAllAgents 全停）。没有匹配的待决提问时为空操作。
     * @param answer 以何种结果结束挂起的等待：默认空答案（对应超时放行的语义，
     *   工具侧按「用户未做选择」继续）。
     */
    @Synchronized
    fun cancelPending(sessionId: String?, answer: UserQuestionAnswer = UserQuestionAnswer(answers = emptyList())) {
        if (currentId == null) return
        if (sessionId != null && currentSessionId != null && currentSessionId != sessionId) return
        _pendingQuestion.value = null
        currentDecision?.complete(answer)
    }
}
