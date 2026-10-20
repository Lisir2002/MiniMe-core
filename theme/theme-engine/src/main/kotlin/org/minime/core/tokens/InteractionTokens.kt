package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.DurationValue
import org.minime.core.schema.NumberValue
import org.minime.core.schema.TokenValue

/**
 * 交互子系统（§6.1.4/§6.2 InteractionTokens）：手势阈值/按压系数/抖动/时序/触觉档位。
 *
 * 边界约定（§6.1.2）：motion 管「动画怎么动」，interaction 管「交互怎么判定」——
 * 本子系统全部为行为参数，可被主题包覆盖。
 *
 * 子树键（26 项）：`flickVelocity` / `flickDismissRatio` / `dragDismissRatio` /
 * `backDismissRatio` / `swipeRevealRatio` / `swipeActionRatio` / `pressScale` /
 * `cardPressScale` / `overlayScale` / `shakeDistance` / `shakeCycles` /
 * `itemEnterOffset` / `stagger.menu` / `stagger.list` / `maxStaggerItems` /
 * `streamFloodThreshold` / `rubberBandDamping` / `parallaxFactor` /
 * `longPressTimeout` / `bannerAutoDismiss` / `selectSheetThreshold` /
 * `chevronRotation` / `badgePopScale` / `haptic.light` / `haptic.medium` / `haptic.heavy`。
 */
@Immutable
data class InteractionTokens(
    val subtree: Map<String, TokenValue>,
) {
    // ---------- 手势阈值（§6.4.4/§6.5.2/§6.6.3） ----------

    val flickVelocity: NumberValue get() = subtree.number("flickVelocity")
    val flickDismissRatio: NumberValue get() = subtree.number("flickDismissRatio")
    val dragDismissRatio: NumberValue get() = subtree.number("dragDismissRatio")
    val backDismissRatio: NumberValue get() = subtree.number("backDismissRatio")
    val swipeRevealRatio: NumberValue get() = subtree.number("swipeRevealRatio")
    val swipeActionRatio: NumberValue get() = subtree.number("swipeActionRatio")
    val rubberBandDamping: NumberValue get() = subtree.number("rubberBandDamping")
    val parallaxFactor: NumberValue get() = subtree.number("parallaxFactor")

    // ---------- 按压/进入系数（§6.4.1/§6.4.2） ----------

    val pressScale: NumberValue get() = subtree.number("pressScale")
    val cardPressScale: NumberValue get() = subtree.number("cardPressScale")
    val overlayScale: NumberValue get() = subtree.number("overlayScale")
    val badgePopScale: NumberValue get() = subtree.number("badgePopScale")
    val chevronRotation: NumberValue get() = subtree.number("chevronRotation")

    // ---------- 抖动（§6.4.2） ----------

    val shakeDistance: DimensionValue get() = subtree.dimension("shakeDistance")
    val shakeCycles: NumberValue get() = subtree.number("shakeCycles")
    val itemEnterOffset: DimensionValue get() = subtree.dimension("itemEnterOffset")

    // ---------- 时序参数（§6.4.1/§6.5.4/§6.6.4） ----------

    val staggerMenu: DurationValue get() = subtree.duration("stagger.menu")
    val staggerList: DurationValue get() = subtree.duration("stagger.list")
    val maxStaggerItems: NumberValue get() = subtree.number("maxStaggerItems")
    val streamFloodThreshold: NumberValue get() = subtree.number("streamFloodThreshold")
    val longPressTimeout: DurationValue get() = subtree.duration("longPressTimeout")
    val bannerAutoDismiss: DurationValue get() = subtree.duration("bannerAutoDismiss")
    val selectSheetThreshold: NumberValue get() = subtree.number("selectSheetThreshold")

    // ---------- 触觉档位（§6.4.2） ----------

    val hapticLight: NumberValue get() = subtree.number("haptic.light")
    val hapticMedium: NumberValue get() = subtree.number("haptic.medium")
    val hapticHeavy: NumberValue get() = subtree.number("haptic.heavy")
}
