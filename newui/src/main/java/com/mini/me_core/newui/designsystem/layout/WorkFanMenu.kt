package com.mini.me_core.newui.designsystem.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.mini.me_core.newui.designsystem.theme.appPalette
import com.mini.me_core.newui.designsystem.token.generated.AppLayout
import com.mini.me_core.newui.designsystem.token.generated.AppMotion
import com.mini.me_core.newui.designsystem.token.generated.AppSizing
import com.mini.me_core.newui.designsystem.token.generated.AppSpacing
import com.mini.me_core.newui.designsystem.token.generated.AppType
import kotlinx.coroutines.delay
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

/** 蒙层透明度：官方 scrim 32%，ink 叠 alpha。 */
private const val SCRIM_ALPHA = 0.32f
/** 项图标入场起始缩放比（弹到 1f）。 */
private const val ITEM_START_SCALE = 0.6f

/**
 * 错峰间隔：每项延迟约 25ms 依次弹出（由 Fast=150ms 令牌 / 6 推导，不引入裸数值）。
 * 文字比图标晚一拍：图标先到，标签再延迟一档（同 ~25ms）淡入，形成「绽放」节奏。
 */
private val FAN_STAGGER_MS: Long = AppMotion.Fast / 6
private val FAN_LABEL_LAG_MS: Long = AppMotion.Fast / 6

/**
 * 底部半圆扇形轮盘浮层：在底栏上方弹出，沿上半圆弧从左到右排列
 * [WorkFanItem.Browser]（左）/ [WorkFanItem.Terminal]（顶）/ [WorkFanItem.Todo]（右，灰显）。
 *
 * - 半透明罩层点空白处或再次点中央按钮均收起（[onDismiss]）。
 * - 错峰弹出：图标先缩放淡入、下方文字晚一拍淡入；进入时长走 [AppMotion.emphasizedTween]。
 * - 选中可用项后宿主自动收回。
 * - 颜色 / 间距 / 尺寸 / 圆角 / 动效全部走 appPalette() 与 AppSpacing/AppSizing/AppLayout/AppType/AppMotion。
 */
@Composable
fun WorkFanMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WorkFanItem) -> Unit,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = AppMotion.standardTween<Float>()),
        exit = fadeOut(animationSpec = AppMotion.standardTween<Float>()),
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
    val itemSize = AppSizing.Fab // 56dp，与中央圆钮同规格

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 淡色罩层：基色 ink 叠 alpha，符合「淡色罩层用基色.copy(alpha=…)」规范
            .background(palette.ink.copy(alpha = SCRIM_ALPHA))
            .clickable(onClick = onDismiss),
    ) {
        val centerX = maxWidth / 2
        // 扇形基线对齐底栏上沿（= 中央圆钮圆心高度），项块圆心在基线上方沿弧排布。
        val baselineY = maxHeight - AppLayout.BottomBarHeight

        val items = WorkFanItem.entries
        val angles = listOf(150f, 90f, 30f)
        items.forEachIndexed { index, item ->
            val rad = Math.toRadians(angles[index].toDouble())
            // Dp 乘 Float 得 Dp，消灭裸 .dp 表外数值。
            val dx = arcRadius * cos(rad).toFloat()
            val dy = arcRadius * sin(rad).toFloat()
            val left = centerX - itemSize / 2 + dx
            val top = baselineY - itemSize / 2 - dy
            // 图标先到、文字晚一拍。
            val iconProgress = rememberFanItemProgress(index, 0L)
            val labelProgress = rememberFanItemProgress(index, FAN_LABEL_LAG_MS)
            Box(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(itemSize),
                contentAlignment = Alignment.Center,
            ) {
                FanItemColumn(
                    item = item,
                    iconProgress = iconProgress,
                    labelProgress = labelProgress,
                    onSelect = { if (item.enabled) onSelect(item) },
                )
            }
        }
    }
}

/**
 * 错峰进度：第 [index] 项延迟 index * [FAN_STAGGER_MS] 再叠加 [extraDelay]（图标 0、文字一档 lag）后，
 * 用 [AppMotion.emphasizedTween] 从 0 动画到 1。
 */
@Composable
private fun rememberFanItemProgress(index: Int, extraDelay: Long): Float {
    val anim = remember(index, extraDelay) { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * FAN_STAGGER_MS + extraDelay)
        anim.animateTo(1f, animationSpec = AppMotion.emphasizedTween())
    }
    return anim.value
}

@Composable
private fun FanItemColumn(
    item: WorkFanItem,
    iconProgress: Float,
    labelProgress: Float,
    onSelect: () -> Unit,
) {
    val palette = appPalette()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = if (item.enabled) palette.primary else palette.surfaceDim,
            modifier = Modifier
                .size(AppSizing.Fab)
                .graphicsLayer {
                    alpha = iconProgress
                    scaleX = ITEM_START_SCALE + (1f - ITEM_START_SCALE) * iconProgress
                    scaleY = ITEM_START_SCALE + (1f - ITEM_START_SCALE) * iconProgress
                }
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
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AppType.Caption),
            fontWeight = if (item.enabled) FontWeight.SemiBold else FontWeight.Normal,
            color = if (item.enabled) palette.ink else palette.labelTertiary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = labelProgress },
        )
    }
}
