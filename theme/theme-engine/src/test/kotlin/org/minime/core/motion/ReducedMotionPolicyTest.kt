package org.minime.core.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * reducedMotion 降级决策器的机器核对（§6.4.6 铁律 7 + charter §4 两级分离）。
 */
class ReducedMotionPolicyTest {

    @Test
    fun `normal and partial scales keep full motion`() {
        // 系统正常 / 半开（用户已自行减速，引擎不叠加）→ 全规格
        for (behavior in ReducedMotion.entries) {
            assertEquals(EffectiveMotion.FULL, ReducedMotionPolicy.resolve(MotionScale.NORMAL, behavior))
            assertEquals(EffectiveMotion.FULL, ReducedMotionPolicy.resolve(MotionScale.PARTIAL, behavior))
        }
    }

    @Test
    fun `off scale maps each declared behavior to its degraded form`() {
        assertEquals(
            EffectiveMotion.SHORT_TWEEN_HALF,
            ReducedMotionPolicy.resolve(MotionScale.OFF, ReducedMotion.SHORT_TWEEN_HALF),
        )
        assertEquals(EffectiveMotion.STATIC, ReducedMotionPolicy.resolve(MotionScale.OFF, ReducedMotion.STATIC))
        assertEquals(EffectiveMotion.DIRECT, ReducedMotionPolicy.resolve(MotionScale.OFF, ReducedMotion.DIRECT))
        // 触觉等非动画模式在系统关动画时保留（降级表不管触觉）
        assertEquals(EffectiveMotion.FULL, ReducedMotionPolicy.resolve(MotionScale.OFF, ReducedMotion.NONE))
    }

    @Test
    fun `system level overrides theme pack level reducedMotion`() {
        // 叠加策略：主题包级 reducedMotion 修饰符（快照已高阻尼化）+ 系统 OFF
        // → 仍走系统级降级表（系统无障碍意志高于皮肤选择）
        val spinner = MotionCatalog.byId("spinner.indeterminate")
        val page = MotionCatalog.byId("page.push/pop")
        assertEquals(
            EffectiveMotion.STATIC,
            ReducedMotionPolicy.resolve(MotionScale.OFF, spinner),
            "系统 OFF 时即使快照已被主题包级修饰符处理，spinner 仍降级为静态",
        )
        assertEquals(EffectiveMotion.SHORT_TWEEN_HALF, ReducedMotionPolicy.resolve(MotionScale.OFF, page))
    }

    @Test
    fun `degraded duration halves only for short tween`() {
        assertEquals(125, ReducedMotionPolicy.degradedDurationMs(250, EffectiveMotion.SHORT_TWEEN_HALF))
        assertEquals(250, ReducedMotionPolicy.degradedDurationMs(250, EffectiveMotion.FULL))
        assertEquals(250, ReducedMotionPolicy.degradedDurationMs(250, EffectiveMotion.STATIC))
        assertEquals(250, ReducedMotionPolicy.degradedDurationMs(250, EffectiveMotion.DIRECT))
    }

    @Test
    fun `system value mapping boundaries`() {
        assertEquals(MotionScale.OFF, MotionScale.fromSystemValue(0f))
        assertEquals(MotionScale.OFF, MotionScale.fromSystemValue(-1f))
        assertEquals(MotionScale.PARTIAL, MotionScale.fromSystemValue(0.5f))
        assertEquals(MotionScale.PARTIAL, MotionScale.fromSystemValue(0.99f))
        assertEquals(MotionScale.NORMAL, MotionScale.fromSystemValue(1f))
        assertEquals(MotionScale.NORMAL, MotionScale.fromSystemValue(2f))
    }
}
