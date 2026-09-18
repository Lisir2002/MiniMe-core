package com.mini.me_core.feature.agent.presentation.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppColor
import com.mini.me_core.newui.designsystem.token.generated.AppRadius
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing

/**
 * 吸附在输入框上方的常驻任务条（只读）。
 *
 * 数据来源：todo 工具流实时产出的最新 todo 快照（[ParsedTodoResult.items]）。
 * - 无任务：不渲染（调用方自行控制可见性）。
 * - 有未完成：左环形进度（已完成/总数，按比例着色）+ 中当前进行中任务名 + 右展开箭头。
 * - 全部完成：绿勾完成态（停留自动收起由调用方控制）。
 * - 点击展开为半屏只读列表（[TodoReadonlySheet]）。
 *
 * 颜色 / 尺寸 / 圆角全部走 newui 令牌，无裸色值与表外 dp。
 */
@Composable
internal fun TodoDockBar(
    items: List<ParsedTodoItem>,
    allDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = appPalette()
    val total = items.size
    val done = items.count { it.status == "completed" }
    val progress = if (total == 0) 0f else done.toFloat() / total
    val current = items.firstOrNull { it.status == "in_progress" }?.subject
        ?: items.firstOrNull { it.status != "completed" }?.subject
        ?: "全部任务已完成"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.Lg, vertical = AppSpacing.Xs)
            .clip(RoundedCornerShape(AppRadius.Md))
            .background(palette.card)
            .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 环形进度 / 完成绿勾
        if (allDone) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = AppColor.StatusSuccess,
                modifier = Modifier.size(AppSizing.IconM),
            )
        } else {
            RingProgress(
                progress = progress,
                modifier = Modifier.size(AppSizing.IconM),
            )
        }
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = if (allDone) "任务完成 $done/$total" else (current ?: "进行中…"),
            style = MaterialTheme.typography.labelMedium,
            color = if (allDone) AppColor.StatusSuccess else palette.ink,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = "展开任务列表",
            tint = palette.labelSecondary,
            modifier = Modifier.size(AppSizing.IconS),
        )
    }
}

/** 环形进度：底色轨 + 按比例主色弧。 */
@Composable
private fun RingProgress(progress: Float, modifier: Modifier = Modifier) {
    val palette = appPalette()
    Canvas(modifier = modifier) {
        val stroke = this.size.width * 0.16f
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width, this.size.height)
        val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
        val inset = androidx.compose.ui.geometry.Size(
            arcSize.width - stroke,
            arcSize.height - stroke,
        )
        drawCircle(
            color = palette.surfaceDim,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = palette.primary,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = inset,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** 半屏只读任务列表：进行中脉动，已完成打勾。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoReadonlySheet(
    items: List<ParsedTodoItem>,
    onDismiss: () -> Unit,
) {
    val palette = appPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
    ) {
        Text(
            text = "任务清单（只读）",
            style = MaterialTheme.typography.titleSmall,
            color = palette.ink,
            modifier = Modifier.padding(start = AppSpacing.Lg, end = AppSpacing.Lg, bottom = AppSpacing.Sm),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.Lg)
                .padding(bottom = AppSpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
        ) {
            items.forEach { item ->
                TodoSheetRow(item = item)
            }
        }
    }
}

@Composable
private fun TodoSheetRow(item: ParsedTodoItem) {
    val palette = appPalette()
    val isDone = item.status == "completed"
    val isRunning = item.status == "in_progress"
    val transition = rememberInfiniteTransition(label = "todoRowPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "todoRowPulseAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            isDone -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = AppColor.StatusSuccess,
                modifier = Modifier.size(AppSizing.IconS),
            )
            isRunning -> Box(
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .graphicsLayer { this.alpha = alpha }
                    .clip(CircleShape)
                    .background(palette.primary),
            )
            else -> Box(
                modifier = Modifier
                    .size(AppSizing.IconXs)
                    .clip(CircleShape)
                    .background(palette.surfaceDim),
            )
        }
        Spacer(Modifier.width(AppSpacing.Sm))
        Text(
            text = item.subject,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDone) palette.labelSecondary else palette.ink,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
    }
}
