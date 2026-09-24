package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.core.util.FileLogger
import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 防截图录屏开关（KVStore）。
 *
 * 开启后，模型供应商编辑页（含 API Key）设置 FLAG_SECURE，
 * 系统截图/录屏/最近任务缩略图均显示空白。
 *
 * Key：secure_screen_enabled → BOOLEAN
 * 默认：true（保持历史行为，敏感页面默认防截图）
 */
@Singleton
class SecureScreenRepository @Inject constructor(
    private val kv: KVStore
) {
    private companion object {
        const val NS = "settings"
        const val KEY = "secure_screen_enabled"
        val TAG = "SecureScreenRepo"
    }

    /** 响应式观察；未设置时默认 true。 */
    val enabledFlow: Flow<Boolean> = kv.observeBool(NS, KEY).map { it ?: true }

    /** 同步读取（用于 Composable 首帧前的 DisposableEffect）。 */
    fun isEnabled(): Boolean = kv.getBool(NS, KEY) ?: true

    suspend fun setEnabled(enabled: Boolean) {
        kv.putBool(NS, KEY, enabled)
        FileLogger.i(TAG, "防截图录屏 → ${if (enabled) "开启" else "关闭"}")
    }
}
