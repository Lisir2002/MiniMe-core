package com.mini.me_core.newui.designsystem.component.molecule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.component.atom.IconContainer
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 通知项（分子组 · AppNotificationItem）：iOS 简约风格的通知行。
 *
 * 归一化要点（对齐 iOS 简约规范）：
 *  - **文字令牌**：标题用 [AppColor.BrandInk]、时间/正文用 [AppColor.LabelSecondary]，
 *    替换原 `colorScheme.onSurface/onSurfaceVariant` 混用，色阶严格走 label 分级。
 *  - **图标**：浅色强调底色（`accentColor @12%`）+ 纯强调色图标，修复"白字 on 0.12f 浅底"不可见缺陷
 *    （iOS 通知图标即「彩色 glyph + 浅色色块」形态）。
 *  - **未读态**：标题加粗 + 尾随强调色圆点；进入用 [AnimatedVisibility] 左侧滑入淡入。
 *  - **交互反馈**：可点击时按压弹簧收缩（`graphicsLayer` 缩放，不参与重排），
 *    与设计系统其余可点行一致（`indication = null` + 自绘按压态）；
 *    布局为透明分组行（贴合 iOS 分组列表），时间戳与正文自动省略。
 */
@Composable
fun AppNotificationItem(
    title: String,
    time: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: String? = null,
    unread: Boolean = false,
    accentColor: Color = AppColor.BrandPrimary,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "notificationScale",
    )

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            animationSpec = tween(AppMotion.Med.toInt()),
            initialOffsetX = { -it / 4 },
        ) + fadeIn(tween(AppMotion.Med.toInt())),
        exit = slideOutHorizontally(
            animationSpec = tween(AppMotion.Fast.toInt()),
            targetOffsetX = { -it / 4 },
        ) + fadeOut(tween(AppMotion.Fast.toInt())),
    ) {
        Row(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppRadius.Md))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onClick() }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconContainer(
                icon = icon,
                tint = accentColor,
                background = accentColor.copy(alpha = 0.12f),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AppSpacing.Md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        color = AppColor.BrandInk,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColor.LabelSecondary,
                        modifier = Modifier.padding(start = AppSpacing.Sm),
                        maxLines = 1,
                    )
                }
                if (body != null) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColor.LabelSecondary,
                        modifier = Modifier.padding(top = AppSpacing.Xs),
                        maxLines = 2,
                    )
                }
            }
            if (unread) {
                Spacer(Modifier.padding(start = AppSpacing.Md))
                Box(
                    Modifier
                        .size(AppSpacing.Sm)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
    }
}