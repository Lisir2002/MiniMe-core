package com.mini.me_core.newui.designsystem.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppType

/**
 * 门户底栏三 tab：对话（左） / 办公·中央凸起主色圆钮（中） / 设置（右）。
 *
 * - 底栏总高 [AppLayout.BottomBarHeight]（56dp），半透明 surface 模拟毛玻璃（targetSdk 28 无 RenderEffect），
 *   顶部 1dp 分割线用 palette.separator。
 * - 中央圆钮直径 [AppSizing.Fab]（56dp），向上探出底栏上沿（露出上半圆），主色填充；按压走
 *   [AppMotion.emphasizedSpring] 弹性缩放。圆内图标 24dp，圆下方标签文字（办公 / 浏览器 / 终端）
 *   由宿主通过 [centerLabel] 下发。
 * - 两侧 tab 等宽均分，图标 24dp + [AppType.Caption]（11sp）标签；选中态 = 主色 + SemiBold，
 *   未选中 = labelSecondary + Normal，颜色 + 字重双通道区分。
 *
 * 本组件只画固定底栏；扇形轮盘 [WorkFanMenu] 由宿主作为上层浮层叠加在底栏之上。
 */
enum class PortalTab { Chat, Work, Settings }

/** 底栏半透明 surface 罩层透明度（毛玻璃模拟）。 */
private const val BAR_SURFACE_ALPHA = 0.92f
/** 中央圆钮按下时的缩放比（松开后由 emphasizedSpring 弹回 1f）。 */
private const val FAB_PRESSED_SCALE = 0.92f

@Composable
fun PortalBottomBar(
    selected: PortalTab,
    centerLabel: String,
    onChat: () -> Unit,
    onWork: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = appPalette()
    val barHeight = AppLayout.BottomBarHeight
    val fabDiameter = AppSizing.Fab
    // 圆钮向上探出底栏上沿的量 = 半径；外层 Box 需为此留白，避免被父裁剪。
    val overhang = fabDiameter / 2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight + overhang),
    ) {
        // 底栏条（底部 56dp）：半透明 surface 毛玻璃 + 顶部 1dp 分割线。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight)
                .background(palette.surface.copy(alpha = BAR_SURFACE_ALPHA)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(AppLayout.DividerThickness)
                    .background(palette.separator),
            )
        }

        // 两侧 tab：等宽均分，在底栏条内垂直居中。中央槽位留白，圆钮与标签由上层浮层叠加。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                icon = Icons.Rounded.ChatBubble,
                label = "对话",
                selected = selected == PortalTab.Chat,
                onClick = onChat,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
            BottomTabItem(
                icon = Icons.Rounded.Settings,
                label = "设置",
                selected = selected == PortalTab.Settings,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }

        // 中央凸起主色圆钮 + 下方标签：探出底栏上沿，按压走 emphasizedSpring 弹性缩放。
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (pressed) FAB_PRESSED_SCALE else 1f,
                animationSpec = AppMotion.emphasizedSpring(),
                label = "fabScale",
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .size(fabDiameter)
                    .clip(CircleShape)
                    .background(palette.primary)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onWork,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Build,
                    contentDescription = centerLabel,
                    tint = palette.onPrimary,
                    modifier = Modifier.size(AppSizing.IconL),
                )
            }
            Spacer(Modifier.height(AppSpacing.Xs))
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = AppType.Caption),
                fontWeight = if (selected == PortalTab.Work) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected == PortalTab.Work) palette.primary else palette.labelSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = appPalette()
    val tint = if (selected) palette.primary else palette.labelSecondary
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = AppSpacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(AppSizing.IconL),
        )
        Spacer(Modifier.height(AppSpacing.Tiny))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AppType.Caption),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
