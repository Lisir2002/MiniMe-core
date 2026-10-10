package com.mini.me_core.feature.settings.presentation.section

import com.mini.me_core.feature.settings.data.repository.NormFlowSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 *  行为保持搬迁：D1-7 规范流程域（总开关 / step_inject / tool_guard / 用量卡片 /
 * 推理预算 / SOP / Playbook 自动触发 / 空转收敛 / 文件观察 / P2 护栏独立开关 / 预设与导入导出）。
 * 全部状态与默认值原样搬迁；协程仍由同一 viewModelScope 驱动。
 */
@Suppress("TooManyFunctions") // 行为保持搬迁：规范流程域公开/内部方法全部原样保留
internal class NormFlowSection(
    private val scope: CoroutineScope,
    private val normFlowSettingsRepository: NormFlowSettingsRepository,
) {
    /** D1-7 规范流程统一开关：总开关 + step_inject/tool_guard 子开关（对齐 norm-chain §3.5）。 */
    private val _normFlowEnabled = MutableStateFlow(true)
    val normFlowEnabled: StateFlow<Boolean> = _normFlowEnabled.asStateFlow()

    private val _stepInjectEnabled = MutableStateFlow(true)
    val stepInjectEnabled: StateFlow<Boolean> = _stepInjectEnabled.asStateFlow()

    private val _toolGuardEnabled = MutableStateFlow(true)
    val toolGuardEnabled: StateFlow<Boolean> = _toolGuardEnabled.asStateFlow()

    // D2-2/D2-4 规范流程子开关：推理预算 / 用量卡片（默认开，保持现有行为）。
    private val _reasoningBudgetEnabled = MutableStateFlow(true)
    val reasoningBudgetEnabled: StateFlow<Boolean> = _reasoningBudgetEnabled.asStateFlow()

    private val _usageCardEnabled = MutableStateFlow(true)
    val usageCardEnabled: StateFlow<Boolean> = _usageCardEnabled.asStateFlow()

    // D4-3 SOP 清单摘要常驻注入子开关（默认开）。
    private val _sopSummaryEnabled = MutableStateFlow(true)
    val sopSummaryEnabled: StateFlow<Boolean> = _sopSummaryEnabled.asStateFlow()

    // D5-pa Playbook 自动触发子开关（默认开，对齐 §3.5）。
    private val _playbookAutoEnabled = MutableStateFlow(true)
    val playbookAutoEnabled: StateFlow<Boolean> = _playbookAutoEnabled.asStateFlow()

    // D2-1 空转软收敛子开关（默认关：研究/浏览类请求会被误伤收敛，按需开启）。
    private val _idleConvergeEnabled = MutableStateFlow(false)
    val idleConvergeEnabled: StateFlow<Boolean> = _idleConvergeEnabled.asStateFlow()

    // P0 step 前注入子开关（默认全开）
    private val _stepInjectGoalEnabled = MutableStateFlow(true)
    val stepInjectGoalEnabled: StateFlow<Boolean> = _stepInjectGoalEnabled.asStateFlow()

    private val _stepInjectStaticRulesEnabled = MutableStateFlow(true)
    val stepInjectStaticRulesEnabled: StateFlow<Boolean> = _stepInjectStaticRulesEnabled.asStateFlow()

    private val _stepInjectLayeredRulesEnabled = MutableStateFlow(true)
    val stepInjectLayeredRulesEnabled: StateFlow<Boolean> = _stepInjectLayeredRulesEnabled.asStateFlow()

    private val _stepInjectProjectAgentsEnabled = MutableStateFlow(true)
    val stepInjectProjectAgentsEnabled: StateFlow<Boolean> = _stepInjectProjectAgentsEnabled.asStateFlow()

    // 文件观察护栏独立开关（默认开）
    private val _fileObservationEnabled = MutableStateFlow(true)
    val fileObservationEnabled: StateFlow<Boolean> = _fileObservationEnabled.asStateFlow()

    // 推理预算强度（low/medium/high，默认 medium）
    private val _reasoningBudgetLevel = MutableStateFlow("medium")
    val reasoningBudgetLevel: StateFlow<String> = _reasoningBudgetLevel.asStateFlow()

    // 空转收敛阈值轮数（默认 6）
    private val _idleConvergeRounds = MutableStateFlow(6)
    val idleConvergeRounds: StateFlow<Int> = _idleConvergeRounds.asStateFlow()

    // 当前预设方案
    private val _activePreset = MutableStateFlow("standard")
    val activePreset: StateFlow<String> = _activePreset.asStateFlow()

    // 新增护栏独立开关
    private val _guardDangerousCommandEnabled = MutableStateFlow(false)
    val guardDangerousCommandEnabled: StateFlow<Boolean> = _guardDangerousCommandEnabled.asStateFlow()

    private val _guardLargeFileEnabled = MutableStateFlow(false)
    val guardLargeFileEnabled: StateFlow<Boolean> = _guardLargeFileEnabled.asStateFlow()

    private val _guardPathBoundaryEnabled = MutableStateFlow(false)
    val guardPathBoundaryEnabled: StateFlow<Boolean> = _guardPathBoundaryEnabled.asStateFlow()

    // 用量卡片显示项
    private val _usageCardItems = MutableStateFlow<Set<String>>(setOf("token"))
    val usageCardItems: StateFlow<Set<String>> = _usageCardItems.asStateFlow()

    init {
        scope.launch {
            normFlowSettingsRepository.normFlowEnabledFlow.collectLatest {
                _normFlowEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.stepInjectEnabledFlow.collectLatest {
                _stepInjectEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.toolGuardEnabledFlow.collectLatest {
                _toolGuardEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.reasoningBudgetEnabledFlow.collectLatest {
                _reasoningBudgetEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.usageCardEnabledFlow.collectLatest {
                _usageCardEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.sopSummaryEnabledFlow.collectLatest {
                _sopSummaryEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.playbookAutoEnabledFlow.collectLatest {
                _playbookAutoEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.idleConvergeEnabledFlow.collectLatest {
                _idleConvergeEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.stepInjectGoalEnabledFlow.collectLatest {
                _stepInjectGoalEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.stepInjectStaticRulesEnabledFlow.collectLatest {
                _stepInjectStaticRulesEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.stepInjectLayeredRulesEnabledFlow.collectLatest {
                _stepInjectLayeredRulesEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.stepInjectProjectAgentsEnabledFlow.collectLatest {
                _stepInjectProjectAgentsEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.fileObservationEnabledFlow.collectLatest {
                _fileObservationEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.reasoningBudgetLevelFlow.collectLatest {
                _reasoningBudgetLevel.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.idleConvergeRoundsFlow.collectLatest {
                _idleConvergeRounds.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.activePresetFlow.collectLatest {
                _activePreset.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.guardDangerousCommandEnabledFlow.collectLatest {
                _guardDangerousCommandEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.guardLargeFileEnabledFlow.collectLatest {
                _guardLargeFileEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.guardPathBoundaryEnabledFlow.collectLatest {
                _guardPathBoundaryEnabled.value = it
            }
        }

        scope.launch {
            normFlowSettingsRepository.usageCardItemsFlow.collectLatest {
                _usageCardItems.value = it
            }
        }
    }

    // D1-7 规范流程统一开关：总开关/子开关持久化（workflow 运行期读同一 repository）。
    // 用户手动调整后自动标记为 custom 预设。
    fun setNormFlowEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setNormFlowEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setStepInjectEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setStepInjectEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setToolGuardEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setToolGuardEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    // D2-2/D2-4 规范流程子开关持久化（workflow 运行期读同一 repository）。
    fun setReasoningBudgetEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setReasoningBudgetEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setUsageCardEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setUsageCardEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setSopSummaryEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setSopSummaryEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setPlaybookAutoEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setPlaybookAutoEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setIdleConvergeEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setIdleConvergeEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    // P0 step 前注入子开关持久化
    fun setStepInjectGoalEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setStepInjectGoalEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setStepInjectStaticRulesEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setStepInjectStaticRulesEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setStepInjectLayeredRulesEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setStepInjectLayeredRulesEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setStepInjectProjectAgentsEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setStepInjectProjectAgentsEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setFileObservationEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setFileObservationEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 推理预算强度
    fun setReasoningBudgetLevel(level: String) {
        scope.launch {
            normFlowSettingsRepository.setReasoningBudgetLevel(level)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 空转收敛阈值
    fun setIdleConvergeRounds(rounds: Int) {
        scope.launch {
            normFlowSettingsRepository.setIdleConvergeRounds(rounds)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 应用预设方案
    fun applyPreset(preset: String) {
        scope.launch {
            normFlowSettingsRepository.applyPreset(preset)
        }
    }

    // 新增护栏独立开关
    fun setGuardDangerousCommandEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setGuardDangerousCommandEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    fun setGuardLargeFileEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setGuardLargeFileEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 安全相关：路径边界开关——默认值/持久化键/读写时机全部原样搬迁，一字未改。
    fun setGuardPathBoundaryEnabled(enabled: Boolean) {
        scope.launch {
            normFlowSettingsRepository.setGuardPathBoundaryEnabled(enabled)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 用量卡片显示项（切换某个 item）
    fun toggleUsageCardItem(item: String) {
        scope.launch {
            val current = _usageCardItems.value
            val newItems = if (item in current) current - item else current + item
            normFlowSettingsRepository.setUsageCardItems(newItems)
            normFlowSettingsRepository.markCustom()
        }
    }

    // 导出配置
    fun exportConfig(): String = normFlowSettingsRepository.exportConfig()

    // 导入配置
    fun importConfig(json: String): Boolean = normFlowSettingsRepository.importConfig(json)
}
