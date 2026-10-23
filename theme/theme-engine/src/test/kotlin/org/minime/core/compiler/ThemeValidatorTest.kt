package org.minime.core.compiler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ImportWarningCode
import org.minime.core.api.MiniMeEngine
import org.minime.core.fallback.FallbackTheme
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.CompositeSchemas
import org.minime.core.schema.ExtensionGroup
import org.minime.core.schema.GlassConfig
import org.minime.core.schema.NumberValue
import org.minime.core.schema.SchemaDeclarations
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenValue

/**
 * 导入期校验（§5.2 校验表逐行）：engineVersion 兼容区间、resolutionOrder 引用完整性、
 * 扩展组冲突、schema 声明一致、词汇表类型一致、玻璃 opaque、WCAG 警告（不阻断）。
 */
class ThemeValidatorTest {

    private val coreSet = TestPacks.setOf(
        "core",
        TestPacks.color("color.primitive.systemBlue", "#007AFF"),
        TestPacks.color("color.semantic.accent", "#007AFF"),
    )
    private val order = listOf(TestPacks.entry(set = "core"))

    // ---------- 行 1：manifest / engineVersion ----------

    @Test
    fun `engine version in range accepted`() {
        ThemeValidator.validatePack(
            TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order),
        )
    }

    @Test
    fun `engine version out of range rejected`() {
        val pack = TestPacks.pack(
            engineVersion = ">=9.0.0 <10.0.0",
            sets = mapOf("core" to coreSet),
            resolutionOrder = order,
        )
        val msg = expectCompileError(CompileErrorCode.ENGINE_VERSION_INCOMPATIBLE) {
            ThemeValidator.validatePack(pack)
        }
        assertTrue(msg.contains(MiniMeEngine.VERSION), "错误信息应含当前引擎版本: $msg")
    }

    @Test
    fun `malformed engine version range rejected`() {
        val pack = TestPacks.pack(
            engineVersion = "第 1 版",
            sets = mapOf("core" to coreSet),
            resolutionOrder = order,
        )
        expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
    }

    // ---------- 行 1：resolutionOrder ----------

    @Test
    fun `resolution order missing set rejected`() {
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet),
            resolutionOrder = listOf(TestPacks.entry(set = "core"), TestPacks.entry(set = "ghost")),
        )
        val msg = expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
        assertTrue(msg.contains("ghost"), "错误信息应含缺失集名: $msg")
    }

    @Test
    fun `resolution order duplicate set rejected`() {
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet),
            resolutionOrder = listOf(TestPacks.entry(set = "core"), TestPacks.entry(set = "core")),
        )
        expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
    }

    @Test
    fun `unreferenced set rejected`() {
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet, "dark" to TestPacks.setOf("dark")),
            resolutionOrder = order, // dark 未被引用
        )
        val msg = expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
        assertTrue(msg.contains("dark"), "错误信息应含未引用集名: $msg")
    }

    // ---------- 行 1：扩展组 / schema 声明 ----------

    @Test
    fun `extension group conflicting with core path rejected`() {
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet),
            resolutionOrder = order,
            extensions = mapOf(
                "chart" to ExtensionGroup(
                    "chart",
                    linkedMapOf(
                        "color.semantic.accent" to TestPacks.color("color.semantic.accent", "#FF0000"),
                    ),
                ),
            ),
        )
        val msg = expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
        assertTrue(msg.contains("color.semantic.accent"), "错误信息应含冲突路径: $msg")
    }

    @Test
    fun `schema declaration mismatch rejected`() {
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet),
            resolutionOrder = order,
            schemaDeclarations = SchemaDeclarations(
                spring = CompositeSchemas.SPRING.keys.toList() + "bounce",
            ),
        )
        expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validatePack(pack)
        }
    }

    @Test
    fun `correct schema declaration accepted`() {
        ThemeValidator.validatePack(
            TestPacks.pack(
                sets = mapOf("core" to coreSet),
                resolutionOrder = order,
                schemaDeclarations = SchemaDeclarations(
                    spring = CompositeSchemas.SPRING.keys.toList(),
                    glass = CompositeSchemas.GLASS.keys.toList(),
                ),
            ),
        )
    }

    // ---------- 行 2：词汇表类型一致 ----------

    @Test
    fun `vocabulary type mismatch rejected`() {
        val merged = mapOf(
            "color.semantic.accent" to TestPacks.number("color.semantic.accent", 1f),
        )
        val msg = expectCompileError(CompileErrorCode.TYPE_MISMATCH) {
            ThemeValidator.validateVocabularyTypes(merged)
        }
        assertTrue(msg.contains("color.semantic.accent"), "错误信息应含违规令牌: $msg")
    }

    @Test
    fun `vocabulary type check ignores custom paths`() {
        // 词汇表外路径（开放词汇）不做类型约束
        ThemeValidator.validateVocabularyTypes(
            mapOf("my.custom.path" to TestPacks.number("my.custom.path", 1f)),
        )
    }

    // ---------- 行 4：玻璃 opaque ----------

    private fun glassEffective(
        enabled: Boolean,
        consumer: String = "color.semantic.text.primary",
        consumerAlpha: Float = 1f,
    ): Map<String, TokenValue> = LinkedHashMap(FallbackTheme.rawValues).apply {
        put("glass.enabled", BooleanValue(enabled))
        put(
            consumer,
            org.minime.core.schema.ColorValue.fromHex(if (consumerAlpha == 1f) "#000000FF" else "#00000080"),
        )
    }

    @Test
    fun `glass consumer must be opaque when glass enabled`() {
        val effective = glassEffective(enabled = true, consumerAlpha = 0.5f)
        val msg = expectCompileError(CompileErrorCode.GLASS_OPAQUE_VIOLATION) {
            ThemeValidator.validateGlassConsumers(effective, TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order, glassConfig = GlassConfig(listOf("color.semantic.text.primary"))))
        }
        assertTrue(msg.contains("color.semantic.text.primary"), "错误信息应含违规令牌: $msg")
    }

    @Test
    fun `glass opaque rule skipped when glass disabled`() {
        // 玻璃关闭时（含 reducedTransparency 修饰符关闭）规则不适用
        val effective = glassEffective(enabled = false, consumerAlpha = 0.5f)
        ThemeValidator.validateGlassConsumers(effective, TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order, glassConfig = GlassConfig(listOf("color.semantic.text.primary"))))
    }

    @Test
    fun `glass consumer referencing missing token rejected`() {
        val effective = glassEffective(enabled = true)
        expectCompileError(CompileErrorCode.SCHEMA_INVALID) {
            ThemeValidator.validateGlassConsumers(effective, TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order, glassConfig = GlassConfig(listOf("color.semantic.text.ghost"))))
        }
    }

    // ---------- 行 5：WCAG 警告（不阻断） ----------

    @Test
    fun `wcag warnings do not block and carry path context`() {
        // 低对比度文本：secondary 用 30% 黑叠在近黑背景上 → 对比度远低于 4.5:1
        val effective = LinkedHashMap(FallbackTheme.rawValues).apply {
            put("color.semantic.text.secondary", org.minime.core.schema.ColorValue.fromHex("#3C3C4333"))
            put("color.semantic.bg.canvas", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
            put("color.semantic.bg.surface", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
            put("color.semantic.bg.grouped", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
            put("color.semantic.bg.surfaceSecondary", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
            put("color.semantic.bg.surfaceTertiary", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
        }
        val warnings = ThemeValidator.wcagWarnings(effective, TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order))
        val textWarnings = warnings.filter { it.code == ImportWarningCode.WCAG_TEXT_CONTRAST }
        assertTrue(textWarnings.isNotEmpty(), "应产出 WCAG 文本对比度警告")
        assertTrue(
            textWarnings.any { it.message.contains("color.semantic.text.secondary") },
            "警告应含前景令牌路径: ${textWarnings.first().message}",
        )
    }

    @Test
    fun `no wcag warning when contrast is sufficient`() {
        val effective = LinkedHashMap(FallbackTheme.rawValues).apply {
            put("color.semantic.text.primary", org.minime.core.schema.ColorValue.fromHex("#000000FF"))
            put("color.semantic.text.secondary", org.minime.core.schema.ColorValue.fromHex("#3C3C43FF"))
            put("color.semantic.text.tertiary", org.minime.core.schema.ColorValue.fromHex("#3C3C43FF"))
            put("color.semantic.text.quaternary", org.minime.core.schema.ColorValue.fromHex("#3C3C43FF"))
            put("color.semantic.text.placeholder", org.minime.core.schema.ColorValue.fromHex("#3C3C43FF"))
            put("color.semantic.accent", org.minime.core.schema.ColorValue.fromHex("#007AFFFF"))
            for (bg in listOf("canvas", "surface", "grouped", "surfaceSecondary", "surfaceTertiary")) {
                put("color.semantic.bg.$bg", org.minime.core.schema.ColorValue.fromHex("#FFFFFFFF"))
            }
        }
        val warnings = ThemeValidator.wcagWarnings(effective, TestPacks.pack(sets = mapOf("core" to coreSet), resolutionOrder = order))
        assertEquals(
            emptyList<org.minime.core.api.ImportWarning>(),
            warnings.filter { it.code == ImportWarningCode.WCAG_TEXT_CONTRAST },
            "高对比配色不应产生警告: $warnings",
        )
    }

    @Test
    fun `glass envelope warning produced when glass enabled`() {
        val effective = LinkedHashMap(FallbackTheme.rawValues).apply {
            put("glass.enabled", BooleanValue(true))
            put("color.semantic.text.primary", org.minime.core.schema.ColorValue.fromHex("#98989DFF"))
            put("glass.thin", org.minime.core.schema.GlassValue(
                blur = org.minime.core.schema.DimensionValue(12f, "dp"),
                saturation = 1.7f,
                tint = org.minime.core.schema.ColorValue.fromHex("#000000FF"),
                tintAlpha = 0.9f,
                hairline = true,
            ))
        }
        val pack = TestPacks.pack(
            sets = mapOf("core" to coreSet),
            resolutionOrder = order,
            glassConfig = GlassConfig(listOf("color.semantic.text.primary")),
        )
        val warnings = ThemeValidator.wcagWarnings(effective, pack)
        assertTrue(
            warnings.any { it.code == ImportWarningCode.WCAG_GLASS_ENVELOPE },
            "应产出玻璃包络警告: $warnings",
        )
    }

    @Test
    fun `contrast math matches known values`() {
        // 黑×白 = 21:1；同色 = 1:1（sRGB 相对亮度公式）
        val black = org.minime.core.schema.ColorValue.fromHex("#000000FF")
        val white = org.minime.core.schema.ColorValue.fromHex("#FFFFFFFF")
        val gray = org.minime.core.schema.ColorValue.fromHex("#777777FF")
        assertEquals(21f, ThemeValidator.contrast(black, white), 0.01f)
        assertEquals(1f, ThemeValidator.contrast(gray, gray), 0.01f)
        // #777 相对亮度≈0.184 < 0.5，故对黑背景对比度更高（4.69:1）于对白（4.48:1）
        assertTrue(
            ThemeValidator.contrast(gray, black) > ThemeValidator.contrast(gray, white),
            "中灰对黑对比度应更高: black=${ThemeValidator.contrast(gray, black)}, white=${ThemeValidator.contrast(gray, white)}",
        )
        // 半透明前景先合成到背景：#00000080（alpha=128/255）叠白 → #7F7F7F，约 4.0:1
        val halfBlack = org.minime.core.schema.ColorValue.fromHex("#00000080")
        assertEquals(4.0f, ThemeValidator.contrast(halfBlack, white), 0.02f)
    }

    // ---------- 词汇表自洽 ----------

    @Test
    fun `token vocabulary covers ten subsystems with declared size`() {
        assertEquals(173, org.minime.core.tokens.TokenVocabulary.SIZE)
        assertEquals(
            173,
            org.minime.core.tokens.TokenVocabulary.ALL.size,
            "SIZE 常量须与 ALL 实际条目一致",
        )
        assertTrue(
            org.minime.core.tokens.TokenVocabulary.ALL.values.containsAll(
                listOf(
                    TokenType.COLOR, TokenType.TYPOGRAPHY, TokenType.DIMENSION,
                    TokenType.SPRING, TokenType.NUMBER, TokenType.GLASS,
                ),
            ),
        )
    }

    @Test
    fun `fallback snapshot provides all vocabulary paths`() {
        // fallback 基座须覆盖全部标准词汇表路径（词汇表缺项由它回填）
        val missing = org.minime.core.tokens.TokenVocabulary.ALL.keys.filterNot {
            it in FallbackTheme.rawValues
        }
        assertEquals(emptyList<String>(), missing, "fallback 缺少词汇表路径: $missing")
    }

    @Test
    fun `number value equality`() {
        assertEquals(NumberValue(0.65f), NumberValue(0.65f))
    }
}
