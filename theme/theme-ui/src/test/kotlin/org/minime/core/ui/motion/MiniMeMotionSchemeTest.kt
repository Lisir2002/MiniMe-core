package org.minime.core.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.tokens.ThemeSnapshot

/**
 * 六槽 → [MiniMeMotionScheme] 回喂契约单测。
 *
 * 核心断言：**回喂值与 `MotionTokens` 一一相等**——
 * 一旦不等，即出现双真源（主题包改了令牌但消费侧没跟上），换肤会出现「一半新一半旧」。
 *
 * 关于官方 M3 `MotionScheme`：material3 1.4.0 stable 中它是 Kotlin `internal`
 * （编译实证 + 官方 API 参考页「Added in 1.5.0-beta02」），故本层用版本无关的
 * [MiniMeMotionScheme] 契约承载回喂语义，官方类型转正后只需加桥接适配器。
 */
class MiniMeMotionSchemeTest {

    private val json: String = MiniMeMotionSchemeTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private val snapshot: ThemeSnapshot by lazy {
        ThemeCompiler().compileJson(json).snapshot
    }

    @Test
    fun `six slots feed back with identical spring parameters`() {
        val motion = snapshot.motion
        val scheme = motion.toMiniMeMotionScheme()

        assertSpring(scheme.fastSpatialSpec(), motion.spatialFast.stiffness, motion.spatialFast.damping)
        assertSpring(scheme.defaultSpatialSpec(), motion.spatialDefault.stiffness, motion.spatialDefault.damping)
        assertSpring(scheme.slowSpatialSpec(), motion.spatialSlow.stiffness, motion.spatialSlow.damping)
        assertSpring(scheme.fastEffectsSpec(), motion.effectsFast.stiffness, motion.effectsFast.damping)
        assertSpring(scheme.defaultEffectsSpec(), motion.effectsDefault.stiffness, motion.effectsDefault.damping)
        assertSpring(scheme.slowEffectsSpec(), motion.effectsSlow.stiffness, motion.effectsSlow.damping)
    }

    @Test
    fun `override modifier propagates to fed back scheme`() {
        // 换肤（覆写层）后回喂值必须同步变化——否则等于双真源
        // 注意：dark 修饰符不改 motion，须用确实覆写 motion 的 reducedMotion 修饰符
        val overridden = ThemeCompiler().compileJson(json, listOf("reducedMotion")).snapshot
        val scheme = overridden.motion.toMiniMeMotionScheme()
        assertSpring(
            scheme.defaultSpatialSpec(),
            overridden.motion.spatialDefault.stiffness,
            overridden.motion.spatialDefault.damping,
        )
        // 该修饰符把 spatial.default 刚度提到 760、阻尼提到 1.0（高阻尼化）
        assertEquals(760f, overridden.motion.spatialDefault.stiffness, 0.001f, "修饰符应已覆写刚度")
        assertEquals(1.0f, overridden.motion.spatialDefault.damping, 0.001f, "修饰符应已覆写阻尼")
    }

    @Test
    fun `adapter is read only and holds no independent state`() {
        val motion = snapshot.motion
        val a = motion.toMiniMeMotionScheme()
        val b = motion.toMiniMeMotionScheme()
        // 同一令牌两次回喂结果一致（无内部可变状态）
        assertSpring(a.defaultSpatialSpec(), b.defaultSpatialSpec() as SpringSpec<Float>)
    }

    private fun assertSpring(spec: FiniteAnimationSpec<Float>, stiffness: Float, damping: Float) {
        val spring = spec as? SpringSpec<Float>
        assertTrue(spring != null, "回喂 spec 应为 spring")
        assertEquals(stiffness, spring!!.stiffness, 0.001f, "回喂刚度须与 MotionTokens 一致")
        assertEquals(damping, spring.dampingRatio, 0.001f, "回喂阻尼须与 MotionTokens 一致")
    }

    private fun assertSpring(a: FiniteAnimationSpec<Float>, b: SpringSpec<Float>) {
        val sa = a as SpringSpec<Float>
        assertEquals(b.stiffness, sa.stiffness, 0.001f)
        assertEquals(b.dampingRatio, sa.dampingRatio, 0.001f)
    }
}
