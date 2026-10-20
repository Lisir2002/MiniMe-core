package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.schema.RawField
import org.minime.core.schema.RawToken
import org.minime.core.schema.TokenType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 集合合并器：§5.1 规则 2（互斥单选/可叠加后者覆盖）+ 规则 3（修饰符不得引用）。
 * 规则 1（先合并后解析）的合并半场在此，解析半场见 [AliasResolverTest]。
 */
class SetMergerTest {

    @Test
    fun `merge in resolution order later wins`() {
        val pack = TestPacks.withModifier(
            core = listOf(
                TestPacks.color("color.primitive.systemBackground", "#FFFFFF"),
                TestPacks.color("color.primitive.systemBlue", "#007AFF"),
            ),
            dark = listOf(
                TestPacks.color("color.primitive.systemBackground", "#000000"),
            ),
        )
        val merged = SetMerger.merge(pack, activeModifiers = listOf("dark"))
        assertEquals("#000000FF", (merged["color.primitive.systemBackground"]!!.literal as org.minime.core.schema.ColorValue).hex)
        // 未被修饰符覆盖的令牌保持 core 值
        assertEquals("#007AFFFF", (merged["color.primitive.systemBlue"]!!.literal as org.minime.core.schema.ColorValue).hex)
    }

    @Test
    fun `inactive modifier skipped`() {
        val pack = TestPacks.withModifier(
            core = listOf(TestPacks.color("color.primitive.systemBackground", "#FFFFFF")),
            dark = listOf(TestPacks.color("color.primitive.systemBackground", "#000000")),
        )
        val merged = SetMerger.merge(pack, activeModifiers = emptyList())
        assertEquals("#FFFFFFFF", (merged["color.primitive.systemBackground"]!!.literal as org.minime.core.schema.ColorValue).hex)
    }

    @Test
    fun `stackable modifiers apply in order`() {
        // highContrast 叠加在 dark 之后（§5.1 规则 2：可叠加修饰符追加，后者覆盖前者）
        val pack = TestPacks.pack(
            sets = mapOf(
                "core" to TestPacks.setOf(
                    "core",
                    TestPacks.color("color.semantic.text.secondary", "#3C3C4399"),
                ),
                "dark" to TestPacks.setOf(
                    "dark",
                    TestPacks.color("color.semantic.text.secondary", "#EBEBF599"),
                ),
                "highContrast" to TestPacks.setOf(
                    "highContrast",
                    TestPacks.color("color.semantic.text.secondary", "#3C3C43FF"),
                ),
            ),
            resolutionOrder = listOf(
                TestPacks.entry(set = "core"),
                TestPacks.entry(modifier = "dark"),
                TestPacks.entry(modifier = "highContrast"),
            ),
        )
        val merged = SetMerger.merge(pack, listOf("dark", "highContrast"))
        assertEquals("#3C3C43FF", (merged["color.semantic.text.secondary"]!!.literal as org.minime.core.schema.ColorValue).hex)

        // 只激活 dark：highContrast 不参与
        val darkOnly = SetMerger.merge(pack, listOf("dark"))
        assertEquals("#EBEBF599", (darkOnly["color.semantic.text.secondary"]!!.literal as org.minime.core.schema.ColorValue).hex)
    }

    @Test
    fun `unknown active modifier rejected`() {
        val pack = TestPacks.withModifier(
            core = listOf(TestPacks.color("color.primitive.systemBackground", "#FFFFFF")),
            dark = listOf(TestPacks.color("color.primitive.systemBackground", "#000000")),
        )
        expectCompileError(CompileErrorCode.MODIFIER_INVALID) {
            SetMerger.merge(pack, listOf("light"))
        }
    }

    @Test
    fun `duplicate active modifier rejected`() {
        val pack = TestPacks.withModifier(
            core = listOf(TestPacks.color("color.primitive.systemBackground", "#FFFFFF")),
            dark = listOf(TestPacks.color("color.primitive.systemBackground", "#000000")),
        )
        expectCompileError(CompileErrorCode.MODIFIER_INVALID) {
            SetMerger.merge(pack, listOf("dark", "dark"))
        }
    }

    @Test
    fun `rule 3 modifier cannot contain top-level alias`() {
        val pack = TestPacks.pack(
            sets = mapOf(
                "core" to TestPacks.setOf(
                    "core",
                    TestPacks.color("color.primitive.systemBlue", "#007AFF"),
                ),
                "dark" to TestPacks.setOf(
                    "dark",
                    TestPacks.alias("color.semantic.accent", TokenType.COLOR, "color.primitive.systemBlue"),
                ),
            ),
            resolutionOrder = listOf(TestPacks.entry(set = "core"), TestPacks.entry(modifier = "dark")),
        )
        val msg = expectCompileError(CompileErrorCode.MODIFIER_INVALID) {
            SetMerger.merge(pack, listOf("dark"))
        }
        assertTrue(msg.contains("color.semantic.accent"), "错误信息应指明违规令牌: $msg")
    }

    @Test
    fun `rule 3 modifier cannot contain composite field alias`() {
        // dark 只覆盖 glass.thin.tintAlpha 字段值——必须是字面量，不能写 {glass.thin.tintAlpha} 之外的别名
        val pack = TestPacks.pack(
            sets = mapOf(
                "core" to TestPacks.setOf(
                    "core",
                    TestPacks.number("glass.thin.tintAlpha", 0.65f),
                ),
                "dark" to TestPacks.setOf(
                    "dark",
                    RawToken(
                        "glass.thin",
                        TokenType.GLASS,
                        fields = linkedMapOf(
                            "blur" to org.minime.core.schema.RawField(literal = org.minime.core.schema.DimensionValue(12f, "dp")),
                            "saturation" to org.minime.core.schema.RawField(literal = org.minime.core.schema.NumberValue(1.7f)),
                            "tint" to org.minime.core.schema.RawField(literal = org.minime.core.schema.ColorValue.fromHex("#FFFFFFFF")),
                            "tintAlpha" to org.minime.core.schema.RawField(alias = "glass.thin.tintAlpha"),
                            "hairline" to org.minime.core.schema.RawField(literal = org.minime.core.schema.BooleanValue(true)),
                        ),
                    ),
                ),
            ),
            resolutionOrder = listOf(TestPacks.entry(set = "core"), TestPacks.entry(modifier = "dark")),
        )
        val msg = expectCompileError(CompileErrorCode.MODIFIER_INVALID) {
            SetMerger.merge(pack, listOf("dark"))
        }
        assertTrue(msg.contains("tintAlpha"), "错误信息应指明违规字段: $msg")
    }

    @Test
    fun `rule 3 inactive modifier with alias is not checked`() {
        // 规则 3 只约束被合并的修饰符；未激活的修饰符不参与合并故不触发
        val pack = TestPacks.pack(
            sets = mapOf(
                "core" to TestPacks.setOf("core", TestPacks.color("color.primitive.systemBlue", "#007AFF")),
                "dark" to TestPacks.setOf(
                    "dark",
                    TestPacks.alias("color.semantic.accent", TokenType.COLOR, "color.primitive.systemBlue"),
                ),
            ),
            resolutionOrder = listOf(TestPacks.entry(set = "core"), TestPacks.entry(modifier = "dark")),
        )
        val merged = SetMerger.merge(pack, emptyList())
        assertFalse("color.semantic.accent" in merged)
        assertTrue("color.primitive.systemBlue" in merged)
    }
}
