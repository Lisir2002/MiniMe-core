package com.mini.me_core.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端点健康注册表测试：熔断开/半开/恢复、健康分排序、无样本原序。
 * 时间全部显式注入（nowMs），不依赖真实时钟。
 */
class EndpointHealthRegistryTest {

    private fun registry() = EndpointHealthRegistry()

    @Test
    fun unknownEndpoint_isNotCircuitOpen_fullScore() {
        val r = registry()
        assertFalse(r.isCircuitOpen("https://a.example/"))
        assertEquals(EndpointHealthRegistry.DEFAULT_SCORE, r.healthScore("https://a.example/"))
    }

    @Test
    fun consecutiveFailures_openCircuitAfterThreshold() {
        val r = registry()
        val now = 1_000_000L
        r.recordFailure("https://a.example/", now)
        r.recordFailure("https://a.example/", now)
        assertFalse("未达阈值不得熔断", r.isCircuitOpen("https://a.example/", now))
        r.recordFailure("https://a.example/", now)
        assertTrue("连续失败达阈值必须熔断", r.isCircuitOpen("https://a.example/", now))
        assertEquals("熔断中健康分为 0", 0, r.healthScore("https://a.example/", now))
    }

    @Test
    fun circuit_expiresAfterWindow_halfOpenProbe() {
        val r = registry()
        val now = 1_000_000L
        repeat(EndpointHealthRegistry.FAILURE_THRESHOLD) {
            r.recordFailure("https://a.example/", now)
        }
        assertTrue(r.isCircuitOpen("https://a.example/", now + EndpointHealthRegistry.CIRCUIT_OPEN_MS - 1))
        assertFalse("到期必须半开", r.isCircuitOpen("https://a.example/", now + EndpointHealthRegistry.CIRCUIT_OPEN_MS))
    }

    @Test
    fun success_clearsFailuresAndClosesCircuit() {
        val r = registry()
        val now = 1_000_000L
        repeat(EndpointHealthRegistry.FAILURE_THRESHOLD) {
            r.recordFailure("https://a.example/", now)
        }
        assertTrue(r.isCircuitOpen("https://a.example/", now))
        r.recordSuccess("https://a.example/", latencyMs = 800, nowMs = now + 10)
        assertFalse(r.isCircuitOpen("https://a.example/", now + 10))
        assertTrue("恢复后延迟入账", r.healthScore("https://a.example/", now + 10) < EndpointHealthRegistry.DEFAULT_SCORE)
    }

    @Test
    fun success_updatesEwmaLatency() {
        val r = registry()
        r.recordSuccess("https://a.example/", latencyMs = 1000)
        r.recordSuccess("https://a.example/", latencyMs = 500)
        // ewma = (1000*3 + 500)/4 = 875 → 罚分 8
        assertEquals(EndpointHealthRegistry.DEFAULT_SCORE - 8, r.healthScore("https://a.example/"))
    }

    @Test
    fun rankEndpoints_putsCircuitOpenLast() {
        val r = registry()
        val now = 1_000_000L
        r.recordSuccess("https://official.example/", latencyMs = 500, nowMs = now)
        repeat(EndpointHealthRegistry.FAILURE_THRESHOLD) {
            r.recordFailure("https://user.example/", now)
        }
        val ranked = r.rankEndpoints(listOf("https://user.example/", "https://official.example/"), now)
        assertEquals("熔断端点必须排到最后", listOf("https://official.example/", "https://user.example/"), ranked)
    }

    @Test
    fun rankEndpoints_noSamples_keepsOriginalOrder() {
        val r = registry()
        val cands = listOf("https://user.example/", "https://official.example/")
        assertEquals("无健康数据时原序返回（存量选路行为不变）", cands, r.rankEndpoints(cands))
    }

    @Test
    fun rankEndpoints_prefersLowerLatencyOnTie() {
        val r = registry()
        val now = 1_000_000L
        r.recordSuccess("https://slow.example/", latencyMs = 3000, nowMs = now)
        r.recordSuccess("https://fast.example/", latencyMs = 200, nowMs = now)
        val ranked = r.rankEndpoints(listOf("https://slow.example/", "https://fast.example/"), now)
        assertEquals(listOf("https://fast.example/", "https://slow.example/"), ranked)
    }

    @Test
    fun rankEndpoints_emptyInput() {
        assertTrue(registry().rankEndpoints(emptyList()).isEmpty())
    }
}
