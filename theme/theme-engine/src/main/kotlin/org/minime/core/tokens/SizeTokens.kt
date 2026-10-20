package org.minime.core.tokens

import org.minime.core.api.Immutable
import org.minime.core.schema.DimensionValue
import org.minime.core.schema.TokenValue

/**
 * 尺寸子系统（§6.1.4/§6.2 SizeTokens）：触达/按钮/输入/行高/导航/标签栏/grabber/图标/FAB/开关/焦点环。
 *
 * 子树键：`touchTarget` / `button.height` / `input.height` / `list.rowHeight` /
 * `navBar.height` / `tabBar.height` / `grabber.width` / `grabber.height` / `icon` /
 * `fab` / `switch.width` / `switch.height` / `focusRing`。
 */
@Immutable
data class SizeTokens(
    val subtree: Map<String, TokenValue>,
) {
    val touchTarget: DimensionValue get() = subtree.dimension("touchTarget")
    val buttonHeight: DimensionValue get() = subtree.dimension("button.height")
    val inputHeight: DimensionValue get() = subtree.dimension("input.height")
    val listRowHeight: DimensionValue get() = subtree.dimension("list.rowHeight")
    val navBarHeight: DimensionValue get() = subtree.dimension("navBar.height")
    val tabBarHeight: DimensionValue get() = subtree.dimension("tabBar.height")
    val grabberWidth: DimensionValue get() = subtree.dimension("grabber.width")
    val grabberHeight: DimensionValue get() = subtree.dimension("grabber.height")
    val icon: DimensionValue get() = subtree.dimension("icon")
    val fab: DimensionValue get() = subtree.dimension("fab")
    val switchWidth: DimensionValue get() = subtree.dimension("switch.width")
    val switchHeight: DimensionValue get() = subtree.dimension("switch.height")
    val focusRing: DimensionValue get() = subtree.dimension("focusRing")
}
