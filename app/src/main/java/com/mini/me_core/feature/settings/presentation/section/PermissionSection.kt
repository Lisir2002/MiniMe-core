package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRule
import com.mini.me_core.feature.agent.domain.execution.permission.PermissionRulesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * P2-17 行为保持搬迁：权限规则域（全局规则 / 当前项目规则 / 项目名快照）。
 * 逻辑原属 SettingsViewModel，逐字搬迁；协程仍由同一 viewModelScope 驱动。
 */
internal class PermissionSection(
    private val scope: CoroutineScope,
    private val permissionRulesRepository: PermissionRulesRepository,
) {
    private val _globalRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val globalRules: StateFlow<List<PermissionRule>> = _globalRules.asStateFlow()

    private val _projectRules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val projectRules: StateFlow<List<PermissionRule>> = _projectRules.asStateFlow()

    val currentProjectName: StateFlow<String?> = permissionRulesRepository.currentProjectNameFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            permissionRulesRepository.globalRulesFlow.collectLatest {
                _globalRules.value = it
            }
        }

        scope.launch {
            permissionRulesRepository.currentProjectRulesFlow.collectLatest {
                _projectRules.value = it
            }
        }
    }

    fun deleteGlobalRule(rule: PermissionRule) {
        scope.launch { permissionRulesRepository.removeGlobalRule(rule) }
    }

    fun deleteProjectRule(rule: PermissionRule) {
        val name = currentProjectName.value ?: return
        scope.launch { permissionRulesRepository.removeProjectRule(name, rule) }
    }

    fun promoteRuleToGlobal(rule: PermissionRule) {
        val name = currentProjectName.value ?: return
        scope.launch { permissionRulesRepository.promoteToGlobal(name, rule) }
    }
}
