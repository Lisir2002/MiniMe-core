package com.mini.me_core.feature.settings.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.agent.domain.prompt.SystemPromptProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** P2：注入诊断面板 ViewModel，从 SystemPromptProvider 读取最近一次 step 注入诊断。 */
@HiltViewModel
class NormFlowDiagnosisViewModel @Inject constructor(
    private val systemPromptProvider: SystemPromptProvider
) : ViewModel() {

    private val _diagnosisList = MutableStateFlow<List<SystemPromptProvider.SourceDiagnosis>>(emptyList())
    val diagnosisList: StateFlow<List<SystemPromptProvider.SourceDiagnosis>> = _diagnosisList.asStateFlow()

    private val _injectionContent = MutableStateFlow<String?>(null)
    val injectionContent: StateFlow<String?> = _injectionContent.asStateFlow()

    private val _budgetUsed = MutableStateFlow(0)
    val budgetUsed: StateFlow<Int> = _budgetUsed.asStateFlow()

    fun refresh() {
        _diagnosisList.value = systemPromptProvider.getLastInjectionDiagnosis()
        _injectionContent.value = systemPromptProvider.getLastInjectionContent()
        _budgetUsed.value = systemPromptProvider.getLastBudgetUsed()
    }
}
