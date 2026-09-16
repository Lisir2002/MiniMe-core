package com.mini.me_core.newui.designsystem.component.molecule

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppElevation
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/** 图标坞项：图标 + 悬浮标签 + 点击回调。 */
data class AppDockItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit = {},
)

/**
 * 图标坞（分子组 · AppDock）：iOS 简约风格的胶囊图标列，用于自底部快速切换/启动。
 *
 * 视觉对齐 iOS 简约规范：
 *  - **选中态**：图标染 [appPalette().primary]，背后一个浅蓝胶囊底（`BrandPrimary @ 12%`），
 *    图标正下方一个指示小圆点（iOS dock 常用 indicator），并轻微放大弹跳。
 *  - **未选中态**：图标染 [MaterialTheme.colorScheme.onSurfaceVariant]（灰标），无底无点。
 *  - **标签**：仅选中态以「悬浮气泡」叠在图标上方显示，用 [Box] 叠加、不参与测量，
 *    因此选中/未选中 Row 高度恒定、无布局跳变。
 *  - 按压即时放大（[collectIsPressedAsState]），选中有独立选中放大，两层互不耦合。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppDock(
    items: List<AppDockItem>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Row(
        modifier = modifier
            .shadow(elevation = AppElevation.Z2, shape = RoundedCornerShape(AppRadius.Pill), clip = false)
            // 不能在此 clip(Pill)：选中态标签气泡用 offset 画在胶囊边界外，会被整体裁掉导致永不显示。
            // 背景直接用 shape 绘制，外观与 clip 后填色等价；按压/选中层在各自图标盒内自裁。
            .background(color = background, shape = RoundedCornerShape(AppRadius.Pill))
            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val selected = index == selectedIndex
            val active = pressed || selected
            // 按压即刻弹起；选中态再叠一层放大，两者取最大值避免冲突
            val scale by animateFloatAsState(
                targetValue = if (active) 1.18f else 1f,
                animationSpec = AppMotion.emphasizedSpring(),
                label = "dockScale",
            )
            val selectBg by animateColorAsState(
                targetValue = if (selected) appPalette().primaryOverlay12 else Color.Transparent,
                label = "dockSelectBg",
            )
            val iconTint by animateColorAsState(
                targetValue = if (selected) appPalette().primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "dockIconTint",
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = AppSpacing.Xs)
                    .clip(RoundedCornerShape(AppRadius.Md))
                    .background(selectBg)
                    .clickable(interactionSource = interaction, indication = null) { item.onClick() }
                    .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Sm),
                contentAlignment = Alignment.Center,
            ) {
                // 按压叠加浅色调层，增强触觉反馈（iOS 按压感知）
                val pressAlpha by animateFloatAsState(
                    targetValue = if (pressed) 1f else 0f,
                    animationSpec = tween(120),
                    label = "dockPressAlpha",
                )
                if (pressAlpha > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * pressAlpha)),
                    )
                }
                // 悬浮气泡标签：Box 叠加，不占测量空间 → 选中/未选中高度恒定
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-AppSpacing.Sm - AppSizing.IconL).value.dp)
                            .clip(RoundedCornerShape(AppRadius.Sm))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                            .padding(horizontal = AppSpacing.Sm, vertical = AppSpacing.Xs),
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.surface,
                            maxLines = 1,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconTint,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .size(AppSizing.IconL),
                    )
                    // 指示点：始终占位，缩放 + 淡入淡出动画显现，避免高度跳变
                    val dotScale by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = AppMotion.standardSpring(),
                        label = "dockDotScale",
                    )
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = tween(180),
                        label = "dockDotAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = AppSpacing.Xs)
                            .size(AppSizing.IconXs / 2f)
                            .graphicsLayer { scaleX = dotScale; scaleY = dotScale; alpha = dotAlpha }
                            .clip(CircleShape)
                            .background(appPalette().primary),
                    )
                }
            }
        }
    }
}