package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ImportWarning
import org.minime.core.api.ImportWarningCode
import org.minime.core.api.MiniMeEngine
import org.minime.core.api.ThemeCompileException
import org.minime.core.schema.ColorValue
import org.minime.core.schema.CompositeSchemas
import org.minime.core.schema.RawToken
import org.minime.core.schema.SemVer
import org.minime.core.schema.SetModel
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenValue
import org.minime.core.schema.ThemePack
import org.minime.core.tokens.TokenVocabulary
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 导入期校验（白皮书 §5.2 校验表全行 + §6.8.5 玻璃三条静态规则）。
 *
 * 阻断行（拒绝导入）：schema/manifest、engineVersion 兼容区间、resolutionOrder 引用完整性、
 * 扩展组冲突、schema 声明一致、词汇表类型一致、别名拓扑（AliasResolver）、玻璃 opaque、
 * 体积上限（DtcgParser）、禁代码执行（纯数据模型天然保证）。
 * 警告行（不阻断）：WCAG 对比度（text×bg 4.5:1、accent×surface 3:1）与玻璃等效底色包络。
 */
object ThemeValidator {

    // ---------- §5.2 行 1：schema / manifest / engineVersion ----------

    /** 结构校验：engineVersion 兼容区间 + resolutionOrder 引用完整性 + 扩展组 + schema 声明 */
    fun validatePack(pack: ThemePack) {
        validateEngineVersion(pack)
        validateResolutionOrder(pack)
        validateExtensions(pack)
        validateSchemaDeclarations(pack)
    }

    private fun validateEngineVersion(pack: ThemePack) {
        val engine = try {
            SemVer.parse(MiniMeEngine.VERSION)
        } catch (e: IllegalArgumentException) {
            throw ThemeCompileException(
                CompileErrorCode.ENGINE_INTERNAL,
                "引擎版本号非法: ${MiniMeEngine.VERSION}（${e.message}）",
            )
        }
        val ok = try {
            SemVer.satisfies(engine, pack.manifest.engineVersion)
        } catch (e: IllegalArgumentException) {
            throw ThemeCompileException(
                CompileErrorCode.SCHEMA_INVALID,
                "manifest.engineVersion 区间非法: \"${pack.manifest.engineVersion}\"（${e.message}）",
            )
        }
        if (!ok) {
            throw ThemeCompileException(
                CompileErrorCode.ENGINE_VERSION_INCOMPATIBLE,
                "主题包要求引擎版本 ${pack.manifest.engineVersion}，当前引擎 ${MiniMeEngine.VERSION}",
            )
        }
    }

    /** resolutionOrder：引用必须解析；每个 set 被引用且仅一次（公共集与修饰符集合计） */
    fun validateResolutionOrder(pack: ThemePack) {
        val referenced = LinkedHashMap<String, Int>()
        for ((index, entry) in pack.resolutionOrder.withIndex()) {
            val name = entry.set ?: entry.modifier!!
            if (name !in pack.sets) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "resolutionOrder[$index] 引用不存在的集: \"$name\"（sets: ${pack.sets.keys.sorted()}）",
                )
            }
            referenced[name] = (referenced[name] ?: 0) + 1
        }
        val duplicated = referenced.filterValues { it > 1 }.keys
        if (duplicated.isNotEmpty()) {
            throw ThemeCompileException(
                CompileErrorCode.SCHEMA_INVALID,
                "resolutionOrder 重复引用集: ${duplicated.sorted()}（每个 set 须被引用且仅一次）",
            )
        }
        val unreferenced = pack.sets.keys - referenced.keys
        if (unreferenced.isNotEmpty()) {
            throw ThemeCompileException(
                CompileErrorCode.SCHEMA_INVALID,
                "存在未被 resolutionOrder 引用的集: ${unreferenced.sorted()}（每个 set 须被引用且仅一次）",
            )
        }
    }

    /** 扩展组（$extensions.minime.*）路径不得与核心集令牌路径冲突 */
    private fun validateExtensions(pack: ThemePack) {
        if (pack.extensions.isEmpty()) return
        val setPaths: Set<String> = pack.sets.values.flatMap { it.tokens.keys }.toSet()
        for (group in pack.extensions.values) {
            val conflicts = group.tokens.keys.filter { it in setPaths }
            if (conflicts.isNotEmpty()) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "扩展组 \"${group.name}\" 与核心令牌路径冲突: ${conflicts.sorted()}",
                )
            }
        }
    }

    /** `$extensions.minime.schema` 声明须与引擎 CompositeSchemas 完全一致 */
    private fun validateSchemaDeclarations(pack: ThemePack) {
        pack.schemaDeclarations.spring?.let { declared ->
            val expected = CompositeSchemas.SPRING.keys
            if (declared.toSet() != expected || declared.size != expected.size) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "${'$'}extensions.minime.schema.spring 字段声明与引擎不一致: 声明=${declared.sorted()}，引擎=${expected.sorted()}",
                )
            }
        }
        pack.schemaDeclarations.glass?.let { declared ->
            val expected = CompositeSchemas.GLASS.keys
            if (declared.toSet() != expected || declared.size != expected.size) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "${'$'}extensions.minime.schema.glass 字段声明与引擎不一致: 声明=${declared.sorted()}，引擎=${expected.sorted()}",
                )
            }
        }
    }

    // ---------- §5.2 行 2：词汇表类型一致 ----------

    /** 合并表中落在标准词汇表内的令牌，声明类型须与词汇表一致（词汇表外路径允许自定义） */
    fun validateVocabularyTypes(merged: Map<String, RawToken>) {
        for ((path, token) in merged) {
            val expected = TokenVocabulary.typeOf(path) ?: continue
            if (token.declaredType != expected) {
                throw ThemeCompileException(
                    CompileErrorCode.TYPE_MISMATCH,
                    "令牌 \"$path\" 声明类型 ${token.declaredType} 与词汇表不符（期望 $expected）",
                )
            }
        }
    }

    // ---------- §5.2 行 4：玻璃表面文本/图标 opaque（§6.8.5-2） ----------

    /**
     * 玻璃 opaque 校验：仅当生效表 glass.enabled = true 时执行——
     * 玻璃关闭时无玻璃表面，规则不适用（reducedTransparency 等修饰符可将其关闭）。
     * 声明消费但令牌不存在/非颜色 → SCHEMA_INVALID；alpha ≠ 1 → GLASS_OPAQUE_VIOLATION。
     */
    fun validateGlassConsumers(effective: Map<String, TokenValue>, pack: ThemePack) {
        val consumers = pack.glassConfig.consumers
        if (consumers.isEmpty()) return
        val glassEnabled = (effective["glass.enabled"] as? org.minime.core.schema.BooleanValue)?.value == true
        if (!glassEnabled) return

        for (path in consumers) {
            val value = effective[path]
                ?: throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "玻璃表面消费配置引用了不存在的令牌: \"$path\"",
                )
            if (value !is ColorValue) {
                throw ThemeCompileException(
                    CompileErrorCode.SCHEMA_INVALID,
                    "玻璃表面消费令牌 \"$path\" 不是 color 类型（${value::class.simpleName}）",
                )
            }
            if (value.alpha != 1f) {
                throw ThemeCompileException(
                    CompileErrorCode.GLASS_OPAQUE_VIOLATION,
                    "玻璃表面消费令牌 \"$path\" 必须 opaque（alpha=1），当前 alpha=${value.alpha}（${value.hex}）",
                )
            }
        }
    }

    // ---------- §5.2 行 5：WCAG 对比度（警告不阻断） ----------

    private const val TEXT_CONTRAST = 4.5f   // 正文文本（WCAG 1.4.3 AA）
    private const val ACCENT_CONTRAST = 3.0f // 强调色 × 表面（WCAG 1.4.11 非文本/UI）

    private val TEXT_TOKENS = listOf(
        "color.semantic.text.primary",
        "color.semantic.text.secondary",
        "color.semantic.text.tertiary",
        "color.semantic.text.quaternary",
        "color.semantic.text.placeholder",
    )
    private val BG_TOKENS = listOf(
        "color.semantic.bg.canvas",
        "color.semantic.bg.surface",
        "color.semantic.bg.grouped",
        "color.semantic.bg.surfaceSecondary",
        "color.semantic.bg.surfaceTertiary",
    )

    /**
     * WCAG 警告扫描（不阻断，设置页明示）：
     * - 文本 × 背景：五档 label × 五档背景，正文阈值 4.5:1；
     * - 强调色 × 表面（canvas/surface）：3:1；
     * - 玻璃等效底色包络（§6.8.5-3）：tint × tintAlpha 叠 #FFFFFF/#000000 取更差结果，
     *   对声明消费的文本令牌按 4.5:1 校验。
     */
    fun wcagWarnings(effective: Map<String, TokenValue>, pack: ThemePack): List<ImportWarning> {
        val warnings = ArrayList<ImportWarning>()
        fun color(path: String): ColorValue? = effective[path] as? ColorValue

        for (fgPath in TEXT_TOKENS) {
            val fg = color(fgPath) ?: continue
            for (bgPath in BG_TOKENS) {
                val bg = color(bgPath) ?: continue
                val ratio = contrast(fg, bg)
                if (ratio < TEXT_CONTRAST) {
                    warnings += ImportWarning(
                        code = ImportWarningCode.WCAG_TEXT_CONTRAST,
                        message = "文本 $fgPath × 背景 $bgPath 对比度 ${fmt(ratio)}:1 < ${fmt(TEXT_CONTRAST)}:1",
                    )
                }
            }
        }

        val accent = color("color.semantic.accent")
        if (accent != null) {
            for (bgPath in listOf("color.semantic.bg.surface", "color.semantic.bg.canvas")) {
                val bg = color(bgPath) ?: continue
                val ratio = contrast(accent, bg)
                if (ratio < ACCENT_CONTRAST) {
                    warnings += ImportWarning(
                        code = ImportWarningCode.WCAG_TEXT_CONTRAST,
                        message = "强调色 color.semantic.accent × $bgPath 对比度 ${fmt(ratio)}:1 < ${fmt(ACCENT_CONTRAST)}:1",
                    )
                }
            }
        }

        warnings += glassEnvelopeWarnings(effective, pack)
        return warnings
    }

    /** §6.8.5-3 等效底色包络：tint 实色 × tintAlpha 叠两种极端背景，取更差结果 */
    private fun glassEnvelopeWarnings(
        effective: Map<String, TokenValue>,
        pack: ThemePack,
    ): List<ImportWarning> {
        val enabled = (effective["glass.enabled"] as? org.minime.core.schema.BooleanValue)?.value == true
        if (!enabled) return emptyList()
        val consumers = pack.glassConfig.consumers.filter { effective[it] is ColorValue }
        if (consumers.isEmpty()) return emptyList()

        val out = ArrayList<ImportWarning>()
        for (material in listOf("glass.ultraThin", "glass.thin", "glass.regular", "glass.thick")) {
            val recipe = effective[material] as? org.minime.core.schema.GlassValue ?: continue
            val overWhite = recipe.tint.withAlpha(recipe.tintAlpha).over(ColorValue.WHITE)
            val overBlack = recipe.tint.withAlpha(recipe.tintAlpha).over(ColorValue.BLACK)
            for (textPath in consumers) {
                val text = effective[textPath] as ColorValue
                val worst = minOf(contrast(text, overWhite), contrast(text, overBlack))
                if (worst < TEXT_CONTRAST) {
                    out += ImportWarning(
                        code = ImportWarningCode.WCAG_GLASS_ENVELOPE,
                        message = "玻璃 $material 包络 × $textPath 对比度 ${fmt(worst)}:1 < ${fmt(TEXT_CONTRAST)}:1" +
                            "（等效底色 ${overWhite.hex}/${overBlack.hex}）",
                    )
                }
            }
        }
        return out
    }

    // ---------- WCAG 计算 ----------

    /** 对比度（前景先按 alpha 合成到背景上；WCAG 2.x relative luminance） */
    fun contrast(foreground: ColorValue, background: ColorValue): Float {
        val fg = if (foreground.alpha < 1f) foreground.over(background) else foreground
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun relativeLuminance(color: ColorValue): Float {
        fun channel(v: Int): Float {
            val c = v / 255f
            return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    private fun fmt(v: Float): String {
        val rounded = (v * 100).roundToInt() / 100f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }
}
