package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.StringValue
import org.minime.core.schema.TokenValue
import org.minime.core.schema.TypographyValue

/**
 * 字体子系统（§6.2 TypographyTokens）：iOS 字阶 11 级 + 代码等宽 + 行高字距。
 *
 * 子树键（相对 typography 根）：`fontFamily.sans` / `fontFamily.mono`（fontFamily 型），
 * `largeTitle`/`title1`/`title2`/`title3`/`headline`/`body`/`callout`/`subheadline`/
 * `footnote`/`caption1`/`caption2`/`monospace`（typography 复合型）。
 */
@Immutable
data class TypographyTokens(
    val subtree: Map<String, TokenValue>,
) {
    // ---------- 字体族 ----------

    val fontFamilySans: StringValue get() = subtree.string("fontFamily.sans")
    val fontFamilyMono: StringValue get() = subtree.string("fontFamily.mono")

    // ---------- iOS 字阶 11 级 + 代码等宽 ----------

    val largeTitle: TypographyValue get() = subtree.typography("largeTitle")
    val title1: TypographyValue get() = subtree.typography("title1")
    val title2: TypographyValue get() = subtree.typography("title2")
    val title3: TypographyValue get() = subtree.typography("title3")
    val headline: TypographyValue get() = subtree.typography("headline")
    val body: TypographyValue get() = subtree.typography("body")
    val callout: TypographyValue get() = subtree.typography("callout")
    val subheadline: TypographyValue get() = subtree.typography("subheadline")
    val footnote: TypographyValue get() = subtree.typography("footnote")
    val caption1: TypographyValue get() = subtree.typography("caption1")
    val caption2: TypographyValue get() = subtree.typography("caption2")
    val monospace: TypographyValue get() = subtree.typography("monospace")
}
