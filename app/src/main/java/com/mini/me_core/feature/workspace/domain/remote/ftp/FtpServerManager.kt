package com.mini.me_core.feature.workspace.domain.remote.ftp

import android.content.Context
import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

const val WORKSPACE_FTP_NS = "workspace"
const val FTP_PORT_KEY = "ftp_port"
const val FTP_USERNAME_KEY = "ftp_username"
const val FTP_PASSWORD_KEY = "ftp_password"
const val FTP_ANONYMOUS_KEY = "ftp_anonymous"
const val FTP_AUTO_START_KEY = "ftp_auto_start"
const val FTP_LAN_EXPOSED_KEY = "ftp_lan_exposed"

/**
 * FTP 安全默认策略（纯逻辑，不依赖 Android，便于单测）。
 *
 * 安全基线：
 *  - 默认仅绑定回环地址 [BIND_LOOPBACK]；仅当用户显式开启「局域网访问」时才绑定全网卡 [BIND_ALL]。
 *  - 拒绝弱口令/常见默认口令（含 123456），启动前强制校验。
 *  - 匿名账号只读，绝不授予写权限；写权限只给已认证且由用户显式配置的账号。
 */
internal object FtpSecurityPolicy {
    const val BIND_LOOPBACK = "127.0.0.1"
    const val BIND_ALL = "0.0.0.0"

    /** 常见弱口令/默认口令黑名单（小写比较）。 */
    private val COMMON_WEAK = setOf(
        "123456", "12345678", "123456789", "1234567890", "password", "passwd",
        "admin", "root", "qwerty", "letmein", "minime", "ftp", "anonymous", "user"
    )

    /** 根据「是否允许局域网访问」决定绑定地址。默认回环。 */
    fun bindAddress(lanExposed: Boolean): String =
        if (lanExposed) BIND_ALL else BIND_LOOPBACK

    /** 弱口令判定：过短、命中常见弱口令、或纯数字。 */
    fun isWeakPassword(password: String): Boolean {
        val t = password.trim()
        if (t.length < 8) return true
        if (t.lowercase() in COMMON_WEAK) return true
        if (t.isNotEmpty() && t.all { it.isDigit() }) return true
        return false
    }

    /** 生成随机强口令（去除易混淆字符 0/O/1/l/I）。仅在用户主动查看时展示，不进日志。 */
    fun generateRandomPassword(): String {
        val pool = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
        val rnd = java.security.SecureRandom()
        return (1..20).map { pool[rnd.nextInt(pool.length)].toString() }.joinToString("")
    }

    /**
     * 启动前校验。返回 null 表示可启动；否则返回面向用户的阻塞原因。
     *
     * 注意：绑定地址由 [bindAddress] 决定；「局域网访问」开关本身就是用户对明文 FTP 风险的
     * 显式确认（UI 侧切换时需二次确认），这里不再因其开启而阻断启动。
     */
    fun validateStart(username: String, password: String): String? {
        if (username.isBlank()) return "FTP 用户名未设置，无法启动。"
        if (isWeakPassword(password)) {
            return "FTP 口令过短或为常见弱口令（至少 8 位、非纯数字、非默认口令），请先设置强口令再启动。"
        }
        return null
    }
}

@Singleton
class FtpServerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val kv: KVStore,
) {
    private companion object {
        const val TAG = "FtpServerManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ftpServer: FtpServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(2121)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _username = MutableStateFlow("minime")
    val username: StateFlow<String> = _username.asStateFlow()

    // 不再预置弱默认口令；未设置时由 init 生成随机强口令（仅回显给用户，绝不写日志）。
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _isAnonymous = MutableStateFlow(false)
    val isAnonymous: StateFlow<Boolean> = _isAnonymous.asStateFlow()

    // 局域网访问默认关：默认仅绑回环；开启后才绑 0.0.0.0（需用户在 UI 二次确认明文风险）。
    private val _isLanExposed = MutableStateFlow(false)
    val isLanExposed: StateFlow<Boolean> = _isLanExposed.asStateFlow()

    private val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val defaultSharedPath: String by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }.absolutePath
    }

    init {
        scope.launch {
            _port.value = (kv.getInt(WORKSPACE_FTP_NS, FTP_PORT_KEY) ?: 2121L).toInt()
            _username.value = kv.getString(WORKSPACE_FTP_NS, FTP_USERNAME_KEY) ?: "minime"
            _isAnonymous.value = kv.getBool(WORKSPACE_FTP_NS, FTP_ANONYMOUS_KEY) ?: false
            _isLanExposed.value = kv.getBool(WORKSPACE_FTP_NS, FTP_LAN_EXPOSED_KEY) ?: false
            _autoStart.value = kv.getBool(WORKSPACE_FTP_NS, FTP_AUTO_START_KEY) ?: false

            // 口令迁移：未设置 / 仍为旧版弱默认口令（123456 等）→ 生成随机强口令并落库。
            // 口令绝不写日志；用户可在设置页主动查看（password 状态流）。
            val storedPw = kv.getString(WORKSPACE_FTP_NS, FTP_PASSWORD_KEY) ?: ""
            _password.value = if (FtpSecurityPolicy.isWeakPassword(storedPw)) {
                val generated = FtpSecurityPolicy.generateRandomPassword()
                kv.putString(WORKSPACE_FTP_NS, FTP_PASSWORD_KEY, generated)
                generated
            } else {
                storedPw
            }

            updateServerUrl()
            if (_autoStart.value) startServer()
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "获取本机 IP 失败: ${e.message}", e)
        }
        return "127.0.0.1"
    }

    private fun updateServerUrl() {
        // 未开启局域网访问时仅回环可达，展示地址也应为 127.0.0.1（避免误导用户以为对外可达）。
        val ip = if (_isLanExposed.value) getLocalIpAddress() else "127.0.0.1"
        _serverUrl.value = "ftp://$ip:${_port.value}"
    }

    /**
     * 切换「局域网访问」。绑定 0.0.0.0 是高风险动作（明文 FTP 对全网卡开放），
     * 由调用方（UI）在用户二次确认后调用；默认关闭。运行中切换会重启服务以生效。
     */
    suspend fun setLanExposed(enabled: Boolean) = withContext(Dispatchers.IO) {
        val wasRunning = _isRunning.value
        if (wasRunning) stopServerInternal()
        _isLanExposed.value = enabled
        kv.putBool(WORKSPACE_FTP_NS, FTP_LAN_EXPOSED_KEY, enabled)
        updateServerUrl()
        if (wasRunning) startServerInternal()
    }

    suspend fun saveConfig(port: Int, username: String, password: String, isAnonymous: Boolean, autoStart: Boolean) = withContext(Dispatchers.IO) {
        val wasRunning = _isRunning.value
        if (wasRunning) stopServerInternal()

        _port.value = port
        _username.value = username
        _password.value = password
        _isAnonymous.value = isAnonymous
        _autoStart.value = autoStart
        updateServerUrl()

        kv.putInt(WORKSPACE_FTP_NS, FTP_PORT_KEY, port.toLong())
        kv.putString(WORKSPACE_FTP_NS, FTP_USERNAME_KEY, username)
        kv.putString(WORKSPACE_FTP_NS, FTP_PASSWORD_KEY, password)
        kv.putBool(WORKSPACE_FTP_NS, FTP_ANONYMOUS_KEY, isAnonymous)
        kv.putBool(WORKSPACE_FTP_NS, FTP_AUTO_START_KEY, autoStart)

        if (wasRunning) startServerInternal()
    }

    suspend fun toggleServer(): Boolean = withContext(Dispatchers.IO) {
        if (_isRunning.value) { stopServerInternal(); false } else startServerInternal()
    }

    suspend fun startServer(): Boolean = withContext(Dispatchers.IO) {
        if (_isRunning.value) return@withContext true
        startServerInternal()
    }

    suspend fun stopServer() = withContext(Dispatchers.IO) {
        if (!_isRunning.value) return@withContext
        stopServerInternal()
    }

    private fun startServerInternal(): Boolean {
        try {
            _errorMessage.value = null
            // 启动前安全校验：弱口令/默认口令直接拒绝启动（fail-closed），不打印口令内容。
            val blocked = FtpSecurityPolicy.validateStart(_username.value, _password.value)
            if (blocked != null) {
                FileLogger.w(TAG, "FTP 启动被安全策略拒绝: $blocked")
                _errorMessage.value = blocked
                _isRunning.value = false
                return false
            }
            stopServerInternal()
            val bindAddr = FtpSecurityPolicy.bindAddress(_isLanExposed.value)
            val serverFactory = FtpServerFactory()
            serverFactory.addListener("default", buildListener(bindAddr))
            serverFactory.userManager = buildUserManager()
            val server = serverFactory.createServer()
            server.start()
            ftpServer = server
            _isRunning.value = true
            updateServerUrl()
            // 日志脱敏：只打绑定地址与共享目录，绝不打口令；对外网开放时附明文风险提示。
            FileLogger.i(TAG, "内置 FTP 服务端已启动( bind=$bindAddr ): ${_serverUrl.value}, 共享目录: $defaultSharedPath")
            if (_isLanExposed.value) {
                FileLogger.w(TAG, "警告：当前为明文 FTP 且对局域网开放，账号与数据明文传输，请在受信网络使用。")
            }
            return true
        } catch (e: Exception) {
            FileLogger.e(TAG, "启动内置 FTP 服务端失败: ${e.message}", e)
            _errorMessage.value = e.message ?: "启动失败，可能端口被占用"
            _isRunning.value = false
            return false
        }
    }

    /** 构造监听：默认仅回环，仅用户显式开启局域网访问时才绑全网卡。 */
    private fun buildListener(bindAddr: String): org.apache.ftpserver.listener.Listener =
        ListenerFactory().apply {
            port = _port.value
            serverAddress = bindAddr
        }.createListener()

    /**
     * 构造用户管理器：
     *  - 已认证账号：可读 + 可写（WritePermission）；
     *  - 匿名账号：只读，绝不授予 WritePermission（即便匿名被启用也只能浏览/下载）。
     */
    private fun buildUserManager(): org.apache.ftpserver.ftplet.UserManager {
        val userManagerFactory = PropertiesUserManagerFactory().apply {
            file = File(context.cacheDir, "ftp_users.properties").apply { createNewFile() }
            passwordEncryptor = ClearTextPasswordEncryptor()
        }
        val userManager = userManagerFactory.createUserManager()
        val authenticatedAuthorities = ArrayList<Authority>().apply {
            add(WritePermission()); add(ConcurrentLoginPermission(20, 20))
        }
        val anonymousAuthorities = ArrayList<Authority>().apply {
            add(ConcurrentLoginPermission(20, 20))
        }
        if (_username.value.isNotBlank()) {
            val user = BaseUser().apply {
                name = _username.value; password = _password.value
                homeDirectory = defaultSharedPath; this.authorities = authenticatedAuthorities
            }
            userManager.save(user)
        }
        if (_isAnonymous.value) {
            val anonUser = BaseUser().apply {
                name = "anonymous"; homeDirectory = defaultSharedPath; this.authorities = anonymousAuthorities
            }
            userManager.save(anonUser)
        }
        return userManager
    }

    private fun stopServerInternal() {
        try {
            ftpServer?.stop(); ftpServer = null; _isRunning.value = false
            FileLogger.i(TAG, "内置 FTP 服务端已停止")
        } catch (e: Exception) {
            FileLogger.e(TAG, "停止内置 FTP 服务端出错（忽略）: ${e.message}", e)
        }
    }
}
