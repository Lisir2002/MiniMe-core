package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.runtime.compositionLocalOf

/**
 * 消息长按快捷操作（重新生成 / 删除 / 分享 / 朗读）的全局回调总线。
 *
 * 为什么用 CompositionLocal 而不是逐层透传参数：调用链是
 * AIChatPanel → TaskAccordion → EmbeddedToolAccordion / 消息循环 → AgentMessageItem，
 * 中间还有 TaskSubGroupRenderer（当前 feature flag 关闭，属预留路径），
 * 逐层加回调会让签名膨胀且每层都要改动。
 * CompositionLocal 让接入点只在一处（AIChatPanel）Provide，渲染端在 AgentMessageItem 内读取。
 */
data class MessageActionBus(
    /** 重新生成：参数为消息 id（ViewModel 内部回溯到最近的用户消息重跑）。 */
    val onRegenerate: (String) -> Unit = {},
    /** 删除消息。 */
    val onDelete: (String) -> Unit = {},
    /** 分享：参数为消息正文。 */
    val onShare: (String) -> Unit = {},
    /** 朗读：参数为消息正文。 */
    val onReadAloud: (String) -> Unit = {},
    /** 是否正在朗读（用于把「朗读」图标切换为「停止」）。 */
    val isSpeaking: Boolean = false,
    /** 停止朗读。 */
    val onStopSpeaking: () -> Unit = {}
)

val LocalMessageActionBus = compositionLocalOf { MessageActionBus() }
