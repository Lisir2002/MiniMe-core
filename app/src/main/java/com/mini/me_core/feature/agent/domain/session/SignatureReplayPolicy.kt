package com.mini.me_core.feature.agent.domain.session

/**
 * Anthropic extended thinking signature 的回放约束（纯函数，便于单测）。
 *
 * 背景：signature 与产生它的 Anthropic 账号（provider）绑定，跨账号回放会被 API
 * 拒绝（400 invalid_request："thinking block signature is invalid"）。原设计仅在
 * agent_session 级记录 provider_id，用户切换账号后，旧消息的 signature 仍会被
 * buildHistory 回放 → 400，对话直接中断。
 *
 * 修复：消息级记录 provider_id（见 agent.sq / 8.sqm）。回放历史时逐条比对
 * 「消息 provider_id」与「当前 provider_id」，判定是否需要剥离 signature
 * （剥离后 convertToAnthropicMessages 的 `signature.isNotEmpty()` 不成立，
 * thinking block 整体不回传，reasoning 也不回传——block 不可拆分）。
 *
 * 判定规则（保守方向：误剥离只损失 thinking 连续性，误保留才会 400）：
 * - 消息 provider_id 与当前 provider_id **均非空且不一致** → 跨账号，剥离；
 * - 任一为空（存量消息 / 备份恢复 / 未知来源）→ 视为未知，保持旧行为（回传）；
 * - 一致 → 回传。
 */
internal object SignatureReplayPolicy {

    fun shouldStripSignature(messageProviderId: String?, currentProviderId: String?): Boolean {
        val msg = messageProviderId?.takeIf { it.isNotBlank() } ?: return false
        val cur = currentProviderId?.takeIf { it.isNotBlank() } ?: return false
        return msg != cur
    }
}
