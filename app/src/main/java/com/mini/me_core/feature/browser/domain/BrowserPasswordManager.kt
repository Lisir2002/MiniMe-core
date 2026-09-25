package com.mini.me_core.feature.browser.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 密码强度等级（F4.3）。 */
enum class PasswordStrength {
    WEAK, MEDIUM, STRONG;

    companion object {
        fun evaluate(password: String): PasswordStrength {
            if (password.length < 8) return WEAK
            val hasLower = password.any { it.isLowerCase() }
            val hasUpper = password.any { it.isUpperCase() }
            val hasDigit = password.any { it.isDigit() }
            val hasSymbol = password.any { !it.isLetterOrDigit() }
            val variety = listOf(hasLower || hasUpper, hasDigit, hasSymbol).count { it }
            return when {
                password.length >= 12 && hasUpper && hasDigit && hasSymbol -> STRONG
                password.length >= 8 && variety >= 2 -> MEDIUM
                else -> WEAK
            }
        }
    }
}

/** 一条已保存密码（F4.3，供密码管理 UI）。 */
data class SavedPassword(
    val host: String,
    val username: String,
    val password: String,
    val strength: PasswordStrength
)

/**
 * F4.3 密码管理器：在 [BrowserCredentialStore]（Android Keystore AES-GCM 加密）之上提供
 * 密码列表、强度评估、复制、删除与访问解锁门。
 *
 * 主密码保护：进入密码列表前需解锁（[locked] = true 时 UI 隐藏明文）。
 * 这里采用会话级解锁（应用退到后台后由调用方调用 [lock] 重新锁定）。
 */
@Singleton
class BrowserPasswordManager @Inject constructor(
    private val credentialStore: BrowserCredentialStore
) {
    private val _locked = MutableStateFlow(true)
    /** 密码库是否处于锁定状态（true 时不得展示明文密码）。 */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** 解锁密码库（生物识别 / 主密码验证通过后调用）。 */
    fun unlock() { _locked.value = false }

    /** 重新锁定（应用退到后台 / 手动锁定）。 */
    fun lock() { _locked.value = true }

    /** 列出全部已保存密码（仅在解锁后调用方应展示明文）。 */
    fun all(): List<SavedPassword> = credentialStore.hosts().mapNotNull { host ->
        val cred = credentialStore.find(host) ?: return@mapNotNull null
        SavedPassword(
            host = host,
            username = cred.username,
            password = cred.password,
            strength = PasswordStrength.evaluate(cred.password)
        )
    }

    /** 保存 / 更新一条密码。 */
    fun save(host: String, username: String, password: String) =
        credentialStore.save(host, username, password)

    /** 删除一条密码。 */
    fun delete(host: String) = credentialStore.delete(host)

    /** 按 host 查找自动填充凭据。 */
    fun autofillFor(host: String): BrowserCredential? = credentialStore.find(host)
}
