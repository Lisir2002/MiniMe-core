package com.mini.me_core.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.core.theme.ThemeSettingsManager
import com.mini.me_core.core.theme.tokens.ThemeMode
import com.mini.me_core.core.theme.tokens.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主题设置页 ViewModel（Phase 4 第一期）。
 *
 * 桥接 UI 层和 [ThemeSettingsManager]，提供 StateFlow 供 Composable 订阅。
 * 不修改任何现有 ViewModel，仅作为新增文件。
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val themeManager: ThemeSettingsManager,
) : ViewModel() {

    /** 当前主题设置（模式 + 预设 ID）。 */
    val settings: StateFlow<ThemeSettings> = themeManager.settings

    /** 切换外观模式。 */
    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { themeManager.setMode(mode) }
    }

    /** 切换主题预设。 */
    fun setPreset(presetId: String) {
        viewModelScope.launch { themeManager.setPreset(presetId) }
    }

    /** 恢复默认。 */
    fun resetToDefaults() {
        viewModelScope.launch { themeManager.resetToDefaults() }
    }
}
