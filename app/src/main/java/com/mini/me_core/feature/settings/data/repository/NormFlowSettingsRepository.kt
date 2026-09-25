package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**「规范流程」运行时机制统一开关。 */
@Singleton
class NormFlowSettingsRepository @Inject constructor(
    private val kv: KVStore
) {
    private companion object {
        const val NS = "settings"
        const val NORM_FLOW_ENABLED_KEY = "norm_flow_enabled"
        const val STEP_INJECT_ENABLED_KEY = "step_inject_enabled"
        const val TOOL_GUARD_ENABLED_KEY = "tool_guard_enabled"
        const val REASONING_BUDGET_ENABLED_KEY = "reasoning_budget_enabled"
        const val USAGE_CARD_ENABLED_KEY = "usage_card_enabled"
        const val SOP_SUMMARY_ENABLED_KEY = "sop_summary_enabled"
        const val PLAYBOOK_AUTO_ENABLED_KEY = "playbook_auto_enabled"
        const val IDLE_CONVERGE_ENABLED_KEY = "idle_converge_enabled"
        // step 前注入子开关（P0 拆分）
        const val STEP_INJECT_GOAL_ENABLED_KEY = "step_inject_goal_enabled"
        const val STEP_INJECT_STATIC_RULES_ENABLED_KEY = "step_inject_static_rules_enabled"
        const val STEP_INJECT_LAYERED_RULES_ENABLED_KEY = "step_inject_layered_rules_enabled"
        const val STEP_INJECT_PROJECT_AGENTS_ENABLED_KEY = "step_inject_project_agents_enabled"
        // 文件观察护栏独立开关
        const val FILE_OBSERVATION_ENABLED_KEY = "file_observation_enabled"
        // P1：推理预算强度（low/medium/high）
        const val REASONING_BUDGET_LEVEL_KEY = "reasoning_budget_level"
        // P1：空转收敛阈值轮数
        const val IDLE_CONVERGE_ROUNDS_KEY = "idle_converge_rounds"
        // P1：当前预设方案
        const val ACTIVE_PRESET_KEY = "active_preset"
        // P2：新增护栏独立开关（默认关）
        const val GUARD_DANGEROUS_COMMAND_ENABLED_KEY = "guard_dangerous_command_enabled"
        const val GUARD_LARGE_FILE_ENABLED_KEY = "guard_large_file_enabled"
        const val GUARD_PATH_BOUNDARY_ENABLED_KEY = "guard_path_boundary_enabled"
        // P2：用量卡片显示项（逗号分隔，默认仅 token）
        const val USAGE_CARD_ITEMS_KEY = "usage_card_items"
    }

    val normFlowEnabledFlow: Flow<Boolean> = kv.observeBool(NS, NORM_FLOW_ENABLED_KEY).map { it ?: true }
    val stepInjectEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_ENABLED_KEY).map { it ?: true }
    val toolGuardEnabledFlow: Flow<Boolean> = kv.observeBool(NS, TOOL_GUARD_ENABLED_KEY).map { it ?: true }
    val reasoningBudgetEnabledFlow: Flow<Boolean> = kv.observeBool(NS, REASONING_BUDGET_ENABLED_KEY).map { it ?: true }
    val usageCardEnabledFlow: Flow<Boolean> = kv.observeBool(NS, USAGE_CARD_ENABLED_KEY).map { it ?: true }
    val sopSummaryEnabledFlow: Flow<Boolean> = kv.observeBool(NS, SOP_SUMMARY_ENABLED_KEY).map { it ?: true }
    val playbookAutoEnabledFlow: Flow<Boolean> = kv.observeBool(NS, PLAYBOOK_AUTO_ENABLED_KEY).map { it ?: true }
    val idleConvergeEnabledFlow: Flow<Boolean> = kv.observeBool(NS, IDLE_CONVERGE_ENABLED_KEY).map { it ?: false }
    // step 前注入子开关（P0 拆分，默认全开）
    val stepInjectGoalEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_GOAL_ENABLED_KEY).map { it ?: true }
    val stepInjectStaticRulesEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_STATIC_RULES_ENABLED_KEY).map { it ?: true }
    val stepInjectLayeredRulesEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_LAYERED_RULES_ENABLED_KEY).map { it ?: true }
    val stepInjectProjectAgentsEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_PROJECT_AGENTS_ENABLED_KEY).map { it ?: true }
    // 文件观察护栏独立开关（默认开）
    val fileObservationEnabledFlow: Flow<Boolean> = kv.observeBool(NS, FILE_OBSERVATION_ENABLED_KEY).map { it ?: true }
    // P1：推理预算强度（默认 medium）
    val reasoningBudgetLevelFlow: Flow<String> = kv.observeString(NS, REASONING_BUDGET_LEVEL_KEY).map { it ?: "medium" }
    // P1：空转收敛阈值（默认 6 轮）
    val idleConvergeRoundsFlow: Flow<Int> = kv.observeInt(NS, IDLE_CONVERGE_ROUNDS_KEY).map { it?.toInt() ?: 6 }
    // P1：当前预设方案（默认 standard）
    val activePresetFlow: Flow<String> = kv.observeString(NS, ACTIVE_PRESET_KEY).map { it ?: "standard" }
    // P2：新增护栏独立开关（默认关）
    val guardDangerousCommandEnabledFlow: Flow<Boolean> = kv.observeBool(NS, GUARD_DANGEROUS_COMMAND_ENABLED_KEY).map { it ?: false }
    val guardLargeFileEnabledFlow: Flow<Boolean> = kv.observeBool(NS, GUARD_LARGE_FILE_ENABLED_KEY).map { it ?: false }
    val guardPathBoundaryEnabledFlow: Flow<Boolean> = kv.observeBool(NS, GUARD_PATH_BOUNDARY_ENABLED_KEY).map { it ?: false }
    // P2：用量卡片显示项（默认仅 token）
    val usageCardItemsFlow: Flow<Set<String>> = kv.observeString(NS, USAGE_CARD_ITEMS_KEY).map { raw ->
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: setOf("token")
    }

    suspend fun setNormFlowEnabled(enabled: Boolean) { kv.putBool(NS, NORM_FLOW_ENABLED_KEY, enabled) }
    suspend fun setStepInjectEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_ENABLED_KEY, enabled) }
    suspend fun setToolGuardEnabled(enabled: Boolean) { kv.putBool(NS, TOOL_GUARD_ENABLED_KEY, enabled) }
    suspend fun setReasoningBudgetEnabled(enabled: Boolean) { kv.putBool(NS, REASONING_BUDGET_ENABLED_KEY, enabled) }
    suspend fun setUsageCardEnabled(enabled: Boolean) { kv.putBool(NS, USAGE_CARD_ENABLED_KEY, enabled) }
    suspend fun setSopSummaryEnabled(enabled: Boolean) { kv.putBool(NS, SOP_SUMMARY_ENABLED_KEY, enabled) }
    suspend fun setPlaybookAutoEnabled(enabled: Boolean) { kv.putBool(NS, PLAYBOOK_AUTO_ENABLED_KEY, enabled) }
    suspend fun setIdleConvergeEnabled(enabled: Boolean) { kv.putBool(NS, IDLE_CONVERGE_ENABLED_KEY, enabled) }
    suspend fun setStepInjectGoalEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_GOAL_ENABLED_KEY, enabled) }
    suspend fun setStepInjectStaticRulesEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_STATIC_RULES_ENABLED_KEY, enabled) }
    suspend fun setStepInjectLayeredRulesEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_LAYERED_RULES_ENABLED_KEY, enabled) }
    suspend fun setStepInjectProjectAgentsEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_PROJECT_AGENTS_ENABLED_KEY, enabled) }
    suspend fun setFileObservationEnabled(enabled: Boolean) { kv.putBool(NS, FILE_OBSERVATION_ENABLED_KEY, enabled) }
    // P2：新增护栏独立开关
    suspend fun setGuardDangerousCommandEnabled(enabled: Boolean) { kv.putBool(NS, GUARD_DANGEROUS_COMMAND_ENABLED_KEY, enabled) }
    suspend fun setGuardLargeFileEnabled(enabled: Boolean) { kv.putBool(NS, GUARD_LARGE_FILE_ENABLED_KEY, enabled) }
    suspend fun setGuardPathBoundaryEnabled(enabled: Boolean) { kv.putBool(NS, GUARD_PATH_BOUNDARY_ENABLED_KEY, enabled) }
    // P2：用量卡片显示项
    suspend fun setUsageCardItems(items: Set<String>) { kv.putString(NS, USAGE_CARD_ITEMS_KEY, items.joinToString(",")) }
    suspend fun getUsageCardItems(): Set<String> = usageCardItemsFlow.first()
    // P1：推理预算强度
    suspend fun setReasoningBudgetLevel(level: String) { kv.putString(NS, REASONING_BUDGET_LEVEL_KEY, level) }
    suspend fun getReasoningBudgetLevel(): String = reasoningBudgetLevelFlow.first()
    // P1：空转收敛阈值
    suspend fun setIdleConvergeRounds(rounds: Int) { kv.putInt(NS, IDLE_CONVERGE_ROUNDS_KEY, rounds.toLong()) }
    suspend fun getIdleConvergeRounds(): Int = idleConvergeRoundsFlow.first()
    // P1：预设方案
    suspend fun setActivePreset(preset: String) { kv.putString(NS, ACTIVE_PRESET_KEY, preset) }
    suspend fun getActivePreset(): String = activePresetFlow.first()

    /** P1：用户手动调整任意开关后调用，将预设标记为 custom。 */
    suspend fun markCustom() { kv.putString(NS, ACTIVE_PRESET_KEY, "custom") }

    /** P1：批量应用预设方案。 */
    suspend fun applyPreset(preset: String) {
        when (preset) {
            "strict" -> {
                setNormFlowEnabled(true)
                setStepInjectEnabled(true)
                setStepInjectGoalEnabled(true)
                setStepInjectStaticRulesEnabled(true)
                setSopSummaryEnabled(true)
                setStepInjectLayeredRulesEnabled(true)
                setStepInjectProjectAgentsEnabled(true)
                setToolGuardEnabled(true)
                setFileObservationEnabled(true)
                setGuardDangerousCommandEnabled(true)
                setGuardLargeFileEnabled(true)
                setGuardPathBoundaryEnabled(true)
                setReasoningBudgetEnabled(true)
                setReasoningBudgetLevel("high")
                setUsageCardEnabled(true)
                setIdleConvergeEnabled(true)
                setIdleConvergeRounds(3)
                setPlaybookAutoEnabled(true)
            }
            "standard" -> {
                setNormFlowEnabled(true)
                setStepInjectEnabled(true)
                setStepInjectGoalEnabled(true)
                setStepInjectStaticRulesEnabled(true)
                setSopSummaryEnabled(true)
                setStepInjectLayeredRulesEnabled(true)
                setStepInjectProjectAgentsEnabled(true)
                setToolGuardEnabled(true)
                setFileObservationEnabled(true)
                setGuardDangerousCommandEnabled(false)
                setGuardLargeFileEnabled(false)
                setGuardPathBoundaryEnabled(false)
                setReasoningBudgetEnabled(true)
                setReasoningBudgetLevel("medium")
                setUsageCardEnabled(true)
                setIdleConvergeEnabled(false)
                setIdleConvergeRounds(6)
                setPlaybookAutoEnabled(true)
            }
            "minimal" -> {
                setNormFlowEnabled(true)
                setStepInjectEnabled(true)
                setStepInjectGoalEnabled(false)
                setStepInjectStaticRulesEnabled(true)
                setSopSummaryEnabled(false)
                setStepInjectLayeredRulesEnabled(false)
                setStepInjectProjectAgentsEnabled(false)
                setToolGuardEnabled(false)
                setFileObservationEnabled(false)
                setGuardDangerousCommandEnabled(false)
                setGuardLargeFileEnabled(false)
                setGuardPathBoundaryEnabled(false)
                setReasoningBudgetEnabled(true)
                setReasoningBudgetLevel("low")
                setUsageCardEnabled(true)
                setIdleConvergeEnabled(false)
                setIdleConvergeRounds(6)
                setPlaybookAutoEnabled(true)
            }
            // custom 不做批量写入
        }
        kv.putString(NS, ACTIVE_PRESET_KEY, preset)
    }

    suspend fun isStepInjectActive(): Boolean = normFlowEnabledFlow.first() && stepInjectEnabledFlow.first()
    suspend fun isToolGuardActive(): Boolean = normFlowEnabledFlow.first() && toolGuardEnabledFlow.first()
    suspend fun isReasoningBudgetActive(): Boolean = normFlowEnabledFlow.first() && reasoningBudgetEnabledFlow.first()
    suspend fun isUsageCardActive(): Boolean = normFlowEnabledFlow.first() && usageCardEnabledFlow.first()
    suspend fun isSopSummaryActive(): Boolean = normFlowEnabledFlow.first() && sopSummaryEnabledFlow.first()
    suspend fun isPlaybookAutoActive(): Boolean = normFlowEnabledFlow.first() && playbookAutoEnabledFlow.first()
    suspend fun isIdleConvergeActive(): Boolean = normFlowEnabledFlow.first() && idleConvergeEnabledFlow.first()
    // step 前注入子开关（总开关 && step注入总开关 && 子开关）
    suspend fun isStepInjectGoalActive(): Boolean = isStepInjectActive() && stepInjectGoalEnabledFlow.first()
    suspend fun isStepInjectStaticRulesActive(): Boolean = normFlowEnabledFlow.first() && stepInjectStaticRulesEnabledFlow.first()
    suspend fun isStepInjectLayeredRulesActive(): Boolean = isStepInjectActive() && stepInjectLayeredRulesEnabledFlow.first()
    suspend fun isStepInjectProjectAgentsActive(): Boolean = normFlowEnabledFlow.first() && stepInjectProjectAgentsEnabledFlow.first()
    suspend fun isFileObservationActive(): Boolean = isToolGuardActive() && fileObservationEnabledFlow.first()
    // P2：新增护栏 active 判断（总开关 && tool_guard && 自身开关）
    suspend fun isGuardDangerousCommandActive(): Boolean = isToolGuardActive() && guardDangerousCommandEnabledFlow.first()
    suspend fun isGuardLargeFileActive(): Boolean = isToolGuardActive() && guardLargeFileEnabledFlow.first()
    suspend fun isGuardPathBoundaryActive(): Boolean = isToolGuardActive() && guardPathBoundaryEnabledFlow.first()

    // P3：配置导出/导入（手动 JSON 构造）
    fun exportConfig(): String {
        val sb = StringBuilder("{")
        val entries = mutableListOf<String>()
        kv.getBool(NS, NORM_FLOW_ENABLED_KEY)?.let { entries.add("\"norm_flow_enabled\":$it") }
        kv.getBool(NS, STEP_INJECT_ENABLED_KEY)?.let { entries.add("\"step_inject_enabled\":$it") }
        kv.getBool(NS, TOOL_GUARD_ENABLED_KEY)?.let { entries.add("\"tool_guard_enabled\":$it") }
        kv.getBool(NS, STEP_INJECT_GOAL_ENABLED_KEY)?.let { entries.add("\"step_inject_goal_enabled\":$it") }
        kv.getBool(NS, STEP_INJECT_STATIC_RULES_ENABLED_KEY)?.let { entries.add("\"step_inject_static_rules_enabled\":$it") }
        kv.getBool(NS, SOP_SUMMARY_ENABLED_KEY)?.let { entries.add("\"sop_summary_enabled\":$it") }
        kv.getBool(NS, STEP_INJECT_LAYERED_RULES_ENABLED_KEY)?.let { entries.add("\"step_inject_layered_rules_enabled\":$it") }
        kv.getBool(NS, STEP_INJECT_PROJECT_AGENTS_ENABLED_KEY)?.let { entries.add("\"step_inject_project_agents_enabled\":$it") }
        kv.getBool(NS, FILE_OBSERVATION_ENABLED_KEY)?.let { entries.add("\"file_observation_enabled\":$it") }
        kv.getBool(NS, GUARD_DANGEROUS_COMMAND_ENABLED_KEY)?.let { entries.add("\"guard_dangerous_command_enabled\":$it") }
        kv.getBool(NS, GUARD_LARGE_FILE_ENABLED_KEY)?.let { entries.add("\"guard_large_file_enabled\":$it") }
        kv.getBool(NS, GUARD_PATH_BOUNDARY_ENABLED_KEY)?.let { entries.add("\"guard_path_boundary_enabled\":$it") }
        kv.getBool(NS, REASONING_BUDGET_ENABLED_KEY)?.let { entries.add("\"reasoning_budget_enabled\":$it") }
        kv.getString(NS, REASONING_BUDGET_LEVEL_KEY)?.let { entries.add("\"reasoning_budget_level\":\"$it\"") }
        kv.getBool(NS, USAGE_CARD_ENABLED_KEY)?.let { entries.add("\"usage_card_enabled\":$it") }
        kv.getString(NS, USAGE_CARD_ITEMS_KEY)?.let { entries.add("\"usage_card_items\":\"$it\"") }
        kv.getBool(NS, IDLE_CONVERGE_ENABLED_KEY)?.let { entries.add("\"idle_converge_enabled\":$it") }
        kv.getInt(NS, IDLE_CONVERGE_ROUNDS_KEY)?.let { entries.add("\"idle_converge_rounds\":$it") }
        kv.getBool(NS, PLAYBOOK_AUTO_ENABLED_KEY)?.let { entries.add("\"playbook_auto_enabled\":$it") }
        kv.getString(NS, ACTIVE_PRESET_KEY)?.let { entries.add("\"active_preset\":\"$it\"") }
        sb.append(entries.joinToString(","))
        sb.append("}")
        return sb.toString()
    }

    fun importConfig(json: String): Boolean {
        return try {
            val cleaned = json.trim().removePrefix("{").removeSuffix("}")
            cleaned.split(",").forEach { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().removePrefix("\"").removeSuffix("\"")
                    val value = parts[1].trim()
                    when (key) {
                        "norm_flow_enabled" -> kv.putBool(NS, NORM_FLOW_ENABLED_KEY, value.toBoolean())
                        "step_inject_enabled" -> kv.putBool(NS, STEP_INJECT_ENABLED_KEY, value.toBoolean())
                        "tool_guard_enabled" -> kv.putBool(NS, TOOL_GUARD_ENABLED_KEY, value.toBoolean())
                        "step_inject_goal_enabled" -> kv.putBool(NS, STEP_INJECT_GOAL_ENABLED_KEY, value.toBoolean())
                        "step_inject_static_rules_enabled" -> kv.putBool(NS, STEP_INJECT_STATIC_RULES_ENABLED_KEY, value.toBoolean())
                        "sop_summary_enabled" -> kv.putBool(NS, SOP_SUMMARY_ENABLED_KEY, value.toBoolean())
                        "step_inject_layered_rules_enabled" -> kv.putBool(NS, STEP_INJECT_LAYERED_RULES_ENABLED_KEY, value.toBoolean())
                        "step_inject_project_agents_enabled" -> kv.putBool(NS, STEP_INJECT_PROJECT_AGENTS_ENABLED_KEY, value.toBoolean())
                        "file_observation_enabled" -> kv.putBool(NS, FILE_OBSERVATION_ENABLED_KEY, value.toBoolean())
                        "guard_dangerous_command_enabled" -> kv.putBool(NS, GUARD_DANGEROUS_COMMAND_ENABLED_KEY, value.toBoolean())
                        "guard_large_file_enabled" -> kv.putBool(NS, GUARD_LARGE_FILE_ENABLED_KEY, value.toBoolean())
                        "guard_path_boundary_enabled" -> kv.putBool(NS, GUARD_PATH_BOUNDARY_ENABLED_KEY, value.toBoolean())
                        "reasoning_budget_enabled" -> kv.putBool(NS, REASONING_BUDGET_ENABLED_KEY, value.toBoolean())
                        "reasoning_budget_level" -> kv.putString(NS, REASONING_BUDGET_LEVEL_KEY, value.removePrefix("\"").removeSuffix("\""))
                        "usage_card_enabled" -> kv.putBool(NS, USAGE_CARD_ENABLED_KEY, value.toBoolean())
                        "usage_card_items" -> kv.putString(NS, USAGE_CARD_ITEMS_KEY, value.removePrefix("\"").removeSuffix("\""))
                        "idle_converge_enabled" -> kv.putBool(NS, IDLE_CONVERGE_ENABLED_KEY, value.toBoolean())
                        "idle_converge_rounds" -> kv.putInt(NS, IDLE_CONVERGE_ROUNDS_KEY, value.toLong())
                        "playbook_auto_enabled" -> kv.putBool(NS, PLAYBOOK_AUTO_ENABLED_KEY, value.toBoolean())
                    }
                }
            }
            kv.putString(NS, ACTIVE_PRESET_KEY, "custom")
            true
        } catch (e: Exception) {
            false
        }
    }
}
