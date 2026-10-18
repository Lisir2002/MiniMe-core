package com.mini.me_core.feature.agent.domain.execution.workflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2 压缩冷却窗口单测（批次 D）。
 * 覆盖：窗口内抑制、窗口外放行、force 由调用方绕过、会话隔离、清理。
 */
class CompactionCooldownTest {

    private val cooldown = CompactionCooldown()
    private val now = 1_000_000L

    @Test
    fun shouldSuppress_withinWindow_true() {
        cooldown.markCompacted("s1", now)
        assertTrue(cooldown.shouldSuppress("s1", now + CompactionCooldown.WINDOW_MS - 1))
    }

    @Test
    fun shouldSuppress_outsideWindow_false() {
        cooldown.markCompacted("s1", now)
        assertFalse(cooldown.shouldSuppress("s1", now + CompactionCooldown.WINDOW_MS))
        assertFalse(cooldown.shouldSuppress("s1", now + CompactionCooldown.WINDOW_MS * 10))
    }

    @Test
    fun shouldSuppress_neverCompacted_false() {
        assertFalse(cooldown.shouldSuppress("s1", now))
    }

    @Test
    fun shouldSuppress_nullSession_false() {
        cooldown.markCompacted(null, now)
        assertFalse(cooldown.shouldSuppress(null, now + 1))
    }

    @Test
    fun sessionsIsolated_markOneDoesNotSuppressOther() {
        cooldown.markCompacted("s1", now)
        assertFalse(cooldown.shouldSuppress("s2", now + 1))
    }

    @Test
    fun markAgain_extendsWindow() {
        cooldown.markCompacted("s1", now)
        // 窗口末再次压缩 → 窗口顺延
        cooldown.markCompacted("s1", now + CompactionCooldown.WINDOW_MS - 1)
        assertTrue(cooldown.shouldSuppress("s1", now + CompactionCooldown.WINDOW_MS))
        assertFalse(cooldown.shouldSuppress("s1", now + CompactionCooldown.WINDOW_MS * 2 - 1))
    }

    @Test
    fun forget_clearsSessionState() {
        cooldown.markCompacted("s1", now)
        cooldown.forget("s1")
        assertFalse(cooldown.shouldSuppress("s1", now + 1))
    }
}
