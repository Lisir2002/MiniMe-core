package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.NumberValue
import org.minime.core.schema.TokenValue

/**
 * 透明度子系统（§6.8.2 OpacityTokens）：alpha 九档阶梯 + 6 个语义透明度。
 *
 * 子树键：
 * - `primitive.0` / `.04` / `.08` / `.16` / `.32` / `.50` / `.64` / `.80` / `.full`（九档阶梯）；
 * - `disabled` / `scrim` / `scrimBright` / `pressHighlight` / `veil` / `dimmed`（语义目录）。
 *
 * 边界（§6.1.2）：opacity 不改色值，只改叠加结果——与 glass 正交。
 */
@Immutable
data class OpacityTokens(
    val primitive: Map<String, TokenValue>,
    val semantic: Map<String, TokenValue>,
) {
    // ---------- alpha 九档阶梯（名即值：0/0.04/0.08/0.16/0.32/0.50/0.64/0.80/1） ----------

    val l0: NumberValue get() = primitive.number("0")
    val l04: NumberValue get() = primitive.number("04")
    val l08: NumberValue get() = primitive.number("08")
    val l16: NumberValue get() = primitive.number("16")
    val l32: NumberValue get() = primitive.number("32")
    val l50: NumberValue get() = primitive.number("50")
    val l64: NumberValue get() = primitive.number("64")
    val l80: NumberValue get() = primitive.number("80")
    val full: NumberValue get() = primitive.number("full")

    // ---------- 语义透明度 ----------

    val disabled: NumberValue get() = semantic.number("disabled")
    val scrim: NumberValue get() = semantic.number("scrim")
    val scrimBright: NumberValue get() = semantic.number("scrimBright")
    val pressHighlight: NumberValue get() = semantic.number("pressHighlight")
    val veil: NumberValue get() = semantic.number("veil")
    val dimmed: NumberValue get() = semantic.number("dimmed")
}
