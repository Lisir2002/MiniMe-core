package com.mini.me_core.feature.agent.presentation.component.agentfirst

/**
 * Agent-First UI 功能开关。
 *
 * 所有新组件通过此 flag 控制，默认 false（使用现有实现），开发完成后改为 true。
 * 任何时候都可以一键回退到旧实现（将对应 flag 设为 false）。
 *
 * 设计红线：渐进式增强，不做"大爆炸"式替换。新组件与旧组件并存，flag 切换。
 */
object AgentFirstFeatureFlags {

    /**
     * ToolCallCard：工具调用卡片（6 状态 + 折叠展开 + 复制 + 错误诊断 + 环境状态条 + 重试）。
     * 替换现有 `ToolMessageBody` 的通栏 surfaceVariant 块。
     * 默认 false，使用现有 ToolMessageBody。
     */
    const val ENABLE_TOOL_CALL_CARD = false

    /**
     * TaskCard：任务卡片（7 状态 + 步骤列表 + 进度条 + ReasoningBlock + AI回复 + Artifact区域 + 底部操作）。
     * 替换现有 `TaskAccordion`（800+ 行的两级手风琴）。
     * 默认 false，使用现有 TaskAccordion。
     */
    const val ENABLE_TASK_CARD = false

    /**
     * 全局 Agent-First UI 总开关。
     * 当此 flag 为 true 时，所有子开关（ENABLE_TOOL_CALL_CARD、ENABLE_TASK_CARD 等）
     * 都视为 true。用于一次性启用全部新组件。
     * 默认 false。
     */
    const val ENABLE_AGENT_FIRST_UI = false

    /**
     * ToolCallCard 是否启用。
     * 优先级：ENABLE_AGENT_FIRST_UI > ENABLE_TOOL_CALL_CARD。
     */
    fun isToolCallCardEnabled(): Boolean = ENABLE_AGENT_FIRST_UI || ENABLE_TOOL_CALL_CARD

    /**
     * TaskCard 是否启用。
     * 优先级：ENABLE_AGENT_FIRST_UI > ENABLE_TASK_CARD。
     */
    fun isTaskCardEnabled(): Boolean = ENABLE_AGENT_FIRST_UI || ENABLE_TASK_CARD
}
