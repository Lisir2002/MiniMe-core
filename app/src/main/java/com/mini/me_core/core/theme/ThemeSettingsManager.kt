package com.mini.me_core.core.theme

import android.util.Log
import com.mini.me_core.datalayer.store.KVStore
import com.mini.me_core.core.theme.tokens.AppThemeState
import com.mini.me_core.core.theme.tokens.SemanticColors
import com.mini.me_core.core.theme.tokens.ThemeMode
import com.mini.me_core.core.theme.tokens.ThemePreset
import com.mini.me_core.core.theme.tokens.ThemePresets
import com.mini.me_core.core.theme.tokens.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题设置管理器（Phase 4 第一期）。
 *
 * 负责：
 * 1. 从 KVStore 加载/持久化用户主题设置（外观模式 + 预设选择）
 * 2. 对外暴露 [settings] StateFlow，UI 层订阅后实时重组
 * 3. 根据当前设置 + 暗色标志，计算最终的 [SemanticColors]
 * 4. 同步写入旧版 theme_mode 键，保持与现有 ThemeSettingsRepository 兼容
 *
 * 第一期不支持颜色自定义覆盖（Phase 5 实现）。
 */
@Singleton
class ThemeSettingsManager @Inject constructor(
    private val kv: KVStore,
) {
    companion object {
        private const val TAG = "ThemeSettingsManager"
        private const val NS = "theme_settings"
        private const val KEY_SETTINGS = "current"

        // 兼容旧版 ThemeSettingsRepository 的键
        private const val LEGACY_NS = "settings"
        private const val LEGACY_THEME_MODE_KEY = "theme_mode"
    }

    private val _settings = MutableStateFlow(loadFromKv())
    val settings: StateFlow<ThemeSettings> = _settings.asStateFlow()

    /**
     * 根据暗色标志和当前设置，计算应使用的 SemanticColors。
     * 由 Composable 层在重组时调用。
     */
    fun resolveColors(isDark: Boolean): SemanticColors {
        val preset = ThemePresets.byId(_settings.value.presetId)
        return if (isDark) preset.darkColors else preset.lightColors
    }

    /**
     * 根据暗色标志和当前设置，计算完整的 AppThemeState。
     */
    fun resolveThemeState(isDark: Boolean): AppThemeState {
        return AppThemeState(
            colors = resolveColors(isDark),
            isDark = isDark,
        )
    }

    /** 切换外观模式（AUTO / LIGHT / DARK）。 */
    suspend fun setMode(mode: ThemeMode) {
        val updated = _settings.value.copy(mode = mode.name)
        persist(updated)
    }

    /** 切换主题预设。 */
    suspend fun setPreset(presetId: String) {
        val updated = _settings.value.copy(presetId = presetId)
        persist(updated)
    }

    /** 恢复默认设置（默认预设 + 跟随系统）。 */
    suspend fun resetToDefaults() {
        persist(ThemeSettings.DEFAULT)
    }

    // ── 内部 ──

    private fun loadFromKv(): ThemeSettings {
        val raw = kv.getString(NS, KEY_SETTINGS)
        val parsed = ThemeSettings.fromJson(raw)
        Log.d(TAG, "Loaded theme settings: mode=${parsed.mode}, preset=${parsed.presetId}")
        return parsed
    }

    private suspend fun persist(newSettings: ThemeSettings) {
        // 写入新格式（完整 JSON）
        kv.putJson(NS, KEY_SETTINGS, ThemeSettings.toJson(newSettings))
        // 同步写入旧版 theme_mode 键，保持与现有 ThemeSettingsRepository 兼容
        kv.putString(LEGACY_NS, LEGACY_THEME_MODE_KEY, newSettings.mode)
        _settings.update { newSettings }
        Log.d(TAG, "Saved theme settings: mode=${newSettings.mode}, preset=${newSettings.presetId}")
    }
}
