package com.mini.me_core.feature.agent.presentation.component.richsegment

/**
 * HTML 实体解码（审计 P2-14）。
 *
 * 问题：模型输出里常出现 `&amp;` `&lt;` `&gt;` `&#39;` 这类实体，直接渲染会原样显示，
 * 用户看到的是 "&amp;" 而不是 "&"。
 *
 * 设计约束：
 *  - 纯函数、无 Android 依赖（不依赖 `android.text.Html`，那会带入整套 HTML 解析语义，
 *    且无法在普通 JVM 单测里验证）；
 *  - 只做「解码」，不做反转义循环——`&amp;amp;` 解一次得到 `&amp;`，这是正确的单遍语义；
 *  - 代码块内容不经过这里（块级阶段已整段隔离），保证代码里的实体原样呈现。
 */
object HtmlEntities {

    /** 命名实体表：只收模型输出里真正会出现的常见实体，不做全量 HTML5 实体表。 */
    private val NAMED: Map<String, String> = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
        "hellip" to "\u2026",
        "mdash" to "\u2014",
        "ndash" to "\u2013",
        "copy" to "\u00A9",
        "reg" to "\u00AE",
        "trade" to "\u2122",
        "lsquo" to "\u2018",
        "rsquo" to "\u2019",
        "ldquo" to "\u201C",
        "rdquo" to "\u201D",
        "times" to "\u00D7",
        "middot" to "\u00B7",
        "bull" to "\u2022",
        "dagger" to "\u2020",
        "permil" to "\u2030",
        "euro" to "\u20AC",
        "pound" to "\u00A3",
        "yen" to "\u00A5",
        "cent" to "\u00A2",
        "sect" to "\u00A7",
        "para" to "\u00B6",
        "deg" to "\u00B0",
        "plusmn" to "\u00B1",
        "frac12" to "\u00BD",
        "sup2" to "\u00B2",
        "sup3" to "\u00B3",
        "micro" to "\u00B5",
        "larr" to "\u2190",
        "rarr" to "\u2192",
        "uarr" to "\u2191",
        "darr" to "\u2193",
        "harr" to "\u2194"
    )

    // &#39; / &#x27; 两类数字实体
    private val NUMERIC_DEC = Regex("&#(\\d{1,7});")
    private val NUMERIC_HEX = Regex("&#[xX]([0-9a-fA-F]{1,6});")

    /** 命名实体（含分号），如 `&amp;`。 */
    private val NAMED_PATTERN = Regex("&([a-zA-Z][a-zA-Z0-9]{1,31});")

    /**
     * 解码文本中的 HTML 实体。
     *
     * 未登记的实体原样保留（不猜、不清除）——宁可显示原始实体，也不要静默丢字符。
     */
    fun decode(text: String): String {
        if (text.isEmpty() || text.indexOf('&') < 0) return text
        var s = NUMERIC_DEC.replace(text) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                m.value
            }
        }
        s = NUMERIC_HEX.replace(s) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                m.value
            }
        }
        s = NAMED_PATTERN.replace(s) { m ->
            NAMED[m.groupValues[1]] ?: m.value
        }
        return s
    }
}
