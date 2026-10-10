package com.mini.me_core.feature.backup.data

import com.mini.me_core.feature.backup.domain.evaluatePasswordStrength
import com.mini.me_core.feature.backup.domain.PasswordStrength
import java.security.SecureRandom

/**
 * 备份口令策略的纯逻辑（无 Android 依赖，可单测）。
 *
 * - 自动备份口令：[randomSecret] 由 [SecureRandom] 生成，不来自 APK 任何公开材料
 *   （签名证书/包名/版本号都不参与），因此「两设备/两次安装」互不相同，也不可由 APK 派生；
 * - 用户公共导出口令：[isAcceptableUserPassphrase] 要求 MEDIUM 起（长度 >=8 且字母+数字）。
 */
object BackupPassphrasePolicy {

    const val SECRET_BYTES = 32

    fun randomSecret(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(SECRET_BYTES).also { random.nextBytes(it) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isAcceptableUserPassphrase(password: String): Boolean {
        if (password.isEmpty()) return false
        return evaluatePasswordStrength(password) != PasswordStrength.WEAK
    }
}
