package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.ImportWarningCode
import org.minime.core.api.MiniMeEngine
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.SemVer
import org.minime.core.schema.SpringValue
import org.minime.core.tokens.TokenVocabulary

/**
 * 编译器端到端：默认主题包（classpath /default-theme.json）在五种上下文下的编译结果。
 *
 * 覆盖：§5.1 三规则真实生效、扩展组（editor/terminal/chart）并入、
 * §5.2 WCAG 警告 golden 基线（不阻断）、十子系统快照装配。
 */
class ThemeCompilerEndToEndTest {

    private val defaultPack: String = ThemeCompilerEndToEndTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private fun compile(vararg modifiers: String) =
        ThemeCompiler().compileJson(defaultPack, modifiers.toList())

    // ---------- light（core only） ----------

    @Test
    fun `light context compiles with expected semantic values`() {
        val result = compile()
        val color = result.snapshot.color
        assertEquals("MiniMe Pure White", result.snapshot.meta.packName)
        assertEquals("1.0.0", result.snapshot.meta.packVersion)
        assertEquals(emptyList<String>(), result.snapshot.meta.activeModifiers)

        // 语义层经别名取到 primitive（规则 1）
        assertEquals("#007AFFFF", color.accent.hex)
        assertEquals("#FFFFFFFF", color.bgCanvas.hex)
        assertEquals("#FFFFFFFF", color.bgSurface.hex)
        assertEquals("#F2F2F7FF", color.bgGrouped.hex)
        assertEquals("#000000FF", color.textPrimary.hex)

        // 玻璃默认关闭（纯白风格）
        assertFalse((result.snapshot.raw["glass.enabled"] as BooleanValue).value)
    }

    @Test
    fun `light context yields complete effective token table`() {
        val result = compile()
        val raw = result.snapshot.raw
        // 词汇表 172 条 + 扩展组 24 条（editor 2 / terminal 16 / chart 6）
        assertEquals(196, raw.size, "生效表规模 = 词汇表 + 扩展组: ${raw.size}")
        for (path in TokenVocabulary.ALL.keys) {
            assertTrue(path in raw, "生效表缺少词汇表路径: $path")
        }
    }

    // ---------- dark ----------

    @Test
    fun `dark modifier overrides primitives and propagates to semantics`() {
        val result = compile("dark")
        val color = result.snapshot.color
        assertEquals("#000000FF", color.bgCanvas.hex)
        assertEquals("#0A84FFFF", color.accent.hex)
        assertEquals("#FFFFFFFF", color.textPrimary.hex)
        // dark 只写 primitive 单层差异，语义层经别名整体换肤（规则 1）
        assertEquals("#1C1C1EFF", color.bgSurfaceSecondary.hex)
        // 复合型字段覆写：dark 只改 tintAlpha 一个字段
        val thin = result.snapshot.glass.thin
        assertEquals(0.45f, thin.tintAlpha)
        assertEquals(12f, thin.blur.value)
    }

    // ---------- 可叠加修饰符 ----------

    @Test
    fun `dark plus highContrast stacks with later winning`() {
        val result = compile("dark", "highContrast")
        assertEquals("#3C3C43FF", result.snapshot.color.textSecondary.hex)
        assertEquals("#000000FF", result.snapshot.color.bgCanvas.hex)
    }

    @Test
    fun `highContrast alone keeps light backgrounds`() {
        val result = compile("highContrast")
        assertEquals("#3C3C43FF", result.snapshot.color.textSecondary.hex)
        assertEquals("#FFFFFFFF", result.snapshot.color.bgCanvas.hex)
    }

    @Test
    fun `reducedMotion swaps springs to high damping`() {
        val light = compile().snapshot.motion.spatialFast
        val reduced = compile("reducedMotion").snapshot.motion.spatialFast
        assertEquals(SpringValue(stiffness = 1600f, damping = 1f, initialVelocity = 0f), reduced)
        assertTrue(reduced.damping >= light.damping, "降低动效不应降低阻尼: ${reduced.damping} < ${light.damping}")
    }

    @Test
    fun `reducedTransparency disables glass and opaques tints`() {
        val result = compile("reducedTransparency")
        assertFalse((result.snapshot.raw["glass.enabled"] as BooleanValue).value)
        assertEquals(0.9f, result.snapshot.glass.thin.tintAlpha)
    }

    // ---------- 扩展组（§3.7） ----------

    @Test
    fun `extension groups are compiled into snapshot`() {
        val snapshot = compile().snapshot
        assertEquals("#C7254EFF", snapshot.color.editorColor("syntax.keyword")?.hex)
        assertEquals("#183691FF", snapshot.color.editorColor("syntax.string")?.hex)
        assertEquals("#FF453AFF", snapshot.color.terminalColor("ansi.red")?.hex)
        assertEquals("#000000FF", snapshot.color.terminalColor("ansi.black")?.hex)
        // chart 是别名，解析到语义色；dark 下随 accent 一起换值（扩展组也遵守规则 1）
        assertEquals("#007AFFFF", snapshot.color.chartColor("c1")?.hex)
        assertEquals("#0A84FFFF", compile("dark").snapshot.color.chartColor("c1")?.hex)
        assertEquals("#5856D6FF", snapshot.color.chartColor("c6")?.hex)
    }

    // ---------- §5.2 行 5：WCAG 警告 golden 基线（不阻断） ----------

    @Test
    fun `light context emits known wcag warnings without blocking`() {
        val result = compile()
        val warnings = result.warnings
        val distinctForegrounds = warnings
            .asSequence()
            .map { Regex("text\\.\\w+").find(it.message)?.value }
            .filterNotNull()
            .toSet()
        assertEquals(
            setOf("text.secondary", "text.tertiary", "text.quaternary", "text.placeholder"),
            distinctForegrounds,
            "WCAG 警告前景集应与 golden 一致（primary 21:1 不应告警）: $distinctForegrounds",
        )
        // 五档文本 × 五档背景中的低对比项；accent（21:1/8.6:1）不应告警
        assertEquals(20, warnings.size, "light 上下文警告条数 golden 基线: ${warnings.size}")
        assertTrue(warnings.all { it.code == ImportWarningCode.WCAG_TEXT_CONTRAST })
    }

    @Test
    fun `dark context also compiles without warnings escalation`() {
        // dark 下文本为白系、背景近黑，对比度达标，警告数应少于 light
        val dark = compile("dark")
        val light = compile()
        assertTrue(
            dark.warnings.size < light.warnings.size || dark.warnings.isEmpty(),
            "dark 警告数应不高于 light: dark=${dark.warnings.size}, light=${light.warnings.size}",
        )
    }

    // ---------- 引擎版本自洽 ----------

    @Test
    fun `default pack declares engine range compatible with current engine`() {
        assertEquals(MiniMeEngine.VERSION, ThemeCompiler.ENGINE_VERSION)
        assertEquals(
            "1.2",
            MiniMeEngine.VERSION,
            "引擎版本应为二段式（步长 +0.1，逢九进一）: ${MiniMeEngine.VERSION}",
        )
        assertTrue(
            SemVer.satisfies(SemVer.parse(MiniMeEngine.VERSION), MiniMeEngine.COMPAT_RANGE),
            "引擎版本须落在自身兼容区间内",
        )
        assertNotNull(compile().snapshot.raw["color.semantic.accent"])
        assertTrue(compile().snapshot.raw["color.semantic.accent"] is ColorValue)
    }
}
