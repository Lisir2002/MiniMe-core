package org.minime.core.ui.m3

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.tokens.ThemeSnapshot

/**
 * M3 映射层（§9.1）——只映射 color / typography / shape 三子系统。
 *
 * 纯 JVM 单测：M3 的 `ColorScheme`/`Typography`/`Shapes` 都是普通 Kotlin 类，
 * 构造不需要 Compose 运行时，因此映射正确性可在本机验证（不必等真机）。
 */
class M3MappingTest {

    private val json: String = M3MappingTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    private fun snapshot(vararg modifiers: String): ThemeSnapshot =
        ThemeCompiler().compileJson(json, modifiers.toList()).snapshot

    // ---------- color ----------

    @Test
    fun `semantic colors map to m3 roles`() {
        val tokens = snapshot().color
        val scheme = tokens.toColorScheme()

        assertEquals(tokens.accent.toComposeColor(), scheme.primary, "accent → primary")
        assertEquals(tokens.bgCanvas.toComposeColor(), scheme.background, "bg.canvas → background")
        assertEquals(tokens.bgSurface.toComposeColor(), scheme.surface, "bg.surface → surface")
        assertEquals(tokens.textPrimary.toComposeColor(), scheme.onSurface, "text.primary → onSurface")
        assertEquals(tokens.stateError.toComposeColor(), scheme.error, "state.error → error")
        assertEquals(tokens.borderOpaque.toComposeColor(), scheme.outline, "border.opaque → outline")
    }

    @Test
    fun `dark context starts from dark baseline`() {
        // 未映射的角色（tertiary 系列）应来自 dark 基底，而非一律亮色——
        // 否则暗色主题下的存量 M3 页面会残留亮色角色
        val light = snapshot().color.toColorScheme()
        val dark = snapshot("dark").color.toColorScheme()

        assertNotEquals(lightColorScheme().tertiary, dark.tertiary, "暗主题应取 dark 基底")
        assertEquals(lightColorScheme().tertiary, light.tertiary, "亮主题应取 light 基底")

        // 已映射角色仍严格跟随引擎语义色
        assertEquals("#000000FF", dark.background.hexOf(), "dark 的 background = bg.canvas")
        assertEquals("#FFFFFFFF", light.background.hexOf())
    }

    @Test
    fun `accent propagates through dark modifier`() {
        val dark = snapshot("dark").color.toColorScheme()
        assertEquals("#0A84FFFF", dark.primary.hexOf(), "dark 下 accent 换值应传导到 M3 primary")
    }

    // ---------- typography ----------

    @Test
    fun `type scale maps by descending size`() {
        val typo = snapshot().typography.toTypography()
        assertEquals(34f, typo.displayLarge.fontSize.value, "largeTitle 34 → displayLarge")
        assertEquals(28f, typo.displayMedium.fontSize.value, "title1 28")
        assertEquals(22f, typo.displaySmall.fontSize.value, "title2 22")
        assertEquals(17f, typo.bodyLarge.fontSize.value, "body 17 → bodyLarge")
        assertEquals(16f, typo.bodyMedium.fontSize.value, "callout 16 → bodyMedium")
        assertEquals(11f, typo.labelSmall.fontSize.value, "caption2 11 → labelSmall")
    }

    @Test
    fun `line height and weight are carried over`() {
        val typo = snapshot().typography.toTypography()
        assertEquals(22f, typo.bodyLarge.lineHeight.value, "body lineHeight 22sp")
        // headline 是 600 字重
        assertEquals(600, typo.headlineMedium.fontWeight?.weight)
        assertEquals(FontFamily.Default, typo.bodyLarge.fontFamily, "sans-serif → Default")
    }

    // ---------- shape ----------

    @Test
    fun `radii map to m3 shape scale`() {
        val shapes = snapshot().shape.toShapes()
        assertTrue(shapes.medium == androidx.compose.foundation.shape.RoundedCornerShape(12.dp), "卡片 12dp → medium")
        assertTrue(shapes.large == androidx.compose.foundation.shape.RoundedCornerShape(16.dp), "大卡 16dp → large")
        assertTrue(shapes.extraLarge == androidx.compose.foundation.shape.RoundedCornerShape(14.dp), "对话框 14dp → extraLarge")
    }

    // ---------- 范围声明 ----------

    @Test
    fun `mapping covers only three subsystems`() {
        // §9.1 范围声明：motion / interaction / size / spacing / elevation / opacity / glass 不进 M3。
        // 这里以「映射函数只接受三个子系统的类型」作为编译期契约的体现（其余子系统无映射入口）。
        val snap = snapshot()
        snap.color.toColorScheme()
        snap.typography.toTypography()
        snap.shape.toShapes()
        assertTrue(snap.motion.spatial.isNotEmpty(), "motion 由自绘组件直接消费，不映射")
        assertTrue(snap.interaction.subtree.isNotEmpty(), "interaction 由自绘组件直接消费，不映射")
    }

    /**
     * 转为引擎的 `#RRGGBBAA` 表示以便与令牌值直接比对。
     * 注意 `toArgb()` 返回的是 **ARGB** 位序（Android 约定），需换位成引擎的 RGBA。
     */
    private fun androidx.compose.ui.graphics.Color.hexOf(): String {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val a = (argb shr 24) and 0xFF
        return "#%02X%02X%02X%02X".format(r, g, b, a)
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
