package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.feature.agent.domain.core.model.AgentImage
import com.mini.me_core.feature.agent.domain.session.MessagePersistenceUseCase
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.domain.execution.tool.ToolPermissionManager
import com.mini.me_core.feature.agent.domain.execution.command.SlashCommandHandler
import com.mini.me_core.feature.agent.domain.execution.command.SlashCommandRegistry
import com.mini.me_core.feature.agent.domain.execution.command.SlashCommandContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话协调器：管理请求队列、Slash命令执行、命令占用状态。
 *
 * 从 AIAgentViewModel 提取，职责单一：
 * - 请求队列管理（入队/出队/移除）
 * - Slash命令执行
 * - 命令占用状态跟踪
 *
 * 注意：核心的 executeAgentRequestStream 仍由 AIAgentViewModel 负责，
 * 本类通过回调调用。
 */
@Singleton
class SessionCoordinator @Inject constructor(
    private val toolPermissionManager: ToolPermissionManager,
    private val messagePersistenceUseCase: MessagePersistenceUseCase,
    private val sessionUseCase: SessionUseCase,
    private val slashCommandRegistry: SlashCommandRegistry
) {
    private val _queuedRequests = MutableStateFlow<Map<String, List<QueuedRequest>>>(emptyMap())
    val queuedRequests: StateFlow<Map<String, List<QueuedRequest>>> = _queuedRequests.asStateFlow()

    private val _runningCommandSessions = MutableStateFlow<Set<String>>(emptySet())
    val runningCommandSessions: StateFlow<Set<String>> = _runningCommandSessions.asStateFlow()

    /**
     * 请求入队或立即执行。
     * 如果会话正在运行，请求入队等待；否则立即执行。
     */
    fun enqueueRequest(
        request: String,
        sessionId: String?,
        isSessionRunning: Boolean,
        modelRequest: String = request,
        currentFile: String? = null,
        selectedCode: String? = null,
        projectRoot: String = "",
        inputImages: List<AgentImage> = emptyList(),
        inputAttachments: List<AgentAttachment> = emptyList(),
        isAutoTrigger: Boolean = false,
        onExecute: (QueuedRequest) -> Unit
    ) {
        val sid = sessionId ?: return
        if (isSessionRunning) {
            val req = QueuedRequest(
                id = UUID.randomUUID().toString(),
                request = request,
                modelRequest = modelRequest,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                inputImages = inputImages,
                inputAttachments = inputAttachments,
                isAutoTrigger = isAutoTrigger
            )
            val currentList = _queuedRequests.value[sid] ?: emptyList()
            _queuedRequests.value = _queuedRequests.value + (sid to (currentList + req))
        } else {
            onExecute(
                QueuedRequest(
                    id = "",
                    request = request,
                    modelRequest = modelRequest,
                    currentFile = currentFile,
                    selectedCode = selectedCode,
                    projectRoot = projectRoot,
                    inputImages = inputImages,
                    inputAttachments = inputAttachments,
                    isAutoTrigger = isAutoTrigger
                )
            )
        }
    }

    /**
     * 从当前会话队列移除指定条目。
     */
    fun removeQueuedRequest(sessionId: String?, id: String) {
        val sid = sessionId ?: return
        val queue = _queuedRequests.value[sid] ?: return
        _queuedRequests.value = _queuedRequests.value + (sid to queue.filterNot { it.id == id })
    }

    /**
     * 处理队列中的下一条请求。
     */
    fun processNextInQueue(sessionId: String, onExecute: (QueuedRequest) -> Unit) {
        // 进入下一条前，清理可能残留的权限确认请求
        toolPermissionManager.cancelPending(sessionId)
        val queue = _queuedRequests.value[sessionId] ?: return
        val next = queue.firstOrNull() ?: return
        _queuedRequests.value = _queuedRequests.value + (sessionId to queue.drop(1))
        onExecute(next)
    }

    /**
     * 执行Slash命令。
     * 先把命令文本作为用户消息落库，再执行handler。
     * 执行期间标记为命令占用，结束后接续队列。
     */
    suspend fun runSlashCommand(
        command: SlashCommandHandler,
        input: String,
        sessionId: String,
        commandContext: SlashCommandContext,
        onComplete: () -> Unit
    ) {
        _runningCommandSessions.value = _runningCommandSessions.value + sessionId
        try {
            messagePersistenceUseCase.persist(sessionId, MessageRole.USER, input)
            sessionUseCase.touch(sessionId, messagePersistenceUseCase.nextTimestamp())
            command.executeWithInput(commandContext, input)
        } finally {
            _runningCommandSessions.value = _runningCommandSessions.value - sessionId
            onComplete()
        }
    }

    /**
     * 检查会话是否正在执行命令。
     */
    fun isRunningCommand(sessionId: String): Boolean =
        sessionId in _runningCommandSessions.value

    /**
     * 获取指定会话的队列长度。
     */
    fun getQueueSize(sessionId: String): Int =
        _queuedRequests.value[sessionId]?.size ?: 0

    /**
     * 清除指定会话的队列和命令状态。
     */
    fun clearSession(sessionId: String) {
        _queuedRequests.value = _queuedRequests.value - sessionId
        _runningCommandSessions.value = _runningCommandSessions.value - sessionId
    }
}
