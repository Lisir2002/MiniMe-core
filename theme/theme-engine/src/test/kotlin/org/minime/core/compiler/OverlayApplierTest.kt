package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.CompileErrorCode
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DtcgParser
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ResolutionEntry
import org.minime.core.schema.SpringValue
import org.minime.core.schema.ThemePack
import org.minime.core.schema.TypographyValue
import org.minime.core.tokens.TokenVocabulary

/**
 * R4 运行时覆写层（OverlayApplier）：§5.1 规则 1 的运行时证明 + §5.2 类型/路径防线。
 *
 * 核心断言：覆写 primitive 后**语义别名随之换值**——覆写不新写合并逻辑，
 * 而是挂合成集走既有流水线，「先合并后解析」在运行时自然成立。
 */
class OverlayApplierTest {

    private val defaultPack: ThemePack = DtcgParser().parse(
        OverlayApplierTest::class.java
            .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }
    )

    private fun compileWith(overlay: Map<String, org.minime.core.schema.TokenValue>, vararg modifiers: String) =
        ThemeCompiler().compile(OverlayApplier.apply(defaultPack, overlay), modifiers.toList())

    // ---------- 验收 1：覆写 primitive 后语义别名换值（§5.1 规则 1 运行时证明） ----------

    @Test
    fun `overriding primitive revalues semantic aliases`() {
        val result = compileWith(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))

        // accent = {color.primitive.systemBlue} → 跟随覆写值（规则 1 在运行时成立）
        assertEquals("#FF0000FF", result.snapshot.color.accent.hex)
        // 未覆写的语义层不受影响
        assertEquals("#FFFFFFFF", result.snapshot.color.bgCanvas.hex)
        // 覆写值本身在生效表内
        assertEquals("#FF0000FF", result.snapshot.raw["color.primitive.systemBlue"]?.let { (it as ColorValue).hex })
    }

    @Test
    fun `overriding semantic token directly wins`() {
        val result = compileWith(mapOf("color.semantic.accent" to ColorValue.fromHex("#00FF00")))

        // 直接覆写语义层（覆盖包内别名声明）
        assertEquals("#00FF00FF", result.snapshot.color.accent.hex)
        // primitive 未被触及，其它经别名取它的语义层仍是原值
        assertEquals("#007AFFFF", result.snapshot.raw["color.primitive.systemBlue"]?.let { (it as ColorValue).hex })
    }

    @Test
    fun `overlay wins over modifier set`() {
        // dark 修饰符把 systemBlue 改暗；覆写在其之上（overrides 集挂最末）
        val result = compileWith(mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")), "dark")

        assertEquals("#FF0000FF", result.snapshot.color.accent.hex)
        assertEquals(listOf("dark"), result.snapshot.meta.activeModifiers)
    }

    // ---------- 验收 2：复合型覆写（glass / spring / typography） ----------

    @Test
    fun `overriding glass recipe replaces composite`() {
        val recipe = GlassValue(
            blur = DimensionValue(30f, "dp"),
            saturation = 2.0f,
            tint = ColorValue.fromHex("#112233"),
            tintAlpha = 0.5f,
            hairline = false,
        )
        val result = compileWith(mapOf("glass.regular" to recipe))

        val glass = result.snapshot.glass
        assertEquals(30f, glass.regular.blur.dp)
        assertEquals(2.0f, glass.regular.saturation)
        assertEquals(0.5f, glass.regular.tintAlpha)
        assertEquals(false, glass.regular.hairline)
    }

    @Test
    fun `overriding glass scalar token revalues composite field alias`() {
        // glass.regular.tintAlpha 字段是别名 {glass.regular.tintAlpha}；覆写该 number 令牌 → 字段跟随
        val result = compileWith(mapOf("glass.regular.tintAlpha" to NumberValue(0.42f)))

        assertEquals(0.42f, result.snapshot.glass.regular.tintAlpha)
        // 其它材质未被触及（thick 默认 0.82）
        assertEquals(0.82f, result.snapshot.glass.thick.tintAlpha)
    }

    @Test
    fun `overriding spring and typography composites`() {
        val result = compileWith(
            mapOf(
                "motion.effects.default" to SpringValue(stiffness = 999f, damping = 1.5f, initialVelocity = 0f),
                "typography.body" to TypographyValue(
                    fontSize = DimensionValue(20f, "sp"),
                    lineHeight = DimensionValue(26f, "sp"),
                    fontFamily = "serif",
                ),
            )
        )

        val spring = result.snapshot.motion.effectsDefault
        assertEquals(999f, spring.stiffness)
        assertEquals(1.5f, spring.damping)
        val body = result.snapshot.typography.body
        assertEquals(20f, body.fontSize.sp)
        assertEquals("serif", body.fontFamily)
    }

    // ---------- 防线：路径与类型（§5.2） ----------

    @Test
    fun `overriding with mismatched value type is rejected`() {
        val msg = expectCompileError(CompileErrorCode.TYPE_MISMATCH) {
            OverlayApplier.apply(defaultPack, mapOf("color.primitive.systemBlue" to NumberValue(1f)))
        }
        assertTrue(msg.contains("color.primitive.systemBlue"), "诊断应含路径: $msg")
    }

    @Test
    fun `overriding unknown path is rejected`() {
        val msg = expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            OverlayApplier.apply(defaultPack, mapOf("nope.token" to NumberValue(1f)))
        }
        assertTrue(msg.contains("nope.token"), "诊断应含路径: $msg")
    }

    @Test
    fun `overriding open-vocabulary extension path is rejected in R4-1`() {
        // 开放词汇（editor/terminal/chart）由主题包整体提供，覆写语义待后续批次
        val msg = expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            OverlayApplier.apply(defaultPack, mapOf("editor.syntax.keyword" to ColorValue.fromHex("#000000")))
        }
        assertTrue(msg.contains("editor.syntax.keyword"), "诊断应含路径: $msg")
    }

    @Test
    fun `base pack with reserved overlay set name is rejected`() {
        val clash = defaultPack.copy(
            sets = defaultPack.sets + (OverlayApplier.OVERLAY_SET to TestPacks.setOf(OverlayApplier.OVERLAY_SET)),
            resolutionOrder = defaultPack.resolutionOrder + ResolutionEntry(set = OverlayApplier.OVERLAY_SET, modifier = null),
        )
        expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            OverlayApplier.apply(clash, mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")))
        }
    }

    // ---------- 结构与不变式 ----------

    @Test
    fun `empty overlay returns base unchanged`() {
        assertSame(defaultPack, OverlayApplier.apply(defaultPack, emptyMap()))
    }

    @Test
    fun `overlay set is appended last and keeps pack invariant`() {
        val applied = OverlayApplier.apply(
            defaultPack,
            mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")),
        )

        // manifest / 其它 set / 扩展组原样保留
        assertEquals(defaultPack.manifest, applied.manifest)
        assertEquals(defaultPack.sets.keys + OverlayApplier.OVERLAY_SET, applied.sets.keys)
        assertEquals(defaultPack.extensions, applied.extensions)

        // 覆写集挂最末（优先级最高：modifier < overlay）
        assertEquals(ResolutionEntry(set = OverlayApplier.OVERLAY_SET, modifier = null), applied.resolutionOrder.last())
        assertEquals(defaultPack.resolutionOrder, applied.resolutionOrder.dropLast(1))

        // 覆写集全字面量（无别名，合并期即终值）
        val token = applied.sets.getValue(OverlayApplier.OVERLAY_SET).tokens.getValue("color.primitive.systemBlue")
        assertEquals(null, token.alias)
        assertEquals(ColorValue.fromHex("#FF0000"), token.literal)
        assertEquals(TokenVocabulary.typeOf("color.primitive.systemBlue"), token.declaredType)

        // 结构不变量保持（每个 set 被引用且仅一次）——能编译通过即证明
        ThemeValidator.validateResolutionOrder(applied)
    }

    @Test
    fun `overlay does not leak into base pack object`() {
        val applied = OverlayApplier.apply(
            defaultPack,
            mapOf("color.primitive.systemBlue" to ColorValue.fromHex("#FF0000")),
        )
        // 基础包对象未被修改（copy 语义）：后续无覆写编译仍得原值
        val clean = ThemeCompiler().compile(defaultPack, emptyList())
        assertEquals("#007AFFFF", clean.snapshot.color.accent.hex)
        assertTrue(OverlayApplier.OVERLAY_SET !in defaultPack.sets)
        assertTrue(applied !== defaultPack)
    }
}
