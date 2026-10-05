package com.mini.me_core.core.theme.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mini.me_core.core.theme.tokens.LocalCornerRadius

/**
 * 浮动偏移：组件不占据父容器布局空间，但视觉上放置在指定偏移位置。
 */
private fun Modifier.floatingOffset(yPx: Int): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(0, 0) { placeable.placeRelative(0, -yPx) }
    }
)

/** 工具页面枚举。 */
@Stable
enum class ToolPage(val route: String, val label: String) {
    GIT("git", "Git"),
    BROWSER("browser", "浏览器"),
    TERMINAL("terminal", "终端"),
    ;
    companion object { fun fromRoute(route: String?): ToolPage? = entries.firstOrNull { it.route == route } }
}

/** 底栏导航项配置。 */
@Stable
private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem("chat", "对话", Icons.Rounded.Chat),
    NavItem("more", "更多", Icons.Rounded.Apps),
    NavItem("settings", "设置", Icons.Rounded.Settings),
)

/**
 * 应用底部导航栏（深度重构版）。
 *
 * 特性：药丸形选中指示器、毛玻璃半透明背景、选中项弹性缩放、
 * 「更多」按钮点击/长按统一展开扇形菜单、扇形菜单错峰弹簧动画、
 * 触觉反馈、左右滑动切换页面、滚动感知隐藏。
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    visible: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    var fanMenuExpanded by remember { mutableStateOf(false) }

    val isToolSelected = currentRoute in listOf("git", "browser", "terminal")
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fanMenuOffsetPx = with(LocalDensity.current) { 220.dp.roundToPx() }

    val selectedIndex = when {
        currentRoute == "chat" -> 0
        isToolSelected -> 1
        currentRoute == "settings" -> 2
        else -> 0
    }

    val indicatorProgress by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "pill_indicator",
    )

    val visibilityAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "bar_visibility",
    )

    val currentToolLabel = ToolPage.fromRoute(currentRoute)?.label ?: "更多"
    var dragStartIndex by remember { mutableStateOf(0) }

    // 用屏幕宽度计算药丸指示器位置
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val itemWidthDp = screenWidthDp / 3f
    val indicatorWidthDp = 64f
    val indicatorOffsetXDp = indicatorProgress * itemWidthDp + (itemWidthDp - indicatorWidthDp) / 2f

    Box(modifier = Modifier.graphicsLayer { alpha = visibilityAlpha }) {
        // 药丸选中指示器
        Box(
            modifier = Modifier
                .width(indicatorWidthDp.dp)
                .height(32.dp)
                .offset(x = indicatorOffsetXDp.dp, y = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(activeColor.copy(alpha = 0.14f)),
        )

        // 底栏容器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .navigationBarsPadding()
                .height(56.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStartIndex = selectedIndex },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 60f && dragStartIndex > 0) {
                                val target = when (dragStartIndex) {
                                    1 -> "chat"
                                    2 -> if (isToolSelected) "git" else "chat"
                                    else -> "chat"
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigate(target)
                                dragStartIndex = -1
                            } else if (dragAmount < -60f && dragStartIndex < 2) {
                                val target = when (dragStartIndex) {
                                    0 -> "git"
                                    1 -> "settings"
                                    else -> "settings"
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigate(target)
                                dragStartIndex = -1
                            }
                        },
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NAV_ITEMS.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.6f),
                    label = "icon_scale_${item.route}",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (item.route == "more") fanMenuExpanded = true else onNavigate(item.route)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) activeColor else inactiveColor,
                            modifier = Modifier.size(24.dp).scale(scale),
                        )
                        Text(
                            text = if (item.route == "more") currentToolLabel else item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) activeColor else inactiveColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 扇形菜单覆盖层
        if (fanMenuExpanded) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp).floatingOffset(fanMenuOffsetPx),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { fanMenuExpanded = false },
                        ),
                )
                Box(modifier = Modifier.width(260.dp).height(180.dp).align(Alignment.BottomCenter)) {
                    ToolPage.entries.forEachIndexed { index, tool ->
                        val angleDeg = when (tool) {
                            ToolPage.GIT -> 150f
                            ToolPage.BROWSER -> 90f
                            ToolPage.TERMINAL -> 30f
                        }
                        val radius = if (tool == ToolPage.BROWSER) 115f else 105f
                        FanMenuItem(
                            label = tool.label,
                            expanded = fanMenuExpanded,
                            angleDeg = angleDeg,
                            radius = radius,
                            staggerIndex = index,
                            isActive = currentRoute == tool.route,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigate(tool.route)
                                fanMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 扇形菜单项，带错峰延迟。 */
@Composable
private fun FanMenuItem(
    label: String,
    expanded: Boolean,
    angleDeg: Float,
    radius: Float,
    staggerIndex: Int,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val targetX = (Math.cos(angleRad) * radius).toFloat()
    val targetY = -(Math.sin(angleRad) * radius).toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 280, delayMillis = staggerIndex * 50, easing = FastOutSlowInEasing),
        label = "fan_$label",
    )

    val bgColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .offset(x = (targetX * animatedProgress).dp, y = (targetY * animatedProgress).dp)
            .graphicsLayer {
                alpha = animatedProgress
                scaleX = 0.6f + 0.4f * animatedProgress
                scaleY = 0.6f + 0.4f * animatedProgress
            }
            .clip(RoundedCornerShape(LocalCornerRadius.current.sm))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}
