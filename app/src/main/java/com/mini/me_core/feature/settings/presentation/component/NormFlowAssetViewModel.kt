package com.mini.me_core.feature.settings.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.agent.domain.prompt.AgentAsset
import com.mini.me_core.feature.agent.domain.prompt.AgentAssetRegistry
import com.mini.me_core.feature.agent.domain.sop.SopAsset
import com.mini.me_core.feature.agent.domain.sop.SopRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** P1：规范查看器 ViewModel（注入 AgentAssetRegistry / SopRegistry，只读展示）。 */
@HiltViewModel
class NormFlowAssetViewModel @Inject constructor(
    private val agentAssetRegistry: AgentAssetRegistry,
    private val sopRegistry: SopRegistry
) : ViewModel() {

    private val _staticRules = MutableStateFlow<List<AgentAsset>>(emptyList())
    val staticRules: StateFlow<List<AgentAsset>> = _staticRules.asStateFlow()

    private val _sopList = MutableStateFlow<List<SopAsset>>(emptyList())
    val sopList: StateFlow<List<SopAsset>> = _sopList.asStateFlow()

    fun load() {
        _staticRules.value = runCatching { agentAssetRegistry.components() }.getOrDefault(emptyList())
        _sopList.value = runCatching { sopRegistry.all() }.getOrDefault(emptyList())
    }
}
