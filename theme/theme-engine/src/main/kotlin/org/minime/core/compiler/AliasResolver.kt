package org.minime.core.compiler

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.ColorValue
import org.minime.core.schema.CompositeSchemas
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.RawField
import org.minime.core.schema.RawToken
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenType
import org.minime.core.schema.TokenTypeCheck
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue

/**
 * 别名解析器（§5.1 规则 1「先合并后解析」的解析半场 + 别名拓扑校验）。
 *
 * 输入为合并后的原始令牌表（含别名引用），输出全量字面值表。
 * 校验（§5.2 别名行）：
 * - 断链：别名目标不存在于合并表 → ALIAS_BROKEN；
 * - 循环：别名链成环 → ALIAS_CYCLE（带环路径诊断）；
 * - 类型不符：别名目标值类型 ≠ 声明类型（含复合型字段别名的字段类型）→ TYPE_MISMATCH；
 * - 复合型整体别名：目标须为同型复合值（直接复用，不复制字段）。
 *
 * 解析在合并表内闭环——别名不得引用词汇表外路径（词汇表缺项由 fallback 回填，
 * 不参与别名解析，见 ThemeCompiler 优先级链）。
 */
class AliasResolver(private val tokens: Map<String, RawToken>) {

    private val resolved = HashMap<String, TokenValue>()
    private val resolving = LinkedHashSet<String>()

    /** 解析全部令牌；返回路径 → 字面值 */
    fun resolveAll(): Map<String, TokenValue> {
        for (path in tokens.keys) resolve(path)
        return resolved
    }

    private fun resolve(path: String): TokenValue {
        resolved[path]?.let { return it }
        if (path in resolving) {
            throw ThemeCompileException(
                CompileErrorCode.ALIAS_CYCLE,
                "别名循环: ${resolving.joinToString(" → ")} → $path",
            )
        }
        val token = tokens[path]
            ?: throw ThemeCompileException(CompileErrorCode.ALIAS_BROKEN, "别名断链: \"$path\" 不存在于合并令牌表")

        resolving.add(path)
        val value = try {
            when {
                token.alias != null -> resolveAlias(path, token.declaredType, token.alias)
                token.fields != null -> resolveComposite(path, token)
                else -> token.literal!!
            }
        } finally {
            resolving.remove(path)
        }
        resolved[path] = value
        return value
    }

    /**
     * 顶层别名：目标值类型须与声明类型一致（复合型整体别名复用目标复合值）。
     * 断链诊断同时给出「引用方路径」与「缺失目标路径」——前者定位主题包中的问题令牌，
     * 后者定位需要补写的值。
     */
    private fun resolveAlias(path: String, declaredType: TokenType, alias: String): TokenValue {
        if (alias !in tokens) {
            throw ThemeCompileException(
                CompileErrorCode.ALIAS_BROKEN,
                "令牌 \"$path\" 的别名目标 \"$alias\" 不存在于合并令牌表",
            )
        }
        val target = resolve(alias)
        checkValueType(path, declaredType, target, "别名 $alias 的目标值")
        return target
    }

    /** 复合型：逐字段解析（字面量或别名），按 schema 装配 */
    private fun resolveComposite(path: String, token: RawToken): TokenValue {
        val schema = CompositeSchemas.forType(token.declaredType)
            ?: error("引擎内部错误: 复合型 ${token.declaredType} 缺少 schema（$path）")
        val fields: Map<String, RawField> = token.fields.orEmpty()

        val fieldValues = HashMap<String, TokenValue>()
        for ((field, raw: RawField) in fields) {
            val expected = schema[field]!!
            val value = if (raw.alias != null) {
                if (raw.alias !in tokens) {
                    throw ThemeCompileException(
                        CompileErrorCode.ALIAS_BROKEN,
                        "复合型 \"$path\" 字段 \"$field\" 的别名目标 \"${raw.alias}\" 不存在于合并令牌表",
                    )
                }
                val target = resolve(raw.alias)
                checkValueType(path, expected, target, "字段 \"$field\" 别名 ${raw.alias} 的目标值")
                target
            } else {
                raw.literal!!.also {
                    // 字面量字段在解析期已按 schema 类型解析（DtcgParser.parseScalar），双重保险
                    checkValueType(path, expected, it, "字段 \"$field\" 字面量")
                }
            }
            fieldValues[field] = value
        }

        // 必填字段校验（§6.1.3 schema：spring/glass 全字段必填；typography 须 fontSize；shadow 须 color）
        val missing = CompositeSchemas.REQUIRED_FIELDS[token.declaredType]
            ?.filterNot { it in fieldValues }
            .orEmpty()
        if (missing.isNotEmpty()) {
            throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "复合型 \"$path\"（${token.declaredType}）缺少必填字段: $missing",
            )
        }

        return assemble(token.declaredType, fieldValues, path)
    }

    private fun assemble(
        type: TokenType,
        fields: Map<String, TokenValue>,
        path: String,
    ): TokenValue = when (type) {
        TokenType.SPRING -> SpringValue(
            stiffness = fields.number("stiffness", path),
            damping = fields.number("damping", path),
            initialVelocity = fields.number("initialVelocity", path),
        )

        TokenType.GLASS -> GlassValue(
            blur = fields.dimension("blur", path),
            saturation = fields.number("saturation", path),
            tint = fields.color("tint", path),
            tintAlpha = fields.number("tintAlpha", path),
            hairline = fields.boolean("hairline", path),
        )

        TokenType.TYPOGRAPHY -> TypographyValue(
            fontSize = fields.dimension("fontSize", path),
            lineHeight = fields["lineHeight"]?.let { it as? DimensionValue },
            fontWeight = fields["fontWeight"]?.let { it as? StringValue }?.value,
            fontFamily = fields["fontFamily"]?.let { it as? StringValue }?.value,
            letterSpacing = fields["letterSpacing"]?.let { it as? DimensionValue },
        )

        TokenType.SHADOW -> ShadowValue(
            color = fields.color("color", path),
            blur = fields["blur"]?.let { it as? DimensionValue },
            offsetX = fields["offsetX"]?.let { it as? DimensionValue },
            offsetY = fields["offsetY"]?.let { it as? DimensionValue },
        )

        else -> error("引擎内部错误: ${type} 不是复合型（$path）")
    }

    // ---------- 类型校验 ----------

    private fun checkValueType(path: String, expected: TokenType, actual: TokenValue, what: String) {
        if (!TokenTypeCheck.matches(expected, actual)) {
            throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "令牌 \"$path\" 类型不符: 期望 $expected，$what 实际为 ${actual::class.simpleName}（$actual）",
            )
        }
    }

    private fun Map<String, TokenValue>.number(field: String, path: String): Float =
        (this[field] as? NumberValue)?.value
            ?: error("引擎内部错误: $path 字段 $field 不是 number")

    private fun Map<String, TokenValue>.dimension(field: String, path: String): DimensionValue =
        (this[field] as? DimensionValue)
            ?: error("引擎内部错误: $path 字段 $field 不是 dimension")

    private fun Map<String, TokenValue>.color(field: String, path: String): ColorValue =
        (this[field] as? ColorValue)
            ?: error("引擎内部错误: $path 字段 $field 不是 color")

    private fun Map<String, TokenValue>.boolean(field: String, path: String): Boolean =
        (this[field] as? BooleanValue)?.value
            ?: error("引擎内部错误: $path 字段 $field 不是 boolean")
}
