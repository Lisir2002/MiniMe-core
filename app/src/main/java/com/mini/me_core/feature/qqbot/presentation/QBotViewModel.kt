package com.mini.me_core.feature.qqbot.presentation

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.qqbot.data.QBotRepository
import com.mini.me_core.feature.qqbot.domain.LLBotProcessManager
import com.mini.me_core.feature.qqbot.domain.MessageProcessor
import com.mini.me_core.feature.qqbot.domain.OneBot11Server
import com.mini.me_core.feature.qqbot.domain.OneBotApiClient
import com.mini.me_core.feature.qqbot.domain.QBotConfig
import com.mini.me_core.feature.qqbot.domain.QBotConstants
import com.mini.me_core.feature.qqbot.domain.QBotLoginState
import com.mini.me_core.feature.qqbot.domain.QBotMessage
import com.mini.me_core.feature.qqbot.domain.QBotModelOption
import com.mini.me_core.feature.qqbot.domain.QBotService
import com.mini.me_core.feature.qqbot.domain.QBotSession
import com.mini.me_core.feature.qqbot.domain.QBotState
import com.mini.me_core.feature.qqbot.domain.QBotWsState
import com.mini.me_core.feature.qqbot.domain.QrCodeManager
import com.mini.me_core.feature.settings.data.remote.ModelMetadataService
import com.mini.me_core.feature.settings.domain.model.AIProviderConfig
import com.mini.me_core.feature.settings.domain.repository.AIProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
    private val oneBotApiClient: OneBotApiClient,
    private val qrCodeManager: QrCodeManager,
    private val messageProcessor: MessageProcessor,
    private val providerRepository: AIProviderRepository,
    private val modelMetadataService: ModelMetadataService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── 运行状态 ────────────────────────────────────────────────────────

    /** 机器人进程生命周期状态（直接转发进程管理器的热流）。 */
    val botState: StateFlow<QBotState> = processManager.state

    /** 反向 WS 连接状态（LLBot 是否已连上来）。 */
    val wsState: StateFlow<QBotWsState> = oneBotServer.wsState

    // ── 登录状态 ────────────────────────────────────────────────────────

    /** 登录二维码 PNG 字节；null 表示当前不展示二维码。 */
    val qrCodeData: StateFlow<ByteArray?> = qrCodeManager.qrCodeData

    /** 登录状态机。 */
    val loginState: StateFlow<QBotLoginState> = qrCodeManager.loginState

    /** 登录成功后的机器人 QQ 号。 */
    val loggedBotQq: StateFlow<Long> = qrCodeManager.botQq

    /** 登录成功后的机器人昵称。 */
    val loggedBotNickname: StateFlow<String> = qrCodeManager.botNickname

    /** 登录失败的错误信息。 */
    val loginErrorMessage: StateFlow<String> = qrCodeManager.loginErrorMessage

    /** 设备锁验证码发送的手机号（尾号）。 */
    val smsCodePhone: StateFlow<String> = qrCodeManager.smsCodePhone

    /** 提交设备锁短信验证码。 */
    fun submitSmsCode(code: String) {
        qrCodeManager.submitSmsCode(code)
    }

    /** 当前选中模型的上下文窗口（token），未知为 0。 */
    private val _selectedModelContextTokens = MutableStateFlow(0)
    val selectedModelContextTokens: StateFlow<Int> = _selectedModelContextTokens.asStateFlow()

    // ── 可选模型列表 ────────────────────────────────────────────────────

    /**
     * 全部可用模型（来自已启用供应商的 models 列表），供设置页下拉选择。
     * 不复写模型管理逻辑，直接复用 [AIProviderRepository.getAllProviders]。
     */
    val modelOptions: StateFlow<List<QBotModelOption>> = providerRepository.getAllProviders()
        .map { providers ->
            providers.filter { it.isEnabled }.flatMap { p ->
                p.models.map { m ->
                    QBotModelOption(modelId = m, providerId = p.id, providerName = p.name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
        // WS 连接建立后查询机器人登录信息，回填 QQ 号 / 昵称。
        viewModelScope.launch {
            wsState.collect { state ->
                if (state == QBotWsState.CONNECTED) {
                    refreshLoginInfo()
                }
            }
        }
    }

    /** 调用 OneBot `get_login_info`，回填机器人 QQ / 昵称并同步到消息处理链。 */
    private fun refreshLoginInfo() {
        viewModelScope.launch {
            runCatching { oneBotApiClient.getLoginInfo() }
                .getOrNull()
                ?.let { (qq, nickname) ->
                    messageProcessor.botQq = qq
                    qrCodeManager.onLoggedIn(qq, nickname)
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
    fun startBot() {
        // 启动前把最新配置（含 botQq / 登录方式 / 密码）注入进程管理器。
        processManager.configure(_config.value)
        QBotService.start(context)
    }

    /** 停止前台服务（内部会停止 LLBot 进程与 WS 服务端）。 */
    fun stopBot() = QBotService.stop(context)

    /** 清除登录状态并重启 LLBot，重新获取二维码。 */
    fun relogin() {
        viewModelScope.launch {
            qrCodeManager.resetForRelogin()
            QBotService.stop(context)
            delay(600L) // 等待服务停止、进程退出
            processManager.configure(_config.value)
            QBotService.start(context)
        }
    }

    /** 手动隐藏当前二维码（例如用户已扫码后刷新页面）。 */
    fun clearQrCode() = qrCodeManager.clearQrCode()

    /** 重试登录（登录失败 / 掉线后）。 */
    fun retryLogin() = relogin()

    /**
     * 从可选模型中选择默认模型；自动读取该模型的上下文窗口并回填上下文长度，
     * 上下文长度不再允许自由编辑。
     */
    fun selectModel(option: QBotModelOption) {
        updateConfig { it.copy(defaultModel = option.modelId) }
        viewModelScope.launch {
            runCatching {
                val providers = providerRepository.getAllProviders().first()
                val provider = providers.firstOrNull { it.id == option.providerId }
                val meta = provider?.let {
                    modelMetadataService.resolve(it.type, option.modelId)
                }
                val ctxTokens = meta?.let { it.inputTokens ?: it.contextTokens } ?: 0
                _selectedModelContextTokens.value = ctxTokens
                // 由模型上下文窗口推导一个合理的消息条数（约每 1K token 一条消息），
                //  clamped 到常用区间；模型无元数据时保留默认 20。
                val derived = if (ctxTokens > 0) (ctxTokens / 1000).coerceIn(8, 200)
                else QBotConstants.DEFAULT_CONTEXT_LENGTH
                updateConfig { it.copy(contextLength = derived) }
            }
        }
    }

    /** 设置登录方式（qrcode / password）。 */
    fun setLoginType(type: String) {
        updateConfig { it.copy(loginType = type) }
    }

    /** 设置密码（轻量 Base64 编码后持久化）。 */
    fun setPassword(plain: String) {
        val encoded = if (plain.isBlank()) ""
        else Base64.encodeToString(plain.toByteArray(), Base64.NO_WRAP)
        updateConfig { it.copy(passwordEncrypted = encoded) }
    }

    /** 读取已保存密码的明文（仅用于回填输入框）。 */
    fun savedPasswordPlain(): String {
        val enc = _config.value.passwordEncrypted
        if (enc.isBlank()) return ""
        return runCatching {
            String(Base64.decode(enc, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrDefault("")
    }

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
        loginType = prefs.getString(KEY_LOGIN_TYPE, "qrcode") ?: "qrcode",
        passwordEncrypted = prefs.getString(KEY_PASSWORD_ENC, "") ?: "",
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
            .putString(KEY_LOGIN_TYPE, config.loginType)
            .putString(KEY_PASSWORD_ENC, config.passwordEncrypted)
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
        private const val KEY_LOGIN_TYPE = "login_type"
        private const val KEY_PASSWORD_ENC = "password_enc"
        private const val KEY_PRIVATE_TRIGGER = "private_trigger"
        private const val KEY_GROUP_AT_TRIGGER = "group_at_trigger"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_CONTEXT_LENGTH = "context_length"
        private const val KEY_LLBOT_PATH = "ll_bot_path"

        private const val DEFAULT_WS_PORT = 3001
        private const val DEFAULT_CONTEXT_LENGTH = 20
    }
}
