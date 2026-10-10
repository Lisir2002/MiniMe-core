package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.feature.settings.data.repository.AppThemeMode
import com.mini.me_core.feature.settings.data.repository.ThemeSettingsRepository
import com.mini.me_core.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：主题域。
 * 设置即时生效后通过 [emitEffect] 向门面的 SettingEffect 通道投递 Applied 事件（语义等同原 tryEmit）。
 */
internal class ThemeSection(
    private val scope: CoroutineScope,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val emitEffect: (SettingsViewModel.SettingEffect) -> Unit,
) {
    private val _themeMode = MutableStateFlow(AppThemeMode.AUTO)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    init {
        scope.launch {
            themeSettingsRepository.themeModeFlow.collectLatest {
                _themeMode.value = it
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        scope.launch {
            themeSettingsRepository.setThemeMode(mode)
            emitEffect(SettingsViewModel.SettingEffect.Applied)
        }
    }
}
