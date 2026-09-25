package com.mini.me_core.feature.settings.presentation.component

import androidx.lifecycle.ViewModel
import com.mini.me_core.feature.agent.domain.rule.RuleAsset
import com.mini.me_core.feature.agent.domain.rule.RuleRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** P3：分层规则管理 ViewModel。 */
@HiltViewModel
class RuleManagerViewModel @Inject constructor(
    private val ruleRegistry: RuleRegistry
) : ViewModel() {

    private val _rules = MutableStateFlow<List<RuleAsset>>(emptyList())
    val rules: StateFlow<List<RuleAsset>> = _rules.asStateFlow()

    private val _disabledNames = MutableStateFlow<Set<String>>(emptySet())
    val disabledNames: StateFlow<Set<String>> = _disabledNames.asStateFlow()

    fun load(projectRoot: String) {
        _rules.value = ruleRegistry.allIncludingDisabled(projectRoot)
    }

    fun toggleRule(name: String, enabled: Boolean) {
        if (enabled) ruleRegistry.enableRule(name) else ruleRegistry.disableRule(name)
        _disabledNames.value = _disabledNames.value.toMutableSet().also { set ->
            if (enabled) set.remove(name) else set.add(name)
        }
    }
}
