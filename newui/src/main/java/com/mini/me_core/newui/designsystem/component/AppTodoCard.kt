package com.mini.me_core.newui.designsystem.component

import com.mini.me_core.newui.designsystem.theme.appPalette
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppStroke

/** 任务清单项状态：未开始 / 进行中 / 已完成。 */
enum class AppTodoStatus { Pending, Running, Done }

/** 任务清单项。 */
data class AppTodoItem(
    val text: String,
    val status: AppTodoStatus = AppTodoStatus.Pending,
)

/**
 * 实时任务清单卡（分子组 · AppTodoCard）：长任务执行中 Agent 自驱追踪子步骤，
 * 与事前审批的 [AppPlanCard] 互补——Plan 是"开始前确认"，Todo 是"进行中逐条打勾"。
 *
 * - Pending：空心圆；Running：旋转中的进行图标（无限动画）；Done：绿色对勾。
 * - 已完成项文字弱化，进行中项高亮。
 *
 * @since 0.1.0-experimental
 */
@Composable
fun AppTodoCard(
    items: List<AppTodoItem>,
    modifier: Modifier = Modifier,
    title: String = "进行中的任务",
) {
    val shape = RoundedCornerShape(AppRadius.Md)
    Column(
        modifier = modifier
            .clip(shape)
            .background(appPalette().card)
            .border(AppStroke.Thin, appPalette().separator, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = null,
                tint = appPalette().primary,
                modifier = Modifier.size(AppSizing.IconXs),
            )
            Spacer(Modifier.width(AppSpacing.Sm))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = appPalette().ink,
                modifier = Modifier.weight(1f),
            )
            val done = items.count { it.status == AppTodoStatus.Done }
            Text(
                text = "$done/${items.size}",
                style = MaterialTheme.typography.labelSmall,
                color = appPalette().labelSecondary,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Xs),
        ) {
            items.forEach { item -> TodoRow(item) }
        }
    }
}

@Composable
private fun TodoRow(item: AppTodoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.status) {
            AppTodoStatus.Done -> Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = AppColor.StatusSuccess,
                modifier = Modifier.size(AppSizing.IconM),
            )
            AppTodoStatus.Running -> {
                val transition = rememberInfiniteTransition(label = "todoSpin")
                val angle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "todoAngle",
                )
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = appPalette().primary,
                    modifier = Modifier
                        .size(AppSizing.IconM)
                        .rotate(angle),
                )
            }
            AppTodoStatus.Pending -> Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = appPalette().labelTertiary,
                modifier = Modifier.size(AppSizing.IconM),
            )
        }
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = when (item.status) {
                AppTodoStatus.Done -> appPalette().labelTertiary
                AppTodoStatus.Running -> appPalette().ink
                AppTodoStatus.Pending -> appPalette().labelSecondary
            },
            fontWeight = if (item.status == AppTodoStatus.Running) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
