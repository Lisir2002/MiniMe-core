package org.minime.core.schema

/**
 * 令牌类型白名单（白皮书 §6.1.3 + §5.1 示例补充）。
 *
 * - DTCG Format 原生型直接沿用：color / dimension / duration / number / fontFamily /
 *   fontWeight / typography / shadow / cubicBezier；
 * - `spring` 与 `glass` 是**引擎自定义型**（DTCG 无弹簧与材质配方类型），
 *   字段 schema 在主题包 `$extensions.minime.schema` 声明并纳入导入校验（§5.2）；
 * - `boolean` / `string` 由 §5.1 示例（glass.enabled）与通用文本令牌需要纳入白名单。
 */
enum class TokenType(val isComposite: Boolean) {
    COLOR(false),
    DIMENSION(false),
    DURATION(false),
    NUMBER(false),
    BOOLEAN(false),
    STRING(false),
    FONT_FAMILY(false),
    FONT_WEIGHT(false),
    CUBIC_BEZIER(false),
    TYPOGRAPHY(true),
    SHADOW(true),
    SPRING(true),
    GLASS(true),
    ;

    companion object {
        /** DTCG 原始拼写 → 枚举（大小写不敏感：fontfamily/fontFamily 均可匹配） */
        private val RAW_NAMES: Map<String, TokenType> = mapOf(
            "color" to COLOR,
            "dimension" to DIMENSION,
            "duration" to DURATION,
            "number" to NUMBER,
            "boolean" to BOOLEAN,
            "string" to STRING,
            "fontfamily" to FONT_FAMILY,
            "fontweight" to FONT_WEIGHT,
            "cubicbezier" to CUBIC_BEZIER,
            "typography" to TYPOGRAPHY,
            "shadow" to SHADOW,
            "spring" to SPRING,
            "glass" to GLASS,
        )

        fun fromRaw(raw: String): TokenType? = RAW_NAMES[raw.trim().lowercase()]
    }
}
