package com.mini.me_core.feature.qqbot.domain

import com.mini.me_core.core.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ 机器人登录二维码管理器。
 *
 * 职责：
 * 1. 解析 LLBot 进程日志，提取登录二维码（Base64 PNG）与登录状态变更。
 * 2. 维护登录状态机：LOGGED_OUT → QR_SHOWN → SCANNING → LOGGING_IN → LOGGED_IN。
 * 3. 暴露 StateFlow 供 UI 层观察二维码、登录状态、机器人信息。
 *
 * 日志格式参考 LLOneBot / LuckyLillia：
 * - 二维码：`[QR] data:image/png;base64,<base64>` 或 `QRCODE:<base64>`
 * - 扫码：`已扫码，请在手机上确认` / `QR code scanned`
 * - 登录成功：`登录成功` / `Logged in` / `QQ: <qq> Nickname: <nick>`
 * - 登录失败：`登录失败` / `Login failed: <reason>`
 */
@Singleton
class QrCodeManager @Inject constructor() {

    private companion object {
        const val TAG = "QrCodeManager"

        // 二维码 Base64 提取：匹配 data:image/png;base64,xxx 或 QRCODE:xxx
        private val QR_BASE64_PATTERN = Pattern.compile(
            "(?:data:image/png;base64,|QRCODE:)([A-Za-z0-9+/=]+)",
            Pattern.CASE_INSENSITIVE
        )

        // 登录成功：提取 QQ 号和昵称
        private val LOGIN_SUCCESS_PATTERN = Pattern.compile(
            "(?:登录成功|Logged in|Login success).*?(\\d{5,12}).*?([\\u4e00-\\u9fa5A-Za-z0-9_\\-]{1,20})",
            Pattern.CASE_INSENSITIVE
        )

        // 扫码确认
        private val SCANNING_KEYWORDS = listOf(
            "已扫码", "请在手机上确认", "QR code scanned",
            "waiting for confirm", "扫码成功"
        )

        // 登录中
        private val LOGGING_IN_KEYWORDS = listOf(
            "正在登录", "logging in", "submitting ticket",
            "鉴权中", "authenticating"
        )

        // 登录失败
        private val LOGIN_FAILED_KEYWORDS = listOf(
            "登录失败", "Login failed", "login error",
            "二维码已过期", "QR expired", "timeout"
        )

        // 需要设备锁短信验证码
        private val SMS_CODE_KEYWORDS = listOf(
            "设备锁", "验证码", "短信验证", "手机验证",
            "sms code", "verification code", "device lock",
            "请输入验证码", "验证码已发送"
        )
    }

    // ============== 可观察状态 ==============

    private val _qrCodeData = MutableStateFlow<ByteArray?>(null)
    val qrCodeData: StateFlow<ByteArray?> = _qrCodeData.asStateFlow()

    private val _loginState = MutableStateFlow(QBotLoginState.LOGGED_OUT)
    val loginState: StateFlow<QBotLoginState> = _loginState.asStateFlow()

    private val _botQq = MutableStateFlow(0L)
    val botQq: StateFlow<Long> = _botQq.asStateFlow()

    private val _botNickname = MutableStateFlow("")
    val botNickname: StateFlow<String> = _botNickname.asStateFlow()

    private val _loginErrorMessage = MutableStateFlow("")
    val loginErrorMessage: StateFlow<String> = _loginErrorMessage.asStateFlow()

    private val _smsCodePhone = MutableStateFlow("")
    val smsCodePhone: StateFlow<String> = _smsCodePhone.asStateFlow()

    // ============== 公开方法 ==============

    /**
     * 解析 LLBot 日志行，提取二维码或登录状态。
     * 内部已做容错，格式不符的行直接忽略。
     */
    fun parseLogLine(line: String) {
        if (line.isBlank()) return

        // 1. 尝试提取二维码
        val qrMatch = QR_BASE64_PATTERN.matcher(line)
        if (qrMatch.find()) {
            try {
                val base64 = qrMatch.group(1)
                val pngBytes = Base64.getDecoder().decode(base64)
                if (pngBytes.isNotEmpty() && pngBytes.size > 100) {
                    _qrCodeData.value = pngBytes
                    _loginState.value = QBotLoginState.QR_SHOWN
                    _loginErrorMessage.value = ""
                    FileLogger.i(TAG, "二维码已提取，大小=${pngBytes.size}字节")
                    return
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "二维码 Base64 解码失败: ${e.message}")
            }
        }

        // 2. 登录成功
        val successMatch = LOGIN_SUCCESS_PATTERN.matcher(line)
        if (successMatch.find()) {
            try {
                val qq = successMatch.group(1)?.toLongOrNull() ?: 0L
                val nickname = successMatch.group(2) ?: ""
                onLoggedIn(qq, nickname)
                return
            } catch (e: Exception) {
                FileLogger.w(TAG, "登录成功信息解析失败: ${e.message}")
            }
        }
        // 宽松匹配：只包含"登录成功"但没有 QQ 号
        if (line.contains("登录成功") || line.contains("Logged in", ignoreCase = true)) {
            if (_loginState.value != QBotLoginState.LOGGED_IN) {
                _loginState.value = QBotLoginState.LOGGED_IN
                _qrCodeData.value = null
                FileLogger.i(TAG, "检测到登录成功（无QQ号信息）")
            }
            return
        }

        // 3. 扫码确认
        if (SCANNING_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
            if (_loginState.value == QBotLoginState.QR_SHOWN) {
                _loginState.value = QBotLoginState.SCANNING
                FileLogger.i(TAG, "用户已扫码，等待确认")
            }
            return
        }

        // 4. 登录中
        if (LOGGING_IN_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
            if (_loginState.value == QBotLoginState.SCANNING ||
                _loginState.value == QBotLoginState.QR_SHOWN
            ) {
                _loginState.value = QBotLoginState.LOGGING_IN
                FileLogger.i(TAG, "正在登录...")
            }
            return
        }

        // 5. 需要设备锁短信验证码
        if (SMS_CODE_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
            if (_loginState.value != QBotLoginState.SMS_CODE_REQUIRED) {
                _loginState.value = QBotLoginState.SMS_CODE_REQUIRED
                _qrCodeData.value = null
                // 尝试从日志中提取手机号（尾号4位）
                val phoneMatch = Regex("""1[3-9]\d{9}|尾号\s*(\d{4})""").find(line)
                _smsCodePhone.value = phoneMatch?.value ?: ""
                FileLogger.i(TAG, "需要设备锁短信验证码: $line")
            }
            return
        }

        // 6. 登录失败
        if (LOGIN_FAILED_KEYWORDS.any { line.contains(it, ignoreCase = true) }) {
            _loginState.value = QBotLoginState.LOGIN_FAILED
            _loginErrorMessage.value = line.trim().take(200)
            _qrCodeData.value = null
            FileLogger.w(TAG, "登录失败: $line")
            return
        }
    }

    /**
     * 登录成功回调（由外部明确调用，如 OneBot API 返回在线状态）。
     */
    fun onLoggedIn(qq: Long, nickname: String) {
        _botQq.value = qq
        _botNickname.value = nickname
        _loginState.value = QBotLoginState.LOGGED_IN
        _qrCodeData.value = null
        _loginErrorMessage.value = ""
        FileLogger.i(TAG, "登录成功: QQ=$qq Nickname=$nickname")
    }

    /**
     * 清除当前二维码（用户已扫码后刷新页面，或手动隐藏）。
     */
    fun clearQrCode() {
        _qrCodeData.value = null
        FileLogger.i(TAG, "二维码已清除")
    }

    /**
     * 重置登录状态，准备重新登录（退出登录 / 重新获取二维码）。
     */
    fun resetForRelogin() {
        _qrCodeData.value = null
        _loginState.value = QBotLoginState.LOGGED_OUT
        _botQq.value = 0L
        _botNickname.value = ""
        _loginErrorMessage.value = ""
        FileLogger.i(TAG, "登录状态已重置，准备重新登录")
    }

    /**
     * 直接设置登录状态（外部状态同步用）。
     */
    fun setLoginState(state: QBotLoginState) {
        _loginState.value = state
        if (state == QBotLoginState.LOGGED_OUT || state == QBotLoginState.LOGIN_FAILED) {
            _qrCodeData.value = null
        }
    }

    /**
     * 提交设备锁短信验证码。
     * 提交后状态转为登录中，实际验证码传递由 LLBotProcessManager 通过容器通道处理。
     */
    fun submitSmsCode(code: String) {
        if (_loginState.value != QBotLoginState.SMS_CODE_REQUIRED) return
        if (code.isBlank()) return
        _loginState.value = QBotLoginState.LOGGING_IN
        _loginErrorMessage.value = ""
        FileLogger.i(TAG, "已提交短信验证码，等待登录结果")
    }
}
