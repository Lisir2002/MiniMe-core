package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.TokenValue

/**
 * 形状子系统（§6.2 ShapeTokens）：radius 阶梯 6 项。
 *
 * 子树键：`radius.button` / `radius.card` / `radius.cardLarge` / `radius.dialog` /
 * `radius.pill` / `radius.sheetTop`。
 * 连续圆角（squircle）为绘制层能力（R3 起），不在令牌树——半径令牌即真源。
 */
@Immutable
data class ShapeTokens(
    val subtree: Map<String, TokenValue>,
) {
    val radiusButton: DimensionValue get() = subtree.dimension("radius.button")
    val radiusCard: DimensionValue get() = subtree.dimension("radius.card")
    val radiusCardLarge: DimensionValue get() = subtree.dimension("radius.cardLarge")
    val radiusDialog: DimensionValue get() = subtree.dimension("radius.dialog")
    val radiusPill: DimensionValue get() = subtree.dimension("radius.pill")
    val radiusSheetTop: DimensionValue get() = subtree.dimension("radius.sheetTop")
}
