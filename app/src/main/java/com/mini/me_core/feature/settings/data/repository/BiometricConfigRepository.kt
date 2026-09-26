package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物识别保护范围（可多选）。
 */
enum class BiometricScope(val key: String) {
    /** SSH 密码 / 私钥。 */
    SSH_CREDENTIALS("biometric_scope_ssh"),

    /** API Key。 */
    API_KEY("biometric_scope_api_key"),

    /** 数据库加密操作。 */
    DB_ENCRYPTION_OP("biometric_scope_db_op"),

    /** 密钥轮换。 */
    KEY_ROTATION("biometric_scope_rotation");

    companion object {
        val DEFAULT: Set<BiometricScope> = setOf(SSH_CREDENTIALS, API_KEY)
    }
}

/**
 * 生物识别重新验证超时（分钟）。
 */
enum class BiometricTimeoutMinutes(val minutes: Int) {
    ONE_MIN(1),
    FIVE_MIN(5),
    FIFTEEN_MIN(15),
    THIRTY_MIN(30);

    companion object {
        val DEFAULT = FIVE_MIN
        fun fromMinutes(m: Int): BiometricTimeoutMinutes =
            entries.firstOrNull { it.minutes == m } ?: DEFAULT
    }
}

/**
 * 生物识别配置仓库（KVStore）：保护范围多选 + 超时单选。
 *
 * 与 [com.mini.me_core.core.security.CredentialEncryptor.setBiometricRequired] 的总开关分离：
 * 总开关决定是否要求生物识别，本仓库决定「保护哪些操作」与「多久后需重新验证」。
 */
@Singleton
class BiometricConfigRepository @Inject constructor(
    private val kv: KVStore,
) {
    private companion object {
        const val NS = "settings"
        const val KEY_SCOPE_PREFIX = "biometric_scope_"
        const val KEY_TIMEOUT_MIN = "biometric_timeout_min"
        val TAG = "BiometricConfigRepo"
    }

    /** 单个 scope 是否启用。 */
    private fun scopeFlow(scope: BiometricScope): Flow<Boolean> =
        kv.observeBool(NS, KEY_SCOPE_PREFIX + scope.key.removePrefix("biometric_scope_"))
            .map { it ?: BiometricScope.DEFAULT.contains(scope) }

    /** 当前选中的全部 scope。 */
    val scopesFlow: Flow<Set<BiometricScope>> =
        combine(BiometricScope.entries.map { scopeFlow(it) }) { flags ->
            BiometricScope.entries.filterIndexed { i, _ -> flags[i] }.toSet()
        }

    /** 当前超时（分钟）。 */
    val timeoutFlow: Flow<BiometricTimeoutMinutes> =
        kv.observeInt(NS, KEY_TIMEOUT_MIN).map { stored ->
            stored?.toInt()?.let { BiometricTimeoutMinutes.fromMinutes(it) } ?: BiometricTimeoutMinutes.DEFAULT
        }

    suspend fun setScopeEnabled(scope: BiometricScope, enabled: Boolean) {
        val key = KEY_SCOPE_PREFIX + scope.key.removePrefix("biometric_scope_")
        kv.putBool(NS, key, enabled)
        FileLogger.i(TAG, "生物识别范围 ${scope.name} → $enabled")
    }

    suspend fun setTimeout(timeout: BiometricTimeoutMinutes) {
        kv.putInt(NS, KEY_TIMEOUT_MIN, timeout.minutes.toLong())
        FileLogger.i(TAG, "生物识别超时 → ${timeout.minutes} 分钟")
    }
}
