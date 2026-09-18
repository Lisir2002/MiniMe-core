package com.mini.me_core.newui.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 门户底栏三 tab：对话（左） / 办公·中央凸起主色大按钮（中） / 设置（右）。
 *
 * - 中央按钮比两侧 tab 高、向上凸起，主色填充；文字随当前选中工作工具在
 *   「办公 / 浏览器 / 终端」间切换，由宿主通过 [centerLabel] 下发。
 * - 两侧 tab 为图标 + 文字，选中态用主色，未选中用二级文字色。
 *
 * 本组件只画固定底栏；扇形轮盘 [WorkFanMenu] 由宿主作为上层浮层叠加在底栏之上。
 */
enum class PortalTab { Chat, Work, Settings }

@Composable
fun PortalBottomBar(
    selected: PortalTab,
    centerLabel: String,
    onChat: () -> Unit,
    onWork: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = appPalette()
    // 中央凸起按钮纵向高度 = 触摸目标 + 一档间距，比两侧 tab 略高；向上凸起量 = 半档间距。
    val centerHeight = AppSizing.TouchTarget + AppSpacing.Md
    val centerLift = AppSpacing.Sm

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(centerHeight + AppSpacing.Sm /* 给上方凸起留白，避免被父裁剪 */),
        verticalAlignment = Alignment.Bottom,
    ) {
        BottomTabItem(
            icon = Icons.Rounded.ChatBubble,
            label = "对话",
            selected = selected == PortalTab.Chat,
            onClick = onChat,
            modifier = Modifier.weight(1f),
        )
        // 中央凸起主色按钮：向上 lift，主色填充胶囊 + 图标 + 文字。
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .offset(y = -centerLift)
                    .clip(RoundedCornerShape(AppRadius.Pill))
                    .background(palette.primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onWork,
                    )
                    .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Build,
                    contentDescription = centerLabel,
                    tint = palette.onPrimary,
                    modifier = Modifier.size(AppSizing.IconM),
                )
                Spacer(Modifier.height(AppSpacing.Tiny))
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.onPrimary,
                    maxLines = 1,
                )
            }
        }
        BottomTabItem(
            icon = Icons.Rounded.Settings,
            label = "设置",
            selected = selected == PortalTab.Settings,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
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
            .height(AppSizing.TouchTarget)
            .clip(RoundedCornerShape(AppRadius.Sm))
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
            modifier = Modifier.size(AppSizing.IconM),
        )
        Spacer(Modifier.height(AppSpacing.Tiny))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
