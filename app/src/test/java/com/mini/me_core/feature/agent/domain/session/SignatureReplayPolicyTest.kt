package com.mini.me_core.feature.agent.domain.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SignatureReplayPolicy] 规则测试：Anthropic signature 跨账号回放防护。
 *
 * 规则（保守方向：误剥离只损失 thinking 连续性，误保留才会 400）：
 * - 消息 provider_id 与当前 provider_id 均非空且不一致 → 剥离；
 * - 任一为空（存量 / 备份恢复 / 未知）→ 保持旧行为（回传）；
 * - 一致 → 回传。
 */
class SignatureReplayPolicyTest {

    @Test
    fun `same provider keeps signature`() {
        assertFalse(SignatureReplayPolicy.shouldStripSignature("p1", "p1"))
    }

    @Test
    fun `different providers strip signature`() {
        assertTrue(SignatureReplayPolicy.shouldStripSignature("p1", "p2"))
    }

    @Test
    fun `empty message provider id keeps signature`() {
        // 存量消息（8.sqm 前落库）/ 备份恢复的旧数据：provider_id 为空 → 未知，保持旧行为。
        assertFalse(SignatureReplayPolicy.shouldStripSignature("", "p1"))
        assertFalse(SignatureReplayPolicy.shouldStripSignature(null, "p1"))
    }

    @Test
    fun `blank message provider id keeps signature`() {
        assertFalse(SignatureReplayPolicy.shouldStripSignature("   ", "p1"))
    }

    @Test
    fun `empty current provider id keeps signature`() {
        // 会话未绑定 provider（未知当前账号）：保持旧行为。
        assertFalse(SignatureReplayPolicy.shouldStripSignature("p1", ""))
        assertFalse(SignatureReplayPolicy.shouldStripSignature("p1", null))
    }

    @Test
    fun `blank current provider id keeps signature`() {
        assertFalse(SignatureReplayPolicy.shouldStripSignature("p1", "  "))
    }

    @Test
    fun `both empty keeps signature`() {
        assertFalse(SignatureReplayPolicy.shouldStripSignature("", ""))
        assertFalse(SignatureReplayPolicy.shouldStripSignature(null, null))
    }

    @Test
    fun `both blank keeps signature`() {
        assertFalse(SignatureReplayPolicy.shouldStripSignature(" ", "\t"))
    }
}
