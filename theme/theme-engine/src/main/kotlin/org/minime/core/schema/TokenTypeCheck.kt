package org.minime.core.schema

import org.minime.core.api.CompileErrorCode
import org.minime.core.api.ThemeCompileException

/**
 * 令牌类型与值实例的一致性判定（§5.2 类型行的共享实现）。
 *
 * 单一真源：解析期（[DtcgParser] 产出的 RawToken 天然一致）、别名解析期
 * （[org.minime.core.compiler.AliasResolver] 校验别名目标值）与覆写期
 * （[org.minime.core.compiler.OverlayApplier] 校验覆写值）共用本对象——
 * 覆写不另写一套类型判断（禁第二套实现）。
 */
object TokenTypeCheck {

    /** 值实例是否匹配声明类型 */
    fun matches(type: TokenType, value: TokenValue): Boolean = when (type) {
        TokenType.COLOR -> value is ColorValue
        TokenType.DIMENSION -> value is DimensionValue
        TokenType.DURATION -> value is DurationValue
        TokenType.NUMBER -> value is NumberValue
        TokenType.BOOLEAN -> value is BooleanValue
        TokenType.STRING, TokenType.FONT_FAMILY, TokenType.FONT_WEIGHT -> value is StringValue
        TokenType.CUBIC_BEZIER -> value is CubicBezierValue
        TokenType.TYPOGRAPHY -> value is TypographyValue
        TokenType.SHADOW -> value is ShadowValue
        TokenType.SPRING -> value is SpringValue
        TokenType.GLASS -> value is GlassValue
    }

    /** 不匹配即抛 [CompileErrorCode.TYPE_MISMATCH]（诊断含路径、期望类型、实际类型与值） */
    fun requireMatches(path: String, type: TokenType, value: TokenValue) {
        if (!matches(type, value)) {
            throw ThemeCompileException(
                CompileErrorCode.TYPE_MISMATCH,
                "令牌 \"$path\" 类型不符: 期望 $type，实际为 ${value::class.simpleName}（$value）",
            )
        }
    }
}
