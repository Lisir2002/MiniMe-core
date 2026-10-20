package org.minime.core.ui.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.schema.ColorValue
import org.minime.core.tokens.ThemeSnapshot

/** 子系统实例复用（§8.2 重组最小化）——换肤性能主路径的纯逻辑验证 */
class SnapshotReuserTest {

    private val json: String = SnapshotReuserTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private fun compile(vararg modifiers: String): ThemeSnapshot =
        ThemeCompiler().compileJson(json, modifiers.toList()).snapshot

    @Test
    fun `identical snapshots fully reuse every subsystem`() {
        val a = compile()
        val b = compile()
        val reused = SnapshotReuser.reuse(a, b)
        assertEquals(10, SnapshotReuser.reusedCount(a, b))
        assertSame(a.color, reused.color, "color 应复用旧实例")
        assertSame(a.motion, reused.motion, "motion 应复用旧实例")
        assertSame(a.interaction, reused.interaction)
        assertSame(a.glass, reused.glass)
    }

    @Test
    fun `changed subsystems are replaced while unchanged ones are reused`() {
        val light = compile()
        val dark = compile("dark")
        val reused = SnapshotReuser.reuse(light, dark)

        // 亮→暗：color 变了（换新实例）；motion / interaction / size / spacing 不变（复用旧实例）
        assertSame(dark.color, reused.color, "color 已变，应用新实例")
        assertSame(light.motion, reused.motion, "motion 未变，应复用")
        assertSame(light.interaction, reused.interaction, "interaction 未变，应复用")
        assertSame(light.size, reused.size)
        assertSame(light.spacing, reused.spacing)
        assertTrue(SnapshotReuser.reusedCount(light, dark) >= 6, "亮→暗至少 6 个子系统可复用")
    }

    @Test
    fun `reuse preserves semantics exactly`() {
        // 复用只换引用，绝不改值——复用后的快照与直接编译结果语义完全等价
        val light = compile()
        val dark = compile("dark")
        val reused = SnapshotReuser.reuse(light, dark)
        assertEquals(dark.color, reused.color)
        assertEquals(dark.raw, reused.raw)
        assertEquals("#000000FF", reused.color.bgCanvas.hex)
        assertEquals("#0A84FFFF", reused.color.accent.hex)
    }

    @Test
    fun `first apply has nothing to reuse`() {
        val first = compile()
        val reused = SnapshotReuser.reuse(null, first)
        assertSame(first, reused)
        assertEquals(0, SnapshotReuser.reusedCount(null, first))
    }

    @Test
    fun `glass toggle changes only glass subsystem`() {
        val base = compile()
        val reduced = compile("reducedTransparency")
        val reused = SnapshotReuser.reuse(base, reduced)
        assertSame(reduced.glass, reused.glass, "glass 变了")
        assertSame(base.typography, reused.typography, "typography 未变")
        assertSame(base.motion, reused.motion)
        assertEquals(org.minime.core.schema.NumberValue(0.9f), reused.glass.thinTintAlpha)
    }

    @Test
    fun `reused color tokens keep accessor values`() {
        val light = compile()
        val hc = compile("highContrast")
        val reused = SnapshotReuser.reuse(light, hc)
        assertEquals(ColorValue.fromHex("#3C3C43FF"), reused.color.textSecondary)
        assertEquals(ColorValue.fromHex("#FFFFFFFF"), reused.color.bgCanvas)
    }
}
