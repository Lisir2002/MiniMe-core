package com.mini.me_core.core.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1/A2 启动画面门闩与真实进度纯函数单元测试。
 *
 * 覆盖：
 * 1. shouldDismissSplash 三条退场路径（就绪+最短 / 总时长硬上限 / 未就绪不退）
 * 2. StartupProgress.progressOf 阶段权重与就绪封顶
 */
class SplashGatingTest {

    // ── 1. shouldDismissSplash 退场路径 ──

    @Test
    fun `未就绪且未达最短展示 - 不退场`() {
        assertFalse(
            shouldDismissSplash(
                elapsedMs = 1_000L,
                minDisplayMs = 1_200L,
                isReady = false,
                totalDurationMs = 6_000L,
            )
        )
    }

    @Test
    fun `已就绪但未达最短展示 - 不退场（防闪）`() {
        assertFalse(
            shouldDismissSplash(
                elapsedMs = 1_199L,
                minDisplayMs = 1_200L,
                isReady = true,
                totalDurationMs = 6_000L,
            )
        )
    }

    @Test
    fun `已就绪且达到最短展示 - 退场`() {
        assertTrue(
            shouldDismissSplash(
                elapsedMs = 1_200L,
                minDisplayMs = 1_200L,
                isReady = true,
                totalDurationMs = 6_000L,
            )
        )
    }

    @Test
    fun `未就绪但达到总时长硬上限 - 退场（初始化异常兜底）`() {
        assertTrue(
            shouldDismissSplash(
                elapsedMs = 6_000L,
                minDisplayMs = 1_200L,
                isReady = false,
                totalDurationMs = 6_000L,
            )
        )
    }

    @Test
    fun `就绪即退不受总时长约束（常规冷启动提前放行）`() {
        assertTrue(
            shouldDismissSplash(
                elapsedMs = 1_500L,
                minDisplayMs = 1_200L,
                isReady = true,
                totalDurationMs = 6_000L,
            )
        )
    }

    // ── 2. StartupProgress.progressOf 阶段权重 ──

    @Test
    fun `STARTING 阶段进度为起始值`() {
        assertEquals(0.1f, StartupProgress.progressOf(StartupPhase.STARTING, isReady = false), 1e-6f)
    }

    @Test
    fun `三阶段进度单调递增`() {
        val starting = StartupProgress.progressOf(StartupPhase.STARTING, isReady = false)
        val data = StartupProgress.progressOf(StartupPhase.DATA_LAYER, isReady = false)
        val core = StartupProgress.progressOf(StartupPhase.CORE_SERVICES, isReady = false)
        val warmup = StartupProgress.progressOf(StartupPhase.ASYNC_WARMUP, isReady = false)
        assertTrue(starting < data)
        assertTrue(data < core)
        assertTrue(core < warmup)
        assertTrue(warmup < 1f)
    }

    @Test
    fun `就绪后任何阶段都封顶 1_0`() {
        for (phase in StartupPhase.entries) {
            assertEquals(
                1f,
                StartupProgress.progressOf(phase, isReady = true),
                1e-6f,
            )
        }
    }
}
