package com.mini.me_core.feature.settings.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.agent.domain.core.rule.RuleAsset
import com.mini.me_core.feature.agent.domain.core.rule.RuleRegistry
import com.mini.me_core.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** 分层规则管理 ViewModel。 */
@HiltViewModel
class RuleManagerViewModel @Inject constructor(
    private val ruleRegistry: RuleRegistry,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _rules = MutableStateFlow<List<RuleAsset>>(emptyList())
    val rules: StateFlow<List<RuleAsset>> = _rules.asStateFlow()

    private val _disabledNames = MutableStateFlow<Set<String>>(emptySet())
    val disabledNames: StateFlow<Set<String>> = _disabledNames.asStateFlow()

    private val _currentProjectRoot = MutableStateFlow("")
    val currentProjectRoot: StateFlow<String> = _currentProjectRoot.asStateFlow()

    /** 自动读取当前工作区路径并加载规则；未选择工作区时 projectRoot 为空。 */
    fun loadCurrent() {
        val root = workspaceRepository.current.value?.path ?: ""
        _currentProjectRoot.value = root
        _rules.value = ruleRegistry.allIncludingDisabled(root)
    }

    fun load(projectRoot: String) {
        _currentProjectRoot.value = projectRoot
        _rules.value = ruleRegistry.allIncludingDisabled(projectRoot)
    }

    fun toggleRule(name: String, enabled: Boolean) {
        if (enabled) ruleRegistry.enableRule(name) else ruleRegistry.disableRule(name)
        _disabledNames.value = _disabledNames.value.toMutableSet().also { set ->
            if (enabled) set.remove(name) else set.add(name)
        }
    }
}
