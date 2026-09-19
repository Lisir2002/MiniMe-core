package com.mini.me_core.core.agentworkflow

/**
 * F5「规范流程」开关的一次性只读快照（norm-chain §3.5）。
 *
 * 反转前：StatefulAgentWorkflow 在一轮内多次跨模块逐个查询
 * [WorkflowSettingsPort.isIdleConvergeActive] / isStepInjectActive / isReasoningBudgetActive /
 * isUsageCardActive / isToolGuardActive（每读一次都走 KVStore 往返）。
 *
 * 反转后：每轮 CallLlm 前调用 [WorkflowSettingsPort.loadNormFlowSnapshot] 一次性把
 * 全部子开关 + 总开关 AND 叠加成 [NormFlowSnapshot]，后续消费点只读字段，不再逐个查询。
 *
 * 字段语义与 [NormFlowSettingsRepository] 运行时开关一一对应；[stepInjectBudget] 为
 * step 注入预算（400/800/1200，默认 800）。[idleResearchAware] 为预留位（仓库尚未提供，
 * 适配器固定返回 false，业务语义不变）。
 */
data class NormFlowSnapshot(
    val stepInject: Boolean,
    val toolGuard: Boolean,
    val reasoningBudget: Boolean,
    val usageCard: Boolean,
    val playbookAuto: Boolean,
    val idleConverge: Boolean,
    val idleResearchAware: Boolean,
    val sopSummary: Boolean,
    val stepInjectBudget: Int,
) {
    companion object {
        /**
         * 读取失败 / 未配置时的安全默认。对齐各开关默认值：子项默认开、
         * [idleConverge] 默认关（D2-1 空转收敛默认关）、预算默认 800。
         */
        val DEFAULT = NormFlowSnapshot(
            stepInject = true,
            toolGuard = true,
            reasoningBudget = true,
            usageCard = true,
            playbookAuto = true,
            idleConverge = false,
            idleResearchAware = false,
            sopSummary = true,
            stepInjectBudget = 800,
        )
    }
}
