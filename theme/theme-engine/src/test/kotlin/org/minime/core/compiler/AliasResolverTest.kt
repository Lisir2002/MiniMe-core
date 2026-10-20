package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.CompileErrorCode
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.ExtensionGroup
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.RawField
import org.minime.core.schema.RawToken
import org.minime.core.schema.SpringValue
import org.minime.core.schema.TokenType

/**
 * 别名解析器（§5.1 规则 1「先合并后解析」的解析半场 + §5.2 别名行校验）。
 *
 * 规则 1 的核心语义：别名统一在**合并后**的扁平表上解析——修饰符对别名**源**的覆盖
 * 会传导到别名处（`color.semantic.accent = {color.primitive.systemBlue}` 在 dark 下
 * 必须拿到 #0A84FF）。若先解析后合并，语义层换肤即失效。
 * `rule 1` 前缀的测试专门守卫此行为。
 */
class AliasResolverTest {

    // ---------- 规则 1：先合并后解析 ----------

    @Test
    fun `rule 1 alias resolves on merged table so modifier override of source propagates`() {
        // accent 别名 systemBlue；dark 只覆盖 systemBlue 的字面量。
        // 先合并后解析 ⇒ dark 下 accent = #0A84FF（先解析后合并会错拿 core 的 #007AFF）
        val pack = TestPacks.withModifier(
            core = listOf(
                TestPacks.color("color.primitive.systemBlue", "#007AFF"),
                TestPacks.alias("color.semantic.accent", TokenType.COLOR, "color.primitive.systemBlue"),
            ),
            dark = listOf(TestPacks.color("color.primitive.systemBlue", "#0A84FF")),
        )

        val light = AliasResolver(SetMerger.merge(pack, emptyList())).resolveAll()
        assertEquals("#007AFFFF", (light["color.semantic.accent"] as ColorValue).hex)

        val dark = AliasResolver(SetMerger.merge(pack, listOf("dark"))).resolveAll()
        assertEquals("#0A84FFFF", (dark["color.semantic.accent"] as ColorValue).hex)
    }

    @Test
    fun `rule 1 modifier override applies even when alias is in core and modifier targets primitive`() {
        // 语义层是别名链（accent → systemBlue），修饰符只写 primitive 单层覆盖（白皮书默认包形态）
        val pack = TestPacks.withModifier(
            core = listOf(
                TestPacks.color("color.primitive.systemBackground", "#FFFFFF"),
                TestPacks.alias("color.semantic.bg.canvas", TokenType.COLOR, "color.primitive.systemBackground"),
            ),
            dark = listOf(TestPacks.color("color.primitive.systemBackground", "#000000")),
        )
        val dark = AliasResolver(SetMerger.merge(pack, listOf("dark"))).resolveAll()
        assertEquals("#000000FF", (dark["color.semantic.bg.canvas"] as ColorValue).hex)
        assertEquals("#000000FF", (dark["color.primitive.systemBackground"] as ColorValue).hex)
    }

    // ---------- 别名链 ----------

    @Test
    fun `multi-level alias chain resolves transitively`() {
        val tokens = mapOf(
            "a" to TestPacks.alias("a", TokenType.COLOR, "b"),
            "b" to TestPacks.alias("b", TokenType.COLOR, "c"),
            "c" to TestPacks.color("c", "#30D158"),
        )
        val resolved = AliasResolver(tokens).resolveAll()
        assertEquals("#30D158FF", (resolved["a"] as ColorValue).hex)
        assertEquals("#30D158FF", (resolved["b"] as ColorValue).hex)
    }

    @Test
    fun `literal tokens pass through unchanged`() {
        val tokens = mapOf(
            "x" to TestPacks.number("x", 0.65f),
            "y" to TestPacks.color("y", "#FF453A"),
        )
        val resolved = AliasResolver(tokens).resolveAll()
        assertEquals(0.65f, (resolved["x"] as NumberValue).value)
        assertEquals("#FF453AFF", (resolved["y"] as ColorValue).hex)
    }

    // ---------- §5.2 别名拓扑校验 ----------

    @Test
    fun `broken alias rejected`() {
        val tokens = mapOf(
            "color.semantic.accent" to TestPacks.alias(
                "color.semantic.accent", TokenType.COLOR, "color.primitive.nope",
            ),
        )
        val msg = expectCompileError(CompileErrorCode.ALIAS_BROKEN) {
            AliasResolver(tokens).resolveAll()
        }
        assertTrue(msg.contains("color.primitive.nope"), "错误信息应含断链目标: $msg")
        assertTrue(msg.contains("color.semantic.accent"), "错误信息应含别名令牌: $msg")
    }

    @Test
    fun `alias cycle rejected with cycle path`() {
        val tokens = mapOf(
            "a" to TestPacks.alias("a", TokenType.COLOR, "b"),
            "b" to TestPacks.alias("b", TokenType.COLOR, "a"),
        )
        val msg = expectCompileError(CompileErrorCode.ALIAS_CYCLE) {
            AliasResolver(tokens).resolveAll()
        }
        assertTrue(msg.contains("a"), "错误信息应含环上节点: $msg")
        assertTrue(msg.contains("b"), "错误信息应含环上节点: $msg")
    }

    @Test
    fun `self-referencing alias rejected`() {
        val tokens = mapOf(
            "self" to TestPacks.alias("self", TokenType.NUMBER, "self"),
        )
        expectCompileError(CompileErrorCode.ALIAS_CYCLE) {
            AliasResolver(tokens).resolveAll()
        }
    }

    @Test
    fun `alias type mismatch rejected`() {
        // accent 声明为 color，但别名目标是 number
        val tokens = mapOf(
            "opacity.veil" to TestPacks.number("opacity.veil", 0.64f),
            "color.semantic.accent" to TestPacks.alias("color.semantic.accent", TokenType.COLOR, "opacity.veil"),
        )
        val msg = expectCompileError(CompileErrorCode.TYPE_MISMATCH) {
            AliasResolver(tokens).resolveAll()
        }
        assertTrue(msg.contains("color.semantic.accent"), "错误信息应含违规令牌: $msg")
        assertTrue(msg.contains("opacity.veil"), "错误信息应含别名目标: $msg")
    }

    // ---------- 复合型（§6.1.3）----------

    @Test
    fun `composite whole alias reuses target value`() {
        val brisk = TestPacks.spring("motion.spring.brisk", stiffness = 420f, damping = 0.9f)
        val tokens = mapOf(
            "motion.spring.brisk" to brisk,
            "motion.spring.snappy" to TestPacks.alias("motion.spring.snappy", TokenType.SPRING, "motion.spring.brisk"),
        )
        val resolved = AliasResolver(tokens).resolveAll()
        assertEquals(
            SpringValue(stiffness = 420f, damping = 0.9f, initialVelocity = 0f),
            resolved["motion.spring.snappy"],
        )
    }

    @Test
    fun `composite field alias resolves to target literal`() {
        // glass.thin.tint / tintAlpha 字段别名（白皮书默认包形态）
        val tokens = mapOf(
            "color.semantic.bg.surface" to TestPacks.color("color.semantic.bg.surface", "#FFFFFF"),
            "glass.thin.tintAlpha" to TestPacks.number("glass.thin.tintAlpha", 0.65f),
            "glass.thin" to TestPacks.glass(
                "glass.thin",
                tintAlias = "color.semantic.bg.surface",
                tintAlphaAlias = "glass.thin.tintAlpha",
            ),
        )
        val resolved = AliasResolver(tokens).resolveAll()
        val glass = resolved["glass.thin"] as GlassValue
        assertEquals("#FFFFFFFF", glass.tint.hex)
        assertEquals(0.65f, glass.tintAlpha)
        assertEquals(DimensionValue(12f, "dp"), glass.blur)
    }

    @Test
    fun `composite field alias type mismatch rejected with field name`() {
        // tintAlpha 期望 number，别名到 color 目标
        val tokens = mapOf(
            "color.semantic.bg.surface" to TestPacks.color("color.semantic.bg.surface", "#FFFFFF"),
            "glass.thin" to TestPacks.glass("glass.thin", tintAlphaAlias = "color.semantic.bg.surface"),
        )
        val msg = expectCompileError(CompileErrorCode.TYPE_MISMATCH) {
            AliasResolver(tokens).resolveAll()
        }
        assertTrue(msg.contains("tintAlpha"), "错误信息应含违规字段: $msg")
    }

    @Test
    fun `composite missing required field rejected`() {
        val tokens = mapOf(
            "motion.spring.broken" to RawToken(
                "motion.spring.broken",
                TokenType.SPRING,
                fields = linkedMapOf(
                    "stiffness" to RawField(literal = NumberValue(380f)),
                    // 缺 damping / initialVelocity（spring 全字段必填）
                ),
            ),
        )
        val msg = expectCompileError(CompileErrorCode.TYPE_MISMATCH) {
            AliasResolver(tokens).resolveAll()
        }
        assertTrue(msg.contains("damping"), "错误信息应含缺失字段: $msg")
    }

    // ---------- 扩展组别名 ----------

    @Test
    fun `extension group alias resolves against core tokens`() {
        val pack = TestPacks.pack(
            sets = mapOf(
                "core" to TestPacks.setOf(
                    "core",
                    TestPacks.color("color.primitive.systemBlue", "#007AFF"),
                    TestPacks.alias("color.semantic.accent", TokenType.COLOR, "color.primitive.systemBlue"),
                ),
            ),
            resolutionOrder = listOf(TestPacks.entry(set = "core")),
            extensions = mapOf(
                "chart" to ExtensionGroup(
                    "chart",
                    linkedMapOf(
                        "chart.c1" to TestPacks.alias("chart.c1", TokenType.COLOR, "color.semantic.accent"),
                    ),
                ),
            ),
        )
        val merged = SetMerger.merge(pack, emptyList())
        assertTrue("chart.c1" in merged, "扩展组令牌须并入合并表（包级常驻）")
        val resolved = AliasResolver(merged).resolveAll()
        assertEquals("#007AFFFF", (resolved["chart.c1"] as ColorValue).hex)
    }
}
