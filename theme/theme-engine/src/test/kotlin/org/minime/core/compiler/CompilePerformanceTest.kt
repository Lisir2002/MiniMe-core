package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 编译耗时回归（§8.2 预算：导入 ≤200ms 典型包）。
 *
 * 本测试跑在 **JVM 桌面**（非设备），因此阈值取桌面基线：
 * - 实测（2026-10-21，默认包 196 条令牌）：p50 ≈ 2.4ms、p95 ≈ 66ms（p95 为 GC 抖动）；
 * - 设备侧（ART，更弱 CPU）由 :theme-ui 的 androidTest 按 200ms 预算复核。
 *
 * 断言只卡 **中位数**（p50 ≤ 20ms）——中位数反映真实算法成本，
 * 不受单次 GC 抖动影响；p95/max 抖动属于运行时噪声，不做断言以免测试脆弱。
 *
 * 这条基线同时是 R2「冷启动同步编译即可首帧即目标主题、无需磁盘快照缓存层」的实测依据。
 */
class CompilePerformanceTest {

    private val json: String = CompilePerformanceTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    @Test
    fun `compile median latency within budget`() {
        val compiler = ThemeCompiler()
        repeat(20) { compiler.compileJson(json) } // 预热 JIT

        val runs = 100
        val samples = LongArray(runs)
        repeat(runs) { i ->
            val t0 = System.nanoTime()
            compiler.compileJson(json)
            samples[i] = System.nanoTime() - t0
        }
        val p50 = samples.sorted()[runs / 2] / 1_000_000.0
        println("编译默认包 p50 = %.2f ms（预算 20ms 桌面基线）".format(p50))
        assertTrue(p50 <= 20.0, "编译中位耗时超预算: $p50 ms")
    }

    @Test
    fun `compile with modifiers median latency within budget`() {
        val compiler = ThemeCompiler()
        val modifierSets = listOf(listOf("dark"), listOf("dark", "highContrast"), listOf("reducedMotion"))
        repeat(10) { modifierSets.forEach { compiler.compileJson(json, it) } }

        val runs = 60
        val samples = LongArray(runs)
        repeat(runs) { i ->
            val modifiers = modifierSets[i % modifierSets.size]
            val t0 = System.nanoTime()
            compiler.compileJson(json, modifiers)
            samples[i] = System.nanoTime() - t0
        }
        val p50 = samples.sorted()[runs / 2] / 1_000_000.0
        println("带修饰符编译 p50 = %.2f ms".format(p50))
        assertTrue(p50 <= 20.0, "带修饰符编译中位耗时超预算: $p50 ms")
    }

    @Test
    fun `repeated compile does not degrade throughput`() {
        // 内存泄漏/缓存失控会导致吞吐持续劣化：对比前后半程中位耗时
        val compiler = ThemeCompiler()
        repeat(20) { compiler.compileJson(json) }
        val half = 40
        fun batch(): Double {
            val s = LongArray(half)
            repeat(half) { i ->
                val t0 = System.nanoTime()
                compiler.compileJson(json)
                s[i] = System.nanoTime() - t0
            }
            return s.sorted()[half / 2] / 1_000_000.0
        }
        val first = batch()
        val second = batch()
        println("前后半程 p50: %.2f ms → %.2f ms".format(first, second))
        assertTrue(second <= first * 3 + 5, "吞吐显著劣化（疑似泄漏）: $first → $second")
    }
}
