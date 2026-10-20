package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.ShadowValue
import org.minime.core.schema.TokenValue

/**
 * 层级子系统（§6.2 ElevationTokens）：发丝线 + 浮层极轻投影。
 *
 * 子树键：`hairline`（dimension）/ `overlay`（shadow 复合）。
 * 模糊档位移至 glass 子系统（v1.8 边界）——elevation 管「层级深度」，glass 管「材质配方」。
 */
@Immutable
data class ElevationTokens(
    val subtree: Map<String, TokenValue>,
) {
    val hairline: DimensionValue get() = subtree.dimension("hairline")
    val overlay: ShadowValue get() = subtree["overlay"] as? ShadowValue
        ?: error("快照缺少 shadow 令牌: overlay")
}
