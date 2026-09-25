package com.mini.me_core.feature.agent.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/** P3：聊天界面规范指示器 ViewModel。 */
@HiltViewModel
class NormFlowIndicatorViewModel @Inject constructor(
    repository: NormFlowSettingsRepository
) : ViewModel() {
    val normFlowEnabled: StateFlow<Boolean> = repository.normFlowEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
}
