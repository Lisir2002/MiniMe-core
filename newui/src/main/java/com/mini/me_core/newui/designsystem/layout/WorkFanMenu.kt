package com.mini.me_core.newui.designsystem.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * 办公扇形轮盘项：浏览器 / 终端可用，「待开发」灰显不可点。
 */
enum class WorkFanItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
) {
    Browser("浏览器", Icons.Rounded.Public, true),
    Terminal("终端", Icons.Rounded.Terminal, true),
    Todo("待开发", Icons.Rounded.Construction, false),
}

/**
 * 底部半圆扇形轮盘浮层：在底栏上方弹出，沿上半圆弧从左到右排列
 * [WorkFanItem.Browser]（左）/ [WorkFanItem.Terminal]（顶）/ [WorkFanItem.Todo]（右，灰显）。
 *
 * - 半透明罩层点空白处或再次点中央按钮均收起（[onDismiss]）。
 * - 动画统一走 [AppMotion] 令牌（emphasized/standard tween），不使用裸 Spring 常量。
 * - 颜色 / 间距 / 尺寸 / 圆角全部走 appPalette() 与 AppSpacing/AppSizing。
 */
@Composable
fun WorkFanMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WorkFanItem) -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = AppMotion.emphasizedTween<Float>()) +
            scaleIn(initialScale = 0.85f, animationSpec = AppMotion.emphasizedTween<Float>()),
        exit = fadeOut(animationSpec = AppMotion.standardTween<Float>()) +
            scaleOut(targetScale = 0.85f, animationSpec = AppMotion.standardTween<Float>()),
    ) {
        FanOverlay(onDismiss = onDismiss, onSelect = onSelect)
    }
}

@Composable
private fun FanOverlay(
    onDismiss: () -> Unit,
    onSelect: (WorkFanItem) -> Unit,
) {
    val palette = appPalette()
    // 扇形弧半径与项块尺寸均由令牌组合得到，不出现表外 dp。
    val arcRadius = AppSpacing.Xxl + AppSpacing.Xxl + AppSpacing.Xl // 88dp
    val itemSize = AppSizing.TouchTarget + AppSpacing.Md // 56dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 淡色罩层：基色 ink 叠 alpha，符合「淡色罩层用基色.copy(alpha=…)」规范
            .background(palette.ink.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
    ) {
        val centerX = maxWidth / 2
        // 扇形基线贴近底部（让出底栏高度），项块圆心在基线上方沿弧排布。
        val baselineY = maxHeight - (AppSizing.TouchTarget + AppSpacing.Xl)

        val items = WorkFanItem.entries
        val angles = listOf(150f, 90f, 30f)
        items.forEachIndexed { index, item ->
            val rad = Math.toRadians(angles[index].toDouble())
            val dx = (arcRadius.value * cos(rad)).dp
            val dy = (arcRadius.value * sin(rad)).dp
            val left = centerX - itemSize / 2 + dx
            val top = baselineY - itemSize / 2 - dy
            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(itemSize),
                contentAlignment = Alignment.Center,
            ) {
                FanItemColumn(
                    item = item,
                    onSelect = { if (item.enabled) onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun FanItemColumn(
    item: WorkFanItem,
    onSelect: () -> Unit,
) {
    val palette = appPalette()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (item.enabled) palette.primary else palette.surfaceDim,
            modifier = Modifier
                .size(AppSizing.TouchTarget + AppSpacing.Md)
                .clip(CircleShape)
                .then(
                    if (item.enabled) Modifier.clickable(onClick = onSelect)
                    else Modifier
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (item.enabled) palette.onPrimary else palette.labelTertiary,
                    modifier = Modifier.size(AppSizing.IconL),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.Xs))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (item.enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.enabled) palette.ink else palette.labelTertiary,
            maxLines = 1,
        )
    }
}
