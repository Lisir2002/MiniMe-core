package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.TokenValue

/**
 * 间距子系统（§6.2 SpacingTokens）：4–32 阶梯 + 页面 gutter/inset/分组间隔。
 *
 * 子树键：数字阶梯 `4`/`8`/`12`/`16`/`20`/`24`/`32`（注意 JSON 键为字符串），
 * 语义键 `gutter`/`listInset`/`dividerInset`/`section`。
 */
@Immutable
data class SpacingTokens(
    val subtree: Map<String, TokenValue>,
) {
    // ---------- 数字阶梯（主题包可改值；别名引用阶梯便于统一调整） ----------

    val s4: DimensionValue get() = subtree.dimension("4")
    val s8: DimensionValue get() = subtree.dimension("8")
    val s12: DimensionValue get() = subtree.dimension("12")
    val s16: DimensionValue get() = subtree.dimension("16")
    val s20: DimensionValue get() = subtree.dimension("20")
    val s24: DimensionValue get() = subtree.dimension("24")
    val s32: DimensionValue get() = subtree.dimension("32")

    // ---------- 页面语义 ----------

    val gutter: DimensionValue get() = subtree.dimension("gutter")
    val listInset: DimensionValue get() = subtree.dimension("listInset")
    val dividerInset: DimensionValue get() = subtree.dimension("dividerInset")
    val section: DimensionValue get() = subtree.dimension("section")
}
