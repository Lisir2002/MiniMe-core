package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.BooleanValue
import org.minime.core.schema.GlassValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.TokenValue

/**
 * 玻璃材质子系统（§6.8.3 GlassTokens）：四级材质配方 + 开关。
 *
 * 子树键：`enabled` / `reducedTransparency`（boolean 开关）+ `ultraThin` / `thin` /
 * `regular` / `thick`（glass 复合配方）+ `*.tintAlpha`（复合字段独立覆写点，§5.1 字段别名）。
 *
 * 边界（§3.6 妥协 3）：默认主题零玻璃（enabled=false），玻璃是可选装饰；
 * 降级链与性能档位见 §6.8.4（消费方为绘制层，R9 落地）。
 */
@Immutable
data class GlassTokens(
    val subtree: Map<String, TokenValue>,
) {
    // ---------- 开关 ----------

    val enabled: BooleanValue get() = subtree.boolean("enabled")
    val reducedTransparency: BooleanValue get() = subtree.boolean("reducedTransparency")

    // ---------- 四级材质配方（§6.8.3：ultraThin→thick，blur 8/12/20/32dp） ----------

    val ultraThin: GlassValue get() = subtree.glass("ultraThin")
    val thin: GlassValue get() = subtree.glass("thin")
    val regular: GlassValue get() = subtree.glass("regular")
    val thick: GlassValue get() = subtree.glass("thick")

    // ---------- 复合字段独立覆写点（tintAlpha 可脱离材质整体单独换值） ----------

    val ultraThinTintAlpha: NumberValue get() = subtree.number("ultraThin.tintAlpha")
    val thinTintAlpha: NumberValue get() = subtree.number("thin.tintAlpha")
    val regularTintAlpha: NumberValue get() = subtree.number("regular.tintAlpha")
    val thickTintAlpha: NumberValue get() = subtree.number("thick.tintAlpha")
}
