package com.mini.me_core.feature.settings.data.repository

import com.mini.me_core.datalayer.store.KVStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
        const val STEP_INJECT_BUDGET_KEY = "step_inject_budget"
        /** 与 StatefulAgentWorkflow.IDLE_CONVERGE_ROUNDS 对齐的只读导出值（不写回 KVStore）。 */
        const val IDLE_CONVERGE_ROUNDS = 6
        val JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }

    /** (F6) step 注入预算 400/800/1200，默认 800。 */
    val stepInjectBudgetFlow: Flow<Int> = kv.observeInt(NS, STEP_INJECT_BUDGET_KEY).map { it?.toInt() ?: 800 }

    suspend fun setStepInjectBudget(value: Int) {
        kv.putInt(NS, STEP_INJECT_BUDGET_KEY, value.toLong())
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "step 预算变更: $value")
    }

    val normFlowEnabledFlow: Flow<Boolean> = kv.observeBool(NS, NORM_FLOW_ENABLED_KEY).map { it ?: true }
    val stepInjectEnabledFlow: Flow<Boolean> = kv.observeBool(NS, STEP_INJECT_ENABLED_KEY).map { it ?: true }
    val toolGuardEnabledFlow: Flow<Boolean> = kv.observeBool(NS, TOOL_GUARD_ENABLED_KEY).map { it ?: true }
    val reasoningBudgetEnabledFlow: Flow<Boolean> = kv.observeBool(NS, REASONING_BUDGET_ENABLED_KEY).map { it ?: true }
    val usageCardEnabledFlow: Flow<Boolean> = kv.observeBool(NS, USAGE_CARD_ENABLED_KEY).map { it ?: true }
    val sopSummaryEnabledFlow: Flow<Boolean> = kv.observeBool(NS, SOP_SUMMARY_ENABLED_KEY).map { it ?: true }
    val playbookAutoEnabledFlow: Flow<Boolean> = kv.observeBool(NS, PLAYBOOK_AUTO_ENABLED_KEY).map { it ?: true }
    val idleConvergeEnabledFlow: Flow<Boolean> = kv.observeBool(NS, IDLE_CONVERGE_ENABLED_KEY).map { it ?: false }

    suspend fun setNormFlowEnabled(enabled: Boolean) { kv.putBool(NS, NORM_FLOW_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setStepInjectEnabled(enabled: Boolean) { kv.putBool(NS, STEP_INJECT_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setToolGuardEnabled(enabled: Boolean) { kv.putBool(NS, TOOL_GUARD_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setReasoningBudgetEnabled(enabled: Boolean) { kv.putBool(NS, REASONING_BUDGET_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setUsageCardEnabled(enabled: Boolean) { kv.putBool(NS, USAGE_CARD_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setSopSummaryEnabled(enabled: Boolean) { kv.putBool(NS, SOP_SUMMARY_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setPlaybookAutoEnabled(enabled: Boolean) { kv.putBool(NS, PLAYBOOK_AUTO_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }
    suspend fun setIdleConvergeEnabled(enabled: Boolean) { kv.putBool(NS, IDLE_CONVERGE_ENABLED_KEY, enabled)
        com.mini.me_core.core.util.FileLogger.i("NormFlow", "开关变更: $enabled") }

    suspend fun isStepInjectActive(): Boolean = normFlowEnabledFlow.first() && stepInjectEnabledFlow.first()
    suspend fun isToolGuardActive(): Boolean = normFlowEnabledFlow.first() && toolGuardEnabledFlow.first()
    suspend fun isReasoningBudgetActive(): Boolean = normFlowEnabledFlow.first() && reasoningBudgetEnabledFlow.first()
    suspend fun isUsageCardActive(): Boolean = normFlowEnabledFlow.first() && usageCardEnabledFlow.first()
    suspend fun isSopSummaryActive(): Boolean = normFlowEnabledFlow.first() && sopSummaryEnabledFlow.first()
    suspend fun isPlaybookAutoActive(): Boolean = normFlowEnabledFlow.first() && playbookAutoEnabledFlow.first()
    suspend fun isIdleConvergeActive(): Boolean = normFlowEnabledFlow.first() && idleConvergeEnabledFlow.first()

    // ── E3 配置导入导出：8 个开关 + step 预算 + 空转阈值（只读常量）序列化为 JSON ──
    // idleResearchAware 仓库尚未提供持久化键，导出为 false、导入忽略（业务语义不变）。
    /** 导出当前全部规范流程开关 + 预算为 JSON 字符串（供写入 Downloads/MiniMe-core/settings-export.json）。 */
    suspend fun exportToJson(): String {
        val obj = buildMap {
            put(NORM_FLOW_ENABLED_KEY, normFlowEnabledFlow.first())
            put(STEP_INJECT_ENABLED_KEY, stepInjectEnabledFlow.first())
            put(TOOL_GUARD_ENABLED_KEY, toolGuardEnabledFlow.first())
            put(REASONING_BUDGET_ENABLED_KEY, reasoningBudgetEnabledFlow.first())
            put(USAGE_CARD_ENABLED_KEY, usageCardEnabledFlow.first())
            put(SOP_SUMMARY_ENABLED_KEY, sopSummaryEnabledFlow.first())
            put(PLAYBOOK_AUTO_ENABLED_KEY, playbookAutoEnabledFlow.first())
            put(IDLE_CONVERGE_ENABLED_KEY, idleConvergeEnabledFlow.first())
            put(STEP_INJECT_BUDGET_KEY, stepInjectBudgetFlow.first())
            put("idle_research_aware", false)
            put("idle_rounds_threshold", IDLE_CONVERGE_ROUNDS)
        }
        return JSON.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(obj.mapValues { (_, v) ->
                when (v) {
                    is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                    is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                    else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                }
            })
        )
    }

    /** 从 JSON 字符串批量写回 KVStore；idle_research_aware / idle_rounds_threshold 无持久化键，忽略。返回是否成功解析并写入。 */
    suspend fun importFromJson(json: String): Boolean = runCatching {
        val root = JSON.parseToJsonElement(json).jsonObject
        fun bool(key: String, default: Boolean): Boolean =
            root[key]?.jsonPrimitive?.boolean ?: default
        fun int(key: String, default: Int): Int =
            root[key]?.jsonPrimitive?.int ?: root[key]?.jsonPrimitive?.long?.toInt() ?: default

        setNormFlowEnabled(bool(NORM_FLOW_ENABLED_KEY, true))
        setStepInjectEnabled(bool(STEP_INJECT_ENABLED_KEY, true))
        setToolGuardEnabled(bool(TOOL_GUARD_ENABLED_KEY, true))
        setReasoningBudgetEnabled(bool(REASONING_BUDGET_ENABLED_KEY, true))
        setUsageCardEnabled(bool(USAGE_CARD_ENABLED_KEY, true))
        setSopSummaryEnabled(bool(SOP_SUMMARY_ENABLED_KEY, true))
        setPlaybookAutoEnabled(bool(PLAYBOOK_AUTO_ENABLED_KEY, true))
        setIdleConvergeEnabled(bool(IDLE_CONVERGE_ENABLED_KEY, false))
        setStepInjectBudget(int(STEP_INJECT_BUDGET_KEY, 800))
        true
    }.getOrElse {
        com.mini.me_core.core.util.FileLogger.w("NormFlow", "导入配置 JSON 解析失败，已忽略", it)
        false
    }
}
