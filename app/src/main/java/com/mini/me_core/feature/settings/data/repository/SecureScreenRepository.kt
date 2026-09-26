package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 防截图录屏保护范围。
 */
enum class SecureScreenScope(val key: String) {
    /** 仅模型供应商编辑页（含 API Key）。 */
    PROVIDER_EDITOR_ONLY("provider_editor_only"),

    /** 所有含敏感信息的页面。 */
    ALL_SENSITIVE_PAGES("all_sensitive_pages"),

    /** 全局（整个应用）。 */
    GLOBAL("global");

    companion object {
        val DEFAULT = PROVIDER_EDITOR_ONLY
        fun fromKey(key: String?): SecureScreenScope =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * 防截图录屏开关（KVStore）。
 *
 * 开启后，受保护页面设置 FLAG_SECURE，系统截图/录屏/最近任务缩略图均显示空白。
 *
 * Key：secure_screen_enabled → BOOLEAN（默认 true）
 *      secure_screen_scope   → STRING（默认 provider_editor_only）
 */
@Singleton
class SecureScreenRepository @Inject constructor(
    private val kv: KVStore
) {
    private companion object {
        const val NS = "settings"
        const val KEY_ENABLED = "secure_screen_enabled"
        const val KEY_SCOPE = "secure_screen_scope"
        val TAG = "SecureScreenRepo"
    }

    /** 响应式观察；未设置时默认 true。 */
    val enabledFlow: Flow<Boolean> = kv.observeBool(NS, KEY_ENABLED).map { it ?: true }

    /** 保护范围观察。 */
    val scopeFlow: Flow<SecureScreenScope> = kv.observeString(NS, KEY_SCOPE).map { SecureScreenScope.fromKey(it) }

    /** 同步读取（用于 Composable 首帧前的 DisposableEffect）。 */
    fun isEnabled(): Boolean = kv.getBool(NS, KEY_ENABLED) ?: true

    suspend fun setEnabled(enabled: Boolean) {
        kv.putBool(NS, KEY_ENABLED, enabled)
        FileLogger.i(TAG, "防截图录屏 → ${if (enabled) "开启" else "关闭"}")
    }

    suspend fun setScope(scope: SecureScreenScope) {
        kv.putString(NS, KEY_SCOPE, scope.key)
        FileLogger.i(TAG, "防截图范围 → ${scope.key}")
    }
}
