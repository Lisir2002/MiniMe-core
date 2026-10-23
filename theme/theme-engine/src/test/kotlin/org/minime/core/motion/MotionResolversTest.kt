package org.minime.core.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.tokens.ThemeSnapshot

/**
 * 槽位/时长/参数解析器单测（§6.3 数据面 → §6.4 规格表的桥）。
 *
 * 纯 JVM 引擎侧，无 Compose 依赖。
 */
class MotionResolversTest {

    private val json: String = MotionResolversTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private val snapshot: ThemeSnapshot by lazy {
        ThemeCompiler().compileJson(json).snapshot
    }

    @Test
    fun `every slot resolves to its spring`() {
        val motion = snapshot.motion
        assertEquals(800f, MotionSlot.SPATIAL_FAST.springOf(motion)!!.stiffness, 0.001f)
        assertEquals(380f, MotionSlot.SPATIAL_DEFAULT.springOf(motion)!!.stiffness, 0.001f)
        assertEquals(200f, MotionSlot.SPATIAL_SLOW.springOf(motion)!!.stiffness, 0.001f)
        assertEquals(1200f, MotionSlot.EFFECTS_FAST.springOf(motion)!!.stiffness, 0.001f)
        assertEquals(600f, MotionSlot.EFFECTS_DEFAULT.springOf(motion)!!.stiffness, 0.001f)
        assertEquals(300f, MotionSlot.EFFECTS_SLOW.springOf(motion)!!.stiffness, 0.001f)
        assertNull(MotionSlot.NONE.springOf(motion), "无槽位模式不承载动画")
    }

    @Test
    fun `every duration token resolves to milliseconds`() {
        val motion = snapshot.motion
        assertEquals(100f, DurationToken.INSTANT.durationOf(motion).ms, 0.001f)
        assertEquals(150f, DurationToken.FAST.durationOf(motion).ms, 0.001f)
        assertEquals(250f, DurationToken.DEFAULT.durationOf(motion).ms, 0.001f)
        assertEquals(300f, DurationToken.SLOW.durationOf(motion).ms, 0.001f)
        assertEquals(400f, DurationToken.MAX.durationOf(motion).ms, 0.001f)
        assertEquals(1000f, DurationToken.SPINNER.durationOf(motion).ms, 0.001f)
        assertEquals(1200f, DurationToken.SHIMMER.durationOf(motion).ms, 0.001f)
    }

    @Test
    fun `interaction token lookup resolves and rejects unknown path`() {
        assertEquals(0.4f, snapshot.interaction.numberAt("interaction.shakeDamping").value, 0.001f)
        val error = runCatching { snapshot.interaction.numberAt("interaction.nope") }.exceptionOrNull()
        assertTrue(error != null, "缺失令牌必须抛错——静默回退会让目录里的令牌名变成谎言")
    }

    @Test
    fun `effective damping applies catalog declared override`() {
        val motion = snapshot.motion
        val interaction = snapshot.interaction
        // action.error：目录标注覆写令牌 → 0.4（低阻尼自然衰减）
        assertEquals(0.4f, MotionCatalog.byId("action.error").effectiveDamping(motion, interaction), 0.001f)
        // 未标注覆写的模式沿用槽位阻尼
        assertEquals(0.9f, MotionCatalog.byId("press.scale").effectiveDamping(motion, interaction), 0.001f)
    }

    @Test
    fun `damping override token must exist in vocabulary`() {
        for (p in MotionCatalog.ALL) {
            val token = p.dampingOverrideToken ?: continue
            assertTrue(token in org.minime.core.tokens.TokenVocabulary.ALL, "${p.id} 覆写令牌不在词汇表: $token")
        }
    }
}
