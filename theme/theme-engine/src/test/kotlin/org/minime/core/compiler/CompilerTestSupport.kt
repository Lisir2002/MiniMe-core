package org.minime.core.compiler

import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.ExtensionGroup
import org.minime.core.schema.GlassConfig
import org.minime.core.schema.Manifest
import org.minime.core.schema.NumberValue
import org.minime.core.schema.RawField
import org.minime.core.schema.RawToken
import org.minime.core.schema.ResolutionEntry
import org.minime.core.schema.SchemaDeclarations
import org.minime.core.schema.SetModel
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.ThemePack
import org.minime.core.schema.TokenType
import org.minime.core.schema.TypographyValue

/** compiler 包测试共享构造辅助（模型层直构，聚焦规则语义） */
object TestPacks {

    const val ENGINE_RANGE = ">=1.0 <2.0"

    fun color(path: String, hex: String) = RawToken(path, TokenType.COLOR, literal = ColorValue.fromHex(hex))

    fun alias(path: String, type: TokenType, target: String) = RawToken(path, type, alias = target)

    fun number(path: String, v: Float) = RawToken(path, TokenType.NUMBER, literal = NumberValue(v))

    fun dimension(path: String, v: Float, unit: String = "dp") =
        RawToken(path, TokenType.DIMENSION, literal = DimensionValue(v, unit))

    fun duration(path: String, ms: Float) = RawToken(path, TokenType.DURATION, literal = DurationValue(ms))

    fun boolean(path: String, v: Boolean) = RawToken(path, TokenType.BOOLEAN, literal = BooleanValue(v))

    fun fontFamily(path: String, v: String) = RawToken(path, TokenType.FONT_FAMILY, literal = StringValue(v))

    /** spring 复合型（全字面量字段） */
    fun spring(
        path: String,
        stiffness: Float = 380f,
        damping: Float = 0.86f,
        velocity: Float = 0f,
    ) = RawToken(
        path,
        TokenType.SPRING,
        fields = linkedMapOf(
            "stiffness" to RawField(literal = NumberValue(stiffness)),
            "damping" to RawField(literal = NumberValue(damping)),
            "initialVelocity" to RawField(literal = NumberValue(velocity)),
        ),
    )

    fun glass(
        path: String,
        blur: Float = 12f,
        tintHex: String = "#FFFFFFFF",
        tintAlias: String? = null,
        tintAlphaAlias: String? = null,
        tintAlpha: Float = 0.65f,
        hairline: Boolean = true,
        saturation: Float = 1.7f,
    ) = RawToken(
        path,
        TokenType.GLASS,
        fields = linkedMapOf(
            "blur" to RawField(literal = DimensionValue(blur, "dp")),
            "saturation" to RawField(literal = NumberValue(saturation)),
            "tint" to if (tintAlias != null) RawField(alias = tintAlias) else RawField(literal = ColorValue.fromHex(tintHex)),
            "tintAlpha" to if (tintAlphaAlias != null) RawField(alias = tintAlphaAlias) else RawField(literal = NumberValue(tintAlpha)),
            "hairline" to RawField(literal = BooleanValue(hairline)),
        ),
    )

    fun typography(
        path: String,
        fontSizeSp: Float = 17f,
        fontFamilyAlias: String? = null,
        fontFamilyLiteral: String? = "sans-serif",
    ) = RawToken(
        path,
        TokenType.TYPOGRAPHY,
        fields = linkedMapOf(
            "fontSize" to RawField(literal = DimensionValue(fontSizeSp, "sp")),
            "fontFamily" to when {
                fontFamilyAlias != null -> RawField(alias = fontFamilyAlias)
                fontFamilyLiteral != null -> RawField(literal = StringValue(fontFamilyLiteral))
                else -> RawField(literal = StringValue("sans-serif"))
            },
        ),
    )

    fun pack(
        name: String = "test-pack",
        version: String = "1.0.0",
        engineVersion: String = ENGINE_RANGE,
        sets: Map<String, SetModel>,
        resolutionOrder: List<ResolutionEntry>,
        extensions: Map<String, ExtensionGroup> = emptyMap(),
        glassConfig: GlassConfig = GlassConfig(),
        schemaDeclarations: SchemaDeclarations = SchemaDeclarations(),
    ) = ThemePack(
        dcgVersion = "2025.10",
        manifest = Manifest(name, version, author = "test", engineVersion = engineVersion, description = null, preview = null),
        sets = sets,
        resolutionOrder = resolutionOrder,
        extensions = extensions,
        glassConfig = glassConfig,
        schemaDeclarations = schemaDeclarations,
    )

    fun setOf(name: String, vararg tokens: RawToken) =
        SetModel(name, linkedMapOf(*tokens.map { it.path to it }.toTypedArray()))

    fun entry(set: String? = null, modifier: String? = null) = ResolutionEntry(set = set, modifier = modifier)

    // ---------- 常用小包 ----------

    /** 单 set（core），无修饰符 */
    fun simple(vararg tokens: RawToken): ThemePack = pack(
        sets = mapOf("core" to setOf("core", *tokens)),
        resolutionOrder = listOf(entry(set = "core")),
    )

    /** core + 一个 dark 修饰符集 */
    fun withModifier(core: List<RawToken>, dark: List<RawToken>): ThemePack = pack(
        sets = mapOf(
            "core" to setOf("core", *core.toTypedArray()),
            "dark" to setOf("dark", *dark.toTypedArray()),
        ),
        resolutionOrder = listOf(entry(set = "core"), entry(modifier = "dark")),
    )
}

/** 断言抛出指定 errorCode 的 ThemeCompileException */
inline fun <T> expectCompileError(code: org.minime.core.api.CompileErrorCode, block: () -> T): String {
    try {
        block()
        error("期望抛出 ThemeCompileException($code)，实际未抛")
    } catch (e: org.minime.core.api.ThemeCompileException) {
        check(e.code == code) { "期望 errorCode=$code，实际 ${e.code}: ${e.message}" }
        return e.message ?: ""
    }
}

/** 便捷：构造解析后的 spring 字面值（校验用） */
fun springOf(stiffness: Float, damping: Float, velocity: Float) =
    SpringValue(stiffness, damping, velocity)

/** 便捷：typography 期望值 */
fun typographyOf(fontSize: Float, family: String) =
    TypographyValue(fontSize = DimensionValue(fontSize, "sp"), fontFamily = family)
