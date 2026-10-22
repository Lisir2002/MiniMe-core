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
enum class TokenType(val isComposite: Boolean, val rawName: String) {
    COLOR(false, "color"),
    DIMENSION(false, "dimension"),
    DURATION(false, "duration"),
    NUMBER(false, "number"),
    BOOLEAN(false, "boolean"),
    STRING(false, "string"),
    FONT_FAMILY(false, "fontFamily"),
    FONT_WEIGHT(false, "fontWeight"),
    CUBIC_BEZIER(false, "cubicBezier"),
    TYPOGRAPHY(true, "typography"),
    SHADOW(true, "shadow"),
    SPRING(true, "spring"),
    GLASS(true, "glass"),
    ;

    companion object {
        /** DTCG 原始拼写 → 枚举（大小写不敏感：fontfamily/fontFamily 均可匹配） */
        private val RAW_NAMES: Map<String, TokenType> =
            entries.associateBy { it.rawName.lowercase() }

        fun fromRaw(raw: String): TokenType? = RAW_NAMES[raw.trim().lowercase()]
    }
}
