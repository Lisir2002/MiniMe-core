package com.mini.me_core.feature.qqbot.presentation

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.qqbot.data.QBotRepository
import com.mini.me_core.feature.qqbot.domain.LLBotProcessManager
import com.mini.me_core.feature.qqbot.domain.OneBot11Server
import com.mini.me_core.feature.qqbot.domain.QBotConfig
import com.mini.me_core.feature.qqbot.domain.QBotMessage
import com.mini.me_core.feature.qqbot.domain.QBotService
import com.mini.me_core.feature.qqbot.domain.QBotSession
import com.mini.me_core.feature.qqbot.domain.QBotState
import com.mini.me_core.feature.qqbot.domain.QBotWsState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * QQ 机器人管理页 ViewModel。
 *
 * 对接 data 层 [QBotRepository]（会话/消息流）、domain 层 [LLBotProcessManager]
 * （进程生命周期状态）与 [OneBot11Server]（反向 WS 连接状态），并通过
 * [QBotService] 启停前台常驻服务。
 *
 * 配置持久化在私有 [SharedPreferences]，键名见 [PREFS_NAME]。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class QBotViewModel @Inject constructor(
    private val repository: QBotRepository,
    private val processManager: LLBotProcessManager,
    private val oneBotServer: OneBot11Server,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 运行状态 ────────────────────────────────────────────────────────

    /** 机器人进程生命周期状态（直接转发进程管理器的热流）。 */
    val botState: StateFlow<QBotState> = processManager.state

    /** 反向 WS 连接状态（LLBot 是否已连上来）。 */
    val wsState: StateFlow<QBotWsState> = oneBotServer.wsState

    /** 进入 RUNNING 的时刻，用于推算在线时长；非运行态为 0。 */
    private val runningSince = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            var wasRunning = false
            botState.collect { state ->
                if (state == QBotState.RUNNING) {
                    if (!wasRunning) runningSince.value = System.currentTimeMillis()
                } else {
                    runningSince.value = 0L
                }
                wasRunning = state == QBotState.RUNNING
            }
        }
    }

    /** 在线时长（毫秒）；每秒刷新一次，非运行态恒为 0。 */
    val uptimeMs: StateFlow<Long> = flow {
        while (true) {
            val since = runningSince.value
            emit(if (since > 0L) System.currentTimeMillis() - since else 0L)
            delay(1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    // ── 配置 ────────────────────────────────────────────────────────────

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<QBotConfig> = _config.asStateFlow()

    // ── 会话 / 消息 ────────────────────────────────────────────────────

    /** 全部会话（按更新时间倒序，仓库层保证）。 */
    val sessions: StateFlow<List<QBotSession>> = repository
        .getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    /** 当前选中的会话对象（未选中为 null）。 */
    val currentSession: StateFlow<QBotSession?> = combine(
        sessions,
        _selectedSessionId,
    ) { list, id -> list.firstOrNull { it.sessionId == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 当前选中会话的消息流（时间正序，最新在末尾）。 */
    val currentMessages: StateFlow<List<QBotMessage>> = _selectedSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getMessagesBySession(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── 操作 ────────────────────────────────────────────────────────────

    /** 选中会话：记录 id 并清零该会话未读数。 */
    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
        if (sessionId != null) {
            viewModelScope.launch { repository.resetUnread(sessionId) }
        }
    }

    /** 启动前台服务（内部会拉起 LLBot 进程与 WS 服务端）。 */
    fun startBot() = QBotService.start(context)

    /** 停止前台服务（内部会停止 LLBot 进程与 WS 服务端）。 */
    fun stopBot() = QBotService.stop(context)

    /** 更新配置并立即写盘。 */
    fun updateConfig(updater: (QBotConfig) -> QBotConfig) {
        val newConfig = updater(_config.value)
        _config.value = newConfig
        saveConfig(newConfig)
    }

    /** 删除会话及其全部消息；若删除的是当前会话则取消选中。 */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
        }
    }

    /** 清空某会话的全部消息（保留会话本身）。 */
    fun clearMessages(sessionId: String) {
        viewModelScope.launch { repository.deleteMessagesBySession(sessionId) }
    }

    /** 生成一个随机 WS Token（16 位十六进制）。 */
    fun generateToken(): String {
        val token = buildString {
            repeat(16) {
                append(Random.nextInt(0, 16).toString(16))
            }
        }
        updateConfig { it.copy(wsToken = token) }
        return token
    }

    // ── SharedPreferences 读写 ────────────────────────────────────────

    private fun loadConfig(): QBotConfig = QBotConfig(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        wsPort = prefs.getInt(KEY_WS_PORT, DEFAULT_WS_PORT),
        wsToken = prefs.getString(KEY_WS_TOKEN, "") ?: "",
        botQq = prefs.getLong(KEY_BOT_QQ, 0L),
        privateTriggerEnabled = prefs.getBoolean(KEY_PRIVATE_TRIGGER, true),
        groupAtTriggerEnabled = prefs.getBoolean(KEY_GROUP_AT_TRIGGER, true),
        defaultModel = prefs.getString(KEY_DEFAULT_MODEL, "") ?: "",
        contextLength = prefs.getInt(KEY_CONTEXT_LENGTH, DEFAULT_CONTEXT_LENGTH),
        llBotPath = prefs.getString(KEY_LLBOT_PATH, "") ?: "",
    )

    private fun saveConfig(config: QBotConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_WS_PORT, config.wsPort)
            .putString(KEY_WS_TOKEN, config.wsToken)
            .putLong(KEY_BOT_QQ, config.botQq)
            .putBoolean(KEY_PRIVATE_TRIGGER, config.privateTriggerEnabled)
            .putBoolean(KEY_GROUP_AT_TRIGGER, config.groupAtTriggerEnabled)
            .putString(KEY_DEFAULT_MODEL, config.defaultModel)
            .putInt(KEY_CONTEXT_LENGTH, config.contextLength)
            .putString(KEY_LLBOT_PATH, config.llBotPath)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "qqbot_config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_WS_PORT = "ws_port"
        private const val KEY_WS_TOKEN = "ws_token"
        private const val KEY_BOT_QQ = "bot_qq"
        private const val KEY_PRIVATE_TRIGGER = "private_trigger"
        private const val KEY_GROUP_AT_TRIGGER = "group_at_trigger"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_CONTEXT_LENGTH = "context_length"
        private const val KEY_LLBOT_PATH = "ll_bot_path"

        private const val DEFAULT_WS_PORT = 3001
        private const val DEFAULT_CONTEXT_LENGTH = 20
    }
}
