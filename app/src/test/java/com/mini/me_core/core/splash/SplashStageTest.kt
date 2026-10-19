package com.mini.me_core.core.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动动画时间轴纯函数单元测试。
 *
 * 覆盖：
 * 1. SplashStage.fromElapsed 三档边界映射（严格 < 语义）
 * 2. SplashStage.stageProgress 每档起点为 0、COMPLETE 为 1、全程落在 [0,1]
 * 3. SplashStage.remainingSeconds 从总时长递减并按 0 封底
 * 4. SplashQualityLevel 分档常量与审计方案（1800/1200/600）一致、阶段终点自洽
 *
 * 注：shouldDismissSplash / StartupProgress 的纯函数测试见 SplashGatingTest。
 */
class SplashStageTest {

    private val qualities = SplashQualityLevel.entries

    @Test
    fun `fromElapsed 三档边界映射正确`() {
        for (q in qualities) {
            val e = SplashStage.explodeEndMs(q)
            val c = SplashStage.convergeEndMs(q)
            val h = SplashStage.holdEndMs(q)
            val s = SplashStage.shatterEndMs(q)
            val total = SplashStage.totalDurationMs(q)

            assertEquals(SplashStage.EXPLODE, SplashStage.fromElapsed(0L, q))
            assertEquals(SplashStage.EXPLODE, SplashStage.fromElapsed(e - 1, q))
            assertEquals(SplashStage.CONVERGE, SplashStage.fromElapsed(e, q))
            assertEquals(SplashStage.CONVERGE, SplashStage.fromElapsed(c - 1, q))
            assertEquals(SplashStage.HOLD, SplashStage.fromElapsed(c, q))
            assertEquals(SplashStage.HOLD, SplashStage.fromElapsed(h - 1, q))
            assertEquals(SplashStage.SHATTER, SplashStage.fromElapsed(h, q))
            assertEquals(SplashStage.SHATTER, SplashStage.fromElapsed(s - 1, q))
            // shatterEndMs == totalDurationMs：到达总时长即 COMPLETE（FADE_OUT 由 Compose 交叉淡出接管）
            assertEquals(SplashStage.COMPLETE, SplashStage.fromElapsed(s, q))
            assertEquals(SplashStage.COMPLETE, SplashStage.fromElapsed(total, q))
        }
    }

    @Test
    fun `stageProgress 每档起点为0、COMPLETE为1、全程落在0到1`() {
        for (q in qualities) {
            val e = SplashStage.explodeEndMs(q)
            val c = SplashStage.convergeEndMs(q)
            val h = SplashStage.holdEndMs(q)
            val total = SplashStage.totalDurationMs(q)

            assertEquals(0f, SplashStage.stageProgress(0L, q), 1e-6f)
            assertEquals(0f, SplashStage.stageProgress(e, q), 1e-6f)
            assertEquals(0f, SplashStage.stageProgress(c, q), 1e-6f)
            assertEquals(0f, SplashStage.stageProgress(h, q), 1e-6f)
            assertEquals(1f, SplashStage.stageProgress(total, q), 1e-6f)

            // 全程扫描：progress 恒在 [0,1]
            for (t in 0..total step 50) {
                val p = SplashStage.stageProgress(t, q)
                assertTrue("progress 应 ≥0 @$t/$q", p >= 0f)
                assertTrue("progress 应 ≤1 @$t/$q", p <= 1f)
            }
            // EXPLODE 中点应在 (0,1)
            val mid = SplashStage.stageProgress(e / 2, q)
            assertTrue("EXPLODE 中点 progress 应在 (0,1)", mid > 0f && mid < 1f)
        }
    }

    @Test
    fun `remainingSeconds 从总时长递减并按0封底`() {
        for (q in qualities) {
            val total = SplashStage.totalDurationMs(q)
            val totalSec = (total / 1000L).toInt()
            assertEquals(totalSec, SplashStage.remainingSeconds(0L, q))
            assertEquals(totalSec - 1, SplashStage.remainingSeconds(1000L, q))
            assertEquals(0, SplashStage.remainingSeconds(total, q))
            assertEquals(0, SplashStage.remainingSeconds(total + 1000L, q))
        }
    }
}

class SplashQualityLevelTest {

    @Test
    fun `分档常量与审计方案一致 1800_1200_600`() {
        assertEquals(1800, SplashQualityLevel.HIGH.particleCount)
        assertEquals(40, SplashQualityLevel.HIGH.shardCount)
        assertEquals(6000L, SplashQualityLevel.HIGH.durationMs)
        assertEquals(true, SplashQualityLevel.HIGH.enable3D)
        assertEquals(true, SplashQualityLevel.HIGH.enableCracks)

        assertEquals(1200, SplashQualityLevel.MEDIUM.particleCount)
        assertEquals(25, SplashQualityLevel.MEDIUM.shardCount)
        assertEquals(5000L, SplashQualityLevel.MEDIUM.durationMs)
        assertEquals(true, SplashQualityLevel.MEDIUM.enable3D)
        assertEquals(true, SplashQualityLevel.MEDIUM.enableCracks)

        assertEquals(600, SplashQualityLevel.LOW.particleCount)
        assertEquals(12, SplashQualityLevel.LOW.shardCount)
        assertEquals(4000L, SplashQualityLevel.LOW.durationMs)
        assertEquals(false, SplashQualityLevel.LOW.enable3D)
        assertEquals(false, SplashQualityLevel.LOW.enableCracks)
    }

    @Test
    fun `分档总时长与阶段终点自洽 shatterEnd等于total`() {
        for (q in SplashQualityLevel.entries) {
            assertEquals(
                SplashStage.totalDurationMs(q),
                SplashStage.shatterEndMs(q),
            )
            assertTrue(SplashStage.explodeEndMs(q) < SplashStage.convergeEndMs(q))
            assertTrue(SplashStage.convergeEndMs(q) < SplashStage.holdEndMs(q))
            assertTrue(SplashStage.holdEndMs(q) < SplashStage.shatterEndMs(q))
        }
    }
}
