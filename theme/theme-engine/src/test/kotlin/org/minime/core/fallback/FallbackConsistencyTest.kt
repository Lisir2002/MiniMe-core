package org.minime.core.fallback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.compiler.ThemeCompiler
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.CubicBezierValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue
import org.minime.core.tokens.TokenVocabulary

/**
 * Fallback 一致性守卫（防漂移）。
 *
 * [FallbackTheme.rawValues] 是从默认主题包派生出来的**静态快照**，编译期零依赖
 * （不解析 JSON、不联网、不读资源），因此必须在单测层与「默认包编译结果」逐一比对：
 * - 任一侧改值而另一侧未同步 → 本测试失败；
 * - fallback 必须覆盖标准词汇表全部路径（词汇表缺项由它回填）；
 * - fallback 自身必须可装配为快照（SnapshotBuilder 强类型转换不抛异常）。
 */
class FallbackConsistencyTest {

    private val defaultPackJson: String = FallbackConsistencyTest::class.java
        .getResourceAsStream("/default-theme.json")!!.use { it.bufferedReader(Charsets.UTF_8).readText() }

    /** 默认包 light 上下文的编译生效表 */
    private val compiled: Map<String, TokenValue> by lazy {
        ThemeCompiler().compileJson(defaultPackJson).snapshot.raw
    }

    @Test
    fun `fallback covers whole standard vocabulary`() {
        val missing = TokenVocabulary.ALL.keys.filterNot { it in FallbackTheme.rawValues }
        assertEquals(emptyList<String>(), missing, "fallback 缺少词汇表路径: $missing")
    }

    @Test
    fun `fallback values equal default pack compiled values`() {
        val diffs = ArrayList<String>()
        for ((path, value) in FallbackTheme.rawValues) {
            val compiledValue = compiled[path]
            when {
                compiledValue == null -> diffs += "$path: 默认包编译结果缺失"
                compiledValue != value -> diffs += "$path: fallback=$value ≠ 默认包=$compiledValue"
            }
        }
        assertEquals(
            emptyList<String>(),
            diffs,
            "fallback 与默认包漂移（${diffs.size} 处）——两侧须同步修改: $diffs",
        )
    }

    @Test
    fun `fallback and compiled effective tables have equal size`() {
        assertEquals(
            compiled.size,
            FallbackTheme.rawValues.size,
            "fallback=${FallbackTheme.rawValues.size} 与默认包生效表=${compiled.size} 规模应一致",
        )
        assertEquals(197, FallbackTheme.rawValues.size)
    }

    @Test
    fun `fallback value types match vocabulary declarations`() {
        val mismatches = TokenVocabulary.ALL.mapNotNull { (path, declared) ->
            val value = FallbackTheme.rawValues[path] ?: return@mapNotNull "$path: fallback 缺失"
            val actual = value.runtimeType
            if (declared in STRINGISH) {
                // string / fontFamily / fontWeight 同以 StringValue 承载，按「字符串族」判定
                if (actual in STRINGISH) null else "$path: 词汇表=$declared，实际值=$actual"
            } else if (actual != declared) {
                "$path: 词汇表=$declared，实际值=$actual"
            } else {
                null
            }
        }
        assertEquals(emptyList<String>(), mismatches, "fallback 值类型与词汇表不符: $mismatches")
    }

    @Test
    fun `fallback snapshot builds with all ten subsystems`() {
        val snapshot = FallbackTheme.snapshot
        assertEquals("MiniMe Built-in Fallback", snapshot.meta.packName)
        assertTrue(snapshot.color.semantic.isNotEmpty(), "配色语义层不应为空")
        assertTrue(snapshot.typography.subtree.isNotEmpty())
        assertTrue(snapshot.shape.subtree.isNotEmpty())
        assertTrue(snapshot.size.subtree.isNotEmpty())
        assertTrue(snapshot.spacing.subtree.isNotEmpty())
        assertTrue(snapshot.motion.spatial.isNotEmpty() && snapshot.motion.duration.isNotEmpty())
        assertTrue(snapshot.interaction.subtree.isNotEmpty())
        assertTrue(snapshot.elevation.subtree.isNotEmpty())
        assertTrue(snapshot.opacity.primitive.isNotEmpty())
        assertTrue(snapshot.glass.subtree.isNotEmpty())

        // 十子系统访问器全部可解析（缺失即抛，不接受静默降级）
        assertEquals(BooleanValue(false), snapshot.glass.enabled)
        snapshot.color.accent
        snapshot.typography.body
        snapshot.shape.radiusCard
        snapshot.size.touchTarget
        snapshot.spacing.s16
        snapshot.motion.spatialFast
        snapshot.motion.durationFast
        snapshot.interaction.flickVelocity
        snapshot.elevation.hairline
        snapshot.opacity.l50
        snapshot.glass.thin
    }

    @Test
    fun `vocabulary subsystem distribution matches whitepaper counts`() {
        fun count(prefix: String) = TokenVocabulary.ALL.keys.count { it.startsWith("$prefix.") }
        assertEquals(
            mapOf(
                "color" to 62,
                "typography" to 14,
                "shape" to 6,
                "size" to 13,
                "spacing" to 11,
                "motion" to 13,
                "interaction" to 27,
                "elevation" to 2,
                "opacity" to 15,
                "glass" to 10,
            ),
            mapOf(
                "color" to count("color"),
                "typography" to count("typography"),
                "shape" to count("shape"),
                "size" to count("size"),
                "spacing" to count("spacing"),
                "motion" to count("motion"),
                "interaction" to count("interaction"),
                "elevation" to count("elevation"),
                "opacity" to count("opacity"),
                "glass" to count("glass"),
            ),
            "十子系统条目分布与白皮书 §6 清单不一致（词汇表增删须同步白皮书）",
        )
        assertEquals(173, TokenVocabulary.SIZE)
    }

    private companion object {
        /** 值类型 → 令牌类型（string 三型同以 StringValue 承载） */
        private val STRINGISH = setOf(TokenType.STRING, TokenType.FONT_FAMILY, TokenType.FONT_WEIGHT)

        private val TokenValue.runtimeType: TokenType
            get() = when (this) {
                is ColorValue -> TokenType.COLOR
                is DimensionValue -> TokenType.DIMENSION
                is DurationValue -> TokenType.DURATION
                is NumberValue -> TokenType.NUMBER
                is BooleanValue -> TokenType.BOOLEAN
                is StringValue -> TokenType.STRING
                is CubicBezierValue -> TokenType.CUBIC_BEZIER
                is TypographyValue -> TokenType.TYPOGRAPHY
                is ShadowValue -> TokenType.SHADOW
                is SpringValue -> TokenType.SPRING
                is GlassValue -> TokenType.GLASS
                else -> error("未知令牌值类型: ${this::class.simpleName}")
            }
    }
}
