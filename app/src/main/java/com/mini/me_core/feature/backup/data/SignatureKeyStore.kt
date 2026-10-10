package com.mini.me_core.feature.backup.data

import android.content.Context
import android.content.SharedPreferences
import com.mini.me_core.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动/公共备份的口令来源（安全口径：绝不从公开签名证书派生）。
 *
 * 历史问题：备份口令 = 应用签名证书 SHA-256 hex。签名证书随 APK 公开分发，任何拿到
 * APK 的人都能离线算出同一口令——公共目录里的加密备份对它等于明文。现已废弃。
 *
 * 新口径：
 *  - **自动备份口令**：首次需要时用 [SecureRandom] 生成 32 字节随机密钥，base64url
 *    编码后存于应用私有 SharedPreferences（MODE_PRIVATE，其他应用不可读）。它不来自
 *    APK 任何公开材料，换设备/重装都不可由 APK 派生；
 *  - **公共导出（用户显式导出到公共目录）**：必须由用户设置强口令（[validateUserPassphrase]
 *    走 MEDIUM 及以上强度校验），系统不再提供「自动口令」。
 */
@Singleton
class SignatureKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val TAG = "SignatureKeyStore"
        const val PREFS_NAME = "backup_auto_export"
        const val KEY_SECRET = "auto_export_secret"
    }

    private fun prefs(): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 自动备份加密口令：应用私有存储内的随机密钥（32 字节 SecureRandom）。
     * 与签名证书无关——APK 本身不含也无法派生出该值，两设备/两次安装互不相同。
     * 返回 null 仅在私有存储写入失败的极端情况（调用方应跳过外部备份，不降级为无口令）。
     */
    fun signaturePassword(): CharArray? {
        prefs().getString(KEY_SECRET, null)?.let { return it.toCharArray() }
        val secret = BackupPassphrasePolicy.randomSecret()
        return runCatching {
            prefs().edit().putString(KEY_SECRET, secret).commit()
            secret.toCharArray()
        }.onFailure {
            FileLogger.w(TAG, "自动备份随机口令落盘失败", it)
        }.getOrNull()
    }

    /**
     * 用户公共导出口令强度校验：MEDIUM 起放行（长度 >=8 且字母+数字）。
     * 弱口令在 UI 层直接拒绝，不进入导出路径。
     */
    fun validateUserPassphrase(password: CharArray): Boolean =
        BackupPassphrasePolicy.isAcceptableUserPassphrase(password.concatToString())

    /** 纯函数：生成一次性随机密钥（base64url，不含填充）。 */
    fun generateRandomSecret(): String = BackupPassphrasePolicy.randomSecret()
}
