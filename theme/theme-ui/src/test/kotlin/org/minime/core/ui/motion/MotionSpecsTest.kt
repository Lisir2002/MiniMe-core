package org.minime.core.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.motion.MotionCatalog
import org.minime.core.motion.MotionScale
import org.minime.core.tokens.ThemeSnapshot

/**
 * 动效规格工厂（theme-ui 解析层）单测。
 *
 * 纯 JVM：`AnimationSpec` 是普通 Kotlin 类，构造不需要 Compose 运行时。
 */
class MotionSpecsTest {

    private val json: String = MotionSpecsTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private val snapshot: ThemeSnapshot by lazy {
        ThemeCompiler().compileJson(json).snapshot
    }

    private fun specs(scale: MotionScale = MotionScale.NORMAL): MotionSpecs =
        MotionSpecs(snapshot.motion, snapshot.interaction, scale)

    // ---------- 全规格态 ----------

    @Test
    fun `slot spring maps to compose spring spec`() {
        val spec = specs().require("overlay.enter/exit")
        val spring = spec.spec as? SpringSpec<Float>
        assertNotNull(spring, "过渡模式应为 spring（铁律 2/6）")
        // spatial.default：stiffness 380 / damping 0.86（默认主题包）
        assertEquals(380f, spring!!.stiffness, 0.001f, "spatial.default 刚度")
        assertEquals(0.86f, spring.dampingRatio, 0.001f, "spatial.default 阻尼")
    }

    @Test
    fun `action error uses declared damping override token`() {
        val spec = specs().require("action.error")
        val spring = spec.spec as? SpringSpec<Float>
        assertNotNull(spring)
        // 目录标注 dampingOverrideToken=interaction.shakeDamping → 0.4（低阻尼自然衰减）
        assertEquals(0.4f, spring!!.dampingRatio, 0.001f, "action.error 低阻尼覆写")
        // 槽位仍是 spatial.fast（刚度来自槽位，不受阻尼覆写影响）
        assertEquals(800f, spring.stiffness, 0.001f, "spatial.fast 刚度")
    }

    @Test
    fun `damping override is declared in catalog not hardcoded in factory`() {
        val pattern = MotionCatalog.byId("action.error")
        assertEquals("interaction.shakeDamping", pattern.dampingOverrideToken, "覆写令牌须在目录标注")
        // 未标注覆写的模式沿用槽位阻尼
        assertNull(MotionCatalog.byId("press.scale").dampingOverrideToken)
    }

    @Test
    fun `spinner is the only linear easing pattern`() {
        val spec = specs().require("spinner.indeterminate")
        val tween = spec.spec as? TweenSpec<Float>
        assertNotNull(tween, "spinner 用 tween 表达匀速周期")
        assertEquals(1000, tween!!.durationMillis, "spinner 周期取 motion.duration.spinner")
        // 铁律 2：其余模式不得用 tween
        for (p in MotionCatalog.MVP) {
            if (p.durationToken.isCyclic) continue
            val s = specs().of(p.id)
            assertTrue(s == null || s.spec is SpringSpec<*>, "${p.id} 不得使用 tween（铁律 2）")
        }
    }

    @Test
    fun `nominal duration anchors to duration token value`() {
        // press.scale：槽位 spatial.fast + 时长覆写 motion.duration.instant（100ms）
        val spec = specs().require("press.scale")
        assertEquals(100, spec.durationMs, "press.scale 标称时长取 instant 档")
        // sheet.present/dismiss：motion.duration.slow（300ms）
        assertEquals(300, specs().require("sheet.present/dismiss").durationMs)
    }

    @Test
    fun `haptic sync produces no animation spec`() {
        assertNull(specs().of("haptic.sync"), "触觉同帧模式无动画")
    }

    @Test
    fun `unregistered pattern id fails fast`() {
        val error = runCatching { specs().of("no.such.pattern") }.exceptionOrNull()
        assertNotNull(error, "目录外 id 必须快速失败，不得静默回退")
    }

    // ---------- reducedMotion 降级 ----------

    @Test
    fun `system off degrades spatial to short tween with halved duration`() {
        val full = specs(MotionScale.NORMAL).require("overlay.enter/exit")
        val degraded = specs(MotionScale.OFF).require("overlay.enter/exit")
        assertTrue(degraded.spec is TweenSpec<*>, "降级后应为短 tween（无回弹）")
        assertEquals(full.durationMs / 2, degraded.durationMs, "降级时长减半")
    }

    @Test
    fun `system off turns direct and static patterns into no animation`() {
        // title.collapse → DIRECT（直切）
        assertNull(specs(MotionScale.OFF).of("title.collapse"), "直切降级应无动画")
        // spinner → STATIC
        assertNull(specs(MotionScale.OFF).of("spinner.indeterminate"), "静态降级应无动画")
    }

    @Test
    fun `partial scale keeps full spec`() {
        // PARTIAL（0<v<1）尊重用户中间档，不叠加降级
        val partial = specs(MotionScale.PARTIAL).require("overlay.enter/exit")
        assertTrue(partial.spec is SpringSpec<*>, "中间档保持全规格")
        assertEquals(250, partial.durationMs, "中间档不 halved")
    }

    @Test
    fun `all mvp patterns resolve without error`() {
        for (p in MotionCatalog.MVP) {
            val spec = specs().of(p.id)
            if (p.id == "haptic.sync") {
                assertNull(spec, "触觉模式无动画")
            } else {
                assertNotNull(spec, "${p.id} 应解析出规格")
            }
        }
    }
}
