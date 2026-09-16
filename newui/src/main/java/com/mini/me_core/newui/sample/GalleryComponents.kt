package com.mini.me_core.newui.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.primitive.AppCard
import com.mini.me_core.newui.designsystem.primitive.AppChip
import com.mini.me_core.newui.designsystem.primitive.AppIcon
import com.mini.me_core.newui.designsystem.primitive.IconContainer
import com.mini.me_core.newui.designsystem.component.AppButton
import com.mini.me_core.newui.designsystem.component.AppButtonVariant
import com.mini.me_core.newui.designsystem.component.AppDivider
import com.mini.me_core.newui.designsystem.component.AppMenuRow
import com.mini.me_core.newui.designsystem.component.AppSectionGroup
import com.mini.me_core.newui.designsystem.component.AppSectionHeader
import com.mini.me_core.newui.designsystem.component.AppStatusDot
import com.mini.me_core.newui.designsystem.layout.AppEmptyState
import com.mini.me_core.newui.designsystem.layout.AppErrorState
import com.mini.me_core.newui.designsystem.layout.AppLoadingState
import com.mini.me_core.newui.designsystem.theme.AppType
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 令牌 / 原子 / 按钮 / 分组行 / 三态等无状态展示 Section。
 * 作为 GalleryBody 滚动内容的第一段调用。
 */
@Composable
internal fun PrimitiveGallerySection() {
    Section("令牌 · 色板") {
        ColorRow(
            listOf(
                "BrandPrimary" to appPalette().primary,
                "BrandSurface" to appPalette().surface,
                "BrandAccent" to appPalette().accent,
                "StatusSuccess" to AppColor.StatusSuccess,
                "StatusDanger" to AppColor.StatusDanger,
            ),
        )
    }

    Section("令牌 · 度量/圆角/阴影") {
        Text(
            "Spacing · 间距增量（隔块间实际留白）",
            style = AppType.SectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SpacingBar("XS", AppSpacing.Xs)
        SpacingBar("SM", AppSpacing.Sm)
        SpacingBar("MD", AppSpacing.Md)
        SpacingBar("LG", AppSpacing.Lg)
        SpacingBar("XL", AppSpacing.Xl)
        SpacingBar("XXL", AppSpacing.Xxl)
        Text(
            "Radius · 圆角（实块对照）",
            style = AppType.SectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
            RadiusTile("SM", AppRadius.Sm)
            RadiusTile("MD", AppRadius.Md)
            RadiusTile("LG", AppRadius.Lg)
            RadiusTile("PILL", AppRadius.Pill)
        }
        Text(
            "Elevation · 阴影（卡片对照）",
            style = AppType.SectionHeader,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
            ElevationTile("Z0", AppElevation.Z0)
            ElevationTile("Z1", AppElevation.Z1)
            ElevationTile("Z2", AppElevation.Z2)
            ElevationTile("Z4", AppElevation.Z4)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
            MetricParam("Sizing", "touch=${AppSizing.TouchTarget} iconBlock=${AppSizing.IconBlock}")
            MetricParam("Layout", "pageH=${AppLayout.PageHorizontal} max=${AppLayout.ContentMaxWidth}")
        }
    }

    Section("令牌 · 排版（iOS 类型尺度）") {
        Text("Large Title · 导航大标题", style = AppType.LargeTitle)
        Text("Title1 · 首屏区块主标题", style = AppType.Title1)
        Text("Title2 · 次级区块标题", style = AppType.Title2)
        Text("Title3 · 小标题", style = AppType.Title3)
        Text("Headline · 加粗正文", style = AppType.Headline)
        Text("Body · 标准正文", style = AppType.Body)
        Text("Callout · 次要正文", style = AppType.Callout)
        Text("Subhead · 注释行", style = AppType.Subhead)
        Text("Footnote · 脚注", style = AppType.Footnote)
        Text("Caption1 · 辅助说明", style = AppType.Caption1)
        Text("Counter", style = AppType.Caption2, color = appPalette().labelSecondary)
    }

    Section("原子组件") {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(AppSpacing.Lg), verticalAlignment = Alignment.CenterVertically) {
                IconContainer(icon = Icons.Rounded.Code, tint = appPalette().onPrimary)
                Text(
                    text = "AppCard · IconContainer",
                    modifier = Modifier.padding(start = AppSpacing.Lg),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppChip(text = "选中", icon = Icons.Rounded.Home)
            AppChip(text = "未选中", selected = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppIcon(icon = Icons.Rounded.Settings)
            AppIcon(icon = Icons.Rounded.Palette, size = AppSizing.IconL)
            AppIcon(icon = Icons.Rounded.Code, size = AppSizing.IconXs)
        }
    }

    Section("分子组件 · 按钮") {
        AppButton(text = "Primary", onClick = {})
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppButton(
                text = "Tonal",
                variant = AppButtonVariant.FilledTonal,
                onClick = {},
            )
            AppButton(
                text = "Outlined",
                variant = AppButtonVariant.Outlined,
                onClick = {},
            )
            AppButton(
                text = "Text",
                variant = AppButtonVariant.Text,
                onClick = {},
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            AppButton(text = "禁用", enabled = false, onClick = {})
            AppButton(
                text = "危险",
                variant = AppButtonVariant.Outlined,
                onClick = {},
            )
        }
    }

    Section("分子组件 · 分组 / 列表行 / 状态") {
        AppSectionHeader(title = "区块标题")
        AppSectionGroup {
            AppMenuRow(
                title = "设置项（无图标）",
                subtitle = "副标题说明",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            AppDivider()
            AppMenuRow(
                title = "带图标块",
                subtitle = "iconContainer = true",
                icon = Icons.Rounded.Code,
                iconContainer = true,
            )
            AppDivider()
            AppMenuRow(
                title = "纯色前置图标",
                icon = Icons.Rounded.Settings,
                iconContainer = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Lg)) {
            AppStatusDot(color = AppColor.StatusSuccess, label = "成功")
            AppStatusDot(color = AppColor.StatusWarning, label = "警告")
            AppStatusDot(color = AppColor.StatusDanger)
        }
    }

    Section("三态") {
        Box(Modifier.fillMaxWidth().size(96.dp), contentAlignment = Alignment.Center) {
            AppLoadingState()
        }
        AppEmptyState(
            icon = Icons.Rounded.Home,
            title = "暂无内容",
            description = "这是空态示例，展示空状态占位。",
        )
        AppCard(modifier = Modifier.fillMaxWidth()) {
            AppErrorState(message = "网络异常，请重试。")
        }
    }
}

/** iOS 简约分组：紧凑灰标题 + 白色圆角卡片分组（去顶部分隔线，以留白分层）。 */
@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AppRadius.Md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.Z0),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                content()
            }
        }
    }
}

/** 间距可视化：两个色块中间的留白宽度即该档令牌实际值。 */
@Composable
internal fun SpacingBar(label: String, gap: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().primary, RoundedCornerShape(AppRadius.None)))
        Spacer(Modifier.width(gap))
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().accent, RoundedCornerShape(AppRadius.None)))
        Spacer(Modifier.width(AppSpacing.Sm))
        Text("$label · $gap", style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 圆角可视化：实块照搬令牌圆角，直观对比 Sm / Md / Lg / Pill。 */
@Composable
internal fun RadiusTile(label: String, radius: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Box(Modifier.size(AppSizing.TouchTarget).background(appPalette().primary, RoundedCornerShape(radius)))
        Text(label, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 阴影可视化：白卡片按令牌档位加阴影，直观对比 Z0 / Z1 / Z2 / Z4。 */
@Composable
internal fun ElevationTile(label: String, elevation: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
    ) {
        Box(
            Modifier
                .size(AppSizing.TouchTarget)
                .shadow(elevation, RoundedCornerShape(AppRadius.Md), clip = false)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(AppRadius.Md)),
        )
        Text(label, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 参数速查：大小/布局令牌的文本值。 */
@Composable
internal fun MetricParam(name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = AppType.Caption1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(value, style = AppType.Caption2, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun ColorRow(list: List<Pair<String, Color>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
    ) {
        list.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(color, shape = RoundedCornerShape(AppRadius.Md)),
                )
                Text(text = name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
