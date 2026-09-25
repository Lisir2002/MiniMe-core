package com.mini.me_core.feature.agent.presentation.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 聊天界面规范指示器 ViewModel：总开关状态 + 7 项主规范启用统计 + 快速切换。 */
@HiltViewModel
class NormFlowIndicatorViewModel @Inject constructor(
    private val repository: NormFlowSettingsRepository
) : ViewModel() {

    val normFlowEnabled: StateFlow<Boolean> = repository.normFlowEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** 7 项主规范启用数量（步骤注入/工具护栏/推理预算/用量卡片/SOP摘要/剧本自动/空转收敛）。 */
    val enabledCount: StateFlow<Int> = combine(
        repository.stepInjectEnabledFlow,
        repository.toolGuardEnabledFlow,
        repository.reasoningBudgetEnabledFlow,
        repository.usageCardEnabledFlow,
        repository.sopSummaryEnabledFlow,
        repository.playbookAutoEnabledFlow,
        repository.idleConvergeEnabledFlow
    ) { values -> values.count { it } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 7)

    val stepInjectEnabled = repository.stepInjectEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val toolGuardEnabled = repository.toolGuardEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val reasoningBudgetEnabled = repository.reasoningBudgetEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val usageCardEnabled = repository.usageCardEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val sopSummaryEnabled = repository.sopSummaryEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val playbookAutoEnabled = repository.playbookAutoEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val idleConvergeEnabled = repository.idleConvergeEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleNormFlow() {
        viewModelScope.launch {
            repository.setNormFlowEnabled(!normFlowEnabled.value)
        }
    }

    companion object {
        const val TOTAL_MAIN_RULES = 7
    }
}
