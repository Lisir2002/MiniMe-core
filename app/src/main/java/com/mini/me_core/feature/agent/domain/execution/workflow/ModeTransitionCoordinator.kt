package com.mini.me_core.feature.agent.domain.execution.workflow

import com.mini.me_core.feature.agent.domain.core.model.AgentContext
import com.mini.me_core.feature.agent.domain.core.model.AgentMode
import com.mini.me_core.feature.agent.domain.execution.tool.ToolCall
import kotlinx.serialization.json.JsonPrimitive

/**
 * 执行模式切换守卫（P1-09 从 StatefulAgentWorkflow 整体搬迁的叶子协作者）。
 *
 * 职责：纯计算 switchMode 工具是否触发执行模式（PLAN/AUTO/BUILD 等）切换——
 * 仅返回「候选新上下文 + 是否发生切换」的二元判定，不 awaitApproval、不发事件、不改主上下文。
 * PLAN→BUILD 的用户审批（planApprovalManager.awaitApproval）与 systemPrompt 重建仍留在主类编排，
 * 因为它们与发送 ModeChanged 事件、回滚 PLAN 等编排强耦合。判定条件与参数解析逐字保持（行为保持搬迁）。
 */
internal class ModeTransitionCoordinator {

    fun checkAndUpdateMode(
        toolCall: ToolCall,
        isError: Boolean,
        currentContext: AgentContext
    ): Pair<AgentContext, Boolean> {
        if (toolCall.name == "switchMode" && !isError) {
            val targetModeStr = (toolCall.arguments["mode"] as? JsonPrimitive)?.content?.trim()?.uppercase()
                ?: toolCall.arguments["mode"]?.toString()?.replace("\"", "")?.trim()?.uppercase()
            if (targetModeStr != null) {
                runCatching { AgentMode.valueOf(targetModeStr) }.getOrNull()?.let { newMode ->
                    if (currentContext.mode != newMode) {
                        return currentContext.copy(mode = newMode) to true
                    }
                }
            }
        }
        return currentContext to false
    }
}
