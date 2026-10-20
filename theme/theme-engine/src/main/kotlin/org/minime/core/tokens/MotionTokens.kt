package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DurationValue
import org.minime.core.schema.SpringValue
import org.minime.core.schema.TokenValue

/**
 * 动效子系统（§6.3 MotionTokens）：六槽 spring + 时长档位。
 *
 * 子树键：
 * - `spatial.fast/default/slow`——位置·旋转·尺寸·形状（可回弹）；
 * - `effects.fast/default/slow`——颜色·透明度（不回弹）；
 * - `duration.instant/fast/default/slow/max/shimmer/spinner`。
 *
 * 动效人格预设（ios/standard/custom，§6.3）不建令牌——人格是消费方对槽位的组合选择，
 * 由组件层读取（RC1 决策：人格无独立真源，避免与六槽 spring 双真源）。
 */
@Immutable
data class MotionTokens(
    val spatial: Map<String, TokenValue>,
    val effects: Map<String, TokenValue>,
    val duration: Map<String, TokenValue>,
) {
    // ---------- 六槽 spring ----------

    val spatialFast: SpringValue get() = spatial.spring("fast")
    val spatialDefault: SpringValue get() = spatial.spring("default")
    val spatialSlow: SpringValue get() = spatial.spring("slow")
    val effectsFast: SpringValue get() = effects.spring("fast")
    val effectsDefault: SpringValue get() = effects.spring("default")
    val effectsSlow: SpringValue get() = effects.spring("slow")

    // ---------- 时长档位 ----------

    val durationInstant: DurationValue get() = duration.duration("instant")
    val durationFast: DurationValue get() = duration.duration("fast")
    val durationDefault: DurationValue get() = duration.duration("default")
    val durationSlow: DurationValue get() = duration.duration("slow")
    val durationMax: DurationValue get() = duration.duration("max")
    val durationShimmer: DurationValue get() = duration.duration("shimmer")
    val durationSpinner: DurationValue get() = duration.duration("spinner")
}
