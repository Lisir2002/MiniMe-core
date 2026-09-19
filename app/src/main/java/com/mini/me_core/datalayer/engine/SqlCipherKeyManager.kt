package com.mini.me_core.datalayer.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SQLCipher 数据库主密钥管理（全盘加密，设计 §8 / §12.2）。
 *
 * 策略（与字段级 DEK 分离，不复用字段级密钥）：
 *  - 生成独立 32 字节随机 DB passphrase（Android 密码学安全随机源）；
 *  - 首启生成一次，后续读取；**禁止硬编码、禁止用户密码派生**；
 *  - 加密后的密钥材料存放于 EncryptedSharedPreferences（其主密钥由 Android Keystore 保管，
 *    与现有凭据/订阅敏感字段加密同模式）。
 *
 * 与字段级 [com.mini.me_core.core.security.CredentialEncryptor] 的 DEK 完全独立：
 * 即使字段级 DEK 轮换，本 DB 主密钥不变；反之亦然。
 */
class SqlCipherKeyManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            .let { masterKey ->
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }
    }

    /** 返回当前 DB 主密钥（首启生成并持久化）。32 字节随机。 */
    fun getOrCreateKey(): ByteArray {
        prefs.getString(KEY_PASSPHRASE, null)?.let { stored ->
            runCatching { return Base64.decode(stored, Base64.NO_WRAP) }
        }
        // 首启：生成 32 字节随机密钥。
        val raw = ByteArray(KEY_LENGTH).also { bytes ->
            java.security.SecureRandom().nextBytes(bytes)
        }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(raw, Base64.NO_WRAP))
            .apply()
        return raw
    }

    private companion object {
        const val PREFS_FILE = "mini_me_sqlcipher"
        const val KEY_PASSPHRASE = "db_passphrase_v1"
        const val KEY_LENGTH = 32
    }
}
