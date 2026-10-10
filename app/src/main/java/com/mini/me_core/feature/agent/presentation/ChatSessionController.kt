package com.mini.me_core.feature.agent.presentation

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.toChatSession
import com.mini.me_core.datalayer.repository.AgentRepository as V2AgentRepository
import com.mini.mecore.datalayer.sqldelight.agent.Agent_message as V2AgentMessage
import com.mini.mecore.datalayer.sqldelight.agent.Agent_session as V2AgentSession
import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import com.mini.me_core.feature.agent.domain.core.model.ChatSession
import com.mini.me_core.feature.agent.domain.core.model.ReasoningEffort
import com.mini.me_core.feature.agent.domain.session.SessionUseCase
import com.mini.me_core.feature.agent.domain.session.checkpoint.CheckpointManager
import com.mini.me_core.feature.backup.domain.BackupManager
import com.mini.me_core.feature.settings.data.repository.DefaultModelSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

/**
 * 会话生命周期控制器（P1-08 从 AIAgentViewModel 提取，行为保持搬迁）。
 *
 * 内聚「会话加载/选择/新建/删除/撤销/重命名/工作台绑定/导出/默认模型绑定」等会话 CRUD。
 * 会话级流式/工具/错误等 transient UI 状态仍由门面持有，本类通过三个回调在原有时序点清理：
 * - [onResetSessionState]：新会话复用空会话时把该会话状态复位（原 setAgentState(Idle)+setChanges(空)）；
 * - [onCancelSessionJob]：删除会话时取消并移除其运行 job（原 sessionJobs[id]?.cancel()/remove）；
 * - [onClearSessionMaps]：删除会话时清理各 transient StateFlow 中该会话条目（原 7 行 map 移除）。
 *
 * currentSessionId/currentWorkspace 的 MutableStateFlow 仍由门面持有并原样传入，
 * 读写同一实例；协程全部由门面传入的同一 [scope]（即 viewModelScope）驱动，不新建独立 scope。
 */
@Suppress("LongParameterList")
internal class ChatSessionController(
    private val scope: CoroutineScope,
    private val currentSessionId: MutableStateFlow<String?>,
    private val currentWorkspace: MutableStateFlow<String>,
    private val sessionUseCase: SessionUseCase,
    private val v2Agent: V2AgentRepository,
    private val checkpointManager: CheckpointManager,
    private val defaultModelSettingsRepository: DefaultModelSettingsRepository,
    private val environmentProbeController: EnvironmentProbeController,
    private val backupManager: BackupManager,
    private val onResetSessionState: (String) -> Unit,
    private val onCancelSessionJob: (String) -> Unit,
    private val onClearSessionMaps: (String) -> Unit,
) {

    /**
     * 最近一次删除的会话数据（会话 + 全部消息），用于 Snackbar「撤销」恢复。
     * 单槽覆盖：仅保留最近一次删除；进程被杀/会话切走后缓存失效（撤销窗口短，低风险）。
     */
    private var lastDeletedSession: Pair<V2AgentSession, List<V2AgentMessage>>? = null

    /** 新建会话；若当前会话还是空的则直接复用，避免堆积空会话。 */
    fun newSession() = scope.launch {
        if (currentWorkspace.value.isBlank()) return@launch
        val curId = currentSessionId.value
        if (curId != null && sessionUseCase.isSessionEmpty(curId)) {
            onResetSessionState(curId)
            return@launch
        }
        val s = createSession()
        sessionUseCase.upsertSession(s)
        currentSessionId.value = s.id
    }

    fun setCurrentSessionId(id: String) {
        if (currentSessionId.value == id) return
        currentSessionId.value = id
        // 切换会话时从磁盘恢复环境快照（冷启动/进程被杀后重建气泡）
        environmentProbeController.restoreSnapshots(id)
    }

    fun selectSession(id: String) {
        if (currentSessionId.value == id) return
        currentSessionId.value = id
        // 切换会话时从磁盘恢复环境快照（冷启动/进程被杀后重建气泡）
        environmentProbeController.restoreSnapshots(id)
    }

    fun setSessionMode(mode: AgentMode) {
        val sid = currentSessionId.value ?: return
        scope.launch {
            sessionUseCase.updateMode(sid, mode.name)
        }
    }

    fun setSessionReasoningEffort(effort: ReasoningEffort) {
        val sid = currentSessionId.value ?: return
        scope.launch {
            sessionUseCase.updateReasoningEffort(sid, effort.name)
        }
    }

    fun setSessionProviderModel(providerId: String, model: String) {
        val sid = currentSessionId.value ?: return
        scope.launch {
            sessionUseCase.updateProviderModel(sid, providerId, model)
            // 空会话中的选择视为「新会话默认模型」，供下次新建会话沿用
            if (sessionUseCase.isSessionEmpty(sid)) {
                defaultModelSettingsRepository.setDefaultModel(providerId, model)
            }
        }
    }

    fun deleteSession(id: String) = scope.launch {
        // 删除前缓存会话 + 消息，供 Snackbar「撤销」恢复（re-insert）
        val entity = sessionUseCase.getSessionById(id)
        val messages = if (entity != null) {
            v2Agent.getMessagesBySessionOnce(id)
        } else emptyList()
        lastDeletedSession = entity?.let { it to messages }

        checkpointManager.clearSessionCheckpoints(id)
        sessionUseCase.deleteSession(id)
        // 删除会话时同步清理环境快照持久化
        environmentProbeController.clearSnapshots(id)

        onCancelSessionJob(id)
        onClearSessionMaps(id)

        if (currentSessionId.value == id) {
            val ws = currentWorkspace.value
            if (ws.isBlank()) {
                currentSessionId.value = null
            } else {
                // 删除后选中该工作台最近会话；否则选最近未绑定会话（同工作台可见）；
                // 再回退全局最近会话；都没有则置空。
                val remaining = sessionUseCase.getFirstSessionOfWorkspace(ws)
                    ?: sessionUseCase.getFirstUnboundSession()
                    ?: sessionUseCase.getMostRecentSession()
                currentSessionId.value = remaining?.id
            }
        }
    }

    /** 撤销最近一次删除：恢复会话 + 消息（re-insert），并选中被恢复的会话。 */
    fun undoDeleteSession() = scope.launch {
        val (entity, messages) = lastDeletedSession ?: return@launch
        lastDeletedSession = null
        sessionUseCase.upsertSession(entity)
        if (messages.isNotEmpty()) {
            v2Agent.insertAllMessages(messages)
        }
        currentSessionId.value = entity.id
    }

    /** 重命名会话标题。仅更新 title，不改 updatedAt，列表顺序保持不变。 */
    fun renameSession(id: String, newTitle: String) = scope.launch {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@launch
        sessionUseCase.updateTitle(id, trimmed)
    }

    /**
     * 手动绑定当前会话到指定工作台（「更多配置 → 工作台绑定」）。
     * 绑定即一次性的：仅未绑定会话生效，已绑定会话忽略（bindWorkspace 内部保证）。
     */
    fun bindSessionWorkspace(workspacePath: String) = scope.launch {
        val sid = currentSessionId.value ?: return@launch
        sessionUseCase.bindWorkspace(sid, workspacePath)
    }

    /**
     * 查询某个工作区绑定的所有会话（按更新时间倒序），供侧边栏「所有工作台 → 查看对话绑定」展示。
     * 会话与工作区一对一绑定、不可中途切换；一个工作区可绑定多个会话。
     */
    suspend fun sessionsBoundToWorkspace(workspacePath: String): List<ChatSession> =
        v2Agent.getAllSessionsByWorkspaceOnce(workspacePath).map { it.toChatSession() }

    /** 导出单个会话为无密码备份格式（tar.gz），流式写入 [output]（调用方打开，本方法负责关闭）。成功回调 true，失败回调 false。 */
    @Suppress("TooGenericExceptionCaught") // 会话操作兜底，行为保持搬迁（保留原 FileLogger 记录 cause 与 finally 关闭流）
    fun exportSession(sessionId: String, output: OutputStream, onResult: (Boolean) -> Unit) = scope.launch {
        try {
            backupManager.exportSession(sessionId, output)
            onResult(true)
        } catch (e: Exception) {
            FileLogger.e("AIAgentViewModel", "exportSession failed", e)
            onResult(false)
        } finally {
            runCatching { output.close() }
        }
    }

    internal suspend fun ensureSession(): String {
        currentSessionId.value?.let { return it }
        val ws = currentWorkspace.value
        if (ws.isBlank()) return ""
        val existing = sessionUseCase.getFirstSessionOfWorkspace(ws)
        val id = if (existing != null) existing.id else {
            val s = createSession()
            sessionUseCase.upsertSession(s)
            s.id
        }
        currentSessionId.value = id
        return id
    }

    /**
     * 创建新会话并按「新会话默认模型」绑定 provider/model；未设置默认时回退全局 active provider。
     * 所有新建会话的入口（冷启动、新建、删除兜底、ensureSession、newChatAndSend）都走这里。
     *
     * 新会话初始**不绑定工作台**（workspacePath 为空）：工作台绑定发生在用户发第一条消息时自动绑定，
     * 或由用户在「更多配置 → 工作台绑定」手动绑定。
     */
    internal suspend fun createSession(): V2AgentSession {
        val s = sessionUseCase.newSession("")
        val providerId = defaultModelSettingsRepository.getDefaultProviderId()
        val model = defaultModelSettingsRepository.getDefaultModel()
        if (providerId.isNotBlank() && model.isNotBlank()) {
            sessionUseCase.updateProviderModel(s.id, providerId, model)
        }
        return s
    }
}
