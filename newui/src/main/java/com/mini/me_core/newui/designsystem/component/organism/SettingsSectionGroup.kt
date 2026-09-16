package com.mini.me_core.newui.designsystem.component.organism

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.mini.me_core.newui.designsystem.component.atom.AppCard
import com.mini.me_core.newui.designsystem.component.molecule.AppMenuRow
import com.mini.me_core.newui.designsystem.component.molecule.AppSectionHeader
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 一个设置项（organism · SettingsSectionGroup 的行模型）。
 *
 * @param id 行唯一 id，用于列表 key。
 * @param title 行主标题，映射到 [AppMenuRow] 的 title。
 * @param icon 可选前导图标，映射到 [AppMenuRow] 的 icon；null 不渲染图标位。
 * @param subtitle 可选副标题，映射到 [AppMenuRow] 的 subtitle；null 不渲染。
 * @param trailing 可选尾随插槽（开关 / 箭头 / 数值等），映射到 [AppMenuRow] 的 trailing；null 不渲染。
 * @param onClick 行点击回调；null 表示该行纯展示（无涟漪）。
 */
data class SettingItem(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
    val subtitle: String? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * 设置分组（organism · SettingsSectionGroup）：组合 [AppSectionHeader] + [AppCard] + [AppMenuRow]。
 *
 * 设计来源：§3.12 设置页卡片组 / 组件分层——
 * - 外层用原子层 [AppCard] 提供统一卡片底 + 圆角 + 阴影；
 * - 分组标题用分子层 [AppSectionHeader]（左侧竖条装饰 + SemiBold 标题）；
 * - 每一行用分子层 [AppMenuRow]（TouchTarget 行高 + 图标块 + 尾随插槽）；
 * - organism 层只负责「卡片包标题 + 多行」这一业务编排，不在此自定义行样式。
 *
 * 间距：标题与首行、行与行之间由 [AppMenuRow] 自带 vertical padding 控制；卡片内边距统一 [AppSpacing.Md]。
 *
 * @param title 分组标题；null 时不渲染 [AppSectionHeader]，卡片内只放行列表。
 * @param items 该分组下的设置行，按从上到下顺序渲染。
 */
@Composable
fun SettingsSectionGroup(
    title: String?,
    items: List<SettingItem>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(AppSpacing.Md)) {
            if (!title.isNullOrEmpty()) {
                AppSectionHeader(title = title)
            }
            items.forEach { item ->
                AppMenuRow(
                    title = item.title,
                    icon = item.icon,
                    subtitle = item.subtitle,
                    trailing = item.trailing,
                    onClick = item.onClick,
                )
            }
        }
    }
}
